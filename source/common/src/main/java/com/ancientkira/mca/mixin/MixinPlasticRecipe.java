package com.ancientkira.mca.mixin;

import com.ancientkira.mca.Config;
import com.ancientkira.mca.item.PlasticItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShapelessRecipe.class)
public abstract class MixinPlasticRecipe {
    @Shadow @Final private ItemStackTemplate result;

    @Inject(method = "matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z",
            at = @At("HEAD"), cancellable = true)
    private void mca$disablePlasticCrafting(CallbackInfoReturnable<Boolean> cir) {
        if (result.item().value() instanceof PlasticItem && !Config.getInstance().enableCorpses) cir.setReturnValue(false);
    }

    @Inject(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private void mca$disablePlasticResult(CallbackInfoReturnable<ItemStack> cir) {
        if (result.item().value() instanceof PlasticItem && !Config.getInstance().enableCorpses) cir.setReturnValue(ItemStack.EMPTY);
    }
}
