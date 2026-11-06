package com.ancientkira.mca.mixin.client;

import com.ancientkira.mca.Config;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreativeModeTabs.class)
public abstract class MixinCorpseCreativeTabs {
    @Shadow private static CreativeModeTab.ItemDisplayParameters CACHED_PARAMETERS;
    @Unique private static Boolean mca$lastCorpsesEnabled;

    @Inject(method = "tryRebuildTabContents", at = @At("HEAD"))
    private static void mca$refreshCorpseItems(CallbackInfoReturnable<Boolean> cir) {
        boolean enabled = Config.getInstance().enableCorpses;
        if (mca$lastCorpsesEnabled == null || mca$lastCorpsesEnabled != enabled) {
            CACHED_PARAMETERS = null;
            mca$lastCorpsesEnabled = enabled;
        }
    }
}
