package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.server.world.data.VillageManager;
import com.ancientkira.mca.util.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A miner must reach a safe retreat before consuming and igniting supplied TNT. */
public final class MinerBlasting {
    public record Plan(BlockPos site, BlockPos retreat) { }
    private MinerBlasting() { }

    public static boolean hasTnt(VillagerEntityMCA miner) {
        return InventoryUtils.getFirstSlotContainingItem(miner.getInventory(), stack -> stack.is(Items.TNT)) >= 0;
    }
    public static Plan prepare(ServerLevel world, VillagerEntityMCA miner, BlockPos target, BlockPos site, BlockPos retreat) {
        var block = world.getBlockState(target);
        if (!hasTnt(miner) || block.is(Blocks.OBSIDIAN) || block.is(Blocks.BEDROCK)
                || block.getDestroySpeed(world, target) < 0 || block.getBlock().getExplosionResistance() >= 20
                || site.distSqr(retreat) < 256 || !clearArea(world, miner, site)) return null;
        if (!world.isEmptyBlock(retreat) || !world.isEmptyBlock(retreat.above())
                || !world.getBlockState(retreat.below()).isFaceSturdy(world, retreat.below(), Direction.UP)) return null;
        var path = miner.getNavigation().createPath(retreat, 0);
        return path != null && path.canReach() ? new Plan(site.immutable(), retreat.immutable()) : null;
    }
    private static boolean clearArea(ServerLevel world, VillagerEntityMCA miner, BlockPos site) {
        if (!world.getGameRules().get(GameRules.MOB_GRIEFING) || !world.isEmptyBlock(site)
                || !world.getBlockState(site.below()).isFaceSturdy(world, site.below(), Direction.UP)) return false;
        if (!world.getEntitiesOfClass(LivingEntity.class, new AABB(site).inflate(16),
                other -> other != miner && other.isAlive() && !other.isSpectator()).isEmpty()) return false;
        var village = VillageManager.get(world).findNearestVillage(miner);
        for (BlockPos pos : BlockPos.betweenClosed(site.offset(-7, -7, -7), site.offset(7, 7, 7))) {
            if (!world.hasChunkAt(pos)) return false;
            var block = world.getBlockState(pos);
            if (!world.getFluidState(pos).isEmpty() || world.getBlockEntity(pos) != null
                    || block.getBlock() instanceof FallingBlock || block.is(Blocks.TNT)
                    || block.isSolid() && !com.ancientkira.mca.entity.ai.brain.tasks.chore.MiningTask.natural(block)
                    || block.is(Blocks.DIRT_PATH) || village.filter(v -> block.is(v.getPathBlock())
                            || v.getBuildingAt(pos).isPresent()).isPresent()) return false;
        }
        return true;
    }
    public static boolean ignite(ServerLevel world, VillagerEntityMCA miner, Plan plan) {
        if (miner.distanceToSqr(Vec3.atBottomCenterOf(plan.retreat())) > 4
                || miner.distanceToSqr(Vec3.atCenterOf(plan.site())) < 256 || !clearArea(world, miner, plan.site())) return false;
        int slot = InventoryUtils.getFirstSlotContainingItem(miner.getInventory(), stack -> stack.is(Items.TNT));
        if (slot < 0) return false;
        PrimedTnt tnt = new PrimedTnt(world, plan.site().getX() + 0.5, plan.site().getY(), plan.site().getZ() + 0.5, miner);
        tnt.setDeltaMovement(0, 0, 0);
        tnt.setFuse(80);
        if (!world.addFreshEntity(tnt)) return false;
        miner.getInventory().getItem(slot).shrink(1);
        for (var player : world.players()) {
            if (player.distanceToSqr(Vec3.atCenterOf(plan.site())) <= 4096)
                miner.sendChatMessage(player, "chore.mining.fire_in_the_hole");
        }
        return true;
    }
}
