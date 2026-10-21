package com.ancientkira.mca.registry;
import com.ancientkira.mca.MCA;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.Blocks;
import java.util.Set;
public final class PoiTypesMCA {
    public static final ResourceKey<PoiType> MINER = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, MCA.locate("miner"));
    public static final PoiType MINER_TYPE = new PoiType(Set.copyOf(Blocks.FURNACE.getStateDefinition().getPossibleStates()), 1, 1);
    private PoiTypesMCA() { }
    public static void register(MCA.RegisterHelper<PoiType> helper) { helper.register(MCA.locate("miner"), MINER_TYPE); }
}
