package com.ancientkira.mca.client.gui;

import com.ancientkira.mca.MCA;
import com.ancientkira.mca.client.gui.widget.WidgetUtils;
import com.ancientkira.mca.resources.data.BuildingType;
import com.ancientkira.mca.server.world.data.Building;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class BlueprintMapRenderer implements AutoCloseable {
    private static final Identifier ICON_TEXTURES = MCA.locate("textures/buildings.png");
    private final BlueprintTerrainRenderer terrainRenderer = new BlueprintTerrainRenderer();

    RenderResult render(GuiGraphicsExtractor context, BlueprintMapViewport viewport,
                        BlueprintMapGeometry geometry, Integer selectedFloor,
                        boolean showTerrain, boolean showBuildingIcons, boolean showPlayerHead,
                        LocalPlayer player, double playerX, double playerZ, int mouseX, int mouseY) {
        if (showTerrain) {
            context.fill(viewport.left() + 1, viewport.top() + 1, viewport.right() - 1, viewport.bottom() - 1, 0xff36551f);
        }
        WidgetUtils.drawRectangle(context, viewport.left(), viewport.top(), viewport.right(), viewport.bottom(), 0xffffff88);
        context.enableScissor(viewport.left() + 1, viewport.top() + 1, viewport.right() - 1, viewport.bottom() - 1);
        if (showTerrain) {
            terrainRenderer.render(context, viewport);
        }

        BlueprintMapFootprint.Cell hoveredCell = viewport.screenToCell(mouseX, mouseY);
        boolean mouseInsideMap = viewport.containsInner(mouseX, mouseY);
        List<BlueprintMapRenderer.HoverTarget> hoverTargets = new ArrayList<>();
        List<BlueprintMapGeometry.MapFootprintLayer> layers = geometry.footprintLayers(selectedFloor);
        Matrix3x2fStack matrices = context.pose();
        matrices.pushMatrix();
        for (BlueprintMapGeometry.MapFootprintLayer layer : layers) {
            Building building = layer.building();
            BuildingType type = layer.presentationType();
            if (type.isIcon()) {
                if (showBuildingIcons && type.hasIcon()) {
                    drawBuildingIcon(context, viewport, building.getCenter(), type);
                }
            } else {
                int fillAlpha = selectedFloor == null ? 74 : 106;
                int color = withAlpha(type.getColor(), fillAlpha);
                drawSpans(context, viewport, layer.fillSpans(), color);
                drawEdges(context, viewport, layer.outlineEdges(), withAlpha(type.getColor(), 255));
                if (showBuildingIcons && type.visible() && type.hasIcon()) {
                    drawBuildingIcon(context, viewport, building.getCenter(), type);
                }
            }

            if (mouseInsideMap && layer.footprintCells().contains(hoveredCell)) {
                hoverTargets.add(new HoverTarget(building, layer.floorOrdinal(), layer.logicalBuildingId(), layer.anchorY()));
            }
        }
        matrices.popMatrix();

        if (player != null && showPlayerHead) {
            BlueprintMapViewport.ScreenPoint point = viewport.clampMarker(
                    viewport.screenX(playerX), viewport.screenY(playerZ), 8, 2);
            int x = point.x() - 3;
            int y = point.y() - 3;
            context.fill(x - 1, y - 1, x + 7, y + 7, 0x99000000);
            PlayerFaceExtractor.extractRenderState(context, player.getSkin(), x, y, 6);
        }
        context.disableScissor();

        hoverTargets.sort(Comparator.comparingInt(HoverTarget::anchorY).reversed());
        return new RenderResult(List.copyOf(hoverTargets));
    }

    private static void drawBuildingIcon(GuiGraphicsExtractor context, BlueprintMapViewport viewport,
                                         BlockPos center, BuildingType type) {
        int x = (int) Math.round(viewport.screenX(center.getX() + 0.5));
        int y = (int) Math.round(viewport.screenY(center.getZ() + 0.5));
        WidgetUtils.drawBuildingIcon(context, ICON_TEXTURES, x, y, type.iconU(), type.iconV());
    }

    private static void drawSpans(GuiGraphicsExtractor context, BlueprintMapViewport viewport,
                                  List<BlueprintMapFootprint.RowSpan> spans, int color) {
        for (BlueprintMapFootprint.RowSpan span : spans) {
            int x0 = (int) Math.floor(viewport.screenX(span.minX()));
            int x1 = (int) Math.ceil(viewport.screenX(span.maxX() + 1));
            int y0 = (int) Math.floor(viewport.screenY(span.z()));
            int y1 = (int) Math.ceil(viewport.screenY(span.z() + 1));
            context.fill(Math.min(x0, x1), Math.min(y0, y1),
                    Math.max(Math.min(x0, x1) + 1, Math.max(x0, x1)),
                    Math.max(Math.min(y0, y1) + 1, Math.max(y0, y1)), color);
        }
    }

    private static void drawEdges(GuiGraphicsExtractor context, BlueprintMapViewport viewport,
                                  List<BlueprintMapFootprint.Edge> edges, int color) {
        for (BlueprintMapFootprint.Edge edge : edges) {
            int x0 = (int) Math.round(viewport.screenX(edge.x0()));
            int y0 = (int) Math.round(viewport.screenY(edge.z0()));
            int x1 = (int) Math.round(viewport.screenX(edge.x1()));
            int y1 = (int) Math.round(viewport.screenY(edge.z1()));
            if (edge.z0() == edge.z1()) {
                context.fill(Math.min(x0, x1), y0, Math.max(Math.min(x0, x1) + 1, Math.max(x0, x1)), y0 + 1, color);
            } else {
                context.fill(x0, Math.min(y0, y1), x0 + 1, Math.max(Math.min(y0, y1) + 1, Math.max(y0, y1)), color);
            }
        }
    }

    private static int withAlpha(int color, int alpha) {
        return color & 0x00ffffff | alpha << 24;
    }

    @Override
    public void close() {
    }

    record HoverTarget(Building building, Integer floorOrdinal, int logicalBuildingId, int anchorY) {
    }

    record RenderResult(List<HoverTarget> hoverTargets) {
    }
}
