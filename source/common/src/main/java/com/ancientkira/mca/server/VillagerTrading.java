package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.Chore;
import com.ancientkira.mca.entity.ai.relationship.AgeState;
import com.ancientkira.mca.registry.ProfessionsMCA;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.Comparator;
import java.util.Map;

/** Nearby villagers buy useful equipment with their own emeralds and real merchant stock. */
public final class VillagerTrading {
    private static final Map<VillagerEntityMCA, Long> NEXT = new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private static final Map<VillagerEntityMCA, Visit> VISITS = new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private static final class Visit {
        final VillagerEntityMCA seller;
        final long expires;
        WalkTarget walk;
        Visit(VillagerEntityMCA seller, long now) { this.seller = seller; expires = now + 600; }
    }
    private VillagerTrading() { }

    private static boolean available(VillagerEntityMCA person) {
        return person.isAlive() && !person.isRemoved() && !person.isNoAi() && !person.isKidnapped()
                && !person.isSleeping() && !person.isTrading() && !person.getVillagerBrain().isPanicking()
                && !ProfessionsMCA.isRaider(person.getProfession())
                && !person.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && person.getInteractions().getInteractingPlayer().isEmpty();
    }

    public static void tick(VillagerEntityMCA buyer) {
        if (!(buyer.level() instanceof ServerLevel world) || buyer.tickCount % 20 != 0) return;
        Visit visit = VISITS.get(buyer);
        if (!available(buyer) || buyer.getAgeState() == AgeState.BABY
                || buyer.getVillagerBrain().getMoveState() != com.ancientkira.mca.entity.ai.MoveState.MOVE
                || buyer.getVillagerBrain().getCurrentJob() != Chore.NONE
                || ClericAid.isHelping(buyer) || CaptiveRescue.isHelping(buyer) || VillageWarfare.isFighting(buyer)) {
            if (visit != null) finish(buyer);
            return;
        }
        long now = world.getGameTime();
        if (visit == null) {
            if (now < NEXT.getOrDefault(buyer, 0L)) return;
            NEXT.put(buyer, now + 200);
            if (buyer.getInventory().countItem(Items.EMERALD) == 0) return;
            var seller = world.getEntitiesOfClass(VillagerEntityMCA.class, buyer.getBoundingBox().inflate(16),
                    candidate -> candidate != buyer && available(candidate) && candidate.canTradeWithProfession()
                            && candidate.distanceToSqr(buyer) <= 16 * 16 && buyer.hasLineOfSight(candidate))
                    .stream().sorted(Comparator.comparingDouble(buyer::distanceToSqr))
                    .filter(candidate -> findOffer(buyer, candidate) != null).findFirst().orElse(null);
            if (seller == null) return;
            visit = new Visit(seller, now);
            VISITS.put(buyer, visit);
        }
        VillagerEntityMCA seller = visit.seller;
        if (!available(seller) || seller.level() != world || !seller.canTradeWithProfession()
                || now >= visit.expires || buyer.distanceToSqr(seller) > 24 * 24) {
            finish(buyer);
            return;
        }
        if (buyer.distanceToSqr(seller) > 3 * 3 || !buyer.hasLineOfSight(seller)) {
            buyer.getBrain().setActiveActivityIfPossible(Activity.IDLE);
            visit.walk = new WalkTarget(new EntityTracker(seller, false), 0.5F, 2);
            buyer.getBrain().setMemory(MemoryModuleType.WALK_TARGET, visit.walk);
            return;
        }
        MerchantOffer offer = findOffer(buyer, seller);
        if (offer != null && purchase(buyer, seller, offer)) NEXT.put(buyer, now + 1200);
        finish(buyer);
    }

    private static MerchantOffer findOffer(VillagerEntityMCA buyer, VillagerEntityMCA seller) {
        int emeralds = buyer.getInventory().countItem(Items.EMERALD);
        return seller.getOffers().stream().filter(offer -> !offer.isOutOfStock() && offer.getCostB().isEmpty()
                && offer.getCostA().is(Items.EMERALD) && offer.getCostA().getCount() > 0
                && offer.getCostA().getCount() <= emeralds
                && offer.satisfiedBy(new ItemStack(Items.EMERALD, offer.getCostA().getCount()), ItemStack.EMPTY)
                && useful(buyer, offer.getResult()))
                .min(Comparator.comparingInt(offer -> offer.getCostA().getCount())).orElse(null);
    }

    private static boolean useful(VillagerEntityMCA buyer, ItemStack result) {
        if (result.isEmpty()) return false;
        var equipment = result.get(DataComponents.EQUIPPABLE);
        if (equipment != null) {
            EquipmentSlot slot = equipment.slot();
            if (slot != EquipmentSlot.HEAD && slot != EquipmentSlot.CHEST && slot != EquipmentSlot.LEGS && slot != EquipmentSlot.FEET) return false;
            int best = buyer.getItemBySlot(slot).getMaxDamage();
            for (int i = 0; i < buyer.getInventory().getContainerSize(); i++) {
                ItemStack item = buyer.getInventory().getItem(i);
                var worn = item.get(DataComponents.EQUIPPABLE);
                if (worn != null && worn.slot() == slot) best = Math.max(best, item.getMaxDamage());
            }
            return result.getMaxDamage() > best;
        }
        if (buyer.getProfession() == ProfessionsMCA.MINER && result.is(ItemTags.PICKAXES)) {
            int best = new ItemStack(Items.IRON_PICKAXE).getMaxDamage();
            for (int i = 0; i < buyer.getInventory().getContainerSize(); i++) {
                ItemStack item = buyer.getInventory().getItem(i);
                if (item.is(ItemTags.PICKAXES)) best = Math.max(best, item.getMaxDamage());
            }
            return result.getMaxDamage() > best;
        }
        return false;
    }

    private static SimpleContainer copy(SimpleContainer inventory) {
        SimpleContainer result = new SimpleContainer(inventory.getContainerSize());
        for (int i = 0; i < inventory.getContainerSize(); i++) result.setItem(i, inventory.getItem(i).copy());
        return result;
    }

    private static boolean purchase(VillagerEntityMCA buyer, VillagerEntityMCA seller, MerchantOffer offer) {
        ItemStack result = offer.getResult().copy();
        ItemStack payment = offer.getCostA().copy();
        if (!offer.satisfiedBy(payment, ItemStack.EMPTY)) return false;
        SimpleContainer buyerAfter = copy(buyer.getInventory()), sellerAfter = copy(seller.getInventory());
        if (buyerAfter.removeItemType(Items.EMERALD, payment.getCount()).getCount() != payment.getCount()
                || !buyerAfter.addItem(result.copy()).isEmpty() || !sellerAfter.addItem(payment.copy()).isEmpty()) return false;
        if (!offer.take(payment, ItemStack.EMPTY)) return false;
        for (int i = 0; i < buyerAfter.getContainerSize(); i++) buyer.getInventory().setItem(i, buyerAfter.getItem(i));
        for (int i = 0; i < sellerAfter.getContainerSize(); i++) seller.getInventory().setItem(i, sellerAfter.getItem(i));
        seller.notifyVillagerTrade(offer);
        var equipment = result.get(DataComponents.EQUIPPABLE);
        if (equipment != null) {
            buyer.getVillagerBrain().setArmorWear(true);
            buyer.setItemSlot(equipment.slot(), result.copy());
        }
        buyer.swing(buyer.getDominantHand());
        seller.swing(seller.getDominantHand());
        return true;
    }

    private static void finish(VillagerEntityMCA buyer) {
        Visit visit = VISITS.remove(buyer);
        if (visit != null && buyer.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null) == visit.walk) {
            buyer.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            buyer.getNavigation().stop();
            buyer.getBrain().updateActivityFromSchedule(buyer.level().environmentAttributes(), buyer.level().getGameTime(), buyer.position());
        }
    }
}
