package com.ancientkira.mca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ancientkira.mca.MCA;
import com.ancientkira.mca.client.model.CribEntityModel;
import com.ancientkira.mca.entity.CribEntity;
import com.ancientkira.mca.entity.CribWoodType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CribEntityRenderer extends EntityRenderer<CribEntity, CribEntityRenderState> {
    private static final int TEXTURE_WIDTH = 88;
    private static final int TEXTURE_HEIGHT = 60;

    private final Map<String, Identifier> registeredTextures = new HashMap<>();
    private final ItemModelResolver itemModelResolver;
    protected final CribEntityModel model;

    public CribEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);

        this.itemModelResolver = ctx.getItemModelResolver();
        this.model = new CribEntityModel(LayerDefinition.create(CribEntityModel.getModelData(CubeDeformation.NONE), TEXTURE_WIDTH, TEXTURE_HEIGHT).bakeRoot());
        this.shadowRadius = 0.75F;

        for (CribWoodType woodType : CribWoodType.values()) {
            for (DyeColor color : DyeColor.values()) {
                try {
                    registeredTextures.put(getTextureID(woodType, color), generateMultiTexture(woodType, color));
                } catch (IOException e) {
                    MCA.LOGGER.warn("And error occurred while loading dynamic crib texture! Skipping...\n{}", e.getMessage());
                }
            }
        }
    }

    @Override
    public CribEntityRenderState createRenderState() {
        return new CribEntityRenderState();
    }

    @Override
    public void extractRenderState(CribEntity cribEntity, CribEntityRenderState state, float partialTicks) {
        super.extractRenderState(cribEntity, state, partialTicks);
        state.crib = cribEntity;
        state.yRot = Mth.rotLerp(partialTicks, cribEntity.yRotO, cribEntity.getYRot());
        this.itemModelResolver.updateForNonLiving(state.babyItem, cribEntity.getBabyItem(), ItemDisplayContext.FIXED, cribEntity);
    }

    @Override
    public void submit(CribEntityRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        CribEntity crib = state.crib;
        if (crib == null) {
            return;
        }

        Identifier texture = getTextureLocation(state);

        poseStack.pushPose();
        poseStack.translate(0.0, 0.375, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yRot));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));

        this.model.setupAnim(state);
        submitNodeCollector.submitModel(this.model, state, poseStack, texture, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);

        ItemStack babyItem = crib.getBabyItem();
        if (!babyItem.equals(ItemStack.EMPTY) && !state.babyItem.isEmpty()) {
            poseStack.translate(0.0F, 0.05F, 0.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
            poseStack.scale(0.75F, 0.75F, 0.75F);
            state.babyItem.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        }

        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    private String getTextureID(CribEntity cribEntity) {
        return getTextureID(cribEntity.getWoodType(), cribEntity.getColor());
    }

    private String getTextureID(CribWoodType wood, DyeColor color) {
        return wood.toString().toLowerCase(Locale.ROOT) + "-" + color.getName();
    }

    private Identifier generateMultiTexture(CribWoodType wood, DyeColor color) throws IOException {
        String frame = "assets/mca/textures/entity/crib/frames/" + wood.toString().toLowerCase(Locale.ROOT) + ".png";
        String bed = "assets/mca/textures/entity/crib/beds/" + color.getName() + ".png";
        try (InputStream frameStream = openTexture(frame, "assets/mca/textures/entity/crib/frames/oak.png");
             InputStream bedStream = openTexture(bed, "assets/mca/textures/entity/crib/beds/white.png");
             NativeImage frameImage = NativeImage.read(frameStream);
             NativeImage bedImage = NativeImage.read(bedStream)) {
            NativeImage combined = new NativeImage(TEXTURE_WIDTH, TEXTURE_HEIGHT, true);
            try {
                composite(combined, frameImage);
                composite(combined, bedImage);
                Identifier textureId = Identifier.fromNamespaceAndPath(MCA.MOD_ID, "dynamic/crib/" + getTextureID(wood, color));
                DynamicTexture dynTex = new DynamicTexture(textureId::toString, combined);
                Minecraft.getInstance().getTextureManager().register(textureId, dynTex);
                return textureId;
            } catch (RuntimeException | Error exception) {
                combined.close();
                throw exception;
            }
        }
    }

    private static InputStream openTexture(String path, String fallback) throws IOException {
        ClassLoader loader = MCA.class.getClassLoader();
        InputStream stream = loader.getResourceAsStream(path);
        if (stream == null) stream = loader.getResourceAsStream(fallback);
        if (stream == null) throw new IOException("Missing crib texture: " + path);
        return stream;
    }

    private static void composite(NativeImage destination, NativeImage source) {
        for (int y = 0; y < Math.min(destination.getHeight(), source.getHeight()); y++) {
            for (int x = 0; x < Math.min(destination.getWidth(), source.getWidth()); x++) {
                destination.setPixel(x, y, sourceOver(destination.getPixel(x, y), source.getPixel(x, y)));
            }
        }
    }

    // Straight-alpha ARGB composition, without starting AWT's macOS application loop.
    private static int sourceOver(int background, int foreground) {
        int alpha = foreground >>> 24;
        if (alpha == 0) return background;
        if (alpha == 255) return foreground;
        int backgroundAlpha = background >>> 24;
        int scaledAlpha = alpha * 255 + backgroundAlpha * (255 - alpha);
        int result = ((scaledAlpha + 127) / 255) << 24;
        for (int shift = 0; shift <= 16; shift += 8) {
            int channel = (((foreground >>> shift) & 255) * alpha * 255
                    + ((background >>> shift) & 255) * backgroundAlpha * (255 - alpha)
                    + scaledAlpha / 2) / scaledAlpha;
            result |= channel << shift;
        }
        return result;
    }

    public Identifier getTextureLocation(CribEntityRenderState state) {
        return state.crib == null ? registeredTextures.values().stream().findFirst().orElseThrow() : registeredTextures.get(getTextureID(state.crib));
    }
}
