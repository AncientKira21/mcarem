package com.ancientkira.mca.server;

import com.ancientkira.mca.Config;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.relationship.AgeState;
import com.ancientkira.mca.entity.ai.relationship.RelationshipState;
import com.ancientkira.mca.registry.ProfessionsMCA;
import com.ancientkira.mca.server.world.data.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Validated inbox requests; adopting updates the ordinary family tree. */
public final class FamilyRequests {
    private static final long DAY = 24000L;
    private FamilyRequests() { }
    public static boolean isChild(VillagerEntityMCA person) {
        var age = person.getAgeState();
        return age == AgeState.BABY || age == AgeState.TODDLER || age == AgeState.CHILD || age == AgeState.TEEN;
    }
    public static boolean isOrphan(FamilyTree tree, FamilyTreeNode child) {
        return child.streamParents().noneMatch(id -> tree.getOrEmpty(id).filter(parent -> !parent.isDeceased()).isPresent());
    }
    private static boolean available(VillagerEntityMCA person) {
        return person.isAlive() && !person.isRemoved() && !person.isKidnapped() && !person.isNoAi()
                && !ProfessionsMCA.isRaider(person.getProfession());
    }
    private static boolean strainedSpouse(VillagerEntityMCA person, ServerPlayer player) {
        return available(person) && person.getRelationships().isMarried()
                && person.getRelationships().getPartnerUUID().filter(player.getUUID()::equals).isPresent()
                && PlayerSaveData.get(player).getPartnerUUID().filter(person.getUUID()::equals).isPresent()
                && person.getVillagerBrain().getMemoriesForPlayer(player).getHearts() <= Config.getInstance().villagerDivorceRequestHearts;
    }
    public static void tick(VillagerEntityMCA person) {
        if (!Config.getInstance().enableFamilyRequestLetters || !available(person)) return;
        ServerLevel world = (ServerLevel) person.level();
        long now = world.getServer().overworld().getGameTime();
        FamilyTree tree = FamilyTree.get(world);
        FamilyTreeNode node = tree.getOrCreate(person);
        boolean orphan = isChild(person) && isOrphan(tree, node);
        boolean pendingAdoption = false;
        for (ServerPlayer player : world.getServer().getPlayerList().getPlayers()) {
            PlayerSaveData data = PlayerSaveData.get(player);
            if (orphan && data.getFamilyRequests().getOrDefault("adopt:" + person.getUUID(), 0L) > now) pendingAdoption = true;
            String type = strainedSpouse(person, player) ? "divorce"
                    : orphan && player.level() == world && !player.isSpectator() && person.distanceToSqr(player) <= 128 * 128
                    && person.getVillagerBrain().getMemoriesForPlayer(player).getHearts() >= 0 ? "adopt" : null;
            if (type == null) continue;
            String key = type + ":" + person.getUUID();
            String memory = "familyRequest." + type + "." + player.getUUID();
            if (data.getFamilyRequests().getOrDefault(key, 0L) > now || person.getLongTermMemory().hasMemory(memory)) continue;
            person.getLongTermMemory().remember(memory, DAY);
            if (world.getRandom().nextDouble() >= Config.getInstance().familyRequestChancePerDay) continue;
            if(!data.reserveInvitation(type,person.getUUID(),now+7*DAY))continue;
            data.updateFamilyRequest(key, now + 7 * DAY);
            if (orphan) {
                pendingAdoption = true;
                person.getLongTermMemory().remember("pendingAdoptionLetter", 7 * DAY);
            }
            data.sendLetter(Component.translatable("mca.letter." + type + "_request", person.getName().getString())
                    .append("\n\n").append(answer(type, person.getUUID(), true)).append("   ").append(answer(type, person.getUUID(), false)));
            if (orphan) break;
        }
        if (!orphan || pendingAdoption || person.getLongTermMemory().hasMemory("pendingAdoptionLetter")
                || person.getLongTermMemory().hasMemory("localAdoptionCheck")) return;
        person.getLongTermMemory().remember("localAdoptionCheck", DAY);
        if (world.getRandom().nextDouble() >= Config.getInstance().orphanLocalAdoptionChancePerDay) return;
        person.getResidency().getHomeVillage().ifPresent(village -> {
            for (var parent : village.getResidents(world)) {
                if (!available(parent) || parent.getAgeState() != AgeState.ADULT || !parent.getRelationships().isMarried()
                        || parent.getRelationships().getFamilyEntry().getChildren().count() >= 3) continue;
                var spouse = parent.getRelationships().getPartner().orElse(null);
                if (!(spouse instanceof VillagerEntityMCA other) || !available(other) || other.getAgeState() != AgeState.ADULT
                        || other.getResidency().getHomeVillage().filter(v -> v.getId() == village.getId()).isEmpty()) continue;
                node.replaceParents(parent.getRelationships().getFamilyEntry(), Optional.of(other.getRelationships().getFamilyEntry()));
                com.ancientkira.mca.entity.FamilyNames.inheritSurname(person);
                record(person, Component.translatable("mca.adoption.local", person.getName().getString(), parent.getName().getString(), other.getName().getString()));
                break;
            }
        });
    }
    private static Component answer(String type, UUID sender, boolean accept) {
        return Component.translatable(accept ? "gui.yes" : "gui.no").withStyle(style -> style
                .withColor(accept ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_RED).withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand("/mca familyRequest " + type + " " + (accept ? "yes " : "no ") + sender)));
    }
    public static int respond(ServerPlayer player, String type, UUID sender, boolean accept) {
        PlayerSaveData data = PlayerSaveData.get(player);
        String key = type + ":" + sender;
        long now = player.level().getServer().overworld().getGameTime();
        if (data.getFamilyRequests().getOrDefault(key, 0L) <= now) {
            data.updateFamilyRequest(key, null);
            player.sendSystemMessage(Component.translatable("mca.family_request.expired")); return 0;
        }
        if (!accept) {
            data.updateFamilyRequest(key, null);
            for (var world : player.level().getServer().getAllLevels()) {
                if (world.getEntity(sender) instanceof VillagerEntityMCA child) child.getLongTermMemory().forget("pendingAdoptionLetter");
            }
            player.sendSystemMessage(Component.translatable("mca.family_request.declined")); return 1;
        }
        VillagerEntityMCA person = null;
        for (var world : player.level().getServer().getAllLevels()) if (world.getEntity(sender) instanceof VillagerEntityMCA found) { person = found; break; }
        if (person == null) {
            player.sendSystemMessage(Component.translatable("mca.proposal.visit_sender")); return 0;
        }
        if (type.equals("divorce") && strainedSpouse(person, player)) {
            person.getRelationships().endRelationShip(RelationshipState.SINGLE);
            data.endRelationShip(RelationshipState.SINGLE);
            record(person, Component.translatable("mca.divorce.accepted", person.getName().getString(), player.getName().getString()));
        } else if (type.equals("adopt") && available(person) && isChild(person)
                && isOrphan(FamilyTree.get((ServerLevel) person.level()), person.getRelationships().getFamilyEntry())) {
            var parent = data.getFamilyEntry();
            var spouse = data.getFamilyTree().getOrEmpty(parent.partner()).filter(node -> !node.isDeceased());
            person.getRelationships().getFamilyEntry().replaceParents(parent, spouse);
            com.ancientkira.mca.entity.FamilyNames.inheritSurname(person);
            record(person, Component.translatable("mca.adoption.player", person.getName().getString(), player.getName().getString()));
        } else {
            data.updateFamilyRequest(key, null);
            player.sendSystemMessage(Component.translatable("mca.family_request.unavailable")); return 0;
        }
        data.updateFamilyRequest(key, null);
        player.sendSystemMessage(Component.translatable("mca.family_request.accepted")); return 1;
    }
    private static void record(VillagerEntityMCA person, Component message) {
        person.getResidency().getRegistryVillage().flatMap(Village::getCivilRegistry).ifPresent(registry -> registry.addText(message));
        ((ServerLevel) person.level()).getServer().getPlayerList().broadcastSystemMessage(message, false);
    }
}
