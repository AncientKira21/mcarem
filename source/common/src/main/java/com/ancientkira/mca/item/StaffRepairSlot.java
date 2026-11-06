package com.ancientkira.mca.item;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Delegate native/loader slot behavior, retaining excess repair material after a custom repair. */
public final class StaffRepairSlot extends Slot {
    private final Slot original;
    private final Container repairSlots;
    private final boolean input;
    public StaffRepairSlot(Slot original,Container repairSlots,boolean input){
        super(original.container,original.getContainerSlot(),original.x,original.y);
        this.original=original;this.repairSlots=repairSlots;this.input=input;index=original.index;
    }
    @Override public boolean mayPlace(ItemStack stack){
        return original.mayPlace(stack) || input && stack.is(Items.NETHER_STAR);
    }
    @Override public boolean mayPickup(Player player){return original.mayPickup(player);}
    @Override public void onTake(Player player,ItemStack output){
        if(input){original.onTake(player,output);return;}
        int star=StaffRepairs.starSlot(repairSlots.getItem(0),repairSlots.getItem(1));
        ItemStack remaining=star<0?ItemStack.EMPTY:repairSlots.getItem(star).copyWithCount(repairSlots.getItem(star).getCount()-1);
        // Retain native disenchanting XP, sound and loader events. Vanilla clears both inputs.
        original.onTake(player,output);
        if(star>=0 && repairSlots.getItem(0).isEmpty() && repairSlots.getItem(1).isEmpty())
            repairSlots.setItem(star,remaining);
    }
}
