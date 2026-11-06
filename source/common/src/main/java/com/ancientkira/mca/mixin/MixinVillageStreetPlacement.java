package com.ancientkira.mca.mixin;

import com.ancientkira.mca.server.world.VillageStreetGravityProcessor;
import com.mojang.datafixers.util.Either;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.GravityProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SinglePoolElement.class)
public abstract class MixinVillageStreetPlacement {
    @Shadow @Final protected Either<Identifier, StructureTemplate> template;

    @Inject(method = "getSettings", at = @At("RETURN"))
    private void mca$streetGround(Rotation rotation, BoundingBox box, LiquidSettings liquids,
            boolean keepJigsaws, CallbackInfoReturnable<StructurePlaceSettings> cir) {
        if (template.left().filter(id -> (id.getNamespace().equals("minecraft") || id.getNamespace().equals("mca"))
                && id.getPath().contains("village/")).isEmpty()) return;
        var processors = cir.getReturnValue().getProcessors();
        for (int i = 0; i < processors.size(); i++) {
            if (processors.get(i) instanceof GravityProcessor && !(processors.get(i) instanceof VillageStreetGravityProcessor)) {
                processors.set(i, VillageStreetGravityProcessor.INSTANCE);
            }
        }
    }
}
