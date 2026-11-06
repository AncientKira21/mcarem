package com.ancientkira.mca.mixin;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.registry.ProfessionsMCA;
import com.ancientkira.mca.server.GolemAllies;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

/** Classifying per entity keeps ordinary MCA villagers out of the vanilla raider tag. */
@Mixin(BellBlockEntity.class)
public abstract class MixinBellBlockEntity {
    @Unique private static boolean mca$raider(LivingEntity entity) {
        return entity instanceof VillagerEntityMCA person && ProfessionsMCA.isRaider(person.getProfession())
                || entity instanceof IronGolem golem && GolemAllies.raider(golem);
    }
    @Inject(method="areRaidersNearby", at=@At("HEAD"), cancellable=true)
    private static void mca$resonateForRaiders(BlockPos pos, List<LivingEntity> entities, CallbackInfoReturnable<Boolean> ci) {
        if (entities != null && entities.stream().anyMatch(entity -> entity.isAlive() && !entity.isRemoved()
                && pos.closerToCenterThan(entity.position(), 32) && mca$raider(entity))) ci.setReturnValue(true);
    }
    @Inject(method="isRaiderWithinRange", at=@At("HEAD"), cancellable=true)
    private static void mca$highlightRaiders(BlockPos pos, LivingEntity entity, CallbackInfoReturnable<Boolean> ci) {
        if (entity.isAlive() && !entity.isRemoved() && pos.closerToCenterThan(entity.position(), 48)
                && mca$raider(entity)) ci.setReturnValue(true);
    }
}
