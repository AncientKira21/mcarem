package com.ancientkira.mca.mixin;

import com.ancientkira.mca.item.StaffRepairs;
import com.ancientkira.mca.item.StaffRepairSlot;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GrindstoneMenu.class)
public abstract class MixinStaffGrindstone {
    @Shadow @Final private Container repairSlots;
    @Shadow private ItemStack removeNonCursesFrom(ItemStack stack){throw new AssertionError();}

    @Inject(method="<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",at=@At("RETURN"))
    private void mca$repairSlots(int id,Inventory inventory,ContainerLevelAccess access,CallbackInfo ci){
        GrindstoneMenu menu=(GrindstoneMenu)(Object)this;
        for(int i=0;i<3;i++)menu.slots.set(i,new StaffRepairSlot(menu.slots.get(i),repairSlots,i<2));
    }
    @Inject(method="computeResult",at=@At("HEAD"),cancellable=true)
    private void mca$repairWithStar(ItemStack first,ItemStack second,CallbackInfoReturnable<ItemStack> cir){
        if(StaffRepairs.starSlot(first,second)>=0)
            cir.setReturnValue(removeNonCursesFrom(StaffRepairs.repair(first,second)));
    }
}
