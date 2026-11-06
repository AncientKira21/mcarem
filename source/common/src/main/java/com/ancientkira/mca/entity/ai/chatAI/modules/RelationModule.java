package com.ancientkira.mca.entity.ai.chatAI.modules;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.Relationship;
import com.ancientkira.mca.server.world.data.FamilyTreeNode;
import java.util.stream.Stream;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class RelationModule {
    public static void apply(List<String> input, VillagerEntityMCA villager, ServerPlayer player) {
        var family = villager.getRelationships().getFamilyEntry();
        addFamily(input, "Parents", family.getParents());
        addFamily(input, "Siblings", siblingsOf(family));
        addFamily(input, "Children", family.lookup(family.streamChildren()));
        addFamily(input, "Grandparents", family.getParents().flatMap(node -> node.getParents()));
        addFamily(input, "Grandchildren", family.lookup(family.streamChildren())
                .flatMap(node -> node.lookup(node.streamChildren())));
        addFamily(input, "Aunts and uncles", family.getParents().flatMap(RelationModule::siblingsOf));
        addFamily(input, "Cousins", family.getParents().flatMap(RelationModule::siblingsOf)
                .flatMap(node -> node.lookup(node.streamChildren())));
        addFamily(input, "Nieces and nephews", siblingsOf(family)
                .flatMap(node -> node.lookup(node.streamChildren())));
        addFamily(input, "Current partner (" + family.getRelationshipState() + ")",
                family.lookup(Stream.of(family.partner())));
        addFamily(input, "Late spouse", family.lookup(Stream.of(family.getWidowedPartner())));
        boolean silentHearts = false;

        if (Relationship.IS_MARRIED.test(villager, player)) {
            input.add("$villager is married to $player. ");
            silentHearts = true;
        } else if (villager.getRelationships().isMarried()) {
            input.add("$villager is married. ");
        }
        if (Relationship.IS_PROMISED.test(villager, player)) {
            input.add("$villager is in love with $player. ");
            silentHearts = true;
        }
        if (Relationship.IS_ENGAGED.test(villager, player)) {
            input.add("$villager is engaged with $player. ");
            silentHearts = true;
        }
        if (Relationship.IS_PARENT.test(villager, player)) {
            input.add("$player is $villagers parent. ");
        }
        if (Relationship.IS_KID.test(villager, player)) {
            input.add("$villager is $player's child. ");
        }

        int hearts = villager.getVillagerBrain().getMemoriesForPlayer(player).getHearts();
        if (hearts < -25) {
            input.add("$villager hates $player. ");
        } else if (!silentHearts) {
            if (hearts < 0) {
                input.add("$villager dislikes $player. ");
            } else if (hearts < 33) {
                input.add("$villager barely knows $player. ");
            } else if (hearts < 66) {
                input.add("$villager knows $player well. ");
            } else if (hearts < 100) {
                input.add("$villager likes $player. ");
            } else {
                input.add("$villager likes $player really well. ");
            }
        }
    }
    private static Stream<FamilyTreeNode> siblingsOf(FamilyTreeNode node) {
        return node.getSiblings().filter(sibling -> !sibling.id().equals(node.id()));
    }

    private static void addFamily(List<String> input, String label,
                                  Stream<FamilyTreeNode> family) {
        var names = family.distinct().limit(24)
                .map(node -> node.getName() + (node.isDeceased() ? " (deceased)" : ""))
                .toList();
        input.add(label + ": " + (names.isEmpty() ? "none recorded" : String.join(", ", names)) + ". ");
    }
}
