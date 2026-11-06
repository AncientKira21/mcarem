package com.ancientkira.mca.entity.ai.chatAI;

import com.ancientkira.mca.Config;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.chatAI.inworldAIModules.EmotionModule;
import com.ancientkira.mca.entity.ai.chatAI.inworldAIModules.RelationshipModule;
import com.ancientkira.mca.entity.ai.chatAI.inworldAIModules.SessionModule;
import com.ancientkira.mca.entity.ai.chatAI.inworldAIModules.TriggerModule;
import com.ancientkira.mca.entity.ai.chatAI.inworldAIModules.api.Interaction;
import com.ancientkira.mca.entity.ai.chatAI.inworldAIModules.api.TriggerEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;
import com.ancientkira.mca.server.world.data.PlayerSaveData;

public class InworldAI implements ChatAIStrategy {
    private final SessionModule sessionModule;
    private final RelationshipModule relationshipModule;
    private final TriggerModule triggerModule;
    private final EmotionModule emotionModule;

    public InworldAI(String resourceName) {
        this.sessionModule = new SessionModule(resourceName);
        this.relationshipModule = new RelationshipModule();
        this.triggerModule = new TriggerModule();
        this.emotionModule = new EmotionModule();
    }

    /**
     * Gets a reply from InworldAI for a given message.
     *
     * @param player   The player requesting the answer
     * @param villager The villager responding
     * @param msg      The message
     * @return {@code Optional.EMPTY} on error, Optional containing the answer to a message on success
     */
    public synchronized Optional<String> answer(ServerPlayer player, VillagerEntityMCA villager, String msg) {

        var server = player.level().getServer();
        Request request = server.submit(() -> {
            if (!ChatAI.canReply(player, villager)) return null;
            TriggerEvent event = new TriggerEvent("status-update", new TriggerEvent.Parameter[]{
                    relationshipModule.getRelationshipTriggerParameter(player, villager),
                    emotionModule.getEmotionTriggerParameter(villager),
                    new TriggerEvent.Parameter("current-context", ChatAIContext.currentFacts(player, villager))
            });
            return new Request(player.getUUID(), player.getName().getString(),
                    PlayerSaveData.get(player).getGender().getDataName(), event);
        }).join();
        if (request == null) return Optional.empty();

        Optional<Interaction> response = sessionModule.getResponse(
                request.playerId(), request.playerName(), request.gender(), msg, request.status());
        return server.submit(() -> {
            if (!ChatAI.canReply(player, villager)) return Optional.<String>empty();
            if (response.isEmpty()) {
                player.sendSystemMessage(Component.translatable("mca.ai_broken").withStyle(ChatFormatting.RED));
                return Optional.<String>empty();
            }
            Interaction interaction = response.get();
            relationshipModule.updateRelationship(interaction, player, villager);
            if (Config.getInstance().villagerChatAIUseTools) {
                triggerModule.processTriggers(interaction, player, villager);
            }
            String answer = String.join("", interaction.textList());
            return answer.isEmpty() ? Optional.<String>empty() : Optional.of(answer);
        }).join();
    }
    private record Request(UUID playerId, String playerName, String gender, TriggerEvent status) {}
}
