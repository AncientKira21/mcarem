package com.ancientkira.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ancientkira.mca.MCA;
import com.ancientkira.mca.client.render.VillagerVisuals;
import com.ancientkira.mca.client.resources.EyeTextureLayers;
import com.ancientkira.mca.resources.FaceList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import com.mojang.blaze3d.platform.NativeImage;

import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FaceLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends VillagerLayer<S, M> {
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;

    private final String variant;
    private final Boolean aboveHair;

    public FaceLayer(RenderLayerParent<S, M> renderer, M model, String variant) {
        super(renderer, model);
        this.variant = variant;
        this.aboveHair = null;
    }
    public FaceLayer(RenderLayerParent<S, M> renderer, M model, String variant, boolean aboveHair) {
        super(renderer,model);this.variant=variant;this.aboveHair=aboveHair;
    }
    @Override public void submit(PoseStack pose,SubmitNodeCollector collector,int light,S state,float yRot,float xRot) {
        if(aboveHair!=null && aboveHair!=com.ancientkira.mca.Config.getInstance().eyesOverHair)return;
        super.submit(pose,collector,light,state,yRot,xRot);
    }

    @Override
    protected void prepareModel(S state) {
        setAllVisible(this.model, false);
        this.model.head.visible = true;
    }

    @Override
    protected boolean isTranslucent() {
        return true;
    }

    @Override
    public int getColor(S state, float tickDelta) {
        return VillagerVisuals.require(state).eyeDye();
    }

    @Override
    public void renderFinal(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, S state, float tickDelta, boolean visible, boolean glowing) {
        int tint = LivingEntityRenderer.getOverlayCoords(state, 0.0F);

        Identifier skin = getSkin(state);
        var visuals = VillagerVisuals.require(state);
        var expressionState=state instanceof com.ancientkira.mca.client.render.VillagerRenderState v ? v : null;
        String expression=expressionState!=null && expressionState.mcaHurtFace ? "hurt"
                : expressionState!=null && expressionState.mcaScaredFace ? "scared" : "normal";
        boolean blink=expressionState!=null && expressionState.mcaCorpse || expression.equals("normal") && visuals.isBlinking();
        Identifier renderedSkin = blink ? getBlinkSkin() : skin;
        if (canUse(renderedSkin)) {
            if (blink) {
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, OPAQUE_WHITE, renderedSkin, tint, visible, glowing, state);
            } else if (visuals.heterochromia()) {
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, OPAQUE_WHITE, getOrGenerateEyeLayer(skin, EyeTextureLayers.Layer.SCLERA, EyeTextureLayers.Side.FULL, expression), tint, visible, glowing, state);
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, EyeTextureLayers.DETAILS_TINT, getOrGenerateEyeLayer(skin, EyeTextureLayers.Layer.DETAILS, EyeTextureLayers.Side.FULL, expression), tint, visible, glowing, state);
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, getEyeColor(visuals, tickDelta, true), getOrGenerateEyeLayer(skin, EyeTextureLayers.Layer.IRIS, EyeTextureLayers.Side.LEFT, expression), tint, visible, glowing, state);
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, getEyeColor(visuals, tickDelta, false), getOrGenerateEyeLayer(skin, EyeTextureLayers.Layer.IRIS, EyeTextureLayers.Side.RIGHT, expression), tint, visible, glowing, state);
            } else {
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, OPAQUE_WHITE, getOrGenerateEyeLayer(skin, EyeTextureLayers.Layer.SCLERA, EyeTextureLayers.Side.FULL, expression), tint, visible, glowing, state);
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, EyeTextureLayers.DETAILS_TINT, getOrGenerateEyeLayer(skin, EyeTextureLayers.Layer.DETAILS, EyeTextureLayers.Side.FULL, expression), tint, visible, glowing, state);
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, getEyeColor(visuals, tickDelta, false), getOrGenerateEyeLayer(skin, EyeTextureLayers.Layer.IRIS, EyeTextureLayers.Side.FULL, expression), tint, visible, glowing, state);
            }
        }

        Identifier overlay = getOverlay(state);
        if (!Objects.equals(skin, overlay) && canUse(overlay)) {
            renderModel(poseStack, submitNodeCollector, lightCoords, this.model, OPAQUE_WHITE, overlay, tint, visible, glowing, state);
        }
    }

    @Override
    public Identifier getSkin(S state) {
        var visuals = VillagerVisuals.require(state);
        var selected=com.ancientkira.mca.util.ImmersiveLibraryIds.contentId(visuals.eyeTexture());
        if(selected.isPresent() && com.ancientkira.mca.client.gui.immersive_library.SkinCache.getImage(selected.getAsInt()).isPresent())
            return com.ancientkira.mca.client.gui.immersive_library.SkinCache.getTextureIdentifier(selected.getAsInt());
        FaceList list = FaceList.getInstance();
        return list == null ? getBlinkSkin() : list.pick(variant, visuals.faceGene());
    }

    private Identifier getBlinkSkin() {
        return cached("skins/face/normal/blink.png", MCA::locate);
    }

    private static final Map<EyeLayerKey, Identifier> EYE_TEXTURE_CACHE = new ConcurrentHashMap<>();

    public static void clearGeneratedEyeTextureCache() {
        com.ancientkira.mca.client.render.CorpseEyeTextures.clear();
        var textureManager = net.minecraft.client.Minecraft.getInstance().getTextureManager();
        EYE_TEXTURE_CACHE.values().forEach(id -> {
            if (id.getNamespace().equals(MCA.MOD_ID) && id.getPath().startsWith("dynamic/eye/")) {
                textureManager.release(id);
            }
        });
        EYE_TEXTURE_CACHE.clear();
    }

    private record EyeLayerKey(Identifier texture, EyeTextureLayers.Layer layer, EyeTextureLayers.Side side, String expression) {
    }

    private static int getEyeColor(VillagerVisuals visuals, float tickDelta, boolean left) {
        return visuals.eyeColor(tickDelta, left);
    }

    private static NativeImage readEyeImage(Identifier id) throws java.io.IOException {
        var content=com.ancientkira.mca.util.ImmersiveLibraryIds.contentId(id.toString());
        if(content.isPresent()) {
            NativeImage source=com.ancientkira.mca.client.gui.immersive_library.SkinCache.getImage(content.getAsInt()).orElseThrow(()->new java.io.IOException("Eye texture is loading"));
            NativeImage copy=new NativeImage(source.getWidth(),source.getHeight(),true);copy.copyFrom(source);return copy;
        }
        var resource=Minecraft.getInstance().getResourceManager().getResource(id).orElseThrow(()->new java.io.IOException("Missing eye texture"));
        try(InputStream stream=resource.open()){return NativeImage.read(stream);}
    }

    private Identifier getOrGenerateEyeLayer(Identifier original, EyeTextureLayers.Layer layer, EyeTextureLayers.Side side, String expression) {
        return EYE_TEXTURE_CACHE.computeIfAbsent(new EyeLayerKey(original, layer, side, expression), key -> {
            try {
                Identifier id = key.texture();
                try (NativeImage originalImage = readEyeImage(id)) {
                    int w = originalImage.getWidth();
                    int h = originalImage.getHeight();
                    if(key.expression().equals("scared")) {
                        EyeTextureLayers.Bounds originalBounds=EyeTextureLayers.findBounds(originalImage);
                        int middle=originalBounds.minX()+originalBounds.width()/2;
                        int[][] eyes={{w,h,-1,-1},{w,h,-1,-1}};
                        for(int x=0;x<w;x++)for(int y=0;y<h;y++) {
                            if(ARGB.alpha(originalImage.getPixel(x,y))==0)continue;
                            int[] eye=eyes[x<middle?0:1];
                            eye[0]=Math.min(eye[0],x);eye[1]=Math.min(eye[1],y);
                            eye[2]=Math.max(eye[2],x);eye[3]=Math.max(eye[3],y);
                        }
                        for(int x=0;x<w;x++)for(int y=0;y<h;y++) {
                            int eyeSide=x<middle?0:1;int[] eye=eyes[eyeSide];
                            originalImage.setPixel(x,y,eye[2]<eye[0]?0:
                                    com.ancientkira.mca.client.resources.EyeExpressionPixels.scaredPixel(w,x,y,
                                            eye[0],eye[1],eye[2],eye[3],eyeSide==0));
                        }
                    } else if(!key.expression().equals("normal")) {
                        for(int x=0;x<w;x++)for(int y=0;y<h;y++) originalImage.setPixel(x,y,
                                com.ancientkira.mca.client.resources.EyeExpressionPixels.pixel(key.expression(),w,x,y));
                    }
                    EyeTextureLayers.Bounds bounds = EyeTextureLayers.findBounds(originalImage);
                    if (key.side() != EyeTextureLayers.Side.FULL && bounds.width() % 2 != 0) {
                        throw new IllegalStateException("Face eye texture width must be divisible by 2 for heterochromia: " + id + " bounds=" + bounds);
                    }
                    int splitX = bounds.minX() + bounds.width() / 2;
                    NativeImage newImage = new NativeImage(w, h, true);

                    try {
                        for (int x = 0; x < w; x++) {
                            for (int y = 0; y < h; y++) {
                                int pixel = originalImage.getPixel(x, y);
                                int a = ARGB.alpha(pixel);
                                if (a == 0) continue;
                                if (!EyeTextureLayers.isInSide(x, splitX, key.side())) continue;

                                if (EyeTextureLayers.isPixelForLayer(key.layer(), a, ARGB.red(pixel), ARGB.green(pixel), ARGB.blue(pixel))) {
                                    newImage.setPixel(x, y, pixel);
                                }
                            }
                        }

                        Identifier newId = Identifier.fromNamespaceAndPath("mca", "dynamic/eye/" + key.expression() + "/" + key.side().name().toLowerCase(Locale.ROOT) + "/" + key.layer().name().toLowerCase(Locale.ROOT) + "/" + id.getNamespace() + "_" + id.getPath().replace("/", "_"));
                        Minecraft.getInstance().getTextureManager().register(newId, new DynamicTexture(newId::toString, newImage));
                        return newId;
                    } catch (Exception exception) {
                        newImage.close();
                        throw exception;
                    }
                }
            } catch (Exception e) {
                MCA.LOGGER.warn("Failed to generate eye texture layer for {}", key.texture(), e);
                return key.texture();
            }
        });
    }
}
