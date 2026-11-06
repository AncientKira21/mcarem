package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.navigation.TerrainSafety;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Prison tags persist with the vanilla entity, including while carried as a golem passenger. */
public final class GolemPolice {
    private static final String CELL = "mca_prison_cell:";
    private static final String RELEASED = "mca_prison_released_until:";
    private static final String TRANSPORT = "mca_prison_transport";
    private GolemPolice() {}
    public static boolean prisoner(Entity mob) { return mob != null && mob.entityTags().stream().anyMatch(t -> t.startsWith(CELL)); }
    public static boolean isHostileType(Entity mob) {
        if (mob == null || !mob.isAlive() || mob.isRemoved()) return false;
        if (mob instanceof IronGolem) return false;
        return mob instanceof net.minecraft.world.entity.monster.Enemy
                || mob instanceof net.minecraft.world.entity.monster.Monster
                || mob instanceof net.minecraft.world.entity.raid.Raider
                || (mob instanceof VillagerEntityMCA person && person.isHostile())
                || (mob instanceof Mob m && (m.isAggressive() || (m instanceof net.minecraft.world.entity.NeutralMob neutral && neutral.isAngry())));
    }
    public static boolean arrestable(Entity mob) {
        if (mob != null) for (String tag : List.copyOf(mob.entityTags())) if (tag.startsWith(RELEASED)) {
            try { if (mob.level().getGameTime() < Long.parseLong(tag.substring(RELEASED.length()))) return false; }
            catch (NumberFormatException ignored) { }
            mob.removeTag(tag);
        }
        return !(mob instanceof net.minecraft.world.entity.monster.warden.Warden)
                && !(mob instanceof net.minecraft.world.entity.LivingEntity living && VillageWarfare.isWarParticipant(living))
                && isHostileType(mob) && !prisoner(mob);
    }
    private static BlockPos cell(Entity mob) {
        for (String tag : mob.entityTags()) if (tag.startsWith(CELL)) {
            try { String[] parts=tag.substring(CELL.length()).split(":");return new BlockPos(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),Integer.parseInt(parts[2])); }
            catch (RuntimeException ignored) {}
        }
        return null;
    }
    private static boolean enclosed(ServerLevel world, BlockPos cell) {
        if (!world.hasChunkAt(cell) || !TerrainSafety.solidFloor(world,cell) || !TerrainSafety.clear(world,cell)) return false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            boolean blocked=false;
            for(int step=1;step<=6;step++) {
                BlockPos edge=cell.relative(direction,step);
                var low=world.getBlockState(edge);var high=world.getBlockState(edge.above());
                if (!low.getCollisionShape(world,edge).isEmpty() && !high.getCollisionShape(world,edge.above()).isEmpty()) {
                    blocked=true;break;
                }
            }
            if(!blocked)return false;
        }
        return true;
    }
    public static boolean arrest(IronGolem golem, Entity target) {
        if (!(golem.level() instanceof ServerLevel world) || GolemAllies.raider(golem) || !arrestable(target)
                || prisoner(target) || !(target instanceof Mob mob) || mob.isNoAi()
                || !golem.getPassengers().isEmpty()) return false;
        List<com.ancientkira.mca.server.world.data.Village> villages = new ArrayList<>();
        GolemAllies.home(golem).ifPresent(villages::add);
        com.ancientkira.mca.server.world.data.VillageManager.get(world)
                .findVillages(v -> v.isWithinBorder(golem.blockPosition(), 128))
                .filter(v -> villages.isEmpty() || v.getId() != villages.get(0).getId())
                .forEach(villages::add);
        if (villages.isEmpty()) return false;
        Set<BlockPos> occupied = new HashSet<>();
        for (var entity : world.getAllEntities()) { BlockPos p = cell(entity); if (p != null) occupied.add(p); }
        Set<BlockPos> cells = new HashSet<>();
        for (var v : villages) {
            for (var prison : v.getBuildingsOfType("prison").toList()) {
                for (BlockPos anchor : prison.getBlockPosStream().toList()) {
                    for (BlockPos pos : BlockPos.betweenClosed(anchor.offset(-3, -1, -3), anchor.offset(3, 1, 3))) {
                        if (prison.containsPos(pos) && !occupied.contains(pos) && enclosed(world, pos)
                                && world.noCollision(mob, mob.getBoundingBox().move(Vec3.atBottomCenterOf(pos).subtract(mob.position())))) cells.add(pos.immutable());
                    }
                }
            }
        }
        if (cells.isEmpty()) {
            for (var v : villages) {
                for (var prison : v.getBuildingsOfType("prison").toList()) {
                    for (BlockPos anchor : prison.getBlockPosStream().toList()) {
                        for (BlockPos pos : BlockPos.betweenClosed(anchor.offset(-3, -1, -3), anchor.offset(3, 1, 3))) {
                            if (prison.containsPos(pos) && !occupied.contains(pos) && world.hasChunkAt(pos)
                                    && TerrainSafety.solidFloor(world, pos) && TerrainSafety.clear(world, pos)
                                    && world.noCollision(mob, mob.getBoundingBox().move(Vec3.atBottomCenterOf(pos).subtract(mob.position())))) cells.add(pos.immutable());
                        }
                    }
                }
            }
        }
        List<BlockPos> choices = new ArrayList<>(cells);
        choices.sort(Comparator.comparingDouble(pos -> pos.distSqr(golem.blockPosition())));
        if (choices.isEmpty()) return false;
        BlockPos pos = choices.getFirst();
        if (target.isPassenger()) target.stopRiding();
        if (!mob.startRiding(golem, true, true)) return false;
        mob.addTag(CELL + pos.getX() + ":" + pos.getY() + ":" + pos.getZ());
        mob.addTag(TRANSPORT);
        mob.setPersistenceRequired();
        if (mob instanceof VillagerEntityMCA person) person.setDespawnDelay(0);
        mob.setNoAi(true); mob.setTarget(null); mob.getNavigation().stop();
        golem.setTarget(null); golem.stopBeingAngry(); return true;
    }
    public static boolean transport(IronGolem golem) {
        if (!(golem.level() instanceof ServerLevel world)) return false;
        for (var entity : List.copyOf(golem.getPassengers())) {
            if (!(entity instanceof Mob mob) || !prisoner(mob)) continue;
            if (mob instanceof net.minecraft.world.entity.monster.warden.Warden) { release(mob); continue; }
            BlockPos pos = cell(mob);
            if (pos == null) { release(mob); return true; }
            golem.setTarget(null); golem.stopBeingAngry();
            if (golem.distanceToSqr(Vec3.atCenterOf(pos)) <= 25) {
                mob.stopRiding(); mob.removeTag(TRANSPORT);
                mob.teleportTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
                mob.setDeltaMovement(Vec3.ZERO);
                mob.setNoAi(false);
                golem.getNavigation().stop();
            } else {
                if (golem.tickCount % 5 == 0 || golem.getNavigation().isDone()) {
                    golem.getNavigation().moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 1.15);
                }
            }
            return true;
        }
        return false;
    }
    public static void tickPrisoner(Mob mob) {
        if (!(mob.level() instanceof ServerLevel world) || mob.tickCount % 20 != 0 || !prisoner(mob)) return;
        if (mob instanceof net.minecraft.world.entity.monster.warden.Warden) { release(mob); return; }
        BlockPos pos = cell(mob);
        if (pos == null || (mob.entityTags().contains(TRANSPORT) && !(mob.getVehicle() instanceof IronGolem))) {
            release(mob);
            return;
        }
        if (!mob.isPassenger()) {
            mob.setDeltaMovement(Vec3.ZERO);
            mob.setTarget(null);
            if (mob.distanceToSqr(Vec3.atBottomCenterOf(pos)) > 2.25) mob.teleportTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        }
    }
    public static boolean belongsTo(Entity entity, com.ancientkira.mca.server.world.data.Village village) {
        BlockPos pos = cell(entity);
        return pos != null && village.isWithinBorder(pos, 4);
    }
    public static net.minecraft.nbt.ListTag inmates(ServerLevel world, com.ancientkira.mca.server.world.data.Village village) {
        var list = new net.minecraft.nbt.ListTag();
        for (Entity entity : world.getAllEntities()) if (entity instanceof Mob && prisoner(entity) && belongsTo(entity, village)) {
            var entry = new net.minecraft.nbt.CompoundTag();
            entry.putString("UUID", entity.getUUID().toString()); entry.putString("Name", entity.getName().getString()); list.add(entry);
        }
        return list;
    }
    public static boolean free(ServerLevel world, com.ancientkira.mca.server.world.data.Village village, UUID id) {
        if (!(world.getEntity(id) instanceof Mob mob) || !prisoner(mob) || !belongsTo(mob, village)) return false;
        BlockPos origin = cell(mob);
        for (int radius = 1; radius <= 48; radius++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
            for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3}) {
                BlockPos pos = origin.offset(dx, dy, dz);
                if (!world.hasChunkAt(pos) || !TerrainSafety.solidFloor(world, pos) || !TerrainSafety.clear(world, pos)
                        || village.getBuildingsOfType("prison").anyMatch(building -> building.containsPos(pos))) continue;
                Vec3 destination = Vec3.atBottomCenterOf(pos);
                var box = mob.getBoundingBox().move(destination.subtract(mob.position()));
                if (!world.noCollision(mob, box) || TerrainSafety.lavaNear(world, box, .25)) continue;
                release(mob); mob.getNavigation().stop(); mob.setTarget(null); mob.setAggressive(false);
                mob.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
                mob.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
                mob.setDeltaMovement(Vec3.ZERO); mob.teleportTo(destination.x, destination.y, destination.z);
                mob.addTag(RELEASED + (world.getGameTime() + 1200));
                return true;
            }
        }
        return false;
    }

    private static void release(Mob mob) {
        mob.stopRiding();mob.setNoAi(false);
        for(String tag:List.copyOf(mob.entityTags()))if(tag.startsWith(CELL)||tag.equals(TRANSPORT))mob.removeTag(tag);
    }
}
