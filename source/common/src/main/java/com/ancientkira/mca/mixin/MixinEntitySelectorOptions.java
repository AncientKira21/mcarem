package com.ancientkira.mca.mixin;

import com.ancientkira.mca.entity.FamilyNames;
import com.ancientkira.mca.entity.VillagerLike;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntitySelectorOptions.class)
public abstract class MixinEntitySelectorOptions {
    // Extend the equality test before vanilla applies name=! inversion.
    @ModifyExpressionValue(method = "lambda$bootStrap$1", at = @At(value = "INVOKE",
            target = "Ljava/lang/String;equals(Ljava/lang/Object;)Z"))
    private static boolean mca$matchFirstName(boolean original,
            @Local(argsOnly = true) String query, @Local(argsOnly = true) Entity entity) {
        return original || entity instanceof VillagerLike<?>
                && FamilyNames.matchesName(entity.getPlainTextName(), query, false);
    }
}
