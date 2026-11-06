package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.server.world.data.Village;
import com.ancientkira.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

public final class UnderwaterVillageSupport {
    public static final ResourceKey<Structure> UNDERWATER_VILLAGE_KEY =
            ResourceKey.create(Registries.STRUCTURE, com.ancientkira.mca.MCA.locate("underwater_village"));
    public static final int MARGIN = 64;

    public static final Identifier WATER_MOVEMENT = com.ancientkira.mca.MCA.locate("underwater_village_movement");
    private static final java.util.Map<LivingEntity,Long> MOVEMENT_EXPIRY = new java.util.WeakHashMap<>();
    private UnderwaterVillageSupport() {}

    /** Vanilla raid placement rejects water and searches the ocean surface instead of the seabed. */
    public static BlockPos findRaidSpawn(ServerLevel world, BlockPos center, int maxTries) {
        double start = world.getRandom().nextDouble() * Math.PI * 2;
        for (int i = 0; i < Math.max(16, maxTries); i++) {
            double angle = start + i * Math.PI / 8;
            int radius = i < 16 ? 32 : 16;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * radius);
            if (!world.hasChunksAt(x - 10, z - 10, x + 10, z + 10)) continue;
            for (int dy = 16; dy >= -24; dy--) {
                BlockPos pos = new BlockPos(x, center.getY() + dy, z);
                if (!world.isPositionEntityTicking(pos) || !world.isWaterAt(pos)
                        || !world.isWaterAt(pos.above()) || !world.isWaterAt(pos.above(2))) continue;
                if (!world.getBlockState(pos.below()).isFaceSturdy(world, pos.below(), net.minecraft.core.Direction.UP)) continue;
                // A ravager is the widest wave member; keep the entire spawn volume clear.
                if (world.noCollision(new net.minecraft.world.phys.AABB(x - 1, pos.getY(), z - 1,
                        x + 2, pos.getY() + 3, z + 2))) return pos;
            }
        }
        return null;
    }

    public static void tickMovement(LivingEntity entity) {
        if (entity.level().isClientSide()) return;
        if (entity.tickCount % 10 == 0 && entity.level() instanceof ServerLevel world) {
            net.minecraft.world.entity.raid.Raid raid = entity instanceof net.minecraft.world.entity.raid.Raider raider
                    ? raider.getCurrentRaid() : entity instanceof VillagerEntityMCA person
                    ? PillagerRaids.getRaid(person).orElse(null) : null;
            if (raid != null && !raid.isStopped() && !raid.isOver()
                    && VillageManager.get(world).findNearestVillage(raid.getCenter(), 4)
                    .filter(Village::isUnderwater).isPresent()) applyWaterBreathing(entity);
        }
        Long expiry=MOVEMENT_EXPIRY.get(entity);
        if(expiry!=null && entity.level().getGameTime()>expiry) {
            var attribute=entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.WATER_MOVEMENT_EFFICIENCY);
            if(attribute!=null)attribute.removeModifier(WATER_MOVEMENT);
            MOVEMENT_EXPIRY.remove(entity);
        }
    }


    public static boolean isAroundUnderwaterVillage(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return false;

        VillageManager manager = VillageManager.get(serverLevel);
        for (Village village : manager) {
            if (village.isUnderwater() && village.isWithinBorder(pos, MARGIN)) {
                return true;
            }
        }

        try {
            var start = serverLevel.structureManager().getStructureWithPieceAt(pos,
                    holder -> holder.is(UNDERWATER_VILLAGE_KEY));
            if (start != null && start.isValid()) {
                return true;
            }
            for (int dx = -MARGIN; dx <= MARGIN; dx += MARGIN) {
                for (int dz = -MARGIN; dz <= MARGIN; dz += MARGIN) {
                    if (dx == 0 && dz == 0) continue;
                    var s = serverLevel.structureManager().getStructureWithPieceAt(pos.offset(dx, 0, dz),
                            holder -> holder.is(UNDERWATER_VILLAGE_KEY));
                    if (s != null && s.isValid() && s.getBoundingBox().inflatedBy(MARGIN).isInside(pos)
                            && pos.getY() >= s.getBoundingBox().minY() - 4
                            && pos.getY() <= s.getBoundingBox().maxY() + 4) {
                        return true;
                    }
                }
            }
        } catch (RuntimeException ignored) {}

        return false;
    }

    public static boolean shouldCancelDrowning(LivingEntity entity, DamageSource source) {
        if (!source.is(DamageTypes.DROWN)) return false;
        if (entity instanceof Player || entity instanceof VillagerEntityMCA || entity instanceof Villager) {
            return isAroundUnderwaterVillage(entity.level(), entity.blockPosition());
        }
        return false;
    }

    public static void tickLevel(ServerLevel world) {
        if (world.getGameTime() % 10 != 0) return;

        VillageManager manager = VillageManager.get(world);
        for (Village village : manager) {
            if (!village.isUnderwater()) continue;

            var bounds = village.getBox();
            var box = new net.minecraft.world.level.levelgen.structure.BoundingBox(
                    bounds.minX() - MARGIN, bounds.minY() - 4, bounds.minZ() - MARGIN,
                    bounds.maxX() + MARGIN, bounds.maxY() + 4, bounds.maxZ() + MARGIN);

            for (Player player : world.players()) {
                if (box.isInside(player.blockPosition())) {
                    applyWaterBreathing(player);
                }
            }

            for (VillagerEntityMCA villager : world.getEntitiesOfClass(VillagerEntityMCA.class,
                    net.minecraft.world.phys.AABB.of(box), LivingEntity::isAlive)) {
                applyWaterBreathing(villager);
            }

            for (Villager villager : world.getEntitiesOfClass(Villager.class,
                    net.minecraft.world.phys.AABB.of(box), LivingEntity::isAlive)) {
                applyWaterBreathing(villager);
            }


        }

        for (Player player : world.players()) {
            if (isAroundUnderwaterVillage(world, player.blockPosition())) {
                applyWaterBreathing(player);
            }
        }
    }

    public static void applyWaterBreathing(LivingEntity entity) {
        var attribute=entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.WATER_MOVEMENT_EFFICIENCY);
        if(attribute!=null && !attribute.hasModifier(WATER_MOVEMENT)) attribute.addTransientModifier(
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(WATER_MOVEMENT,1,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        MOVEMENT_EXPIRY.put(entity,entity.level().getGameTime()+20);
        entity.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 200, 0, true, false, true));
        entity.setAirSupply(entity.getMaxAirSupply());
    }
}
