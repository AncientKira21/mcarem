package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import java.util.Map;

public final class DroppedEmeraldTrading {
    private static final Map<VillagerEntityMCA, Long> NEXT = new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private DroppedEmeraldTrading() { }
    public static void tick(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel world) || villager.tickCount % 10 != 0
                || !villager.isAlive() || villager.isKidnapped() || villager.isNoAi()
                || !villager.canTradeWithProfession() || villager.getTradingPlayer() != null
                || villager.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET)
                || world.getGameTime() < NEXT.getOrDefault(villager, 0L)) return;
        for (var emerald : world.getEntitiesOfClass(ItemEntity.class, villager.getBoundingBox().inflate(1.5),
                item -> item.isAlive() && item.getAge() >= 10 && item.getItem().is(Items.EMERALD))) {
            if (!(emerald.getOwner() instanceof ServerPlayer player) || player.level() != world
                    || player.isSpectator() || player.distanceToSqr(villager) > 256) continue;
            var offers = villager.getOffers().stream().filter(offer -> !offer.isOutOfStock()
                    && offer.getCostB().isEmpty() && offer.getCostA().is(Items.EMERALD)
                    && !offer.getResult().isEmpty() && offer.satisfiedBy(emerald.getItem(), ItemStack.EMPTY)).toList();
            if (offers.isEmpty()) continue;
            var offer = offers.get(world.getRandom().nextInt(offers.size()));
            ItemStack result = offer.getResult().copy();
            var returned = new ItemEntity(world, villager.getX(), villager.getEyeY() - 0.3, villager.getZ(), result);
            returned.setDeltaMovement(player.position().subtract(villager.position()).normalize().scale(0.25).add(0, 0.2, 0));
            returned.setPickUpDelay(10);
            returned.setThrower(villager);
            returned.setTarget(player.getUUID());
            if (!world.addFreshEntity(returned)) return;
            int cost = offer.getCostA().getCount();
            if (!offer.take(emerald.getItem(), ItemStack.EMPTY)) { returned.discard(); return; }
            villager.take(emerald, cost);
            if (emerald.getItem().isEmpty()) emerald.discard();
            else emerald.setItem(emerald.getItem().copy());
            villager.setTradingPlayer(player);
            try { villager.notifyTrade(offer); } finally { villager.setTradingPlayer(null); }
            NEXT.put(villager, world.getGameTime() + 40);
            return;
        }
    }
}
