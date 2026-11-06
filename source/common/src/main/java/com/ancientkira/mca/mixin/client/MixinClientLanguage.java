package com.ancientkira.mca.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.ancientkira.mca.MCA;
import com.ancientkira.mca.entity.CommonSpeechManager;
import com.ancientkira.mca.entity.ai.DialogueType;
import com.ancientkira.mca.util.localization.PooledTranslationStorage;
import com.ancientkira.mca.util.McaPair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(value = ClientLanguage.class, priority = 990)
abstract class MixinClientLanguage extends Language {
    @Shadow
    @Final
    private Map<String, String> storage;
    @Unique
    private PooledTranslationStorage mca$pool;

    @Shadow
    public abstract @NotNull String getOrDefault(String key, String fallback);

    @Unique
    private PooledTranslationStorage mca$getPool() {
        if (mca$pool == null) {
            mca$pool = new PooledTranslationStorage(storage);
            MCA.storage = storage;
            MCA.language = Minecraft.getInstance().options.languageCode;
        }
        return mca$pool;
    }

    @Inject(method = "getOrDefault", at = @At("HEAD"), cancellable = true)
    private void mca$injectGetOrDefault(String key, String fallback, CallbackInfoReturnable<String> info) {
        String resident = null;
        if (key.startsWith("#R")) {
            int end = key.indexOf('.');
            if (end > 2) {
                try {
                    resident = new String(java.util.Base64.getUrlDecoder().decode(key.substring(2, end)), java.nio.charset.StandardCharsets.UTF_8);
                    key = key.substring(end + 1);
                } catch (IllegalArgumentException ignored) { }
            }
        }
        String modifiedKey = DialogueType.applyFallback(key);
        McaPair<String, String> unpooled = mca$getPool().get(modifiedKey, resident);
        if (unpooled != null) {
            CommonSpeechManager.INSTANCE.lastResolvedKey.set(unpooled.getA());
            if (storage.containsKey(unpooled.getA()) && !storage.get(unpooled.getA()).equals(unpooled.getB())) {
                // In this case, the text has been dynamically created and we need to return directly
                info.setReturnValue(unpooled.getB());
            } else {
                info.setReturnValue(getOrDefault(unpooled.getA(), fallback));
            }
        } else if (resident != null && storage.containsKey(modifiedKey)) {
            info.setReturnValue(com.ancientkira.mca.util.localization.TemplateSet.replaceResident(storage.get(modifiedKey), resident));
        } else if (!key.equals(modifiedKey)) {
            info.setReturnValue(getOrDefault(modifiedKey, fallback));
        }
    }

    @ModifyReturnValue(method = "has(Ljava/lang/String;)Z", at = @At("RETURN"))
    private boolean mca$includePooledTranslations(boolean original, String key) {
        boolean pooled = mca$getPool().contains(key);
        return original || pooled;
    }
}
