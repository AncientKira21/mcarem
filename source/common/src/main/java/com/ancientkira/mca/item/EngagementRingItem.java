package com.ancientkira.mca.item;

import com.ancientkira.mca.Config;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.Relationship;
import com.ancientkira.mca.server.world.data.PlayerSaveData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

public class EngagementRingItem extends RelationshipItem {
    public EngagementRingItem(Properties properties) {
        super(properties);
    }

    @Override
    protected int getHeartsRequired() {
        return Config.getInstance().engagementHeartsRequirement;
    }

    @Override
    public InteractionResult handle(ServerPlayer player, VillagerEntityMCA villager) {
        InteractionResult result = validate(player, villager);
        if (result != InteractionResult.PASS) {
            return result;
        }

        if (Relationship.IS_ENGAGED.test(villager, player)) {
            villager.sendChatMessage(player, "interaction.engage.fail.engaged");
            return InteractionResult.FAIL;
        }

        PlayerSaveData playerData = PlayerSaveData.get(player);
        playerData.engage(villager);
        villager.getRelationships().engage(player);
        villager.getVillagerBrain().modifyMoodValue(10);
        villager.sendChatMessage(player, "interaction.engage.success");
        return InteractionResult.CONSUME;
    }
}
