package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.navigation.TerrainSafety;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class PortalHomecoming {
    private record Route(BlockPos portal, long expires) { }
    private record Exit(BlockPos portal, long expires) { }
    private static final Map<ServerLevel, Exit> EXITS = new WeakHashMap<>();
    private static final class Progress {
        double closest = Double.MAX_VALUE;
        long since;
        Progress(long now) { since = now; }
    }
    private static final Map<VillagerEntityMCA, Progress> PROGRESS = new WeakHashMap<>();
    private static final Map<VillagerEntityMCA, Route> ROUTES = new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private PortalHomecoming() { }
    public static Optional<GlobalPos> home(VillagerEntityMCA person) {
        return person.getPortalHomeState().read("Home", GlobalPos.CODEC).or(() -> person.getResidency().getHome());
    }
    public static void rememberHome(VillagerEntityMCA person) {
        person.getResidency().getHome().ifPresent(home -> person.getPortalHomeState().store("Home", GlobalPos.CODEC, home));
    }
    public static boolean isReturning(VillagerEntityMCA person) {
        if (person.isKidnapped() || person.isNoAi() || PortalPursuit.active(person)) return false;
        if (person.level().dimension() == Level.END)
            return home(person).map(home -> home.dimension() != Level.END).orElse(true);
        return person.level().dimension() == Level.NETHER
                && home(person).filter(home -> home.dimension() != Level.NETHER).isPresent();
    }
    public static void tick(VillagerEntityMCA person) {
        if (!(person.level() instanceof ServerLevel world) || !person.isAlive()) return;
        if (home(person).isEmpty() || home(person).get().dimension() == world.dimension()) rememberHome(person);
        if (!isReturning(person)) { ROUTES.remove(person); PROGRESS.remove(person); return; }
        if (person.isSleeping()) person.stopSleeping();
        // Touching a portal during cooldown renews it. Step clear before trying again.
        if (person.isOnPortalCooldown()) {
            var box = person.getBoundingBox();
            boolean touchingPortal = false;
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                    BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
                if (world.getBlockState(pos).is(Blocks.NETHER_PORTAL) || world.getBlockState(pos).is(Blocks.END_PORTAL)) { touchingPortal = true; break; }
            }
            if (touchingPortal) {
                for (BlockPos pos : BlockPos.betweenClosed(person.blockPosition().offset(-3, -1, -3), person.blockPosition().offset(3, 1, 3))) {
                    if (!TerrainSafety.solidFloor(world, pos) || !TerrainSafety.clear(world, pos)
                            || world.getBlockState(pos).is(Blocks.NETHER_PORTAL) || world.getBlockState(pos).is(Blocks.END_PORTAL)
                            || TerrainSafety.dangerousEdge(world, pos)) continue;
                    person.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos.immutable(), .65F, 0));
                    return;
                }
            }
            person.getNavigation().stop();
            person.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            return;
        }
        if (world.dimension() == Level.END) { returnFromEnd(world,person); return; }
        Route route = ROUTES.get(person);
        if (route == null && person.tickCount % 100 != 0) return;
        if ((route == null || route.expires < world.getGameTime()) && person.tickCount % 100 == 0) {
            BlockPos portal = null;
            if (world.dimension() == Level.NETHER) {
                portal = world.getPoiManager().getInSquare(type -> type.is(PoiTypes.NETHER_PORTAL), person.blockPosition(), 128, PoiManager.Occupancy.ANY)
                        .map(record -> record.getPos()).filter(pos -> world.hasChunkAt(pos))
                        .min(Comparator.comparingDouble(pos -> person.distanceToSqr(Vec3.atCenterOf(pos)))).orElse(null);
            }
            if (portal == null) return;
            route = new Route(portal, world.getGameTime() + 100);
            ROUTES.put(person, route);
        }
        person.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(route.portal, .65F, 0));
        if (person.distanceToSqr(Vec3.atCenterOf(route.portal)) <= 4) {
            person.getMoveControl().setWantedPosition(route.portal.getX() + .5, route.portal.getY() + .1, route.portal.getZ() + .5, .65);
        }
    }
    private static BlockPos endExit(ServerLevel world) {
        Exit cached = EXITS.get(world);
        if (cached != null && world.getGameTime() < cached.expires()) return cached.portal();
        BlockPos center = new BlockPos(0,64,0);
        var fight = world.getDragonFight();
        if (fight != null) {
            center = net.minecraft.world.level.dimension.end.EnderDragonFight.CODEC
                    .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,fight).result()
                    .flatMap(tag -> tag instanceof net.minecraft.nbt.CompoundTag data
                            ? data.read("exit_portal_location",BlockPos.CODEC) : Optional.<BlockPos>empty()).orElse(center);
        }
        // The known exit is only four chunks; never scan the entire world height per villager.
        for (int dx : new int[]{-4,4}) for (int dz : new int[]{-4,4}) world.getChunkAt(center.offset(dx,0,dz));
        BlockPos found = null;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4,-8,-4),center.offset(4,8,4))) {
            if (world.getBlockState(pos).is(Blocks.END_PORTAL)) { found=pos.immutable();break; }
        }
        EXITS.put(world,new Exit(found,world.getGameTime()+200));
        return found;
    }
    private static void returnFromEnd(ServerLevel world,VillagerEntityMCA person) {
        if (Math.floorMod(person.tickCount+person.getId(),20)!=0) return;
        BlockPos portal=endExit(world);
        if (portal==null) return; // The dragon must leave an active exit; do not fabricate one.
        world.getChunkAt(portal);
        if (!world.getBlockState(portal).is(Blocks.END_PORTAL)) {EXITS.remove(world);return;}
        var progress=PROGRESS.computeIfAbsent(person,p->new Progress(world.getGameTime()));
        double distance=person.distanceToSqr(Vec3.atCenterOf(portal));
        if (distance+1 < progress.closest) {progress.closest=distance;progress.since=world.getGameTime();}
        if (distance<=9 || world.getGameTime()-progress.since>=200) {
            // Use the real portal's normal transition, including the saved-home override.
            TeleportTransition transition=((net.minecraft.world.level.block.EndPortalBlock)Blocks.END_PORTAL)
                    .getPortalDestination(world,person,portal);
            if (transition!=null) {
                person.getNavigation().stop();person.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                ROUTES.remove(person);PROGRESS.remove(person);person.teleport(transition);
            }
            return;
        }
        Route route=ROUTES.get(person);
        if (route!=null && world.getGameTime()<route.expires()) {
            person.getBrain().setMemory(MemoryModuleType.WALK_TARGET,new WalkTarget(route.portal(),.65F,0));return;
        }
        BlockPos approach=null;
        double best=Double.MAX_VALUE;
        Vec3 toward=Vec3.atCenterOf(portal).subtract(person.position());
        double length=Math.sqrt(toward.x*toward.x+toward.z*toward.z);
        BlockPos waypoint=length>24 ? BlockPos.containing(person.getX()+toward.x/length*24,portal.getY(),person.getZ()+toward.z/length*24) : portal;
        world.getChunkAt(waypoint);
        for (BlockPos pos:BlockPos.betweenClosed(waypoint.offset(-3,-4,-3),waypoint.offset(3,4,3))) {
            if(!world.hasChunkAt(pos) || !TerrainSafety.solidFloor(world,pos) || !TerrainSafety.clear(world,pos)
                    || TerrainSafety.dangerousEdge(world,pos) || world.getBlockState(pos).is(Blocks.END_PORTAL))continue;
            double score=pos.distSqr(portal);
            if(score<best){best=score;approach=pos.immutable();}
        }
        if(approach!=null) {
            ROUTES.put(person,new Route(approach,world.getGameTime()+100));
            person.getBrain().setMemory(MemoryModuleType.WALK_TARGET,new WalkTarget(approach,.65F,0));
        }
    }
    public static TeleportTransition endDestination(ServerLevel source, VillagerEntityMCA person) {
        if (source.dimension() != Level.END) return null;
        GlobalPos home = home(person).orElse(null);
        if (home == null || home.dimension() == Level.END) return null;
        ServerLevel destination = source.getServer().getLevel(home.dimension());
        if (destination == null) return null;
        destination.getChunkAt(home.pos());
        for (int distance = 0; distance <= 8; distance++)
        for (BlockPos pos : BlockPos.betweenClosed(home.pos().offset(-3, -2, -3), home.pos().offset(3, 2, 3))) {
            if (Math.abs(pos.getX() - home.pos().getX()) + Math.abs(pos.getY() - home.pos().getY())
                    + Math.abs(pos.getZ() - home.pos().getZ()) != distance) continue;
            if (!TerrainSafety.solidFloor(destination, pos) || !TerrainSafety.clear(destination, pos)
                    || TerrainSafety.dangerousEdge(destination, pos)) continue;
            return new TeleportTransition(destination, Vec3.atBottomCenterOf(pos), Vec3.ZERO, person.getYRot(), person.getXRot(),
                    TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET));
        }
        return null;
    }
    public static final class ReturnActivity extends net.minecraft.world.entity.ai.behavior.Behavior<VillagerEntityMCA> {
        public ReturnActivity() { super(com.google.common.collect.ImmutableMap.of(), 1200); }
        @Override protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA person) { return isReturning(person); }
        @Override protected boolean canStillUse(ServerLevel level, VillagerEntityMCA person, long time) { return isReturning(person); }
        @Override protected void tick(ServerLevel level, VillagerEntityMCA person, long time) { PortalHomecoming.tick(person); }
    }
}
