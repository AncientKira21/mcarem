package com.ancientkira.mca.client.tts;

import com.ancientkira.mca.Config;
import com.ancientkira.mca.MCA;
import com.ancientkira.mca.entity.VillagerLike;
import com.ancientkira.mca.client.gui.SearchableChoiceScreen;
import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Local Voicebox REST adapter. Uses each profile's configured engine. */
public class VoiceboxSpeechManager extends RealtimeSpeechManager {
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    public VoiceboxSpeechManager() { super(""); }

    private static String endpoint() {
        String base = Config.getInstance().voiceboxUrl.strip().replaceAll("/+$", "");
        URI uri = URI.create(base);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null)
            throw new IllegalArgumentException("Invalid Voicebox address");
        return base;
    }

    public static CompletableFuture<List<SearchableChoiceScreen.Choice>> profiles() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var request = HttpRequest.newBuilder(URI.create(endpoint() + "/profiles"))
                        .timeout(Duration.ofSeconds(5)).GET().build();
                var response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
                var choices = new ArrayList<SearchableChoiceScreen.Choice>();
                for (var entry : JsonParser.parseString(response.body()).getAsJsonArray()) {
                    var profile = entry.getAsJsonObject();
                    choices.add(new SearchableChoiceScreen.Choice("voicebox:" + profile.get("id").getAsString(),
                            profile.get("name").getAsString()));
                }
                choices.sort(Comparator.comparing(SearchableChoiceScreen.Choice::label));
                return List.copyOf(choices);
            } catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
        });
    }

    public static String voice(VillagerLike<?> person) {
        String selected = person.getTrackedValue(VillagerLike.VOICE_ID);
        // Namespacing prevents an ElevenLabs voice ID from being sent to Voicebox.
        return selected.startsWith("voicebox:") ? selected.substring(9) : "";
    }

    @Override public String cacheKey(String voice, String text) {
        return AudioCache.getHash("voicebox-pcm-v1:" + endpoint() + ":" + voice) + "/" + AudioCache.getHash(text);
    }

    static String payload(String voice, String text) {
        var json = new JsonObject();
        json.addProperty("model", "tts-1"); // Voicebox alias: profile's configured engine.
        json.addProperty("voice", voice);
        json.addProperty("input", text);
        json.addProperty("response_format", "pcm");
        return json.toString();
    }

    @Override public boolean downloadAudio(OutputStream output, String voice, String text) {
        try {
            var request = HttpRequest.newBuilder(URI.create(endpoint() + "/v1/audio/speech"))
                    .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                    .header("X-Voicebox-Client-Id", "mca-remastered")
                    .POST(HttpRequest.BodyPublishers.ofString(payload(voice, text))).build();
            var response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var input = response.body()) {
                if (response.statusCode() != 200) {
                    MCA.LOGGER.warn("Voicebox speech failed: HTTP {}", response.statusCode());
                    return false;
                }
                byte[] pcm = input.readNBytes(8 * 1024 * 1024 + 1);
                if (pcm.length > 8 * 1024 * 1024 || pcm.length < 128 || pcm.length % 2 != 0) return false;
                output.write(resample(pcm));
                return true;
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            MCA.LOGGER.warn("Voicebox speech unavailable ({})", e.getClass().getSimpleName());
            return false;
        }
    }

    /** 24 kHz signed little-endian mono -> MCA's 22.05 kHz PCM, preserving pitch. */
    static byte[] resample(byte[] input) {
        int samples = input.length / 2;
        int count = (int) ((long) samples * 22050 / 24000);
        byte[] result = new byte[count * 2];
        for (int i = 0; i < count; i++) {
            double position = i * 24000.0 / 22050;
            int left = Math.min((int) position, samples - 1), right = Math.min(left + 1, samples - 1);
            int a = (short) ((input[left * 2] & 255) | input[left * 2 + 1] << 8);
            int b = (short) ((input[right * 2] & 255) | input[right * 2 + 1] << 8);
            int value = (int) Math.round(a + (b - a) * (position - left));
            result[i * 2] = (byte) value;
            result[i * 2 + 1] = (byte) (value >> 8);
        }
        return result;
    }
}
