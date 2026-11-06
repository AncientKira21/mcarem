package com.ancientkira.mca.entity.ai;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Slime;
import java.util.Set;

public final class CivilianThreats {
    private static final Set<String> HOSTILES = Set.of("drowned", "evoker", "husk", "illusioner", "pillager",
            "ravager", "vex", "vindicator", "zoglin", "zombie", "zombie_villager");
    private CivilianThreats() {}
    public static boolean threatens(VillagerEntityMCA observer, LivingEntity target) {
        if (!target.isAlive() || target == observer || observer.hasEffect(MobEffects.BLINDNESS)
                || com.ancientkira.mca.server.GolemPolice.prisoner(target)) return false;
        if (com.ancientkira.mca.compat.HostileGolemCompat.hostile(target)) return target.distanceToSqr(observer) <= 225;
        if (target instanceof Creeper creeper) return CreeperThreat.explodingNear(creeper, observer);
        if (target instanceof Slime slime) {
            boolean magma = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getPath().equals("magma_cube");
            return (slime.getSize() > 1 || magma) && target.distanceToSqr(observer) <= 144;
        }
        if (target instanceof com.ancientkira.mca.entity.ZombieVillagerEntityMCA zombie) {
            return !zombie.isSleeping() && !com.ancientkira.mca.server.InfirmaryCare.isZombiePatient(zombie)
                    && zombie.distanceToSqr(observer) <= 64;
        }
        if (target instanceof VillagerEntityMCA person) return person.isHostile() && target.distanceToSqr(observer) <= 225;
        if (target instanceof Mob mob && mob.getTarget() == observer) return target.distanceToSqr(observer) <= 225;
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        double range = switch (id.getPath()) {
            case "pillager" -> 15;
            case "evoker", "illusioner", "ravager", "vex" -> 12;
            case "vindicator" -> 10;
            default -> 8;
        };
        return id.getNamespace().equals("minecraft") && HOSTILES.contains(id.getPath())
                && target.distanceToSqr(observer) <= range * range;
    }
}
