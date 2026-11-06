package com.ancientkira.mca.server.world.data;

import com.ancientkira.mca.server.BedCompatibility;
import com.ancientkira.mca.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** World-owned home reservations survive logout and never set a bed's occupied block state. */
public final class PlayerBedClaims extends SavedData {
    private final Map<UUID, GlobalPos> homes = new HashMap<>();
    private PlayerBedClaims(ServerLevel world) { }
    private PlayerBedClaims(CompoundTag tag, HolderLookup.Provider provider) {
        for (String key : tag.keySet()) {
            try {
                UUID player = UUID.fromString(key);
                tag.read(key, GlobalPos.CODEC).ifPresent(home -> homes.put(player, home));
            } catch (IllegalArgumentException ignored) { }
        }
    }
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        homes.forEach((player, home) -> tag.store(player.toString(), GlobalPos.CODEC, home));
        return tag;
    }
    public static PlayerBedClaims get(ServerLevel world) {
        return WorldUtils.loadData(world.getServer().overworld(), PlayerBedClaims::new,
                PlayerBedClaims::new, "mca_player_beds");
    }
    public static BlockPos head(ServerLevel world, BlockPos pos) {
        var state = world.getBlockState(pos);
        return BedCompatibility.isBed(state) && state.getValue(BedBlock.PART) == BedPart.FOOT
                ? pos.relative(state.getValue(BedBlock.FACING)) : pos;
    }
    public static boolean reserved(ServerLevel world, BlockPos pos) {
        return get(world).homes.containsValue(GlobalPos.of(world.dimension(), head(world, pos)));
    }
    public static void claim(ServerPlayer player, ServerLevel world, BlockPos pos) {
        if (player.isSpectator() || !BedCompatibility.isBed(world.getBlockState(pos))) return;
        BlockPos bed = head(world, pos).immutable();
        var claims = get(world);
        GlobalPos home = GlobalPos.of(world.dimension(), bed);
        if (home.equals(claims.homes.put(player.getUUID(), home))) return;
        claims.setDirty();
        for (var entity : world.getAllEntities()) {
            if (entity instanceof com.ancientkira.mca.entity.VillagerEntityMCA person) {
                person.getBrain().getMemoryInternal(MemoryModuleType.HOME).filter(home::equals).ifPresent(old -> {
                    person.releasePoi(MemoryModuleType.HOME);
                    person.getBrain().eraseMemory(MemoryModuleType.HOME);
                    person.getBrain().eraseMemory(com.ancientkira.mca.entity.ai.MemoryModuleTypeMCA.FORCED_HOME);
                    person.getNavigation().stop();
                });
                if (person.getSleepingPos().filter(bed::equals).isPresent()) person.stopSleeping();
            }
        }
    }
}
