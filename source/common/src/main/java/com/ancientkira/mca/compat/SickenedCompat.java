package com.ancientkira.mca.compat;

import com.ancientkira.mca.Config;
import com.ancientkira.mca.MCA;
import com.ancientkira.mca.entity.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.*;
import java.util.*;

/** Optional integration: no Sickened Mobs classes or item registrations are required when absent. */
public final class SickenedCompat {
    public static final String MOD = "sickened_mobs", TAG = "sickened_mob", CURE = "sickened_cure:";
    private static final String PROGRESS_TAG = "mca_sickness:", ZOMBIE_TAG = "mca_sickness_zombie:";
    private static final Identifier STEW = Identifier.fromNamespaceAndPath(MOD,"golden_apple_stew");
    private SickenedCompat() {}
    public static boolean enabled() { return MCA.platformHelper.isModLoaded(MOD); }
    public static float sanitize(float progress) { return Float.isFinite(progress) ? Math.max(0,Math.min(1,progress)) : 0; }
    public static float progress(VillagerLike<?> person) { return enabled() ? person.getTrackedValue(VillagerLike.SICKNESS_PROGRESS) : 0; }
    public static boolean sick(VillagerLike<?> person) { return enabled() && (progress(person)>0 || person.getTrackedValue(VillagerLike.SICKENED)); }
    public static String ensureSickenedId(VillagerLike<?> person) {
        String id = person.getTrackedValue(VillagerLike.SICKENED_ID);
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
            person.setTrackedValue(VillagerLike.SICKENED_ID, id);
        }
        for (String tag : new HashSet<>(person.asEntity().entityTags())) {
            if (tag.startsWith("sickened_id:")) person.asEntity().removeTag(tag);
        }
        person.asEntity().addTag("sickened_id:" + id);
        return id;
    }
    public static void playCoughSound(LivingEntity entity) {
        var snd = (entity instanceof VillagerEntityMCA vmca && vmca.getGenetics().getGender() == com.ancientkira.mca.entity.ai.relationship.Gender.MALE)
                ? com.ancientkira.mca.registry.SoundsMCA.VILLAGER_MALE_COUGH
                : com.ancientkira.mca.registry.SoundsMCA.VILLAGER_FEMALE_COUGH;
        entity.playSound(snd, 1.0F, 1.0F);
    }
    public static void setProgress(VillagerLike<?> person,float progress) {
        float value=enabled()?sanitize(progress):0;
        person.setTrackedValue(VillagerLike.SICKNESS_PROGRESS,value);
        person.setTrackedValue(VillagerLike.SICKENED,value>0);
        if(value==0){
            person.asEntity().removeTag(TAG);
            person.setTrackedValue(VillagerLike.SICKENED_ID,"");
            for (String tag : new HashSet<>(person.asEntity().entityTags())) {
                if (tag.startsWith("sickened_id:")) person.asEntity().removeTag(tag);
            }
        } else {
            ensureSickenedId(person);
            if (value >= 1.0F) {
                person.asEntity().addTag(TAG);
            } else {
                person.asEntity().removeTag(TAG);
            }
        }
    }
    private static float zombieProgress(Mob mob) {
        if(mob instanceof VillagerLike<?> person)return person.getTrackedValue(VillagerLike.SICKNESS_PROGRESS);
        for(String tag:mob.entityTags())if(tag.startsWith(PROGRESS_TAG))try{return sanitize(Float.parseFloat(tag.substring(PROGRESS_TAG.length())));}catch(NumberFormatException ignored){}
        return 0;
    }
    private static void zombieProgress(Mob mob,float value) {
        if(mob instanceof VillagerLike<?> person){
            person.setTrackedValue(VillagerLike.SICKNESS_PROGRESS,sanitize(value));
            if(value>0) {
                person.setTrackedValue(VillagerLike.SICKENED,true);
                ensureSickenedId(person);
            }
            return;
        }
        for(String tag:new HashSet<>(mob.entityTags()))if(tag.startsWith(PROGRESS_TAG))mob.removeTag(tag);
        if(value>0)mob.addTag(PROGRESS_TAG+sanitize(value));
    }
    public static void mark(Mob mob) {
        if(!enabled())return;
        mob.addTag(TAG);mob.setPersistenceRequired();zombieProgress(mob,1);
        if(mob instanceof VillagerLike<?> person) {
            person.setTrackedValue(VillagerLike.SICKENED,true);
            ensureSickenedId(person);
        }
    }
    private static float zombieOverride(Mob mob) {
        if(mob instanceof VillagerLike<?> person)return person.getTrackedValue(VillagerLike.SICKNESS_ZOMBIE_PROGRESS);
        for(String tag:mob.entityTags())if(tag.startsWith(ZOMBIE_TAG))try{return sanitize(Float.parseFloat(tag.substring(ZOMBIE_TAG.length())));}catch(NumberFormatException ignored){}
        return 0;
    }
    private static void zombieOverride(Mob mob,float value) {
        if(mob instanceof VillagerLike<?> person){person.setTrackedValue(VillagerLike.SICKNESS_ZOMBIE_PROGRESS,sanitize(value));return;}
        for(String tag:new HashSet<>(mob.entityTags()))if(tag.startsWith(ZOMBIE_TAG))mob.removeTag(tag);
        if(value>0)mob.addTag(ZOMBIE_TAG+sanitize(value));
    }
    public static void clear(Mob mob) {
        zombieOverride(mob,0);
        mob.removeTag(TAG);zombieProgress(mob,0);
        if(mob instanceof VillagerLike<?> person) {
            person.setTrackedValue(VillagerLike.SICKENED,false);
            person.setTrackedValue(VillagerLike.SICKENED_ID,"");
        }
        for (String tag : new HashSet<>(mob.entityTags())) {
            if (tag.startsWith("sickened_id:")) mob.removeTag(tag);
        }
        boolean curing=mob.entityTags().stream().anyMatch(t->t.startsWith(CURE));
        for(String tag:new HashSet<>(mob.entityTags()))if(tag.startsWith(CURE))mob.removeTag(tag);
        if(curing)mob.removeEffect(MobEffects.STRENGTH);
    }
    private static boolean undead(Entity attacker) {
        return attacker != null && (attacker instanceof Zombie
                || attacker instanceof net.minecraft.world.entity.monster.skeleton.AbstractSkeleton
                || attacker.getType().builtInRegistryHolder().is(net.minecraft.tags.EntityTypeTags.UNDEAD));
    }
    private static boolean normalCombatAttacker(Entity attacker) {
        if (!(attacker instanceof Mob mob) || attacker.entityTags().contains(TAG)) return false;
        if (mob.getAttributes().hasAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                && mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) > 0) return true;
        return mob instanceof net.minecraft.world.entity.monster.skeleton.AbstractSkeleton
                || mob instanceof net.minecraft.world.entity.monster.Creeper
                || mob instanceof net.minecraft.world.entity.monster.Slime
                || mob instanceof net.minecraft.world.entity.monster.Ghast
                || mob instanceof net.minecraft.world.entity.animal.equine.Llama
                || mob instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon;
    }
    /** Opposing undead factions retain MCA identity instead of using vanilla replacements. */
    public static boolean onDeath(LivingEntity victim, DamageSource source) {
        if (!enabled() || !(victim instanceof ZombieVillagerEntityMCA person)
                || !(victim.level() instanceof ServerLevel world) || world.getDifficulty() != Difficulty.HARD) return false;
        Entity attacker = source.getEntity();
        boolean infected = attacker != null && attacker.entityTags().contains(TAG)
                || source.is(DamageTypes.WITHER) || source.is(DamageTypes.WITHER_SKULL)
                || victim.hasEffect(MobEffects.WITHER) || attacker instanceof WitherSkeleton || attacker instanceof WitherBoss;
        boolean wasSickened = person.entityTags().contains(TAG);
        if (infected == wasSickened || !infected && !normalCombatAttacker(attacker)) return false;
        var type = infected ? com.ancientkira.mca.registry.EntitiesMCA.SICKENED_PERSON
                : com.ancientkira.mca.registry.EntitiesMCA.ZOMBIE_PERSON;
        var converted = person.convertTo(type, ConversionParams.single(person, true, false), result -> {});
        if (converted == null) return false;
        if (infected) mark(converted); else clear(converted);
        converted.setHealth(converted.getMaxHealth());
        converted.removeEffect(MobEffects.WITHER);
        converted.removeEffect(MobEffects.STRENGTH);
        converted.setTarget(null);
        converted.setLastHurtByMob(null);
        return true;
    }
    public static void onHurt(LivingEntity victim,DamageSource source) {
        if(!enabled() || !Config.getInstance().enableInfection || !(victim.level() instanceof ServerLevel world)
                || !(victim instanceof VillagerEntityMCA || victim instanceof Zombie)
                || !(victim instanceof Mob mob))return;
        Entity attacker=source.getEntity();
        boolean exposure=source.is(DamageTypes.WITHER) || source.is(DamageTypes.WITHER_SKULL) || victim.hasEffect(MobEffects.WITHER)
                || attacker instanceof WitherSkeleton || attacker instanceof WitherBoss
                || attacker!=null && attacker.entityTags().contains(TAG);
        if(victim instanceof Zombie && mob.entityTags().contains(TAG) && undead(attacker) && !exposure) {
            if(zombieOverride(mob)==0 && InfectionRules.roll(mob))zombieOverride(mob,.001F);
            return;
        }
        if(exposure && !mob.entityTags().contains(TAG) && zombieProgress(mob)==0
                && InfectionRules.roll(mob)) {
            zombieProgress(mob,.001F);
            if(mob instanceof VillagerLike<?> person) {
                person.sendChatToAllAround("villager.sickness");
                playCoughSound(mob);
                ensureSickenedId(person);
            }
        }
    }
    public static void tick(Mob mob) {
        if(!(mob instanceof VillagerLike<?> || mob instanceof Zombie) || mob.level().isClientSide())return;
        if(!enabled()) {
            boolean turned=mob.entityTags().contains(TAG) || zombieProgress(mob)>=1;
            if(turned && mob instanceof VillagerEntityMCA person) {
                clear(person);
                person.convertTo(EntityType.ZOMBIE_VILLAGER,ConversionParams.single(person,true,false),SickenedCompat::clear);
                return;
            }
            if (mob instanceof ZombieVillagerEntityMCA person
                    && (mob.getType() == com.ancientkira.mca.registry.EntitiesMCA.SICKENED_PERSON
                        || mob.getType() == com.ancientkira.mca.registry.EntitiesMCA.SICKENED_VILLAGER)) {
                var converted = person.convertTo(com.ancientkira.mca.registry.EntitiesMCA.ZOMBIE_PERSON,
                        ConversionParams.single(person,true,false), result -> {});
                if (converted != null) clear(converted);
                return;
            }
            if(zombieProgress(mob)>0 || zombieOverride(mob)>0 || mob.entityTags().contains(TAG)
                    || mob.entityTags().stream().anyMatch(t->t.startsWith(CURE))
                    || mob instanceof VillagerLike<?> p && p.getTrackedValue(VillagerLike.SICKENED))clear(mob);
            return;
        }
        if(mob instanceof VillagerLike<?> person && sick(person)) {
            ensureSickenedId(person);
            if(mob.tickCount % 20 == 0 && mob.getRandom().nextInt(25) == 0) {
                playCoughSound(mob);
                person.sendChatToAllAround("villager.sickness");
            }
        }
        if(mob.entityTags().contains(TAG)) {
            // Repair saves written by the former living-villager implementation.
            if (mob instanceof VillagerEntityMCA person) {
                var converted = person.convertTo(com.ancientkira.mca.registry.EntitiesMCA.SICKENED_PERSON,
                        ConversionParams.single(person,true,false), result -> {});
                if (converted != null) mark(converted);
                return;
            }
            float override=zombieOverride(mob);
            if(override>0 && mob.tickCount%20==0 && mob.isAlive()) {
                override=advance(override,Config.getInstance().infectionTime);
                if(override>=1){
                    if (mob instanceof ZombieVillagerEntityMCA person) {
                        var converted = person.convertTo(com.ancientkira.mca.registry.EntitiesMCA.ZOMBIE_PERSON,
                                ConversionParams.single(person,true,false), result -> {});
                        if (converted != null) { clear(converted); converted.setTarget(null); }
                    } else { clear(mob); mob.setTarget(null); }
                    return;
                }
                zombieOverride(mob,override);
            }
            if(mob instanceof VillagerLike<?> person){
                person.setTrackedValue(VillagerLike.SICKENED,true);
                person.setTrackedValue(VillagerLike.SICKNESS_PROGRESS,1F);
                ensureSickenedId(person);
            }
            return;
        }
        // The external cure removes its tag before converting; do not re-infect the cured entity.
        if(mob instanceof VillagerLike<?> person && person.getTrackedValue(VillagerLike.SICKENED) && zombieProgress(mob) == 0) { clear(mob);return; }
        float progress=zombieProgress(mob);
        if(progress<=0 || !mob.isAlive() || mob.tickCount%20!=0)return;
        progress=advance(progress,Config.getInstance().infectionTime);
        zombieProgress(mob,progress);
        if(progress>=1) {
            if(mob instanceof VillagerEntityMCA person) {
                // Set tracked state first: MCA's conversion copies all person data after its callback.
                person.setTrackedValue(VillagerLike.SICKENED,true);
                person.setTrackedValue(VillagerLike.SICKNESS_ZOMBIE_PROGRESS,person.getInfectionProgress());
                ensureSickenedId(person);
                Mob converted=person.convertTo(com.ancientkira.mca.registry.EntitiesMCA.SICKENED_PERSON,ConversionParams.single(person,true,false),SickenedCompat::mark);
                if(converted!=null)mark(converted);
            } else if (mob instanceof ZombieVillagerEntityMCA person) {
                var converted = person.convertTo(com.ancientkira.mca.registry.EntitiesMCA.SICKENED_PERSON,
                        ConversionParams.single(person,true,false), result -> {});
                if (converted != null) mark(converted);
            } else mark(mob);
        }
    }
    public static float advance(float progress,int configuredSteps) { return sanitize(progress+1F/Math.max(1,configuredSteps)); }
    public static boolean cureGift(ServerPlayer player,VillagerEntityMCA person,ItemStack stack) {
        if(!enabled() || !sick(person) || !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(STEW))return false;
        // A gift treats a pending infection immediately; fully turned mobs use Sickened Mobs' timed cure.
        clear(person);person.ate();
        if(!player.getAbilities().instabuild) {
            stack.shrink(1);ItemStack bowl=new ItemStack(Items.BOWL);
            if(!player.getInventory().add(bowl))player.spawnAtLocation(player.level(),bowl);
        }
        player.level().sendParticles(ParticleTypes.HAPPY_VILLAGER,person.getX(),person.getY()+1,person.getZ(),8,.3,.4,.3,.02);
        return true;
    }
    public static void dropFlesh(LivingEntity victim,ServerLevel world) {
        if(!enabled() || !(victim instanceof VillagerLike<?> person)
                || !(person.getTrackedValue(VillagerLike.SICKENED) || victim.entityTags().contains(TAG)))return;
        BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath(MOD,"withered_flesh"))
                .ifPresent(item -> victim.spawnAtLocation(world,new ItemStack(item.value(),1+victim.getRandom().nextInt(2))));
    }
    public static void transfer(Mob from,Mob to) {
        if(!enabled()){clear(to);return;}
        float progress=zombieProgress(from);
        zombieOverride(to,zombieOverride(from));
        if(from.entityTags().contains(TAG))mark(to);else if(progress>0)zombieProgress(to,progress);
        for(String tag:from.entityTags())if(tag.startsWith(CURE))to.addTag(tag);
        if(from instanceof VillagerLike<?> fromP && to instanceof VillagerLike<?> toP) {
            String sickId = fromP.getTrackedValue(VillagerLike.SICKENED_ID);
            if(sickId != null && !sickId.isBlank()) {
                toP.setTrackedValue(VillagerLike.SICKENED_ID, sickId);
                to.addTag("sickened_id:" + sickId);
            }
        }
    }
    public static void afterZombieCure(ZombieVillagerEntityMCA from,VillagerLike<?> to) {
        if(!enabled() || from.getTrackedValue(VillagerLike.SICKENED) && !from.entityTags().contains(TAG)) {
            if(to.asEntity() instanceof Mob mob)clear(mob);
        }
    }
}
