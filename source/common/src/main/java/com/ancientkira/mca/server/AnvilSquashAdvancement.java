package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.registry.CriterionMCA;
import com.ancientkira.mca.registry.ProfessionsMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AnvilBlock;

public final class AnvilSquashAdvancement {
    private AnvilSquashAdvancement() {}

    public static boolean isDefender(Entity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity instanceof IronGolem) return true;
        if (entity instanceof VillagerEntityMCA person) {
            return person.isGuard() || person.getProfession() == ProfessionsMCA.GUARD || person.getProfession() == ProfessionsMCA.ARCHER;
        }
        return false;
    }

    public static boolean isHostile(Entity entity) {
        if (entity == null) return false;
        return entity instanceof Enemy
                || entity instanceof Monster
                || entity instanceof Raider
                || (entity instanceof VillagerEntityMCA person && person.isHostile());
    }

    public static boolean isAnvilSource(DamageSource source) {
        if (source.is(DamageTypes.FALLING_ANVIL)) return true;
        if (source.is(DamageTypes.FALLING_BLOCK)) {
            if (source.getDirectEntity() instanceof FallingBlockEntity falling) {
                var state = falling.getBlockState();
                return state.is(BlockTags.ANVIL) || state.getBlock() instanceof AnvilBlock;
            }
        }
        if (source.getDirectEntity() instanceof FallingBlockEntity falling) {
            var state = falling.getBlockState();
            return state.is(BlockTags.ANVIL) || state.getBlock() instanceof AnvilBlock;
        }
        Entity direct = source.getDirectEntity();
        Entity attacker = source.getEntity();
        if (direct instanceof LivingEntity living && isAnvilItem(living.getMainHandItem())) return true;
        if (attacker instanceof LivingEntity living && isAnvilItem(living.getMainHandItem())) return true;
        return false;
    }

    public static boolean isAnvilItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(ItemTags.ANVIL) || stack.is(Items.ANVIL) || stack.is(Items.CHIPPED_ANVIL) || stack.is(Items.DAMAGED_ANVIL)) return true;
        return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof AnvilBlock;
    }

    public static void checkKill(LivingEntity victim, DamageSource source) {
        if (!(victim.level() instanceof ServerLevel world)) return;
        if (!isHostile(victim)) return;
        if (!isAnvilSource(source)) return;

        Entity directAttacker = source.getEntity();
        boolean defenderInvolved = isDefender(directAttacker);

        if (!defenderInvolved) {
            if (isDefender(victim.getLastHurtByMob())) {
                defenderInvolved = true;
            }
        }

        if (!defenderInvolved && victim instanceof net.minecraft.world.entity.Mob mob) {
            if (isDefender(mob.getTarget())) {
                defenderInvolved = true;
            }
        }

        if (!defenderInvolved) {
            var nearbyDefenders = world.getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(32),
                    e -> isDefender(e) && (e.getLastHurtMob() == victim
                            || (e instanceof net.minecraft.world.entity.Mob m && m.getTarget() == victim)));
            if (!nearbyDefenders.isEmpty()) {
                defenderInvolved = true;
            }
        }

        if (defenderInvolved) {
            world.players().stream()
                    .filter(p -> p.distanceTo(victim) <= 64)
                    .forEach(p -> CriterionMCA.GENERIC_EVENT.trigger(p, "hostile_mob_squasher"));
        }
    }
}
