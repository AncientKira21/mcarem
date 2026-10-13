package com.ancientkira.mca.server;

import com.ancientkira.mca.Config;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.Relationship;
import com.ancientkira.mca.entity.ai.relationship.AgeState;
import com.ancientkira.mca.server.world.data.PlayerSaveData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/** Proposal letters use the ordinary inbox; only the recipient's saved pending proposal authorizes an answer. */
public final class VillagerProposals {
    private static final long DAY = 24000L;
    private static final long EXPIRY = 7 * DAY;

    private VillagerProposals() { }

    public static boolean eligible(VillagerEntityMCA villager, ServerPlayer player) {
        PlayerSaveData data = PlayerSaveData.get(player);
        var relationship = villager.getRelationships();
        return villager.isAlive() && !villager.isRemoved() && !villager.isHostile() && !villager.isInfected()
                && villager.getAgeState() == AgeState.ADULT
                && player.isAlive() && !player.isSpectator()
                && !Relationship.IS_RELATIVE.test(villager, player)
                && !relationship.isMarried() && !data.isMarried()
                && relationship.getPartnerUUID().filter(id -> !id.equals(player.getUUID())).isEmpty()
                && data.getPartnerUUID().filter(id -> !id.equals(villager.getUUID())).isEmpty()
                && villager.getVillagerBrain().getMemoriesForPlayer(player).getHearts() >= Config.getInstance().marriageHeartsRequirement
                && villager.canBeAttractedTo(data);
    }

    public static void tick(VillagerEntityMCA villager) {
        Config config = Config.getInstance();
        if (!config.enableVillagerMarriageProposals || !config.enableVillagerMailingPlayers
                || villager.isHostile() || villager.getAgeState() != AgeState.ADULT
                || villager.getVillagerBrain().isPanicking()
                || villager.getVillagerBrain().getMoodValue() <= 0) return;
        ServerLevel world = (ServerLevel) villager.level();
        long now = world.getServer().overworld().getGameTime();
        for (ServerPlayer player : world.players()) {
            if (villager.distanceToSqr(player) > 32 * 32 || !eligible(villager, player)) continue;
            String memory = "marriageProposal." + player.getUUID();
            if (villager.getLongTermMemory().hasMemory(memory)) continue;
            PlayerSaveData data = PlayerSaveData.get(player);
            if (data.getMarriageProposals().getOrDefault(villager.getUUID(), 0L) > now) continue;
            if (villager.getRandom().nextFloat() >= config.villagerMarriageProposalChance) {
                villager.getLongTermMemory().remember(memory, DAY);
                continue;
            }
            data.updateMarriageProposal(villager.getUUID(), now + EXPIRY);
            villager.getLongTermMemory().remember(memory, EXPIRY);
            data.sendLetter(Component.translatable("mca.letter.proposal", villager.getName().getString())
                    .append("\n\n").append(answer(villager.getUUID(), true)).append("   ").append(answer(villager.getUUID(), false)));
            break;
        }
    }

    private static Component answer(UUID sender, boolean accept) {
        return Component.translatable(accept ? "gui.yes" : "gui.no").withStyle(style -> style
                .withColor(accept ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_RED)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand("/mca villagerProposal " + (accept ? "yes " : "no ") + sender)));
    }

    public static int respond(ServerPlayer player, UUID sender, boolean accept) {
        PlayerSaveData data = PlayerSaveData.get(player);
        Long expires = data.getMarriageProposals().get(sender);
        if (expires == null || expires <= player.level().getServer().overworld().getGameTime()) {
            data.updateMarriageProposal(sender, null);
            player.sendSystemMessage(Component.translatable("mca.proposal.expired"));
            return 0;
        }
        if (!accept) {
            data.updateMarriageProposal(sender, null);
            player.sendSystemMessage(Component.translatable("mca.proposal.declined"));
            return 1;
        }
        VillagerEntityMCA villager = null;
        for (ServerLevel world : player.level().getServer().getAllLevels()) {
            Entity entity = world.getEntity(sender);
            if (entity instanceof VillagerEntityMCA person) { villager = person; break; }
        }
        if (villager == null) {
            var node = data.getFamilyTree().getOrEmpty(sender);
            if (node.isEmpty() || node.get().isDeceased()) {
                data.updateMarriageProposal(sender, null);
                player.sendSystemMessage(Component.translatable("mca.proposal.expired"));
            } else {
                player.sendSystemMessage(Component.translatable("mca.proposal.visit_sender"));
            }
            return 0;
        }
        if (!eligible(villager, player)) {
            data.updateMarriageProposal(sender, null);
            player.sendSystemMessage(Component.translatable("mca.proposal.unavailable"));
            return 0;
        }
        data.updateMarriageProposal(sender, null);
        data.marry(villager);
        villager.getRelationships().marry(player);
        villager.getVillagerBrain().modifyMoodValue(15);
        player.sendSystemMessage(Component.translatable("mca.proposal.accepted", villager.getName().getString()));
        return 1;
    }
}
