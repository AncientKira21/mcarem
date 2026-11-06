package com.ancientkira.mca.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The debug overlay has a separate extraction path; both must stay hidden during setup.
@Mixin({Gui.class, DebugScreenOverlay.class})
public class MixinSetupGui {
    @Inject(method="extractRenderState",at=@At("HEAD"),cancellable=true)
    private void mca$hideWorldHudDuringSetup(CallbackInfo callback) {
        if (com.ancientkira.mca.MCAClient.getDestinyManager().isSettingUp()
                || Minecraft.getInstance().screen instanceof com.ancientkira.mca.client.gui.DestinyScreen) callback.cancel();
    }
}
