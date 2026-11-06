package com.ancientkira.mca.mixin;

import com.ancientkira.mca.MCA;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.Optional;
import java.util.stream.Stream;

@Mixin(FlatLevelSource.class)
public class MixinFlatLevelSource {
    @ModifyExpressionValue(method="createState", at=@At(value="INVOKE", target="Lnet/minecraft/world/level/levelgen/flat/FlatLevelGeneratorSettings;structureOverrides()Ljava/util/Optional;"))
    private Optional<HolderSet<StructureSet>> mca$waterPresetVillages(Optional<HolderSet<StructureSet>> original,
            HolderLookup<StructureSet> registry, RandomState ignoredRandom, long ignoredSeed) {
        var settings = ((FlatLevelSource)(Object)this).settings();
        // No override already includes every structure. An empty whitelist disables structures.
        if (original.isEmpty() || original.get().size() == 0) return original;
        boolean water = settings.getLayersInfo().stream().anyMatch(layer -> layer.getHeight() > 0
                && layer.getBlockState().getFluidState().is(FluidTags.WATER));
        if (!water) return original;
        ResourceKey<StructureSet> key = ResourceKey.create(Registries.STRUCTURE_SET, MCA.locate("underwater_village"));
        var structures = Stream.concat(original.get().stream(),
                registry.get(key).stream().map(holder -> (Holder<StructureSet>)holder)).distinct().toList();
        return Optional.of(HolderSet.direct(structures));
    }
}
