package com.ancientkira.mca.client.gui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import com.ancientkira.mca.client.gui.PreviewEntityAnimation;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3x2fStack;

import java.util.List;

// FIXME: 1.20 loses the DrawableHelper attachment, determine if DrawContext can replace this
public class WidgetUtils {
    public static void drawBuildingIcon(GuiGraphicsExtractor context, Identifier texture, int x, int y, int u, int v) {
        drawBuildingIcon(context, texture, x, y, u, v, 0xffffffff);
    }

    public static void drawBuildingIcon(GuiGraphicsExtractor context, Identifier texture, int x, int y, int u, int v, int color) {
        Matrix3x2fStack matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(x - 6.6f, y - 6.6f);
        matrices.scale(0.66f, 0.66f);
        context.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, u, v, 20, 20, 256, 256, color);
        matrices.popMatrix();
    }

    public static void drawRectangle(GuiGraphicsExtractor context, int x0, int y0, int x1, int y1, int color) {
        context.fill(x0 + 1, y0, x1, y0 + 1, color);
        context.fill(x1 - 1, y0 + 1, x1, y1, color);
        context.fill(x0, y1 - 1, x1 - 1, y1, color);
        context.fill(x0, y0, x0 + 1, y1 - 1, color);
    }

    public static void drawTexturedQuad(GuiGraphicsExtractor context, Identifier texture, float x0, float x1, float y0, float y1, float u0, float u1, float v0, float v1) {
        context.blit(texture, Math.round(x0), Math.round(y0), Math.round(x1), Math.round(y1), u0, u1, v0, v1);
    }

    public static void drawTexturedQuad(GuiGraphicsExtractor context, Identifier texture, float x0, float x1, float y0, float y1, float u0, float u1, float v0, float v1, int color) {
        int width = Math.round(x1 - x0);
        int height = Math.round(y1 - y0);
        context.blit(RenderPipelines.GUI_TEXTURED, texture, Math.round(x0), Math.round(y0), u0 * 64.0f, v0 * 64.0f, width, height, Math.round((u1 - u0) * 64.0f), Math.round((v1 - v0) * 64.0f), 64, 64, color);
    }

    public static void drawTooltip(GuiGraphicsExtractor context, Font font, Component text, int x, int y) {
        drawTooltip(context, font, List.of(text), x, y);
    }

    public static void drawTooltip(GuiGraphicsExtractor context, Font font, List<Component> text, int x, int y) {
        List<FormattedCharSequence> lines = text.stream().map(Component::getVisualOrderText).toList();
        List<ClientTooltipComponent> components = lines.stream().map(ClientTooltipComponent::create).toList();
        context.tooltip(font, components, x, y, DefaultTooltipPositioner.INSTANCE, null);
    }

    /** Extract an isolated GUI snapshot rather than inheriting world-only visibility and offsets. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void drawLibraryEntity(GuiGraphicsExtractor context, int x0, int y0, int x1, int y1,
                                         int size, float mouseX, float mouseY, LivingEntity entity) {
        com.ancientkira.mca.client.gui.immersive_library.LocalLibrary.withoutSelections(() ->
                PreviewEntityAnimation.withPreviewTime(entity, () -> {
                    var dispatcher = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher();
                    net.minecraft.client.renderer.entity.EntityRenderer renderer = dispatcher.getRenderer(entity);
                    // Allocate and finalize a snapshot for each card before the deferred GUI draw.
                    var state = renderer.createRenderState(entity, PreviewEntityAnimation.getActivePartialTick());
                    state.isInvisible = false;
                    state.shadowPieces.clear();
                    state.outlineColor = 0;
                    state.nameTag = null;
                    state.displayFireAnimation = false;
                    state.passengerOffset = null;
                    float horizontalAngle = (float) Math.atan(((x0 + x1) * 0.5F - mouseX) / 40.0F);
                    float verticalAngle = (float) Math.atan(((y0 + y1) * 0.5F - mouseY) / 40.0F);
                    if (state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState living) {
                        living.isInvisibleToPlayer = false;
                        living.pose = net.minecraft.world.entity.Pose.STANDING;
                        living.bedOrientation = null;
                        living.deathTime = 0;
                        living.scale = 1.0F;
                        living.bodyRot = 180.0F + horizontalAngle * 20.0F;
                        living.yRot = horizontalAngle * 20.0F;
                        living.xRot = -verticalAngle * 20.0F;
                    }
                    if (state instanceof com.ancientkira.mca.client.render.VillagerRenderState villager) {
                        villager.isPassenger = false;
                        villager.mcaSleeping = false;
                        villager.mcaCorpse = false;
                        villager.isKidnapped = false;
                        villager.panicAnimationProgress = 0;
                    }
                    // Use finite entity bounds directly; never divide GUI bounds by an inherited scale.
                    state.boundingBoxWidth = Math.max(0.1F, entity.getBbWidth());
                    state.boundingBoxHeight = Math.max(0.1F, entity.getBbHeight());
                    var tilt = new org.joml.Quaternionf().rotateX(verticalAngle * (float) Math.PI / 9.0F);
                    var rotation = new org.joml.Quaternionf().rotateZ((float) Math.PI).mul(tilt);
                    context.entity(state, size, new org.joml.Vector3f(0, state.boundingBoxHeight / 2, 0),
                            rotation, tilt, x0, y0, x1, y1);
                    return null;
                }));
    }

    /**
     * The same as the Inventory function but with negative Z
     */
    public static void drawBackgroundEntity(GuiGraphicsExtractor context, int x, int y, int size, float mouseX, float mouseY, LivingEntity entity) {
        PreviewEntityAnimation.withPreviewTime(entity, () -> {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    context,
                    x - size,
                    y - size,
                    x + size,
                    y + size,
                    size,
                    0.0f,
                    mouseX,
                    mouseY,
                    entity
            );
            return null;
        });
    }
}
