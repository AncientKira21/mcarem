package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.MoveState;
import com.ancientkira.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import java.util.*;

/** Afternoon music uses a marked playback copy; the library disc never leaves its chest. */
public final class VillageMusic {
    private static final String BORROWED = "mcaVillageMusic";
    private record MusicOrder(BlockPos target, long expires, boolean dance, int villageId) {}
    private static final Map<VillagerEntityMCA, MusicOrder> ORDERS = new WeakHashMap<>();
    public static final class Activity extends net.minecraft.world.entity.ai.behavior.Behavior<VillagerEntityMCA> {
        public Activity() { super(com.google.common.collect.ImmutableMap.of()); }
        @Override protected boolean checkExtraStartConditions(net.minecraft.server.level.ServerLevel level, VillagerEntityMCA person) {
            MusicOrder order = ORDERS.get(person);
            return order != null && order.expires() >= level.getGameTime() && available(person);
        }
        @Override protected void start(net.minecraft.server.level.ServerLevel level, VillagerEntityMCA person, long time) { tick(level, person, time); }
        @Override protected boolean canStillUse(net.minecraft.server.level.ServerLevel level, VillagerEntityMCA person, long time) { return checkExtraStartConditions(level, person); }
        @Override protected void tick(net.minecraft.server.level.ServerLevel level, VillagerEntityMCA person, long time) {
            MusicOrder order = ORDERS.get(person);
            if (order == null) return;
            if (order.dance()) {
                person.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                holdStill(person);
            } else walk(person, order.target());
        }
    }
    private final Village village;
    private long lastDay = Long.MIN_VALUE, deadline;
    private UUID courier;
    private BlockPos chest, jukebox;
    private int slot;
    private ItemStack stoppedDisc=ItemStack.EMPTY;
    private String startFailure="no_jukebox";
    public String getStartFailure(){return startFailure;}
    private boolean collected;
    private final Map<UUID, BlockPos> danceSpots = new HashMap<>();
    public VillageMusic(Village village) { this.village = village; }
    public static ItemStack defaultDisc() {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(net.minecraft.resources.Identifier.withDefaultNamespace("music_disc_lava_chicken"))
                .map(holder -> holder.value().getDefaultInstance())
                .orElseGet(() -> net.minecraft.world.item.Items.MUSIC_DISC_CAT.getDefaultInstance());
    }

    /** Cheats may start a party without a shop, using an existing nearby jukebox. */
    public boolean start(BlockPos origin, ItemStack heldDisc) {
        var world = village.getWorld();
        if (world == null) return false;
        startFailure="no_jukebox";
        boolean storeExists=village.getBuildingsOfType("music_store").findAny().isPresent();
        ItemStack storeDisc=ItemStack.EMPTY;
        if(storeExists){
            search: for(var store:village.getBuildingsOfType("music_store").toList())
                for(var storage:store.getBlocks().values().stream().flatMap(Collection::stream).toList())
                    if(world.hasChunkAt(storage) && world.getBlockEntity(storage) instanceof Container container)
                        for(int i=0;i<container.getContainerSize();i++) if(com.ancientkira.mca.compat.DiscCordMusic.playable(container.getItem(i))){storeDisc=container.getItem(i);break search;}
        }
        for (BlockPos candidate : BlockPos.betweenClosed(origin.offset(-16,-4,-16), origin.offset(16,4,16))) {
            if (!world.hasChunkAt(candidate) || !(world.getBlockEntity(candidate) instanceof JukeboxBlockEntity box)) continue;
            if (!box.getTheItem().isEmpty() && !isPlaybackCopy(box.getTheItem())
                    && (storeExists || !com.ancientkira.mca.compat.DiscCordMusic.isDisc(box.getTheItem()))) continue;
            end();
            jukebox = candidate.immutable();
            stoppedDisc=ItemStack.EMPTY;
            ItemStack disc = !storeDisc.isEmpty() ? storeDisc : com.ancientkira.mca.compat.DiscCordMusic.playable(heldDisc) ? heldDisc
                    : defaultDisc();
            if(box.getTheItem().isEmpty())box.setTheItem(playbackCopy(disc));
            else if(com.ancientkira.mca.compat.DiscCordMusic.isDisc(box.getTheItem()))box.setTheItem(box.getTheItem());
            lastDay = Math.floorDiv(world.getOverworldClockTime(), 24000);
            return true;
        }
        return false;
    }

    public boolean end(BlockPos origin){
        if(jukebox==null || !(village.getWorld().getBlockEntity(jukebox) instanceof JukeboxBlockEntity existing)
                || !(isPlaybackCopy(existing.getTheItem()) || com.ancientkira.mca.compat.DiscCordMusic.isDisc(existing.getTheItem())))
            for(BlockPos pos:BlockPos.betweenClosed(origin.offset(-16,-4,-16),origin.offset(16,4,16)))
                if(village.getWorld().hasChunkAt(pos) && village.getWorld().getBlockEntity(pos) instanceof JukeboxBlockEntity box
                        && com.ancientkira.mca.compat.DiscCordMusic.isDisc(box.getTheItem())){jukebox=pos.immutable();break;}
        return end();
    }

    public boolean end() {
        var world = village.getWorld();
        if (world == null) return false;
        boolean active = courier != null;
        if (jukebox != null && world.hasChunkAt(jukebox) && world.getBlockEntity(jukebox) instanceof JukeboxBlockEntity box
                && (isPlaybackCopy(box.getTheItem()) || com.ancientkira.mca.compat.DiscCordMusic.isDisc(box.getTheItem()))) {
            if(com.ancientkira.mca.compat.DiscCordMusic.isDisc(box.getTheItem()))com.ancientkira.mca.compat.DiscCordMusic.stop(box);
            stoppedDisc=box.getTheItem();
            if(isPlaybackCopy(box.getTheItem()))box.setTheItem(ItemStack.EMPTY);
            active = true;
        }
        Iterator<Map.Entry<VillagerEntityMCA, MusicOrder>> iterator = ORDERS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey().level() != world || entry.getValue().villageId() != village.getId()) continue;
            VillagerEntityMCA person = entry.getKey();
            person.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            person.getNavigation().stop();
            world.broadcastEntityEvent(person, (byte)-125);
            iterator.remove();
            active = true;
        }
        courier = null;
        danceSpots.clear();
        lastDay = Math.floorDiv(world.getOverworldClockTime(), 24000);
        return active;
    }

    public static boolean isPlaybackCopy(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(BORROWED, false);
    }
    public static ItemStack playbackCopy(ItemStack original) {
        ItemStack copy = original.copyWithCount(1);
        CompoundTag tag = copy.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean(BORROWED, true);
        copy.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return copy;
    }
    private static boolean available(VillagerEntityMCA person) {
        return !InfirmaryCare.isPatient(person) && !ClericAid.isHelping(person) && !person.getHangoutState().getBooleanOr("Active", false)
                && !person.getHangoutState().contains("Work") && !person.getHangoutState().contains("ReturnHome")
                && person.isAlive() && !person.isKidnapped() && person.getAgeState() != com.ancientkira.mca.entity.ai.relationship.AgeState.BABY
                && person.getVillagerBrain().getCurrentJob() == com.ancientkira.mca.entity.ai.Chore.NONE && !com.ancientkira.mca.registry.ProfessionsMCA.isRaider(person.getProfession())
                && person.getVillagerBrain().getMoveState() == MoveState.MOVE
                && !person.getVillagerBrain().isPanicking()
                && !person.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && !person.getBrain().hasMemoryValue(MemoryModuleType.INTERACTION_TARGET);
    }
    private static void holdStill(VillagerEntityMCA person){
        person.getNavigation().stop();
        person.getMoveControl().setWantedPosition(person.getX(),person.getY(),person.getZ(),0);
        person.setSpeed(0.0F);
        person.setXxa(0.0F);
        person.setZza(0.0F);
        person.setDeltaMovement(0, person.getDeltaMovement().y, 0);
    }
    private static void walk(VillagerEntityMCA person, BlockPos target) {
        person.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(target), 0.6F, 1));
    }
    private boolean safeDanceSpot(VillagerEntityMCA person, BlockPos spot) {
        var world = village.getWorld();
        return world.hasChunkAt(spot)
                && com.ancientkira.mca.entity.ai.navigation.TerrainSafety.solidFloor(world, spot)
                && com.ancientkira.mca.entity.ai.navigation.TerrainSafety.clear(world, spot)
                && !com.ancientkira.mca.entity.ai.navigation.TerrainSafety.dangerousEdge(world, spot)
                && world.noCollision(person, person.getBoundingBox().move(
                        spot.getX()+0.5-person.getX(), spot.getY()-person.getY(), spot.getZ()+0.5-person.getZ()));
    }

    private BlockPos findDanceSpot(VillagerEntityMCA person) {
        List<BlockPos> choices = new ArrayList<>();
        for (int x=-6;x<=6;x+=2) for (int z=-6;z<=6;z+=2) {
            if (x*x+z*z < 4 || x*x+z*z > 36) continue;
            for (int dy : new int[]{0,1,-1,2,-2}) {
                BlockPos spot=jukebox.offset(x,dy,z);
                if (danceSpots.containsValue(spot) || !safeDanceSpot(person,spot)) continue;
                choices.add(spot); break;
            }
        }
        choices.sort(Comparator.comparingDouble(spot -> person.distanceToSqr(spot.getX()+0.5,spot.getY(),spot.getZ()+0.5)));
        for (BlockPos spot : choices.stream().limit(8).toList()) {
            var path=person.getNavigation().createPath(spot,1);
            if (path != null && path.canReach()) return spot;
        }
        return null;
    }

    public void tick(long now) {
        var world = village.getWorld();
        if (world == null || now % 20 != 0) return;
        if (courier != null) {
            if (!(world.getEntity(courier) instanceof VillagerEntityMCA person) || !available(person) || now > deadline) {
                courier = null; return;
            }
            if (!world.hasChunkAt(chest) || !world.hasChunkAt(jukebox)
                    || !(world.getBlockEntity(chest) instanceof Container container)
                    || !(world.getBlockEntity(jukebox) instanceof JukeboxBlockEntity box) || !box.getTheItem().isEmpty()) {
                courier = null; return;
            }
            BlockPos target = collected ? jukebox : chest;
            ORDERS.put(person, new MusicOrder(target, now + 25, false, village.getId()));
            if (person.distanceToSqr(target.getX()+0.5, target.getY()+0.5, target.getZ()+0.5) > 9) { walk(person, target); return; }
            if (!collected) { collected = true; walk(person, jukebox); return; }
            ItemStack disc = slot < container.getContainerSize() ? container.getItem(slot) : ItemStack.EMPTY;
            if (com.ancientkira.mca.compat.DiscCordMusic.playable(disc)) box.setTheItem(playbackCopy(disc));
            ORDERS.remove(person);
            person.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            person.getNavigation().stop();
            courier = null;
        }
        // Notice manually played DiscCord music too, when no store owns the music selection.
        if ((jukebox==null || !(world.getBlockEntity(jukebox) instanceof JukeboxBlockEntity current)
                || current.getTheItem().isEmpty()) && now%100==0
                && village.getBuildingsOfType("music_store").findAny().isEmpty()) {
            search: for(var center:village.getBuildingsOfType("town_center").toList())
                for(BlockPos pos:BlockPos.betweenClosed(center.getSourceBlock().offset(-8,-2,-8),center.getSourceBlock().offset(8,2,8)))
                    if(world.hasChunkAt(pos) && world.getBlockEntity(pos) instanceof JukeboxBlockEntity candidate
                            && com.ancientkira.mca.compat.DiscCordMusic.isDisc(candidate.getTheItem())
                            && candidate.getTheItem()!=stoppedDisc){jukebox=pos.immutable();break search;}
        }
        // Dance locally while the jukebox owns a village playback copy, including its cleanup delay.
        if (jukebox != null && world.hasChunkAt(jukebox) && world.getBlockEntity(jukebox) instanceof JukeboxBlockEntity box
                && (isPlaybackCopy(box.getTheItem()) || com.ancientkira.mca.compat.DiscCordMusic.isDisc(box.getTheItem()) && box.getTheItem()!=stoppedDisc)) {
            net.minecraft.world.phys.AABB searchBox = new net.minecraft.world.phys.AABB(jukebox).inflate(64);
            if (village.getBox() != null) {
                searchBox = searchBox.minmax(new net.minecraft.world.phys.AABB(
                        village.getBox().minX(), village.getBox().minY(), village.getBox().minZ(),
                        village.getBox().maxX() + 1, village.getBox().maxY() + 1, village.getBox().maxZ() + 1).inflate(16));
            }
            List<VillagerEntityMCA> guests = world.getEntitiesOfClass(VillagerEntityMCA.class,
                    searchBox,
                    person -> available(person) && (village.isWithinBorder(person)
                            || person.getResidency().getHomeVillage().map(home -> home.getId() == village.getId()).orElse(false)));
            Set<UUID> present = guests.stream().map(VillagerEntityMCA::getUUID).collect(java.util.stream.Collectors.toSet());
            danceSpots.keySet().retainAll(present);
            for (VillagerEntityMCA person : guests) {
                BlockPos spot = danceSpots.get(person.getUUID());
                if (spot == null || !safeDanceSpot(person, spot)) {
                    danceSpots.remove(person.getUUID());
                    spot = findDanceSpot(person);
                    if (spot == null) continue;
                    danceSpots.put(person.getUUID(), spot);
                }
                boolean arrived = person.distanceToSqr(spot.getX()+0.5, spot.getY(), spot.getZ()+0.5) <= 2.25;
                ORDERS.put(person, new MusicOrder(spot, now + 25, arrived, village.getId()));
                if (arrived) {
                    person.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                    holdStill(person);
                    if (now % 60 == 0) {
                        person.getVillagerBrain().modifyMoodValue(1);
                    }
                    world.broadcastEntityEvent(person, (byte)-124);
                } else walk(person, spot);
            }
            return;
        }
        danceSpots.clear();
        long day = Math.floorDiv(world.getOverworldClockTime(), 24000);
        long time = Math.floorMod(world.getOverworldClockTime(), 24000);
        if (time < 6000 || time >= 11000 || day == lastDay || now % 200 != 0) return;
        List<BlockPos> squares = village.getBuildingsOfType("town_center")
                .flatMap(b -> b.getBlocks().values().stream()).flatMap(Collection::stream).toList();
        for (BlockPos center : squares) {
            for (BlockPos candidate : BlockPos.betweenClosed(center.offset(-8,-2,-8), center.offset(8,2,8))) {
                if (!world.hasChunkAt(candidate) || !world.getBlockState(candidate).is(Blocks.JUKEBOX)
                        || !(world.getBlockEntity(candidate) instanceof JukeboxBlockEntity box) || !box.getTheItem().isEmpty()) continue;
                boolean foundDiscInStore = false;
                for (var store : village.getBuildingsOfType("music_store").toList()) {
                    for (BlockPos storage : store.getBlocks().values().stream().flatMap(Collection::stream).toList()) {
                        if (!world.hasChunkAt(storage) || !(world.getBlockEntity(storage) instanceof Container container)) continue;
                        for (int i=0;i<container.getContainerSize();i++) {
                            if (!com.ancientkira.mca.compat.DiscCordMusic.playable(container.getItem(i))) continue;
                            Optional<VillagerEntityMCA> person = village.getResidents(world).stream().filter(VillageMusic::available)
                                    .min(Comparator.comparingDouble(v -> v.distanceToSqr(storage.getX(),storage.getY(),storage.getZ())));
                            if (person.isEmpty()) return;
                            courier=person.get().getUUID(); chest=storage.immutable(); jukebox=candidate.immutable(); slot=i;
                            collected=false; deadline=now+2400; lastDay=day; walk(person.get(),chest); return;
                        }
                    }
                }
                // Afternoon dancing can start without a disc; default is Lava Chicken
                jukebox = candidate.immutable();
                stoppedDisc = ItemStack.EMPTY;
                box.setTheItem(playbackCopy(defaultDisc()));
                lastDay = day;
                return;
            }
        }
    }
}
