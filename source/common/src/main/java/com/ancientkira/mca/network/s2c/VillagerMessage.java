package com.ancientkira.mca.network.s2c;

import com.ancientkira.mca.ClientProxy;
import com.ancientkira.mca.MCA;
import com.ancientkira.mca.network.HandleablePayload;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public record VillagerMessage(Component prefix, Component message, UUID uuid) implements HandleablePayload {
    public static final CustomPacketPayload.Type<VillagerMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("villager_message"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerMessage> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, VillagerMessage::prefix,
            ComponentSerialization.STREAM_CODEC, VillagerMessage::message,
            UUIDUtil.STREAM_CODEC, VillagerMessage::uuid,
            VillagerMessage::new
    );

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleVillagerMessage(this);
    }

    @Override
    public Type<VillagerMessage> type() {
        return TYPE;
    }
}
