package com.ancientkira.mca.network.c2s;
import com.ancientkira.mca.MCA;
import com.ancientkira.mca.network.HandleablePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
public record DiscCordFinishedMessage(BlockPos pos) implements HandleablePayload {
    public static final Type<DiscCordFinishedMessage> TYPE=new Type<>(MCA.locate("disccord_finished"));
    public static final StreamCodec<FriendlyByteBuf,DiscCordFinishedMessage> STREAM_CODEC=StreamCodec.of(
            (buf,value)->buf.writeBlockPos(value.pos()),buf->new DiscCordFinishedMessage(buf.readBlockPos()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    @Override public void handleServer(ServerPlayer player){
        if (!pos.closerToCenterThan(player.position(),64) || !player.level().hasChunkAt(pos)) return;
        if (player.level().getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.JukeboxBlockEntity box
                && com.ancientkira.mca.server.VillageMusic.isPlaybackCopy(box.getTheItem())
                && com.ancientkira.mca.compat.DiscCordMusic.isDisc(box.getTheItem())) {
            com.ancientkira.mca.compat.DiscCordMusic.stop(box);
            box.setTheItem(net.minecraft.world.item.ItemStack.EMPTY);
        }
    }
}
