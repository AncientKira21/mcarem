package com.ancientkira.mca.server;

import com.ancientkira.mca.registry.EntitiesMCA;
import com.ancientkira.mca.entity.ai.relationship.AgeState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/** Entity tags keep the normal three-to-five-minute cure timer across saves. */
public final class SkeletonCuring {
    private static final String TIMER="mca_skeleton_cure:";
    private SkeletonCuring() {}
    private static String timer(Mob skeleton) {
        return skeleton.entityTags().stream().filter(tag -> tag.startsWith(TIMER)).findFirst().orElse(null);
    }
    public static boolean interact(Mob mob,Player player,InteractionHand hand) {
        if(!(mob instanceof AbstractSkeleton) || !player.getItemInHand(hand).is(Items.GOLDEN_APPLE))return false;
        if(timer(mob)!=null)return true;
        if(!mob.hasEffect(MobEffects.WEAKNESS))return false;
        if(mob.level() instanceof ServerLevel world) {
            int delay=3600+world.getRandom().nextInt(2401);
            mob.addTag(TIMER+(world.getServer().overworld().getGameTime()+delay));
            mob.setPersistenceRequired();
            mob.removeEffect(MobEffects.WEAKNESS);
            mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH,delay,Math.max(0,world.getDifficulty().getId()-1)));
            player.getItemInHand(hand).consume(1,player);
            world.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,mob.getX(),mob.getY()+1,mob.getZ(),8,.3,.4,.3,.01);
        }
        return true;
    }
    public static void tick(Mob mob) {
        if(!(mob instanceof AbstractSkeleton) || !(mob.level() instanceof ServerLevel world) || !mob.isAlive())return;
        String tag=timer(mob);if(tag==null)return;
        long end;
        try { end=Long.parseLong(tag.substring(TIMER.length())); }
        catch(NumberFormatException invalid){mob.removeTag(tag);return;}
        if(world.getServer().overworld().getGameTime()<end)return;
        mob.convertTo(EntitiesMCA.ZOMBIE_PERSON,ConversionParams.single(mob,true,true),zombie -> {
            zombie.removeTag(tag);
            zombie.finalizeSpawn(world,world.getCurrentDifficultyAt(zombie.blockPosition()),EntitySpawnReason.CONVERSION,null);
            zombie.setAgeState(AgeState.ADULT);
            zombie.removeEffect(MobEffects.STRENGTH);
            zombie.setPersistenceRequired();
            world.levelEvent(1027,zombie.blockPosition(),0);
        });
    }
}
