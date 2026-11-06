package com.ancientkira.mca.entity.interaction.gifts;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.resources.data.Analysis;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

public final class FavoriteGiftBonus {
    private FavoriteGiftBonus() {}

    public static void addTo(Analysis analysis, VillagerEntityMCA recipient, ItemStack stack) {
        String food = recipient.getFavoriteFood();
        String foodId = food.contains(":") ? food : "minecraft:" + food;
        if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(foodId)) {
            analysis.add("favorite_food", 10);
        }
        int color = colorBonus(recipient, stack);
        if (color > 0) {
            analysis.add(color == 10 ? "favorite_color" : "similar_favorite_color", color);
        }
    }

    public static int colorBonus(VillagerEntityMCA recipient, ItemStack stack) {
        DyeColor favorite = null;
        for (DyeColor color : DyeColor.values()) {
            if (color.getName().equals(recipient.getFavoriteColor())) favorite = color;
        }
        if (favorite == null) return 0;

        var dyed = stack.get(DataComponents.DYED_COLOR);
        if (dyed != null) return FavoriteColorMatch.bonus(favorite.getTextureDiffuseColor(), dyed.rgb());

        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        // Longest names first: light_blue must not be mistaken for blue, for example.
        DyeColor matched = null;
        for (DyeColor color : DyeColor.values()) {
            String name = color.getName();
            if ((path.equals(name) || path.startsWith(name + "_"))
                    && (matched == null || name.length() > matched.getName().length())) matched = color;
        }
        return matched == null ? 0 : FavoriteColorMatch.bonus(
                favorite.getTextureDiffuseColor(), matched.getTextureDiffuseColor());
    }
}
