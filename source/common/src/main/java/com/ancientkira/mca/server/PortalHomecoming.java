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
    private static final Map<VillagerEntityMCA, Route> ROUTES = new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private PortalHomecoming() { }
    public static Optional<GlobalPos> home(VillagerEntityMCA person) {
        return person.getPortalHomeState().read("Home", GlobalPos.CODEC).or(() -> person.getResidency().getHome());
    }
    public static void rememberHome(VillagerEntityMCA person) {
        person.getResidency().getHome().ifPresent(home -> person.getPortalHomeState().store("Home", GlobalPos.CODEC, home));
    }
    public static boolean isReturning(VillagerEntityMCA person) {
        return !person.isKidnapped() && !person.isNoAi()
                && (person.level().dimension() == Level.NETHER || person.level().dimension() == Level.END)
                && home(person).filter(home -> home.dimension() != person.level().dimension()).isPresent();
    }
    public static void tick(VillagerEntityMCA person) {
        if (!(person.level() instanceof ServerLevel world) || !person.isAlive()) return;
        if (home(person).isEmpty() || home(person).get().dimension() == world.dimension()) rememberHome(person);
        if (!isReturning(person)) { ROUTES.remove(person); return; }
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
        Route route = ROUTES.get(person);
        if (route == null && person.tickCount % 100 != 0) return;
        if ((route == null || route.expires < world.getGameTime()) && person.tickCount % 100 == 0) {
            BlockPos portal = null;
            if (world.dimension() == Level.NETHER) {
                portal = world.getPoiManager().getInSquare(type -> type.is(PoiTypes.NETHER_PORTAL), person.blockPosition(), 128, PoiManager.Occupancy.ANY)
                        .map(record -> record.getPos()).filter(pos -> world.hasChunkAt(pos))
                        .min(Comparator.comparingDouble(pos -> person.distanceToSqr(Vec3.atCenterOf(pos)))).orElse(null);
            } else {
                // Vanilla's End exit fountain is centered at 0,0. Never create an exit portal.
                for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-4, world.getMinY(), -4), new BlockPos(4, world.getMaxY() - 1, 4))) {
                    if (world.hasChunkAt(pos) && world.getBlockState(pos).is(Blocks.END_PORTAL)) { portal = pos.immutable(); break; }
                }
                if (portal == null && world.getDragonFight() != null && world.getDragonFight().hasPreviouslyKilledDragon()) {
                    portal = world.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.ZERO);
                }
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
