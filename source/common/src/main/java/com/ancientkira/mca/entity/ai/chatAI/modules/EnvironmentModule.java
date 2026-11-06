package com.ancientkira.mca.entity.ai.chatAI.modules;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class EnvironmentModule {
    public static void apply(List<String> input, VillagerEntityMCA villager, ServerPlayer player) {
        input.add("Current location: " + villager.level().dimension().identifier() + ", "
                + villager.blockPosition().toShortString() + ". ");
        var nearby = villager.level().getEntitiesOfClass(VillagerEntityMCA.class,
                villager.getBoundingBox().inflate(16), other -> other != villager && other.isAlive());
        var names = nearby.stream().sorted(java.util.Comparator.comparingDouble(villager::distanceToSqr))
                .limit(12).map(other -> other.getName().getString()).toList();
        input.add("Nearby villagers: " + (names.isEmpty() ? "none" : String.join(", ", names)) + ". ");
        if (villager.level().isRaining()) {
            input.add("It is raining. ");
        }
        if (villager.level().isThundering()) {
            input.add("It is thundering. ");
        }
        long dayTime = villager.level().getOverworldClockTime() % 24000L;
        if (dayTime >= 13000L && dayTime < 23000L) {
            input.add("It is night. ");
        } else {
            input.add("It is daytime. ");
        }
    }
}
