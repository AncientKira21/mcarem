package com.ancientkira.mca.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

import java.util.function.Consumer;

public class StaffOfLifeItem extends TooltippedItem {
    public StaffOfLifeItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        InteractionResult result = ScytheItem.use(context, true);
        if (result == InteractionResult.SUCCESS) {
            if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
                context.getItemInHand().hurtAndBreak(1, serverPlayer.level(), serverPlayer, item -> {
                });
            }
            return result;
        }
        return result;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay, Consumer<Component> consumer, TooltipFlag flag) {
        int remaining = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
        var enchantments = stack.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        int unbreaking = enchantments.entrySet().stream()
                .filter(entry -> entry.getKey().is(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING))
                .mapToInt(entry -> entry.getIntValue()).max().orElse(0);
        if (unbreaking > 0) {
            long expectedUses = (long) remaining * (unbreaking + 1L);
            consumer.accept(Component.translatableWithFallback(getDescriptionId() + ".estimated_uses",
                    "Estimated uses left: %s", expectedUses));
        } else {
            consumer.accept(Component.translatable(getDescriptionId() + ".uses", remaining));
        }
        super.appendHoverText(stack, context, tooltipDisplay, consumer, flag);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

}
