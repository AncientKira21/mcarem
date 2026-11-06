package com.ancientkira.mca.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.ancientkira.mca.Config;
import com.ancientkira.mca.MCA;
import com.ancientkira.mca.MCAClient;
import com.ancientkira.mca.client.gui.immersive_library.*;
import com.ancientkira.mca.client.gui.immersive_library.responses.*;
import com.ancientkira.mca.client.gui.immersive_library.types.Content;
import com.ancientkira.mca.client.gui.immersive_library.types.LiteContent;
import com.ancientkira.mca.client.gui.immersive_library.types.User;
import com.ancientkira.mca.client.gui.widget.*;
import com.ancientkira.mca.client.resources.*;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.VillagerLike;
import com.ancientkira.mca.entity.ai.Genetics;
import com.ancientkira.mca.entity.ai.relationship.Gender;
import com.ancientkira.mca.network.Network;
import com.ancientkira.mca.network.c2s.AddCustomClothingMessage;
import com.ancientkira.mca.network.c2s.RemoveCustomClothingMessage;
import com.ancientkira.mca.registry.EntitiesMCA;
import com.ancientkira.mca.resources.data.skin.Clothing;
import com.ancientkira.mca.resources.data.skin.Hair;
import com.ancientkira.mca.resources.data.skin.HairStyle;
import com.ancientkira.mca.resources.data.skin.SkinListEntry;
import com.ancientkira.mca.util.compat.ButtonWidget;
import com.ancientkira.mca.util.localization.FlowingText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static com.ancientkira.mca.client.gui.immersive_library.Api.request;

public class SkinLibraryScreen extends Screen implements SkinListUpdateListener {
    private static final java.util.concurrent.atomic.AtomicInteger PREVIEW_IDS = new java.util.concurrent.atomic.AtomicInteger(-1);
    static final int CLOTHES_H = 7;
    static final int CLOTHES_V = 2;
    static final int CLOTHES_PER_PAGE = CLOTHES_H * CLOTHES_V + 1;
    private static final Identifier TEMPLATE_IDENTIFIER = MCA.locate("textures/skin_template.png");
    private static final Identifier EMPTY_IDENTIFIER = MCA.locate("skins/empty.png");
    private static final Identifier CANVAS_IDENTIFIER = MCA.locate("temp");
    private static final float CANVAS_SCALE = 2.35f;
    protected final VillagerEntityMCA villagerVisualization = Objects.requireNonNull(EntitiesMCA.PERSON.create(Objects.requireNonNull(Minecraft.getInstance().level), EntitySpawnReason.LOAD));
    private final List<LiteContent> serverContent = new ArrayList<>();
    private final ColorSelector color = new ColorSelector();
    private final VillagerEditorScreen previousScreen;
    private final List<LiteContent> contents = new ArrayList<>();
    private String filteredString = "";
    private SortingMode sortingMode = SortingMode.RECOMMENDATIONS;
    private boolean filterInvalidSkins = true;
    private boolean moderatorMode = false;
    private SkinType category = SkinType.CLOTHING;
    private boolean filterHair = false;
    private boolean filterClothing = false;
    private SubscriptionFilter subscriptionFilter = SubscriptionFilter.LIBRARY;
    private User currentUser;
    private int selectionPage;
    private LiteContent focusedContent;
    private LiteContent hoveredContent;
    private LiteContent deleteConfirmationContent;
    private LiteContent reportConfirmationContent;
    private Page page;
    private ContentQuery loadedQuery;
    private ContentQuery pendingQuery;
    private ButtonWidget pageWidget;
    private Workspace workspace;
    private int activeMouseButton;
    private int lastPixelMouseX;
    private int lastPixelMouseY;
    private float x0, x1, y0, y1;
    private boolean isPanning;
    private boolean hasPanned;
    private double lastMouseX;
    private double lastMouseY;
    private int timeSinceLastRebuild;
    private boolean contentRefreshPending;
    private Component error;
    private boolean authenticated = false;
    private boolean awaitingAuthentication = false;
    private boolean isBrowserOpen = false;
    private boolean uploading = false;
    private Thread thread;
    private EditBox textFieldWidget;
    private boolean skipHairWarning;
    private final List<LiteContent> remoteContents = new ArrayList<>();
    private final java.util.Map<Integer, VillagerEntityMCA> libraryPreviews = new java.util.HashMap<>();
    private final CompoundTag basePreviewData;
    private final String selectedClothing;
    private final Map<SkinType, String> wornTextures = new EnumMap<>(SkinType.class);
    private long assetRevision = -1;
    private int availablePages = 1;
    private final List<LiteContent> categoryCandidates = new ArrayList<>();
    private ContentQuery categoryScanQuery;
    private int categoryScanPage;
    private boolean categoryScanPending, categoryScanExhausted;


    private record ContentQuery(int page, String search, SortingMode sorting, boolean filterInvalid, boolean filterHair, boolean filterClothing, boolean moderatorMode, SkinType category) {
        Map<String, String> parameters() {
            List<String> blacklist = new ArrayList<>(3);
            if (filterInvalid) blacklist.add("invalid");
            if (filterHair) blacklist.add("hair");
            if (filterClothing) blacklist.add("clothing");
            return Map.of("whitelist", (category.usesCoverage() ? search : category.tag()+(search.isBlank()?"":","+search)), "blacklist", String.join(",", blacklist), "order", sorting.order,
                    "descending", "true", "offset", Integer.toString(page * CLOTHES_PER_PAGE),
                    "limit", Integer.toString(CLOTHES_PER_PAGE), "moderator", Boolean.toString(moderatorMode));
        }
    }

    public SkinLibraryScreen() {
        this(null, null);
    }

    public SkinLibraryScreen(VillagerEditorScreen screen, VillagerEntityMCA villagerVisualization) {
        super(Component.translatable("gui.skin_library.title"));
        this.villagerVisualization.setId(PREVIEW_IDS.getAndDecrement());

        this.previousScreen = screen;
        if(screen!=null && screen.getPage().equals("hair"))category=SkinType.HAIR;

        if (this.previousScreen instanceof NeedleScreen) {
            filterHair = true;
        }
        if (this.previousScreen instanceof CombScreen) {
            category = SkinType.HAIR;
        }

        if (villagerVisualization != null) {
            this.villagerVisualization.load(TagValueInput.create(ProblemReporter.DISCARDING, this.villagerVisualization.registryAccess(), saveEntityData(villagerVisualization)));
        } else {
            assert Minecraft.getInstance().player != null;
            VillagerLike<?> villagerLike = MCAClient.getPlayerData(Minecraft.getInstance().player.getUUID()).orElse(null);
            if (villagerLike instanceof VillagerEntityMCA villager) {
                this.villagerVisualization.load(TagValueInput.create(ProblemReporter.DISCARDING, this.villagerVisualization.registryAccess(), saveEntityData(villager)));
            }
        }
        this.villagerVisualization.setUUID(UUID.randomUUID());
        selectedClothing = this.villagerVisualization.getClothes();
        basePreviewData = saveEntityData(this.villagerVisualization);
        wornTextures.put(SkinType.CLOTHING, selectedClothing);
        wornTextures.put(SkinType.HAIR, this.villagerVisualization.getHair());
        wornTextures.put(SkinType.CLASSIC, this.villagerVisualization.getClassicSkin());
        wornTextures.put(SkinType.EYES, this.villagerVisualization.getTrackedValue(VillagerLike.EYE_TEXTURE));
        for (SkinType type : SkinType.values()) if (type.accessorySlot() >= 0)
            wornTextures.put(type, this.villagerVisualization.getAccessory(type.accessorySlot()));
    }

    public SkinLibraryScreen category(SkinType type) {category=type;filterHair=false;filterClothing=false;return this;}

    private static CompoundTag saveEntityData(VillagerEntityMCA entity) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
        entity.save(output);
        return output.buildResult();
    }

    private static MutableComponent getProfessionText(String profession) {
        if (MCA.isBlankString(profession)) {
            return Component.translatable("entity.minecraft.villager");
        }
        String fallback = profession.substring(profession.lastIndexOf('.') + 1);
        fallback = Character.toUpperCase(fallback.charAt(0)) + fallback.substring(1);
        return Component.translatableWithFallback("entity.minecraft.villager." + profession, fallback);
    }

    @Override
    public void onClose() {
        if (previousScreen == null) {
            super.onClose();
        } else {
            Minecraft.getInstance().setScreen(previousScreen);
        }
    }

    @Override
    protected void init() {
        super.init();

        refreshServerContent();

        thread = Thread.currentThread();

        if (page == null) {
            if (Auth.loadToken() == null) {
                setPage(Page.LIBRARY);
            } else {
                setPage(Page.LOGIN);
            }
        } else {
            refreshPage();
        }
    }

    private ContentQuery createQuery() {
        return new ContentQuery(selectionPage, filteredString, sortingMode, filterInvalidSkins, filterHair, filterClothing, moderatorMode, category);
    }

    private void loadPage() {
        if (subscriptionFilter != SubscriptionFilter.LIBRARY) {
            return;
        }

        if (category.usesCoverage()) {
            loadCategoryPage();
            return;
        }
        ContentQuery query = createQuery();
        if (query.equals(loadedQuery) || query.equals(pendingQuery)) {
            return;
        }

        loadedQuery = null;
        remoteContents.clear();
        pendingQuery = query;

        CompletableFuture
                .supplyAsync(() -> request(Api.HttpMethod.GET, ContentListResponse.class, "v2/content/mca", query.parameters()))
                .thenAccept(response -> Minecraft.getInstance().executeIfPossible(() -> acceptContents(query, response)));
    }

    private LiteContent resolvedContent(LiteContent content) {
        return content.userid() < 0 || content.title().equalsIgnoreCase("unknown")
                ? SkinCache.description(content.contentid()).orElse(content) : content;
    }
    private Optional<LiteContent> onlineWornContent() {
        String texture = wornTextures.getOrDefault(category, "");
        var id = com.ancientkira.mca.util.ImmersiveLibraryIds.contentId(texture);
        if (id.isEmpty()) return Optional.empty();
        LiteContent content = SkinCache.description(id.getAsInt()).orElseGet(() -> new LiteContent(id.getAsInt(), -1, "", -1,
                Set.of(category.tag()), "Asset #" + id.getAsInt(), -1));
        SkinCache.sync(content); return Optional.of(content);
    }
    private void loadCategoryPage() {
        ContentQuery signature = new ContentQuery(0, filteredString, sortingMode, filterInvalidSkins, filterHair, filterClothing, moderatorMode, category);
        if (!signature.equals(categoryScanQuery)) {
            categoryScanQuery = signature; categoryScanPage = 0; categoryScanExhausted = false;
            categoryScanPending = false; categoryCandidates.clear();
            Map<String, String> seedParameters = new HashMap<>(signature.parameters());
            String tags = "mcarem," + signature.category().tag();
            if (!signature.search().isBlank()) tags += "," + signature.search();
            seedParameters.put("whitelist", tags);
            CompletableFuture.supplyAsync(() -> request(Api.HttpMethod.GET, ContentListResponse.class,
                            "v2/content/mca", seedParameters))
                    .thenAccept(response -> Minecraft.getInstance().execute(() -> {
                        if (!signature.equals(categoryScanQuery)) return;
                        if (response instanceof ContentListResponse(LiteContent[] loaded)) {
                            List<LiteContent> trusted = Arrays.stream(loaded)
                                    .filter(content -> content.hasTag("mcarem") && content.hasTag(signature.category().tag()))
                                    .toList();
                            trusted.forEach(SkinCache::remember);
                            Set<Integer> ids = trusted.stream().map(LiteContent::contentid).collect(java.util.stream.Collectors.toSet());
                            categoryCandidates.removeIf(content -> ids.contains(content.contentid()));
                            categoryCandidates.addAll(0, trusted);
                            refreshContentList();
                        }
                    }));
        }
        if (categoryScanPending || categoryScanExhausted) return;
        int ready = 0;
        int waitingForAssets = 0;
        for (LiteContent candidate : categoryCandidates) {
            if (!candidate.hasTag("mcarem") && !SkinCache.classified(candidate.contentid())
                    && !SkinCache.failed(candidate.contentid())) {
                SkinCache.classify(candidate);
                waitingForAssets++;
            } else if (category.matches(candidate)) ready++;
        }
        if (ready >= (selectionPage + 1) * CLOTHES_PER_PAGE) return;
        // Pipeline one page ahead instead of waiting for every image in the previous page.
        if (waitingForAssets > CLOTHES_PER_PAGE) return;
        categoryScanPending = true;
        ContentQuery batch = new ContentQuery(categoryScanPage, filteredString, sortingMode, filterInvalidSkins, filterHair, filterClothing, moderatorMode, category);
        CompletableFuture.supplyAsync(() -> request(Api.HttpMethod.GET, ContentListResponse.class, "v2/content/mca", batch.parameters()))
                .thenAccept(response -> Minecraft.getInstance().execute(() -> {
                    if (!signature.equals(categoryScanQuery)) return;
                    categoryScanPending = false;
                    if (response instanceof ContentListResponse(LiteContent[] loaded)) {
                        for (LiteContent content : loaded) {
                            SkinCache.remember(content);
                            if (categoryCandidates.stream().noneMatch(c -> c.contentid() == content.contentid())) categoryCandidates.add(content);
                        }
                        categoryScanPage++; categoryScanExhausted = loaded.length < CLOTHES_PER_PAGE;
                    } else { categoryScanExhausted = true; setError(Component.translatable("gui.skin_library.list_fetch_failed")); }
                    refreshContentList();
                }));
    }

    private void acceptContents(ContentQuery query, Response response) {
        if (!query.equals(pendingQuery)) {
            return;
        }

        pendingQuery = null;
        if (!query.equals(createQuery())) {
            return;
        }

        if (response instanceof ContentListResponse(LiteContent[] loadedContents)) {
            loadedQuery = query;
            remoteContents.clear();
            remoteContents.addAll(List.of(loadedContents));
            remoteContents.forEach(SkinCache::remember);
            clearError();
            refreshContentList();
        } else {
            setError(Component.translatable("gui.skin_library.list_fetch_failed"));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        final Matrix3x2fStack matrices = context.pose();

        hoveredContent = null;

        villagerVisualization.setAge(0);
        villagerVisualization.refreshDimensions();

        List<Component> tooltip = null;

        switch (page) {
            case LIBRARY -> {
                List<LiteContent> visibleContents = contents;
                int i = 0;
                for (int y = 0; y < CLOTHES_V; y++) {
                    for (int x = 0; x < CLOTHES_H + y; x++) {
                        if (visibleContents.size() > i) {
                            LiteContent c = visibleContents.get(i);

                            VillagerEntityMCA preview = libraryPreviews.computeIfAbsent(c.contentid(), id -> {
                                var dummy = Objects.requireNonNull(EntitiesMCA.PERSON.create(minecraft.level, EntitySpawnReason.LOAD));
                                dummy.setId(PREVIEW_IDS.getAndDecrement());
                                return dummy;
                            });
                            setDummyTexture(preview, c);

                            int cx = width / 2 + (int) ((x - CLOTHES_H / 2.0 + 0.5 - 0.5 * (y % 2)) * 55);
                            int cy = height / 2 + (int) ((y - CLOTHES_V / 2.0 + 0.5) * 80);

                            if (Math.abs(cx - mouseX) <= 15 && Math.abs(cy - mouseY - 10) <= 25) {
                                hoveredContent = c;
                                tooltip = getMetaDataText(c);
                            }

                            preview.getGenetics().setGender(SkinCache.getMeta(c).map(SkinMeta::getGender).orElse(Gender.MALE).binary());
                            preview.refreshDimensions();
                            WidgetUtils.drawLibraryEntity(context, cx - 25, cy - 50, cx + 25, cy + 30,
                                    hoveredContent == c ? 30 : 28, mouseX, mouseY, preview);
                            i++;
                        } else {
                            break;
                        }
                    }
                }

                if (!authenticated && (subscriptionFilter == SubscriptionFilter.LIKED || subscriptionFilter == SubscriptionFilter.SUBMISSIONS)) {
                    drawTextBox(context, Component.translatable("gui.skin_library.like_locked"));
                }
            }
            case EDITOR_LOCKED -> {
                drawTextBox(context, Component.translatable("gui.skin_library.locked"));
            }
            case EDITOR_PREPARE -> {
                drawTextBox(context, Component.translatable("gui.skin_library.drop"));
            }
            case EDITOR_TYPE -> {
                drawTextBox(context, Component.translatable("gui.skin_library.prepare"));
            }
            case DELETE -> {
                drawTextBox(context, Component.translatable("gui.skin_library.delete_confirm"));
            }
            case REPORT -> {
                drawTextBox(context, Component.translatable("gui.skin_library.report_confirm"));
            }
            case EDITOR -> {
                if (workspace.isDirty()) {
                    workspace.backendTexture.upload();
                    Minecraft.getInstance().getTextureManager().register(CANVAS_IDENTIFIER, workspace.backendTexture);
                    workspace.setDirty(false);
                }

                SkinCache.setPreviewUntinted(workspace.skinType==SkinType.HAIR && workspace.untinted);
                //painting area
                int tw = 64;
                int th = 64;
                matrices.pushMatrix();
                matrices.translate(width / 2.0f - tw * CANVAS_SCALE / 2.0f, height / 2.0f - th * CANVAS_SCALE / 2.0f);
                matrices.scale(CANVAS_SCALE, CANVAS_SCALE);

                // Calculate the clamped vertex and UV coordinates
                float vx0 = Mth.clamp(0, x0, x1);
                float vx1 = Mth.clamp(1, x0, x1);
                float vy0 = Mth.clamp(0, y0, y1);
                float vy1 = Mth.clamp(1, y0, y1);

                float uvx0 = (vx0 - x0) / (x1 - x0);
                float uvx1 = (vx1 - x0) / (x1 - x0);
                float uvy0 = (vy0 - y0) / (y1 - y0);
                float uvy1 = (vy1 - y0) / (y1 - y0);

                //draw template
                WidgetUtils.drawTexturedQuad(context, TEMPLATE_IDENTIFIER, vx0 * 64, vx1 * 64, vy0 * 64, vy1 * 64, uvx0, uvx1, uvy0, uvy1, ARGB.white(0.25f));

                //draw canvas
                WidgetUtils.drawTexturedQuad(context, CANVAS_IDENTIFIER, vx0 * 64, vx1 * 64, vy0 * 64, vy1 * 64, uvx0, uvx1, uvy0, uvy1);

                //border
                WidgetUtils.drawRectangle(context, -1, -1, tw + 1, th + 1, 0xaaffffff);

                matrices.popMatrix();

                //dummy
                applyBasePreview(villagerVisualization);
                if(workspace.skinType==SkinType.CLASSIC) {
                    villagerVisualization.setClassicSkin(CANVAS_IDENTIFIER.toString());
                } else if(workspace.skinType.accessorySlot()>=0) {
                    villagerVisualization.setAccessory(workspace.skinType.accessorySlot(),CANVAS_IDENTIFIER.toString());
                } else if (workspace.skinType == SkinType.CLOTHING) {
                    villagerVisualization.setClassicSkin("");
                    villagerVisualization.setClothes(CANVAS_IDENTIFIER);
                } else {
                    villagerVisualization.setClassicSkin("");
                    villagerVisualization.setHairStyle(HairStyle.singleLayer(CANVAS_IDENTIFIER.toString(), workspace.gender.binary(), 1.0F));
                    villagerVisualization.setClothes(EMPTY_IDENTIFIER);
                }

                int cx = width / 2 + 150;
                int cy = height / 2 - 10;

                villagerVisualization.getGenetics().setGender(workspace.gender.binary());
                WidgetUtils.drawLibraryEntity(context, cx - 50, cy - 50, cx + 50, cy + 50, 50, mouseX, mouseY, villagerVisualization);

                if (workspace.skinType == SkinType.HAIR) {
                    context.centeredText(font, Component.translatable("gui.skin_library.hair_color"), width / 2 - 150, height / 2 - 40, 0xAAFFFFFF);
                }

                //hovered element
                int x = (int) getPixelX();
                int y = (int) getPixelY();
                if (x >= 0 && x < 64 && y >= 0 && y < 64) {
                    SkinLocations.Part part = SkinLocations.LOOKUP[x][y];
                    if (part != null) {
                        Component text = part.getTranslation();
                        int textWidth = font.width(text);
                        WidgetUtils.drawTooltip(context, font, text, width / 2 - textWidth / 2 - 12, height / 2 - 68);
                    }
                }
            }
            case LOGIN -> {
                pollAuthentication();

                // auth hint
                if (isBrowserOpen) {
                    drawTextBox(context, Component.translatable("gui.skin_library.authenticating_browser"));
                } else if (error != null) {
                    drawTextBox(context, Component.translatable("gui.skin_library.authenticating"));
                } else {
                    drawTextBox(context, Component.translatable("gui.skin_library.authenticating").append(Component.literal(" " + ".".repeat((int) (System.currentTimeMillis() / 500 % 4)))));
                }
            }
            case DETAIL -> {
                //dummy
                setDummyTexture(villagerVisualization, focusedContent);

                int cx = width / 2;
                int cy = height / 2;

                villagerVisualization.getGenetics().setGender(SkinCache.getMeta(focusedContent).map(SkinMeta::getGender).orElse(Gender.MALE).binary());
                WidgetUtils.drawLibraryEntity(context, cx - 30, cy - 60, cx + 30, cy + 60, 60, mouseX, mouseY, villagerVisualization);

                //metadata
                WidgetUtils.drawTooltip(context, font, getMetaDataText(focusedContent), width / 2 + 200, height / 2 - 50);
            }
        }

        if (tooltip != null && !DropdownWidget.isOpen(this)) {
            WidgetUtils.drawTooltip(context, font, tooltip, mouseX, mouseY);
        }

        if (error != null) {
            context.centeredText(font, error, width / 2, height / 2, 0xFFFF0000);
        }
        DropdownWidget.extractOverlay(this, context, mouseX, mouseY, delta);
    }

    private void setDummyTexture(VillagerEntityMCA preview, LiteContent content) {
        applyBasePreview(preview);
        if (category == SkinType.EYES || SkinType.of(content)==SkinType.EYES) {
            preview.setClassicSkin("");
            preview.setSkin("");
            preview.setTrackedValue(VillagerLike.SKIN_COLOR, 0xFF000000);
            preview.setTrackedValue(VillagerLike.EYE_TEXTURE,"immersive_library:"+content.contentid());
            preview.setTrackedValue(VillagerLike.EYES_OVER_SKIN,true);
        } else if (SkinType.of(content)==SkinType.CLASSIC) {
            SkinCache.getTextureIdentifier(content);
            preview.setClassicSkin("immersive_library:"+content.contentid());
        } else if (SkinType.of(content).accessorySlot()>=0) {
            preview.setAccessory(SkinType.of(content).accessorySlot(),SkinCache.getTextureIdentifier(content).toString());
        } else if (content.hasTag("clothing")) {
            preview.setClassicSkin("");
            preview.setHair("");
            preview.setHairStyleId("");
            preview.clearLayeredHair();
            preview.setClothes(SkinCache.getTextureIdentifier(content));
        } else {
            preview.setClassicSkin("");
            preview.setHairStyle(HairStyle.singleLayer(SkinCache.getTextureIdentifier(content).toString(), preview.getGenetics().getGender(), 1.0F));
            preview.setClothes(EMPTY_IDENTIFIER);
        }
    }

    private void applyBasePreview(VillagerEntityMCA preview) {
        if (basePreviewData != null) {
            var data = basePreviewData.copy();
            data.getCompound(VillagerEntityMCA.MCA_DATA_KEY).ifPresent(mca -> mca.putInt("PlayerModel", 0));
            data.remove("UUID"); data.remove("Passengers"); data.remove("Invisible");
            data.remove("DeathTime"); data.remove("HurtTime"); data.remove("Fire");
            data.getCompound(VillagerEntityMCA.MCA_DATA_KEY).ifPresent(mca -> {
                mca.putLong("KidnappedUntil", 0); mca.remove("CaptivityState");
            });
            UUID identity = preview.getUUID();
            preview.load(TagValueInput.create(ProblemReporter.DISCARDING, preview.registryAccess(), data));
            preview.setUUID(identity);
            ((com.ancientkira.mca.entity.RevivalState) preview).mca$resetDeathState();
            preview.removeAllEffects();
            preview.setInvisible(false);
            preview.setHealth(preview.getMaxHealth());
            preview.deathTime = 0;
            preview.setPose(net.minecraft.world.entity.Pose.STANDING);
            preview.setAge(0);
            preview.refreshDimensions();
            preview.snapTo(0, 0, 0);
        }
    }

    private List<Component> getMetaDataText(LiteContent content) {
        return getLoadedMetaDataText(resolvedContent(content));
    }
    private List<Component> getLoadedMetaDataText(LiteContent content) {
        Optional<SkinMeta> meta = SkinCache.getMeta(content);
        if (meta.isEmpty()) {
            return List.of(Component.literal(content.title()),Component.translatable("gui.skin_library.meta.by",content.username()),SkinType.of(content).text());
        } else {
            List<Component> wrap = FlowingText.wrap(Component.literal(String.join(", ", content.tags())).withStyle(ChatFormatting.YELLOW), 160);
            ArrayList<Component> texts = new ArrayList<>(List.of(
                    Component.literal(content.title()),
                    SkinType.of(content).text(),
                    content.users()==null?Component.translatable("gui.skin_library.uses_unavailable"):Component.translatable("gui.skin_library.uses",content.users()),
                    Component.translatable("gui.skin_library.meta.by", content.username()).withStyle(ChatFormatting.ITALIC),
                    (content.likes() >= 0 ? Component.translatable("gui.skin_library.meta.likes", content.likes())
                            : Component.translatable("gui.skin_library.meta.likes_unavailable")).withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.skin_library.gender", meta.get().getGender() == Gender.MALE ? Component.translatable("gui.villager_editor.masculine") : (meta.get().getGender() == Gender.FEMALE ? Component.translatable("gui.villager_editor.feminine") : Component.translatable("gui.villager_editor.neutral"))),
                    Component.translatable("gui.skin_library.profession", getProfessionText(meta.get().getProfession())),
                    Component.translatable("gui.skin_library.temperature", Component.translatable("gui.skin_library.temperature." + (meta.get().getTemperature() + 2))),
                    Component.translatable("gui.skin_library.chance_val", (int) (meta.get().getChance() * 100)).withStyle(ChatFormatting.GRAY)
            ));
            if(content.hasTag("hair"))texts.add(Component.translatable(meta.get().isUntinted()?"gui.skin_library.untinted":"gui.skin_library.tinted"));
            texts.addAll(wrap);

            if (content.tags().contains("invalid")) {
                texts.add(Component.translatable("gui.skin_library.probably_not_valids").withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.RED));
            }

            return texts;
        }
    }

    private double getScreenScaleX() {
        assert minecraft != null;
        return (double) this.minecraft.getWindow().getGuiScaledWidth() / (double) this.minecraft.getWindow().getScreenWidth();
    }

    private double getScreenScaleY() {
        assert minecraft != null;
        return (double) this.minecraft.getWindow().getGuiScaledHeight() / (double) this.minecraft.getWindow().getScreenHeight();
    }

    private double getCanvasX() {
        assert minecraft != null;
        double x = minecraft.mouseHandler.xpos() * getScreenScaleX();
        return (x - width / 2.0 + 32 * CANVAS_SCALE) / CANVAS_SCALE / 64.0;
    }

    private double getCanvasY() {
        assert minecraft != null;
        double y = minecraft.mouseHandler.ypos() * getScreenScaleY();
        return (y - height / 2.0 + 32 * CANVAS_SCALE) / CANVAS_SCALE / 64.0;
    }

    private float getPixelX() {
        double cx = getCanvasX();
        return (int) ((cx - x0) / (x1 - x0) * 64);
    }

    private float getPixelY() {
        double cy = getCanvasY();
        return (int) ((cy - y0) / (y1 - y0) * 64.0);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        mouseDeltaMoved(lastMouseX - mouseX, lastMouseY - mouseY);

        lastMouseX = mouseX;
        lastMouseY = mouseY;

        super.mouseMoved(mouseX, mouseY);
    }

    protected void mouseDeltaMoved(double deltaX, double deltaY) {
        if (isPanning) {
            float ox = (float) (deltaX / 64 / CANVAS_SCALE);
            x0 -= ox;
            x1 -= ox;

            float oy = (float) (deltaY / 64 / CANVAS_SCALE);
            y0 -= oy;
            y1 -= oy;

            hasPanned = true;
        } else {
            hasPanned = false;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if(com.ancientkira.mca.client.gui.widget.DropdownWidget.escape(this,event.key()))return true;
        int keyCode = event.key();
        // Pan
        if (keyCode == GLFW.GLFW_KEY_SPACE && (textFieldWidget == null || !textFieldWidget.isFocused())) {
            isPanning = true;
            return true;
        }

        if (page == Page.EDITOR && (textFieldWidget == null || !textFieldWidget.isFocused())) {
            // Reset viewport
            if (keyCode == GLFW.GLFW_KEY_R) {
                x0 = 0.0f;
                x1 = 1.0f;
                y0 = 0.0f;
                y1 = 1.0f;
                return true;
            }

            // Fill-delete
            if (keyCode == GLFW.GLFW_KEY_F) {
                workspace.fillDelete((int) getPixelX(), (int) getPixelY());
                return true;
            }

            // Undo
            if (keyCode == GLFW.GLFW_KEY_Y || keyCode == GLFW.GLFW_KEY_Z) {
                workspace.undo();
                return true;
            }

            // Color pick
            if (keyCode == GLFW.GLFW_KEY_P) {
                pickColor();
                return true;
            }
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_SPACE) {
            isPanning = false;
        }

        return super.keyReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (!isPanning && activeMouseButton >= 0 && page == Page.EDITOR) {
            int x = (int) getPixelX();
            int y = (int) getPixelY();
            ClientUtils.bethlehemLine(lastPixelMouseX, lastPixelMouseY, x, y, this::paint);
            lastPixelMouseX = x;
            lastPixelMouseY = y;
        }

        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if(com.ancientkira.mca.client.gui.widget.DropdownWidget.scroll(this,scrollY))return true;
        if (page == Page.EDITOR) {
            float zoom = (float) (scrollY * 0.2f) * (x1 - x0);

            float ox = getPixelX() / 64.0f;
            x0 = x0 - zoom * ox;
            x1 = x1 + zoom * (1 - ox);

            float oy = getPixelY() / 64.0f;
            y0 = y0 - zoom * oy;
            y1 = y1 + zoom * (1 - oy);
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void tick() {
        super.tick();

        timeSinceLastRebuild++;
        if (page == Page.LIBRARY && timeSinceLastRebuild >= 10 && !DropdownWidget.isOpen(this) && (contentRefreshPending || assetRevision != SkinCache.revision() || category.usesCoverage() || subscriptionFilter == SubscriptionFilter.GLOBAL)) {
            assetRevision = SkinCache.revision(); refreshContentList();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if(com.ancientkira.mca.client.gui.widget.DropdownWidget.click(this,event,doubleClick))return true;
        if (timeSinceLastRebuild < 2) {
            return false;
        }

        if (page == Page.EDITOR) {
            if (event.button() == 0 || event.button() == 1) {
                activeMouseButton = event.button();

                int x = (int) getPixelX();
                int y = (int) getPixelY();

                if (workspace.validPixel(x, y)) {
                    workspace.saveSnapshot(true);
                }

                if (!isPanning) {
                    paint(x, y);
                }

                lastPixelMouseX = x;
                lastPixelMouseY = y;
            } else if (event.button() == 2) {
                isPanning = true;
            }
        } else {
            if (hoveredContent != null) {
                if (previousScreen == null) {
                    focusedContent = hoveredContent;
                    setPage(Page.DETAIL);
                } else {
                    var selectedType=SkinType.of(hoveredContent);
                    if(hoveredContent.contentid()<0) {
                        LocalLibrary.select(previousScreen.getVillager().getUUID(),selectedType,"immersive_library:"+hoveredContent.contentid());
                        onClose();return true;
                    }
                    LocalLibrary.select(previousScreen.getVillager().getUUID(),selectedType,null);
                    if (selectedType==SkinType.EYES) {
                        previousScreen.getVillager().setTrackedValue(VillagerLike.EYE_TEXTURE,"immersive_library:"+hoveredContent.contentid());
                        returnToPreviousScreen();
                    } else if (SkinType.of(hoveredContent)==SkinType.CLASSIC) {
                        previousScreen.getVillager().setClassicSkin("immersive_library:"+hoveredContent.contentid());
                        returnToPreviousScreen();
                    } else if(SkinType.of(hoveredContent).accessorySlot()>=0) {
                        previousScreen.getVillager().setAccessory(SkinType.of(hoveredContent).accessorySlot(),"immersive_library:"+hoveredContent.contentid());
                        returnToPreviousScreen();
                    } else if (hoveredContent.hasTag("clothing")) {
                        var villager = previousScreen.getVillager();
                        villager.setClassicSkin("");
                        villager.setClothes("immersive_library:" + hoveredContent.contentid());
                        previousScreen.markClothingSelected();
                        returnToPreviousScreen();
                    } else if (hoveredContent.hasTag("hair")) {
                        previousScreen.applyLibraryHair("immersive_library:" + hoveredContent.contentid());
                        returnToPreviousScreen();
                    }
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    private void returnToPreviousScreen() {
        previousScreen.syncVillagerData();
        if (previousScreen instanceof NeedleScreen || previousScreen instanceof CombScreen) {
            previousScreen.onClose();
        } else {
            onClose();
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        activeMouseButton = -1;

        if (event.button() == 2) {
            isPanning = false;
            if (!hasPanned && page == Page.EDITOR) {
                pickColor();
            }
        }

        return super.mouseReleased(event);
    }

    private void drawTextBox(GuiGraphicsExtractor context, Component text) {
        List<Component> wrap = FlowingText.wrap(text, 220);
        int y = height / 2 - 20 - wrap.size() * 12;
        context.fill(width / 2 - 115, y - 5, width / 2 + 115, y + 12 * wrap.size(), 0x50000000);
        for (Component t : wrap) {
            context.centeredText(font, t, width / 2, y, 0xFFFFFFFF);
            y += 12;
        }
    }

    private void paint(int x, int y) {
        if (page == SkinLibraryScreen.Page.EDITOR && workspace.validPixel(x, y)) {
            if (activeMouseButton == 0) {
                workspace.currentImage.setPixel(x, y, color.getColor());
                workspace.setDirty(true);
            } else if (activeMouseButton == 1) {
                workspace.currentImage.setPixel(x, y, 0);
                workspace.setDirty(true);
            }
        }
    }

    private void pickColor() {
        int x = (int) getPixelX();
        int y = (int) getPixelY();
        if (workspace.validPixel(x, y)) {
            int pixel = workspace.currentImage.getPixel(x, y);
            color.setRGB(
                    ARGB.red(pixel) / 255.0,
                    ARGB.green(pixel) / 255.0,
                    ARGB.blue(pixel) / 255.0
            );
            if (workspace.skinType == SkinType.HAIR) color.setHSV(0, 0, color.brightness);
        }
    }

    private void rebuild() {
        com.ancientkira.mca.client.gui.widget.DropdownWidget.close(this);
        clearWidgets();

        timeSinceLastRebuild = 0;

        // filters
        if (page == Page.LIBRARY || page == Page.EDITOR_LOCKED || page == Page.EDITOR_PREPARE || page == Page.EDITOR_TYPE) {
            List<Page> b = new LinkedList<>();
            b.add(Page.LIBRARY);
            b.add(Page.EDITOR_PREPARE);
            b.add(Page.HELP);

            if (authenticated) {
                b.add(Page.LOGOUT);
            } else {
                b.add(Page.LOGIN);
            }

            int x = page == Page.LIBRARY ? width / 2 - 20 : width / 2 - 110;
            int w = 220 / b.size();
            for (Page page : b) {
                addRenderableWidget(new ButtonWidget(x, height / 2 - 110, w, 20, Component.translatable("gui.skin_library.page." + page.name().toLowerCase(Locale.ROOT)), sender -> setPage(page))).active = page != this.page;
                x += w;
            }
        }

        switch (page) {
            case LIBRARY -> {
                //page
                addRenderableWidget(new ButtonWidget(width / 2 - 30 - 30, height / 2 + 80, 30, 20, Component.literal("<<"), sender -> {
                    setSelectionPage(selectionPage - 1);
                    refreshContentList();
                }));
                pageWidget = addRenderableWidget(new DropdownWidget<Integer>(this, this::addRenderableWidget, this::removeWidget,
                        width / 2 - 30, height / 2 + 80, 60, 20,
                        java.util.stream.IntStream.range(0, availablePages).boxed().toList(), selectionPage,
                        number -> Component.translatable("gui.villager_editor.page", number + 1),
                        number -> { setSelectionPage(number); refreshContentList(); }));
                addRenderableWidget(new ButtonWidget(width / 2 + 30, height / 2 + 80, 30, 20, Component.literal(">>"), sender -> {
                    setSelectionPage(selectionPage + 1);
                    refreshContentList();
                }));
                setSelectionPage(selectionPage);

                int iconX = width / 2 - 170;

                //sorting icons
                addRenderableWidget(new ToggleableTooltipIconButtonWidget(iconX, height / 2 + 82, 6 * 16, 3 * 16,
                        sortingMode == SortingMode.LIKES,
                        Component.translatable("gui.skin_library.sort_likes"),
                        v -> {
                            sortingMode = SortingMode.LIKES;
                            refreshContentList();
                        }));
                addRenderableWidget(new ToggleableTooltipIconButtonWidget(iconX + 22, height / 2 + 82, 7 * 16, 3 * 16,
                        sortingMode == SortingMode.NEWEST,
                        Component.translatable("gui.skin_library.sort_newest"),
                        v -> {
                            sortingMode = SortingMode.NEWEST;
                            refreshContentList();
                        }));
                addRenderableWidget(new ToggleableTooltipIconButtonWidget(iconX + 22 * 2, height / 2 + 82, 14 * 16, 3 * 16,
                        sortingMode == SortingMode.RECOMMENDATIONS,
                        Component.translatable("gui.skin_library.sort_recommendations"),
                        v -> {
                            sortingMode = SortingMode.RECOMMENDATIONS;
                            refreshContentList();
                        }));

                if(subscriptionFilter!=SubscriptionFilter.LIBRARY) addRenderableWidget(typeDropdown(width/2+105,height/2+82,100,type->{category=type;selectionPage=0;refreshContentList();}));
                iconX = width / 2 + 50;
                if (subscriptionFilter == SubscriptionFilter.LIBRARY) {
                    //filter
                    addRenderableWidget(new ToggleableTooltipIconButtonWidget(iconX + 22 * 2, height / 2 + 82, 9 * 16, 3 * 16,
                            filterInvalidSkins,
                            Component.translatable("gui.skin_library.filter_invalid"),
                            v -> {
                                filterInvalidSkins = !filterInvalidSkins;
                                refreshContentList();
                            }));

                    addRenderableWidget(typeDropdown(iconX+22*3,height/2+82,100,type->{
                                category=type;selectionPage=0;filterHair=false;filterClothing=false;invalidateRemotePage();refreshContentList();
                            }));

                    //moderator search
                    if (isModerator()) {
                        addRenderableWidget(new ToggleableTooltipIconButtonWidget(iconX + 22 * 5, height / 2 + 82, 11 * 16, 3 * 16,
                                moderatorMode,
                                Component.translatable("gui.skin_library.filter_moderator"),
                                v -> {
                                    moderatorMode = !moderatorMode;
                                    refreshContentList();
                                }));
                    }
                }

                //search
                if (subscriptionFilter == SubscriptionFilter.LIBRARY || subscriptionFilter==SubscriptionFilter.LOCAL) {
                    EditBox textFieldWidget = addRenderableWidget(new EditBox(this.font, width / 2 - 200 + 65, height / 2 - 110 + 2, 110, 16,
                            Component.translatable("gui.skin_library.search")));
                    this.textFieldWidget = textFieldWidget;
                    textFieldWidget.setMaxLength(64);
                    textFieldWidget.setValue(filteredString);
                    if (filteredString.isEmpty()) {
                        textFieldWidget.setSuggestion("Search");
                    }
                    textFieldWidget.setResponder(s -> {
                        filteredString = s;
                        selectionPage = 0;
                        refreshContentList();
                        textFieldWidget.setSuggestion(null);
                    });
                }

                //group
                addRenderableWidget(new DropdownWidget<SubscriptionFilter>(this, this::addRenderableWidget, this::removeWidget,
                        width / 2 - 200, height / 2 - 110, 60, 20, List.of(SubscriptionFilter.values()), subscriptionFilter,
                        SubscriptionFilter::getText, filter -> {
                            this.subscriptionFilter = filter; selectionPage = 0; refreshContentList();
                        }));

                //controls
                int i = 0;
                for (int y = 0; y < CLOTHES_V; y++) {
                    for (int x = 0; x < CLOTHES_H + y; x++) {
                        if (contents.size() > i) {
                            LiteContent c = contents.get(i);

                            int cx = width / 2 + (int) ((x - CLOTHES_H / 2.0 + 0.5 - 0.5 * (y % 2)) * 55);
                            int cy = height / 2 + 15 + (int) ((y - CLOTHES_V / 2.0 + 0.5) * 80);

                            drawControls(c, false, cx, cy);

                            //quick invalid toggle
                            if (isModerator()) {
                                addRenderableWidget(new ToggleableTooltipIconButtonWidget(cx + 16, cy - 48, 10 * 16, 3 * 16,
                                        false,
                                        Component.literal("Toggle invalid"),
                                        v -> setTag(c.contentid(), "invalid", !c.hasTag("invalid"))));
                            }

                            //sorting icons
                            if (c.tags().contains("invalid")) {
                                addRenderableWidget(new ToggleableTooltipIconButtonWidget(cx + 12, cy - 16, 9 * 16, 3 * 16,
                                        true,
                                        Component.translatable("gui.skin_library.probably_not_valids"),
                                        v -> {
                                        }));
                            }
                            i++;
                        } else {
                            break;
                        }
                    }
                }
            }
            case EDITOR_PREPARE -> {
                //URL
                EditBox textFieldWidget = addRenderableWidget(new EditBox(this.font, width / 2 - 90, height / 2 - 18, 180, 16,
                        Component.literal("URL")));
                textFieldWidget.setMaxLength(1024);

                addRenderableWidget(new ButtonWidget(width / 2 - 50, height / 2 + 5, 100, 20, Component.translatable("gui.skin_library.load_image"), sender -> loadImage(textFieldWidget.getValue())));
            }
            case EDITOR_TYPE -> {
                addRenderableWidget(typeDropdown(width/2-100,height/2-20,200,type->category=type));
                addRenderableWidget(new ButtonWidget(width/2-50,height/2+5,100,20,Component.translatable("gui.proceed"),b->{
                    workspace.skinType=category;
                    if(category==SkinType.HAIR)color.setHSV(0,0,.5F);
                    setPage(Page.EDITOR);
                }));

                // help
                addRenderableWidget(new TooltipButtonWidget(width / 2 - 10, height / 2 + 30, 20, 20,
                        Component.literal("?"),
                        Component.translatable("gui.skin_library.help"),
                        v -> {
                            openHelp();
                        }));
            }
            case LOGIN -> {
                addRenderableWidget(new ButtonWidget(width / 2 - 50, height / 2 + 25, 100, 20,
                        Component.translatable("gui.skin_library.cancel"),
                        v -> cancelAuthentication()));
            }
            case DETAIL -> {
                if (canModifyFocusedContent()) {
                    //tag name
                    EditBox tagNameWidget = addRenderableWidget(new EditBox(this.font, width / 2 - 200, height / 2 - 100 + 2, 95, 16,
                            Component.literal("")));
                    tagNameWidget.setMaxLength(20);
                    tagNameWidget.setSuggestion("New Tag Name");
                    tagNameWidget.setResponder(v -> {
                        tagNameWidget.setSuggestion(null);
                    });

                    //add tag
                    addRenderableWidget(new TooltipButtonWidget(width / 2 - 100, height / 2 - 100, 40, 20, "gui.skin_library.add", sender -> {
                        String tag = tagNameWidget.getValue().trim().toLowerCase(Locale.ROOT);
                        if (!tag.isEmpty()) {
                            setTag(focusedContent.contentid(), tag, true);
                            tagNameWidget.setValue("");
                            rebuild();
                        }
                    }));
                }

                //controls
                drawControls(focusedContent, true, width / 2 + 130, height / 2 + 60);

                //close
                addRenderableWidget(new ButtonWidget(width / 2 - 40, height / 2 + 60, 80, 20,
                        Component.translatable("gui.skin_library.close"),
                        v -> {
                            setPage(Page.LIBRARY);
                        }));


                //tags
                int ty = height / 2 - 70;
                for (String tag : focusedContent.tags()) {
                    if (!tag.equals("clothing") && !tag.equals("hair") && !tag.equals("invalid") || isModerator()) {
                        int w = font.width(tag) + 10;
                        if (canModifyFocusedContent()) {
                            addRenderableWidget(new ButtonWidget(width / 2 - 200, ty, 20, 20,
                                    Component.literal("X"),
                                    v -> {
                                        setTag(focusedContent.contentid(), tag, false);
                                        rebuild();
                                    }));
                        }
                        addRenderableWidget(new ButtonWidget(width / 2 - 200 + 20, ty, w, 20,
                                Component.literal(tag),
                                v -> {
                                }));
                        ty += 20;
                    }
                }
            }
            case DELETE -> {
                addRenderableWidget(new ButtonWidget(width / 2 - 65, height / 2, 60, 20,
                        Component.translatable("gui.skin_library.cancel"),
                        v -> {
                            setPage(Page.DETAIL);
                        }));
                addRenderableWidget(new ButtonWidget(width / 2 + 5, height / 2, 60, 20,
                        Component.translatable("gui.skin_library.delete"),
                        v -> {
                            removeContent(deleteConfirmationContent.contentid());
                            setPage(Page.LIBRARY);
                        }));
            }
            case REPORT -> {
                addRenderableWidget(new TooltipButtonWidget(width / 2 - 105, height / 2, 100, 20,
                        Component.translatable("gui.skin_library.report_invalid"),
                        Component.translatable("gui.skin_library.report_invalid_tooltip"),
                        v -> {
                            reportContent(reportConfirmationContent.contentid(), "INVALID");
                            setPage(Page.DETAIL);
                        }));
                addRenderableWidget(new TooltipButtonWidget(width / 2 + 5, height / 2, 100, 20,
                        Component.translatable("gui.skin_library.report_default"),
                        Component.translatable("gui.skin_library.report_default_tooltip"),
                        v -> {
                            reportContent(reportConfirmationContent.contentid(), "DEFAULT");
                            setPage(Page.DETAIL);
                        }));

                addRenderableWidget(new ButtonWidget(width / 2 - 50, height / 2 + 22, 100, 20,
                        Component.translatable("gui.skin_library.cancel"),
                        v -> {
                            setPage(Page.DETAIL);
                        }));
            }
            case EDITOR -> {
                // name
                textFieldWidget = addRenderableWidget(new EditBox(this.font, width / 2 - 60, height / 2 - 105, 120, 20,
                        Component.translatable("gui.skin_library.name")));
                textFieldWidget.setMaxLength(1024);
                textFieldWidget.setValue(workspace.title);
                if (workspace.title.isEmpty()) {
                    textFieldWidget.setSuggestion(workspace.title);
                }
                textFieldWidget.setFocused(false);
                textFieldWidget.setResponder(v -> {
                    workspace.title = v;
                    textFieldWidget.setSuggestion(null);
                });

                // help
                addRenderableWidget(new TooltipButtonWidget(width / 2 + 65, height / 2 - 105, 20, 20,
                        Component.literal("?"),
                        Component.translatable("gui.skin_library.tool_help"),
                        v -> {
                            openHelp();
                        }));

                //gender
                addRenderableWidget(new DropdownWidget<Gender>(this, this::addRenderableWidget, this::removeWidget,
                        width / 2 - 200, height / 2 - 80, 105, 20, List.of(Gender.MALE, Gender.NEUTRAL, Gender.FEMALE),
                        workspace.gender, Gender::getText, gender -> this.workspace.gender = gender));

                if(workspace.skinType==SkinType.HAIR) {
                    addRenderableWidget(new TooltipButtonWidget(width/2-200,height/2-60,105,20,
                            Component.translatable("gui.skin_library.untinted").append(workspace.untinted?": ON":": OFF"),
                            Component.translatable("gui.skin_library.untinted.tooltip"),b->{workspace.untinted=!workspace.untinted;rebuild();}));
                }
                // temperature
                if (workspace.skinType == SkinType.CLOTHING) {
                    addRenderableWidget(new IntegerSliderWidget(width / 2 - 200, height / 2 - 60, 105, 20,
                            workspace.temperature,
                            -2, 2,
                            v -> {
                                workspace.temperature = v;
                            },
                            v -> Component.translatable("gui.skin_library.temperature." + (v + 2)),
                            () -> Component.translatable("gui.skin_library.temperature.tooltip")
                    ));
                }

                //profession
                if (workspace.skinType == SkinType.CLOTHING) {
                    int ox = 0;
                    int oy = 0;
                    List<ItemButtonWidget> widgets = new LinkedList<>();
                    for (VillagerProfession profession : BuiltInRegistries.VILLAGER_PROFESSION) {
                        String professionPath = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession).getPath();
                        MutableComponent text = Component.translatable("entity.minecraft.villager." + professionPath);
                        ItemButtonWidget widget = addRenderableWidget(new ItemButtonWidget(width / 2 - 200 + ox * 21, height / 2 - 30 + oy * 21, 20, text,
                                ProfessionIcons.ICONS.getOrDefault(professionPath, Items.OAK_SAPLING.getDefaultInstance()),
                                v -> {
                                    workspace.profession = "none".equals(professionPath) ? null : professionPath;
                                    widgets.forEach(b -> b.active = true);
                                    v.active = false;
                                }));
                        widget.active = !Objects.equals(workspace.profession, "none".equals(professionPath) ? null : professionPath);
                        widgets.add(widget);
                        ox++;
                        if (ox >= 5) {
                            ox = 0;
                            oy++;
                        }
                    }
                }


                int y = height / 2 - 5;

                if (workspace.skinType == SkinType.CLOTHING) {
                    //hue
                    color.hueWidget = addRenderableWidget(new HorizontalColorPickerWidget(width / 2 + 100, y, 100, 15,
                            color.hue / 360.0,
                            MCA.locate("textures/colormap/hue.png"),
                            (vx, vy) -> {
                                color.setHSV(
                                        vx * 360,
                                        color.saturation,
                                        color.brightness
                                );
                            }));

                    //saturation
                    color.saturationWidget = addRenderableWidget(new HorizontalGradientWidget(width / 2 + 100, y + 20, 100, 15,
                            color.saturation,
                            () -> {
                                double[] doubles = ClientUtils.HSV2RGB(color.hue, 0.0, 1.0);
                                return new float[]{
                                        (float) doubles[0], (float) doubles[1], (float) doubles[2], 1.0f,
                                };
                            },
                            () -> {
                                double[] doubles = ClientUtils.HSV2RGB(color.hue, 1.0, 1.0);
                                return new float[]{
                                        (float) doubles[0], (float) doubles[1], (float) doubles[2], 1.0f,
                                };
                            },
                            (vx, vy) -> {
                                color.setHSV(
                                        color.hue,
                                        vx,
                                        color.brightness
                                );
                            }));
                }

                //brightness
                color.brightnessWidget = addRenderableWidget(new HorizontalGradientWidget(width / 2 + 100, y + 40, 100, 15,
                        color.brightness,
                        () -> {
                            double[] doubles = ClientUtils.HSV2RGB(color.hue, color.saturation, 0.0);
                            return new float[]{
                                    (float) doubles[0], (float) doubles[1], (float) doubles[2], 1.0f,
                            };
                        },
                        () -> {
                            double[] doubles = ClientUtils.HSV2RGB(color.hue, color.saturation, 1.0);
                            return new float[]{
                                    (float) doubles[0], (float) doubles[1], (float) doubles[2], 1.0f,
                            };
                        },
                        (vx, vy) -> {
                            color.setHSV(
                                    color.hue,
                                    color.saturation,
                                    vx
                            );
                        }));

                //fill tool strength
                addRenderableWidget(new IntegerSliderWidget(width / 2 + 100, y + 60, 100, 20,
                        workspace.fillToolThreshold,
                        0, 128,
                        v -> {
                            workspace.fillToolThreshold = v;
                        },
                        v -> Component.translatable("gui.skin_library.fillToolThreshold"),
                        () -> Component.translatable("gui.skin_library.fillToolThreshold.tooltip")
                ));

                //undo
                addRenderableWidget(new ButtonWidget(width / 2 - 160, height / 2 + 80, 75, 20,
                        Component.translatable("gui.skin_library.undo"),
                        v -> {
                            workspace.undo();
                        }));

                if (workspace.skinType == SkinType.HAIR) {
                    // make black and white
                    addRenderableWidget(new ButtonWidget(width / 2 + 100, y - 20, 100, 20,
                            Component.translatable("gui.skin_library.remove_saturation"),
                            v -> {
                                workspace.removeSaturation();
                            }));

                    //less contrast
                    addRenderableWidget(new TooltipButtonWidget(width / 2 + 100, y, 50, 20,
                            Component.literal("C -"),
                            Component.translatable("gui.skin_library.less_contrast"),
                            v -> {
                                workspace.addContrast(-0.15f);
                            }));

                    //more contrast
                    addRenderableWidget(new TooltipButtonWidget(width / 2 + 150, y, 50, 20,
                            Component.literal("C +"),
                            Component.translatable("gui.skin_library.more_contrast"),
                            v -> {
                                workspace.addContrast(0.15f);
                            }));

                    //less contrast
                    addRenderableWidget(new TooltipButtonWidget(width / 2 + 100, y + 20, 50, 20,
                            Component.literal("B -"),
                            Component.translatable("gui.skin_library.less_brightness"),
                            v -> {
                                workspace.addBrightness(-8);
                            }));

                    //more contrast
                    addRenderableWidget(new TooltipButtonWidget(width / 2 + 150, y + 20, 50, 20,
                            Component.literal("B +"),
                            Component.translatable("gui.skin_library.more_brightness"),
                            v -> {
                                workspace.addBrightness(8);
                            }));

                    //hair color
                    Genetics genetics = villagerVisualization.getGenetics();
                    addRenderableWidget(new ColorPickerWidget(width / 2 - 200, height / 2 - 30, 100, 100,
                            genetics.getGene(Genetics.PHEOMELANIN),
                            genetics.getGene(Genetics.EUMELANIN),
                            MCA.locate("textures/colormap/villager_hair.png"),
                            (vx, vy) -> {
                                genetics.setGene(Genetics.PHEOMELANIN, vx.floatValue());
                                genetics.setGene(Genetics.EUMELANIN, vy.floatValue());
                            }));
                }

                // cancel
                addRenderableWidget(new ButtonWidget(width / 2 - 80, height / 2 + 80, 75, 20,
                        Component.translatable("gui.skin_library.cancel"),
                        v -> {
                            setPage(Page.LIBRARY);
                        }));

                addRenderableWidget(new ButtonWidget(width/2+85,height/2+80,100,20,Component.translatable("gui.skin_library.save_local"),b->{
                    try {LocalLibrary.save(workspace,getPlayerName());category=workspace.skinType;subscriptionFilter=SubscriptionFilter.LOCAL;setPage(Page.LIBRARY);refreshContentList();}
                    catch(IOException error){setError(Component.literal(error.getMessage()));}
                }));
                // publish
                addRenderableWidget(new ButtonWidget(width / 2 + 5, height / 2 + 80, 75, 20,
                        Component.translatable("gui.skin_library.publish_public"),
                        v -> {
                            publish();
                        }));
            }
        }
    }

    private void drawControls(LiteContent content, boolean advanced, int cx, int cy) {
        int w = advanced ? 20 : 16;
        List<TooltipButtonWidget> widgets = new LinkedList<>();

        // subscribe
        if (content.contentid()>=0 && (content.hasTag("clothing") || content.hasTag("hair")) && (isOp() || Config.getServerConfig().allowEveryoneToAddContentGlobally)) {
            widgets.add(new ToggleableTooltipIconButtonWidget(0, 0, 0, 3 * 16,
                    getServerContentById(content.contentid()).isPresent(),
                    Component.translatable("gui.skin_library.subscribe"),
                    v -> {
                        if (((ToggleableTooltipButtonWidget) v).toggle) {
                            Network.sendToServer(new RemoveCustomClothingMessage(content.hasTag("clothing") ? RemoveCustomClothingMessage.Type.CLOTHING : RemoveCustomClothingMessage.Type.HAIR, Identifier.fromNamespaceAndPath("immersive_library", String.valueOf(content.contentid()))));
                        } else {
                            toListEntry(content).ifPresent(e -> {
                                Network.sendToServer(AddCustomClothingMessage.fromEntry(e));
                            });
                        }
                        ((ToggleableTooltipButtonWidget) v).toggle = !((ToggleableTooltipButtonWidget) v).toggle;
                    }));
        }

        // like
        if (authenticated && content.contentid()>=0) {
            widgets.add(new ToggleableTooltipIconButtonWidget(0, 0, 16, 3 * 16,
                    isLiked(content),
                    Component.translatable("gui.skin_library.like"),
                    v -> {
                        ToggleableTooltipButtonWidget button = (ToggleableTooltipButtonWidget) v;
                        boolean liked = !button.toggle;
                        if (setLike(content, liked)) {
                            button.toggle = liked;
                            refreshContentList();
                        }
                    }));
        }

        // edit
        if (advanced && content.contentid()>=0 && canModifyContent(content)) {
            widgets.add(new ToggleableTooltipIconButtonWidget(0, 0, 2 * 16, 3 * 16,
                    false,
                    Component.translatable("gui.skin_library.edit"),
                    v -> {

                        SkinCache.getImage(content).ifPresent(image -> {
                            SkinCache.getMeta(content).ifPresent(meta -> {
                                workspace = new Workspace(image, meta, content);
                                setPage(Page.EDITOR);
                                if (workspace.skinType == SkinType.HAIR) color.setHSV(0, 0, 0.5);
                            });
                        });
                    }));
        }

        // report
        if (advanced && authenticated) {
            widgets.add(new ToggleableTooltipIconButtonWidget(cx - 12 + 25, cy, 10 * 16, 3 * 16,
                    true,
                    Component.translatable("gui.skin_library.report"),
                    v -> {
                        reportConfirmationContent = content;
                        setPage(Page.REPORT);
                    }));
        }

        // delete
        if (advanced && content.contentid()>=0 && canModifyContent(content)) {
            widgets.add(new ToggleableTooltipIconButtonWidget(cx - 12 + 25, cy, 3 * 16, 3 * 16,
                    true,
                    Component.translatable("gui.skin_library.delete"),
                    v -> {
                        deleteConfirmationContent = content;
                        setPage(Page.DELETE);
                    }));
        }

        // advanced
        if (!advanced) {
            widgets.add(new ToggleableTooltipIconButtonWidget(cx - 12 + 25, cy, 4 * 16, 4 * 16,
                    true,
                    Component.translatable("gui.skin_library.details"),
                    v -> {
                        if (isPanning && isModerator()) {
                            //admin tool
                            reportContent(content.contentid(), "DEFAULT");
                            refreshContentList();
                        } else {
                            focusedContent = content;
                            setPage(Page.DETAIL);
                        }
                    }));
        }

        // ban
        if (advanced && isModerator()) {
            widgets.add(new ToggleableTooltipIconButtonWidget(cx - 12 + 25, cy, 5 * 16, 3 * 16,
                    false,
                    Component.translatable("gui.skin_library.ban"),
                    v -> {
                        setBan(content.userid(), true);
                        refreshContentList();
                    }));

            widgets.add(new ToggleableTooltipIconButtonWidget(cx - 12 + 25, cy, 11 * 16, 3 * 16,
                    false,
                    Component.translatable("gui.skin_library.unban"),
                    v -> {
                        setBan(content.userid(), false);
                        refreshContentList();
                    }));
        }

        // add the widgets
        int wx = cx - widgets.size() * w / 2;
        for (TooltipButtonWidget buttonWidget : widgets) {
            addRenderableWidget(buttonWidget);
            buttonWidget.setX(wx);
            buttonWidget.setY(cy);
            wx += w;
        }
    }

    private Optional<SkinListEntry> toListEntry(LiteContent content) {
        return SkinCache.getMeta(content).map(meta -> {
            if (content.hasTag("clothing")) {
                return new Clothing("immersive_library:" + content.contentid(), meta.getProfession(), meta.getTemperature(), false, meta.getGender());
            } else {
                return new Hair("immersive_library:" + content.contentid());
            }
        });
    }

    private boolean canModifyFocusedContent() {
        return canModifyContent(focusedContent);
    }

    private boolean canModifyContent(LiteContent content) {
        return currentUser != null && (currentUser.moderator() || currentUser.userid() == content.userid());
    }

    private boolean isModerator() {
        return currentUser != null && currentUser.moderator();
    }

    private boolean isLiked(LiteContent content) {
        return currentUser != null && (currentUser.likes().stream().anyMatch(c -> c.contentid() == content.contentid()));
    }

    private com.ancientkira.mca.client.gui.widget.DropdownWidget<SkinType> typeDropdown(int x,int y,int width,java.util.function.Consumer<SkinType> change) {
        return new com.ancientkira.mca.client.gui.widget.DropdownWidget<>(this,widget->addRenderableWidget(widget),this::removeWidget,
                x,y,width,20,java.util.List.of(SkinType.values()),category,SkinType::text,change);
    }

    public void setPage(Page page) {
        if (Thread.currentThread() != thread) {
            assert minecraft != null;
            minecraft.executeIfPossible(() -> setPage(page));
            return;
        }

        clearError();

        if (page == Page.HELP) {
            openHelp();
            return;
        }

        if (page == Page.LOGIN) {
            if (!Auth.hasToken()) {
                isBrowserOpen = true;
                Auth.authenticate(getPlayerName());
            } else {
                isBrowserOpen = false;
            }
        }

        if (page == Page.LOGOUT) {
            authenticated = false;
            currentUser = null;
            invalidateRemotePage();
            Auth.clearToken();
            refreshPage();
            return;
        }

        this.page = page;

        if (page == Page.EDITOR) {
            x0 = 0.0f;
            x1 = 1.0f;
            y0 = 0.0f;
            y1 = 1.0f;
            uploading = false;
        }

        if (page == Page.LIBRARY) {
            refreshContentList();
        } else {
            rebuild();
        }
    }

    private void pollAuthentication() {
        if (awaitingAuthentication) {
            return;
        }
        awaitingAuthentication = true;
        CompletableFuture.runAsync(() -> {
            try {
                String token = Auth.getToken();
                Response response = token == null ? null : request(Api.HttpMethod.GET, IsAuthResponse.class, "auth");
                if (page != Page.LOGIN || !Objects.equals(token, Auth.getToken())) {
                    return;
                }
                if (response instanceof IsAuthResponse(boolean success)) {
                    if (success) {
                        Response userResponse = request(Api.HttpMethod.GET, UserResponse.class, "user/mca/me");
                        Minecraft.getInstance().executeIfPossible(() -> {
                            if (page != Page.LOGIN || !Objects.equals(token, Auth.getToken())) {
                                return;
                            }
                            if (userResponse instanceof UserResponse(User user)) {
                                authenticated = true;
                                currentUser = user;
                                isBrowserOpen = false;
                                clearError();
                                invalidateRemotePage();
                                Auth.saveToken();
                                setPage(Page.LIBRARY);
                            } else {
                                isBrowserOpen = false;
                                setError(Component.translatable("gui.skin_library.list_fetch_failed"));
                            }
                        });
                    } else if (!isBrowserOpen) {
                        Auth.clearToken();
                        setPage(Page.LIBRARY);
                        setError(Component.translatable("gui.skin_library.is_auth_failed"));
                    }
                } else {
                    setError(Component.translatable("gui.skin_library.is_auth_failed"));
                }
                Thread.sleep(2000);
            } catch (Exception e) {
                MCA.LOGGER.error(e);
            } finally {
                awaitingAuthentication = false;
            }
        });
    }

    private void cancelAuthentication() {
        Auth.clearToken();
        authenticated = false;
        currentUser = null;
        isBrowserOpen = false;
        invalidateRemotePage();
        page = Page.LIBRARY;
        Minecraft.getInstance().setScreen(this);
    }

    private void openHelp() {
        try {
            Util.getPlatform().openUri(URI.create("https://github.com/Luke100000/minecraft-comes-alive/wiki/Skin-Editor"));
        } catch (Exception e) {
            MCA.LOGGER.error(e);
        }
    }

    private void invalidateRemotePage() {
        loadedQuery = null;
        pendingQuery = null;
        remoteContents.clear();
    }

    private void refreshContentList() {
        if (Thread.currentThread() != thread) {
            assert minecraft != null;
            minecraft.executeIfPossible(this::refreshContentList);
            return;
        }

        // Download callbacks must not rebuild widgets while the user is choosing an option.
        if (DropdownWidget.isOpen(this)) {
            contentRefreshPending = true;
            return;
        }
        contentRefreshPending = false;

        boolean restoreSearchFocus = textFieldWidget != null && getFocused() == textFieldWidget;
        int searchCursor = restoreSearchFocus ? textFieldWidget.getCursorPosition() : 0;
        refreshServerContent();

        // fetch the list matching the current subscription filter
        List<LiteContent> newList;
        switch (subscriptionFilter) {
            case LIBRARY -> {
                loadPage();
                newList = remoteContents;
            }
            case LOCAL -> { newList=LocalLibrary.contents(); }
            case GLOBAL -> {
                newList = serverContent;
            }
            case LIKED -> {
                newList = (currentUser != null ? currentUser.likes() : Collections.emptyList());
            }
            case SUBMISSIONS -> {
                newList = currentUser != null ? currentUser.submissions() : Collections.emptyList();
            }
            default -> throw new IllegalStateException("Unexpected value: " + subscriptionFilter);
        }

        if (subscriptionFilter == SubscriptionFilter.LIBRARY && category.usesCoverage())
            newList = categoryCandidates;
        List<LiteContent> matching = new ArrayList<>();
        int queued = 0;
        int needed = (selectionPage + 2) * CLOTHES_PER_PAGE;
        for (LiteContent candidate : newList) {
            LiteContent content = resolvedContent(candidate);
            if (subscriptionFilter == SubscriptionFilter.GLOBAL && content.userid() < 0
                    && SkinCache.description(content.contentid()).isEmpty() && !SkinCache.failed(content.contentid()) && queued++ < 8)
                SkinCache.sync(content);
            if (!filteredString.isBlank() && !(content.title() + " " + content.username()).toLowerCase(Locale.ROOT)
                    .contains(filteredString.toLowerCase(Locale.ROOT))) continue;
            if (category.usesCoverage() && !content.hasTag("mcarem") && !SkinCache.classified(content.contentid())) {
                if (!SkinCache.failed(content.contentid()) && queued++ < 8) SkinCache.classify(content);
                continue;
            }
            boolean matches = category.matches(content);
            if (matches) matching.add(content);
            if (category.usesCoverage() && matching.size() >= needed) break;
        }
        boolean publicRegular = subscriptionFilter == SubscriptionFilter.LIBRARY && !category.usesCoverage();
        availablePages = Math.max(selectionPage + 1, Math.max(1, (matching.size() + CLOTHES_PER_PAGE - 1) / CLOTHES_PER_PAGE));
        if (subscriptionFilter == SubscriptionFilter.LIBRARY && (!categoryScanExhausted || publicRegular && remoteContents.size() == CLOTHES_PER_PAGE))
            availablePages = Math.max(availablePages, selectionPage + 2);
        Optional<LiteContent> pinned = subscriptionFilter == SubscriptionFilter.LIBRARY || subscriptionFilter == SubscriptionFilter.GLOBAL
                ? onlineWornContent().filter(category::matches) : Optional.empty();
        List<LiteContent> visible = LibraryPages.page(matching, selectionPage, CLOTHES_PER_PAGE, publicRegular,
                pinned, LiteContent::contentid);
        contents.clear(); contents.addAll(visible);
        contents.forEach(SkinCache::sync);
        libraryPreviews.clear();
        rebuild();
        if (restoreSearchFocus && textFieldWidget != null) {
            setFocused(textFieldWidget); textFieldWidget.setFocused(true); textFieldWidget.setCursorPosition(searchCursor);
        }
        setSelectionPage(selectionPage);
    }

    private String getPlayerName() {
        return Minecraft.getInstance().player == null ? "Unknown" : Minecraft.getInstance().player.getGameProfile().name();
    }

    private boolean isOp() {
        return Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    private String encodeCurrentImage() throws IOException {
        Path tempPath = Files.createTempFile("mca-skin-library-upload-", ".png");
        try {
            workspace.currentImage.writeToFile(tempPath);
            return Base64.getEncoder().encodeToString(Files.readAllBytes(tempPath));
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    private void setSelectionPage(int p) {
        selectionPage = Math.max(0, p);
        if (pageWidget != null) {
            pageWidget.setMessage(Component.translatable("gui.villager_editor.page", selectionPage + 1).append(" ▾"));
        }
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        Path path = paths.getFirst();
        loadImage(path.toString());
    }

    private void loadImage(String path) {
        try (InputStream stream = openImageStream(path)) {
            if (stream == null) {
                return;
            }

            NativeImage image = NativeImage.read(stream);

            if (image.getWidth() == 64 && image.getHeight() == 64) {
                if (SkinPorter.isSlimFormat(image)) {
                    SkinPorter.convertSlimToDefault(image);
                }
                workspace = new Workspace(image);
                setPage(Page.EDITOR_TYPE);
            } else if (image.getWidth() == 64 && image.getHeight() == 32) {
                // port legacy skins
                workspace = new Workspace(SkinPorter.portLegacySkin(image));
                setPage(Page.EDITOR_TYPE);
            } else {
                setError(Component.translatable("gui.skin_library.not_64"));
            }
        } catch (IOException e) {
            MCA.LOGGER.error(e);
        }
    }

    private InputStream openImageStream(String path) {
        try {
            return URI.create(path).toURL().openStream();
        } catch (Exception exception) {
            try {
                return new FileInputStream(path);
            } catch (Exception e) {
                MCA.LOGGER.error(e);
                return null;
            }
        }
    }

    private void publish() {
        if(!Auth.hasToken()){setError(Component.translatable("gui.skin_library.login_to_publish"));return;}
        if (workspace.title.equals("Unnamed Asset")) {
            setError(Component.translatable("gui.skin_library.choose_name"));
            return;
        }

        int region = workspace.skinType.coverageRegion();
        if (region != 0 && (AccessoryTextureCoverage.classify(workspace.currentImage.getWidth(), workspace.currentImage.getHeight(),
                (x, y) -> (workspace.currentImage.getPixel(x, y) >>> 24) & 255) & region) == 0) {
            setError(Component.translatable("gui.skin_library.accessory_invalid"));
            return;
        }
        if(workspace.skinType==SkinType.EYES && !EyeTextureCoverage.qualifies(workspace.currentImage.getWidth(),workspace.currentImage.getHeight(),
                (x,y)->(workspace.currentImage.getPixel(x,y)>>>24)&255)) {
            setError(Component.translatable("gui.skin_library.eyes_invalid"));return;
        }
        if(workspace.skinType==SkinType.CLASSIC && !com.ancientkira.mca.client.gui.immersive_library.ClassicSkinCoverage.qualifies(
                workspace.currentImage.getWidth(),workspace.currentImage.getHeight(),(x,y)->(workspace.currentImage.getPixel(x,y)>>>24)&255)) {
            setError(Component.translatable("gui.skin_library.classic_incomplete"));return;
        }
        if (workspace.skinType!=SkinType.CLASSIC && !Utils.verify(workspace.currentImage)) {
            setError(Component.translatable("gui.skin_library.read_the_help"));
            return;
        }

        if (workspace.skinType == SkinType.HAIR && !workspace.untinted && !Utils.verifyHair(workspace.currentImage) && !skipHairWarning) {
            setError(Component.translatable("gui.skin_library.read_the_help_hair"));
            skipHairWarning = true;
            return;
        }
        skipHairWarning = false;

        if (!uploading) {
            uploading = true;
            CompletableFuture.runAsync(() -> {
                if (Auth.hasToken()) {
                    Response request = null;
                    try {
                        request = request(workspace.contentid == -1 ? Api.HttpMethod.POST : Api.HttpMethod.PUT, workspace.contentid == -1 ? ContentIdResponse.class : SuccessResponse.class, workspace.contentid == -1 ? "content/mca" : ("content/mca/" + workspace.contentid), Map.of(

                        ), Map.of(
                                "title", workspace.title,
                                "meta", workspace.toListEntry().toJson().toString(),
                                "data", encodeCurrentImage()
                        ));
                    } catch (IOException e) {
                        MCA.LOGGER.error(e);
                    }

                    if (request instanceof ContentIdResponse || request instanceof SuccessResponse) {
                        int contentid = request instanceof ContentIdResponse(
                                int contentid1
                        ) ? contentid1 : workspace.contentid;

                        // default tags
                        for(var kind:SkinType.values())if(kind!=workspace.skinType && workspace.contentid>=0 && focusedContent!=null && focusedContent.hasTag(kind.tag()))setTag(contentid,kind.tag(),false);
                        setTag(contentid, "mcarem", true);
                        setTag(contentid, workspace.skinType.tag(), true);
                        if(workspace.skinType==SkinType.HAIR)setTag(contentid,"untinted",workspace.untinted);
                        if (workspace.profession != null) {
                            setTag(contentid, workspace.profession.replace("mca.", ""), true);
                        }

                        Response contentResponse = request(Api.HttpMethod.GET, ContentResponse.class, "content/mca/" + contentid);
                        if (contentResponse instanceof ContentResponse(Content content)) {
                            LiteContent publishedContent = new LiteContent(
                                    content.contentid(), content.userid(), content.username(), content.likes(),
                                    content.tags(), content.title(), content.version()
                            );
                            if (currentUser != null) {
                                currentUser.submissions().removeIf(v -> v.contentid() == contentid);
                                currentUser.submissions().add(publishedContent);
                            }
                            focusedContent = publishedContent;
                            setPage(Page.DETAIL);
                        } else {
                            setPage(Page.LIBRARY);
                        }
                        uploading = false;

                        //also refresh our cache
                        SkinCache.enforceSync(contentid);
                    } else if (request instanceof ErrorResponse response) {
                        if (response.code() == 428) {
                            setError(Component.translatable("gui.skin_library.upload_duplicate"));
                        } else {
                            setError(Component.translatable("gui.skin_library.upload_failed"));
                        }
                        uploading = false;
                    }
                }
            });
        } else {
            setError(Component.translatable("gui.skin_library.already_uploading"));
        }
    }

    private Optional<LiteContent> getContentById(int contentid) {
        return Stream.concat(remoteContents.stream(), serverContent.stream()).filter(v -> v.contentid() == contentid).findAny();
    }

    private Optional<LiteContent> getServerContentById(int contentid) {
        return serverContent.stream().filter(v -> v.contentid() == contentid).findAny();
    }

    private Optional<LiteContent> getSubmittedContent(int contentid) {
        return currentUser == null ? Optional.empty() : currentUser.submissions().stream().filter(v -> v.contentid() == contentid).findAny();
    }

    private void setTag(int contentid, String tag, boolean add) {
        if (Auth.hasToken()) {
            request(add ? Api.HttpMethod.POST : Api.HttpMethod.DELETE, SuccessResponse.class, "tag/mca/" + contentid + "/" + tag);
            getContentById(contentid).ifPresent(c -> {
                if (add) {
                    c.tags().add(tag);
                } else {
                    c.tags().remove(tag);
                }
            });
            getSubmittedContent(contentid).ifPresent(c -> {
                if (add) {
                    c.tags().add(tag);
                } else {
                    c.tags().remove(tag);
                }
            });
        }
    }

    private void removeContent(int contentId) {
        if (Auth.hasToken()) {
            request(Api.HttpMethod.DELETE, SuccessResponse.class, "content/mca/" + contentId);
            removeContentLocally(contentId);
        }
    }

    private void removeContentLocally(int contentId) {
        remoteContents.removeIf(v -> v.contentid() == contentId);

        if (currentUser != null) {
            currentUser.likes().removeIf(v -> v.contentid() == contentId);
            currentUser.submissions().removeIf(v -> v.contentid() == contentId);
        }
    }

    private void reportContent(int contentId, String reason) {
        if (Auth.hasToken()) {
            request(Api.HttpMethod.POST, SuccessResponse.class, "report/mca/" + contentId + "/" + reason);

            if (reason.equals("DEFAULT")) {
                removeContentLocally(contentId);
            }

            setError(Component.translatable("gui.skin_library.reported"));
        }
    }

    private boolean setLike(LiteContent content, boolean add) {
        if (!Auth.hasToken() || currentUser == null) {
            return false;
        }

        Response response = request(add ? Api.HttpMethod.POST : Api.HttpMethod.DELETE, SuccessResponse.class, "like/mca/" + content.contentid());
        if (!(response instanceof SuccessResponse)) {
            return false;
        }

        if (add) {
            currentUser.likes().removeIf(v -> v.contentid() == content.contentid());
            currentUser.likes().add(content);
        } else {
            currentUser.likes().removeIf(v -> v.contentid() == content.contentid());
        }
        return true;
    }

    private void setBan(int userid, boolean banned) {
        if (Auth.hasToken() && currentUser != null) {
            request(Api.HttpMethod.PUT, SuccessResponse.class, "user/" + userid, Map.of(
                    "banned", Boolean.toString(banned)
            ));
        }
    }

    public void refreshPage() {
        setPage(page);
    }

    public void clearError() {
        this.error = null;
    }

    public void setError(Component text) {
        this.error = text;
    }

    private <T> void addServerContent(Map<String, T> map, String type) {
        for (Map.Entry<String, T> entry : map.entrySet()) {
            if (entry.getKey().startsWith("immersive_library:")) {
                try {
                    int contentid = Integer.parseInt(entry.getKey().substring(18));
                    serverContent.add(SkinCache.description(contentid).orElseGet(() -> new LiteContent(
                            contentid, -1, "", -1, Set.of(type), "Asset #" + contentid, -1
                    )));
                } catch (NumberFormatException ignored) {
                    //nop
                }
            }
        }
    }

    @Override
    public void skinListUpdatedCallback() {
        refreshServerContent();

        if (page == Page.LIBRARY) {
            refreshContentList();
        }
    }

    private void refreshServerContent() {
        serverContent.clear();
        addServerContent(ClientSkinCatalog.clothing(), "clothing");
        addServerContent(ClientSkinCatalog.hair(), "hair");
        for (var entry : wornTextures.entrySet()) {
            var id = com.ancientkira.mca.util.ImmersiveLibraryIds.contentId(entry.getValue());
            if (id.isPresent() && serverContent.stream().noneMatch(c -> c.contentid() == id.getAsInt()))
                serverContent.add(SkinCache.description(id.getAsInt()).orElseGet(() -> new LiteContent(id.getAsInt(), -1, "", -1,
                        Set.of(entry.getKey().tag()), "Asset #" + id.getAsInt(), -1)));
        }
        // Use the edited person's outfit, not the dummy's last preview texture.
        String currentClothing = previousScreen == null ? selectedClothing : previousScreen.getVillager().getClothes();
        if (currentClothing.startsWith("immersive_library:")) {
            try {
                int selectedId = Integer.parseInt(currentClothing.substring(18));
                if (serverContent.stream().noneMatch(content -> content.contentid() == selectedId)) {
                    serverContent.add(new LiteContent(selectedId, -1, "", -1,
                            Set.of("clothing"), "Asset #" + selectedId, -1));
                }
                // Stable sorting preserves every other outfit's order before pagination.
                serverContent.sort(Comparator.comparing(content -> content.contentid() != selectedId));
            } catch (NumberFormatException ignored) {
                // A malformed clothing identifier cannot be an online library entry.
            }
        }
    }

    public enum Page {
        LIBRARY,
        EDITOR_LOCKED,
        EDITOR_PREPARE,
        EDITOR_TYPE,
        EDITOR,
        HELP,
        LOGIN,
        LOGOUT,
        DETAIL,
        DELETE,
        REPORT
    }

    public enum SkinType {
        CLOTHING, HAIR, CLASSIC, HAT, GLASSES, FACE, BACK, EYES;
        public String tag(){return name().toLowerCase(Locale.ROOT);}
        public Component text(){return Component.translatable("gui.skin_library.kind."+tag());}
        public int accessorySlot(){return switch(this){case HAT->0;case GLASSES->1;case FACE->2;case BACK->3;default->-1;};}
        public int coverageRegion() {
            return switch (this) {
                case HAT -> AccessoryTextureCoverage.HEAD;
                case GLASSES -> AccessoryTextureCoverage.UPPER_HEAD;
                case FACE -> AccessoryTextureCoverage.FACE;
                default -> 0;
            };
        }
        public boolean usesCoverage() {
            return this == CLASSIC || this == EYES || this == HAT || this == GLASSES || this == FACE;
        }
        public boolean matches(LiteContent content) {
            if (content.hasTag("mcarem")) return content.hasTag(tag());
            return switch (this) {
                case CLASSIC -> SkinCache.isCompleteSkin(content);
                case EYES -> SkinCache.isEyeTexture(content);
                case HAT -> SkinCache.coversOnly(content, AccessoryTextureCoverage.HEAD);
                case GLASSES -> SkinCache.coversOnly(content, AccessoryTextureCoverage.UPPER_HEAD);
                case FACE -> SkinCache.coversOnly(content, AccessoryTextureCoverage.FACE);
                default -> content.hasTag(tag());
            };
        }
        public static SkinType of(LiteContent content) {
            if (content.hasTag("mcarem")) {
                for (SkinType kind : values()) if (content.hasTag(kind.tag())) return kind;
                return CLOTHING;
            }
            for (SkinType kind : new SkinType[]{CLASSIC, EYES, GLASSES, FACE, HAT}) {
                if (kind.matches(content)) return kind;
            }
            for (SkinType kind : new SkinType[]{BACK, HAIR, CLOTHING}) {
                if (content.hasTag(kind.tag())) return kind;
            }
            return CLOTHING;
        }
    }

    public enum SortingMode {
        LIKES("likes"),
        NEWEST("date"),
        RECOMMENDATIONS("recommendations"),
        REPORTS("reports");

        public final String order;

        SortingMode(String order) {
            this.order = order;
        }
    }

    public enum SubscriptionFilter {
        LIBRARY,
        LOCAL,
        GLOBAL,
        LIKED,
        SUBMISSIONS;

        public static Component getText(SubscriptionFilter t) {
            return Component.translatable("gui.skin_library.subscription_filter." + t.name().toLowerCase(Locale.ROOT));
        }
    }
}
