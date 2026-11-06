package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.MoveState;
import com.ancientkira.mca.entity.ai.Chore;
import com.ancientkira.mca.util.WorldUtils;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Villager-owned mounts and owner-approved, single-errand horse loans. */
public final class HorseHelp extends SavedData {
    private record Request(UUID owner, UUID horse, long expires, java.lang.ref.WeakReference<MinecraftServer> server) {}
    private static final Map<VillagerEntityMCA, Long> ASK_AFTER = new WeakHashMap<>();
    private static final Map<UUID, Request> REQUESTS = new HashMap<>();
    private static final class Loan {
        UUID horse, owner;
        GlobalPos origin, last;
        long issued, lastThreat;
        boolean returning;
    }
    private final Map<UUID, UUID> owned = new HashMap<>();
    private final Map<UUID, Loan> loans = new HashMap<>();
    private HorseHelp(ServerLevel level) {}
    private HorseHelp(ServerLevel level, CompoundTag data) {
        var mounts = data.getCompoundOrEmpty("Owned");
        for (String key : mounts.keySet()) try { owned.put(UUID.fromString(key), UUID.fromString(mounts.getStringOr(key,""))); } catch (IllegalArgumentException ignored) {}
        var saved = data.getCompoundOrEmpty("Loans");
        for (String key : saved.keySet()) try {
            var n = saved.getCompoundOrEmpty(key); var loan = new Loan();
            loan.horse = UUID.fromString(n.getStringOr("Horse", "")); loan.owner = UUID.fromString(n.getStringOr("Owner", ""));
            loan.origin = n.read("Origin",GlobalPos.CODEC).orElseThrow(); loan.last = n.read("Last",GlobalPos.CODEC).orElseThrow();
            loan.issued = n.getLongOr("Issued",0); loan.lastThreat = n.getLongOr("Threat",loan.issued); loan.returning = n.getBooleanOr("Returning",false);
            loans.put(UUID.fromString(key),loan);
        } catch (IllegalArgumentException | NoSuchElementException ignored) {}
    }
    public CompoundTag save(CompoundTag data, HolderLookup.Provider provider) {
        var mounts = new CompoundTag(); owned.forEach((r,h)->mounts.putString(r.toString(),h.toString())); data.put("Owned",mounts);
        var saved = new CompoundTag(); loans.forEach((r,l)->{
            var n = new CompoundTag(); n.putString("Horse",l.horse.toString());n.putString("Owner",l.owner.toString());
            n.store("Origin",GlobalPos.CODEC,l.origin);n.store("Last",GlobalPos.CODEC,l.last);
            n.putLong("Issued",l.issued);n.putLong("Threat",l.lastThreat);n.putBoolean("Returning",l.returning);saved.put(r.toString(),n);
        }); data.put("Loans",saved);return data;
    }
    private static HorseHelp get(MinecraftServer server) { return WorldUtils.loadData(server.overworld(),(tag,provider)->new HorseHelp(server.overworld(),tag),HorseHelp::new,"mca_horse_help"); }
    private static long time(ServerLevel level) { return level.getServer().overworld().getGameTime(); }
    private static boolean ownedBy(Horse horse, UUID owner) { return horse.getOwnerReference()!=null && horse.getOwnerReference().getUUID().equals(owner); }
    private static boolean available(Horse horse) { return horse.isAlive() && !horse.isBaby() && !horse.isVehicle() && !horse.isLeashed() && !horse.isPassenger(); }
    private static LivingEntity threat(VillagerEntityMCA person) {
        if (person.getTarget()!=null && person.getTarget().isAlive()) return person.getTarget();
        var attack=person.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET);
        if(attack!=null && attack.filter(LivingEntity::isAlive).isPresent())return attack.get();
        if(person.getVillagerBrain().isPanicking()) {
            var hurt=person.getBrain().getMemoryInternal(MemoryModuleType.HURT_BY_ENTITY);
            if(hurt!=null)return hurt.filter(LivingEntity::isAlive).orElse(null);
        } return null;
    }
    public static boolean tooFast(VillagerEntityMCA person, LivingEntity target) {
        return target!=null && target.getAttribute(Attributes.MOVEMENT_SPEED)!=null && target.level()==person.level() && person.distanceToSqr(target)>16
                && target.getAttributeValue(Attributes.MOVEMENT_SPEED)>person.getAttributeValue(Attributes.MOVEMENT_SPEED)*1.15;
    }
    private static boolean hasSaddle(VillagerEntityMCA person) { return saddleSlot(person)>=0; }
    private static int saddleSlot(VillagerEntityMCA person) {
        for(int i=0;i<person.getInventory().getContainerSize();i++)if(person.getInventory().getItem(i).is(Items.SADDLE))return i;
        return -1;
    }
    private static boolean saddle(VillagerEntityMCA person,Horse horse) {
        if(horse.isSaddled())return true;
        int slot=saddleSlot(person);if(slot<0 || !horse.canUseSlot(EquipmentSlot.SADDLE))return false;
        horse.setItemSlot(EquipmentSlot.SADDLE,person.getInventory().getItem(slot).split(1));person.getInventory().setChanged();return horse.isSaddled();
    }
    public static void tick(VillagerEntityMCA person) {
        if(!(person.level() instanceof ServerLevel level) || person.tickCount%20!=0 || !person.isAlive() || person.isBaby()
                || person.isSleeping() || person.isNoAi() || person.isKidnapped() || PortalPursuit.active(person)
                || person.getVillagerBrain().getMoveState()!=MoveState.MOVE || person.getInteractions().getInteractingPlayer().isPresent())return;
        var data=get(level.getServer());var loan=data.loans.get(person.getUUID());var enemy=threat(person);
        if(loan!=null)return; // Loan movement and return are coordinated by the server tick.
        if(person.getVehicle() instanceof Horse horse) {
            if(!horse.isTamed() && horse.getOwnerReference()==null && horse.getFirstPassenger()==person) {
                if(!hasSaddle(person)) { person.stopRiding();return; }
                horse.modifyTemper(5);
                if(person.getRandom().nextInt(horse.getMaxTemper())<horse.getTemper()) {
                    horse.setOwner(person);horse.setTamed(true);saddle(person,horse);data.owned.put(person.getUUID(),horse.getUUID());data.setDirty();
                    level.broadcastEntityEvent(horse,(byte)7);
                } else if(person.getRandom().nextInt(4)==0) { person.stopRiding();level.broadcastEntityEvent(horse,(byte)6); }
            } else if(ownedBy(horse,person.getUUID()))saddle(person,horse);
            return;
        }
        if(person.isPassenger())return;
        UUID remembered=data.owned.get(person.getUUID());
        Entity rememberedHorse=remembered==null?null:find(level.getServer(),remembered);
        if(rememberedHorse instanceof Horse h && (!h.isAlive() || !ownedBy(h,person.getUUID()))) { data.owned.remove(person.getUUID());data.setDirty(); }

        // Reuse the villager's own horse before requesting somebody else's.
        var own=level.getEntitiesOfClass(Horse.class,person.getBoundingBox().inflate(24),h->available(h)&&ownedBy(h,person.getUUID()))
                .stream().min(Comparator.comparingDouble(person::distanceToSqr)).orElse(null);
        if(own!=null && enemy!=null) { approach(person,own);return; }
        if(tooFast(person,enemy) && !data.owned.containsKey(person.getUUID())) { ask(person,level,data);return; }
        // Peaceful taming requires a real saddle in inventory and does not interrupt work or another walk order.
        if(enemy!=null || data.owned.containsKey(person.getUUID()) || !hasSaddle(person)
                || person.getVillagerBrain().getCurrentJob()!=Chore.NONE || person.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET))return;
        var wild=level.getEntitiesOfClass(Horse.class,person.getBoundingBox().inflate(12),h->available(h)&&!h.isTamed()&&h.getOwnerReference()==null)
                .stream().min(Comparator.comparingDouble(person::distanceToSqr)).orElse(null);
        if(wild!=null)approach(person,wild);
    }
    public static void horseDied(Horse horse) {
        if (!(horse.level() instanceof ServerLevel level)) return;
        var data=get(level.getServer());
        if(data.owned.entrySet().removeIf(e->e.getValue().equals(horse.getUUID())))data.setDirty();
    }
    private static void approach(VillagerEntityMCA person,Horse horse) {
        if(person.distanceToSqr(horse)<=4) {
            if(!horse.isTamed() || saddle(person,horse))person.startRiding(horse,false,true);
        } else {
            person.getBrain().setMemory(MemoryModuleType.WALK_TARGET,new net.minecraft.world.entity.ai.memory.WalkTarget(new net.minecraft.world.entity.ai.behavior.EntityTracker(horse,false),.8F,1));
            // A loaned or villager-owned mount can meet its rider instead of chasing a moving combat path.
            if(horse.isTamed())horse.getNavigation().moveTo(person,1.0);
        }
    }
    private static void ask(VillagerEntityMCA person,ServerLevel level,HorseHelp data) {
        long now=time(level); if(now<ASK_AFTER.getOrDefault(person,0L))return;
        REQUESTS.entrySet().removeIf(e->e.getValue().expires()<now || e.getValue().server().get()==null);
        Horse horse=level.getEntitiesOfClass(Horse.class,person.getBoundingBox().inflate(24),h->available(h)&&h.isTamed()&&h.getOwnerReference()!=null&&(h.isSaddled()||hasSaddle(person)))
                .stream().filter(h->level.getServer().getPlayerList().getPlayer(h.getOwnerReference().getUUID())!=null)
                .filter(h->data.loans.values().stream().noneMatch(l->l.horse.equals(h.getUUID())))
                .filter(h->REQUESTS.values().stream().noneMatch(r->r.server().get()==level.getServer() && r.horse().equals(h.getUUID()) && r.expires()>=now))
                .min(Comparator.comparingDouble(person::distanceToSqr)).orElse(null);
        if(horse==null)return;
        ServerPlayer owner=level.getServer().getPlayerList().getPlayer(horse.getOwnerReference().getUUID());
        if(owner==null || owner.level()!=level || owner.distanceToSqr(person)>64*64)return;
        ASK_AFTER.put(person,now+1200);
        REQUESTS.put(person.getUUID(),new Request(owner.getUUID(),horse.getUUID(),now+1200,new java.lang.ref.WeakReference<>(level.getServer())));
        owner.sendSystemMessage(Component.translatable("mca.horse.borrow.request",person.getName(),horse.getName())
                .append(" ").append(answer(person,true)).append(" ").append(answer(person,false)));
    }
    private static Component answer(VillagerEntityMCA person,boolean allow) {
        return Component.translatable("mca.horse.borrow."+(allow?"allow":"deny")).withStyle(style->style
                .withColor(allow?net.minecraft.ChatFormatting.GREEN:net.minecraft.ChatFormatting.RED)
                .withClickEvent(new ClickEvent.RunCommand("/mca horseLoan "+(allow?"yes ":"no ")+person.getUUID())));
    }
    public static boolean permissionMatches(UUID intendedOwner, UUID responder, long expires, long now) {
        return intendedOwner != null && intendedOwner.equals(responder) && now <= expires;
    }
    public static int respond(ServerPlayer player,UUID villager,boolean allow) {
        Request request=REQUESTS.get(villager);
        if(request==null || request.server().get()!=player.level().getServer() || !permissionMatches(request.owner(),player.getUUID(),request.expires(),time(player.level())))return 0;
        REQUESTS.remove(villager);if(!allow)return 1;
        if(!(player.level().getEntity(villager) instanceof VillagerEntityMCA person)
                || !(player.level().getEntity(request.horse()) instanceof Horse horse)
                || !available(horse) || !horse.isTamed() || !ownedBy(horse,player.getUUID()) || person.isPassenger()
                || person.isKidnapped() || person.isBaby() || person.getVillagerBrain().getMoveState()!=MoveState.MOVE
                || person.distanceToSqr(horse)>32*32 || player.distanceToSqr(person)>64*64 || !tooFast(person,threat(person)))return 0;
        var data=get(player.level().getServer());
        if(data.loans.containsKey(villager) || data.loans.values().stream().anyMatch(l->l.horse.equals(horse.getUUID())))return 0;
        if(!saddle(person,horse))return 0;
        var loan=new Loan();loan.owner=player.getUUID();loan.horse=horse.getUUID();loan.origin=loan.last=GlobalPos.of(horse.level().dimension(),horse.blockPosition());loan.issued=loan.lastThreat=time(player.level());
        data.loans.put(villager,loan);data.setDirty();approach(person,horse);return 1;
    }
    private static Entity find(MinecraftServer server,UUID id) {
        for(var world:server.getAllLevels()){var entity=world.getEntity(id);if(entity!=null)return entity;}return null;
    }
    public static void serverTick(MinecraftServer server) {
        long now=server.overworld().getGameTime();if(now%20!=0)return;
        var data=get(server);
        for(var entry:new ArrayList<>(data.loans.entrySet())) {
            var loan=entry.getValue();var world=server.getLevel(loan.last.dimension());if(world==null)continue;
            world.getChunkSource().addTicketWithRadius(TicketType.PORTAL,ChunkPos.containing(loan.last.pos()),3);
            if(now-loan.issued>12000){Entity borrower=find(server,entry.getKey());if(borrower!=null && borrower.getVehicle()!=null && borrower.getVehicle().getUUID().equals(loan.horse))borrower.stopRiding();data.loans.remove(entry.getKey());data.setDirty();continue;}
            Entity found=find(server,loan.horse);if(!(found instanceof Horse horse))continue;
            if(!horse.isAlive() || !ownedBy(horse,loan.owner)){data.loans.remove(entry.getKey());data.setDirty();continue;}
            if(horse.getPassengers().stream().anyMatch(p -> !p.getUUID().equals(entry.getKey()))) {
                Entity borrower=find(server,entry.getKey());if(borrower!=null && borrower.getVehicle()==horse)borrower.stopRiding();
                data.loans.remove(entry.getKey());data.setDirty();continue;
            }
            loan.last=GlobalPos.of(horse.level().dimension(),horse.blockPosition());
            var rider=find(server,entry.getKey()) instanceof VillagerEntityMCA p?p:null;
            LivingEntity enemy=rider==null?null:threat(rider);
            if(enemy!=null)loan.lastThreat=now;
            if(rider==null || !rider.isAlive() || rider.isKidnapped() || rider.getVillagerBrain().getMoveState()!=MoveState.MOVE
                    || now-loan.lastThreat>60 || now-loan.issued>6000)loan.returning=true;
            if(!loan.returning && rider!=null && rider.level()==horse.level()) {
                if(!horse.isVehicle() && !horse.isLeashed())approach(rider,horse);
                if(horse.getControllingPassenger()==rider && enemy!=null) {
                    if(rider.getVillagerBrain().isPanicking()) {
                        var away=net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(horse,16,7,enemy.position());
                        if(away!=null)horse.getNavigation().moveTo(away.x,away.y,away.z,1.2);
                    } else horse.getNavigation().moveTo(enemy,1.2);
                }
            } else if(loan.returning) {
                // Never change the lender's ownership, and never move a horse they have mounted or leashed.
                if(horse.isLeashed() || horse.isVehicle() && (rider==null || horse.getFirstPassenger()!=rider)) {
                    data.loans.remove(entry.getKey());data.setDirty();continue;
                }
                if(horse.level().dimension()!=loan.origin.dimension())continue;
                world.getChunkSource().addTicketWithRadius(TicketType.PORTAL,ChunkPos.containing(loan.origin.pos()),3);
                if(horse.distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(loan.origin.pos()))<=9) {
                    if(rider!=null && rider.getVehicle()==horse)rider.stopRiding();horse.getNavigation().stop();data.loans.remove(entry.getKey());
                } else horse.getNavigation().moveTo(loan.origin.pos().getX()+.5,loan.origin.pos().getY(),loan.origin.pos().getZ()+.5,1.0);
            }
            data.setDirty();
        }
    }
}
