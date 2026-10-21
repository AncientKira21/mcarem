package com.ancientkira.mca.server;

import com.ancientkira.mca.Config;
import com.ancientkira.mca.MCA;
import com.ancientkira.mca.resources.data.skin.Clothing;
import com.ancientkira.mca.server.world.data.CustomClothingManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

/** Public metadata only; textures continue through the existing client-side skin cache. */
public final class OnlineClothingCatalog {
    static final long WAIT_MILLIS = Duration.ofHours(2).toMillis();
    private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Set<String> MCA_JOBS = Set.of("guard", "archer", "pillager", "vindicator", "evoker", "witch", "outlaw", "miner", "adventurer", "mercenary", "wandering_trader", "cultist");
    private record Candidate(int id, int version, String author, JsonObject metadata) { }
    private static final class State { long nextCheck; boolean loading; }
    private OnlineClothingCatalog() { }

    public static void tick(ServerLevel world) {
        if (world != world.getServer().overworld() || !Config.getInstance().automaticOnlineClothing) return;
        MinecraftServer server = world.getServer();
        State state = STATES.computeIfAbsent(server, ignored -> new State());
        long now = System.currentTimeMillis();
        if (state.loading || now < state.nextCheck) return;
        state.loading = true;
        state.nextCheck = now + Duration.ofMinutes(10).toMillis();
        String base = Config.getInstance().immersiveLibraryUrl.replaceAll("/+$", "");
        CompletableFuture.supplyAsync(() -> fetch(base)).whenComplete((candidates, error) -> server.execute(() -> {
            state.loading = false;
            if (!server.isRunning()) return;
            if (error != null) {
                MCA.LOGGER.warn("Automatic clothing library refresh failed; keeping the existing catalog.");
                return;
            }
            apply(candidates, System.currentTimeMillis());
        }));
    }

    private static List<Candidate> fetch(String base) {
        List<Candidate> result = new ArrayList<>();
        // The endpoint exposes no creation time. Each item/version waits two hours from first observation.
        for (int page = 0; page < 100; page++) {
            String query = "/v2/content/mca?whitelist=clothing&blacklist=invalid&filter_banned=true&filter_reported=true"
                    + "&include_meta=true&parse_meta=true&order=date&descending=true&limit=500&offset=" + page * 500;
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(base + query)).timeout(Duration.ofSeconds(20))
                        .header("Accept", "application/json").GET().build();
                HttpResponse<java.io.InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
                String json;
                try (var stream = response.body()) {
                    byte[] bytes = stream.readNBytes(8 * 1024 * 1024 + 1);
                    if (response.statusCode() != 200 || bytes.length > 8 * 1024 * 1024) throw new java.io.IOException("Catalog unavailable");
                    json = new String(bytes, StandardCharsets.UTF_8);
                }
                var rows = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("contents");
                if (rows == null) throw new java.io.IOException("Missing catalog");
                for (var value : rows) {
                    try {
                        JsonObject row = value.getAsJsonObject();
                        var tags = row.getAsJsonArray("tags");
                        if (tags == null || !tags.asList().stream().anyMatch(tag -> tag.getAsString().equals("clothing"))
                                || tags.asList().stream().anyMatch(tag -> tag.getAsString().equals("invalid"))) continue;
                        var raw = row.get("meta");
                        if (raw == null || raw.isJsonNull()) continue;
                        JsonObject meta = raw.isJsonObject() ? raw.getAsJsonObject() : JsonParser.parseString(raw.getAsString()).getAsJsonObject();
                        if (!meta.has("profession") || meta.get("profession").isJsonNull()
                                || meta.has("exclude") && meta.get("exclude").getAsBoolean()) continue;
                        String profession = normalizeProfession(meta.get("profession").getAsString());
                        if (profession.isBlank()) continue;
                        meta = meta.deepCopy();
                        meta.addProperty("profession", profession);
                        if (row.has("title") && !row.get("title").isJsonNull()) meta.addProperty("name", row.get("title").getAsString());
                        int id = row.get("contentid").getAsInt();
                        if (id <= 0) continue;
                        result.add(new Candidate(id, row.get("version").getAsInt(), row.get("username").getAsString(), meta));
                    } catch (RuntimeException malformedEntry) {
                        // A broken upload must not stop otherwise valid outfits from loading.
                    }
                }
                if (rows.size() < 500) return result;
            } catch (Exception error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new java.util.concurrent.CompletionException(error);
            }
        }
        throw new IllegalStateException("Catalog exceeds paging limit");
    }

    static String normalizeProfession(String value) {
        String name = value.strip().toLowerCase(java.util.Locale.ROOT);
        if (name.isEmpty()) return "";
        if (name.contains(".")) name = name.replaceFirst("\\.", ":");
        if (!name.contains(":")) name = (MCA_JOBS.contains(name) ? "mca:" : "minecraft:") + name;
        return name;
    }

    private static void apply(List<Candidate> candidates, long now) {
        var storage = CustomClothingManager.getClothing();
        Map<String, Clothing> additions = new HashMap<>();
        for (Candidate candidate : candidates) {
            String profession = candidate.metadata.get("profession").getAsString();
            Identifier job = Identifier.tryParse(profession);
            if (job == null || !BuiltInRegistries.VILLAGER_PROFESSION.containsKey(job)) continue;
            String id = "immersive_library:" + candidate.id;
            Clothing old = storage.getEntries().get(id);
            JsonObject meta = candidate.metadata.deepCopy();
            meta.addProperty("onlineAutomatic", true);
            meta.addProperty("onlineAuthor", candidate.author);
            meta.addProperty("onlineVersion", candidate.version);
            meta.addProperty("onlineAvailableAfter", old != null && old.onlineAutomatic && old.onlineVersion == candidate.version
                    ? old.onlineAvailableAfter : now + WAIT_MILLIS);
            float weight = candidate.author.equalsIgnoreCase(Config.getInstance().preferredOnlineClothingAuthor)
                    ? Math.max(1, Math.min(100, Config.getInstance().preferredOnlineClothingWeight)) : 1;
            meta.addProperty("chance", weight);
            try { additions.put(id, new Clothing(id, meta)); }
            catch (RuntimeException malformedMetadata) { /* Keep other usable uploads. */ }
        }
        storage.replaceAutomaticClothing(additions);
    }
}
