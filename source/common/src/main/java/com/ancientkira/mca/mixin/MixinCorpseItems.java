package com.ancientkira.mca.mixin;

import com.ancientkira.mca.Config;
import com.ancientkira.mca.item.CorpseItem;
import com.ancientkira.mca.item.PlasticItem;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Disabled corpse items are deleted when accessed, including in saved containers. */
@Mixin(ItemStack.class)
public abstract class MixinCorpseItems {
    @Shadow @Final private Holder<Item> item;
    @Shadow private int count;

    @Inject(method = "isEmpty", at = @At("HEAD"), cancellable = true)
    private void mca$removeDisabledCorpseItems(CallbackInfoReturnable<Boolean> cir) {
        if (count <= 0 || item == null) return;
        Item value = item.value();
        if ((value instanceof CorpseItem || value instanceof PlasticItem) && !Config.getInstance().enableCorpses) {
            count = 0;
            cir.setReturnValue(true);
        }
    }
}
