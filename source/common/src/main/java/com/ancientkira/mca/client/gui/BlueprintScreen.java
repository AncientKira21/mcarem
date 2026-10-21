package com.ancientkira.mca.client.gui;

import com.ancientkira.mca.MCA;
import com.ancientkira.mca.client.gui.widget.LegacyImageButton;
import com.ancientkira.mca.client.gui.widget.TooltipButtonWidget;
import com.ancientkira.mca.client.gui.widget.WidgetUtils;
import com.ancientkira.mca.network.Network;
import com.ancientkira.mca.network.c2s.GetVillageRequest;
import com.ancientkira.mca.network.c2s.RenameVillageMessage;
import com.ancientkira.mca.network.c2s.ReportBuildingMessage;
import com.ancientkira.mca.network.c2s.SaveVillageMessage;
import com.ancientkira.mca.resources.BuildingTypes;
import com.ancientkira.mca.resources.Rank;
import com.ancientkira.mca.resources.data.BuildingType;
import com.ancientkira.mca.resources.data.tasks.Task;
import com.ancientkira.mca.server.world.data.Building;
import com.ancientkira.mca.server.world.data.RoomScanPlan;
import com.ancientkira.mca.server.world.data.Village;
import com.ancientkira.mca.util.compat.ButtonWidget;
import com.ancientkira.mca.util.localization.FlowingText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import org.joml.Matrix3x2fStack;

import java.util.*;
import java.util.function.Consumer;

public class BlueprintScreen extends ExtendedScreen {
    //gui element Y positions
    private static final int POSITION_TAXES = -60;
    private static final int POSITION_BIRTH = -10;
    private static final int POSITION_MARRIAGE = 40;
    private static final Identifier ICON_TEXTURES = MCA.locate("textures/buildings.png");
    private static Integer rememberedFloorOrdinal;
    private static MapScaleMode rememberedMapScaleMode = MapScaleMode.FIT;
    private static boolean rememberedPlayerCentered;
    private static boolean rememberedShowPlayerHead = true;
    // 1.19.3: This needs to be the MC type, DO NOT TOUCH !!!
    private final List<net.minecraft.client.gui.components.Button> catalogButtons = new LinkedList<>();
    private Village village;
    private int reputation;
    private String medicalInsurance = "none";
    public void setMedicalInsurance(String status) { medicalInsurance = status; }
    private boolean isVillage;
    private Rank rank;
    private Set<String> completedTasks;
    private String page;
    private ButtonWidget[] buttonTaxes;
    private ButtonWidget[] buttonBirths;
    private ButtonWidget[] buttonMarriage;
    private EditBox pathBlockSearch;
    private final List<ButtonWidget> pathBlockOptions = new ArrayList<>();
    private ButtonWidget pathBlockPrevious;
    private ButtonWidget pathBlockNext;
    private ButtonWidget pathBlockPageLabel;
    private int pathBlockPage;
    private ButtonWidget buttonPage;
    private int pageNumber = 0;
    private int warDelayMinutes;
    private int warPage;
    private BuildingType selectedBuilding;
    private UUID selectedVillager;
    private Integer selectedFloorOrdinal = rememberedFloorOrdinal;
    private MapScaleMode mapScaleMode = rememberedMapScaleMode;
    private boolean showPlayerHead = rememberedShowPlayerHead;
    private boolean showIcons = true;
    private boolean showTerrain = true;
    private boolean playerCentered = rememberedPlayerCentered;
    private ButtonWidget floorPreviousButton;
    private ButtonWidget floorButton;
    private ButtonWidget floorNextButton;
    private final List<ButtonWidget> floorOptionButtons = new ArrayList<>();
    private EditBox floorSearch;
    private boolean floorDropdownOpen;
    private int floorDropdownOffset;
    private ButtonWidget iconsButton;
    private ButtonWidget terrainButton;
    private ButtonWidget mapScaleButton;
    private ButtonWidget playerCenteredButton;
    private ButtonWidget playerHeadButton;
    private boolean selectPlayerFloorOnNextVillageResponse;
    private TooltipButtonWidget structureScanButton;
    private TooltipButtonWidget attachmentScanButton;
    private TooltipButtonWidget removeRoomButton;
    private ButtonWidget removeBuildingButton;
    private TooltipButtonWidget mainRoomButton;
    private TooltipButtonWidget inheritanceButton;
    private List<Integer> floorOrdinals = List.of();
    private BlueprintRoomTypeResolver roomTypeResolver = BlueprintRoomTypeResolver.create(null);
    private BlueprintMapGeometry mapGeometry = BlueprintMapGeometry.empty();
    private BlueprintTooltipFactory tooltipFactory = BlueprintTooltipFactory.empty();
    private final BlueprintMapRenderer mapRenderer = new BlueprintMapRenderer();

    private int mouseX;
    private int mouseY;

    private Map<Rank, List<Task>> tasks;

    public BlueprintScreen() {
        super(Component.literal("Blueprint"));
    }

    private void saveVillage() {
        Network.sendToServer(new SaveVillageMessage(village));
    }

    private void changeTaxes(float d) {
        village.setTaxes(Math.max(0.0f, Math.min(1.0f, village.getTaxes() + d)));
        saveVillage();
    }

    private void changePopulationThreshold(float d) {
        village.setPopulationThreshold(Math.max(0.0f, Math.min(1.0f, village.getPopulationThreshold() + d)));
        saveVillage();
    }

    private void changeMarriageThreshold(float d) {
        village.setMarriageThreshold(Math.max(0.0f, Math.min(1.0f, village.getMarriageThreshold() + d)));
        saveVillage();
    }

    private ButtonWidget[] createValueChanger(int x, int y, int w, int h, Consumer<Boolean> onPress, Component tooltip) {
        ButtonWidget[] buttons = new ButtonWidget[3];

        buttons[1] = addRenderableWidget(new ButtonWidget(x - w / 2, y, w / 4, h,
                Component.literal("<<"), b -> onPress.accept(false)));

        buttons[2] = addRenderableWidget(new ButtonWidget(x + w / 4, y, w / 4, h,
                Component.literal(">>"), b -> onPress.accept(true)));

        buttons[0] = addRenderableWidget(new ButtonWidget(x - w / 4, y, w / 2, h,
                Component.literal(""), b -> {
        },
                tooltip
        ));

        return buttons;
    }

    protected void drawBuildingIcon(GuiGraphicsExtractor context, Identifier texture, int x, int y, int u, int v) {
        WidgetUtils.drawBuildingIcon(context, texture, x, y, u, v);
    }

    private List<Integer> getFloorNavigationOrder() {
        List<Integer> floors = new ArrayList<>();
        floors.add(null);
        floorOrdinals.stream().sorted().forEach(floors::add);
        return floors;
    }

    private List<Integer> getFilteredFloors() {
        String query = floorSearch == null ? "" : floorSearch.getValue().strip().toLowerCase(Locale.ROOT);
        return getFloorNavigationOrder().stream().filter(floor -> getFloorLabel(floor).getString()
                .toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    private void changeSelectedFloor(int direction) {
        List<Integer> floors = getFloorNavigationOrder();
        int currentIndex = floors.indexOf(selectedFloorOrdinal);
        int nextIndex = currentIndex + direction;
        if (nextIndex >= 0 && nextIndex < floors.size()) {
            selectedFloorOrdinal = floors.get(nextIndex);
            rememberedFloorOrdinal = selectedFloorOrdinal;
            floorDropdownOpen = false;
            floorDropdownOffset = 0;
        }
        updateFloorControls();
    }

    private void selectFloor(Integer floorOrdinal) {
        selectedFloorOrdinal = floorOrdinal;
        rememberedFloorOrdinal = floorOrdinal;
        floorDropdownOpen = false;
        floorDropdownOffset = 0;
        updateFloorControls();
    }

    private void toggleFloorDropdown() {
        floorDropdownOpen = !floorDropdownOpen;
        floorDropdownOffset = 0;
        updateFloorControls();
    }

    private Component getFloorLabel(Integer floorOrdinal) {
        if (floorOrdinal == null) {
            return Component.translatable("gui.blueprint.floor.all");
        }
        if (floorOrdinal == 0) {
            return Component.translatable("gui.blueprint.floor.ground");
        }
        return floorOrdinal > 0
                ? Component.translatable("gui.blueprint.floor.upper", floorOrdinal + 1)
                : Component.translatable("gui.blueprint.floor.basement", -floorOrdinal);
    }

    private Component getMapScaleTooltip() {
        return switch (mapScaleMode) {
            case FIT -> Component.translatable("gui.blueprint.mapScale.fit.tooltip");
            case HALF -> Component.literal("Map scale: " + mapScaleMode.label);
            case ONE -> Component.translatable("gui.blueprint.mapScale.oneToOne.tooltip");
            case TWO -> Component.translatable("gui.blueprint.mapScale.twoToOne.tooltip");
            case THREE -> Component.translatable("gui.blueprint.mapScale.threeToOne.tooltip");
            case FOUR -> Component.translatable("gui.blueprint.mapScale.fourToOne.tooltip");
        };
    }

    private void updateFloorControls() {
        List<Integer> floors = getFloorNavigationOrder();
        int selectedIndex = floors.indexOf(selectedFloorOrdinal);
        if (floorPreviousButton != null) {
            floorPreviousButton.active = selectedIndex > 0;
        }
        if (floorNextButton != null) {
            floorNextButton.active = selectedIndex >= 0 && selectedIndex < floors.size() - 1;
        }
        if (floorButton != null) {
            floorButton.setMessage(getFloorLabel(selectedFloorOrdinal));
            floorButton.setTooltip(Tooltip.create(Component.translatable("gui.blueprint.floor.tooltip")));
            floorButton.active = !floorOrdinals.isEmpty();
        }
        List<Integer> options = getFilteredFloors();
        int maxOffset = Math.max(0, options.size() - floorOptionButtons.size());
        floorDropdownOffset = Math.min(floorDropdownOffset, maxOffset);
        if (floorSearch != null) {
            floorSearch.visible = floorDropdownOpen;
            floorSearch.active = floorDropdownOpen;
            floorSearch.setY(floorButton.getY() - 20 * floorOptionButtons.size() - 22);
            if (!floorDropdownOpen) floorSearch.setFocused(false);
        }
        for (int i = 0; i < floorOptionButtons.size(); i++) {
            ButtonWidget option = floorOptionButtons.get(i);
            option.setY(floorButton.getY() - 20 * floorOptionButtons.size() + 20 * i);
            int optionIndex = floorDropdownOffset + i;
            boolean visible = floorDropdownOpen && optionIndex < options.size();
            option.visible = visible;
            option.active = visible && !Objects.equals(options.get(optionIndex), selectedFloorOrdinal);
            if (optionIndex < options.size()) {
                option.setMessage(getFloorLabel(options.get(optionIndex)));
            }
        }
    }

    private void scrollFloorDropdown(int direction) {
        int maxOffset = Math.max(0, getFilteredFloors().size() - floorOptionButtons.size());
        floorDropdownOffset = Math.max(0, Math.min(maxOffset, floorDropdownOffset + direction));
        updateFloorControls();
    }

    private float getMapScale() {
        return switch (mapScaleMode) {
            case FIT -> {
                int horizontalSpan = Math.max(village.getBox().getXSpan(), village.getBox().getZSpan());
                yield Math.min(138.0f / Math.max(1, horizontalSpan), 2.0f);
            }
            case HALF -> 0.5f;
            case ONE -> 1.0f;
            case TWO -> 2.0f;
            case THREE -> 3.0f;
            case FOUR -> 4.0f;
        };
    }

    private void cycleMapScale(int direction) {
        mapScaleMode = mapScaleMode.step(direction);
        rememberedMapScaleMode = mapScaleMode;
        updateMapScaleControl();
    }

    private void updateMapScaleControl() {
        if (mapScaleButton != null) {
            mapScaleButton.setMessage(mapScaleMode.label());
            mapScaleButton.setTooltip(Tooltip.create(getMapScaleTooltip()));
        }
    }

    private void updateMapControls() {
        updateFloorControls();
        if (iconsButton != null) {
            iconsButton.setMessage(filterLabel("gui.blueprint.buildingIcons.short", showIcons));
            iconsButton.setTooltip(Tooltip.create(Component.translatable("gui.blueprint.buildingIcons")));
        }
        if (terrainButton != null) {
            terrainButton.setMessage(filterLabel("gui.blueprint.terrain", showTerrain));
            terrainButton.setTooltip(Tooltip.create(Component.translatable("gui.blueprint.terrain.tooltip")));
        }
        if (playerCenteredButton != null) {
            playerCenteredButton.setMessage(playerCenteredLabel());
            playerCenteredButton.setTooltip(Tooltip.create(Component.translatable("gui.blueprint.playerCentered.tooltip")));
        }
        updateMapScaleControl();
        if (village != null) {
            updateMapActionControls(getPlayerRoomScanPlan());
        }
    }

    private static Component filterLabel(String labelKey, boolean enabled) {
        MutableComponent label = Component.translatable(labelKey);
        return enabled ? label.withStyle(ChatFormatting.GREEN)
                : label.withStyle(ChatFormatting.GRAY, ChatFormatting.STRIKETHROUGH);
    }

    private Component playerCenteredLabel() {
        MutableComponent label = Component.translatable("gui.blueprint.playerCentered");
        return playerCentered ? label.withStyle(ChatFormatting.GREEN) : label.withStyle(ChatFormatting.GRAY);
    }

    private RoomScanPlan getPlayerRoomScanPlan() {
        if (village == null || minecraft == null || minecraft.player == null) {
            return RoomScanPlan.addBuilding(BlockPos.ZERO);
        }
        return village.getRoomScanPlan(minecraft.player.blockPosition());
    }

    private void updateMapActionControls(RoomScanPlan plan) {
        Village.RoomScanMode mode = plan.mode();
        Village.RoomScanMode primaryMode = mode;
        if (structureScanButton != null) {
            structureScanButton.setMessage(scanTranslationKey(primaryMode));
        }
        boolean inStructure = mode == Village.RoomScanMode.UPDATE_ROOM || mode == Village.RoomScanMode.ADD_ROOM;
        if (attachmentScanButton != null) {
            attachmentScanButton.visible = mode.isAttachment();
            attachmentScanButton.active = mode.isAttachment();
            if (mode.isAttachment()) {
                attachmentScanButton.setMessage(scanTranslationKey(Village.RoomScanMode.ADD_BUILDING));
            }
        }
        if (removeRoomButton != null) {
            removeRoomButton.visible = mode == Village.RoomScanMode.UPDATE_ROOM;
            removeRoomButton.active = removeRoomButton.visible
                    && plan.functionalRoom().map(room -> !village.isMainRoom(room)).orElse(false);
            if (removeRoomButton.visible && !removeRoomButton.active) {
                removeRoomButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("gui.blueprint.removeRoom.disabled.mainRoom")));
            } else if (removeRoomButton.visible) {
                removeRoomButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("gui.blueprint.removeRoom.tooltip")));
            }
        }
        if (removeBuildingButton != null) {
            removeBuildingButton.visible = inStructure;
            removeBuildingButton.active = inStructure;
        }
        if (mainRoomButton != null) {
            Building room = plan.functionalRoom().orElse(null);
            boolean active = room != null;
            boolean automatic = active && village.isMainRoomAutomatic(room);
            mainRoomButton.setMessage(automatic ? "gui.blueprint.setMainRoom" : "gui.blueprint.useAutomaticMainRoom");
            mainRoomButton.active = active;
        }
        if (inheritanceButton != null) {
            Building room = plan.functionalRoom().orElse(null);
            inheritanceButton.active = room != null;
            if (room == null) {
                inheritanceButton.setMessage("gui.blueprint.roomInheritance.enable");
            } else if (!room.isInheritanceEnabled()) {
                inheritanceButton.setMessage("gui.blueprint.roomInheritance.enable");
            } else if (village.isMainRoom(room)) {
                inheritanceButton.setMessage("gui.blueprint.roomInheritance.disable");
            } else {
                inheritanceButton.setMessage("gui.blueprint.roomInheritance.remove");
            }
        }
    }

    private static String scanTranslationKey(Village.RoomScanMode mode) {
        return switch (mode) {
            case ADD_BUILDING -> "gui.blueprint.addBuilding";
            case ADD_ROOM -> "gui.blueprint.addRoom";
            case UPDATE_ROOM -> "gui.blueprint.updateRoom";
            case ADD_FLOOR -> "gui.blueprint.addFloor";
            case ADD_BASEMENT -> "gui.blueprint.addBasement";
        };
    }

    private void requestStructureScan(Village.RoomScanMode mode, int targetBuildingId) {
        ReportBuildingMessage.Action action = switch (mode) {
            case ADD_BUILDING -> ReportBuildingMessage.Action.ADD_BUILDING;
            case ADD_ROOM -> ReportBuildingMessage.Action.ADD_ROOM;
            case UPDATE_ROOM -> ReportBuildingMessage.Action.UPDATE_ROOM;
            case ADD_FLOOR -> ReportBuildingMessage.Action.ADD_FLOOR;
            case ADD_BASEMENT -> ReportBuildingMessage.Action.ADD_BASEMENT;
        };
        selectPlayerFloorOnNextVillageResponse = true;
        Network.sendToServer(new ReportBuildingMessage(action,
                targetBuildingId < 0 ? null : Integer.toString(targetBuildingId)));
    }

    private void reconcileSelectedFloor() {
        if (floorOrdinals.isEmpty()) {
            selectedFloorOrdinal = null;
            rememberedFloorOrdinal = null;
        } else if (selectedFloorOrdinal != null && !floorOrdinals.contains(selectedFloorOrdinal)) {
            int previous = selectedFloorOrdinal;
            selectedFloorOrdinal = floorOrdinals.stream()
                    .min(Comparator.comparingInt(floor -> Math.abs(floor - previous)))
                    .orElse(null);
            rememberedFloorOrdinal = selectedFloorOrdinal;
        }
    }

    private enum MapScaleMode {
        FIT("Fit"),
        HALF("0.5:1"),
        ONE("1:1"),
        TWO("2:1"),
        THREE("3:1"),
        FOUR("4:1");

        private final String label;

        MapScaleMode(String label) {
            this.label = label;
        }

        Component label() {
            return this == FIT ? Component.translatable("gui.blueprint.fit") : Component.literal(label);
        }

        MapScaleMode step(int direction) {
            MapScaleMode[] modes = values();
            return modes[Math.floorMod(ordinal() + direction, modes.length)];
        }
    }

    @Override
    public void init() {
        Network.sendToServer(new GetVillageRequest());
        setPage("waiting");
    }

    private void setPage(String page) {
        if (page.equals("close")) {
            assert minecraft != null;
            minecraft.setScreen(null);
            return;
        }

        this.page = page;

        clearWidgets();
        floorDropdownOpen = false;
        floorDropdownOffset = 0;
        floorOptionButtons.clear();
        floorSearch = null;
        floorPreviousButton = null;
        floorButton = null;
        floorNextButton = null;
        iconsButton = null;
        terrainButton = null;
        mapScaleButton = null;
        playerCenteredButton = null;
        playerHeadButton = null;
        structureScanButton = null;
        attachmentScanButton = null;
        removeRoomButton = null;
        removeBuildingButton = null;
        mainRoomButton = null;
        inheritanceButton = null;

        // back button
        addRenderableWidget(new ButtonWidget(5, 5, 20, 20, Component.translatable("gui.button.backarrow"), b -> setPage("close")));

        //page selection
        int bx = width / 2 - 180;
        int by = height / 2 - 78;
        if (!page.equals("rename") && (!page.equals("empty") && !page.equals("waiting"))) {
            for (String p : new String[]{"map", "rank", "catalog", "villagers", "rules", "refresh", "war"}) {
                ButtonWidget widget = new ButtonWidget(bx, by, 80, 20, Component.translatable("gui.blueprint." + p), b -> setPage(p));
                addRenderableWidget(widget);
                if (page.equals(p) || page.equals("advanced") && p.equals("map")) {
                    widget.active = false;
                }
                by += 22;
            }
        }

        switch (page) {
            case "empty":
                //add building
                bx = width / 2 - 48;
                by = height / 2;
                addRenderableWidget(new TooltipButtonWidget(bx - 50, by + 5, 96, 20, "gui.blueprint.addRoom", b -> {
                    Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.ADD_ROOM));
                    onClose();
                }));
                addRenderableWidget(new TooltipButtonWidget(bx + 50, by + 5, 96, 20, "gui.blueprint.addBuilding", b -> {
                    Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.ADD));
                    onClose();
                }));
                break;
            case "refresh":
                Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.FULL_SCAN));
                assert minecraft != null;
                assert minecraft.player != null;
                minecraft.gui.setOverlayMessage(Component.translatable("blueprint.refreshed"), true);
                setPage("map");
                break;
            case "map":
            case "advanced":
                int floorControlX = width / 2 - 75;
                int floorControlY = height / 2 + 87;
                floorPreviousButton = addRenderableWidget(new ButtonWidget(floorControlX, floorControlY, 24, 20,
                        Component.literal("<"), b -> changeSelectedFloor(-1)));
                floorButton = addRenderableWidget(new ButtonWidget(floorControlX + 26, floorControlY, 98, 20,
                        getFloorLabel(selectedFloorOrdinal), b -> toggleFloorDropdown(),
                        Component.translatable("gui.blueprint.floor.tooltip")));
                floorNextButton = addRenderableWidget(new ButtonWidget(floorControlX + 126, floorControlY, 24, 20,
                        Component.literal(">"), b -> changeSelectedFloor(1)));
                floorSearch = addRenderableWidget(new EditBox(font, floorControlX + 26, floorControlY - 42, 98, 20,
                        Component.translatable("gui.search")));
                floorSearch.setHint(Component.translatable("gui.search"));
                floorSearch.setMaxLength(64);
                floorSearch.visible = false;
                floorSearch.setResponder(query -> { floorDropdownOffset = 0; updateFloorControls(); });
                int floorOptionCount = Math.max(1, Math.min(8, (height - 120) / 20));
                for (int row = 0; row < floorOptionCount; row++) {
                    final int optionRow = row;
                    ButtonWidget option = new ButtonWidget(floorControlX + 26,
                            floorControlY - 20 * (row + 1), 98, 20, Component.empty(), b -> {
                        int index = floorDropdownOffset + optionRow;
                        List<Integer> options = getFilteredFloors();
                        if (index < options.size()) {
                            selectFloor(options.get(index));
                        }
                    });
                    option.visible = false;
                    floorOptionButtons.add(addRenderableWidget(option));
                }

                int mapFiltersX = width / 2 - 190;
                int mapFiltersY = height / 2 + 76;
                iconsButton = addRenderableWidget(new ButtonWidget(mapFiltersX, mapFiltersY, 47, 20,
                        filterLabel("gui.blueprint.buildingIcons.short", showIcons), b -> {
                    showIcons = !showIcons;
                    updateMapControls();
                }, Component.translatable("gui.blueprint.buildingIcons")));
                terrainButton = addRenderableWidget(new ButtonWidget(mapFiltersX + 50, mapFiltersY, 56, 20,
                        filterLabel("gui.blueprint.terrain", showTerrain), b -> {
                    showTerrain = !showTerrain;
                    updateMapControls();
                }, Component.translatable("gui.blueprint.terrain.tooltip")));
                mapScaleButton = addRenderableWidget(new ButtonWidget(mapFiltersX, mapFiltersY + 22, 47, 20,
                        mapScaleMode.label(), b -> cycleMapScale(1), getMapScaleTooltip()));
                int sideControlX = width / 2 + 89;
                int playerControlY = floorControlY - 12;
                playerCenteredButton = addRenderableWidget(new ButtonWidget(sideControlX, playerControlY, 110, 20,
                        playerCenteredLabel(), b -> {
                    playerCentered = !playerCentered;
                    rememberedPlayerCentered = playerCentered;
                    updateMapControls();
                }, Component.translatable("gui.blueprint.playerCentered.tooltip")));
                playerHeadButton = addRenderableWidget(new ButtonWidget(sideControlX + 112, playerControlY, 20, 20,
                        Component.empty(), b -> {
                    showPlayerHead = !showPlayerHead;
                    rememberedShowPlayerHead = showPlayerHead;
                }, Component.translatable("gui.blueprint.playerHead.tooltip")));

                bx = sideControlX;
                by = height / 2 - 56;
                if (page.equals("advanced")) {
                    MutableComponent text = Component.translatable("gui.blueprint.autoScan");
                    text.withStyle(village.isAutoScan() ? ChatFormatting.GREEN : ChatFormatting.GRAY);
                    addRenderableWidget(new TooltipButtonWidget(bx, by, 132, 20, text,
                            Component.translatable("gui.blueprint.autoScan.tooltip"), b -> {
                        Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.AUTO_SCAN));
                    }));
                    by += 22;
                    inheritanceButton = addRenderableWidget(new TooltipButtonWidget(bx, by, 132, 20,
                            "gui.blueprint.roomInheritance.enable", b -> {
                        RoomScanPlan plan = getPlayerRoomScanPlan();
                        plan.functionalRoom().ifPresent(room ->
                                Network.sendToServer(new ReportBuildingMessage(
                                        ReportBuildingMessage.Action.SET_ROOM_INHERITANCE,
                                        Boolean.toString(!room.isInheritanceEnabled()))));
                    }));
                    by += 22;
                    addRenderableWidget(new TooltipButtonWidget(bx, by, 132, 20, "gui.blueprint.restrictAccess", b -> {
                        Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.FORCE_TYPE, "blocked"));
                    }));
                    by += 22;
                    mainRoomButton = addRenderableWidget(new TooltipButtonWidget(bx, by, 132, 20,
                            "gui.blueprint.setMainRoom", b -> {
                        selectPlayerFloorOnNextVillageResponse = true;
                        Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.SET_MAIN_ROOM));
                    }));
                    by += 22;
                    RoomScanPlan outpostPlan = getPlayerRoomScanPlan();
                    Building outpostRoom = outpostPlan.building().orElse(null);
                    if (outpostRoom != null && village.canRegisterOutpost(outpostRoom)) {
                        Building root = village.getBuilding(outpostRoom.getStructureId()).orElse(outpostRoom);
                        boolean outpost = root.getType().equals("pillager_outpost");
                        addRenderableWidget(new ButtonWidget(bx, by, 132, 20,
                                Component.translatable(outpost ? "gui.blueprint.normalHouse" : "gui.blueprint.pillagerOutpost"), b ->
                                Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.FORCE_TYPE,
                                        outpost ? "house" : "pillager_outpost"))));
                        by += 22;
                    }
                    if (isVillage) {
                        addRenderableWidget(new ButtonWidget(bx, by, 132, 20,
                                Component.translatable("gui.blueprint.renameVillage"), b -> setPage("rename")));
                    }
                    addRenderableWidget(new ButtonWidget(sideControlX, height / 2 + 98, 132, 20,
                            Component.translatable("gui.back"), b -> setPage("map")));
                } else {
                    by += 66;
                    structureScanButton = addRenderableWidget(new TooltipButtonWidget(bx, by, 132, 20,
                            "gui.blueprint.addBuilding", b -> {
                        RoomScanPlan plan = getPlayerRoomScanPlan();
                        Village.RoomScanMode mode = plan.mode();
                        requestStructureScan(mode, mode == Village.RoomScanMode.ADD_BUILDING ? -1 : plan.targetBuildingId());
                    }));
                    by += 22;
                    attachmentScanButton = addRenderableWidget(new TooltipButtonWidget(bx, by, 132, 20,
                            "gui.blueprint.addFloor", b -> {
                        RoomScanPlan plan = getPlayerRoomScanPlan();
                        if (plan.mode().isAttachment()) {
                            requestStructureScan(Village.RoomScanMode.ADD_BUILDING, -1);
                        }
                    }));
                    removeRoomButton = addRenderableWidget(new TooltipButtonWidget(bx, by, 132, 20,
                            "gui.blueprint.removeRoom", b -> Network.sendToServer(
                            new ReportBuildingMessage(ReportBuildingMessage.Action.REMOVE_ROOM))));
                    by += 22;
                    removeBuildingButton = addRenderableWidget(new ButtonWidget(bx, by, 132, 20,
                            Component.translatable("gui.blueprint.removeBuilding"), b -> Network.sendToServer(
                            new ReportBuildingMessage(ReportBuildingMessage.Action.REMOVE))));
                    by += 44;
                    addRenderableWidget(new ButtonWidget(bx, by, 132, 20,
                            Component.translatable("gui.blueprint.advanced"), b -> setPage("advanced")));
                }
                updateMapControls();
                break;
            case "rank":
                break;
            case "war": {
                var opponents = village.getWarCandidates();
                int center = width / 2 + 20;
                int top = height / 2 - 78;
                addRenderableWidget(new ButtonWidget(center - 110, top, 25, 20, Component.literal("−"), b -> {
                    warDelayMinutes = Math.max(0, warDelayMinutes - 1); setPage("war");
                }));
                ButtonWidget delay = new ButtonWidget(center - 80, top, 160, 20,
                        Component.translatable("gui.blueprint.warDelay", warDelayMinutes), b -> {});
                delay.active = false; addRenderableWidget(delay);
                addRenderableWidget(new ButtonWidget(center + 85, top, 25, 20, Component.literal("+"), b -> {
                    warDelayMinutes = Math.min(20, warDelayMinutes + 1); setPage("war");
                }));
                int pages = Math.max(1, (opponents.size() + 4) / 5);
                warPage = Math.min(warPage, pages - 1);
                for (int row = 0; row < 5 && warPage * 5 + row < opponents.size(); row++) {
                    var opponent = opponents.getCompound(warPage * 5 + row).orElseThrow();
                    int target = opponent.getIntOr("Id", -1);
                    boolean atWar = opponent.getBooleanOr("AtWar", false);
                    boolean release = opponent.getBooleanOr("Release", false);
                    boolean friendly = opponent.getBooleanOr("Friendly", false);
                    String name = opponent.getStringOr("Name", "Village");
                    int nameLimit = release ? 24 : 12;
                    if (name.length() > nameLimit) name = name.substring(0, nameLimit) + "…";
                    Component label = release ? Component.translatable("gui.blueprint.releaseVillage", name) : atWar ? Component.translatable("gui.blueprint.warPeace", name)
                            : Component.translatable("gui.blueprint.warDeclare", name);
                    ButtonWidget warOption = addRenderableWidget(new ButtonWidget(center - 110, top + 28 + row * 24, 140, 20, label, b -> {
                        Network.sendToServer(new ReportBuildingMessage(release ? ReportBuildingMessage.Action.RELEASE_VILLAGE : atWar ? ReportBuildingMessage.Action.CANCEL_WAR
                                : ReportBuildingMessage.Action.DECLARE_WAR, village.getId() + ":" + target + ":" + warDelayMinutes));
                    }));
                    warOption.active = release || !friendly;
                    if(!release&&!atWar){
                        ButtonWidget merge=addRenderableWidget(new ButtonWidget(center+114,top+28+row*24,48,20,
                                Component.translatable("gui.blueprint.merge"),b->Network.sendToServer(new ReportBuildingMessage(
                                    ReportBuildingMessage.Action.MERGE_VILLAGE,village.getId()+":"+target+":0"))));
                        merge.active=rank.isAtLeast(Rank.MAYOR) || minecraft.player.isCreative()
                                && minecraft.player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
                    }
                    if(release){
                        ButtonWidget independence=addRenderableWidget(new ButtonWidget(center+34,top+28+row*24,76,20,
                                Component.translatable("gui.blueprint.independence"),b->Network.sendToServer(new ReportBuildingMessage(
                                    ReportBuildingMessage.Action.START_INDEPENDENCE,village.getId()+":"+target+":"+warDelayMinutes))));
                        independence.active=target>=0&&rank.isAtLeast(Rank.MAYOR);
                    }
                    if (!release) {
                        ButtonWidget friendship = addRenderableWidget(new ButtonWidget(center + 34, top + 28 + row * 24, 76, 20,
                                Component.translatable(friendly ? "gui.blueprint.friendly" : "gui.blueprint.befriend"), b ->
                                Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.TOGGLE_FRIENDLY,
                                        village.getId() + ":" + target + ":0"))));
                        friendship.active = rank != null && rank.isAtLeast(Rank.MAYOR);
                    }
                }
                if (pages > 1) {
                    addRenderableWidget(new ButtonWidget(center - 110, top + 152, 105, 20, Component.literal("‹"), b -> {
                        warPage = Math.floorMod(warPage - 1, pages); setPage("war");
                    }));
                    addRenderableWidget(new ButtonWidget(center + 5, top + 152, 105, 20, Component.literal("›"), b -> {
                        warPage = (warPage + 1) % pages; setPage("war");
                    }));
                }
                break;
            }
            case "catalog":
                //list catalog button
                int row = 0;
                int col = 0;
                int size = 21;
                int x = width / 2 - 4 * size - 8;
                int y = (int) (height / 2.0 - 2.0 * size);
                catalogButtons.clear();
                for (BuildingType bt : BuildingTypes.getInstance()) {
                    if (bt.visible()) {
                        Button widget;
                        if (bt.hasIcon()) {
                            widget = new LegacyImageButton(
                                    row * size + x + 10, col * size + y - 10, 20, 20, bt.iconU(), bt.iconV() + 20, 20, ICON_TEXTURES, 256, 256, button -> {
                                selectBuilding(bt);
                                button.active = false;
                                catalogButtons.forEach(b -> b.active = true);
                            }, Component.translatable("buildingType." + bt.name()));
                        } else {
                            widget = new ButtonWidget(row * size + x + 10, col * size + y - 10, 20, 20, Component.empty(), button -> {
                                selectBuilding(bt);
                                button.active = false;
                                catalogButtons.forEach(b -> b.active = true);
                            }, Component.translatable("buildingType." + bt.name()));
                        }
                        catalogButtons.add(addRenderableWidget(widget));

                        row++;
                        if (row > 4) {
                            row = 0;
                            col++;
                        }
                    }
                }
                break;
            case "villagers":
                addRenderableWidget(new ButtonWidget(width / 2 - 24 - 20, height / 2 + 54, 20, 20, Component.literal("<"), b -> {
                    if (pageNumber > 0) {
                        pageNumber--;
                    }
                }));
                addRenderableWidget(new ButtonWidget(width / 2 + 24, height / 2 + 54, 20, 20, Component.literal(">"), b -> {
                    if (pageNumber < Math.max(1, Math.ceil(village.getBlueprintResidentNames().size() / 9.0)) - 1) {
                        pageNumber++;
                    }
                }));
                buttonPage = addRenderableWidget(new ButtonWidget(width / 2 - 24, height / 2 + 54, 48, 20, Component.literal("0/0)"), b -> {
                }));
                break;
            case "policies": {
                boolean privileged=minecraft.player.isCreative() && minecraft.player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
                String[] keys={"beds","graves","taxes"};
                boolean[] values={village.isIgnoreBedCapacity(),village.isGraveMourningDisabled(),village.isTaxMourningDisabled()};
                for(int policyRow=0;policyRow<keys.length;policyRow++){
                    String key=keys[policyRow];boolean next=!values[policyRow];
                    ButtonWidget toggle=addRenderableWidget(new ButtonWidget(width/2-115,height/2-55+policyRow*26,230,20,
                            Component.translatable("gui.blueprint.policy."+key).append(": ").append(values[policyRow]?"ON":"OFF"),b->{
                        village.setPolicy(key,next);
                        Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.SET_POLICY,village.getId()+":"+key+":"+next));
                        setPage("policies");
                    }));
                    toggle.active=key.equals("beds")?privileged||rank.isAtLeast(Rank.MAYOR):privileged;
                }
                addRenderableWidget(new ButtonWidget(width/2-60,height/2+45,120,20,Component.literal("Back"),b->setPage("rules")));
                break;
            }
            case "rules":
                addRenderableWidget(new ButtonWidget(width/2-100,height/2+55,200,20,Component.translatable("gui.blueprint.policies"),b->setPage("policies")));
                //taxes
                buttonTaxes = createValueChanger(width / 2, height / 2 + POSITION_TAXES + 10, 80, 20, b -> changeTaxes(b ? 0.125f : -0.125f), Component.translatable("gui.blueprint.tooltip.taxes"));
                toggleButtons(buttonTaxes, false);

                //birth threshold
                buttonBirths = createValueChanger(width / 2, height / 2 + POSITION_BIRTH + 10, 80, 20, b -> changePopulationThreshold(b ? 0.125f : -0.125f), Component.translatable("gui.blueprint.tooltip.births"));
                toggleButtons(buttonBirths, false);

                //marriage threshold
                buttonMarriage = createValueChanger(width / 2, height / 2 + POSITION_MARRIAGE + 10, 80, 20, b -> changeMarriageThreshold(b ? 0.125f : -0.125f), Component.translatable("gui.blueprint.tooltip.marriage"));
                toggleButtons(buttonMarriage, false);
                addRenderableWidget(new ButtonWidget(width / 2 - 100, height / 2 + 80, 200, 20,
                        Component.translatable("gui.blueprint.pathBlock", village.getPathBlock().getName()), b -> {
                            pathBlockPage = 0;
                            setPage("pathBlocks");
                        }));
                break;
            case "pathBlocks":
                pathBlockOptions.clear();
                pathBlockSearch = addRenderableWidget(new EditBox(font, width / 2 - 100, height / 2 - 70, 200, 20,
                        Component.translatable("gui.blueprint.searchBlocks")));
                pathBlockSearch.setHint(Component.translatable("gui.blueprint.searchBlocks"));
                pathBlockPrevious = addRenderableWidget(new ButtonWidget(width / 2 - 100, height / 2 + 72, 30, 20,
                        Component.literal("<"), b -> { pathBlockPage--; updatePathBlockOptions(); }));
                pathBlockPageLabel = addRenderableWidget(new ButtonWidget(width / 2 - 65, height / 2 + 72, 70, 20,
                        Component.literal(""), b -> {}));
                pathBlockPageLabel.active = false;
                pathBlockNext = addRenderableWidget(new ButtonWidget(width / 2 + 10, height / 2 + 72, 30, 20,
                        Component.literal(">"), b -> { pathBlockPage++; updatePathBlockOptions(); }));
                addRenderableWidget(new ButtonWidget(width / 2 + 45, height / 2 + 72, 55, 20,
                        Component.translatable("gui.blueprint.cancel"), b -> setPage("rules")));
                pathBlockSearch.setResponder(query -> { pathBlockPage = 0; updatePathBlockOptions(); });
                updatePathBlockOptions();
                break;
            case "rename":
                EditBox field = addRenderableWidget(new EditBox(font, width / 2 - 65, height / 2 - 16, 130, 20, Component.translatable("gui.blueprint.renameVillage")));
                field.setMaxLength(32);
                field.setValue(village.getName());

                addRenderableWidget(new ButtonWidget(width / 2 - 66, height / 2 + 8, 64, 20, Component.translatable("gui.blueprint.cancel"), b -> {
                    setPage("map");
                }));
                addRenderableWidget(new ButtonWidget(width / 2 + 2, height / 2 + 8, 64, 20, Component.translatable("gui.blueprint.rename"), b -> {
                    Network.sendToServer(new RenameVillageMessage(village.getId(), field.getValue()));
                    village.setName(field.getValue());
                    setPage("map");
                }));
                break;
        }
    }

    private void selectBuilding(BuildingType b) {
        selectedBuilding = b;
    }

    private void updatePathBlockOptions() {
        pathBlockOptions.forEach(this::removeWidget);
        pathBlockOptions.clear();
        String query = pathBlockSearch.getValue().toLowerCase(Locale.ROOT).trim();
        List<Block> blocks = BuiltInRegistries.BLOCK.stream()
                .filter(block -> Village.isValidPathBlock(BuiltInRegistries.BLOCK.getKey(block)))
                .filter(block -> block.getName().getString().toLowerCase(Locale.ROOT).contains(query)
                        || BuiltInRegistries.BLOCK.getKey(block).toString().contains(query))
                .sorted(Comparator.comparing((Block block) -> block.getName().getString(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString()))
                .toList();
        int pages = Math.max(1, (blocks.size() + 4) / 5);
        pathBlockPage = Math.clamp(pathBlockPage, 0, pages - 1);
        pathBlockPrevious.active = pathBlockPage > 0;
        pathBlockNext.active = pathBlockPage + 1 < pages;
        pathBlockPageLabel.setMessage(Component.literal((pathBlockPage + 1) + " / " + pages));
        for (int row = 0; row < 5 && pathBlockPage * 5 + row < blocks.size(); row++) {
            Block block = blocks.get(pathBlockPage * 5 + row);
            ButtonWidget option = new ButtonWidget(width / 2 - 100, height / 2 - 40 + row * 22, 200, 20,
                    block.getName(), b -> {
                        village.setPathBlock(BuiltInRegistries.BLOCK.getKey(block));
                        saveVillage();
                        setPage("rules");
                    }, Component.literal(BuiltInRegistries.BLOCK.getKey(block).toString()));
            pathBlockOptions.add(addRenderableWidget(option));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float offset) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        if (village != null && (page.equals("map") || page.equals("advanced"))) {
            updateMapControls();
            renderMap(context);
        }
        super.extractRenderState(context, mouseX, mouseY, offset);
        if (village != null && (page.equals("map") || page.equals("advanced"))) {
            assert minecraft != null;
            renderPlayerHeadButtonIcon(context, minecraft.player);
        }

        switch (page) {
            case "waiting" ->
                    context.centeredText(font, Component.translatable("gui.blueprint.waiting"), width / 2, height / 2, 0xffaaaaaa);
            case "empty" ->
                    context.centeredText(font, Component.translatable("gui.blueprint.empty"), width / 2, height / 2 - 20, 0xffaaaaaa);
            case "map" -> {
                renderStats(context);
                renderName(context);
            }
            case "advanced" -> {
                renderName(context);
            }
            case "rank" -> {
                renderTasks(context);
                renderStats(context);
            }
            case "war" -> context.centeredText(font, Component.translatable(village.getWarCandidates().isEmpty()
                    ? "gui.blueprint.warEmpty" : "gui.blueprint.warTitle"), width / 2 + 20, height / 2 - 98, 0xffffffff);
            case "catalog" -> renderCatalog(context);
            case "villagers" -> renderVillagers(context);
            case "rules" -> renderRules(context);
            case "pathBlocks" -> context.centeredText(font, Component.translatable("gui.blueprint.choosePathBlock"),
                    width / 2, height / 2 - 90, 0xffffffff);
        }
    }

    private void renderName(GuiGraphicsExtractor context) {
        final Matrix3x2fStack matrices = context.pose();
        //name
        matrices.pushMatrix();
        matrices.scale(2.0f, 2.0f);
        context.centeredText(font, village.getName(), width / 4, height / 4 - 48, 0xffffffff);
        matrices.popMatrix();
    }

    private void renderStats(GuiGraphicsExtractor context) {
        int x = width / 2 + (page.equals("rank") ? -70 : 105);
        int y = height / 2 - 50;

        //rank
        Component rankStr = Component.translatable(rank.getTranslationKey());
        int rankColor = rank.ordinal() == 0 ? 0xffff0000 : 0xffffff00;

        context.text(font, Component.translatable("gui.blueprint.currentRank", rankStr), x, y, rankColor);
        context.text(font, Component.translatable("gui.blueprint.reputation", String.valueOf(reputation)), x, y + 11, rank.ordinal() == 0 ? 0xffff0000 : 0xffffffff);
        context.text(font, Component.translatable("gui.blueprint.buildings", village.getBuildings().values().stream().map(Building::getStructureId).distinct().count()), x, y + 22, 0xffffffff);
        context.text(font, Component.translatable("gui.blueprint.population", village.getPopulation(), village.getMaxPopulation()), x, y + 33, 0xffffffff);
        context.text(font, Component.translatable("gui.blueprint.insurance", Component.translatable("mca.insurance." + medicalInsurance)), x, y + 44, 0xffffffff);
    }

    private void renderMap(GuiGraphicsExtractor context) {
        int centerX = width / 2;
        int centerY = height / 2 + 8;
        reconcileSelectedFloor();
        updateMapControls();
        assert minecraft != null;
        LocalPlayer player = minecraft.player;
        double playerX = player == null ? village.getCenter().getX() : player.getX();
        double playerZ = player == null ? village.getCenter().getZ() : player.getZ();
        double mapCenterX = playerCentered && player != null ? playerX : village.getCenter().getX();
        double mapCenterZ = playerCentered && player != null ? playerZ : village.getCenter().getZ();
        BlueprintMapViewport viewport = BlueprintMapViewport.create(centerX, centerY, 75, mapCenterX, mapCenterZ, getMapScale());
        BlueprintMapRenderer.RenderResult result = mapRenderer.render(
                context, viewport, mapGeometry, selectedFloorOrdinal, showTerrain, showIcons, showPlayerHead,
                player, playerX, playerZ, mouseX, mouseY);

        LinkedHashMap<Integer, BlueprintMapRenderer.HoverTarget> uniqueTargets = new LinkedHashMap<>();
        for (BlueprintMapRenderer.HoverTarget target : result.hoverTargets()) {
            uniqueTargets.putIfAbsent(target.logicalBuildingId(), target);
        }
        if (!uniqueTargets.isEmpty()) {
            BlueprintMapRenderer.HoverTarget target = uniqueTargets.values().iterator().next();
            List<Component> tooltip = tooltipFactory.tooltip(target.building(), target.floorOrdinal());
            WidgetUtils.drawTooltip(context, font, tooltip, mouseX, mouseY - getTooltipHeight(tooltip) / 2 + 12);
        }
    }

    private void renderPlayerHeadButtonIcon(GuiGraphicsExtractor context, LocalPlayer player) {
        if (playerHeadButton == null || player == null || !playerHeadButton.visible) {
            return;
        }
        int iconSize = 16;
        int x = playerHeadButton.getX() + (playerHeadButton.getWidth() - iconSize) / 2;
        int y = playerHeadButton.getY() + (playerHeadButton.getHeight() - iconSize) / 2;
        PlayerFaceExtractor.extractRenderState(context, player.getSkin(), x, y, iconSize);
        if (!showPlayerHead) {
            context.fill(x, y, x + iconSize, y + iconSize, 0x88000000);
        }
    }

    private void renderTasks(GuiGraphicsExtractor context) {
        if (rank == null) {
            return;
        }

        int y = height / 2 + 5;
        int x = width / 2 - 70;

        //tasks
        for (Task task : tasks.get(rank.promote())) {
            boolean completed = completedTasks.contains(task.getId());
            Component t = task.getTranslatable().withStyle(completed ? ChatFormatting.STRIKETHROUGH : ChatFormatting.RESET);
            context.text(font, t, x, y, completed ? 0xff88ff88 : 0xffff5555);
            y += 11;
        }
    }

    private void renderCatalog(GuiGraphicsExtractor context) {
        final Matrix3x2fStack matrices = context.pose();
        //title
        matrices.pushMatrix();
        matrices.scale(2.0f, 2.0f);
        context.centeredText(font, Component.translatable("gui.blueprint.catalogFull"), width / 4, height / 4 - 52, 0xffffffff);
        matrices.popMatrix();

        //explanation
        context.centeredText(font, Component.translatable("gui.blueprint.catalogHint").withStyle(ChatFormatting.GRAY), width / 2, height / 2 - 82, 0xffffffff);

        //building
        int x = width / 2 + 35;
        int y = height / 2 - 50;
        if (selectedBuilding != null) {
            //name
            context.text(font, Component.translatable("buildingType." + selectedBuilding.name()), x, y, selectedBuilding.getColor());
            y += 12;

            //description
            List<Component> wrap = FlowingText.wrap(Component.translatable("buildingType." + selectedBuilding.name() + ".description").withStyle(ChatFormatting.GRAY).withStyle(ChatFormatting.ITALIC), 150);
            for (Component t : wrap) {
                context.text(font, t, x, y, 0xffffffff);
                y += 10;
            }
            y += 24;

            //required blocks
            for (Map.Entry<Identifier, Integer> b : selectedBuilding.getGroups().entrySet()) {
                context.text(font, Component.literal(b.getValue() + " x ").append(getBlockName(b.getKey())), x, y, 0xffffffff);
                y += 10;
            }
        } else {
            //help
            List<Component> wrap = FlowingText.wrap(Component.translatable("gui.blueprint.buildingTypes").withStyle(ChatFormatting.GRAY).withStyle(ChatFormatting.ITALIC), 150);
            for (Component t : wrap) {
                context.text(font, t, x, y, 0xffffffff);
                y += 10;
            }
        }
    }

    private void renderVillagers(GuiGraphicsExtractor context) {
        int maxPages = (int) Math.max(1, Math.ceil(village.getBlueprintResidentNames().size() / 9.0));
        pageNumber = Math.min(pageNumber, maxPages - 1);
        buttonPage.setMessage(Component.literal((pageNumber + 1) + "/" + maxPages));

        List<Map.Entry<UUID, String>> villager = village.getBlueprintResidentNames().entrySet().stream()
                .sorted(Map.Entry.comparingByValue()).toList();

        selectedVillager = null;
        for (int i = 0; i < 9; i++) {
            int index = i + pageNumber * 9;
            if (index < villager.size()) {
                int y = height / 2 - 51 + i * 11;
                boolean hover = isMouseWithin(width / 2 - 50, y - 1, 100, 11);
                context.centeredText(font, Component.literal(villager.get(index).getValue()), width / 2, y, hover ? 0xFFD7D784 : 0xFFFFFFFF);
                if (hover) {
                    selectedVillager = villager.get(index).getKey();
                }
            } else {
                break;
            }
        }
    }

    private void renderRules(GuiGraphicsExtractor context) {
        buttonTaxes[0].setMessage(Component.literal((int) (village.getTaxes() * 100) + "%"));
        buttonMarriage[0].setMessage(Component.literal((int) (village.getMarriageThreshold() * 100) + "%"));
        buttonBirths[0].setMessage(Component.literal((int) (village.getPopulationThreshold() * 100) + "%"));

        //taxes
        context.centeredText(font, Component.translatable("gui.blueprint.taxes"), width / 2, height / 2 + POSITION_TAXES, 0xffffffff);
        if (!rank.isAtLeast(Rank.MERCHANT)) {
            context.centeredText(font, Component.translatable("gui.blueprint.rankTooLow"), width / 2, height / 2 + POSITION_TAXES + 15, 0xffffffff);
            toggleButtons(buttonTaxes, false);
        } else {
            toggleButtons(buttonTaxes, true);
        }

        //births
        context.centeredText(font, Component.translatable("gui.blueprint.birth"), width / 2, height / 2 + POSITION_BIRTH, 0xffffffff);
        if (!rank.isAtLeast(Rank.NOBLE)) {
            context.centeredText(font, Component.translatable("gui.blueprint.rankTooLow"), width / 2, height / 2 + POSITION_BIRTH + 15, 0xffffffff);
            toggleButtons(buttonBirths, false);
        } else {
            toggleButtons(buttonBirths, true);
        }

        //marriages
        context.centeredText(font, Component.translatable("gui.blueprint.marriage"), width / 2, height / 2 + POSITION_MARRIAGE, 0xffffffff);
        if (!rank.isAtLeast(Rank.MAYOR)) {
            context.centeredText(font, Component.translatable("gui.blueprint.rankTooLow"), width / 2, height / 2 + POSITION_MARRIAGE + 15, 0xffffffff);
            toggleButtons(buttonMarriage, false);
        } else {
            toggleButtons(buttonMarriage, true);
        }
    }

    private Component getBlockName(Identifier id) {
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            return BuiltInRegistries.BLOCK.get(id).orElseThrow().value().getName();
        } else {
            return Component.translatable("tag.block." + id.getNamespace() + "." + id.getPath());
        }
    }

    private void toggleButtons(ButtonWidget[] buttons, boolean active) {
        for (ButtonWidget b : buttons) {
            b.active = active;
            b.visible = active;
        }
    }

    public boolean handleFloorDropdownClick(MouseButtonEvent event) {
        if (event.button() != 0) {
            return false;
        }
        if (floorDropdownOpen) {
            for (ButtonWidget option : floorOptionButtons) {
                if (option.visible && event.x() >= option.getX()
                        && event.x() < option.getX() + option.getWidth()
                        && event.y() >= option.getY()
                        && event.y() < option.getY() + option.getHeight()) {
                    int index = floorDropdownOffset + floorOptionButtons.indexOf(option);
                    List<Integer> options = getFilteredFloors();
                    if (index < options.size()) {
                        selectFloor(options.get(index));
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (handleFloorDropdownClick(event)) {
            return true;
        }
        if (floorDropdownOpen && !isWithinFloorDropdown(event.x(), event.y())) {
            floorDropdownOpen = false;
            updateFloorControls();
        }
        if (page.equals("villagers") && selectedVillager != null) {
            assert minecraft != null;
            minecraft.setScreen(new FamilyTreeScreen(selectedVillager));
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (floorDropdownOpen && isWithinFloorDropdown(mouseX, mouseY) && scrollY != 0) {
            scrollFloorDropdown(scrollY > 0 ? -1 : 1);
            return true;
        }
        if ((page.equals("map") || page.equals("advanced"))
                && mouseX >= width / 2.0 - 75 && mouseX <= width / 2.0 + 75
                && mouseY >= height / 2.0 + 8 - 75 && mouseY <= height / 2.0 + 8 + 75
                && scrollY != 0) {
            cycleMapScale(scrollY > 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean isWithinFloorDropdown(double x, double y) {
        if (floorOptionButtons.isEmpty()) {
            return false;
        }
        double left = floorButton.getX();
        double right = left + 98;
        double bottom = floorButton.getY() + floorButton.getHeight();
        int visibleRows = floorOptionButtons.size();
        double top = floorButton.getY() - visibleRows * 20.0 - 22;
        return x >= left && x < right && y >= top && y < bottom;
    }

    protected boolean isMouseWithin(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    public void setVillage(Village village) {
        if (village != null) {
            village.normalizeFloorNumbers();
        }
        this.village = village;
        roomTypeResolver = BlueprintRoomTypeResolver.create(village);
        mapGeometry = BlueprintMapGeometry.build(village, roomTypeResolver);
        tooltipFactory = BlueprintTooltipFactory.create(village, roomTypeResolver);
        floorOrdinals = mapGeometry.floorOrdinals();
        reconcileSelectedFloor();
        RoomScanPlan scanPlan = getPlayerRoomScanPlan();
        if (selectPlayerFloorOnNextVillageResponse && scanPlan.mode() == Village.RoomScanMode.UPDATE_ROOM) {
            scanPlan.functionalRoom().ifPresent(room -> {
                selectedFloorOrdinal = room.getFloorNumber();
                rememberedFloorOrdinal = selectedFloorOrdinal;
            });
        }
        selectPlayerFloorOnNextVillageResponse = false;
        if (village == null) {
            setPage("empty");
        } else if (page.equals("waiting")) {
            setPage("map");
        } else if (page.equals("map") || page.equals("advanced") || page.equals("war")) {
            setPage(page);
        }
    }

    public void setVillageData(Rank rank, int reputation, boolean isVillage, Set<String> completedTasks, Map<Rank, List<Task>> tasks) {
        this.rank = rank;
        this.reputation = reputation;
        this.isVillage = isVillage;
        this.completedTasks = completedTasks;
        this.tasks = tasks;
    }
}
