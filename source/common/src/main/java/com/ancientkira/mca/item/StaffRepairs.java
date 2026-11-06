package com.ancientkira.mca.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** One Nether Star replenishes the staff, matching the star cost of crafting a fresh one. */
public final class StaffRepairs {
    private StaffRepairs() {}
    public static int starSlot(ItemStack first,ItemStack second){
        if(staff(first) && second.is(Items.NETHER_STAR) && !second.isEmpty())return 1;
        if(staff(second) && first.is(Items.NETHER_STAR) && !first.isEmpty())return 0;
        return -1;
    }
    private static boolean staff(ItemStack stack){
        return stack.getItem() instanceof StaffOfLifeItem && stack.getCount()==1 && stack.isDamaged();
    }
    public static int anvilDivisor(int vanilla,ItemStack first,ItemStack second){
        return starSlot(first,second)==1?1:vanilla;
    }
    public static ItemStack repair(ItemStack first,ItemStack second){
        int star=starSlot(first,second);if(star<0)return ItemStack.EMPTY;
        ItemStack result=(star==1?first:second).copy();
        result.setDamageValue(0);
        return result;
    }
}
