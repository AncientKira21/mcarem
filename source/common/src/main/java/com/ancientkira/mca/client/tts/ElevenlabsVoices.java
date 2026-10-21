package com.ancientkira.mca.client.tts;

import com.ancientkira.mca.Config;
import com.ancientkira.mca.client.gui.SearchableChoiceScreen.Choice;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Read-only account voice listing; no synthesis or credit use. */
public final class ElevenlabsVoices {
    private record Catalog(List<Choice> choices, Map<String, String> genders, boolean planVerified) { }
    private static CompletableFuture<Catalog> cached;
    private static String cachedKey;
    private static long fetchedAt;
    private ElevenlabsVoices() { }

    private static synchronized CompletableFuture<Catalog> catalog() {
        String rawKey = Config.getInstance().elevenlabsPrivateAPIkey;
        String key = rawKey == null ? "" : rawKey.strip();
        if (cached == null || cached.isCompletedExceptionally() || !Objects.equals(key, cachedKey)
                || System.currentTimeMillis() - fetchedAt > 300000) {
            cachedKey = key; fetchedAt = System.currentTimeMillis();
            cached = CompletableFuture.supplyAsync(() -> fetch(key));
        }
        return cached;
    }
    public static CompletableFuture<List<Choice>> load() { return catalog().thenApply(Catalog::choices); }
    public static CompletableFuture<String> automaticVoice(String gender, float gene) {
        return catalog().thenApply(catalog -> {
            var matching = catalog.choices().stream()
                    .filter(v -> gender.equalsIgnoreCase(catalog.genders().getOrDefault(v.id(), ""))).toList();
            var choices = matching.isEmpty() ? catalog.choices() : matching;
            if (choices.isEmpty()) return "";
            int index = Math.clamp((int) Math.floor(gene * choices.size()), 0, choices.size() - 1);
            return choices.get(index).id();
        });
    }
    public static synchronized boolean isPlanVerified() {
        if (cached == null || !cached.isDone() || cached.isCompletedExceptionally()) return false;
        return cached.join().planVerified();
    }
    public static void reloadKey() {
        var file = Config.getConfigFile();
        if (file.isFile()) {
            try (var reader = java.nio.file.Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                var settings = JsonParser.parseReader(reader).getAsJsonObject();
                Config.getInstance().elevenlabsPrivateAPIkey = string(settings, "elevenlabsPrivateAPIkey").strip();
            } catch (Exception exception) {
                throw new VoiceListException("gui.villager_editor.voices_failed");
            }
        }
        invalidate();
    }
    public static synchronized void invalidate() { cached = null; }
    public static String errorKey(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        return error instanceof VoiceListException ? error.getMessage() : "gui.villager_editor.voices_failed";
    }
    public static String failureKey(int httpStatus, String body) {
        String code = "";
        try {
            var root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("detail") && root.get("detail").isJsonObject()) {
                var detail = root.getAsJsonObject("detail");
                code = string(detail, "code");
                if (code.isEmpty()) code = string(detail, "status");
            }
        } catch (RuntimeException ignored) { }
        // Interpret known codes only: never echo provider messages or credentials.
        return switch (code) {
            case "missing_permissions", "insufficient_permissions", "forbidden", "workspace_access_denied" -> "gui.villager_editor.voices_forbidden";
            case "invalid_api_key", "missing_api_key", "invalid_authorization_header" -> "gui.villager_editor.voices_unauthorized";
            case "payment_required", "paid_plan_required", "subscription_required", "feature_not_available", "voice_access_denied", "model_access_denied" -> "gui.villager_editor.voices_payment";
            case "insufficient_credits", "quota_exceeded" -> "gui.villager_editor.voices_credits";
            case "rate_limit_exceeded", "concurrent_limit_exceeded", "too_many_concurrent_requests", "system_busy" -> "gui.villager_editor.voices_rate_limit";
            default -> switch (httpStatus) {
                case 401 -> "gui.villager_editor.voices_unauthorized";
                case 402 -> "gui.villager_editor.voices_payment";
                case 403 -> "gui.villager_editor.voices_forbidden";
                case 429 -> "gui.villager_editor.voices_rate_limit";
                default -> "gui.villager_editor.voices_failed";
            };
        };
    }

    private static final class VoiceListException extends IllegalStateException {
        VoiceListException(String key) { super(key); }
    }
    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
    private static HttpResponse<String> get(HttpClient client, String key, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("https://api.elevenlabs.io" + path))
                .timeout(Duration.ofSeconds(20)).header("xi-api-key", key).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private static String subscriptionTier(HttpClient client, String key) {
        try {
            var response = get(client, key, "/v1/user/subscription");
            if (response.statusCode() == 200) return string(JsonParser.parseString(response.body()).getAsJsonObject(), "tier");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            // A restricted key may list voices without permission to read the subscription.
        }
        return "";
    }
    private static boolean usable(JsonObject voice, String tier) {
        if (tier.isEmpty()) return true;
        String category = string(voice, "category");
        // Community/library and cloned voices require a paid plan for API synthesis.
        if (tier.equals("free") && (category.equals("professional") || category.equals("cloned")
                || voice.has("sharing") && voice.get("sharing").isJsonObject()
                    && !string(voice.getAsJsonObject("sharing"), "public_owner_id").isEmpty())) return false;
        if (voice.has("available_for_tiers") && voice.get("available_for_tiers").isJsonArray()) {
            var tiers = voice.getAsJsonArray("available_for_tiers");
            if (!tiers.isEmpty()) {
                for (var allowed : tiers) if (!allowed.isJsonNull() && allowed.getAsString().equalsIgnoreCase(tier)) return true;
                return false;
            }
        }
        return true;
    }
    private static Catalog fetch(String key) {
        if (key == null || key.isBlank()) throw new VoiceListException("gui.villager_editor.voices_key_required");
        var voices = new LinkedHashMap<String, Choice>();
        var genders = new HashMap<String, String>();
        Set<String> pages = new HashSet<>();
        String token = "";
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()) {
            String tier = subscriptionTier(client, key);
            do {
                if (!pages.add(token)) throw new IllegalStateException("Repeated page token");
                String path = "/v2/voices?page_size=100&include_total_count=false";
                if (!token.isEmpty()) path += "&next_page_token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
                var response = get(client, key, path);
                if (response.statusCode() == 401 || response.statusCode() == 402 || response.statusCode() == 403 || response.statusCode() == 429)
                    throw new VoiceListException(failureKey(response.statusCode(), response.body()));
                if (response.statusCode() != 200 && token.isEmpty()) response = get(client, key, "/v1/voices");
                if (response.statusCode() != 200) throw new VoiceListException(failureKey(response.statusCode(), response.body()));
                var page = JsonParser.parseString(response.body()).getAsJsonObject();
                if (!page.has("voices") || !page.get("voices").isJsonArray()) throw new IllegalStateException("Missing voices");
                for (var element : page.getAsJsonArray("voices")) {
                    if (!element.isJsonObject()) continue;
                    var voice = element.getAsJsonObject();
                    String id = string(voice, "voice_id");
                    if (!id.matches("[A-Za-z0-9_-]{1,128}") || !usable(voice, tier)) continue;
                    String name = string(voice, "name");
                    String accent = "";
                    if (voice.has("labels") && voice.get("labels").isJsonObject()) {
                        var labels = voice.getAsJsonObject("labels");
                        accent = string(labels, "accent");
                        genders.put(id, string(labels, "gender"));
                    }
                    voices.put(id, new Choice(id, (name.isEmpty() ? id : name) + (accent.isEmpty() ? "" : " — " + accent)));
                }
                if (!page.has("has_more") || !page.get("has_more").getAsBoolean()) break;
                token = string(page, "next_page_token");
                if (token.isEmpty()) throw new IllegalStateException("Missing page token");
            } while (true);
            return new Catalog(voices.values().stream().sorted(Comparator.comparing(Choice::label, String.CASE_INSENSITIVE_ORDER)).toList(),
                    Map.copyOf(genders), !tier.isEmpty());
        } catch (Exception exception) {
            if (exception instanceof VoiceListException known) throw known;
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("Could not load ElevenLabs voices");
        }
    }
}
