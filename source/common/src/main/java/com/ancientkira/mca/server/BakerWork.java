package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.Chore;
import com.ancientkira.mca.registry.BlocksMCA;
import com.ancientkira.mca.registry.ProfessionsMCA;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Bakers turn supplied wheat into bread while working at their claimed table. */
public final class BakerWork {
    private BakerWork() { }

    public static void tick(VillagerEntityMCA baker) {
        if (baker.tickCount % 100 != 0 || baker.getProfession() != ProfessionsMCA.BAKER
                || !baker.isAlive() || baker.isBaby() || baker.isNoAi() || baker.isKidnapped()
                || baker.isTrading() || baker.getVillagerBrain().isPanicking()
                || baker.getVillagerBrain().getCurrentJob() != Chore.NONE
                || baker.getInteractions().getInteractingPlayer().isPresent()
                || baker.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                || !baker.getBrain().isActive(Activity.WORK)) return;
        var site = baker.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
        if (site == null || site.dimension() != baker.level().dimension()
                || baker.blockPosition().distSqr(site.pos()) > 9
                || !baker.level().getBlockState(site.pos()).is(BlocksMCA.BAKERS_TABLE)) return;
        var inventory = baker.getInventory();
        int wheat = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(Items.WHEAT)) wheat += stack.getCount();
        }
        ItemStack bread = new ItemStack(Items.BREAD);
        if (wheat < 3 || !inventory.canAddItem(bread)) return;
        int remaining = 3;
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(Items.WHEAT)) {
                int used = Math.min(remaining, stack.getCount());
                stack.shrink(used);
                remaining -= used;
            }
        }
        inventory.addItem(bread);
        inventory.setChanged();
    }
}
