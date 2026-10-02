package com.ancientkira.mca.item;

import com.ancientkira.mca.network.Network;
import com.ancientkira.mca.network.s2c.OpenGuiRequest;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public class BlueprintItem extends TooltippedItem {
    public BlueprintItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public final InteractionResult use(Level world, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            Network.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.BLUEPRINT), serverPlayer);
        }

        return player.level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }
}

