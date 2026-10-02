package com.ancientkira.mca.fabric.datagen;

import com.ancientkira.mca.entity.CribWoodType;
import com.ancientkira.mca.registry.ItemsMCA;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.DyeColor;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class CribLanguageProvider extends FabricLanguageProvider {
    protected CribLanguageProvider(FabricPackOutput dataOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(dataOutput, registryLookup);
    }

    @Override
    public void generateTranslations(HolderLookup.Provider provider, TranslationBuilder translationBuilder) {
        for (CribWoodType wood : CribWoodType.values()) {
            for (DyeColor color : DyeColor.values()) {
                ItemsMCA.CRIBS.stream().filter(c -> {
                    return c.getColor() == color && c.getWood() == wood;
                }).findAny().ifPresent(item -> {
                    StringBuilder name = new StringBuilder();
                    for (String s : item.getColor().getName().split("_")) {
                        name.append(s.substring(0, 1).toUpperCase(Locale.ROOT)).append(s.substring(1)).append(" ");
                    }

                    for (String s : item.getWood().toString().toLowerCase(Locale.ROOT).split("_")) {
                        name.append(s.substring(0, 1).toUpperCase(Locale.ROOT)).append(s.substring(1)).append(" ");
                    }

                    name.append("Crib");

                    translationBuilder.add(item, name.toString());
                });
            }
        }
    }
}
