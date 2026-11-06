package com.ancientkira.mca.entity.ai.chatAI.modules;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.registry.ProfessionsMCA;
import com.ancientkira.mca.server.world.data.Building;
import com.ancientkira.mca.server.world.data.Village;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class VillageModule {
    private static final Map<String, String> nameExceptions = Map.of(
            "fishermans_hut", "fisherman's hut",
            "weaving_mill", "weaving mill",
            "big_house", "big house",
            "music_store", "music store",
            "town_center", "town center",
            "building", "",
            "blocked", "",
            "house", ""
    );

    public static void apply(List<String> input, VillagerEntityMCA villager, ServerPlayer player) {
        if (ProfessionsMCA.isRaider(villager.getProfession())) {
            String biome = villager.level().getBiome(villager.blockPosition()).unwrapKey()
                    .map(v -> v.identifier().getPath().replace("_", " ")).orElse("plains");
            input.add("$villager is currently in a " + biome + " biome. Nearby villages are surroundings, "
                    + "not proof that you are a peaceful village resident. Do not invent an outpost or home location. ");
            return;
        }
        Optional<Village> village = villager.getResidency().getHomeVillage();

        // Probably completely over-detailed fact
        String biome = villager.level().getBiome(villager.blockPosition()).unwrapKey().map(v -> v.identifier().getPath()).orElse("plains");

        input.add("Current biome: " + biome.replace("_", " ") + ". ");
        input.add(village.map(v -> "Your home village is " + v.getName() + ", a " + sizeDescription(v) + " village. ")
                .orElse("You have no registered home village. "));

        // Buildings
        village.ifPresent(v -> {
            String buildings = v.getBuildings().values().stream()
                    .map(Building::getType)
                    .map(b -> nameExceptions.getOrDefault(b, b))
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .collect(Collectors.joining(", "));
            if (!buildings.isEmpty()) {
                input.add("The village has a " + buildings + ". ");
            }
        });
    }
    private static String sizeDescription(Village village) {
        int population = village.getPopulation();
        if (population > 45) return "huge";
        if (population > 30) return "large";
        return population > 15 ? "medium-sized" : "small";
    }
}
