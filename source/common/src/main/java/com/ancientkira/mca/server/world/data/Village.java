package com.ancientkira.mca.server.world.data;

import com.ancientkira.mca.Config;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.Memories;
import com.ancientkira.mca.resources.API;
import com.ancientkira.mca.resources.BuildingTypes;
import com.ancientkira.mca.resources.data.BuildingType;
import com.ancientkira.mca.server.world.data.villageComponents.*;
import com.ancientkira.mca.util.BlockBoxExtended;
import com.ancientkira.mca.util.NbtHelper;
import com.ancientkira.mca.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.resources.Identifier;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Village implements Iterable<Building> {
    public static final int PLAYER_BORDER_MARGIN = 32;
    public static final int BORDER_MARGIN = 48;
    public static final int MERGE_MARGIN = 64;
    private static final int SAME_FLOOR_VERTICAL_DISTANCE = 2;
    private static final int MAX_FLOOR_ATTACHMENT_GAP = 12;
    private static final int MOVE_IN_COOLDOWN = 1200;
    private static final long BED_SYNC_TIME = 200;

    public final List<ItemStack> storageBuffer = new LinkedList<>();

    private final ServerLevel world;
    private final Map<Integer, Building> buildings = new HashMap<>();
    private final Map<Integer, Integer> mainRoomOverrides = new HashMap<>();
    private final int id;
    private final VillageGuardsManager villageGuardsManager = new VillageGuardsManager(this);
    private final VillageInnManager villageInnManager = new VillageInnManager(this);
    private final VillageMarriageManager villageMarriageManager = new VillageMarriageManager(this);
    private final VillageProcreationManager villageProcreationManager = new VillageProcreationManager(this);
    private final VillageTaxesManager villageTaxesManager = new VillageTaxesManager(this);

    public long lastMoveIn;
    private String name = API.getVillagePool().pickVillageName("village");
    private String chatAIPrompt = "";
    private Map<UUID, Map<UUID, Integer>> reputation = new HashMap<>();
    private int beds;
    private long lastBedSync;
    private final Set<UUID> blueprintHiddenRaiders = new HashSet<>();
    private Map<UUID, String> residentNames = new HashMap<>();
    private Map<UUID, Long> residentHomes = new HashMap<>();
    private ListTag warCandidates = new ListTag();
    private float taxes = 0;
    private float populationThreshold = 0.75f;
    private float marriageThreshold = 0.5f;
    private boolean autoScan = Config.getInstance().enableAutoScanByDefault;
    private Identifier pathBlock = Identifier.withDefaultNamespace("dirt_path");
    private BlockBoxExtended box = new BlockBoxExtended(0, 0, 0, 0, 0, 0);

    public ServerLevel getWorld() { return world; }

    public Village(int id, ServerLevel world) {
        this.id = id;

        this.world = world;
    }

    public Village(CompoundTag v, ServerLevel world) {
        id = v.getInt("id").orElse(0);
        warCandidates = v.getList("WarCandidates").map(ListTag::copy).orElseGet(ListTag::new);
        name = v.getString("name").orElse(name);
        chatAIPrompt = v.getString("chatAIPrompt").orElse("");
        taxes = v.getFloat("taxesFloat").orElse(0.0f);
        beds = v.getInt("beds").orElse(0);
        reputation = NbtHelper.toMap(v.getCompound("reputation").orElseGet(CompoundTag::new), UUID::fromString, i ->
                NbtHelper.toMap((CompoundTag) i, UUID::fromString, i2 -> i2.asInt().orElse(0))
        );
        residentNames = NbtHelper.toMap(v.getCompound("residentNames").orElseGet(CompoundTag::new), UUID::fromString, i -> i.asString().orElse(""));
        v.getList("blueprintHiddenRaiders").ifPresent(list -> list.forEach(tag -> {
            if (tag instanceof CompoundTag data) data.read("uuid", net.minecraft.core.UUIDUtil.CODEC).ifPresent(blueprintHiddenRaiders::add);
        }));
        residentHomes = NbtHelper.toMap(v.getCompound("residentHomes").orElseGet(CompoundTag::new), UUID::fromString, i -> i.asLong().orElse(0L));

        if (v.contains("populationThresholdFloat")) {
            populationThreshold = v.getFloat("populationThresholdFloat").orElse(populationThreshold);
        }
        if (v.contains("marriageThresholdFloat")) {
            marriageThreshold = v.getFloat("marriageThresholdFloat").orElse(marriageThreshold);
        }
        this.world = world;

        autoScan = v.getBoolean("autoScan").orElse(true);
        Identifier savedPathBlock = Identifier.tryParse(v.getString("pathBlock").orElse("minecraft:dirt_path"));
        if (savedPathBlock != null && isValidPathBlock(savedPathBlock)) {
            pathBlock = savedPathBlock;
        }

        ListTag b = v.getList("buildings").orElseGet(ListTag::new);
        for (int i = 0; i < b.size(); i++) {
            Building building = new Building(b.getCompound(i).orElseGet(CompoundTag::new));

            if (world == null || BuildingTypes.getInstance().getBuildingTypes().containsKey(building.getType())) {
                buildings.put(building.getId(), building);
            }
        }
        ListTag savedMainRooms = v.getList("mainRoomOverrides").orElseGet(ListTag::new);
        for (int i = 0; i < savedMainRooms.size(); i++) {
            CompoundTag entry = savedMainRooms.getCompound(i).orElseGet(CompoundTag::new);
            mainRoomOverrides.put(entry.getInt("structureId").orElse(-1), entry.getInt("roomId").orElse(-1));
        }

        if (!buildings.isEmpty()) {
            calculateDimensions();
        }
    }

    public static Optional<Village> findNearest(Entity entity) {
        return VillageManager.get((ServerLevel) entity.level()).findNearestVillage(entity);
    }

    public boolean isWithinBorder(Entity entity) {
        return isWithinBorder(entity.blockPosition(), entity instanceof Player ? PLAYER_BORDER_MARGIN : BORDER_MARGIN);
    }

    public String getChatAIPrompt() { return chatAIPrompt; }

    public Identifier getPathBlockId() { return pathBlock; }

    public Block getPathBlock() {
        return BuiltInRegistries.BLOCK.get(pathBlock).map(holder -> holder.value()).orElse(Blocks.DIRT_PATH);
    }

    public static boolean isValidPathBlock(Identifier id) {
        return BuiltInRegistries.BLOCK.get(id)
                .filter(holder -> !holder.value().defaultBlockState().isAir() && !(holder.value() instanceof LiquidBlock))
                .isPresent();
    }

    public void setPathBlock(Identifier id) {
        if (isValidPathBlock(id) && !pathBlock.equals(id)) {
            pathBlock = id;
            if (world != null) {
                markDirty();
            }
        }
    }

    public void setChatAIPrompt(String chatAIPrompt) {
        this.chatAIPrompt = chatAIPrompt;
        markDirty();
    }

    public boolean isWithinBorder(BlockPos pos, int margin) {
        return box.inflatedBy(margin).isInside(pos);
    }

    @Override
    public Iterator<Building> iterator() {
        return buildings.values().iterator();
    }

    public void removeBuilding(int id) {
        Building removed = buildings.remove(id);
        if (removed != null && Objects.equals(mainRoomOverrides.get(removed.getStructureId()), id)) {
            mainRoomOverrides.put(removed.getStructureId(), -1);
        }
        if (removed != null) {
            recalculateRoomTypes(removed.getStructureId());
        }
        if (!buildings.isEmpty()) {
            calculateDimensions();
        } else {
            box = new BlockBoxExtended(0, 0, 0, 0, 0, 0);
        }
        markDirty();
    }

    public Stream<Building> getBuildingsOfType(String type) {
        return getBuildings().values().stream().filter(b -> b.getType().equals(type));
    }

    public Optional<Building> getBuildingAt(Vec3i pos) {
        return getBuildings().values().stream().filter(b -> b.containsPos(pos)).findAny();
    }

    public Optional<Building> getFunctionalRoomAt(BlockPos pos) {
        return buildings.values().stream()
                .filter(Building::isStrictScan)
                .filter(building -> building.containsPos(pos))
                .filter(building -> Math.abs(building.getSourceBlock().getY() - pos.getY())
                        <= SAME_FLOOR_VERTICAL_DISTANCE)
                .min(Comparator.comparingLong(Village::boundsVolume));
    }

    public RoomScanPlan getRoomScanPlan(BlockPos pos) {
        Optional<Building> containingRoom = buildings.values().stream()
                .filter(Building::isStrictScan)
                .filter(building -> !building.getBuildingType().grouped())
                .filter(building -> building.containsPos(pos))
                .filter(building -> Math.abs(building.getSourceBlock().getY() - pos.getY())
                        <= SAME_FLOOR_VERTICAL_DISTANCE)
                .min(Comparator.comparingInt((Building building) ->
                                Math.abs(building.getSourceBlock().getY() - pos.getY()))
                        .thenComparingLong(Village::boundsVolume));
        if (containingRoom.isPresent()) {
            return RoomScanPlan.updateRoom(containingRoom.get(), pos);
        }

        Optional<Building> containingStructure = buildings.values().stream()
                .filter(building -> !building.isStrictScan())
                .filter(building -> !building.getBuildingType().grouped())
                .filter(building -> building.containsPos(pos))
                .filter(building -> Math.abs(building.getSourceBlock().getY() - pos.getY())
                        <= SAME_FLOOR_VERTICAL_DISTANCE)
                .min(Comparator.comparingLong(Village::boundsVolume));
        if (containingStructure.isPresent()) {
            return RoomScanPlan.addRoom(containingStructure.get(), pos);
        }

        Building attachment = buildings.values().stream()
                .filter(building -> !building.getBuildingType().grouped())
                .filter(building -> pos.getX() >= building.getPos0().getX()
                        && pos.getX() <= building.getPos1().getX()
                        && pos.getZ() >= building.getPos0().getZ()
                        && pos.getZ() <= building.getPos1().getZ())
                .filter(building -> isOutsideFloorVerticalBounds(building, pos.getY()))
                .filter(building -> verticalDistanceToFloor(building, pos.getY())
                        <= MAX_FLOOR_ATTACHMENT_GAP)
                .min(Comparator.comparingInt(building -> verticalDistanceToFloor(building, pos.getY())))
                .orElse(null);
        if (attachment != null) {
            Village.RoomScanMode mode = pos.getY() < attachment.getSourceBlock().getY()
                    ? RoomScanMode.ADD_BASEMENT : RoomScanMode.ADD_FLOOR;
            int floor = attachment.getFloorNumber() + (mode == RoomScanMode.ADD_BASEMENT ? -1 : 1);
            return RoomScanPlan.attachment(attachment.getId(), floor, mode, pos);
        }
        return RoomScanPlan.addBuilding(pos);
    }

    private static boolean isOutsideFloorVerticalBounds(Building building, int y) {
        // Stair-connected scans can span several storeys. Their bounding box
        // describes the structure, not the height of this recorded floor.
        return Math.abs(building.getSourceBlock().getY() - y) > SAME_FLOOR_VERTICAL_DISTANCE;
    }

    private static int verticalDistanceToFloor(Building building, int y) {
        return Math.abs(building.getSourceBlock().getY() - y);
    }

    public void assignAddedFloor(Building target, Building added, RoomScanMode mode) {
        added.setStructureId(target.getStructureId());
        added.setFloorNumber(target.getFloorNumber() + (mode == RoomScanMode.ADD_BASEMENT ? -1 : 1));
    }

    public boolean normalizeFloorNumbers() {
        boolean[] changed = {false};
        buildings.values().stream().map(Building::getStructureId).distinct()
                .forEach(structureId -> changed[0] |= normalizeStructureFloors(structureId));
        return changed[0];
    }

    private boolean normalizeStructureFloors(int structureId) {
        List<Building> strictRooms = new ArrayList<>(buildings.values().stream()
                .filter(Building::isStrictScan)
                .filter(building -> !building.getBuildingType().grouped())
                .filter(building -> building.getStructureId() == structureId)
                .toList());
        Building root = buildings.get(structureId);
        // Keep a ground-floor anchor even when the root is a whole-building scan.
        if (root != null && !strictRooms.contains(root)) {
            strictRooms.add(root);
        }
        strictRooms.sort(Comparator.comparingInt(building -> building.getSourceBlock().getY()));
        if (strictRooms.isEmpty()) {
            return false;
        }

        List<List<Building>> floorGroups = new ArrayList<>();
        for (Building room : strictRooms) {
            List<Building> matchingGroup = floorGroups.stream()
                    .filter(group -> group.stream().allMatch(existing ->
                            Math.abs(room.getSourceBlock().getY() - existing.getSourceBlock().getY())
                                    <= SAME_FLOOR_VERTICAL_DISTANCE))
                    .findFirst()
                    .orElse(null);
            if (matchingGroup == null) {
                matchingGroup = new ArrayList<>();
                floorGroups.add(matchingGroup);
            }
            matchingGroup.add(room);
        }
        floorGroups.sort(Comparator.comparingInt(group -> group.stream()
                .mapToInt(building -> building.getSourceBlock().getY()).min().orElse(0)));

        int groundGroup = 0;
        if (root != null) {
            int rootY = root.getSourceBlock().getY();
            groundGroup = java.util.stream.IntStream.range(0, floorGroups.size())
                    .boxed()
                    .min(Comparator.comparingInt(index -> floorGroups.get(index).stream()
                            .mapToInt(building -> Math.abs(building.getSourceBlock().getY() - rootY))
                            .min().orElse(Integer.MAX_VALUE)))
                    .orElse(0);
        }

        boolean changed = false;
        for (int i = 0; i < floorGroups.size(); i++) {
            int floorNumber = i - groundGroup;
            for (Building room : floorGroups.get(i)) {
                if (room.getFloorNumber() != floorNumber) {
                    room.setFloorNumber(floorNumber);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static int verticalOverlap(Building first, Building second) {
        int low = Math.max(first.getPos0().getY(), second.getPos0().getY());
        int high = Math.min(first.getPos1().getY(), second.getPos1().getY());
        return Math.max(0, high - low + 1);
    }

    private static int verticalDistanceToBounds(Building building, int y) {
        if (y < building.getPos0().getY()) {
            return building.getPos0().getY() - y;
        }
        if (y > building.getPos1().getY()) {
            return y - building.getPos1().getY();
        }
        return 0;
    }

    private static long boundsVolume(Building building) {
        BlockPos min = building.getPos0();
        BlockPos max = building.getPos1();
        return (long) (max.getX() - min.getX() + 1)
                * (max.getY() - min.getY() + 1)
                * (max.getZ() - min.getZ() + 1);
    }

    public Optional<Building> getMainRoom(Building room) {
        if (room == null) {
            return Optional.empty();
        }
        int structureId = room.getStructureId();
        int mainRoomId = mainRoomOverrides.getOrDefault(structureId, -1);
        if (mainRoomId >= 0) {
            Optional<Building> selected = Optional.ofNullable(buildings.get(mainRoomId))
                    .filter(candidate -> candidate.getStructureId() == structureId && candidate.isStrictScan());
            if (selected.isPresent()) {
                return selected;
            }
        }
        return buildings.values().stream()
                .filter(candidate -> candidate.getStructureId() == structureId && candidate.isStrictScan())
                .min(Comparator.comparingInt(Building::getFloorNumber).thenComparingInt(Building::getId));
    }

    public boolean isMainRoom(Building room) {
        return room != null && getMainRoom(room).map(main -> main.getId() == room.getId()).orElse(false);
    }

    public boolean isMainRoomAutomatic(Building room) {
        return room == null || mainRoomOverrides.getOrDefault(room.getStructureId(), -1) < 0;
    }

    public boolean setMainRoom(Building room) {
        if (room == null || !room.isStrictScan() || buildings.get(room.getId()) != room) {
            return false;
        }
        if (Objects.equals(mainRoomOverrides.get(room.getStructureId()), room.getId())) {
            return false;
        }
        mainRoomOverrides.put(room.getStructureId(), room.getId());
        recalculateRoomTypes(room.getStructureId());
        markDirty();
        return true;
    }

    public boolean useAutomaticMainRoom(Building room) {
        if (room == null || !room.isStrictScan() || buildings.get(room.getId()) != room
                || isMainRoomAutomatic(room)) {
            return false;
        }
        mainRoomOverrides.put(room.getStructureId(), -1);
        recalculateRoomTypes(room.getStructureId());
        markDirty();
        return true;
    }

    public void recalculateRoomTypes(int structureId) {
        List<Building> rooms = buildings.values().stream()
                .filter(Building::isStrictScan)
                .filter(room -> room.getStructureId() == structureId)
                .sorted(Comparator.comparingInt(Building::getId))
                .toList();
        if (rooms.isEmpty()) {
            return;
        }
        Building main = getMainRoom(rooms.getFirst()).orElse(null);
        for (Building room : rooms) {
            if (room.isTypeForced()) {
                continue;
            }
            if (room != main) {
                room.setType(room.getVisibleMatchingTypes().stream().findFirst().map(BuildingType::name).orElse("building"));
                continue;
            }

            Map<Identifier, LinkedHashSet<BlockPos>> combined = new HashMap<>();
            mergePoi(combined, room.getBlocks());
            if (room.isInheritanceEnabled()) {
                rooms.stream()
                        .filter(contributor -> contributor != room && contributor.isInheritanceEnabled())
                        .forEach(contributor -> mergePoi(combined, contributor.getBlocks()));
            }
            Map<Identifier, List<BlockPos>> effective = new HashMap<>();
            combined.forEach((id, positions) -> effective.put(id, List.copyOf(positions)));
            BuildingType inheritedType = matchingTypes(effective).stream()
                    .findFirst().orElse(null);
            room.setType(inheritedType == null ? "house" : inheritedType.name());
        }
        markDirty();
    }

    private static List<BuildingType> matchingTypes(Map<Identifier, List<BlockPos>> poi) {
        List<BuildingType> matchingTypes = BuildingTypes.getInstance().getBuildingTypes().values().stream()
                    .filter(type -> !type.grouped() && !type.name().equals("pillager_outpost"))
                    .filter(type -> {
                        Map<Identifier, List<BlockPos>> grouped = type.getGroups(poi);
                        return type.getGroups().entrySet().stream().allMatch(entry ->
                                grouped.getOrDefault(entry.getKey(), List.of()).size() >= entry.getValue());
                    })
                    .sorted(Comparator.comparingInt(BuildingType::priority).reversed().thenComparing(BuildingType::name))
                    .filter(type -> type.visible() || type.name().equals("house"))
                    .filter(type -> !type.name().equals("blocked") && !type.name().equals("building"))
                    .toList();
        boolean hasBigHouse = matchingTypes.stream().anyMatch(type -> type.name().equals("big_house"));
        return hasBigHouse ? matchingTypes.stream().filter(type -> !type.name().equals("house")).toList() : matchingTypes;
    }

    private static void mergePoi(Map<Identifier, LinkedHashSet<BlockPos>> destination,
                                 Map<Identifier, List<BlockPos>> source) {
        source.forEach((id, positions) -> destination.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).addAll(positions));
    }

    public List<Integer> getFloorOrdinals() {
        return buildings.values().stream()
                .filter(building -> !building.getBuildingType().grouped())
                .map(Building::getFloorNumber).distinct().sorted().toList();
    }

    public void calculateDimensions() {
        int sx = Integer.MAX_VALUE;
        int sy = Integer.MAX_VALUE;
        int sz = Integer.MAX_VALUE;
        int ex = Integer.MIN_VALUE;
        int ey = Integer.MIN_VALUE;
        int ez = Integer.MIN_VALUE;

        for (Building building : buildings.values()) {
            ex = Math.max(building.getPos1().getX(), ex);
            sx = Math.min(building.getPos0().getX(), sx);

            ey = Math.max(building.getPos1().getY(), ey);
            sy = Math.min(building.getPos0().getY(), sy);

            ez = Math.max(building.getPos1().getZ(), ez);
            sz = Math.min(building.getPos0().getZ(), sz);
        }

        box = new BlockBoxExtended(sx, sy, sz, ex, ey, ez);
    }

    public Vec3i getCenter() {
        return box.getCenter();
    }

    public BlockBoxExtended getBox() {
        return box;
    }

    public List<String> getResidents(int building) {
        return getBuilding(building).map(value -> residentHomes.entrySet().stream().filter(e -> {
            return value.containsPos(BlockPos.of(e.getValue()));
        }).map(k -> residentNames.getOrDefault(k.getKey(), "Unknown")).collect(Collectors.toList())).orElseGet(List::of);
    }

    public float getTaxes() {
        return taxes;
    }

    public void setTaxes(float taxes) {
        this.taxes = taxes;
    }

    public float getPopulationThreshold() {
        return populationThreshold;
    }

    public void setPopulationThreshold(float populationThreshold) {
        this.populationThreshold = populationThreshold;
    }

    public float getMarriageThreshold() {
        return marriageThreshold;
    }

    public void setMarriageThreshold(float marriageThreshold) {
        this.marriageThreshold = marriageThreshold;
    }

    public boolean isAutoScan() {
        return autoScan;
    }

    public void setAutoScan(boolean autoScan) {
        if (this.autoScan != autoScan) {
            this.autoScan = autoScan;
            markDirty();
        }
    }

    public void toggleAutoScan() {
        setAutoScan(!isAutoScan());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<Integer, Building> getBuildings() {
        return buildings;
    }

    public Optional<Building> getBuilding(int id) {
        return Optional.ofNullable(buildings.get(id));
    }

    public int getId() {
        return id;
    }

    public boolean hasSpace() {
        // Recruitment must see beds just placed, rather than the periodic cached count.
        updateMaxPopulation();
        return getPopulation() < getMaxPopulation();
    }

    public int getPopulation() {
        return residentNames.size();
    }

    public Stream<UUID> getResidentsUUIDs() {
        return residentNames.keySet().stream();
    }

    // verify that this bed is not blocked
    public boolean isPositionValidBed(BlockPos pos) {
        return getBuildingAt(pos).filter(b -> b.getBuildingType().noBeds()).isEmpty();
    }

    public List<VillagerEntityMCA> getResidents(ServerLevel world) {
        return getResidentsUUIDs()
                .map(world::getEntity)
                .filter(VillagerEntityMCA.class::isInstance)
                .map(VillagerEntityMCA.class::cast)
                .collect(Collectors.toList());
    }

    public void updateMaxPopulation() {
        if (world != null) {
            int previousBeds = beds;
            lastBedSync = world.getGameTime();
            Vec3i dimensions = box.getLength();
            int radius = (int) Math.sqrt(dimensions.getX() * dimensions.getX() + dimensions.getY() * dimensions.getY() + dimensions.getZ() * dimensions.getZ());
            beds = (int) world.getPoiManager().findAll(
                    registryEntry -> registryEntry.is(PoiTypes.HOME),
                    this::isPositionValidBed,
                    new BlockPos(getCenter()),
                    radius + BORDER_MARGIN,
                    PoiManager.Occupancy.ANY).count();
            if (beds != previousBeds) VillageManager.get(world).setDirty();
        }
    }

    public int getMaxPopulation() {
        if (world != null && world.getGameTime() - lastBedSync > BED_SYNC_TIME) {
            lastBedSync = world.getGameTime();
            updateMaxPopulation();
        }
        return beds;
    }

    public boolean hasStoredResource() {
        return !storageBuffer.isEmpty();
    }

    public boolean hasBuilding(String building) {
        return buildings.values().stream().anyMatch(b -> b.getType().equals(building) && b.isComplete());
    }

    public void tick(ServerLevel world, long time) {
        if (time % 20 == 0 && repairOutpostTypes()) markDirty();
        // spread performance to avoid lag spikes
        time += getId();

        boolean isTaxSeason = time % Config.getInstance().taxSeason == 0;
        boolean isVillageUpdateTime = time % MOVE_IN_COOLDOWN == 0;

        if (isTaxSeason && hasBuilding("storage")) {
            villageTaxesManager.taxes(world);
        }

        if (time % 24000 == 0) {
            cleanReputation();
        }

        if (isVillageUpdateTime && lastMoveIn + MOVE_IN_COOLDOWN < time && WorldUtils.isChunkLoaded(world, getCenter())) {
            villageGuardsManager.spawnGuards(world);
            villageInnManager.updateInn(world);
            villageMarriageManager.marry(world);
            villageProcreationManager.procreate(world);
        }
    }

    public void onEnter(ServerLevel world) {
        villageTaxesManager.deliverTaxes(world);
    }

    public void broadCastMessage(ServerLevel world, String event, VillagerEntityMCA suitor, VillagerEntityMCA mate) {
        world.players().stream().filter(p -> PlayerSaveData.get(p).getLastSeenVillageId().orElse(-2) == getId()
                                             || suitor.getVillagerBrain().getMemoriesForPlayer(p).getHearts() > Config.getInstance().heartsToBeConsideredAsFriend
                                             || mate.getVillagerBrain().getMemoriesForPlayer(p).getHearts() > Config.getInstance().heartsToBeConsideredAsFriend)
                .forEach(player -> player.sendSystemMessage(Component.translatable(event, suitor.getName(), mate.getName()), !Config.getInstance().showNotificationsAsChat));
    }

    public void broadCastMessage(ServerLevel world, String event, String targetName) {
        world.players().stream().filter(p -> PlayerSaveData.get(p).getLastSeenVillageId().orElse(-2) == getId())
                .forEach(player -> player.sendSystemMessage(Component.translatable(event, targetName), !Config.getInstance().showNotificationsAsChat));
    }

    public void markDirty() {
        VillageManager.get(world).setDirty();
    }

    // removes all villagers no longer living here
    public void cleanReputation() {
        Set<UUID> residents = getResidentsUUIDs().collect(Collectors.toSet());
        for (Map<UUID, Integer> map : reputation.values()) {
            Set<UUID> toRemove = map.keySet().stream().filter(v -> !residents.contains(v)).collect(Collectors.toSet());
            for (UUID uuid : toRemove) {
                map.remove(uuid);
            }
        }
    }

    public void setReputation(Player player, VillagerEntityMCA villager, int rep) {
        reputation.computeIfAbsent(player.getUUID(), i -> new HashMap<>()).put(villager.getUUID(), rep);
        markDirty();
    }

    public int getReputation(Player player) {
        return reputation.getOrDefault(player.getUUID(), Collections.emptyMap()).values().stream().mapToInt(i -> i).sum();
    }

    public void pushHearts(Player player, int h) {
        List<Memories> loadedMemories = new ArrayList<>();
        for (UUID uuid : residentNames.keySet()) {
            if (world.getEntity(uuid) instanceof VillagerEntityMCA villager) {
                loadedMemories.add(villager.getVillagerBrain().getMemoriesForPlayer(player));
            }
        }
        if (loadedMemories.isEmpty()) {
            return;
        }
        int splitHearts = (int) Math.ceil((double) h / loadedMemories.size());
        for (Memories memories : loadedMemories) {
            memories.modHearts(splitHearts);
        }
        markDirty();
    }

    public void pushMood(int m) {
        for (UUID uuid : residentNames.keySet()) {
            if (world.getEntity(uuid) instanceof VillagerEntityMCA villager) {
                villager.getVillagerBrain().modifyMoodValue(m);
            }
        }
        markDirty();
    }

    public CompoundTag save() {
        CompoundTag v = new CompoundTag();
        v.putInt("id", id);
        v.putString("name", name);
        v.putString("chatAIPrompt", chatAIPrompt);
        v.putFloat("taxesFloat", taxes);
        v.putInt("beds", beds);
        v.put("reputation", NbtHelper.fromMap(new CompoundTag(), reputation, UUID::toString, i ->
                NbtHelper.fromMap(new CompoundTag(), i, UUID::toString, IntTag::valueOf)
        ));
        getBlueprintResidentNames();
        v.put("blueprintHiddenRaiders", NbtHelper.fromList(blueprintHiddenRaiders, uuid -> {
            CompoundTag data = new CompoundTag();
            data.store("uuid", net.minecraft.core.UUIDUtil.CODEC, uuid);
            return data;
        }));
        v.put("residentNames", NbtHelper.fromMap(new CompoundTag(), residentNames, Object::toString, StringTag::valueOf));
        v.put("residentHomes", NbtHelper.fromMap(new CompoundTag(), residentHomes, Object::toString, LongTag::valueOf));
        v.putFloat("populationThresholdFloat", populationThreshold);
        v.putFloat("marriageThresholdFloat", marriageThreshold);
        v.put("buildings", NbtHelper.fromList(buildings.values(), Building::save));
        v.putBoolean("autoScan", autoScan);
        v.putString("pathBlock", pathBlock.toString());
        v.put("mainRoomOverrides", NbtHelper.fromList(mainRoomOverrides.entrySet(), entry -> {
            CompoundTag mainRoom = new CompoundTag();
            mainRoom.putInt("structureId", entry.getKey());
            mainRoom.putInt("roomId", entry.getValue());
            return mainRoom;
        }));
        return v;
    }

    public CompoundTag getBlueprintData() {
        updateMaxPopulation();
        CompoundTag data = save();
        if (world != null) data.put("WarCandidates", com.ancientkira.mca.server.VillageWarfare.get(world).nearby(this));
        return data;
    }

    public ListTag getWarCandidates() { return warCandidates.copy(); }

    public void absorbConqueredVillage(Village loser) {
        merge(loser);
        residentNames.putAll(loser.residentNames);
        residentHomes.putAll(loser.residentHomes);
        blueprintHiddenRaiders.addAll(loser.blueprintHiddenRaiders);
        storageBuffer.addAll(loser.storageBuffer);
        loser.reputation.forEach((player, scores) -> reputation.computeIfAbsent(player, id -> new HashMap<>()).putAll(scores));
        getCivilRegistry().ifPresent(registry -> loser.getCivilRegistry().ifPresent(registry::absorb));
        markDirty();
    }

    public void merge(Village village) {
        buildings.putAll(village.buildings);
        mainRoomOverrides.putAll(village.mainRoomOverrides);
        calculateDimensions();
        buildings.values().stream().map(Building::getStructureId).distinct()
                .forEach(this::recalculateRoomTypes);
    }

    public boolean isVillage() {
        return getBuildings().size() >= Config.getInstance().minimumBuildingsToBeConsideredAVillage;
    }

    public void updateResident(VillagerEntityMCA e) {
        residentNames.put(e.getUUID(), e.getName().getString());

        Optional<GlobalPos> home = e.getResidency().getHome();
        if (home.isPresent()) {
            residentHomes.put(e.getUUID(), home.get().pos().asLong());
        } else {
            residentHomes.remove(e.getUUID());
        }
    }

    public Map<UUID, String> getBlueprintResidentNames() {
        if (world != null) {
            blueprintHiddenRaiders.clear();
            for (UUID uuid : residentNames.keySet()) {
                var entity = world.getEntity(uuid);
                boolean raider = entity instanceof VillagerEntityMCA person
                        ? com.ancientkira.mca.registry.ProfessionsMCA.isRaider(person.getProfession())
                        : FamilyTree.get(world).getOrEmpty(uuid)
                                .map(node -> com.ancientkira.mca.registry.ProfessionsMCA.isRaider(node.getProfession())).orElse(false);
                if (raider) blueprintHiddenRaiders.add(uuid);
            }
        }
        return residentNames.entrySet().stream().filter(entry -> !blueprintHiddenRaiders.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Map<UUID, String> getResidentNames() {
        return residentNames;
    }

    public void removeResident(VillagerEntityMCA villager) {
        removeResident(villager.getUUID());
    }

    public void removeResident(UUID uuid) {
        residentNames.remove(uuid);
        residentHomes.remove(uuid);
        cleanReputation();
        markDirty();
    }

    public VillageGuardsManager getVillageGuardsManager() {
        return villageGuardsManager;
    }

    public boolean repairOutpostTypes() {
        boolean changed = false;
        for (Building building : buildings.values()) {
            if (building.getType().equals("pillager_outpost")
                    && (!building.isTypeForced() || !canRegisterOutpost(building))) {
                building.setTypeForced(false);
                if (!building.determineType()) building.setType("house");
                changed = true;
            }
        }
        return changed;
    }

    public boolean canRegisterOutpost(Building building) {
        return buildings.values().stream()
                .filter(room -> room.getStructureId() == building.getStructureId())
                .map(Building::getFloorNumber).distinct().count() >= 3;
    }

    public Optional<CivilRegistryManager> getCivilRegistry() {
        return world != null ? Optional.of(CivilRegistryManager.get(world, this)) : Optional.empty();
    }

    public enum RoomScanMode {
        ADD_BUILDING,
        ADD_ROOM,
        UPDATE_ROOM,
        ADD_FLOOR,
        ADD_BASEMENT;

        public boolean isAttachment() {
            return this == ADD_FLOOR || this == ADD_BASEMENT;
        }
    }
}
