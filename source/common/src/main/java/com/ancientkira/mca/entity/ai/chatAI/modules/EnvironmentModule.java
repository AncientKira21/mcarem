package com.ancientkira.mca.entity.ai.chatAI.modules;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class EnvironmentModule {
    public static void apply(List<String> input, VillagerEntityMCA villager, ServerPlayer player) {
        if (player.level().isRaining()) {
            input.add("It is raining. ");
        }
        if (player.level().isThundering()) {
            input.add("It is thundering. ");
        }
        long dayTime = player.level().getOverworldClockTime() % 24000L;
        if (dayTime >= 13000L && dayTime < 23000L) {
            input.add("It is night. ");
        }
    }
}
