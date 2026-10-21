package com.ancientkira.mca.mixin.client;

import com.ancientkira.mca.client.gui.McaConfigScreen;
import com.ancientkira.mca.util.compat.ButtonWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public abstract class MixinOptionsScreen extends Screen {
    protected MixinOptionsScreen(Component title){super(title);}
    @Inject(method="init",at=@At("TAIL"))
    private void mca$addSettings(CallbackInfo ci){
        addRenderableWidget(new ButtonWidget(width-94,8,86,20,Component.literal("MCA Settings"),
                b->minecraft.setScreen(new McaConfigScreen(this))));
    }
}
