package com.ancientkira.mca.client.resources;

import com.ancientkira.mca.Config;
import com.ancientkira.mca.MCA;
import com.ancientkira.mca.client.gui.immersive_library.Api;
import com.ancientkira.mca.client.gui.immersive_library.Auth;
import com.ancientkira.mca.client.gui.immersive_library.responses.*;
import com.ancientkira.mca.resources.data.skin.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Editor-only views of public Silvernatural uploads and the signed-in user's submissions. */
public final class EditorLibraryCatalog {
    private record Entry(String id, String title, String author, boolean own, boolean hair, JsonObject meta) { }
    private static Map<String, Entry> entries = Map.of();
    private static long nextRefresh;
    private static boolean loading;
    private static String loadedToken;
    private EditorLibraryCatalog() { }
    public static java.util.List<net.minecraft.network.chat.Component> tooltip(String id,String kind) {
        Entry entry=entries.get(id);
        if(entry!=null)return java.util.List.of(net.minecraft.network.chat.Component.literal(entry.title),net.minecraft.network.chat.Component.literal(kind),net.minecraft.network.chat.Component.literal(entry.author));
        var local=com.ancientkira.mca.client.gui.immersive_library.LocalLibrary.contents().stream().filter(c->id.equals("immersive_library:"+c.contentid())).findFirst();
        return local.map(c->java.util.List.<net.minecraft.network.chat.Component>of(net.minecraft.network.chat.Component.literal(c.title()),net.minecraft.network.chat.Component.literal(kind),net.minecraft.network.chat.Component.literal(c.username())))
                .orElseGet(()->java.util.List.of(net.minecraft.network.chat.Component.literal(id.substring(id.lastIndexOf('/')+1).replace(".png","").replace('_',' ')),net.minecraft.network.chat.Component.literal(kind)));
    }
    public static boolean isOwn(String id) { return entries.containsKey(id) && entries.get(id).own; }
    public static boolean visible(String id) {
        Entry entry = entries.get(id);
        if (entry == null) return true;
        return entry.own ? Config.getInstance().showOwnLibrarySubmissions : Config.getInstance().enableSilvernaturalPack;
    }
    public static boolean matches(String id, String query) {
        Entry entry = entries.get(id);
        return entry != null && (entry.title + " " + entry.author).toLowerCase(Locale.ROOT).contains(query)
                || com.ancientkira.mca.client.gui.immersive_library.LocalLibrary.contents().stream()
                    .anyMatch(c->id.equals("immersive_library:"+c.contentid()) && (c.title()+" "+c.username()).toLowerCase(Locale.ROOT).contains(query));
    }
    public static Map<String, Clothing> clothing() {
        Map<String, Clothing> result = new HashMap<>();
        entries.values().stream().filter(e -> !e.hair && visible(e.id)).forEach(e -> {
            try { result.put(e.id, new Clothing(e.id, e.meta)); } catch (RuntimeException ignored) { }
        });
        for(var local:com.ancientkira.mca.client.gui.immersive_library.LocalLibrary.contents())if(local.hasTag("clothing")) {
            JsonObject meta=new JsonObject();meta.addProperty("name",local.title());meta.addProperty("onlineAuthor",local.username());result.put("immersive_library:"+local.contentid(),new Clothing("immersive_library:"+local.contentid(),meta));
        }
        return result;
    }
    public static Map<String, HairStyle> hairStyles() {
        Map<String, HairStyle> result = new HashMap<>();
        entries.values().stream().filter(e -> e.hair && visible(e.id)).forEach(e -> {
            try { result.put(e.id, HairStyle.fromHair(new Hair(e.id, e.meta))); } catch (RuntimeException ignored) { }
        });
        for(var local:com.ancientkira.mca.client.gui.immersive_library.LocalLibrary.contents())if(local.hasTag("hair"))result.put("immersive_library:"+local.contentid(),HairStyle.fromHair(new Hair("immersive_library:"+local.contentid())));
        return result;
    }
    public static void refresh(Runnable changed) {
        String token = Auth.getToken();
        if (loading || Objects.equals(token, loadedToken) && System.currentTimeMillis() < nextRefresh) return;
        loadedToken = token; loading = true; nextRefresh = System.currentTimeMillis() + 300000;
        CompletableFuture.supplyAsync(() -> load(token)).whenComplete((loaded, error) -> Minecraft.getInstance().execute(() -> {
            loading = false;
            if (!Objects.equals(token, Auth.getToken())) { nextRefresh = 0; return; }
            if (error != null) { MCA.LOGGER.warn("Editor clothing/hair library refresh failed: {}", error.toString()); return; }
            entries = Map.copyOf(loaded);
            ClientSkinCatalog.refreshEditorEntries();
            changed.run();
        }));
    }
    private static Map<String, Entry> load(String token) {
        Map<String, Entry> result = new HashMap<>();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        for (String kind : List.of("clothing", "hair")) {
            for (int page = 0; page < 100; page++) {
                String url = Config.getInstance().immersiveLibraryUrl + "/v2/content/mca?whitelist=" + kind
                        + "&blacklist=invalid&filter_banned=true&filter_reported=true&include_meta=true&parse_meta=true&limit=500&offset=" + page * 500;
                try {
                    var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build();
                    var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                    JsonArray rows;
                    try (var stream = response.body()) {
                        byte[] bytes = stream.readNBytes(8 * 1024 * 1024 + 1);
                        if (response.statusCode() != 200 || bytes.length > 8 * 1024 * 1024) break;
                        rows = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("contents");
                    }
                    if (rows == null) break;
                    for (var value : rows) {
                        try {
                            JsonObject row = value.getAsJsonObject();
                            if (!row.get("username").getAsString().equalsIgnoreCase("Silvernatural")) continue;
                            JsonElement metadata = row.get("meta");
                            JsonObject meta = metadata.isJsonObject() ? metadata.getAsJsonObject() : JsonParser.parseString(metadata.getAsString()).getAsJsonObject();
                            add(result, row.get("contentid").getAsInt(), row.get("title").getAsString(), "Silvernatural", false, kind.equals("hair"), meta);
                        } catch (RuntimeException ignored) { }
                    }
                    if (rows.size() < 500) break;
                } catch (Exception error) {
                    if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (token != null && Api.request(Api.HttpMethod.GET, UserResponse.class, "user/mca/me") instanceof UserResponse user) {
            for (var submission : user.user().submissions()) {
                if (Api.request(Api.HttpMethod.GET, ContentResponse.class, "content/mca/" + submission.contentid()) instanceof ContentResponse response) {
                    var content = response.content();
                    if (!content.tags().contains("clothing") && !content.tags().contains("hair")) continue;
                    try { add(result, content.contentid(), content.title(), content.username(), true,
                            content.tags().contains("hair"), JsonParser.parseString(content.meta()).getAsJsonObject()); }
                    catch (RuntimeException ignored) { }
                }
            }
        }
        return result;
    }
    private static void add(Map<String, Entry> result, int contentId, String title, String author, boolean own, boolean hair, JsonObject source) {
        String id = "immersive_library:" + contentId;
        JsonObject meta = source.deepCopy();
        meta.addProperty("name", title); meta.addProperty("onlineAuthor", author);
        result.put(id, new Entry(id, title, author, own, hair, meta));
    }
}
