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
        if (fullName == null || fullName.isBlank() || !displayed.startsWith(fullName)) return displayed;
        return firstName(fullName) + displayed.substring(fullName.length());
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
        String full = fullName(firstName(person.getCustomName() == null ? person.getName().getString()
                : person.getCustomName().getString()), surname);
        person.setCustomName(Component.literal(full));
        EntityRelationship.of(person).ifPresent(r -> r.getFamilyEntry().setName(full));
        if (person instanceof net.minecraft.server.level.ServerPlayer player) {
            var data = PlayerSaveData.get(player);
            var saved = data.getEntityData().copy();
            saved.store("CustomName", net.minecraft.network.chat.ComponentSerialization.CODEC,
                    player.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), Component.literal(full));
            data.setEntityData(saved);
            for (var viewer : player.level().getServer().getPlayerList().getPlayers()) {
                com.ancientkira.mca.network.Network.sendToPlayer(new com.ancientkira.mca.network.s2c.PlayerDataMessage(player.getUUID(), saved), viewer);
            }
        }
    }
    /** Rename a family line and its households, without crossing into spouses' ancestry. */
    public static void changeFamilySurname(Entity person, String surname) {
        changeFamilySurname(person, lastName(person.getName().getString()), surname);
    }

    public static void changeFamilySurname(Entity person, String previousSurname, String surname) {
        if (!(person.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
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
                        String parentSurname = lastName(parent.getName());
                        if (parentSurname.isBlank() || parentSurname.equals(previousSurname) || parentSurname.equals(surname)) {
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
            tree.getOrEmpty(id).ifPresent(node -> node.queueSurname(surname));
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
        if (family.stream().noneMatch(node -> !node.isPlayer() && lastName(node.getName()).isBlank())) return;
        String surname = family.stream().sorted(java.util.Comparator.comparing(node -> node.id()))
                .map(node -> lastName(node.getName())).filter(name -> !name.isBlank()).findFirst()
                .orElseGet(() -> lastName(generated("Family", UUID.randomUUID())));
        for (var node : family) {
            if (!node.isPlayer() && lastName(node.getName()).isBlank()) node.queueSurname(surname);
        }
    }

    public static void applyPendingSurname(Entity person) {
        if (!(person.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        com.ancientkira.mca.server.world.data.FamilyTree.get(level).getOrEmpty(person.getUUID()).ifPresent(node -> {
            if (!node.isPlayer() && lastName(node.getName()).isBlank()) {
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
        if (own.isPlayer()) surname = lastName(own.getName());
        else if (other.isPlayer()) surname = lastName(other.getName());
        else surname = lastName((own.id().compareTo(other.id()) <= 0 ? own : other).getName());
        if (surname.isBlank()) surname = lastName(own.getName());
        if (surname.isBlank()) surname = lastName(other.getName());
        if (surname.isBlank()) surname = lastName(generated("Family", own.id().compareTo(other.id()) <= 0 ? own.id() : other.id()));
        String shared = surname;
        setSurname(spouse, shared);
        for (var world : spouse.level().getServer().getAllLevels()) {
            Entity self = world.getEntity(own.id());
            if (self != null) { setSurname(self, shared); break; }
        }
        own.setName(fullName(firstName(own.getName()), shared));
        other.setName(fullName(firstName(other.getName()), shared));
    }
    public static void inheritSurname(VillagerEntityMCA child) {
        if (child.level().isClientSide()) return;
        var entry = child.getRelationships().getFamilyEntry();
        entry.streamParents().map(child.getRelationships().getFamilyTree()::getOrEmpty)
                .flatMap(java.util.Optional::stream).map(parent -> lastName(parent.getName()))
                .filter(name -> !name.isBlank()).findFirst().ifPresent(surname -> {
                    if (child.getCustomName() == null) child.setCustomName(Component.literal(Names.pickCitizenName(child.getGenetics().getGender(), child)));
                    setSurname(child, surname);
                });
    }
}
