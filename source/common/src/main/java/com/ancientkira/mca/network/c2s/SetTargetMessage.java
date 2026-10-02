package com.ancientkira.mca.network.c2s;

import com.ancientkira.mca.MCA;
import com.ancientkira.mca.network.HandleablePayload;
import com.ancientkira.mca.registry.DataComponentsMCA;
import com.ancientkira.mca.registry.ItemsMCA;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.UUID;

public record SetTargetMessage(String targetName, UUID targetUUID) implements HandleablePayload {
    public static final CustomPacketPayload.Type<SetTargetMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("set_target"));
    public static final StreamCodec<FriendlyByteBuf, SetTargetMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SetTargetMessage::targetName,
            UUIDUtil.STREAM_CODEC, SetTargetMessage::targetUUID,
            SetTargetMessage::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        Arrays.stream(InteractionHand.values()).forEach(hand -> {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(ItemsMCA.VILLAGER_TRACKER)) {
                stack.set(DataComponentsMCA.TRACKER_NAME, targetName);
                stack.set(DataComponentsMCA.TRACKER_UUID, targetUUID);
            }
        });
    }

    @Override
    public Type<SetTargetMessage> type() {
        return TYPE;
    }
}
