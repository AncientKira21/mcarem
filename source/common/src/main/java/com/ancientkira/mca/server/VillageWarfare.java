package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.MemoryModuleTypeMCA;
import com.ancientkira.mca.registry.ProfessionsMCA;
import com.ancientkira.mca.server.world.data.*;
import com.ancientkira.mca.util.WorldUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** Saved declarations, defender rosters and conquest for neighboring registered villages. */
public final class VillageWarfare extends SavedData {
    public static final int NEARBY_RANGE = 512;
    private final List<War> wars = new ArrayList<>();
    private final Map<UUID, CompoundTag> returns = new HashMap<>();
    private final Map<Integer,Long> rebellionChecks=new HashMap<>();
    private final Set<Long> friendly = new HashSet<>();
    private final ServerLevel world;
    private VillageWarfare(ServerLevel world) { this.world = world; }
    private VillageWarfare(ServerLevel world, CompoundTag saved) {
        this(world);
        for (var tag : saved.getListOrEmpty("FunReturns")) if (tag instanceof CompoundTag entry)
            returns.put(UUID.fromString(entry.getStringOr("Id", "")), entry);
        for(var tag:saved.getListOrEmpty("RebellionChecks"))if(tag instanceof CompoundTag entry)
            rebellionChecks.put(entry.getIntOr("Village",-1),entry.getLongOr("Next",0));
        for (var tag : saved.getListOrEmpty("Friendly")) {
            if (tag instanceof CompoundTag pair) friendly.add(pairKey(pair.getIntOr("First", -1), pair.getIntOr("Second", -1)));
        }
        for (var tag : saved.getListOrEmpty("Wars")) {
            if (!(tag instanceof CompoundTag data)) continue;
            War war = new War(data.getIntOr("First", -1), data.getIntOr("Second", -1), data.getLongOr("Starts", 0));
            war.started = data.getBooleanOr("Started", false);
            war.fun = data.getBooleanOr("Fun", false);
            for (var tagState : data.getListOrEmpty("Participants")) if (tagState instanceof CompoundTag entry)
                war.participants.put(UUID.fromString(entry.getStringOr("Id", "")), entry);
            war.independence=data.getBooleanOr("Independence",false);
            readRoster(data, "FirstDefenders", war.firstDefenders);
            readRoster(data, "SecondDefenders", war.secondDefenders);
            readRoster(data, "GolemDefenders", war.golems);
            wars.add(war);
        }
    }
    public static VillageWarfare get(ServerLevel world) {
        return WorldUtils.loadData(world, (nbt, provider) -> new VillageWarfare(world, nbt), VillageWarfare::new, "mca_village_wars");
    }
    private static void readRoster(CompoundTag data, String key, Set<UUID> roster) {
        data.getListOrEmpty(key).forEach(tag -> tag.asString().ifPresent(id -> roster.add(UUID.fromString(id))));
    }
    private static ListTag writeRoster(Set<UUID> roster) {
        ListTag result = new ListTag();
        roster.forEach(id -> result.add(StringTag.valueOf(id.toString())));
        return result;
    }
    public CompoundTag save(CompoundTag saved, HolderLookup.Provider provider) {
        ListTag pending = new ListTag(); returns.values().forEach(pending::add); saved.put("FunReturns", pending);
        ListTag checks=new ListTag();rebellionChecks.forEach((id,next)->{CompoundTag tag=new CompoundTag();tag.putInt("Village",id);tag.putLong("Next",next);checks.add(tag);});saved.put("RebellionChecks",checks);
        ListTag list = new ListTag();
        for (War war : wars) {
            CompoundTag data = new CompoundTag();
            data.putInt("First", war.first); data.putInt("Second", war.second);
            data.putLong("Starts", war.starts); data.putBoolean("Started", war.started);
            data.putBoolean("Independence",war.independence);
            data.putBoolean("Fun", war.fun);
            ListTag participants = new ListTag(); war.participants.values().forEach(participants::add);
            data.put("Participants", participants);
            data.put("FirstDefenders", writeRoster(war.firstDefenders));
            data.put("SecondDefenders", writeRoster(war.secondDefenders));
            data.put("GolemDefenders", writeRoster(war.golems));
            list.add(data);
        }
        saved.put("Wars", list);
        ListTag friendships = new ListTag();
        for (long pair : friendly) {
            CompoundTag entry = new CompoundTag(); entry.putInt("First", (int) (pair >> 32)); entry.putInt("Second", (int) pair);
            friendships.add(entry);
        }
        saved.put("Friendly", friendships);
        return saved;
    }
    public static long pairKey(int first, int second) {
        return ((long) Math.min(first, second) << 32) | (Math.max(first, second) & 0xffffffffL);
    }
    public boolean isFriendly(int first, int second) { return friendly.contains(pairKey(first, second)); }
    public boolean toggleFriendly(Village first, int otherId) {
        Village second = VillageManager.get(world).getOrEmpty(otherId).orElse(null);
        if (second == null || first == second || first.getCenter().distSqr(second.getCenter()) > NEARBY_RANGE * NEARBY_RANGE) return false;
        long pair = pairKey(first.getId(), otherId);
        boolean added = !friendly.remove(pair);
        if (added) { friendly.add(pair); cancel(first, otherId); }
        setDirty();
        announce(first, second, Component.translatable(added ? "mca.war.friendly" : "mca.war.neutral", first.getName(), second.getName()));
        return true;
    }

    public boolean isBusy(int village) {
        return wars.stream().anyMatch(war -> war.involves(village));
    }
    private long districtGuards(Village parent,int district){
        CompoundTag snapshot=parent.releaseRecords().get(district);
        if(snapshot==null)return 0;
        return snapshot.getCompoundOrEmpty("residentNames").keySet().stream().filter(id->{
            try{return world.getEntity(UUID.fromString(id)) instanceof VillagerEntityMCA person && person.isAlive() && person.isGuard();}
            catch(IllegalArgumentException e){return false;}
        }).count();
    }
    public boolean startIndependence(Village parent,int district,int minutes){
        if(district<0||minutes<0||minutes>20||isBusy(parent.getId())||districtGuards(parent,district)<1)return false;
        Village rebels=VillageManager.get(world).detachForIndependence(parent,district);
        if(rebels==null)return false;
        War war=new War(rebels.getId(),parent.getId(),world.getGameTime()+minutes*1200L);war.independence=true;
        wars.add(war);rebellionChecks.put(parent.getId(),world.getGameTime()+3*24000L);setDirty();
        Component warning=Component.translatable("mca.independence.warning",rebels.getName(),parent.getName(),minutes);
        announce(rebels,parent,warning);
        for(var player:world.players())if(com.ancientkira.mca.resources.Tasks.getRank(parent,player).isAtLeast(com.ancientkira.mca.resources.Rank.MAYOR))
            com.ancientkira.mca.server.world.data.PlayerSaveData.get(player).sendLetter(warning);
        if(minutes==0)tick();
        return true;
    }
    private void automaticIndependence(){
        long now=world.getGameTime();if(now%24000!=0)return;
        for(Village parent:java.util.stream.StreamSupport.stream(VillageManager.get(world).spliterator(),false).toList()){
            if(isBusy(parent.getId())||now<rebellionChecks.getOrDefault(parent.getId(),0L))continue;
            rebellionChecks.put(parent.getId(),now+24000);setDirty();
            if(world.getRandom().nextInt(10)!=0)continue;
            for(int district:parent.releaseRecords().keySet())if(district>=0 && districtGuards(parent,district)>=2){startIndependence(parent,district,3);break;}
        }
    }

    public boolean declare(Village first, int targetId, int minutes) {
        return declare(first, targetId, minutes, false);
    }
    public boolean declare(Village first, int targetId, int minutes, boolean fun) {
        Village second = VillageManager.get(world).getOrEmpty(targetId).orElse(null);
        if (minutes < 0 || minutes > 20 || second == null || first == second || !fun && isFriendly(first.getId(), targetId)
                || first.getCenter().distSqr(second.getCenter()) > NEARBY_RANGE * NEARBY_RANGE
                || wars.stream().anyMatch(war -> war.involves(first.getId()) || war.involves(targetId))) return false;
        War war = new War(first.getId(), targetId, world.getGameTime() + minutes * 1200L);
        war.fun = fun;
        wars.add(war); setDirty();
        announce(first, second, Component.translatable(fun ? "mca.war.fun.declared" : "mca.war.declared", first.getName(), second.getName(), minutes));
        if (minutes == 0) tick();
        return true;
    }
    public boolean cancel(Village village, int otherId) {
        War war = wars.stream().filter(w -> w.involves(village.getId()) && w.involves(otherId)).findFirst().orElse(null);
        if (war == null) return false;
        Village other = VillageManager.get(world).getOrEmpty(otherId).orElse(null);
        clearOrders(war); restoreFun(war); wars.remove(war); setDirty();
        if (other != null) announce(village, other, Component.translatable("mca.war.peace", village.getName(), other.getName()));
        return true;
    }
    public ListTag nearby(Village village) {
        ListTag list = new ListTag();
        VillageManager.get(world).findVillages(other -> other != village
                        && village.getCenter().distSqr(other.getCenter()) <= NEARBY_RANGE * NEARBY_RANGE)
                .sorted(Comparator.comparingDouble(other -> village.getCenter().distSqr(other.getCenter())))
                .forEach(other -> {
                    CompoundTag entry = new CompoundTag(); entry.putInt("Id", other.getId()); entry.putString("Name", other.getName());
                    War war = wars.stream().filter(w -> w.involves(village.getId()) && w.involves(other.getId())).findFirst().orElse(null);
                    entry.putBoolean("Friendly", isFriendly(village.getId(), other.getId()));
                    entry.putBoolean("AtWar", war != null);
                    entry.putBoolean("Fun", war != null && war.fun);
                    entry.putBoolean("Started", war != null && war.started);
                    entry.putInt("Minutes", war == null ? 0 : (int) Math.max(0, (war.starts - world.getGameTime() + 1199) / 1200));
                    list.add(entry);
                });
        return list;
    }
    private void recruit(Village village, Set<UUID> roster, Set<UUID> opponents) {
        FamilyTree family = FamilyTree.get(world);
        village.getResidentsUUIDs().filter(id -> !opponents.contains(id)).filter(id -> {
            if (world.getEntity(id) instanceof VillagerEntityMCA person) {
                var home = person.getResidency().getHomeVillage()
                        .or(() -> VillageManager.get(world).findNearestVillage(person));
                return person.isAlive() && person.isGuard()
                        && home.map(current -> current.getId() == village.getId()).orElse(true);
            }
            return family.getOrEmpty(id).filter(node -> !node.isDeceased()
                    && (node.getProfession() == ProfessionsMCA.GUARD || node.getProfession() == ProfessionsMCA.ARCHER)).isPresent();
        }).forEach(roster::add);
        for (var entity : world.getAllEntities()) {
            if (entity instanceof IronGolem golem && golem.isAlive() && !GolemAllies.raider(golem)
                    && !opponents.contains(golem.getUUID()) && GolemAllies.home(golem).filter(v->v.getId()==village.getId()).isPresent()) {
                roster.add(golem.getUUID());
                continue;
            }
            if (!(entity instanceof VillagerEntityMCA person) || !person.isAlive() || !person.isGuard()
                    || opponents.contains(person.getUUID())) continue;
            var home = person.getResidency().getHomeVillage()
                    .or(() -> VillageManager.get(world).findNearestVillage(person));
            if (home.filter(current -> current.getId() == village.getId()).isPresent()) roster.add(person.getUUID());
        }
    }
    private static int homeOf(LivingEntity entity) {
        if(entity instanceof VillagerEntityMCA person && person.isGuard())
            return person.getResidency().getRegistryVillage().map(Village::getId).orElse(-1);
        if(entity instanceof IronGolem golem && !GolemAllies.raider(golem))
            return GolemAllies.home(golem).map(Village::getId).orElse(-1);
        return -1;
    }
    private static int side(War war, LivingEntity entity) {
        if(!(entity instanceof IronGolem) && !(entity instanceof VillagerEntityMCA person && person.isGuard()))return 0;
        return WarAllegiance.side(entity.getUUID(), war.firstDefenders, war.secondDefenders,
                war.firstDefenders.contains(entity.getUUID()) != war.secondDefenders.contains(entity.getUUID()) ? -1 : homeOf(entity),
                war.first, war.second);
    }
    /** Present only when both combatants belong to this active war. */
    public static Optional<Boolean> combatRelation(LivingEntity first, LivingEntity second) {
        if (com.ancientkira.mca.compat.HostileGolemCompat.hostile(first) || com.ancientkira.mca.compat.HostileGolemCompat.hostile(second)) return Optional.empty();
        if(first.level()!=second.level() || !(first.level() instanceof ServerLevel world))return Optional.empty();
        for(War war:get(world).wars) if(war.started) {
            int a=side(war,first),b=side(war,second);
            if(a!=0 && b!=0)return Optional.of(a!=b && !knockedOut(first) && !knockedOut(second));
        }
        return Optional.empty();
    }
    public static boolean isWarParticipant(LivingEntity entity) {
        return entity.level() instanceof ServerLevel world && get(world).wars.stream().anyMatch(w->w.started && side(w,entity)!=0);
    }
    public static Optional<LivingEntity> nearestOpponent(LivingEntity entity) {
        if(!(entity.level() instanceof ServerLevel world))return Optional.empty();
        Set<UUID> enemies=new HashSet<>();
        for(War war:get(world).wars) if(war.started) {
            int own=side(war,entity);
            if(own!=0)enemies.addAll(own==1?war.secondDefenders:war.firstDefenders);
        }
        return enemies.stream().map(world::getEntity).filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast)
                .filter(other->other.isAlive() && !other.isRemoved() && !knockedOut(other)
                        && !(other instanceof VillagerEntityMCA person && person.isKidnapped())
                        && combatRelation(entity,other).orElse(false))
                .min(Comparator.comparingDouble(entity::distanceToSqr));
    }
    public static boolean knockedOut(LivingEntity entity) { return entity.entityTags().contains(OUT); }
    private boolean alive(UUID id) {
        if (world.getEntity(id) instanceof VillagerEntityMCA person) return person.isAlive() && person.isGuard() && !knockedOut(person);
        if (world.getEntity(id) instanceof IronGolem golem) return golem.isAlive() && !knockedOut(golem);
        if (wars.stream().anyMatch(w -> w.golems.contains(id))) return true;
        return FamilyTree.get(world).getOrEmpty(id).map(node -> !node.isDeceased()
                && (node.getProfession() == ProfessionsMCA.GUARD || node.getProfession() == ProfessionsMCA.ARCHER)).orElse(true);
    }
    public void tick() {
        automaticIndependence();
        if (world.getGameTime() % 20 != 0 && wars.stream().noneMatch(w -> !w.started && world.getGameTime() >= w.starts)) return;
        var manager = VillageManager.get(world);
        for (War war : List.copyOf(wars)) {
            Village first = manager.getOrEmpty(war.first).orElse(null), second = manager.getOrEmpty(war.second).orElse(null);
            if (first == null || second == null) { clearOrders(war); restoreFun(war); wars.remove(war); setDirty(); continue; }
            if (!war.fun && isFriendly(war.first, war.second)) { cancel(first, war.second); continue; }
            if (!war.started && world.getGameTime() < war.starts) continue;
            if (!war.started) {
                war.started = true;
                if (war.fun) {
                    first.getResidents(world).forEach(person -> capture(war, person));
                    second.getResidents(world).forEach(person -> capture(war, person));
                    for (var entity : world.getAllEntities()) if (entity instanceof net.minecraft.world.entity.animal.golem.IronGolem golem
                            && GolemAllies.home(golem).filter(v -> war.involves(v.getId())).isPresent()) captureGolem(war, golem);
                }
                setDirty();
                announce(first, second, Component.translatable("mca.war.started", first.getName(), second.getName()));
                first.getResidents(world).stream().filter(VillagerEntityMCA::isGuard).forEach(VillageWarfare::mobilize);
                second.getResidents(world).stream().filter(VillagerEntityMCA::isGuard).forEach(VillageWarfare::mobilize);
            }
            int before = war.firstDefenders.size() + war.secondDefenders.size();
            repairTeams(war);
            if (before == 0 || world.getGameTime() % 100 == 0) {
                recruit(first, war.firstDefenders, war.secondDefenders);
                recruit(second, war.secondDefenders, war.firstDefenders);
            }
            if (before != war.firstDefenders.size() + war.secondDefenders.size()) setDirty();
            java.util.stream.Stream.concat(war.firstDefenders.stream(), war.secondDefenders.stream())
                    .filter(id -> world.getEntity(id) instanceof IronGolem).forEach(id -> { if(war.golems.add(id))setDirty(); });
            boolean firstAlive = war.firstDefenders.stream().anyMatch(this::alive);
            boolean secondAlive = war.secondDefenders.stream().anyMatch(this::alive);
            if (!firstAlive || !secondAlive) {
                clearOrders(war); restoreFun(war); wars.remove(war); setDirty();
                if (war.fun) {
                    announce(first, second, Component.translatable("mca.war.fun.ended", first.getName(), second.getName()));
                    continue;
                }
                if (firstAlive || secondAlive) {
                    Village winner = firstAlive ? first : second, loser = firstAlive ? second : first;
                    announce(winner, loser, Component.translatable("mca.war.won", winner.getName(), loser.getName()));
                    boolean wonIndependence=war.independence && winner==first;
                    if(wonIndependence){
                        announce(winner,loser,Component.translatable("mca.independence.won",winner.getName(),loser.getName()));
                        winner.keepSeparateFrom(loser.getId());loser.keepSeparateFrom(winner.getId());
                    }else{
                        winner.absorbConqueredVillage(loser);
                        manager.removeVillage(loser.getId());manager.setDirty();
                        winner.getResidents(world).forEach(person -> person.getResidency().remapVillage(loser.getId(),winner.getId()));
                    }
                    celebrateConquest(firstAlive ? war.firstDefenders : war.secondDefenders, winner, loser);
                } else {
                    announce(first,second,Component.translatable("mca.war.draw",first.getName(),second.getName()));
                    if(war.independence){
                        second.absorbConqueredVillage(first);manager.removeVillage(first.getId());
                        second.getResidents(world).forEach(person->person.getResidency().remapVillage(first.getId(),second.getId()));
                    }
                }
            } else {
                if (world.getGameTime() % 20 == 0) {
                    march(war.firstDefenders, war.secondDefenders, second);
                    march(war.secondDefenders, war.firstDefenders, first);
                }
            }
        }
    }
    private void repairTeams(War war) {
        Set<UUID> overlap = new HashSet<>(war.firstDefenders);
        overlap.retainAll(war.secondDefenders);
        for (UUID id : overlap) {
            int home = world.getEntity(id) instanceof VillagerEntityMCA person
                    ? person.getResidency().getHomeVillage().map(Village::getId).orElse(war.first) : war.first;
            if (home == war.second) war.firstDefenders.remove(id);
            else war.secondDefenders.remove(id);
        }
        if (!overlap.isEmpty()) setDirty();
    }

    private static void mobilize(VillagerEntityMCA person) {
        if (com.ancientkira.mca.entity.ai.brain.VillagerTasksMCA.hasMovementOrder(person)) return;
        person.getVillagerBrain().setMoveState(com.ancientkira.mca.entity.ai.MoveState.MOVE, null);
        person.getVillagerBrain().abandonJob();
        if (person.isSleeping()) person.stopSleeping();
    }
    private void celebrateConquest(Set<UUID> victors, Village winner, Village loser) {
        com.ancientkira.mca.MCA.LOGGER.info("Village war won: {} defeated and absorbed {}", winner.getName(), loser.getName());
        var destination = loser.getBuildings().values().stream().filter(room -> room.getFloorNumber() == 0)
                .min(Comparator.comparingDouble(room -> room.getSourceBlock().distSqr(loser.getCenter())))
                .map(Building::getSourceBlock).orElseGet(() -> new net.minecraft.core.BlockPos(
                        loser.getCenter().getX(), loser.getCenter().getY(), loser.getCenter().getZ()));
        int fireworks = 0;
        for (UUID id : victors) {
            if (!(world.getEntity(id) instanceof VillagerEntityMCA person) || !person.isAlive()
                    || person.isKidnapped() || person.isNoAi()
                    || com.ancientkira.mca.entity.ai.brain.VillagerTasksMCA.hasMovementOrder(person)) continue;
            mobilize(person);
            person.getBrain().setActiveActivityIfPossible(Activity.IDLE);
            person.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(destination, 0.75F, 3));
            person.getNavigation().moveTo(destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5, 0.75);
            if (fireworks++ >= 10) continue;
            var rocket = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FIREWORK_ROCKET);
            var explosion = new net.minecraft.world.item.component.FireworkExplosion(
                    net.minecraft.world.item.component.FireworkExplosion.Shape.STAR,
                    it.unimi.dsi.fastutil.ints.IntList.of(0xffcc33, 0x4488ff),
                    it.unimi.dsi.fastutil.ints.IntList.of(0xffffff), true, true);
            rocket.set(net.minecraft.core.component.DataComponents.FIREWORKS,
                    new net.minecraft.world.item.component.Fireworks(2, List.of(explosion)));
            world.addFreshEntity(new net.minecraft.world.entity.projectile.FireworkRocketEntity(
                    world, person.getX(), person.getY() + 3, person.getZ(), rocket));
        }
    }
    private void march(Set<UUID> defenders, Set<UUID> opponents, Village enemy) {
        for (UUID id : defenders) {
            if (!(world.getEntity(id) instanceof VillagerEntityMCA person) || !person.isAlive() || !person.isGuard()
                    || knockedOut(person) || person.isNoAi() || person.isKidnapped()
                    || com.ancientkira.mca.entity.ai.brain.VillagerTasksMCA.hasMovementOrder(person)) continue;
            if (person.isSleeping() || person.getVillagerBrain().getMoveState() != com.ancientkira.mca.entity.ai.MoveState.MOVE
                    || person.getVillagerBrain().getCurrentJob() != com.ancientkira.mca.entity.ai.Chore.NONE) mobilize(person);
            person.getBrain().setActiveActivityIfPossible(Activity.FIGHT);
            LivingEntity target = nearestOpponent(person).orElse(null);
            if (target != null) {
                person.getBrain().setMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY, target);
                person.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
                person.setTarget(target);
                if (person.distanceToSqr(target) > 16 * 16) person.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new EntityTracker(target, false), 0.75F, 2));
            } else person.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new net.minecraft.core.BlockPos(
                    enemy.getCenter().getX(), enemy.getCenter().getY(), enemy.getCenter().getZ()), 0.75F, 4));
        }
    }
    private void clearOrders(War war) {
        java.util.stream.Stream.concat(war.firstDefenders.stream(), war.secondDefenders.stream()).map(world::getEntity)
                .filter(IronGolem.class::isInstance).map(IronGolem.class::cast).forEach(golem->{
                    golem.setTarget(null);golem.stopBeingAngry();golem.setLastHurtByMob(null);golem.getNavigation().stop();
                });
        java.util.stream.Stream.concat(war.firstDefenders.stream(), war.secondDefenders.stream()).map(world::getEntity)
                .filter(VillagerEntityMCA.class::isInstance).map(VillagerEntityMCA.class::cast).forEach(person -> {
                    person.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                    person.setTarget(null);
                    person.getBrain().eraseMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY);
                    person.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                    person.getNavigation().stop();
                    person.getBrain().updateActivityFromSchedule(world.environmentAttributes(), world.getGameTime(), person.position());
                });
    }
    private void announce(Village first, Village second, Component message) {
        first.getCivilRegistry().ifPresent(registry -> registry.addText(message));
        second.getCivilRegistry().ifPresent(registry -> registry.addText(message));
        world.getServer().getPlayerList().broadcastSystemMessage(message, false);
    }
    public static boolean areEnemies(ServerLevel world, int first, int second) {
        return first != second && get(world).wars.stream().anyMatch(w -> w.started
                && (w.first == first && w.second == second || w.second == first && w.first == second));
    }
    public static boolean hasWar(ServerLevel world, int village) {
        return get(world).wars.stream().anyMatch(w -> w.started && w.involves(village));
    }
    public static boolean isEnemy(int village, VillagerEntityMCA defender) {
        return !knockedOut(defender) && defender.isGuard() && defender.level() instanceof ServerLevel world && get(world).wars.stream()
                .anyMatch(w -> w.started && (w.first == village && w.secondDefenders.contains(defender.getUUID())
                        || w.second == village && w.firstDefenders.contains(defender.getUUID())));
    }
    public static boolean isEnemy(VillagerEntityMCA first, VillagerEntityMCA second) {
        if (knockedOut(first) || knockedOut(second)) return false;
        if (first == second || first.getUUID().equals(second.getUUID()) || !first.isGuard() || !second.isGuard() || first.level() != second.level() || !(first.level() instanceof ServerLevel world)) return false;
        return combatRelation(first,second).orElse(false);
    }
    public static boolean isFighting(VillagerEntityMCA person) {
        return person.level() instanceof ServerLevel world && get(world).wars.stream().anyMatch(war -> war.started
                && (war.firstDefenders.contains(person.getUUID()) || war.secondDefenders.contains(person.getUUID())));
    }
    public static boolean isFunFighting(VillagerEntityMCA person) {
        return !knockedOut(person) && person.level() instanceof ServerLevel world && get(world).wars.stream().anyMatch(w -> w.fun && w.started
                && (w.firstDefenders.contains(person.getUUID()) || w.secondDefenders.contains(person.getUUID())));
    }
    public static void tickCombat(VillagerEntityMCA person) {
        if (!(person.level() instanceof ServerLevel world) || Math.floorMod(person.tickCount + person.getId(), 10) != 0
                || !person.isAlive() || person.isNoAi() || person.isKidnapped() || !isFighting(person)
                || knockedOut(person) || !isFunFighting(person) && person.getHealth() <= person.getMaxHealth() * 0.25F) return;
        LivingEntity target = person.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET)
                .filter(enemy -> enemy.isAlive() && combatRelation(person, enemy).orElse(false)).orElse(null);
        if (target == null) target = nearestOpponent(person).filter(other -> com.ancientkira.mca.entity.ai.GuardPerception.canLocate(person,other)).orElse(null);
        if (target == null) return;
        if (com.ancientkira.mca.entity.ai.brain.VillagerTasksMCA.hasMovementOrder(person)
                && !com.ancientkira.mca.entity.ai.brain.VillagerTasksMCA.canPursueFromVillage(person,target)) return;
        person.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        person.getBrain().setMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY, target);
        person.setTarget(target);
        person.getBrain().setActiveActivityIfPossible(Activity.FIGHT);
        if (person.isSleeping()) person.stopSleeping();
        person.getLookControl().setLookAt(target, 30, 30);
        double distance = person.distanceToSqr(target);
        boolean ranged = person.getProfession() == ProfessionsMCA.ARCHER
                && com.ancientkira.mca.entity.ai.RangedWeaponHelper.isHoldingSupportedWeapon(person);
        double range = ranged ? 15 : person.getBbWidth() + target.getBbWidth() + 2;
        if (ranged ? distance > range * range || !person.hasLineOfSight(target)
                : !com.ancientkira.mca.entity.ai.MeleeAttack.canHit(person, target)) {
            if (com.ancientkira.mca.entity.ai.brain.VillagerTasksMCA.hasMovementOrder(person)) return;
            person.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new EntityTracker(target, false), 0.75F, ranged ? 10 : 0));
            if ((person.getNavigation().isDone() || !person.getNavigation().getPath().canReach())
                    && Math.floorMod(world.getGameTime() + person.getId(), 40) < 10) person.getNavigation().moveTo(target, 0.75);
            return;
        }
        if (person.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)) return;
        if (person.isUsingCombatShield()) person.stopUsingItem();
        if (ranged) person.performRangedAttack(target, 1.0F);
        else {
            person.swing(person.getDominantHand());
            person.doHurtTarget(world, target);
        }
        person.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, ranged ? 30 : 20);
    }
    private static final String OUT = "mca_fun_war_out";
    public static boolean knockedOut(VillagerEntityMCA person) { return person.entityTags().contains(OUT); }
    private void capture(War war, VillagerEntityMCA person) {
        if (war.participants.containsKey(person.getUUID())) return;
        CompoundTag state = new CompoundTag();
        state.putString("Id", person.getUUID().toString());
        state.putDouble("X", person.getX()); state.putDouble("Y", person.getY()); state.putDouble("Z", person.getZ());
        state.putFloat("Yaw", person.getYRot()); state.putFloat("Pitch", person.getXRot());
        state.putFloat("Health", person.getHealth());
        state.putBoolean("NoAi", person.isNoAi()); state.putBoolean("Invisible", person.isInvisible());
        state.putBoolean("Invulnerable", person.isInvulnerable());
        state.putString("Move", person.getVillagerBrain().getMoveState().name());
        person.getBrain().getMemoryInternal(MemoryModuleTypeMCA.PLAYER_FOLLOWING)
                .ifPresent(player -> state.putString("Leader", player.getUUID().toString()));
        war.participants.put(person.getUUID(), state); setDirty();
    }
    public static boolean simulateDeath(VillagerEntityMCA person) {
        if (!(person.level() instanceof ServerLevel world)) return false;
        var manager = get(world);
        War war = manager.wars.stream().filter(w -> w.fun && w.started && w.participants.containsKey(person.getUUID())).findFirst().orElse(null);
        if (war == null) return false;
        person.setHealth(1); person.addTag(OUT); person.setNoAi(true); person.setInvisible(true); person.setInvulnerable(true);
        person.stopUsingItem(); person.getNavigation().stop(); person.setTarget(null);
        person.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        person.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        person.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); person.clearFire();
        manager.setDirty(); return true;
    }
    public static void tickParticipant(VillagerEntityMCA person) {
        if (!(person.level() instanceof ServerLevel world)) return;
        var manager = get(world);
        CompoundTag restore = manager.returns.remove(person.getUUID());
        if (restore != null) { manager.restore(person, restore); manager.setDirty(); }
        if (knockedOut(person)) {
            person.setDeltaMovement(0,person.getDeltaMovement().y,0); person.clearFire();
            person.setHealth(Math.max(1, person.getHealth())); return;
        }
        int home = person.getResidency().getHomeVillage().map(Village::getId).orElse(-1);
        for (War war : manager.wars) if (war.fun && war.started && war.involves(home)) manager.capture(war, person);
    }
    private void restoreFun(War war) {
        if (!war.fun) return;
        war.participants.forEach((id, state) -> {
            if (world.getEntity(id) instanceof VillagerEntityMCA person) restore(person, state);
            else if (world.getEntity(id) instanceof net.minecraft.world.entity.animal.golem.IronGolem golem) restoreGolem(golem,state);
            else returns.put(id, state);
        });
    }
    private void restore(VillagerEntityMCA person, CompoundTag state) {
        person.removeTag(OUT); person.setNoAi(state.getBooleanOr("NoAi", false));
        person.setInvisible(state.getBooleanOr("Invisible", false)); person.setInvulnerable(state.getBooleanOr("Invulnerable", false));
        person.setHealth(Math.max(1, state.getFloatOr("Health", person.getMaxHealth())));
        person.clearFire(); person.fallDistance = 0; person.stopUsingItem(); person.stopRiding();
        if (person.isSleeping()) person.stopSleeping();
        person.getNavigation().stop(); person.setTarget(null);
        person.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        person.getBrain().eraseMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY);
        person.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        person.teleportTo(state.getDoubleOr("X", person.getX()), state.getDoubleOr("Y", person.getY()), state.getDoubleOr("Z", person.getZ()));
        person.setYRot(state.getFloatOr("Yaw", 0)); person.setXRot(state.getFloatOr("Pitch", 0));
        person.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        if (!com.ancientkira.mca.entity.ai.brain.VillagerTasksMCA.hasMovementOrder(person))
        try { person.getVillagerBrain().setMoveState(com.ancientkira.mca.entity.ai.MoveState.valueOf(state.getStringOr("Move", "MOVE")),
                state.contains("Leader") ? world.getServer().getPlayerList().getPlayer(UUID.fromString(state.getStringOr("Leader", ""))) : null); }
        catch (IllegalArgumentException ignored) {}
    }
    private void captureGolem(War war, net.minecraft.world.entity.animal.golem.IronGolem golem) {
        if(war.participants.containsKey(golem.getUUID()))return;
        CompoundTag state=new CompoundTag();state.putString("Id",golem.getUUID().toString());state.putBoolean("Golem",true);
        state.putDouble("X",golem.getX());state.putDouble("Y",golem.getY());state.putDouble("Z",golem.getZ());
        state.putFloat("Health",golem.getHealth());state.putBoolean("NoAi",golem.isNoAi());
        state.putBoolean("Invisible",golem.isInvisible());state.putBoolean("Invulnerable",golem.isInvulnerable());
        war.participants.put(golem.getUUID(),state);setDirty();
    }
    public static boolean simulateGolemDeath(net.minecraft.world.entity.animal.golem.IronGolem golem) {
        if(!(golem.level() instanceof ServerLevel level))return false;
        var manager=get(level);
        if(manager.wars.stream().noneMatch(w->w.fun&&w.started&&w.participants.containsKey(golem.getUUID())))return false;
        golem.setHealth(1);golem.setNoAi(true);golem.setInvisible(true);golem.setInvulnerable(true);golem.addTag(OUT);
        golem.getNavigation().stop();golem.setTarget(null);manager.setDirty();return true;
    }
    public static void recordGolemDeath(IronGolem golem) {
        if(!(golem.level() instanceof ServerLevel world))return;
        var manager=get(world);
        for(War war:manager.wars) {
            boolean changed=war.firstDefenders.remove(golem.getUUID());
            changed=war.secondDefenders.remove(golem.getUUID()) || changed;
            changed=war.golems.remove(golem.getUUID()) || changed;
            if(changed)manager.setDirty();
        }
    }
    public static void tickGolem(net.minecraft.world.entity.animal.golem.IronGolem golem) {
        if(!(golem.level() instanceof ServerLevel level))return;
        var manager=get(level);var state=manager.returns.remove(golem.getUUID());
        if(state!=null){manager.restoreGolem(golem,state);manager.setDirty();}
        if(!golem.isAlive() || GolemAllies.raider(golem))return;
        for(War war:manager.wars) if(war.started) {
            int own=side(war,golem);
            if(own!=0) {
                if((own==1?war.firstDefenders:war.secondDefenders).add(golem.getUUID()))manager.setDirty();
                if(war.golems.add(golem.getUUID()))manager.setDirty();
                if(war.fun)manager.captureGolem(war,golem);
            }
        }
    }
    private void restoreGolem(net.minecraft.world.entity.animal.golem.IronGolem golem,CompoundTag state) {
        golem.removeTag(OUT);golem.setNoAi(state.getBooleanOr("NoAi",false));golem.setInvisible(state.getBooleanOr("Invisible",false));
        golem.setInvulnerable(state.getBooleanOr("Invulnerable",false));golem.setHealth(Math.max(1,state.getFloatOr("Health",golem.getMaxHealth())));
        golem.clearFire();golem.fallDistance=0;golem.getNavigation().stop();golem.setTarget(null);golem.stopBeingAngry();
        golem.teleportTo(state.getDoubleOr("X",golem.getX()),state.getDoubleOr("Y",golem.getY()),state.getDoubleOr("Z",golem.getZ()));
        golem.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }
    private static final class War {
        final int first, second;
        final long starts;
        boolean started, independence, fun;
        final Map<UUID, CompoundTag> participants = new HashMap<>();
        final Set<UUID> firstDefenders = new HashSet<>(), secondDefenders = new HashSet<>(), golems = new HashSet<>();
        War(int first, int second, long starts) { this.first = first; this.second = second; this.starts = starts; }
        boolean involves(int id) { return first == id || second == id; }
    }
}
