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
    private final Map<Integer,Long> rebellionChecks=new HashMap<>();
    private final Set<Long> friendly = new HashSet<>();
    private final ServerLevel world;
    private VillageWarfare(ServerLevel world) { this.world = world; }
    private VillageWarfare(ServerLevel world, CompoundTag saved) {
        this(world);
        for(var tag:saved.getListOrEmpty("RebellionChecks"))if(tag instanceof CompoundTag entry)
            rebellionChecks.put(entry.getIntOr("Village",-1),entry.getLongOr("Next",0));
        for (var tag : saved.getListOrEmpty("Friendly")) {
            if (tag instanceof CompoundTag pair) friendly.add(pairKey(pair.getIntOr("First", -1), pair.getIntOr("Second", -1)));
        }
        for (var tag : saved.getListOrEmpty("Wars")) {
            if (!(tag instanceof CompoundTag data)) continue;
            War war = new War(data.getIntOr("First", -1), data.getIntOr("Second", -1), data.getLongOr("Starts", 0));
            war.started = data.getBooleanOr("Started", false);
            war.independence=data.getBooleanOr("Independence",false);
            readRoster(data, "FirstDefenders", war.firstDefenders);
            readRoster(data, "SecondDefenders", war.secondDefenders);
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
        ListTag checks=new ListTag();rebellionChecks.forEach((id,next)->{CompoundTag tag=new CompoundTag();tag.putInt("Village",id);tag.putLong("Next",next);checks.add(tag);});saved.put("RebellionChecks",checks);
        ListTag list = new ListTag();
        for (War war : wars) {
            CompoundTag data = new CompoundTag();
            data.putInt("First", war.first); data.putInt("Second", war.second);
            data.putLong("Starts", war.starts); data.putBoolean("Started", war.started);
            data.putBoolean("Independence",war.independence);
            data.put("FirstDefenders", writeRoster(war.firstDefenders));
            data.put("SecondDefenders", writeRoster(war.secondDefenders));
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
        Village second = VillageManager.get(world).getOrEmpty(targetId).orElse(null);
        if (minutes < 0 || minutes > 20 || second == null || first == second || isFriendly(first.getId(), targetId)
                || first.getCenter().distSqr(second.getCenter()) > NEARBY_RANGE * NEARBY_RANGE
                || wars.stream().anyMatch(war -> war.involves(first.getId()) || war.involves(targetId))) return false;
        War war = new War(first.getId(), targetId, world.getGameTime() + minutes * 1200L);
        wars.add(war); setDirty();
        announce(first, second, Component.translatable("mca.war.declared", first.getName(), second.getName(), minutes));
        if (minutes == 0) tick();
        return true;
    }
    public boolean cancel(Village village, int otherId) {
        War war = wars.stream().filter(w -> w.involves(village.getId()) && w.involves(otherId)).findFirst().orElse(null);
        if (war == null) return false;
        Village other = VillageManager.get(world).getOrEmpty(otherId).orElse(null);
        clearOrders(war); wars.remove(war); setDirty();
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
            if (!(entity instanceof VillagerEntityMCA person) || !person.isAlive() || !person.isGuard()
                    || opponents.contains(person.getUUID())) continue;
            var home = person.getResidency().getHomeVillage()
                    .or(() -> VillageManager.get(world).findNearestVillage(person));
            if (home.filter(current -> current.getId() == village.getId()).isPresent()) roster.add(person.getUUID());
        }
    }
    private boolean alive(UUID id) {
        if (world.getEntity(id) instanceof VillagerEntityMCA person) return person.isAlive() && person.isGuard();
        return FamilyTree.get(world).getOrEmpty(id).map(node -> !node.isDeceased()
                && (node.getProfession() == ProfessionsMCA.GUARD || node.getProfession() == ProfessionsMCA.ARCHER)).orElse(true);
    }
    public void tick() {
        automaticIndependence();
        if (world.getGameTime() % 20 != 0 && wars.stream().noneMatch(w -> !w.started && world.getGameTime() >= w.starts)) return;
        var manager = VillageManager.get(world);
        for (War war : List.copyOf(wars)) {
            Village first = manager.getOrEmpty(war.first).orElse(null), second = manager.getOrEmpty(war.second).orElse(null);
            if (first == null || second == null) { clearOrders(war); wars.remove(war); setDirty(); continue; }
            if (isFriendly(war.first, war.second)) { cancel(first, war.second); continue; }
            if (!war.started && world.getGameTime() < war.starts) continue;
            if (!war.started) {
                war.started = true;
                setDirty();
                announce(first, second, Component.translatable("mca.war.started", first.getName(), second.getName()));
                first.getResidents(world).stream().filter(VillagerEntityMCA::isGuard).forEach(VillageWarfare::mobilize);
                second.getResidents(world).stream().filter(VillagerEntityMCA::isGuard).forEach(VillageWarfare::mobilize);
            }
            int before = war.firstDefenders.size() + war.secondDefenders.size();
            repairTeams(war);
            recruit(first, war.firstDefenders, war.secondDefenders);
            recruit(second, war.secondDefenders, war.firstDefenders);
            if (before != war.firstDefenders.size() + war.secondDefenders.size()) setDirty();
            boolean firstAlive = war.firstDefenders.stream().anyMatch(this::alive);
            boolean secondAlive = war.secondDefenders.stream().anyMatch(this::alive);
            if (!firstAlive || !secondAlive) {
                clearOrders(war); wars.remove(war); setDirty();
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
                march(war.firstDefenders, war.secondDefenders, second);
                march(war.secondDefenders, war.firstDefenders, first);
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
                    || person.isKidnapped() || person.isNoAi()) continue;
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
                    || person.isNoAi() || person.isKidnapped()) continue;
            if (person.isSleeping() || person.getVillagerBrain().getMoveState() != com.ancientkira.mca.entity.ai.MoveState.MOVE
                    || person.getVillagerBrain().getCurrentJob() != com.ancientkira.mca.entity.ai.Chore.NONE) mobilize(person);
            person.getBrain().setActiveActivityIfPossible(Activity.IDLE);
            VillagerEntityMCA target = opponents.stream().map(world::getEntity).filter(VillagerEntityMCA.class::isInstance)
                    .map(VillagerEntityMCA.class::cast).filter(other -> other != person && !other.getUUID().equals(person.getUUID())
                            && other.isAlive() && other.isGuard() && !other.isKidnapped() && isEnemy(person, other))
                    .min(Comparator.comparingDouble(person::distanceToSqr)).orElse(null);
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
    public static boolean isEnemy(VillagerEntityMCA first, VillagerEntityMCA second) {
        if (first == second || first.getUUID().equals(second.getUUID()) || !first.isGuard() || !second.isGuard() || first.level() != second.level() || !(first.level() instanceof ServerLevel world)) return false;
        return get(world).wars.stream().anyMatch(war -> war.started &&
                (war.firstDefenders.contains(first.getUUID()) && !war.secondDefenders.contains(first.getUUID())
                        && war.secondDefenders.contains(second.getUUID()) && !war.firstDefenders.contains(second.getUUID())
                        || war.secondDefenders.contains(first.getUUID()) && !war.firstDefenders.contains(first.getUUID())
                        && war.firstDefenders.contains(second.getUUID()) && !war.secondDefenders.contains(second.getUUID())));
    }
    public static boolean isFighting(VillagerEntityMCA person) {
        return person.level() instanceof ServerLevel world && get(world).wars.stream().anyMatch(war -> war.started
                && (war.firstDefenders.contains(person.getUUID()) || war.secondDefenders.contains(person.getUUID())));
    }
    public static void tickCombat(VillagerEntityMCA person) {
        if (!(person.level() instanceof ServerLevel world) || person.tickCount % 10 != 0
                || !person.isAlive() || person.isNoAi() || person.isKidnapped() || !isFighting(person)
                || person.getHealth() <= person.getMaxHealth() * 0.25F) return;
        VillagerEntityMCA target = person.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET)
                .filter(VillagerEntityMCA.class::isInstance).map(VillagerEntityMCA.class::cast)
                .filter(enemy -> enemy.isAlive() && !enemy.isKidnapped() && isEnemy(person, enemy)).orElse(null);
        if (target == null) return;
        if (person.isSleeping()) person.stopSleeping();
        person.getLookControl().setLookAt(target, 30, 30);
        double distance = person.distanceToSqr(target);
        boolean ranged = person.getProfession() == ProfessionsMCA.ARCHER
                && com.ancientkira.mca.entity.ai.RangedWeaponHelper.isHoldingSupportedWeapon(person);
        double range = ranged ? 15 : person.getBbWidth() + target.getBbWidth() + 2;
        if (distance > range * range || !person.hasLineOfSight(target)) {
            person.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new EntityTracker(target, false), 0.75F, ranged ? 10 : 2));
            person.getNavigation().moveTo(target, 0.75);
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
    private static final class War {
        final int first, second;
        final long starts;
        boolean started, independence;
        final Set<UUID> firstDefenders = new HashSet<>(), secondDefenders = new HashSet<>();
        War(int first, int second, long starts) { this.first = first; this.second = second; this.starts = starts; }
        boolean involves(int id) { return first == id || second == id; }
    }
}
