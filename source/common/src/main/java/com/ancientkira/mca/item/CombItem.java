package com.ancientkira.mca.item;

import com.ancientkira.mca.entity.VillagerLike;
import com.ancientkira.mca.network.Network;
import com.ancientkira.mca.network.s2c.OpenGuiRequest;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class CombItem extends TooltippedItem {
    public CombItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public final InteractionResult use(Level world, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            Network.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.COMB), serverPlayer);
            return InteractionResult.SUCCESS_SERVER;
        }
        return super.use(world, player, hand);
    }

    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand) {
        if (entity instanceof VillagerLike && !entity.level().isClientSide() && player instanceof ServerPlayer) {
            Network.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.COMB, entity), (ServerPlayer) player);
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.CONSUME;
        }
    }
}

