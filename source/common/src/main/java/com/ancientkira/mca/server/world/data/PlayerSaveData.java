package com.ancientkira.mca.server.world.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.ancientkira.mca.Config;
import com.ancientkira.mca.datafix.McaDataFixers;
import com.ancientkira.mca.entity.PlayerDimensions;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.relationship.EntityRelationship;
import com.ancientkira.mca.entity.ai.relationship.Gender;
import com.ancientkira.mca.entity.ai.relationship.RelationshipState;
import com.ancientkira.mca.entity.ai.relationship.RelationshipType;
import com.ancientkira.mca.network.Network;
import com.ancientkira.mca.network.s2c.ShowToastRequest;
import com.ancientkira.mca.registry.CriterionMCA;
import com.ancientkira.mca.registry.DataComponentsMCA;
import com.ancientkira.mca.registry.EntitiesMCA;
import com.ancientkira.mca.registry.ItemsMCA;
import com.ancientkira.mca.resources.API;
import com.ancientkira.mca.resources.Rank;
import com.ancientkira.mca.resources.Tasks;
import com.ancientkira.mca.util.NbtHelper;
import com.ancientkira.mca.util.WorldUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PlayerSaveData extends SavedData implements EntityRelationship {
    private final Map<String,Long> socialInvitations = new HashMap<>();
    private long nextInvitationLetter;
    /** Shared, persisted cap and delivery spacing across social invitation types. */
    public boolean reserveInvitation(String kind,UUID sender,long expires) {
        long now=world.getServer().overworld().getGameTime();
        if(!Config.getInstance().enableVillagerMailingPlayers || now<nextInvitationLetter)return false;
        socialInvitations.entrySet().removeIf(e->e.getValue()<=now);
        Set<String> people=new HashSet<>();
        socialInvitations.forEach((key,end)->people.add(key.substring(key.indexOf(':')+1)));
        marriageProposals.forEach((id,end)->{if(end>now)people.add(id.toString());});
        familyRequests.forEach((key,end)->{if(end>now)people.add(key.substring(key.indexOf(':')+1));});
        if(people.size()>=3 || people.contains(sender.toString()))return false;
        socialInvitations.put(kind+":"+sender,expires);nextInvitationLetter=now+6000;setDirty();return true;
    }
    public void finishInvitation(String kind,UUID sender){socialInvitations.remove(kind+":"+sender);setDirty();}
    private final Map<String, Long> familyRequests = new HashMap<>();
    public Map<String, Long> getFamilyRequests() { return familyRequests; }
    public void updateFamilyRequest(String key, Long expires) {
        if (expires == null) {familyRequests.remove(key);socialInvitations.remove(key);} else familyRequests.put(key, expires);
        setDirty();
    }
    private final Set<UUID> medicalPolicies = new HashSet<>();
    public boolean hasMedicalPolicy(UUID cleric) { return medicalPolicies.contains(cleric); }
    public void insureWith(UUID cleric) { medicalPolicies.add(cleric); setDirty(); }
    private boolean welcome820Shown;
    public boolean markWelcome820Shown() {
        if (welcome820Shown) return false;
        welcome820Shown = true;
        setDirty();
        return true;
    }
    private boolean befriendedVillager;
    private boolean raiderTruce;
    private final Set<UUID> contactedRaiders = new HashSet<>();

    public boolean hasBefriendedVillager() { return befriendedVillager; }
    public boolean hasContactedRaider(UUID raider) { return raiderTruce || contactedRaiders.contains(raider); }
    public void recordVillagerFriendship() { befriendedVillager = true; setDirty(); }
    public void recordRaiderContact(UUID raider) { raiderTruce = true; contactedRaiders.add(raider); setDirty(); }
    public boolean hasRaiderTruce() { return raiderTruce; }

    private ServerLevel world;
    private final UUID uuid;
    private final List<Letter> inbox = new LinkedList<>();
    private final Map<UUID, Long> marriageProposals = new HashMap<>();

    public Map<UUID, Long> getMarriageProposals() { return marriageProposals; }
    public void updateMarriageProposal(UUID sender, Long expires) {
        if (expires == null) {marriageProposals.remove(sender);socialInvitations.remove("marriage:"+sender);}
        else marriageProposals.put(sender, expires);
        setDirty();
    }
    private @Nullable Integer lastSeenVillageId;
    private @Nullable Integer registryVillageId;
    private String registryDimension = "minecraft:overworld";
    private boolean entityDataSet;
    private boolean overrideVillageRequirements = false;
    private String chatAIPrompt = "";
    private CompoundTag entityData;
    private PlayerDimensions.Scale dimensionsScale;

    PlayerSaveData(ServerLevel world, UUID uuid) {
        this.world = world;
        this.uuid = uuid;

        resetEntityData();
    }

    PlayerSaveData(ServerLevel world, UUID uuid, CompoundTag nbt) {
        this.world = world;
        this.uuid = uuid;

        CompoundTag invitations=nbt.getCompound("socialInvitations").orElseGet(CompoundTag::new);
        invitations.keySet().forEach(key->socialInvitations.put(key,invitations.getLongOr(key,0)));
        nextInvitationLetter=nbt.getLongOr("nextInvitationLetter",0);
        CompoundTag requests = nbt.getCompound("familyRequests").orElseGet(CompoundTag::new);
        requests.keySet().forEach(key -> familyRequests.put(key, requests.getLongOr(key, 0)));
        CompoundTag policies = nbt.getCompound("medicalPolicies").orElseGet(CompoundTag::new);
        for (String id : policies.keySet()) { try { medicalPolicies.add(UUID.fromString(id)); } catch (IllegalArgumentException ignored) {} }
        welcome820Shown = nbt.getBooleanOr("welcome820Shown", false);
        befriendedVillager = nbt.getBooleanOr("befriendedVillager", false);
        CompoundTag contacts = nbt.getCompound("contactedRaiders").orElseGet(CompoundTag::new);
        for (String key : contacts.keySet()) {
            try { contactedRaiders.add(UUID.fromString(key)); }
            catch (IllegalArgumentException ignored) { }
        }
        raiderTruce = nbt.getBooleanOr("raiderTruce", !contactedRaiders.isEmpty());
        lastSeenVillageId = nbt.getInt("lastSeenVillage").orElse(null);
        registryVillageId = nbt.getInt("registryVillage").orElse(lastSeenVillageId);
        registryDimension = nbt.getStringOr("registryDimension", "minecraft:overworld");
        entityDataSet = nbt.contains("entityDataSet") && nbt.getBoolean("entityDataSet").orElse(false);
        overrideVillageRequirements = nbt.contains("overrideVillageRequirements") && nbt.getBoolean("overrideVillageRequirements").orElse(false);
        chatAIPrompt = nbt.getString("chatAIPrompt").orElse("");

        Optional<CompoundTag> storedEntityData = nbt.getCompound("entityData");
        if (storedEntityData.isPresent()) {
            CompoundTag stored = storedEntityData.get();
            entityData = normalizeEntityData(stored);
            if (!entityData.equals(stored)) {
                setDirty();
            }
        } else {
            resetEntityData();
        }

        CompoundTag proposals = nbt.getCompound("marriageProposals").orElseGet(CompoundTag::new);
        for (String key : proposals.keySet()) {
            try { marriageProposals.put(UUID.fromString(key), proposals.getLongOr(key, 0L)); }
            catch (IllegalArgumentException ignored) { }
        }
        ListTag inbox = nbt.getList("inbox").orElseGet(ListTag::new);
        this.inbox.addAll(NbtHelper.toList(inbox, e -> new Letter((CompoundTag) e, world.registryAccess())));
    }

    public static PlayerSaveData get(ServerPlayer player) {
        PlayerSaveData data = get(player.level(), player.getUUID());
        data.world = player.level();
        return data;
    }

    public static PlayerSaveData get(ServerLevel world, UUID uuid) {
        return WorldUtils.loadData(world.getServer().overworld(), (nbt, provider) -> new PlayerSaveData(world, uuid, nbt), w -> new PlayerSaveData(world, uuid), "mca_player_" + uuid);
    }

    public static Optional<PlayerSaveData> getIfPresent(ServerLevel world, UUID uuid) {
        return WorldUtils.loadDataIfPresent(
                world.getServer().overworld(),
                (nbt, provider) -> new PlayerSaveData(world, uuid, nbt),
                "mca_player_" + uuid
        );
    }

    public static void showMailNotification(ServerPlayer player) {
        Network.sendToPlayer(new ShowToastRequest(
                "server.mail.title",
                "server.mail.description"
        ), player);
    }

    private void resetEntityData() {
        entityData = new CompoundTag();

        VillagerEntityMCA villager = EntitiesMCA.PERSON.create(world, EntitySpawnReason.LOAD);
        assert villager != null;
        villager.initializeSkin(true);
        villager.getGenetics().randomize();
        villager.getTraits().randomize();
        villager.getVillagerBrain().randomize();
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, world.registryAccess());
        villager.saveWithoutId(output);
        entityData = output.buildResult();
    }

    public boolean isEntityDataSet() {
        return entityDataSet;
    }

    public void setEntityDataSet(boolean entityDataSet) {
        if (this.entityDataSet == entityDataSet) {
            return;
        }
        this.entityDataSet = entityDataSet;
        setDirty();
        refreshPlayerDimensions();
    }

    public CompoundTag getEntityData() {
        return entityData.copy();
    }

    public PlayerDimensions.Scale getDimensionsScale() {
        if (dimensionsScale == null) {
            dimensionsScale = PlayerDimensions.fromEntityData(entityData);
        }
        return dimensionsScale;
    }

    public void setEntityData(CompoundTag entityData) {
        CompoundTag copy = normalizeEntityData(entityData);
        if (copy.equals(this.entityData)) {
            return;
        }
        this.entityData = copy;
        dimensionsScale = PlayerDimensions.fromEntityData(copy);
        setDirty();
        refreshPlayerDimensions();
    }

    private static CompoundTag normalizeEntityData(CompoundTag entityData) {
        return McaDataFixers.update(entityData.copy());
    }

    private void refreshPlayerDimensions() {
        if (world.getPlayerByUUID(uuid) instanceof ServerPlayer player) {
            player.refreshDimensions();
        }
    }

    @Override
    public void onTragedy(DamageSource cause, @Nullable BlockPos burialSite, RelationshipType type, Entity victim) {
        EntityRelationship.super.onTragedy(cause, burialSite, type, victim);

        // send letter of condolence
        if (victim instanceof VillagerEntityMCA victimVillager) {
            sendLetterOfCondolence(victimVillager.getName().getString(),
                    victimVillager.getResidency().getHomeVillage().map(Village::getName).orElse(API.getVillagePool().pickVillageName("village")));
        }
    }

    public void updateLastSeenVillage(VillageManager manager, ServerPlayer self) {
        Optional<Village> prevVillage = getLastSeenVillage(manager);
        Optional<Village> nextVillage = prevVillage
                .filter(v -> v.isWithinBorder(self))
                .or(() -> manager.findNearestVillage(self));

        setLastSeenVillage(self, prevVillage.orElse(null), nextVillage.orElse(null));

        // village rank advancement
        if (nextVillage.isPresent()) {
            Rank rank = Tasks.getRank(nextVillage.get(), self);
            CriterionMCA.RANK.trigger(self, rank);
        }
    }

    public void setLastSeenVillage(ServerPlayer self, Village oldVillage, @Nullable Village newVillage) {
        lastSeenVillageId = newVillage != null ? newVillage.getId() : null;
        if (newVillage != null) {
            registryVillageId = newVillage.getId();
            registryDimension = self.level().dimension().identifier().toString();
        }
        setDirty();

        if (oldVillage != newVillage) {
            if (oldVillage != null) {
                onLeave(self, oldVillage);
            }
            if (newVillage != null) {
                onEnter(self, newVillage);
            }
        }
    }

    public Optional<Village> getLastSeenVillage(VillageManager manager) {
        return Optional.ofNullable(lastSeenVillageId).flatMap(manager::getOrEmpty);
    }

    public Optional<Village> getRegistryVillage(ServerLevel currentWorld) {
        var dimension = net.minecraft.resources.Identifier.tryParse(registryDimension);
        ServerLevel registryWorld = dimension == null ? currentWorld : currentWorld.getServer().getLevel(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimension));
        Optional<Village> remembered = registryWorld == null ? Optional.empty()
                : Optional.ofNullable(registryVillageId).flatMap(VillageManager.get(registryWorld)::getOrEmpty);
        return remembered.or(() -> getLastSeenVillage(VillageManager.get(currentWorld))).or(() -> {
            var player = currentWorld.getServer().getPlayerList().getPlayer(uuid);
            Optional<Village> nearest = VillageManager.get(currentWorld).findVillages(village -> true)
                    .min(java.util.Comparator.comparingDouble(village -> player == null ? village.getId()
                            : village.getCenter().distSqr(player.blockPosition())));
            nearest.ifPresent(village -> {
                registryVillageId = village.getId();
                registryDimension = currentWorld.dimension().identifier().toString();
                setDirty();
            });
            return nearest;
        });
    }

    public Optional<Integer> getLastSeenVillageId() {
        return Optional.ofNullable(lastSeenVillageId);
    }

    protected void onLeave(ServerPlayer self, Village village) {
        if (Config.getInstance().enterVillageNotification && !village.getBuildings().isEmpty()) {
            self.sendSystemMessage(Component.translatable("gui.village.left", village.getName()).withStyle(ChatFormatting.GOLD), true);
        }
    }

    protected void onEnter(ServerPlayer self, Village village) {
        if (Config.getInstance().enterVillageNotification && !village.getBuildings().isEmpty()) {
            self.sendSystemMessage(Component.translatable("gui.village.welcome", village.getName()).withStyle(ChatFormatting.GOLD), true);
        }
        village.onEnter(world);
    }

    @Override
    public void marry(Entity spouse) {
        EntityRelationship.super.marry(spouse);
        setDirty();
    }

    @Override
    public void endRelationShip(RelationshipState newState) {
        EntityRelationship.super.endRelationShip(newState);
        setDirty();
    }

    @Override
    public ServerLevel getWorld() {
        return world;
    }

    @Override
    public UUID getUUID() {
        return uuid;
    }

    @Override
    public Gender getGender() {
        CompoundTag mcaData = NbtHelper.getCompoundOrSelf(entityData, VillagerEntityMCA.MCA_DATA_KEY);
        if (mcaData.contains("Gender")) {
            return Gender.byId(mcaData.getInt("Gender").orElse(Gender.UNASSIGNED.ordinal()));
        }
        if (entityData.contains("gender")) {
            return Gender.byId(entityData.getInt("gender").orElse(Gender.UNASSIGNED.ordinal()));
        }
        return Gender.UNASSIGNED;
    }

    @Override
    public @NotNull FamilyTreeNode getFamilyEntry() {
        return getFamilyTree().getOrEmpty(uuid).orElseGet(() -> {
            String name = Optional.ofNullable(world.getPlayerByUUID(uuid)).map(p -> p.getName().getString()).orElse("Unnamed Adventurer");
            return getFamilyTree().getOrCreate(uuid, name, getGender(), true);
        });
    }

    public void reset() {
        endRelationShip(RelationshipState.SINGLE);
        setDirty();
    }

    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        CompoundTag policies = new CompoundTag();
        medicalPolicies.forEach(id -> policies.putBoolean(id.toString(), true));
        nbt.put("medicalPolicies", policies);
        if (lastSeenVillageId != null) {
            nbt.putInt("lastSeenVillage", lastSeenVillageId);
        }
        if (registryVillageId != null) nbt.putInt("registryVillage", registryVillageId);
        nbt.putString("registryDimension", registryDimension);
        CompoundTag invitations=new CompoundTag();socialInvitations.forEach(invitations::putLong);
        nbt.put("socialInvitations",invitations);nbt.putLong("nextInvitationLetter",nextInvitationLetter);
        CompoundTag requests = new CompoundTag();
        familyRequests.forEach(requests::putLong);
        nbt.put("familyRequests", requests);
        nbt.putBoolean("welcome820Shown", welcome820Shown);
        nbt.putBoolean("befriendedVillager", befriendedVillager);
        nbt.putBoolean("raiderTruce", raiderTruce);
        CompoundTag contacts = new CompoundTag();
        contactedRaiders.forEach(id -> contacts.putBoolean(id.toString(), true));
        nbt.put("contactedRaiders", contacts);
        CompoundTag storedEntityData = entityData.copy();
        McaDataFixers.stampCurrentVersion(storedEntityData);
        nbt.put("entityData", storedEntityData);
        nbt.putBoolean("entityDataSet", entityDataSet);
        nbt.putBoolean("overrideVillageRequirements", overrideVillageRequirements);
        nbt.putString("chatAIPrompt", chatAIPrompt);
        nbt.put("inbox", NbtHelper.fromList(inbox, v -> v.toTag(provider)));
        CompoundTag proposals = new CompoundTag();
        marriageProposals.forEach((sender, expires) -> proposals.putLong(sender.toString(), expires));
        nbt.put("marriageProposals", proposals);
        return nbt;
    }

    public String getChatAIPrompt() { return chatAIPrompt; }

    public void setChatAIPrompt(String chatAIPrompt) {
        this.chatAIPrompt = chatAIPrompt;
        setDirty();
    }

    public void sendMail(Letter pages) {
        if (Config.getInstance().enableVillagerMailingPlayers) {
            inbox.add(pages);
        }
        setDirty();
    }

    public boolean hasMail() {
        return !inbox.isEmpty();
    }

    public ItemStack getMail() {
        if (hasMail()) {
            // Letters are saved oldest-to-newest; collect the newest pending letter first.
            Letter letter = inbox.removeLast();
            setDirty();
            ItemStack stack = new ItemStack(ItemsMCA.LETTER, 1);
            stack.set(DataComponentsMCA.BOOK_PAGES, letter.pages());
            return stack;
        } else {
            return null;
        }
    }

    public void sendLetterOfCondolence(String name, String village) {
        sendLetter(Component.translatable("mca.letter.condolence", getFamilyEntry().getName(), name, village));
    }

    public void sendLetter(Component... lines) {
        sendMail(new Letter("", Arrays.asList(lines)));
        Optional.ofNullable(world.getPlayerByUUID(uuid)).ifPresent(p -> showMailNotification((ServerPlayer) p));
    }

    public record Letter(String title, List<Component> pages) {
        private static final Codec<List<Component>> PAGES_CODEC = ComponentSerialization.CODEC.listOf();

        public Letter(CompoundTag nbt, HolderLookup.Provider registries) {
            this(
                    nbt.getString("title").orElse(""),
                    nbt.read("pages", PAGES_CODEC, registries.createSerializationContext(NbtOps.INSTANCE))
                            .orElse(List.of())
            );
        }

        CompoundTag toTag(HolderLookup.Provider registries) {
            CompoundTag nbt = new CompoundTag();
            nbt.putString("title", title);

            DynamicOps<Tag> dynamicOps = registries.createSerializationContext(NbtOps.INSTANCE);
            nbt.store("pages", PAGES_CODEC, dynamicOps, pages);

            return nbt;
        }
    }

    public boolean isOverrideVillageRequirements() {
        return overrideVillageRequirements;
    }

    public void setOverrideVillageRequirements(boolean overrideVillageRequirements) {
        if (this.overrideVillageRequirements != overrideVillageRequirements) {
            this.overrideVillageRequirements = overrideVillageRequirements;
            setDirty();
        }
    }
}
