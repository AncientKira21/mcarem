package com.ancientkira.mca.server.world.data;

import io.netty.buffer.ByteBuf;
import com.ancientkira.mca.MCA;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.VillagerLike;
import com.ancientkira.mca.entity.ai.relationship.EntityRelationship;
import com.ancientkira.mca.entity.ai.relationship.Gender;
import com.ancientkira.mca.entity.ai.relationship.RelationshipState;
import com.ancientkira.mca.util.NbtHelper;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

public final class FamilyTreeNode {
    public static final StreamCodec<ByteBuf, FamilyTreeNode> STREAM_CODEC = StreamCodec.of(
            (out, value) -> {
                ByteBufCodecs.COMPOUND_TAG.encode(out, value.save());
            },
            (in) -> {
                return new FamilyTreeNode(null, ByteBufCodecs.COMPOUND_TAG.decode(in));
            }
    );
    private final boolean isPlayer;
    private final UUID id;
    private final Set<UUID> children = new HashSet<>();
    private transient final FamilyTree rootNode;
    private Gender gender;
    private String name;
    private String profession = VillagerProfession.NONE.identifier().toString();
    private UUID father;
    private UUID mother;
    private UUID partner = Util.NIL_UUID;
    private UUID widowedPartner = Util.NIL_UUID;
    private RelationshipState relationshipState = RelationshipState.SINGLE;
    private boolean deceased;
    private CompoundTag revivalData;
    private CompoundTag appearanceData;
    private UUID lastRevivalRescuer;
    private String pendingSurname;
    private net.minecraft.nbt.ListTag revivalThanks = new net.minecraft.nbt.ListTag();

    public FamilyTreeNode(FamilyTree rootNode, UUID id, String name, boolean isPlayer, Gender gender, UUID father, UUID mother) {
        this.rootNode = rootNode;
        this.id = id;
        this.name = name;
        this.isPlayer = isPlayer;
        this.gender = gender;
        this.father = father;
        this.mother = mother;
    }

    public FamilyTreeNode(FamilyTree rootNode, CompoundTag nbt) {
        this(
                rootNode,
                nbt.read("id", UUIDUtil.CODEC).orElseThrow(),
                nbt.getString("name").orElse(""),
                nbt.getBoolean("isPlayer").orElse(false),
                Gender.byId(nbt.getInt("gender").orElse(0)),
                nbt.read("father", UUIDUtil.CODEC).orElse(Util.NIL_UUID),
                nbt.read("mother", UUIDUtil.CODEC).orElse(Util.NIL_UUID)
        );
        nbt.getList("children").ifPresent(listTag -> children.addAll(
                NbtHelper.toList(listTag, c -> ((CompoundTag) c).read("uuid", UUIDUtil.CODEC).orElse(Util.NIL_UUID))
        ));
        pendingSurname = nbt.getString("pendingSurname").orElse(null);
        lastRevivalRescuer = nbt.read("lastRevivalRescuer", UUIDUtil.CODEC).orElse(null);
        profession = nbt.getString("profession").orElse(profession);
        deceased = nbt.getBoolean("isDeceased").orElse(false);
        partner = nbt.read("spouse", UUIDUtil.CODEC).orElse(Util.NIL_UUID);
        widowedPartner = nbt.read("widowedPartner", UUIDUtil.CODEC).orElse(Util.NIL_UUID);
        relationshipState = RelationshipState.byId(nbt.getInt("marriageState").orElse(0));
        revivalData = nbt.getCompound("revivalData").orElse(null);
        appearanceData = nbt.getCompound("appearanceData").orElse(null);
        revivalThanks = nbt.getList("revivalThanks").map(net.minecraft.nbt.ListTag::copy).orElseGet(net.minecraft.nbt.ListTag::new);
    }

    public static boolean isValid(@Nullable UUID uuid) {
        return uuid != null && !Util.NIL_UUID.equals(uuid);
    }

    private static void gatherParents(FamilyTreeNode current, Set<UUID> family, int depth) {
        gather(current, family, depth, FamilyTreeNode::streamParents);
    }

    private static void gatherChildren(FamilyTreeNode current, Set<UUID> family, int depth) {
        gather(current, family, depth, FamilyTreeNode::streamChildren);
    }

    private static void gather(@Nullable FamilyTreeNode entry, Set<UUID> output, int depth, Function<FamilyTreeNode, Stream<UUID>> walker) {
        if (entry == null || depth <= 0) {
            return;
        }
        walker.apply(entry).forEach(id -> {
            if (!Util.NIL_UUID.equals(id)) {
                output.add(id); //zero UUIDs are no real members
            }
            if (depth > 1) {
                entry.getRoot().getOrEmpty(id).ifPresent(e -> gather(e, output, depth - 1, walker));
            }
        });
    }

    public UUID id() {
        return id;
    }

    private void markDirty() {
        if (rootNode != null) {
            rootNode.setDirty();
        }
    }

    public boolean isDeceased() {
        return deceased;
    }

    public void setDeceased(boolean deceased) {
        boolean changed = this.deceased != deceased;
        if (changed && deceased) rememberWidowedMarriage();
        this.deceased = deceased;
        if (!deceased) restoreWidowedMarriage();
        markDirty();
    }

    private void rememberWidowedMarriage() {
        if (!relationshipState.isMarried() || partner.equals(Util.NIL_UUID)) return;
        UUID spouseId = partner;
        widowedPartner = spouseId;
        rootNode.getOrEmpty(spouseId).ifPresent(spouse -> {
            if (spouse.partner.equals(id) && spouse.relationshipState.isMarried()) {
                spouse.widowedPartner = id;
                spouse.partner = Util.NIL_UUID;
                spouse.relationshipState = RelationshipState.WIDOW;
                spouse.markDirty();
            }
        });
        partner = Util.NIL_UUID;
        relationshipState = RelationshipState.WIDOW;
    }

    private void restoreWidowedMarriage() {
        if (widowedPartner.equals(Util.NIL_UUID) && revivalData != null) {
            widowedPartner = revivalData.read("MCAWidowedPartner", UUIDUtil.CODEC).orElse(Util.NIL_UUID);
        }
        if (widowedPartner.equals(Util.NIL_UUID) && relationshipState == RelationshipState.WIDOW) {
            var matches = rootNode.getAllEntries().filter(candidate -> !candidate.id.equals(id)
                    && candidate.widowedPartner.equals(id) && candidate.relationshipState == RelationshipState.WIDOW).limit(2).toList();
            if (matches.size() == 1) widowedPartner = matches.getFirst().id;
        }
        if (widowedPartner.equals(Util.NIL_UUID) || !partner.equals(Util.NIL_UUID)
                || relationshipState != RelationshipState.WIDOW) return;
        rootNode.getOrEmpty(widowedPartner).ifPresent(spouse -> {
            if (!spouse.deceased && spouse.partner.equals(Util.NIL_UUID)
                    && spouse.relationshipState == RelationshipState.WIDOW
                    && (spouse.widowedPartner.equals(id) || spouse.widowedPartner.equals(Util.NIL_UUID))) {
                updatePartner(spouse);
                spouse.updatePartner(this);
            }
        });
    }

    public UUID getWidowedPartner() { return widowedPartner; }

    public Optional<CompoundTag> getRevivalData() {
        return Optional.ofNullable(revivalData).map(CompoundTag::copy);
    }

    public void setRevivalData(Entity entity) {
        TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING, entity.level().registryAccess());
        entity.saveWithoutId(output);
        revivalData = output.buildResult();
        revivalData.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        revivalData.store("MCAEntityUUID", UUIDUtil.CODEC, entity.getUUID());
        UUID spouse = relationshipState.isMarried() ? partner : widowedPartner;
        if (!spouse.equals(Util.NIL_UUID)) revivalData.store("MCAWidowedPartner", UUIDUtil.CODEC, spouse);
        if (entity instanceof VillagerEntityMCA person) {
            person.getResidency().getRegistryVillage().ifPresent(village -> {
                revivalData.putInt("MCARevivalVillage", village.getId());
                revivalData.putString("MCARevivalDimension", entity.level().dimension().identifier().toString());
            });
        }
        markDirty();
    }

    public boolean hasAppearanceData() {
        return appearanceData != null && !appearanceData.isEmpty();
    }

    public Optional<CompoundTag> getAppearanceData() {
        return Optional.ofNullable(appearanceData).map(CompoundTag::copy);
    }

    public void setAppearanceData(Entity entity) {
        if (!(entity instanceof VillagerLike<?>)) {
            return;
        }
        TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING, entity.level().registryAccess());
        entity.saveWithoutId(output);
        CompoundTag saved = output.buildResult()
                .getCompound(VillagerEntityMCA.MCA_DATA_KEY)
                .orElseGet(CompoundTag::new);
        CompoundTag appearance = new CompoundTag();
        List.of(
                "Clothes", "ClothingLocked", "Skin", "Hair", "HairStyle",
                "HairBase", "HairBangs", "HairBack", "HairFront", "HairExtra",
                "SkinColor", "HairColor", "EyeColor", "EyeColorLeft", "AgeState", "Gender"
        ).forEach(key -> {
            net.minecraft.nbt.Tag value = saved.get(key);
            if (value != null) {
                appearance.put(key, value.copy());
            }
        });
        saved.keySet().stream()
                .filter(key -> key.startsWith("Gene"))
                .forEach(key -> appearance.put(key, saved.get(key).copy()));
        appearanceData = appearance;
        markDirty();
    }

    public void clearRevivalData() {
        revivalData = null;
        markDirty();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        markDirty();
    }

    public VillagerProfession getProfession() {
        return BuiltInRegistries.VILLAGER_PROFESSION.getValue(getProfessionId());
    }

    public void setProfession(VillagerProfession profession) {
        this.profession = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession).toString();
        markDirty();
    }

    public Identifier getProfessionId() {
        return Identifier.tryParse(profession);
    }

    public String getProfessionName() {
        String professionName = (
                getProfessionId().getNamespace().equalsIgnoreCase("minecraft") ?
                        (getProfessionId().getPath().equals("none") ? "mca.none" : getProfessionId().getPath()) :
                        getProfessionId().toString()
        ).replace(":", ".");

        return MCA.isBlankString(professionName) ? "mca.none" : professionName;
    }

    public MutableComponent getProfessionText() {
        return Component.translatable("entity.minecraft.villager." + getProfessionName());
    }

    public boolean isPlayer() {
        return isPlayer;
    }

    public Gender gender() {
        return gender;
    }

    public UUID father() {
        return father;
    }

    public UUID mother() {
        return mother;
    }

    /**
     * Id of the last this entity's most recent partner.
     */
    public UUID partner() {
        return partner;
    }

    public RelationshipState getRelationshipState() {
        return relationshipState;
    }

    //debug usage only
    public void setRelationshipState(RelationshipState relationshipState) {
        this.relationshipState = relationshipState;
        if (relationshipState != RelationshipState.WIDOW) widowedPartner = Util.NIL_UUID;
        markDirty();
    }

    public void updatePartner(@Nullable Entity newPartner, @Nullable RelationshipState state) {
        if (newPartner == null && state == RelationshipState.WIDOW) rememberWidowedMarriage();
        if (newPartner != null || state != RelationshipState.WIDOW) widowedPartner = Util.NIL_UUID;
        //cancel relationship with previous partner
        if (!this.partner.equals(Util.NIL_UUID) && (newPartner == null || !this.partner.equals(newPartner.getUUID()))) {
            getRoot().getOrEmpty(this.partner).ifPresent(n -> {
                n.partner = Util.NIL_UUID;
                n.relationshipState = RelationshipState.SINGLE;
            });
        }

        this.partner = newPartner == null ? Util.NIL_UUID : newPartner.getUUID();
        this.relationshipState = state == null && newPartner == null ? RelationshipState.SINGLE : state;

        // ensure the family tree has an entry
        if (newPartner != null) {
            rootNode.getOrCreate(newPartner);
        }

        rootNode.setDirty();
    }

    public void updatePartner(FamilyTreeNode spouse) {
        widowedPartner = Util.NIL_UUID;
        this.partner = spouse.id();
        this.relationshipState = spouse.isPlayer ? RelationshipState.MARRIED_TO_PLAYER : RelationshipState.MARRIED_TO_VILLAGER;
        markDirty();
    }

    public Set<UUID> children() {
        return children;
    }

    public Stream<UUID> streamChildren() {
        return children.stream().filter(FamilyTreeNode::isValid)
                .filter(child -> rootNode.getOrEmpty(child).map(node -> node.isParent(id)).orElse(false));
    }

    public Stream<UUID> streamParents() {
        return Stream.of(father(), mother()).filter(FamilyTreeNode::isValid);
    }

    /**
     * All persons who share at least one common parent
     */
    public Set<UUID> siblings() {
        Set<UUID> siblings = new HashSet<>();

        streamParents().forEach(parent -> getRoot().getOrEmpty(parent).ifPresent(p -> gatherChildren(p, siblings, 1)));

        return siblings;
    }

    public Stream<UUID> getChildren() {
        return getRelatives(0, 1);
    }

    // returns indirect relatives like siblings and their respective family
    // potential slow for large families, getRelatives() is preferred if indirect family members are not relevant
    public Stream<UUID> getAllRelatives(int depth) {
        Set<UUID> family = new HashSet<>();

        //recursive family fetching
        Set<UUID> todo = new HashSet<>();
        todo.add(id);
        for (int d = 0; d < depth; d++) {
            Set<UUID> nextTodo = new HashSet<>();
            for (UUID uuid : todo) {
                if (!family.contains(uuid)) {
                    rootNode.getOrEmpty(uuid).ifPresent(node -> {
                        family.add(uuid);

                        //add parents and children
                        node.streamParents().forEach(nextTodo::add);
                        node.streamChildren().forEach(nextTodo::add);
                    });
                }
            }
            todo = nextTodo;
        }

        //the caller is not meant
        family.remove(id);

        return family.stream();
    }

    // returns all direct relatives (parents, grandparents, children, grandchildren)
    public Stream<UUID> getRelatives(int parentDepth, int childrenDepth) {
        Set<UUID> family = new HashSet<>();

        //fetch parents and children
        gatherParents(this, family, parentDepth);
        gatherChildren(this, family, childrenDepth);

        //and the caller is not meant either
        family.remove(id);

        return family.stream();
    }

    /** Family access includes spouses of relatives and the spouse's relatives. */
    public Stream<UUID> getFamilyMembers(int depth) {
        Set<UUID> family = new HashSet<>();
        family.add(id);
        getAllRelatives(depth).forEach(family::add);
        rootNode.getOrEmpty(partner).ifPresent(spouse -> {
            family.add(spouse.id());
            spouse.getAllRelatives(depth).forEach(family::add);
        });
        for (UUID relative : new HashSet<>(family)) {
            rootNode.getOrEmpty(relative).ifPresent(node -> {
                if (node.relationshipState.isMarried() && !node.partner.equals(Util.NIL_UUID)) family.add(node.partner);
            });
        }
        family.remove(id);
        return family.stream();
    }

    public boolean isFamilyMember(UUID with) {
        return with != null && (isRelative(with) || getFamilyMembers(6).anyMatch(with::equals));
    }

    public boolean isRelative(UUID with) {
        if (with == null) {
            return false;
        }
        return isBloodRelative(with);
    }

    public boolean isBloodRelative(UUID otherId) {
        if (otherId == null) {
            return false;
        }
        Set<UUID> myAncestors = new HashSet<>();
        gatherAncestors(this.id, myAncestors);

        Set<UUID> otherAncestors = new HashSet<>();
        gatherAncestors(otherId, otherAncestors);

        for (UUID ancestor : myAncestors) {
            if (otherAncestors.contains(ancestor)) {
                return true;
            }
        }
        return false;
    }

    private void gatherAncestors(UUID startId, Set<UUID> ancestors) {
        if (startId == null || ancestors.contains(startId)) {
            return;
        }
        ancestors.add(startId);
        getRoot().getOrEmpty(startId).ifPresent(node -> {
            node.streamParents().forEach(parentId -> gatherAncestors(parentId, ancestors));
        });
    }

    public Stream<FamilyTreeNode> getParents() {
        return lookup(streamParents());
    }

    /**
     * All persons who share at least one common parent
     */
    public Stream<FamilyTreeNode> getSiblings() {
        return lookup(siblings().stream());
    }

    public Stream<FamilyTreeNode> lookup(Stream<UUID> uuids) {
        return uuids.map(getRoot()::getOrEmpty).filter(Optional::isPresent).map(Optional::get);
    }

    public boolean isParent(UUID id) {
        return streamParents().anyMatch(parent -> parent.equals(id));
    }

    public boolean isGrandParent(UUID id) {
        return getParents().anyMatch(parent -> parent.isParent(id));
    }

    public boolean isUncle(UUID id) {
        return getParents().flatMap(parent -> parent.siblings().stream()).distinct().anyMatch(id::equals);
    }

    public void addChild(UUID child) {
        children.add(child);
    }

    public FamilyTree getRoot() {
        return rootNode;
    }

    public boolean replaceParents(EntityRelationship one, EntityRelationship two) {
        return replaceParents(Stream.of(one.getFamilyEntry(), two.getFamilyEntry()));
    }

    public boolean replaceParents(FamilyTreeNode one, Optional<FamilyTreeNode> two) {
        return replaceParents(Stream.concat(Stream.of(one), two.stream()));
    }

    public boolean replaceParents(Stream<FamilyTreeNode> parents) {
        boolean result = clearParents();
        return parents.map(this::assignParent).reduce(result, (changed, parentChanged) -> changed | parentChanged);
    }

    public boolean assignParent(FamilyTreeNode parent) {
        int parents = (isValid(father) ? 1 : 0) + (isValid(mother) ? 1 : 0);

        if (parents == 1) {
            //fill up last slot, independent on gender
            if (!isValid(father)) {
                return setFather(parent);
            } else if (!isValid(mother)) {
                return setMother(parent);
            }
        } else {
            //fill up gender respective slot
            if (parent.gender() == Gender.MALE) {
                return setFather(parent);
            } else {
                return setMother(parent);
            }
        }
        return true;
    }

    public boolean clearParents() {
        return removeFather() | removeMother();
    }

    private boolean canAssignParent(FamilyTreeNode parent) {
        if (parent.getRoot() != rootNode || parent.id().equals(id)) return false;
        var pending = new java.util.ArrayDeque<UUID>();
        var visited = new java.util.HashSet<UUID>();
        pending.add(parent.id());
        while (!pending.isEmpty()) {
            UUID ancestor = pending.removeFirst();
            if (ancestor.equals(id)) return false;
            if (visited.add(ancestor)) rootNode.getOrEmpty(ancestor)
                    .ifPresent(node -> node.streamParents().forEach(pending::addLast));
        }
        return true;
    }

    public boolean setFather(FamilyTreeNode parent) {
        if (!canAssignParent(parent) || parent.id().equals(mother)) return false;
        if (father.equals(parent.id())) { parent.children.add(id); markDirty(); return true; }
        removeFather();
        father = parent.id();
        parent.children().add(id);
        markDirty();
        return true;
    }

    public boolean setMother(FamilyTreeNode parent) {
        if (!canAssignParent(parent) || parent.id().equals(father)) return false;
        if (mother.equals(parent.id())) { parent.children.add(id); markDirty(); return true; }
        removeMother();
        mother = parent.id();
        parent.children().add(id);
        markDirty();
        return true;
    }

    public boolean removeFather() {
        if (isValid(father)) {
            rootNode.getOrEmpty(father).ifPresent(e -> e.children.remove(this.id));
            father = Util.NIL_UUID;
            markDirty();
            return true;
        } else {
            return false;
        }
    }

    public boolean removeMother() {
        if (isValid(mother)) {
            rootNode.getOrEmpty(mother).ifPresent(e -> e.children.remove(this.id));
            mother = Util.NIL_UUID;
            markDirty();
            return true;
        } else {
            return false;
        }
    }

    public void setGender(Gender gender) {
        this.gender = gender;
        markDirty();
    }

    // entries with these conditions are usually generated
    public boolean probablyGenerated() {
        return mother.equals(Util.NIL_UUID) && father.equals(Util.NIL_UUID) && children.size() == 1 && deceased && !isPlayer();
    }

    // true if there is at least one non-generated relative
    public boolean willBeRemembered() {
        if (!children.isEmpty()) {
            return true;
        }
        if (!partner.equals(Util.NIL_UUID)) {
            return true;
        }
        return !getParents().allMatch(FamilyTreeNode::probablyGenerated);
    }

    public void queueSurname(String surname) {
        pendingSurname = surname;
        setName(com.ancientkira.mca.entity.FamilyNames.fullName(
                com.ancientkira.mca.entity.FamilyNames.firstName(name), surname));
    }

    public String takePendingSurname() {
        String surname = pendingSurname;
        if (surname != null) { pendingSurname = null; markDirty(); }
        return surname;
    }

    public UUID lastRevivalRescuer() { return lastRevivalRescuer; }

    public void recordReviver(UUID rescuer) { lastRevivalRescuer = rescuer; markDirty(); }

    public net.minecraft.nbt.ListTag revivalThanks() { return revivalThanks; }

    public void queueRevivalThanks(UUID victim, UUID rescuer, int hearts) {
        CompoundTag event = new CompoundTag();
        event.store("victim", UUIDUtil.CODEC, victim);
        event.store("rescuer", UUIDUtil.CODEC, rescuer);
        event.putInt("hearts", hearts);
        revivalThanks.add(event);
        markDirty();
    }

    public CompoundTag save() {
        CompoundTag nbt = new CompoundTag();
        if (pendingSurname != null) nbt.putString("pendingSurname", pendingSurname);
        if (lastRevivalRescuer != null) nbt.store("lastRevivalRescuer", UUIDUtil.CODEC, lastRevivalRescuer);
        nbt.put("revivalThanks", revivalThanks.copy());
        nbt.putString("name", name);
        nbt.store("id", UUIDUtil.CODEC, id);
        nbt.putBoolean("isPlayer", isPlayer);
        nbt.putBoolean("isDeceased", deceased);
        nbt.putInt("gender", gender.getId());
        nbt.store("father", UUIDUtil.CODEC, father);
        nbt.store("mother", UUIDUtil.CODEC, mother);
        nbt.store("spouse", UUIDUtil.CODEC, partner);
        nbt.store("widowedPartner", UUIDUtil.CODEC, widowedPartner);
        nbt.putInt("marriageState", relationshipState.ordinal());
        nbt.putString("profession", profession);
        if (revivalData != null) {
            nbt.put("revivalData", revivalData.copy());
        }
        if (appearanceData != null) {
            nbt.put("appearanceData", appearanceData.copy());
        }
        nbt.put("children", NbtHelper.fromList(children, child -> {
            CompoundTag n = new CompoundTag();
            n.store("uuid", UUIDUtil.CODEC, child);
            return n;
        }));
        return nbt;
    }
}
