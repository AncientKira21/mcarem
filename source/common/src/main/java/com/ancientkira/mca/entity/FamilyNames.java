package com.ancientkira.mca.entity;

import com.ancientkira.mca.entity.ai.relationship.EntityRelationship;
import com.ancientkira.mca.resources.Names;
import com.ancientkira.mca.server.world.data.PlayerSaveData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import java.util.UUID;

/** Full names remain in the existing synced custom-name and family-tree fields. */
public final class FamilyNames {
    private static final String[] SURNAMES = {"Ashford", "Baker", "Blackwood", "Brook", "Carter", "Cooper",
            "Fairweather", "Fletcher", "Greenwood", "Hawthorne", "Hill", "Lark", "Miller", "Oakley",
            "Reed", "Rivers", "Shepherd", "Smith", "Stone", "Thorne", "Walker", "Wells", "Willow", "Wood"};
    private FamilyNames() { }
    public static NameParts parts(Entity person) {
        String full = person.getCustomName() == null ? person.getName().getString() : person.getCustomName().getString();
        if (person instanceof VillagerLike<?> villager) {
            return NameParts.resolve(full, villager.getTrackedValue(VillagerLike.FIRST_NAME), villager.getTrackedValue(VillagerLike.LAST_NAME));
        }
        if (person.level() instanceof net.minecraft.server.level.ServerLevel level) {
            var node = com.ancientkira.mca.server.world.data.FamilyTree.get(level).getOrEmpty(person.getUUID());
            if (node.isPresent() && node.get().getName().equals(full)) return new NameParts(node.get().getFirstName(), node.get().getSurname());
        }
        return NameParts.resolve(full, "", "");
    }
    public static void setParts(Entity person, String first, String last) {
        if (person instanceof VillagerLike<?> villager) {
            villager.setTrackedValue(VillagerLike.SURNAME_DISABLED, com.ancientkira.mca.entity.NameParts.disablesSurname(last));
            villager.setTrackedValue(VillagerLike.FIRST_NAME, first.strip());
            villager.setTrackedValue(VillagerLike.LAST_NAME, last.strip());
        }
    }
    public static String firstName(Entity person) { return parts(person).first(); }
    public static String lastName(Entity person) { return surnameDisabled(person) ? " " : parts(person).last(); }
    public static boolean surnameDisabled(Entity person) {
        if (person instanceof VillagerLike<?> v) return v.getTrackedValue(VillagerLike.SURNAME_DISABLED);
        if (person.level() instanceof net.minecraft.server.level.ServerLevel level)
            return com.ancientkira.mca.server.world.data.FamilyTree.get(level).getOrEmpty(person.getUUID())
                    .map(com.ancientkira.mca.server.world.data.FamilyTreeNode::isSurnameDisabled).orElse(false);
        return false;
    }

    public static String firstName(String full) {
        String name = full == null ? "" : full.strip();
        int split = name.indexOf(' '); return split < 0 ? name : name.substring(0, split);
    }
    public static boolean matchesName(String full, String query, boolean ignoreCase) {
        if (full == null || query == null) return false;
        return ignoreCase ? full.equalsIgnoreCase(query) || firstName(full).equalsIgnoreCase(query)
                : full.equals(query) || firstName(full).equals(query);
    }
    public static String nameTag(String displayed, String fullName) {
        return nameTag(displayed, fullName, firstName(fullName));
    }
    public static String nameTag(String displayed, String fullName, String first) {
        if (fullName == null || fullName.isBlank() || !displayed.startsWith(fullName)) return displayed;
        return first + displayed.substring(fullName.length());
    }
    public static String lastName(String full) {
        String name = full == null ? "" : full.strip();
        int split = name.indexOf(' '); return split < 0 ? "" : name.substring(split + 1).strip();
    }
    public static String fullName(String first, String last) {
        return (first.strip() + " " + last.strip()).strip();
    }
    public static String generated(String first, UUID family) {
        return fullName(first, SURNAMES[Math.floorMod(family.hashCode(), SURNAMES.length)]);
    }
    public static void setSurname(Entity person, String surname) {
        if (person.level().isClientSide()) return;
        String first = firstName(person);
        String full = fullName(first, surname);
        setParts(person, first, surname);
        person.setCustomName(Component.literal(full));
        EntityRelationship.of(person).ifPresent(r -> r.getFamilyEntry().setNameParts(first, surname));
        if (person instanceof net.minecraft.server.level.ServerPlayer player) {
            var data = PlayerSaveData.get(player);
            var saved = data.getEntityData().copy();
            saved.store("CustomName", net.minecraft.network.chat.ComponentSerialization.CODEC,
                    player.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), Component.literal(full));
            data.setEntityData(saved);
            player.setCustomName(Component.literal(full));
            var mca = com.ancientkira.mca.util.NbtHelper.getOrCreateCompound(saved, VillagerEntityMCA.MCA_DATA_KEY);
            mca.putString("FirstName", first); mca.putString("LastName", surname.strip());
            mca.putBoolean("SurnameDisabled", com.ancientkira.mca.entity.NameParts.disablesSurname(surname));
            data.setEntityData(saved);
            data.getFamilyEntry().setNameParts(first, surname);
            for (var viewer : player.level().getServer().getPlayerList().getPlayers()) {
                com.ancientkira.mca.network.Network.sendToPlayer(new com.ancientkira.mca.network.s2c.PlayerDataMessage(player.getUUID(), saved), viewer);
            }
        }
    }
    /** Rename a family line and its households, without crossing into spouses' ancestry. */
    public static void changeFamilySurname(Entity person, String surname) {
        changeFamilySurname(person, lastName(person), surname);
    }

    public static void changeFamilySurname(Entity person, String previousSurname, String surname) {
        if (!(person.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        if (surname.isEmpty()) surname = lastName(generated("Family", UUID.randomUUID()));
        final String chosenSurname = surname;
        var tree = com.ancientkira.mca.server.world.data.FamilyTree.get(level);
        tree.rebuildChildLinks();
        var ancestors = new java.util.ArrayDeque<UUID>();
        var roots = new java.util.HashSet<UUID>();
        ancestors.add(person.getUUID());
        while (!ancestors.isEmpty()) {
            UUID id = ancestors.removeFirst();
            if (!roots.add(id)) continue;
            tree.getOrEmpty(id).ifPresent(node -> node.streamParents().forEach(parentId ->
                    tree.getOrEmpty(parentId).ifPresent(parent -> {
                        String parentSurname = parent.getSurname();
                        if (parentSurname.isBlank() || parentSurname.equals(previousSurname) || parentSurname.equals(chosenSurname)) {
                            ancestors.addLast(parentId);
                        }
                    })));
        }
        var pending = new java.util.ArrayDeque<UUID>(roots);
        var bloodFamily = new java.util.HashSet<UUID>();
        var rename = new java.util.HashSet<UUID>();
        while (!pending.isEmpty()) {
            UUID id = pending.removeFirst();
            if (!bloodFamily.add(id)) continue;
            tree.getOrEmpty(id).ifPresent(node -> {
                rename.add(id);
                node.streamChildren().forEach(pending::addLast);
                // Include spouses, but their parents and other children belong to another family line.
                tree.getOrEmpty(node.partner()).filter(spouse -> spouse.partner().equals(id))
                        .ifPresent(spouse -> rename.add(spouse.id()));
            });
        }
        for (UUID id : rename) {
            tree.getOrEmpty(id).ifPresent(node -> node.queueSurname(chosenSurname));
            Entity loaded = level.getServer().getPlayerList().getPlayer(id);
            if (loaded == null) {
                for (var world : level.getServer().getAllLevels()) {
                    loaded = world.getEntity(id);
                    if (loaded != null) break;
                }
            }
            if (loaded != null) applyPendingSurname(loaded);
        }
    }

    /** Backfill connected families once, preserving every surname already chosen. */
    public static void fillMissingSurnames(com.ancientkira.mca.server.world.data.FamilyTree tree) {
        var visited = new java.util.HashSet<UUID>();
        for (var node : tree.getAllEntries().toList()) {
            if (!visited.contains(node.id())) fillMissingFamilySurname(tree, node.id(), visited);
        }
    }

    private static void fillMissingFamilySurname(com.ancientkira.mca.server.world.data.FamilyTree tree,
                                                 UUID root, java.util.Set<UUID> visited) {
        var pending = new java.util.ArrayDeque<UUID>();
        var family = new java.util.ArrayList<com.ancientkira.mca.server.world.data.FamilyTreeNode>();
        pending.add(root);
        while (!pending.isEmpty()) {
            UUID id = pending.removeFirst();
            if (!visited.add(id)) continue;
            tree.getOrEmpty(id).ifPresent(node -> {
                family.add(node);
                node.streamParents().forEach(pending::addLast);
                node.streamChildren().forEach(pending::addLast);
                if (!node.partner().equals(net.minecraft.util.Util.NIL_UUID)) pending.addLast(node.partner());
                if (!node.getWidowedPartner().equals(net.minecraft.util.Util.NIL_UUID)) pending.addLast(node.getWidowedPartner());
            });
        }
        if (family.stream().noneMatch(node -> !node.isPlayer() && !node.isSurnameDisabled() && node.getSurname().isBlank())) return;
        String surname = family.stream().sorted(java.util.Comparator.comparing(node -> node.id()))
                .map(node -> node.getSurname()).filter(name -> !name.isBlank()).findFirst()
                .orElseGet(() -> lastName(generated("Family", UUID.randomUUID())));
        for (var node : family) {
            if (!node.isPlayer() && !node.isSurnameDisabled() && node.getSurname().isBlank()) node.queueSurname(surname);
        }
    }

    public static void applyPendingSurname(Entity person) {
        if (!(person.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        com.ancientkira.mca.server.world.data.FamilyTree.get(level).getOrEmpty(person.getUUID()).ifPresent(node -> {
            if (!node.isPlayer() && !node.isSurnameDisabled() && node.getSurname().isBlank()) {
                fillMissingFamilySurname(node.getRoot(), node.id(), new java.util.HashSet<>());
            }
            String surname = node.takePendingSurname();
            if (surname != null) setSurname(person, surname);
        });
    }

    public static void shareSurname(EntityRelationship relationship, Entity spouse) {
        if (spouse.level().isClientSide()) return;
        var own = relationship.getFamilyEntry();
        var other = relationship.getFamilyTree().getOrCreate(spouse);
        String surname;
        if (own.isPlayer()) surname = own.getSurname();
        else if (other.isPlayer()) surname = other.getSurname();
        else surname = (own.id().compareTo(other.id()) <= 0 ? own : other).getSurname();
        if (surname.isBlank()) surname = own.getSurname();
        if (surname.isBlank()) surname = other.getSurname();
        if (surname.isBlank()) surname = lastName(generated("Family", own.id().compareTo(other.id()) <= 0 ? own.id() : other.id()));
        String shared = own.isSurnameDisabled() || other.isSurnameDisabled() ? " " : surname;
        setSurname(spouse, shared);
        for (var world : spouse.level().getServer().getAllLevels()) {
            Entity self = world.getEntity(own.id());
            if (self != null) { setSurname(self, shared); break; }
        }
        own.setNameParts(own.getFirstName(), shared);
        other.setNameParts(other.getFirstName(), shared);
    }
    public static void inheritSurname(VillagerEntityMCA child) {
        if (child.level().isClientSide()) return;
        var entry = child.getRelationships().getFamilyEntry();
        if (entry.streamParents().map(child.getRelationships().getFamilyTree()::getOrEmpty)
                .flatMap(java.util.Optional::stream).anyMatch(com.ancientkira.mca.server.world.data.FamilyTreeNode::isSurnameDisabled)) {
            setSurname(child, " "); return;
        }
        entry.streamParents().map(child.getRelationships().getFamilyTree()::getOrEmpty)
                .flatMap(java.util.Optional::stream).map(parent -> parent.getSurname())
                .filter(name -> !name.isBlank()).findFirst().ifPresent(surname -> {
                    if (child.getCustomName() == null) child.setCustomName(Component.literal(Names.pickCitizenName(child.getGenetics().getGender(), child)));
                    setSurname(child, surname);
                });
    }
}
