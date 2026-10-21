package com.ancientkira.mca.compat;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class DiscCordMusic {
    private DiscCordMusic() {}
    public static boolean isDisc(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem().getClass().getName().equals("com.mrbysco.disccord.item.DiscCordItem");
    }
    public static boolean playable(ItemStack stack) {
        return stack.has(net.minecraft.core.component.DataComponents.JUKEBOX_PLAYABLE) || isDisc(stack);
    }
    public static void stop(JukeboxBlockEntity box) {
        if (!(box.getLevel() instanceof ServerLevel world)) return;
        try {
            Class<?> payload=Class.forName("com.mrbysco.disccord.network.payload.PlayRecordPayload");
            CustomPacketPayload packet=(CustomPacketPayload)payload.getConstructor(net.minecraft.core.BlockPos.class,String.class)
                    .newInstance(box.getBlockPos(),"");
            for (var player : world.players()) player.connection.send(new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(packet));
        } catch (ReflectiveOperationException | LinkageError e) {
            com.ancientkira.mca.MCA.LOGGER.warn("Could not stop DiscCord playback at {}",box.getBlockPos(),e);
        }
        box.getSongPlayer().stop(world,box.getBlockState());
    }
}
