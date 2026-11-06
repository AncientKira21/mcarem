package com.ancientkira.mca.mixin;

import com.ancientkira.mca.Config;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.chatAI.ChatAI;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

@Mixin(ServerGamePacketListenerImpl.class)
public class MixinServerGamePacketListenerImpl {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void mca$endInitialSetupOnDisconnect(CallbackInfo callback) {
        com.ancientkira.mca.server.FirstJoinSetup.finish(player);
    }

    @Inject(method = "handleChat", at = @At("HEAD"))
    public void mca$injectHandleChat(ServerboundChatPacket message, CallbackInfo ci) {
        if (Config.getInstance().enableVillagerChatAI) {
            String msg = StringUtils.normalizeSpace(message.message());
            if (!msg.startsWith("/")) {
                ServerPlayer sender = player;
                sender.level().getServer().execute(() -> ChatAI.getVillagerForConversation(sender, msg)
                        .filter(villager -> ChatAI.canReply(sender, villager))
                        .ifPresent(villager -> mca$runAsyncAnswerRequest(sender, villager, msg)));
            }
        }
    }

    @Unique
    private void mca$runAsyncAnswerRequest(ServerPlayer player, VillagerEntityMCA villager, String msg) {
        CompletableFuture.runAsync(() -> {
            var answer = ChatAI.answer(player, villager, msg);
            player.level().getServer().execute(() -> {
                if (ChatAI.canReply(player, villager)) {
                    answer.ifPresent(a -> villager.conversationManager.addMessage(player, Component.literal(a)));
                }
            });
        });
    }
}

