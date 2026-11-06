package com.ancientkira.mca.mixin;

import com.ancientkira.mca.item.StaffRepairs;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(AnvilMenu.class)
public abstract class MixinStaffAnvil {
    /** Both material-repair divisions; XP, repair history, naming and other recipes stay native. */
    @ModifyConstant(method="createResult()V",constant=@Constant(intValue=4),require=0)
    private int mca$rechargeWithOneStar(int vanilla){
        AnvilMenu menu=(AnvilMenu)(Object)this;
        return StaffRepairs.anvilDivisor(vanilla,menu.getSlot(0).getItem(),menu.getSlot(1).getItem());
    }
    /** Newer NeoForge moves the recipe body out of its final event-dispatching wrapper. */
    @Dynamic("NeoForge AnvilMenu recipe implementation")
    @ModifyConstant(method="createResultInternal()V",constant=@Constant(intValue=4),require=0,remap=false)
    private int mca$rechargeWithOneStarNeoForge(int vanilla){
        AnvilMenu menu=(AnvilMenu)(Object)this;
        return StaffRepairs.anvilDivisor(vanilla,menu.getSlot(0).getItem(),menu.getSlot(1).getItem());
    }
}
