package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.MoveState;
import com.ancientkira.mca.entity.ai.MemoryModuleTypeMCA;
import com.ancientkira.mca.entity.ai.navigation.TerrainSafety;
import com.ancientkira.mca.util.WorldUtils;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.*;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Persistent, bounded portal errands. Temporary portal tickets keep AI running without nearby players. */
public final class PortalPursuit extends SavedData {
    private static final class Trip {
        UUID target;
        GlobalPos origin, entry, exit, last, targetLast;
        boolean combat, follow, returning, arrived;
        int closeEnough;
        long started, seen;
    }
    private record Candidate(UUID id, boolean combat, boolean follow, int closeEnough, GlobalPos start) {}
    private record Departure(GlobalPos portal, List<Candidate> followers) {}
    private static final Map<Entity, Departure> DEPARTURES = new WeakHashMap<>();
    private final Map<UUID, Trip> trips = new HashMap<>();
    private PortalPursuit(ServerLevel level) {}
    private PortalPursuit(ServerLevel level, CompoundTag tag) {
        for (String key : tag.keySet()) try {
            var t = tag.getCompoundOrEmpty(key); var trip = new Trip();
            trip.target = UUID.fromString(t.getStringOr("Target", ""));
            trip.origin = t.read("Origin", GlobalPos.CODEC).orElseThrow();
            trip.entry = t.read("Entry", GlobalPos.CODEC).orElseThrow();
            trip.exit = t.read("Exit", GlobalPos.CODEC).orElseThrow();
            trip.last = t.read("Last", GlobalPos.CODEC).orElseThrow();
            trip.targetLast = t.read("TargetLast", GlobalPos.CODEC).orElseThrow();
            trip.combat = t.getBooleanOr("Combat", false); trip.follow = t.getBooleanOr("Follow", false);
            trip.returning = t.getBooleanOr("Returning", false); trip.arrived = t.getBooleanOr("Arrived", false);
            trip.closeEnough = t.getIntOr("CloseEnough", 2);
            trip.started = t.getLongOr("Started", 0); trip.seen = t.getLongOr("Seen", trip.started);
            trips.put(UUID.fromString(key), trip);
        } catch (IllegalArgumentException | NoSuchElementException ignored) { }
    }
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        trips.forEach((id, p) -> {
            var t = new CompoundTag(); t.putString("Target", p.target.toString());
            t.store("Origin", GlobalPos.CODEC, p.origin); t.store("Entry", GlobalPos.CODEC, p.entry);
            t.store("Exit", GlobalPos.CODEC, p.exit); t.store("Last", GlobalPos.CODEC, p.last);
            t.store("TargetLast", GlobalPos.CODEC, p.targetLast);
            t.putBoolean("Combat", p.combat); t.putBoolean("Follow", p.follow);
            t.putBoolean("Returning", p.returning); t.putBoolean("Arrived", p.arrived);
            t.putInt("CloseEnough", p.closeEnough); t.putLong("Started", p.started); t.putLong("Seen", p.seen);
            tag.put(id.toString(), t);
        }); return tag;
    }
    private static PortalPursuit get(MinecraftServer server) {
        return WorldUtils.loadData(server.overworld(), (tag, provider) -> new PortalPursuit(server.overworld(), tag), PortalPursuit::new, "mca_portal_pursuit");
    }
    private static GlobalPos position(Entity entity) { return GlobalPos.of(entity.level().dimension(), entity.blockPosition()); }
    public static boolean active(Mob mob) {
        return mob.level() instanceof ServerLevel level && get(level.getServer()).trips.containsKey(mob.getUUID());
    }
    private static boolean eligible(Mob mob) {
        return (mob instanceof VillagerEntityMCA || mob instanceof IronGolem) && mob.isAlive() && !mob.isNoAi()
                && !mob.isPassenger() && (!(mob instanceof VillagerEntityMCA p) || !p.isKidnapped() && p.getVillagerBrain().getMoveState() != MoveState.STAY);
    }
    private static Entity attack(Mob mob) {
        if (mob.getTarget() != null) return mob.getTarget();
        var memory = mob.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET);
        return memory == null ? null : memory.orElse(null);
    }
    public static void beforeTeleport(Entity entity, TeleportTransition transition) {
        if (!(entity.level() instanceof ServerLevel source) || source == transition.newLevel()
                || !(source.dimension() == Level.NETHER && transition.newLevel().dimension() == Level.OVERWORLD
                    || source.dimension() == Level.OVERWORLD && transition.newLevel().dimension() == Level.NETHER)
                || entity.portalProcess == null) return;
        BlockPos entry = entity.portalProcess.getEntryPosition();
        if (!source.getBlockState(entry).is(Blocks.NETHER_PORTAL)) return;
        List<Candidate> followers = new ArrayList<>();
        for (Mob mob : source.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(64), PortalPursuit::eligible)) {
            if (mob == entity || active(mob)) continue;
            boolean combat = attack(mob) == entity, follow = false; int closeEnough = 2;
            var walk = mob.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET);
            boolean walking = walk != null && walk.filter(w -> w.getTarget() instanceof EntityTracker tracker && tracker.getEntity() == entity).isPresent();
            if (walking) closeEnough = walk.get().getCloseEnoughDist();
            if (mob instanceof VillagerEntityMCA person) {
                var followed = person.getBrain().getMemoryInternal(MemoryModuleTypeMCA.PLAYER_FOLLOWING);
                follow = followed != null && followed.filter(p -> p == entity).isPresent();
            }
            if (combat || walking || follow) followers.add(new Candidate(mob.getUUID(), combat, follow, closeEnough, position(mob)));
        }
        DEPARTURES.putIfAbsent(entity, new Departure(GlobalPos.of(source.dimension(), entry), followers));
    }
    public static void afterTeleport(Entity oldEntity, Entity transferred) {
        Departure departure = DEPARTURES.remove(oldEntity);
        if (transferred == null || !(transferred.level() instanceof ServerLevel level)) return;
        var data = get(level.getServer()); long now = level.getServer().overworld().getGameTime();
        Trip own = data.trips.get(transferred.getUUID());
        if (own != null) {
            own.last = position(transferred);
            // Entity-valued follow memories are not serialized by vanilla's cross-dimension clone.
            if (own.follow && !own.returning && transferred instanceof VillagerEntityMCA person) {
                var leader = level.getServer().getPlayerList().getPlayer(own.target);
                if (leader != null) person.getBrain().setMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING, leader);
            }

            if (level.dimension() != own.origin.dimension()) { own.arrived = true; own.exit = portalNear(level, transferred.blockPosition()); }
            data.setDirty();
        }
        if (departure == null) return;
        for (Candidate c : departure.followers()) {
            Trip t = new Trip(); t.target = transferred.getUUID(); t.origin = c.start(); t.last = c.start();
            t.entry = departure.portal(); t.exit = portalNear(level, transferred.blockPosition()); t.targetLast = position(transferred);
            t.combat = c.combat(); t.follow = c.follow(); t.closeEnough = c.closeEnough(); t.started = t.seen = now;
            data.trips.put(c.id(), t);
        }
        data.setDirty();
    }
    private static GlobalPos portalNear(ServerLevel level, BlockPos around) {
        BlockPos best = around; double score = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(around.offset(-4, -3, -4), around.offset(4, 3, 4))) {
            if (!level.getBlockState(p).is(Blocks.NETHER_PORTAL)) continue;
            double d = p.distSqr(around); if (d < score) { best = p.immutable(); score = d; }
        } return GlobalPos.of(level.dimension(), best);
    }
    private static Entity find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) { Entity entity = level.getEntity(id); if (entity != null) return entity; }
        return null;
    }
    private static void keepAlive(MinecraftServer server, GlobalPos pos) {
        ServerLevel level = server.getLevel(pos.dimension()); if (level == null) return;
        level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, net.minecraft.world.level.ChunkPos.containing(pos.pos()), 3);
    }
    public static boolean taskEnded(boolean dead, boolean missing, long unseenTicks, long elapsedTicks) {
        return dead || missing && unseenTicks > 100 || elapsedTicks > 6000;
    }
    public static void tick(MinecraftServer server) {
        if (server.overworld().getGameTime() % 10 != 0) return;
        var data = get(server); long now = server.overworld().getGameTime();
        for (var e : new ArrayList<>(data.trips.entrySet())) {
            Trip t = e.getValue();
            // Stale/dead travelers cannot hold ticking chunks indefinitely.
            if (now - t.started > 12000) { data.trips.remove(e.getKey()); data.setDirty(); continue; }
            keepAlive(server, t.last); keepAlive(server, t.entry); keepAlive(server, t.exit);
            if (!t.returning) keepAlive(server, t.targetLast);
            Entity found = find(server, e.getKey());
            if (!(found instanceof Mob mob)) continue;
            if (!mob.isAlive()) { data.trips.remove(e.getKey()); data.setDirty(); continue; }
            t.last = position(mob);
            if (!eligible(mob)) { t.returning = true; continue; }
            Entity target = find(server, t.target);
            if (target != null && target.isAlive()) { t.targetLast = position(target); t.seen = now; }
            if (taskEnded(target != null && !target.isAlive(), target == null, now - t.seen, now - t.started)) t.returning = true;
            if (!t.returning && t.follow && mob instanceof VillagerEntityMCA p) {
                var followed = p.getBrain().getMemoryInternal(MemoryModuleTypeMCA.PLAYER_FOLLOWING);
                if (followed == null || followed.filter(player -> player.getUUID().equals(t.target)).isEmpty()) t.returning = true;
            }
            if (!t.returning && target != null && target.level() == mob.level() && t.arrived) {
                if (t.combat && target instanceof LivingEntity living) {
                    if (!mob.canAttack(living)) t.returning = true;
                    else { mob.setTarget(living); if (mob instanceof VillagerEntityMCA) mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, living); }
                } else if (!t.follow && mob.distanceToSqr(target) <= Math.pow(Math.max(2, t.closeEnough), 2)) t.returning = true;
                if (!t.returning) move(mob, target.blockPosition(), Math.max(1, t.closeEnough));
            } else if (!t.returning && target != null && target.level() == mob.level()) t.returning = true;
            if (t.returning && mob.level().dimension() == t.origin.dimension()) {
                if (mob.isOnPortalCooldown()) stepClear(mob); else { mob.getNavigation().stop(); data.trips.remove(e.getKey()); }
            } else if (t.returning) enter(mob, t.exit);
            else if (mob.level().dimension() != t.targetLast.dimension()) enter(mob, t.entry);
            if (mob instanceof VillagerEntityMCA p && t.returning) { p.setTarget(null); p.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET); }
            data.setDirty();
        }
    }
    private static void move(Mob mob, BlockPos pos, int distance) {
        if (mob instanceof VillagerEntityMCA) mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, .8F, distance));
        else mob.getNavigation().moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 1.0);
    }
    private static void enter(Mob mob, GlobalPos gate) {
        if (!(mob.level() instanceof ServerLevel level) || level.dimension() != gate.dimension()) return;
        if (mob.isOnPortalCooldown()) { stepClear(mob); return; }
        if (!level.getBlockState(gate.pos()).is(Blocks.NETHER_PORTAL)) return;
        move(mob, gate.pos(), 0);
        if (mob.distanceToSqr(Vec3.atCenterOf(gate.pos())) < 9) {
            mob.getMoveControl().setWantedPosition(gate.pos().getX() + .5, gate.pos().getY(), gate.pos().getZ() + .5, .8);
            if (mob.getBoundingBox().inflate(.15).intersects(new net.minecraft.world.phys.AABB(gate.pos())) && mob.canUsePortal(false)) {
                var transition = ((NetherPortalBlock)Blocks.NETHER_PORTAL).getPortalDestination(level, mob, gate.pos());
                if (transition != null) { mob.setPortalCooldown(); mob.teleport(transition); }
            }
        }
    }
    private static void stepClear(Mob mob) {
        var level = mob.level();
        for (BlockPos p : BlockPos.betweenClosed(mob.blockPosition().offset(-4,-1,-4), mob.blockPosition().offset(4,1,4))) {
            var box = mob.getBoundingBox().move(Vec3.atBottomCenterOf(p).subtract(mob.position()));
            if (TerrainSafety.solidFloor(level,p) && TerrainSafety.clear(level,p) && !TerrainSafety.dangerousEdge(level,p)
                    && !level.getBlockState(p).is(Blocks.NETHER_PORTAL) && level.noCollision(mob, box)
                    && BlockPos.betweenClosedStream(box).noneMatch(b -> level.getBlockState(b).is(Blocks.NETHER_PORTAL))) {
                move(mob,p.immutable(),0); return;
            }
        }
    }
}
