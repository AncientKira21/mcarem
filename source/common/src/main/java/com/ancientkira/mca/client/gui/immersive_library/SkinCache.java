package com.ancientkira.mca.client.gui.immersive_library;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.platform.NativeImage;
import com.ancientkira.mca.MCA;
import com.ancientkira.mca.client.gui.immersive_library.responses.ContentResponse;
import com.ancientkira.mca.client.gui.immersive_library.responses.Response;
import com.ancientkira.mca.client.gui.immersive_library.types.Content;
import com.ancientkira.mca.client.gui.immersive_library.types.LiteContent;
import com.ancientkira.mca.client.resources.SkinMeta;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.ancientkira.mca.client.gui.immersive_library.Api.request;

public class SkinCache {
    static final Map<Integer, Boolean> requested = new ConcurrentHashMap<>();
    static final Map<Integer, Integer> cachedVersions = new ConcurrentHashMap<>();
    static final Map<Integer, Identifier> textureIdentifiers = new ConcurrentHashMap<>();
    static final Map<Integer, NativeImage> images = new ConcurrentHashMap<>();
    static final Map<Integer, SkinMeta> metas = new ConcurrentHashMap<>();
    private static final Identifier DEFAULT_SKIN = MCA.locate("skins/empty.png");
    private static final Gson gson = new Gson();
    private static final Map<Integer, Boolean> fullCoverage = new ConcurrentHashMap<>();
    private static final Map<Integer, Boolean> eyeCoverage = new ConcurrentHashMap<>();
    private static final java.util.ArrayDeque<Integer> loadedOrder = new java.util.ArrayDeque<>();
    private record Coverage(int schema, int version, boolean full, boolean eyes, int accessories) {}
    private static final Map<Integer, Integer> classificationVersions = new ConcurrentHashMap<>();

    /** Classify candidates without allocating GPU textures for every rejected library entry. */
    public static void classify(LiteContent content) {
        Integer version = classificationVersions.get(content.contentid());
        if (version != null && version >= content.version()) return;
        load(content.contentid(), content.version(), false);
    }

    private static void rememberCoverage(int id, Coverage coverage) {
        fullCoverage.put(id, coverage.full());
        eyeCoverage.put(id, coverage.eyes());
        accessoryCoverage.put(id, coverage.accessories());
        classificationVersions.put(id, coverage.version());
    }

    private static final Map<Integer, Integer> accessoryCoverage = new ConcurrentHashMap<>();
    public static boolean classified(int id) {
        return fullCoverage.containsKey(id) && eyeCoverage.containsKey(id) && accessoryCoverage.containsKey(id);
    }
    public static boolean coversOnly(LiteContent content, int region) {
        Integer coverage = accessoryCoverage.get(content.contentid());
        if (coverage == null) { sync(content); return false; }
        return (coverage & region) != 0;
    }
    private static final java.util.concurrent.ExecutorService ASSETS = java.util.concurrent.Executors.newFixedThreadPool(4, task -> {
        Thread worker = new Thread(task, "MCA library asset"); worker.setDaemon(true); return worker;
    });
    private static final Map<Integer, Long> retryAfter = new ConcurrentHashMap<>();
    private static final Map<Integer, LiteContent> descriptions = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong REVISION = new java.util.concurrent.atomic.AtomicLong();
    public static boolean failed(int id) { return System.currentTimeMillis() < retryAfter.getOrDefault(id, 0L); }
    public static long revision() { return REVISION.get(); }
    public static Optional<LiteContent> description(int id) { return Optional.ofNullable(descriptions.get(id)); }
    public static void remember(LiteContent content) {
        if (content.userid() >= 0 && content.version() >= 0) {
            descriptions.put(content.contentid(), content);
            Integer classifiedVersion = classificationVersions.get(content.contentid());
            if (classifiedVersion != null && content.version() > classifiedVersion) {
                fullCoverage.remove(content.contentid());
                eyeCoverage.remove(content.contentid());
                accessoryCoverage.remove(content.contentid());
                classificationVersions.remove(content.contentid());
            }
        }
    }

    private static final Map<NativeImage,Boolean> completeSkins = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    public static boolean isCompleteSkin(LiteContent content) {
        Boolean classified = fullCoverage.get(content.contentid());
        if (classified != null) return classified;
        sync(content); return false;
    }
    public static boolean isCompleteSkin(int contentid) {
        Boolean classified = fullCoverage.get(contentid);
        if (classified != null) return classified;
        sync(contentid, -2); return false;
    }
    private static final Map<NativeImage,Boolean> eyeTextures = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    public static boolean isEyeTexture(LiteContent content) {
        Boolean classified = eyeCoverage.get(content.contentid());
        if (classified != null) return classified;
        sync(content); return false;
    }
    public static Optional<NativeImage> getImage(int contentid) {
        getTextureIdentifier(contentid);
        return Optional.ofNullable(images.get(contentid));
    }
    private static boolean isCompleteSkin(NativeImage image) {
        return completeSkins.computeIfAbsent(image,png -> ClassicSkinCoverage.qualifies(png.getWidth(),png.getHeight(),
                (x,y)->(png.getPixel(x,y)>>>24)&255));
    }


    private static File getFile(String key) {
        //noinspection ResultOfMethodCallIgnored
        new File("./immersive_library/").mkdirs();

        return new File("./immersive_library/" + key);
    }

    private static void write(String file, String content) {
        try {
            FileUtils.writeStringToFile(getFile(file), content, StandardCharsets.UTF_8, false);
        } catch (IOException e) {
            MCA.LOGGER.warn("Unable to write immersive library cache file {}", file, e);
        }
    }

    private static void write(String file, byte[] content) {
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(getFile(file)))) {
            out.write(content);
        } catch (IOException e) {
            MCA.LOGGER.warn("Unable to write immersive library cache file {}", file, e);
        }
    }

    private static String read(String file) throws IOException {
        return FileUtils.readFileToString(getFile(file), StandardCharsets.UTF_8);
    }

    /**
     * @param contentid The content id
     *                  Enforces re downloading the assets, mostly when local files appear to be corrupted
     */
    public static void enforceSync(int contentid) {
        try {
            Files.delete(getFile(contentid + ".version").toPath());
            cachedVersions.remove(contentid); fullCoverage.remove(contentid); eyeCoverage.remove(contentid); retryAfter.remove(contentid);
        } catch (IOException e) {
            MCA.LOGGER.warn(e);
        }
    }

    public static void sync(LiteContent content) {
        sync(content.contentid(), content.version());
    }

    /**
     * @param contentid      The content id
     * @param currentVersion The current version, used to invalidate the cache
     *                       Downloads the assets if they are not up to date
     */
    public static void sync(int contentid, int currentVersion) {
        load(contentid, currentVersion, true);
    }

    private static void load(int contentid, int currentVersion, boolean texture) {
        int loadedVersion = cachedVersions.getOrDefault(contentid, -1);
        if (textureIdentifiers.containsKey(contentid) && (currentVersion < 0 || loadedVersion >= currentVersion)
                && (contentid < 0 || descriptions.containsKey(contentid))) return;
        if (System.currentTimeMillis() < retryAfter.getOrDefault(contentid, 0L)
                || requested.putIfAbsent(contentid, true) != null) return;
        // Never read files, decode PNGs or contact the service during GUI extraction.
        CompletableFuture.runAsync(() -> {
            NativeImage image = null;
            try {
                if (!texture) {
                    try {
                        Coverage saved = gson.fromJson(read(contentid + ".coverage.json"), Coverage.class);
                        if (saved != null && saved.schema() == 1 && saved.version() >= currentVersion) {
                            rememberCoverage(contentid, saved);
                            REVISION.incrementAndGet();
                            requested.remove(contentid);
                            return;
                        }
                    } catch (Exception ignored) { }
                }
                int version = -1;
                try { version = Integer.parseInt(read(contentid + ".version").trim()); } catch (Exception ignored) { }
                if (!descriptions.containsKey(contentid)) {
                    try {
                        LiteContent saved = gson.fromJson(read(contentid + ".content.json"), LiteContent.class);
                        if (saved != null) descriptions.put(contentid, saved);
                    } catch (Exception ignored) { }
                }
                boolean needsDownload = contentid >= 0 && version < 0 || !getFile(contentid + ".png").isFile() || !getFile(contentid + ".json").isFile()
                        || currentVersion > version;
                if (contentid >= 0 && (needsDownload || !descriptions.containsKey(contentid))) {
                    Response response = request(Api.HttpMethod.GET, ContentResponse.class, "content/mca/" + contentid);
                    if (response instanceof ContentResponse(Content content)) {
                        LiteContent description = new LiteContent(content.contentid(), content.userid(), content.username(),
                                content.likes(), content.tags(), content.title(), content.version());
                        descriptions.put(contentid, description);
                        write(contentid + ".content.json", gson.toJson(description));
                        if (content.data() != null && !content.data().isBlank()) write(contentid + ".png", Base64.getDecoder().decode(content.data()));
                        write(contentid + ".json", content.meta());
                        version = content.version(); write(contentid + ".version", Integer.toString(version));
                    } else {
                        retryAfter.put(contentid, System.currentTimeMillis() + 30_000);
                    }
                }
                SkinMeta meta = gson.fromJson(read(contentid + ".json"), SkinMeta.class);
                if (meta == null) throw new IOException("Missing library metadata");
                try (InputStream stream = new FileInputStream(getFile(contentid + ".png"))) { image = NativeImage.read(stream); }
                // Coverage classification also stays off the render thread.
                fullCoverage.put(contentid, isCompleteSkin(image));
                NativeImage decoded = image;
                accessoryCoverage.put(contentid, AccessoryTextureCoverage.classify(decoded.getWidth(), decoded.getHeight(),
                        (x, y) -> (decoded.getPixel(x, y) >>> 24) & 255));
                eyeCoverage.put(contentid, eyeTextures.computeIfAbsent(decoded, png -> EyeTextureCoverage.qualifies(png.getWidth(), png.getHeight(),
                        (x,y) -> (png.getPixel(x,y) >>> 24) & 255)));
                Coverage coverage = new Coverage(1, version, fullCoverage.get(contentid),
                        eyeCoverage.get(contentid), accessoryCoverage.get(contentid));
                rememberCoverage(contentid, coverage);
                write(contentid + ".coverage.json", gson.toJson(coverage));
                if (!texture) {
                    decoded.close();
                    image = null;
                    requested.remove(contentid);
                    REVISION.incrementAndGet();
                    return;
                }
                int finalVersion = contentid < 0 ? Math.max(1, currentVersion) : version;
                Minecraft.getInstance().execute(() -> {
                    try {
                        Identifier identifier = Identifier.fromNamespaceAndPath("immersive_library", Integer.toString(contentid));
                        Minecraft.getInstance().getTextureManager().register(identifier,
                                new DynamicTexture(() -> "immersive_library/" + contentid, decoded));
                        metas.put(contentid, meta); images.put(contentid, decoded);
                        cachedVersions.put(contentid, finalVersion); textureIdentifiers.put(contentid, identifier);
                        loadedOrder.remove(contentid); loadedOrder.addLast(contentid);
                        while (loadedOrder.size() > 256) {
                            int evicted = loadedOrder.removeFirst();
                            Identifier evictedTexture = textureIdentifiers.remove(evicted);
                            images.remove(evicted);
                            if (evictedTexture != null) Minecraft.getInstance().getTextureManager().release(evictedTexture);
                        }
                        REVISION.incrementAndGet();
                    } finally { requested.remove(contentid); }
                });
            } catch (Exception error) {
                if (image != null) image.close();
                retryAfter.put(contentid, System.currentTimeMillis() + 30_000);
                if (contentid >= 0) try { Files.deleteIfExists(getFile(contentid + ".version").toPath()); } catch (IOException ignored) { }
                requested.remove(contentid);
                MCA.LOGGER.warn("Unable to load library asset {}", contentid, error);
            }
        }, ASSETS);
    }

    private static void logger(String s) {
        //noinspection ConstantConditions
        if (false) {
            MCA.LOGGER.info(s);
        }
    }

    public static Optional<SkinMeta> getMeta(LiteContent content) {
        sync(content);
        return Optional.ofNullable(metas.get(content.contentid()));
    }

    public static Optional<NativeImage> getImage(LiteContent content) {
        sync(content);
        return Optional.ofNullable(images.get(content.contentid()));
    }

    public static Identifier getTextureIdentifier(LiteContent content) {
        sync(content);
        touch(content.contentid());
        return textureIdentifiers.getOrDefault(content.contentid(), DEFAULT_SKIN);
    }

    /**
     * @param contentid The content id
     * @return The texture identifier
     * Unlike the other getters this function will sync at least once no matter the local state of the cache, as it lacks the current version
     */
    public static Identifier getTextureIdentifier(int contentid) {
        sync(contentid, -2);
        touch(contentid);
        return textureIdentifiers.getOrDefault(contentid, DEFAULT_SKIN);
    }

    private static void touch(int id) {
        if (Minecraft.getInstance().isSameThread() && textureIdentifiers.containsKey(id)) {
            loadedOrder.remove(id); loadedOrder.addLast(id);
        }
    }

    private static boolean previewUntinted;
    public static void setPreviewUntinted(boolean untinted){previewUntinted=untinted;}
    public static boolean isUntinted(String texture) {
        if(texture.equals("mca:temp"))return previewUntinted;
        var id=com.ancientkira.mca.util.ImmersiveLibraryIds.contentId(texture);
        return id.isPresent() && metas.containsKey(id.getAsInt()) && metas.get(id.getAsInt()).isUntinted();
    }

    public static boolean isLoaded(int contentid) {
        return textureIdentifiers.containsKey(contentid);
    }
}
