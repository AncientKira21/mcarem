package com.ancientkira.mca.client.tts;

import com.ancientkira.mca.Config;
import net.minecraft.world.entity.Entity;

import java.io.OutputStream;
import net.minecraft.client.Minecraft;


public class ElevenlabsSpeechManager extends RealtimeSpeechManager {
    public ElevenlabsSpeechManager() {
        super("https://api.elevenlabs.io/v1/text-to-speech/");
    }

    public void play(String text, String gender, float pitch, float gene, Entity entity, boolean cacheable) {
        if (entity instanceof com.ancientkira.mca.entity.VillagerEntityMCA person) {
            String selected = person.getTrackedValue(com.ancientkira.mca.entity.VillagerLike.VOICE_ID);
            if (!selected.isBlank() && selected.matches("[A-Za-z0-9_-]{1,128}")) {
                play(text, pitch, entity, cacheable, selected);
                return;
            }
        }
        ElevenlabsVoices.automaticVoice(gender, gene).thenAccept(voice -> {
            if (!voice.isEmpty()) play(text, pitch, entity, cacheable, voice);
        }).exceptionally(error -> {
            Minecraft.getInstance().execute(() -> {
                var player = Minecraft.getInstance().player;
                if (player != null) Minecraft.getInstance().gui.setOverlayMessage(net.minecraft.network.chat.Component.translatable(
                        ElevenlabsVoices.errorKey(error)), true);
            });
            return null;
        });
    }

    static boolean isScream(String text) {
        return text.strip().toLowerCase(java.util.Locale.ROOT).startsWith("[scream]");
    }

    static String modelFor(String text, String configuredModel) {
        if (!isScream(text)) return configuredModel;
        return configuredModel != null && (configuredModel.startsWith("eleven_v3") || configuredModel.startsWith("eleven_v4"))
                ? configuredModel : "eleven_v3";
    }

    @Override
    public String cacheKey(String voice, String text) {
        return AudioCache.getHash("elevenlabs-reactions-v4:" + modelFor(text, Config.getInstance().elevenlabsModel) + ":" + voice)
                + "/" + AudioCache.getHash(text);
    }

    static String payload(String text, String configuredModel) {
        String model = modelFor(text, configuredModel);
        var payload = new com.google.gson.JsonObject();
        payload.addProperty("text", "[scream]".equalsIgnoreCase(text.strip()) ? "[scream] Aaaah!" : text);
        payload.addProperty("model_id", model);
        var settings = new com.google.gson.JsonObject();
        boolean expressive = model != null && (model.startsWith("eleven_v3") || model.startsWith("eleven_v4"));
        settings.addProperty("stability", expressive ? 0.5 : 0.3);
        settings.addProperty("similarity_boost", 0.5);
        if (!expressive) {
            settings.addProperty("style", 0.05);
            settings.addProperty("use_speaker_boost", true);
        }
        payload.add("voice_settings", settings);
        return payload.toString();
    }

    @Override
    public boolean downloadAudio(OutputStream output, String voiceId, String text) {
        return RealtimeSpeechManager.download(output, this.url + voiceId + "?output_format=pcm_22050",
                payload(text, Config.getInstance().elevenlabsModel), Config.getInstance().elevenlabsPrivateAPIkey);
    }
}
