package com.ancientkira.mca.server.world.data;

import com.ancientkira.mca.Config;
import com.ancientkira.mca.MCA;
import com.ancientkira.mca.registry.CriterionMCA;
import com.ancientkira.mca.resources.BuildingTypes;
import com.ancientkira.mca.resources.data.BuildingType;
import com.ancientkira.mca.server.ReaperSpawner;
import com.ancientkira.mca.server.SpawnQueue;
import com.ancientkira.mca.util.NbtHelper;
import com.ancientkira.mca.util.WorldUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class VillageManager extends SavedData implements Iterable<Village> {
    public final Set<BlockPos> cache = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Village> villages = new HashMap<>();
    private final List<BlockPos> buildingQueue = new LinkedList<>();
    private final Map<Long, Long> automaticScanTimes = new HashMap<>();
    private final ServerLevel world;
    private final ReaperSpawner reapers;
    private int lastBuildingId;
    private int lastVillageId;
    private int buildingCooldown = 21;

    VillageManager(ServerLevel world) {
        this.world = world;
        reapers = new ReaperSpawner(this);
    }

    VillageManager(ServerLevel world, CompoundTag nbt) {
        this.world = world;
        lastBuildingId = nbt.getInt("lastBuildingId").orElse(0);
        lastVillageId = nbt.getInt("lastVillageId").orElse(0);
        reapers = nbt.getCompound("reapers").map(reapers -> new ReaperSpawner(this, reapers)).orElseGet(() -> new ReaperSpawner(this));

        ListTag villageList = nbt.getList("villages").orElseGet(ListTag::new);
        for (int i = 0; i < villageList.size(); i++) {
            Village village = new Village(villageList.getCompound(i).orElseGet(CompoundTag::new), world);
            if (village.getBuildings().isEmpty()) {
                MCA.LOGGER.warn("Empty village detected ({}), removing...", village.getName());
                setDirty();
            } else {
                villages.put(village.getId(), village);
                if (village.normalizeFloorNumbers()) {
                    setDirty();
                }
                if (village.repairOutpostTypes()) setDirty();
            }
        }
    }

    public static VillageManager get(ServerLevel world) {
        return WorldUtils.loadData(world, (nbt, provider) -> new VillageManager(world, nbt), VillageManager::new, "mca_villages");
    }

    public ReaperSpawner getReaperSpawner() {
        return reapers;
    }

    public Optional<Village> getOrEmpty(int id) {
        return Optional.ofNullable(villages.get(id));
    }

    public Village detachForIndependence(Village origin,int territory){
        if(com.ancientkira.mca.server.VillageWarfare.get(world).isBusy(origin.getId()))return null;
        int id=territory>=0&&!villages.containsKey(territory)?territory:lastVillageId++;
        Village released=origin.detachVillage(territory,id);
        if(released==null)return null;
        villages.put(id,released);cache.clear();setDirty();
        released.getResidents(world).forEach(person->person.getResidency().remapVillage(origin.getId(),released.getId()));
        for(var person:origin.getResidents(world))if(released.isWithinBorder(person.blockPosition(),0))person.getResidency().returnAfterRelease(origin,released);
        return released;
    }

    public boolean releaseVillage(Village origin, int territory) {
        if (com.ancientkira.mca.server.VillageWarfare.get(world).isBusy(origin.getId())) return false;
        int id = territory >= 0 && !villages.containsKey(territory) ? territory : lastVillageId++;
        Village released = origin.detachVillage(territory, id);
        if (released == null) return false;
        villages.put(id, released); cache.clear(); setDirty();
        released.getResidents(world).forEach(person->person.getResidency().remapVillage(origin.getId(),released.getId()));
        for(var person:origin.getResidents(world)){
            if(!released.isWithinBorder(person.blockPosition(),0))continue;
            person.getResidency().returnAfterRelease(origin,released);
        }
        var message = net.minecraft.network.chat.Component.translatable("mca.war.released", released.getName(), origin.getName());
        origin.getCivilRegistry().ifPresent(registry -> registry.addText(message));
        released.getCivilRegistry().ifPresent(registry -> registry.addText(message));
        world.getServer().getPlayerList().broadcastSystemMessage(message, false);
        return true;
    }

    public boolean removeVillage(int id) {
        if (villages.remove(id) != null) {
            cache.clear();
            return true;
        }
        return false;
    }

    @Override
    public Iterator<Village> iterator() {
        return villages.values().iterator();
    }

    public Stream<Village> findVillages(Predicate<Village> predicate) {
        return villages.values().stream().filter(predicate);
    }

    public Optional<Village> findNearestVillage(Entity entity) {
        BlockPos p = entity.blockPosition();
        return findVillages(v -> v.isWithinBorder(entity)).min((a, b) -> (int) (a.getCenter().distSqr(p) - b.getCenter().distSqr(p)));
    }

    public Optional<Village> findNearestVillage(BlockPos p, int margin) {
        return findVillages(v -> v.isWithinBorder(p, margin)).min((a, b) -> (int) (a.getCenter().distSqr(p) - b.getCenter().distSqr(p)));
    }

    public boolean isWithinHorizontalBoundaries(BlockPos p) {
        return villages.values().stream().anyMatch(v -> v.getBox().expand(0, 1000, 0).isInside(p));
    }

    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        nbt.putInt("lastBuildingId", lastBuildingId);
        nbt.putInt("lastVillageId", lastVillageId);
        nbt.put("villages", NbtHelper.fromList(villages.values(), Village::save));
        nbt.put("reapers", reapers.writeNbt());
        return nbt;
    }

    /**
     * Updates all the villages in the world.
     */
    public void tick() {
        //keep track of where player are currently
        if (world.getOverworldClockTime() % 100 == 0) {
            world.players().forEach(player ->
                    PlayerSaveData.get(player).updateLastSeenVillage(this, player)
            );
        }

        //send bounty hunters
        if (world.getOverworldClockTime() % (Config.getInstance().bountyHunterInterval / 10) == 0 && world.getDifficulty() != Difficulty.PEACEFUL) {
            world.players().forEach(player -> {
                if (world.getRandom().nextInt(10) == 0 && !isWithinHorizontalBoundaries(player.blockPosition()) && !player.isCreative()) {
                    villages.values().stream()
                            .filter(v -> v.getPopulation() >= 3)
                            .filter(v -> v.getReputation(player) < Config.getInstance().bountyHunterHearts)
                            .min(Comparator.comparingInt(v -> v.getReputation(player)))
                            .ifPresent(buildings -> startBountyHunterWave(player, buildings));
                }
            });
        }

        long time = world.getGameTime();
        if (time % 200 == 0) mergeConnectedVillages();

        for (Village v : this) {
            v.tick(world, time);
        }

        //process a single building
        if (time % buildingCooldown == 0 && !buildingQueue.isEmpty()) {
            processBuilding(buildingQueue.removeFirst());
        }

        com.ancientkira.mca.server.AutomaticVillageConflicts.get(world).tick();
        com.ancientkira.mca.server.VillageWarfare.get(world).tick();
        reapers.tick(world);
        SpawnQueue.getInstance().tick();
        com.ancientkira.mca.server.OnlineClothingCatalog.tick(world);
    }

    private void startBountyHunterWave(ServerPlayer player, Village sender) {
        int heartsPerHunter = 100;
        int count = Math.min(15, -sender.getReputation(player) / heartsPerHunter + 2);

        if (sender.getPopulation() == 0) {
            //the village has been wiped out, lets send one last wave
            sender.cleanReputation();

            count *= 2;
        } else {
            //slightly increase your reputation
            sender.pushHearts(player, count * heartsPerHunter / 2);
        }

        //trigger advancement
        CriterionMCA.GENERIC_EVENT.trigger(player, "bounty_hunter");

        //spawn the bois
        for (int c = 0; c < count; c++) {
            if (world.getRandom().nextBoolean()) {
                spawnBountyHunter(EntityType.PILLAGER, player);
            } else {
                spawnBountyHunter(EntityType.VINDICATOR, player);
            }
        }

        //warn the player
        player.sendSystemMessage(Component.translatable(sender.getPopulation() == 0 ? "events.bountyHuntersFinal" : "events.bountyHunters", sender.getName()).withStyle(ChatFormatting.RED));

        //civil entry
        sender.getCivilRegistry().ifPresent(r -> r.addText(Component.translatable("civil_registry.bounty_hunters", player.getName())));
    }

    private <T extends AbstractIllager> void spawnBountyHunter(EntityType<T> t, ServerPlayer player) {
        AbstractIllager pillager = t.create(world, EntitySpawnReason.EVENT);
        if (pillager != null) {
            for (int attempt = 0; attempt < 32; attempt++) {
                float f = this.world.getRandom().nextFloat() * 6.2831855F;
                int x = (int) (player.getX() + Mth.cos(f) * 32.0f);
                int z = (int) (player.getZ() + Mth.sin(f) * 32.0f);
                int y = world.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                BlockPos pos = new BlockPos(x, y, z);
                if (SpawnPlacements.isSpawnPositionOk(t, world, pos)) {
                    pillager.setPos(x, y, z);
                    pillager.setTarget(player);
                    WorldUtils.spawnEntity(world, pillager, EntitySpawnReason.EVENT);
                    break;
                }
            }
        }
    }

    //adds a potential block to the processing queue
    public void reportBuilding(BlockPos pos) {
        // Coalesce reports from villagers sharing an area; retry so later edits are detected.
        long now = world.getGameTime();
        long cell = BlockPos.asLong(Math.floorDiv(pos.getX(), 4), Math.floorDiv(pos.getY(), 2), Math.floorDiv(pos.getZ(), 4));
        Long last = automaticScanTimes.get(cell);
        if (last != null && now >= last && now - last < 200) return;
        automaticScanTimes.put(cell, now);
        if (automaticScanTimes.size() > 4096) automaticScanTimes.entrySet().removeIf(entry -> now - entry.getValue() >= 200);
        cache.add(pos.immutable());

        if (!buildingQueue.contains(pos)) {
            buildingQueue.add(pos.immutable());
        }
    }

    public Building.validationResult processBuilding(BlockPos pos) {
        if (getGroupedBuildingType(pos) != null) {
            return processBuilding(pos, false, true);
        }
        Village village = findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        RoomScanPlan plan = village == null ? RoomScanPlan.addBuilding(pos) : village.getRoomScanPlan(pos);
        if (plan.mode() == Village.RoomScanMode.UPDATE_ROOM) {
            Building existing = plan.building().orElseThrow();
            BuildingScanResult scan = analyzeIndependentBuilding(existing.getSourceBlock(), true, existing, village);
            return commitRoomUpdate(scan, existing.isTypeForced() ? existing.getType() : null, existing.getId(), true);
        }
        BuildingScanResult scan = analyzeIndependentBuilding(pos, true,
                plan.mode() == Village.RoomScanMode.ADD_ROOM ? plan.building().orElse(null) : null,
                village, plan.mode() == Village.RoomScanMode.ADD_BUILDING ? 0 : plan.prospectiveFloorNumber());
        if (plan.mode() == Village.RoomScanMode.ADD_BUILDING) {
            // An upstairs door or workstation can be discovered first. Find
            // the lowest valid entrance before choosing the ground-room anchor.
            BuildingScanResult whole = analyzeIndependentBuilding(pos, false, null, village);
            if (whole.result() == Building.validationResult.SUCCESS) {
                Building shell = whole.building();
                List<BlockPos> doors = BlockPos.betweenClosedStream(shell.getPos0(), shell.getPos1())
                        .filter(door -> world.getBlockState(door).getBlock() instanceof net.minecraft.world.level.block.DoorBlock)
                        .map(BlockPos::immutable)
                        .sorted(Comparator.comparingInt(BlockPos::getY))
                        .toList();
                for (BlockPos door : doors) {
                    if (!(world.getBlockState(door).getBlock() instanceof net.minecraft.world.level.block.DoorBlock)) {
                        continue;
                    }
                    for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                        BlockPos entrance = door.relative(direction);
                        if (!world.getBlockState(entrance).isAir() || world.getBlockState(entrance.below()).isAir()) {
                            continue;
                        }
                        BuildingScanResult ground = analyzeIndependentBuilding(entrance, true, null, village);
                        if (ground.result() == Building.validationResult.SUCCESS
                                && commitRoomAddition(ground, null, Village.RoomScanMode.ADD_BUILDING, -1, true)
                                == Building.validationResult.SUCCESS) {
                            Village anchored = findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
                            // Only retry after the entrance actually establishes an
                            // attachment; offset rooms must not recursively add roots.
                            if (anchored != null && anchored.getRoomScanPlan(pos).mode() != Village.RoomScanMode.ADD_BUILDING) {
                                return processBuilding(pos);
                            }
                            return Building.validationResult.NO_DOOR;
                        }
                    }
                }
            }
        }
        return commitRoomAddition(scan, null, plan.mode(), plan.targetBuildingId(), true);
    }

    public Building.validationResult scanStructure(BlockPos pos) {
        if (getGroupedBuildingType(pos) != null) {
            return processBuilding(pos);
        }
        // Discover the connected interior without registered rooms blocking the
        // search. Room validation below still enforces enclosure.
        Building shell = new Building(pos, false);
        Building.validationResult result = shell.validateBuilding(world, Set.of());
        if (result != Building.validationResult.SUCCESS) {
            return result;
        }
        result = processBuilding(pos);
        if (result != Building.validationResult.SUCCESS) {
            return result;
        }
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> pending = new ArrayDeque<>();
        List<BlockPos> seeds = new ArrayList<>();
        pending.add(pos.immutable());
        visited.add(pos.immutable());
        while (!pending.isEmpty() && visited.size() <= Config.getInstance().maxBuildingSize) {
            BlockPos current = pending.removeFirst();
            if (world.getBlockState(current).isAir()
                    && world.getBlockState(current.above()).isAir()
                    && !world.getBlockState(current.below()).isAir()
                    && world.getBlockState(current.below()).getFluidState().isEmpty()
                    && !(world.getBlockState(current.below()).getBlock() instanceof net.minecraft.world.level.block.DoorBlock)) {
                seeds.add(current);
            }
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!shell.containsPos(next) || visited.contains(next)) {
                    continue;
                }
                var state = world.getBlockState(next);
                if (state.isAir() || !state.getFluidState().isEmpty()
                        || state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
                    visited.add(next);
                    pending.addLast(next);
                }
            }
        }
        Village village = findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        Building root = village == null ? null : village.getBuildingAt(pos).orElse(null);
        int anchorY = root == null ? pos.getY() : village.getBuilding(root.getStructureId())
                .orElse(root).getSourceBlock().getY();
        // Nearer levels establish attachments before more distant levels.
        seeds.sort(Comparator.comparingInt(seed -> Math.abs(seed.getY() - anchorY)));
        Set<BlockPos> scannedRooms = new HashSet<>();
        for (BlockPos seed : seeds) {
            if (scannedRooms.contains(seed)) {
                continue;
            }
            Village currentVillage = findNearestVillage(seed, Village.MERGE_MARGIN).orElse(null);
            RoomScanPlan plan = currentVillage == null ? RoomScanPlan.addBuilding(seed) : currentVillage.getRoomScanPlan(seed);
            if (plan.mode() != Village.RoomScanMode.UPDATE_ROOM && plan.mode() != Village.RoomScanMode.ADD_BUILDING) {
                Set<BlockPos> blocked = new HashSet<>(getBlockedSet(currentVillage));
                if (plan.mode() == Village.RoomScanMode.ADD_ROOM) {
                    plan.building().ifPresent(parent -> blocked.remove(parent.getSourceBlock()));
                }
                Building room = new Building(seed, true);
                room.setFloorNumber(plan.prospectiveFloorNumber());
                Building.validationResult roomResult = room.validateBuilding(world, blocked, scannedRooms);
                scannedRooms.add(seed);
                if (roomResult == Building.validationResult.SUCCESS) {
                    BuildingScanResult scan = new BuildingScanResult(roomResult, seed, true, room,
                            room.getVisibleMatchingTypes().stream().map(BuildingType::name).toList(), currentVillage);
                    commitRoomAddition(scan, null, plan.mode(), plan.targetBuildingId(), true);
                }
            }
        }
        return Building.validationResult.SUCCESS;
    }

    //checks weather the given block contains a grouped building block, e.g., a town bell or gravestone
    private BuildingType getGroupedBuildingType(BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
        for (BuildingType bt : BuildingTypes.getInstance()) {
            if (bt.grouped() && bt.matchesBlock(blockId)) {
                return bt;
            }
        }
        return null;
    }

    //returns the scan-source blocks of all buildings, used to check for overlaps
    public Set<BlockPos> getBlockedSet(Village village) {
        return village.getBuildings().values().stream()
                .filter(b -> !b.getBuildingType().grouped())
                .map(Building::getSourceBlock)
                .collect(Collectors.toSet());
    }

    public BuildingBlockedResult getBlockedResult(BlockPos pos) {
        Optional<Village> optionalVillage = findNearestVillage(pos, Village.MERGE_MARGIN);
        Set<BlockPos> blocked = new java.util.HashSet<>();
        Building existingBuilding = null;
        if (optionalVillage.isPresent()) {
            Village village = optionalVillage.get();
            blocked = getBlockedSet(village);
            for (Building b : village.getBuildings().values()) {
                if (b.containsPos(pos) && !b.getBuildingType().grouped()) {
                    existingBuilding = b;
                    break;
                }
            }
        }
        return new BuildingBlockedResult(blocked, existingBuilding, optionalVillage.orElse(null));
    }

    public BuildingScanResult analyzeBuilding(BlockPos pos, boolean strictScan) {
        BuildingBlockedResult blockResult = getBlockedResult(pos);
        Building building;
        if (blockResult.existingBuilding() != null) {
            building = new Building(blockResult.existingBuilding().getSourceBlock(), blockResult.existingBuilding().isStrictScan());
            building.setFloorNumber(blockResult.existingBuilding().getFloorNumber());
        } else {
            building = new Building(pos, strictScan);
        }
        Building.validationResult result = building.validateBuilding(world, blockResult.blocked());
        List<String> matchingTypes = new java.util.ArrayList<>();
        if (result == Building.validationResult.SUCCESS) {
            building.getVisibleMatchingTypes().forEach(bt -> matchingTypes.add(bt.name()));
        }
        return new BuildingScanResult(
            result,
            building.getSourceBlock(),
            building.isStrictScan(),
            building,
            matchingTypes,
            blockResult.village()
        );
    }

    public BuildingScanResult analyzeRoomAddition(BlockPos pos) {
        return analyzeRoomAddition(pos, -1);
    }

    public BuildingScanResult analyzeRoomAddition(BlockPos pos, int expectedParentId) {
        Village village = findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        RoomScanPlan plan = village == null ? RoomScanPlan.addBuilding(pos) : village.getRoomScanPlan(pos);
        Building current = plan.building().orElse(null);
        if (current != null && current.isStrictScan()) {
            return failedBuildingScan(Building.validationResult.ROOM_ALREADY_ADDED, pos, village, true);
        }
        if (expectedParentId >= 0 && (plan.mode() != Village.RoomScanMode.ADD_ROOM
                || current == null || current.getId() != expectedParentId)) {
            return failedBuildingScan(Building.validationResult.NOT_IN_BUILDING, pos, village, true);
        }
        return analyzeIndependentBuilding(pos, true, current, village);
    }

    public BuildingScanResult analyzeRoomUpdate(BlockPos pos, int expectedBuildingId) {
        Village village = findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        Building existing = village == null ? null : village.getBuilding(expectedBuildingId).orElse(null);
        if (existing == null || !existing.isStrictScan() || !existing.containsPos(pos)) {
            return failedBuildingScan(Building.validationResult.NOT_IN_BUILDING, pos, village, true);
        }
        return analyzeIndependentBuilding(pos, true, existing, village);
    }

    public BuildingScanResult analyzeAttachedRoom(BlockPos pos, Village.RoomScanMode mode, int targetBuildingId) {
        Village village = findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        Building target = village == null ? null : village.getBuilding(targetBuildingId).orElse(null);
        RoomScanPlan plan = village == null ? RoomScanPlan.addBuilding(pos) : village.getRoomScanPlan(pos);
        if (target == null || !mode.isAttachment() || plan.mode() != mode || plan.targetBuildingId() != targetBuildingId) {
            return failedBuildingScan(Building.validationResult.NOT_IN_BUILDING, pos, village, true);
        }
        return analyzeIndependentBuilding(pos, true, null, village, plan.prospectiveFloorNumber());
    }

    private BuildingScanResult analyzeIndependentBuilding(BlockPos pos, boolean strictScan, Building replace, Village village) {
        return analyzeIndependentBuilding(pos, strictScan, replace, village,
                replace == null ? 0 : replace.getFloorNumber());
    }

    private BuildingScanResult analyzeIndependentBuilding(BlockPos pos, boolean strictScan, Building replace,
                                                         Village village, int floorNumber) {
        Set<BlockPos> blocked = village == null ? Set.of() : new HashSet<>(getBlockedSet(village));
        if (replace != null) {
            blocked.remove(replace.getSourceBlock());
        }
        Building candidate = new Building(pos, strictScan);
        // The floor must be known before validation, including updates to existing floors.
        candidate.setFloorNumber(floorNumber);
        Building.validationResult result = candidate.validateBuilding(world, blocked);
        List<String> matchingTypes = result == Building.validationResult.SUCCESS
                ? candidate.getVisibleMatchingTypes().stream().map(BuildingType::name).toList()
                : List.of();
        return new BuildingScanResult(result, candidate.getSourceBlock(), strictScan, candidate, matchingTypes, village);
    }

    private static BuildingScanResult failedBuildingScan(Building.validationResult result, BlockPos source,
                                                        Village village, boolean strictScan) {
        return new BuildingScanResult(result, source, strictScan, new Building(source, strictScan), List.of(), village);
    }

    public Building.validationResult commitRoomAddition(BuildingScanResult scan, String forcedType,
                                                        Village.RoomScanMode mode, int targetBuildingId) {
        return commitRoomAddition(scan, forcedType, mode, targetBuildingId, false);
    }

    private Building.validationResult commitRoomAddition(BuildingScanResult scan, String forcedType,
                                                        Village.RoomScanMode mode, int targetBuildingId, boolean automatic) {
        if (scan.result() != Building.validationResult.SUCCESS) {
            return scan.result();
        }
        if (forcedType != null && !scan.matchesType(forcedType)) {
            return Building.validationResult.INVALID_TYPE;
        }
        if (!automatic && forcedType == null && scan.isAmbiguous()) {
            return Building.validationResult.INVALID_TYPE;
        }
        Village village = scan.village();
        if (village == null) {
            if (targetBuildingId >= 0) {
                return Building.validationResult.NOT_IN_BUILDING;
            }
            village = new Village(lastVillageId++, world);
            villages.put(village.getId(), village);
        }
        Building target = village.getBuilding(targetBuildingId).orElse(null);
        if (mode != Village.RoomScanMode.ADD_BUILDING && targetBuildingId >= 0 && target == null) {
            return Building.validationResult.NOT_IN_BUILDING;
        }
        if (mode.isAttachment()) {
            RoomScanPlan currentPlan = village.getRoomScanPlan(scan.source());
            if (target == null || currentPlan.mode() != mode
                    || currentPlan.targetBuildingId() != targetBuildingId) {
                return Building.validationResult.NOT_IN_BUILDING;
            }
        }
        Building added = scan.building();
        if (forcedType != null) {
            added.setTypeForced(true);
            added.setType(forcedType);
        } else {
            added.setTypeForced(false);
            added.determineType();
        }
        if (!added.getBuildingType().grouped()) {
            Optional<Building> duplicate = village.getBuildings().values().stream()
                    .filter(existing -> existing.isStrictScan() == added.isStrictScan() && existing.isIdentical(added))
                    .findFirst();
            if (duplicate.isPresent()) return Building.validationResult.ROOM_ALREADY_ADDED;
        }
        added.setId(lastBuildingId++);
        if (mode == Village.RoomScanMode.ADD_ROOM && target != null) {
            added.setStructureId(target.getStructureId());
            added.setFloorNumber(target.getFloorNumber());
        } else if (mode.isAttachment() && target != null) {
            village.assignAddedFloor(target, added, mode);
        } else {
            added.setStructureId(added.getId());
            added.setFloorNumber(0);
        }
        village.getBuildings().put(added.getId(), added);
        village.normalizeFloorNumbers();
        village.calculateDimensions();
        village.recalculateRoomTypes(added.getStructureId());
        mergeConnectedVillages();
        village.markDirty();
        setDirty();
        return Building.validationResult.SUCCESS;
    }

    public Building.validationResult commitRoomUpdate(BuildingScanResult scan, String forcedType, int expectedBuildingId) {
        return commitRoomUpdate(scan, forcedType, expectedBuildingId, false);
    }

    private Building.validationResult commitRoomUpdate(BuildingScanResult scan, String forcedType, int expectedBuildingId,
                                                      boolean automatic) {
        if (scan.result() != Building.validationResult.SUCCESS) {
            return scan.result();
        }
        Village village = scan.village();
        Building existing = village == null ? null : village.getBuilding(expectedBuildingId).orElse(null);
        if (existing == null || !existing.isStrictScan()) {
            return Building.validationResult.NOT_IN_BUILDING;
        }
        if (forcedType != null && !scan.matchesType(forcedType)) {
            return Building.validationResult.INVALID_TYPE;
        }
        if (!automatic && forcedType == null && !existing.isTypeForced() && scan.isAmbiguous()) {
            return Building.validationResult.INVALID_TYPE;
        }
        Building scanned = scan.building();
        if (forcedType != null) {
            scanned.setTypeForced(true);
            scanned.setType(forcedType);
        } else if (existing.isTypeForced()) {
            scanned.setTypeForced(true);
            scanned.setType(existing.getType());
        } else {
            scanned.setTypeForced(false);
            scanned.determineType();
        }
        existing.updateFrom(scanned, world.getGameTime());
        village.normalizeFloorNumbers();
        village.calculateDimensions();
        village.recalculateRoomTypes(existing.getStructureId());
        village.markDirty();
        return Building.validationResult.SUCCESS;
    }

    public BuildingEditResult removeBuilding(BlockPos pos) {
        Village village = findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        Building target = village == null ? null : village.getBuildingAt(pos).orElse(null);
        if (target == null) {
            return BuildingEditResult.NO_BUILDING;
        }
        int structureId = target.getStructureId();
        List<Integer> members = village.getBuildings().values().stream()
                .filter(building -> building.getStructureId() == structureId)
                .map(Building::getId).toList();
        members.forEach(village::removeBuilding);
        if (village.getBuildings().isEmpty()) {
            villages.remove(village.getId());
        }
        setDirty();
        return BuildingEditResult.SUCCESS;
    }

    public BuildingEditResult removeRoom(BlockPos pos) {
        Village village = findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        if (village == null) {
            return BuildingEditResult.NO_BUILDING;
        }
        Building room = village.getFunctionalRoomAt(pos).orElse(null);
        if (room == null) {
            return village.getBuildingAt(pos).isPresent() ? BuildingEditResult.NO_ROOM : BuildingEditResult.NO_BUILDING;
        }
        if (village.isMainRoom(room)) {
            return BuildingEditResult.CANNOT_REMOVE_MAIN_ROOM;
        }
        village.removeBuilding(room.getId());
        setDirty();
        return BuildingEditResult.SUCCESS;
    }

    public boolean setRoomInheritance(BlockPos pos, boolean enabled) {
        Village village = findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        Building room = village == null ? null : village.getFunctionalRoomAt(pos).orElse(null);
        if (room == null || room.isInheritanceEnabled() == enabled) {
            return false;
        }
        room.setInheritanceEnabled(enabled);
        village.recalculateRoomTypes(room.getStructureId());
        village.markDirty();
        return true;
    }

    public boolean setStructureOutpost(BlockPos pos, boolean outpost) {
        Village village = findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        Building room = village == null ? null : village.getBuildingAt(pos).orElse(null);
        if (room == null || outpost && !village.canRegisterOutpost(room)) return false;
        Building root = village.getBuilding(room.getStructureId()).orElse(room);
        root.setType(outpost ? "pillager_outpost" : "house");
        root.setTypeForced(true);
        village.markDirty();
        return true;
    }

    public boolean forceRoomType(BlockPos pos, String type) {
        if ("pillager_outpost".equals(type) || "house".equals(type)) return setStructureOutpost(pos, "pillager_outpost".equals(type));
        Village village = findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        Building building = village == null ? null : village.getBuildingAt(pos).orElse(null);
        if (building == null) {
            return false;
        }
        if (building.getType().equals(type)) {
            building.setTypeForced(false);
            building.determineType();
        } else {
            building.setTypeForced(true);
            building.setType(type);
        }
        village.markDirty();
        return true;
    }

    public BuildingEditResult toggleMainRoom(BlockPos pos) {
        Village village = findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        if (village == null) {
            return BuildingEditResult.NO_BUILDING;
        }
        Building room = village.getFunctionalRoomAt(pos).orElse(null);
        if (room == null) {
            return BuildingEditResult.NO_ROOM;
        }
        boolean changed = village.isMainRoomAutomatic(room)
                ? village.setMainRoom(room)
                : village.useAutomaticMainRoom(room);
        return changed ? BuildingEditResult.SUCCESS : BuildingEditResult.NO_ROOM;
    }

    public Building.validationResult fullScan(Village village) {
        Building.validationResult result = Building.validationResult.SUCCESS;
        for (Building building : village.getBuildings().values().stream().toList()) {
            Building.validationResult current = processBuilding(building.getSourceBlock(), true, building.isStrictScan());
            if (current != Building.validationResult.SUCCESS) {
                result = current;
            }
        }
        return result;
    }

    public Building.validationResult commitBuilding(BuildingScanResult scan, String forcedType) {
        if (scan.result() != Building.validationResult.SUCCESS) {
            return scan.result();
        }
        if (forcedType != null && !scan.matchesType(forcedType)) {
            return Building.validationResult.INVALID_TYPE;
        }
        if (forcedType == null && scan.isAmbiguous()) {
            return Building.validationResult.INVALID_TYPE;
        }
        return commitBuilding(scan.building(), scan.village(), forcedType);
    }

    private Building.validationResult commitBuilding(Building building, Village village, String forcedType) {
        Village targetVillage = village;
        if (targetVillage == null) {
            targetVillage = new Village(lastVillageId++, world);
        }
        Building existing = targetVillage.getBuildings().values().stream()
                .filter(b -> b.getSourceBlock().equals(building.getSourceBlock()))
                .findFirst().orElse(null);
        if (existing != null) {
            existing.getBlocks().clear();
            existing.getBlocks().putAll(building.getBlocks());
            existing.setLastScan(world.getGameTime());
            if (forcedType != null) {
                existing.setTypeForced(true);
                existing.setType(forcedType);
            } else if (!existing.isTypeForced()) {
                existing.determineType();
            }
            existing.validateBuilding(world, getBlockedSet(targetVillage));
        } else {
            if (forcedType != null) {
                building.setTypeForced(true);
                building.setType(forcedType);
            } else {
                building.setTypeForced(false);
                building.determineType();
            }
            BuildingBlockedResult blockResult = getBlockedResult(building.getSourceBlock());
            Building.validationResult result = building.validateBuilding(world, blockResult.blocked());
            if (result != Building.validationResult.SUCCESS) {
                return result;
            }
            if (targetVillage.getBuildings().values().stream().anyMatch(b -> b.isIdentical(building))) {
                return Building.validationResult.IDENTICAL;
            }
            villages.put(targetVillage.getId(), targetVillage);
            building.setId(lastBuildingId++);
            targetVillage.getBuildings().put(building.getId(), building);
        }
        targetVillage.calculateDimensions();
        Village finalVillage = targetVillage;
        villages.values().stream()
                .filter(v -> v != finalVillage)
                .filter(v -> v.getBox().inflatedBy(Village.MERGE_MARGIN).intersects(finalVillage.getBox()))
                .findAny()
                .ifPresent(v -> {
                    if (v.getPopulation() > finalVillage.getPopulation()) {
                        merge(v, finalVillage);
                        villages.remove(finalVillage.getId());
                    } else {
                        merge(finalVillage, v);
                        villages.remove(v.getId());
                    }
                });
        setDirty();
        return Building.validationResult.SUCCESS;
    }

    //processed a building at given position
    public Building.validationResult processBuilding(BlockPos pos, boolean enforce, boolean strictScan) {
        return processBuilding(pos, enforce, strictScan, null);
    }

    public Building.validationResult processBuilding(BlockPos pos, boolean enforce, boolean strictScan, String forcedType) {
        BuildingType groupedBuildingType = getGroupedBuildingType(pos);
        if (groupedBuildingType != null) {
            Optional<Village> optionalVillage = findNearestVillage(pos, Village.MERGE_MARGIN);
            if (optionalVillage.isPresent()) {
                Village village = optionalVillage.get();
                String name = groupedBuildingType.name();
                double range = groupedBuildingType.mergeRange() * groupedBuildingType.mergeRange();
                Optional<Building> building = village.getBuildings().values().stream()
                        .filter(b -> b.getType().equals(name))
                        .min((a, b) -> (int) (a.getCenter().distSqr(pos) - b.getCenter().distSqr(pos)))
                        .filter(b -> b.getCenter().distSqr(pos) < range);
                if (building.isPresent()) {
                    building.get().addPOI(world, pos);
                    setDirty();
                    return Building.validationResult.SUCCESS;
                }
            }
            Village village = optionalVillage.orElse(new Village(lastVillageId++, world));
            Building building = new Building(pos, strictScan);
            building.setType(groupedBuildingType.name());
            building.addPOI(world, pos);
            villages.put(village.getId(), village);
            building.setId(lastBuildingId++);
            village.getBuildings().put(building.getId(), building);
            village.calculateDimensions();
            setDirty();
            return Building.validationResult.SUCCESS;
        }
        BuildingScanResult scan = analyzeBuilding(pos, strictScan);
        if (scan.result() != Building.validationResult.SUCCESS) {
            if (enforce) {
                BuildingBlockedResult blockResult = getBlockedResult(pos);
                if (blockResult.existingBuilding() != null) {
                    Village village = blockResult.village();
                    if (village != null) {
                        village.removeBuilding(blockResult.existingBuilding().getId());
                        if (village.getBuildings().isEmpty()) {
                            villages.remove(village.getId());
                        }
                        setDirty();
                    }
                }
            }
            return scan.result();
        }
        return commitBuilding(scan, forcedType);
    }

    public void setBuildingCooldown(int buildingCooldown) {
        this.buildingCooldown = buildingCooldown;
    }

    private boolean connectedByRoad(Village a,Village b){
        if(a.getCenter().distSqr(b.getCenter())>256*256)return false;
        java.util.ArrayDeque<BlockPos> queue=new java.util.ArrayDeque<>();
        Set<BlockPos> seen=new HashSet<>();
        for(var building:a.getBuildingsOfType("town_center").toList())
            for(BlockPos pos:BlockPos.betweenClosed(building.getSourceBlock().offset(-6,-3,-6),building.getSourceBlock().offset(6,1,6)))
                if(world.hasChunkAt(pos) && world.getBlockState(pos).is(a.getPathBlock()) && seen.add(pos.immutable()))queue.add(pos.immutable());
        int visits=0;
        while(!queue.isEmpty() && visits++<4096){
            BlockPos current=queue.removeFirst();
            if(b.isWithinBorder(current.above(),4))return true;
            for(var direction:net.minecraft.core.Direction.Plane.HORIZONTAL)
                for(int dy=-1;dy<=1;dy++){
                    BlockPos next=current.relative(direction).offset(0,dy,0);
                    if(seen.contains(next)||!world.hasChunkAt(next))continue;
                    var block=world.getBlockState(next);
                    if((block.is(a.getPathBlock())||block.is(b.getPathBlock())) && seen.add(next))queue.add(next);
                }
        }
        return false;
    }

    private void mergeConnectedVillages() {
        boolean changed;
        do {
            changed = false;
            List<Village> ordered = villages.values().stream().sorted(Comparator.comparingInt(Village::getId)).toList();
            outer: for (int i = 0; i < ordered.size(); i++) {
                for (int j = i + 1; j < ordered.size(); j++) {
                    Village a = ordered.get(i), b = ordered.get(j);
                    if (a.getBuildings().isEmpty() || b.getBuildings().isEmpty()) continue;
                    if(a.isSeparateFrom(b) || com.ancientkira.mca.server.VillageWarfare.get(world).isBusy(a.getId())
                            || com.ancientkira.mca.server.VillageWarfare.get(world).isBusy(b.getId()))continue;
                    boolean horizontal=a.getBox().minX()-Village.MERGE_MARGIN<=b.getBox().maxX()
                            && a.getBox().maxX()+Village.MERGE_MARGIN>=b.getBox().minX()
                            && a.getBox().minZ()-Village.MERGE_MARGIN<=b.getBox().maxZ()
                            && a.getBox().maxZ()+Village.MERGE_MARGIN>=b.getBox().minZ();
                    if(!horizontal && !connectedByRoad(a,b))continue;
                    Village into = b.getPopulation() > a.getPopulation() ? b : a;
                    merge(into, into == a ? b : a);
                    changed = true;
                    break outer;
                }
            }
        } while (changed);
    }

    public void merge(Village into, Village from) {
        if (into == from) return;
        into.merge(from);
        villages.remove(from.getId());
        for (var entity : world.getAllEntities()) {
            if (entity instanceof com.ancientkira.mca.entity.VillagerEntityMCA person) {
                person.getResidency().remapVillage(from.getId(), into.getId());
            }
        }
        cache.clear();
        setDirty();
    }

    public enum BuildingEditResult {
        SUCCESS,
        NO_BUILDING,
        NO_ROOM,
        CANNOT_REMOVE_MAIN_ROOM
    }
}
