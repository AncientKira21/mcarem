package com.ancientkira.mca.item;

import com.ancientkira.mca.server.Corpses;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;

/** Ordinary item placement: survival consumes one item; Creative can place copies. */
public final class CorpseItem extends Item {
    public CorpseItem(Properties properties) { super(properties); }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() != net.minecraft.core.Direction.UP) return InteractionResult.FAIL;
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        return Corpses.place(level, context.getClickedPos().relative(context.getClickedFace()), context.getItemInHand(), context.getPlayer())
                ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }
    @Override public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        Corpses.tickCarried(stack, level, entity);
    }
}
