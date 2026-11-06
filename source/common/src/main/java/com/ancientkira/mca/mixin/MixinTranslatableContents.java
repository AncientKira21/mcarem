package com.ancientkira.mca.mixin;

import com.ancientkira.mca.entity.CommonSpeechManager;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TranslatableContents.class)
public class MixinTranslatableContents {
    @Inject(method = "decompose()V", at = @At("HEAD"))
    private void mca$clearPreviousTranslation(CallbackInfo ci) {
        CommonSpeechManager.INSTANCE.lastResolvedKey.remove();
    }

    @Inject(method = "decompose()V", at = @At("TAIL"))
    private void mca$updateTranslations(CallbackInfo ci) {
        if (CommonSpeechManager.INSTANCE.lastResolvedKey.get() != null) {
            CommonSpeechManager.INSTANCE.translations.put((ComponentContents) this, CommonSpeechManager.INSTANCE.lastResolvedKey.get());
            CommonSpeechManager.INSTANCE.lastResolvedKey.remove();
        }
    }
}
