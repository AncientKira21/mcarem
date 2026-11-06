package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.network.Network;
import com.ancientkira.mca.network.s2c.VillagerMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.util.ProblemReporter;
import net.minecraft.sounds.SoundEvents;
import java.util.Map;
import java.util.WeakHashMap;

/** One server-selected command drives both the spoken line and its corresponding action. */
public final class TacticalCommands {
    private static final String ANVIL="mca_tactical_anvil";
    private static final Map<IronGolem,Long> NEXT_SHOUT=new WeakHashMap<>();
    private TacticalCommands() {}
    public static void shout(VillagerEntityMCA speaker) {
        LivingEntity target=speaker.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).orElse(speaker.getTarget());
        int line=speaker.getRandom().nextInt(16)+1;
        if(line==5 && !smash(speaker,target))line=3;
        speaker.sendChatToAllAround("villager.attack/"+line);
    }
    public static void tickGolem(IronGolem golem) {
        if(!(golem.level() instanceof ServerLevel world) || golem.isNoAi() || !golem.isAlive()
                || GolemDance.dancing(golem) || golem.getTarget()==null || !golem.getTarget().isAlive()
                || !golem.hasLineOfSight(golem.getTarget()))return;
        long now=world.getGameTime();
        if(now<NEXT_SHOUT.getOrDefault(golem,0L))return;
        NEXT_SHOUT.put(golem,now+160+golem.getRandom().nextInt(80));
        int line=golem.getRandom().nextInt(16)+1;
        if(line==5 && !smash(golem,golem.getTarget()))line=3;
        var prefix=golem.getDisplayName().copy().append(": ");
        var words=Component.translatable("villager.attack/"+line);
        for(var player:world.getPlayers(p->p.distanceToSqr(golem)<=400))Network.sendToPlayer(new VillagerMessage(prefix,words,golem.getUUID()),player);
    }
    private static boolean smash(Mob attacker,LivingEntity target) {
        if(!(attacker.level() instanceof ServerLevel world) || target==null || target==attacker
                || !target.isAlive() || target.isRemoved() || target.level()!=world
                || attacker.distanceToSqr(target)>16*16 || !attacker.hasLineOfSight(target) || !attacker.canAttack(target))return false;
        // A display cannot be mined, picked up or placed as terrain; the hit uses vanilla anvil damage.
        var anvil=new Display.BlockDisplay(EntityType.BLOCK_DISPLAY,world);
        var data=new CompoundTag();data.store("block_state",BlockState.CODEC,Blocks.ANVIL.defaultBlockState());
        anvil.load(TagValueInput.create(ProblemReporter.DISCARDING,world.registryAccess(),data));
        anvil.snapTo(target.getX()-.5,target.getY()+target.getBbHeight()-.2,target.getZ()-.5);
        anvil.setInvulnerable(true);anvil.setNoGravity(true);anvil.addTag(ANVIL);
        if(!world.addFreshEntity(anvil))return false;
        target.hurtServer(world,world.damageSources().anvil(attacker),6.0F);
        anvil.playSound(SoundEvents.ANVIL_LAND,.8F,1.0F);
        return true;
    }
    public static void tickAnvil(Entity entity) {
        if(entity.level().isClientSide() || !(entity instanceof Display.BlockDisplay) || !entity.entityTags().contains(ANVIL))return;
        if(entity.tickCount>=2)entity.discard();
        else entity.setPos(entity.getX(),entity.getY()-.4,entity.getZ());
    }
}
