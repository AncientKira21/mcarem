package com.ancientkira.mca.network.s2c;
import com.ancientkira.mca.MCA;
import com.ancientkira.mca.network.HandleablePayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
public record OpenNativeSocial(int entity,CompoundTag data) implements HandleablePayload {
    public static final CustomPacketPayload.Type<OpenNativeSocial> TYPE=new CustomPacketPayload.Type<>(MCA.locate("open_native_social"));
    public static final StreamCodec<FriendlyByteBuf,OpenNativeSocial> STREAM_CODEC=StreamCodec.composite(ByteBufCodecs.INT,OpenNativeSocial::entity,ByteBufCodecs.COMPOUND_TAG,OpenNativeSocial::data,OpenNativeSocial::new);
    @Override public CustomPacketPayload.Type<OpenNativeSocial> type(){return TYPE;}
    @Override public void handle(Player player){com.ancientkira.mca.ClientProxy.getNetworkHandler().handleNativeSocial(this);}
}
