package com.ancientkira.mca.network.c2s;

import com.ancientkira.mca.MCA;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.network.HandleablePayload;
import com.ancientkira.mca.network.Network;
import com.ancientkira.mca.network.s2c.InteractionDialogueResponse;
import com.ancientkira.mca.resources.Dialogues;
import com.ancientkira.mca.resources.data.dialogue.Question;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

public record InteractionDialogueInitMessage(UUID villagerUUID) implements HandleablePayload {
    public static final CustomPacketPayload.Type<InteractionDialogueInitMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("interaction_dialogue_init"));
    public static final StreamCodec<FriendlyByteBuf, InteractionDialogueInitMessage> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, InteractionDialogueInitMessage::villagerUUID,
            InteractionDialogueInitMessage::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        Entity v = player.level().getEntity(villagerUUID);
        if (v instanceof VillagerEntityMCA villager) {
            if (villager.isTooBusyToTalk()) {
                villager.getInteractions().stopInteracting();
                String key = villager.getRandom().nextBoolean() ? "dialogue.busy" : "dialogue.goaway";
                if (villager.getRelationships().isMarriedTo(player.getUUID())) key = "spouse." + key;
                else if (villager.getRelationships().getFamilyEntry().isParent(player.getUUID())) key = "childp." + key;
                villager.sendChatMessage(player, key);
                Network.sendToPlayer(new com.ancientkira.mca.network.s2c.OpenGuiRequest(
                        com.ancientkira.mca.network.s2c.OpenGuiRequest.Type.CLOSE), player);
                return;
            }
            Question question = Dialogues.getInstance().getQuestion("root");
            if (question.isAuto()) {
                Dialogues.getInstance().selectAnswer(villager, player, question.getName(), question.getRandomAnswer().getName());
            } else {
                InteractionDialogueResponse response = new InteractionDialogueResponse(question, player, villager);
                Network.sendToPlayer(response, player);
            }
        }
    }

    @Override
    public Type<InteractionDialogueInitMessage> type() {
        return TYPE;
    }
}
