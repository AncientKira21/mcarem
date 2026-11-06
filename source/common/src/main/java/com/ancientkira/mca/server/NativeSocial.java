package com.ancientkira.mca.server;

import java.util.*;
import com.ancientkira.mca.entity.*;
import com.ancientkira.mca.entity.ai.relationship.Gender;
import com.ancientkira.mca.resources.Names;
import com.ancientkira.mca.registry.ProfessionsMCA;
import com.ancientkira.mca.util.WorldUtils;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.server.level.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.saveddata.SavedData;

/** Social profiles preserve native entities and use MCA's existing dialogue engine. */
public final class NativeSocial extends SavedData {
    private final Map<UUID,CompoundTag> profiles=new HashMap<>();
    private final Map<Mob,VillagerEntityMCA> live=new WeakHashMap<>();
    private NativeSocial(ServerLevel level) {}
    private NativeSocial(ServerLevel level,CompoundTag data) {
        for(String id:data.keySet())try{profiles.put(UUID.fromString(id),data.getCompoundOrEmpty(id));}catch(IllegalArgumentException ignored){}
    }
    public static NativeSocial get(ServerLevel level){return WorldUtils.loadData(level,(tag,provider)->new NativeSocial(level,tag),NativeSocial::new,"mca_native_social");}
    public CompoundTag save(CompoundTag data,HolderLookup.Provider provider){profiles.forEach((id,tag)->data.put(id.toString(),tag));return data;}
    public static boolean supported(Entity entity){return !(entity instanceof VillagerLike<?>) && (entity instanceof Villager || entity instanceof Raider || entity instanceof IronGolem || entity instanceof net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader);}
    public static void name(Mob mob) {
        if(!supported(mob) || !(mob.level() instanceof ServerLevel world))return;
        Gender gender=(mob.getUUID().getLeastSignificantBits()&1)==0?Gender.MALE:Gender.FEMALE;
        var tree=com.ancientkira.mca.server.world.data.FamilyTree.get(world);
        String displayed=mob.hasCustomName()?mob.getCustomName().getString():Names.pickCitizenName(gender,mob);
        var entry=tree.getOrCreate(mob.getUUID(),displayed,gender);
        String full=entry.getName();
        if(FamilyNames.lastName(full).isBlank()) {full=FamilyNames.generated(displayed,mob.getUUID());entry.setName(full);}
        String first=FamilyNames.firstName(full);
        if(!mob.hasCustomName() || !mob.getCustomName().getString().equals(first))mob.setCustomName(Component.literal(first));
    }
    public static VillagerEntityMCA resolve(ServerPlayer player,UUID id) {
        Entity entity=player.level().getEntity(id);
        if(entity instanceof VillagerEntityMCA person)return person;
        if(!(entity instanceof Mob mob) || !supported(mob) || !mob.isAlive() || player.distanceToSqr(mob)>64)return null;
        name(mob);var manager=get(player.level());
        VillagerEntityMCA proxy=manager.live.computeIfAbsent(mob,nativeMob->{
            var profile=VillagerFactory.newVillager(player.level()).withUUID(id).withName(nativeMob.getName().getString())
                    .withGender((id.getLeastSignificantBits()&1)==0?Gender.MALE:Gender.FEMALE)
                    .withAge(nativeMob instanceof AgeableMob ageable?ageable.getAge():0).withPosition(nativeMob).build();
            CompoundTag saved=manager.profiles.get(id);if(saved!=null)profile.getTypeDataManager().load(profile,saved);
            if(nativeMob instanceof Villager villager)profile.setVillagerData(villager.getVillagerData());
            else if(nativeMob instanceof Raider raider && SpawnQueue.raiderProfession(raider)!=null)profile.setProfession(SpawnQueue.raiderProfession(raider));
            else if(nativeMob instanceof IronGolem)profile.setProfession(ProfessionsMCA.GUARD);
            return profile;
        });
        proxy.setId(mob.getId());proxy.snapTo(mob.getX(),mob.getY(),mob.getZ());proxy.setCustomName(Component.literal(com.ancientkira.mca.server.world.data.FamilyTree.get(player.level()).getOrCreate(id,mob.getName().getString(),proxy.getGenetics().getGender()).getName()));
        return proxy;
    }
    public static void persist(ServerPlayer player,VillagerEntityMCA proxy) {
        if(proxy==null || player.level().getEntity(proxy.getUUID())==proxy)return;
        var manager=get(player.level());CompoundTag data=new CompoundTag();proxy.getTypeDataManager().save(proxy,data);
        manager.profiles.put(proxy.getUUID(),data);manager.setDirty();
    }
    public static boolean open(ServerPlayer player,Mob mob) {
        VillagerEntityMCA proxy=resolve(player,mob.getUUID());if(proxy==null)return false;
        proxy.getInteractions().beginConversation(player);
        CompoundTag data=new CompoundTag();proxy.getTypeDataManager().save(proxy,data);
        data.putInt("NativeAge",proxy.getAge());
        data.putString("NativeProfession",net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(proxy.getProfession()).toString());
        com.ancientkira.mca.network.Network.sendToPlayer(new com.ancientkira.mca.network.s2c.OpenNativeSocial(mob.getId(),data),player);
        persist(player,proxy);return true;
    }
}
