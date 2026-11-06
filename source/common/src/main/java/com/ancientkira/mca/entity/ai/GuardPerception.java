package com.ancientkira.mca.entity.ai;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
public final class GuardPerception {
    private GuardPerception() {}
    public static boolean canLocate(VillagerEntityMCA guard,LivingEntity target) {
        return !guard.isGuard() || !guard.hasEffect(MobEffects.BLINDNESS)
                && !(target instanceof Player && (target.isInvisible() || target.hasEffect(MobEffects.INVISIBILITY)));
    }
}
