package com.ancientkira.mca.entity.ai.brain.tasks.chore;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.Chore;
import com.ancientkira.mca.util.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import java.util.Map;

/** Descend by one block per horizontal step, preserving a two-block-high return staircase. */
public class MiningTask extends AbstractChoreTask {
    private BlockPos step;
    private Direction direction;
    private BlockPos breaking;
    private int progress, walking, steps;
    private final boolean professionWork;
    private BlockPos furnace, entrance;
    private boolean returning, ending, newShaft;
    private String endMessage;
    private long retryAt;
    private int diggingTicks, blastRetreatTicks;
    private long nextBlastAttempt, blastWaitUntil;
    private com.ancientkira.mca.server.MinerBlasting.Plan blastPlan;
    private ItemStack suppliedPickaxe = ItemStack.EMPTY;
    private boolean restoredRoute;


    public MiningTask() { this(false); }
    public MiningTask(boolean professionWork) { super(Map.of()); this.professionWork = professionWork; }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA entity) {
        return world.getGameTime() >= retryAt
                && (!professionWork || world.getGameRules().get(GameRules.MOB_GRIEFING))
                && (professionWork ? entity.getProfession() == com.ancientkira.mca.registry.ProfessionsMCA.MINER
                    && entity.getVillagerBrain().getCurrentJob() == Chore.NONE
                    && entity.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.WORK)
                    : entity.getVillagerBrain().getCurrentJob() == Chore.PROSPECT)
                && !entity.isKidnapped() && !entity.isNoAi() && !entity.isTrading()
                && entity.getInteractions().getInteractingPlayer().isEmpty()
                && entity.getVillagerBrain().getMoveState() == com.ancientkira.mca.entity.ai.MoveState.MOVE
                && !entity.getVillagerBrain().isPanicking()
                && !entity.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }

    @Override
    protected boolean canStillUse(ServerLevel world, VillagerEntityMCA entity, long time) {
        return checkExtraStartConditions(world, entity);
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA entity, long time) {
        super.start(world, entity, time);
        if (!restoredRoute) { restoreRoute(world, entity); restoredRoute = true; }
        if (professionWork) equipPickaxe(entity);
        if (furnace == null || !world.getBlockState(furnace).is(Blocks.FURNACE)) {
            furnace = com.ancientkira.mca.server.MinerWork.furnace(world, entity).orElse(null);
            if (furnace == null) { finish("chore.mining.no_furnace"); return; }
        }
        if (step == null && entrance == null) {
            entrance = findEntrance(world, entity);
            if (entrance == null) { finish("chore.mining.unsafe"); return; }
            progress = walking = steps = 0;
        }
    }

    public static BlockPos nextStep(BlockPos from, Direction direction) {
        return from.relative(direction).below();
    }

    @Override
    protected void stop(ServerLevel world, VillagerEntityMCA entity, long time) {
        saveRoute(entity);
        clearChoreItem(entity);
        // Conversation and merchant tasks own movement while the work is paused.
        if (entity.getInteractions().getInteractingPlayer().isEmpty() && !entity.isTrading()) {
            entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            entity.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
            entity.getNavigation().stop();
        }
        if (!professionWork && entity.getVillagerBrain().getCurrentJob() != Chore.PROSPECT) {
            step = null; entrance = null; furnace = null; returning = false; ending = false; newShaft = false;
            blastPlan = null;
            saveRoute(entity);
        }
    }

    private void finish(String message) {
        if (!ending && furnace != null && villager != null && com.ancientkira.mca.server.MinerWork.hasOre(villager)) {
            ending = true; endMessage = message; returning = true;
            return;
        }
        if (professionWork && (message.equals("chore.mining.unsafe") || message.equals("chore.mining.blocked"))) {
            com.ancientkira.mca.server.MinerWork.exhaustEntrance(villager, entrance);
        }
        ending = false; newShaft = false; blastPlan = null;
        step = null; entrance = null; furnace = null; returning = false;
        retryAt = villager.level().getGameTime() + (professionWork ? 40 : 600);
        if (!professionWork) abandonJobWithMessage(message);
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA entity, long time) {
        try {
        if (entity.isTrading() || entity.getInteractions().getInteractingPlayer().isPresent()) return;
        if (!professionWork) {
            super.tick(world, entity, time);
            if (getAssigningPlayer().isEmpty()) return;
        }
        if (!world.getGameRules().get(GameRules.MOB_GRIEFING)) {
            clearChoreItem(entity);
            if (!professionWork) finish("chore.mining.disabled");
            return;
        }
        if (furnace == null || !world.getBlockState(furnace).is(Blocks.FURNACE)) { finish("chore.mining.no_furnace"); return; }
        if (blastPlan != null) {
            entity.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(blastPlan.retreat(), 0.8F, 1));
            if (entity.distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(blastPlan.retreat())) <= 4) {
                if (com.ancientkira.mca.server.MinerBlasting.ignite(world, entity, blastPlan)) {
                    com.ancientkira.mca.server.MinerWork.exhaustEntrance(entity, entrance);
                    newShaft = returning = true;
                    blastWaitUntil = time + 100;
                    diggingTicks = walking = progress = 0;
                }
                blastPlan = null;
            } else if (++blastRetreatTicks > 300) blastPlan = null;
            return;
        }
        if (time < blastWaitUntil) return;
        if (returning) {
            if (entity.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(furnace)) > 9) {
                entity.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(returnWaypoint(entity), 0.5F, 0));
                if (++walking > Math.max(600, steps * 40)) finish("chore.mining.unsafe");
                return;
            }
            entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            entity.getNavigation().stop();
            if (com.ancientkira.mca.server.MinerWork.smelt(world, entity)) return;
            returning = false;
            walking = 0;
            if (ending) { finish(endMessage); return; }
            if (newShaft) {
                newShaft = false;
                step = null;
                entrance = findEntrance(world, entity);
                progress = steps = 0;
                if (entrance == null) { finish("chore.mining.unsafe"); return; }
            }
            if (!professionWork && steps >= 32) { finish("chore.mining.finished"); return; }
        }
        if (step == null && entrance != null) {
            if (entity.distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(entrance)) > 0.65) {
                entity.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(entrance, 0.5F, 0));
                if (++walking > 400) finish("chore.mining.unsafe");
                return;
            }
            direction = Direction.Plane.HORIZONTAL.stream().filter(dir -> {
                BlockPos candidate = nextStep(entrance, dir);
                return usableStep(world, entity, candidate);
            }).findFirst().orElse(null);
            if (direction == null) { finish("chore.mining.unsafe"); return; }
            step = nextStep(entrance, direction);
            walking = 0;
        }
        ItemStack pickaxe = equipPickaxe(entity);
        if (pickaxe.isEmpty()) { finish("chore.mining.nopick"); return; }
        if (step == null) return;
        BlockPos target = null;
        // Clear headroom first, never the block under the miner's own feet.
        for (int height = 2; height >= 0; height--) {
            BlockPos pos = step.above(height);
            if (!world.getBlockState(pos).isAir()) { target = pos; break; }
        }
        if (target == null) {
            if (!safeStep(world, step)) { finish("chore.mining.unsafe"); return; }

            if (entity.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(step)) < 0.65) {
                ++steps;
                if (steps % 8 == 0) returning = true;
                step = nextStep(step, direction);
                progress = walking = 0;
                return;
            }
            entity.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(step, 0.45F, 0));
            if (++walking >= 200) finish("chore.mining.unsafe");
            return;
        }
        if (target.getX() == entity.blockPosition().getX() && target.getZ() == entity.blockPosition().getZ()
                && target.getY() < entity.blockPosition().getY()) { finish("chore.mining.unsafe"); return; }
        if (com.ancientkira.mca.server.MinerWork.protectedGround(world, entity, target)) { finish("chore.mining.unsafe"); return; }
        if (entity.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target)) > 16) {
            entity.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(descentWaypoint(entity), 0.5F, 0));
            if (++walking > 400) finish("chore.mining.unsafe");
            return;
        }
        BlockState state = world.getBlockState(target);
        if ((state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK)) && !suitableTool(state, pickaxe)) {
            ItemStack upgrade = InventoryUtils.stream(entity.getInventory())
                    .filter(stack -> suitableTool(state, stack))
                    .findFirst().orElse(ItemStack.EMPTY);
            if (!upgrade.isEmpty()) {
                pickaxe = upgrade;
                entity.setItemInHand(entity.getDominantHand(), pickaxe);
            }
        }
        if (endsShaft(world, target, state, pickaxe)) {
            com.ancientkira.mca.server.MinerWork.exhaustEntrance(entity, entrance);
            newShaft = returning = true;
            breaking = null;
            progress = walking = 0;
            return;
        }
        if (!safeStep(world, step)) { finish("chore.mining.unsafe"); return; }
        if (!natural(state) || state.getDestroySpeed(world, target) < 0
                || state.requiresCorrectToolForDrops() && !pickaxe.isCorrectToolForDrops(state)) {
            finish("chore.mining.blocked"); return;
        }
        if (professionWork && ++diggingTicks >= 600 && time >= nextBlastAttempt
                && com.ancientkira.mca.server.MinerBlasting.hasTnt(entity)) {
            nextBlastAttempt = time + 200;
            if (entity.getRandom().nextInt(4) == 0) {
                BlockPos site = step.relative(direction.getOpposite()).above();
                int retreatDepth = Math.max(0, steps - 14);
                BlockPos retreat = retreatDepth == 0 ? furnace
                        : entrance.relative(direction, retreatDepth).below(retreatDepth);
                blastPlan = com.ancientkira.mca.server.MinerBlasting.prepare(world, entity, target, site, retreat);
                if (blastPlan != null) { blastRetreatTicks = 0; return; }
            }
        }
        entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        entity.getNavigation().stop();
        entity.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
        if (!target.equals(breaking)) { breaking = target; progress = 0; }
        entity.swing(entity.getDominantHand());
        float speed = pickaxe.getDestroySpeed(state);
        int efficiency = net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                entity.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                        .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY), pickaxe);
        if (speed > 1 && efficiency > 0) speed += efficiency * efficiency + 1;
        int ticks = Math.max(10, (int) Math.ceil(Math.max(0.1F, state.getDestroySpeed(world, target)) * 30 / Math.max(1, speed)));
        if (++progress < ticks) return;
        var drops = Block.getDrops(state, world, target, null, entity, pickaxe);
        if (!world.destroyBlock(target, false, entity)) { finish("chore.mining.blocked"); return; }
        com.ancientkira.mca.server.NeutralMobRules.onBlockMined(world, entity, target, state);
        for (ItemStack drop : drops) {
            ItemStack remainder = entity.getInventory().addItem(drop);
            if (!remainder.isEmpty()) entity.spawnAtLocation(world, remainder);
        }
        pickaxe.hurtAndBreak(1, entity, entity.getDominantSlot());
        progress = 0;
        } finally {
            saveRoute(entity);
        }
    }

    private ItemStack equipPickaxe(VillagerEntityMCA entity) {
        ItemStack inventoryTool = InventoryUtils.stream(entity.getInventory()).filter(stack -> stack.is(ItemTags.PICKAXES))
                .max(java.util.Comparator.comparingInt(ItemStack::getMaxDamage)).orElse(ItemStack.EMPTY);
        ItemStack tool;
        if (professionWork) {
            suppliedPickaxe = com.ancientkira.mca.server.MinerWork.suppliedPickaxe(entity, suppliedPickaxe);
            tool = !inventoryTool.isEmpty() && inventoryTool.getMaxDamage() >= suppliedPickaxe.getMaxDamage()
                    ? inventoryTool : suppliedPickaxe;
        } else {
            ItemStack held = entity.getItemInHand(entity.getDominantHand());
            tool = held.is(ItemTags.PICKAXES) ? held : inventoryTool;
        }
        if (!tool.isEmpty()) entity.setItemInHand(entity.getDominantHand(), tool);
        return tool;
    }

    private BlockPos findEntrance(ServerLevel world, VillagerEntityMCA entity) {
        return com.ancientkira.mca.server.MinerWork.entrance(world, entity, furnace,
                pos -> Direction.Plane.HORIZONTAL.stream().anyMatch(dir -> usableStep(world, entity, nextStep(pos, dir))))
                .orElse(null);
    }

    private static boolean usableStep(ServerLevel world, VillagerEntityMCA entity, BlockPos candidate) {
        if (!safeStep(world, candidate)) return false;
        for (int height = 0; height <= 2; height++) {
            BlockPos pos = candidate.above(height);
            BlockState state = world.getBlockState(pos);
            if ((state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK))
                    && endsShaft(world, pos, state, entity.getItemInHand(entity.getDominantHand()))
                    && InventoryUtils.stream(entity.getInventory()).noneMatch(stack -> !endsShaft(world, pos, state, stack))) return false;
            if (!state.isAir() && (!natural(state) || state.getDestroySpeed(world, pos) < 0
                    || com.ancientkira.mca.server.MinerWork.protectedGround(world, entity, pos))) return false;
        }
        return true;
    }

    /** Descend in reachable segments too, rather than targeting a distant deep workface. */
    private BlockPos descentWaypoint(VillagerEntityMCA entity) {
        BlockPos approach = step.relative(direction.getOpposite()).above();
        if (entrance == null || direction == null) return approach;
        int currentDepth = Math.max(0, entrance.getY() - entity.blockPosition().getY());
        int desiredDepth = Math.max(0, entrance.getY() - approach.getY());
        int depth = Math.min(desiredDepth, currentDepth + 8);
        return entrance.relative(direction, depth).below(depth);
    }

    private String routeKey() { return professionWork ? "ProfessionRoute" : "ChoreRoute"; }

    private void saveRoute(VillagerEntityMCA entity) {
        var state = entity.getMiningWorkState();
        if (furnace == null || entrance == null) { state.remove(routeKey()); return; }
        var route = new net.minecraft.nbt.CompoundTag();
        route.putString("Dimension", entity.level().dimension().toString());
        putPosition(route, "Furnace", furnace);
        putPosition(route, "Entrance", entrance);
        if (step != null) putPosition(route, "Step", step);
        if (direction != null) route.putString("Direction", direction.name());
        route.putInt("Steps", steps);
        route.putInt("Progress", progress);
        route.putBoolean("Returning", returning);
        route.putBoolean("Ending", ending);
        route.putBoolean("NewShaft", newShaft);
        if (endMessage != null) route.putString("EndMessage", endMessage);
        state.put(routeKey(), route);
    }

    private void restoreRoute(ServerLevel world, VillagerEntityMCA entity) {
        var route = entity.getMiningWorkState().getCompoundOrEmpty(routeKey());
        if (!route.getString("Dimension").orElse("").equals(world.dimension().toString())) return;
        BlockPos savedFurnace = readPosition(route, "Furnace"), savedEntrance = readPosition(route, "Entrance");
        if (savedFurnace == null || savedEntrance == null || !world.getBlockState(savedFurnace).is(Blocks.FURNACE)) return;
        if (professionWork && entity.getBrain().getMemoryInternal(MemoryModuleType.JOB_SITE)
                .filter(site -> site.dimension() != world.dimension() || !site.pos().equals(savedFurnace)).isPresent()) return;
        Direction savedDirection;
        try { savedDirection = Direction.valueOf(route.getString("Direction").orElse("")); }
        catch (IllegalArgumentException missingDirection) { savedDirection = null; }
        BlockPos savedStep = readPosition(route, "Step");
        if (savedStep != null && (savedDirection == null || savedDirection.getStepY() != 0)) return;
        furnace = savedFurnace; entrance = savedEntrance; step = savedStep; direction = savedDirection;
        steps = Math.max(0, route.getIntOr("Steps", 0));
        progress = Math.max(0, route.getIntOr("Progress", 0));
        returning = route.getBoolean("Returning").orElse(false);
        ending = route.getBoolean("Ending").orElse(false);
        newShaft = route.getBoolean("NewShaft").orElse(false);
        endMessage = route.getString("EndMessage").orElse("chore.mining.unsafe");
        walking = 0;
    }

    private static void putPosition(net.minecraft.nbt.CompoundTag route, String key, BlockPos pos) {
        route.putIntArray(key, new int[]{pos.getX(), pos.getY(), pos.getZ()});
    }

    private static BlockPos readPosition(net.minecraft.nbt.CompoundTag route, String key) {
        int[] coordinates = route.getIntArray(key).orElse(new int[0]);
        return coordinates.length == 3 ? new BlockPos(coordinates[0], coordinates[1], coordinates[2]) : null;
    }

    // Climb in short segments so deep shafts do not exceed the navigation search range.
    private BlockPos returnWaypoint(VillagerEntityMCA entity) {
        if (entrance == null || direction == null || entity.blockPosition().getY() >= entrance.getY() - 2) return furnace;
        int depth = Math.max(0, Math.min(steps, entrance.getY() - entity.blockPosition().getY() - 8));
        return entrance.relative(direction, depth).below(depth);
    }

    private static boolean safeStep(ServerLevel world, BlockPos step) {
        if (!world.hasChunkAt(step) || !world.getBlockState(step.below()).isFaceSturdy(world, step.below(), Direction.UP)) return false;
        for (int height = -1; height <= 3; height++) {
            BlockPos pos = step.above(height);
            if (!world.hasChunkAt(pos) || !world.getFluidState(pos).isEmpty()
                    || world.getBlockState(pos).getBlock() instanceof FallingBlock
                    || world.getBlockEntity(pos) != null) return false;
            for (Direction side : Direction.values()) {
                BlockPos neighbor = pos.relative(side);
                if (!world.hasChunkAt(neighbor) || !world.getFluidState(neighbor).isEmpty()
                        || world.getBlockState(neighbor).getBlock() instanceof FallingBlock) return false;
            }
        }
        return true;
    }

    private static boolean suitableTool(BlockState state, ItemStack pickaxe) {
        if (!pickaxe.is(ItemTags.PICKAXES) || !pickaxe.isCorrectToolForDrops(state)) return false;
        if (!state.is(Blocks.BEDROCK)) return true;
        // Check the unenchanted tool's capabilities, so Efficiency cannot promote a diamond pickaxe.
        BlockState stone = Blocks.STONE.defaultBlockState();
        return pickaxe.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState())
                && pickaxe.getDestroySpeed(stone) >= new ItemStack(net.minecraft.world.item.Items.NETHERITE_PICKAXE).getDestroySpeed(stone);
    }

    private static boolean supportedBedrock(ServerLevel world, BlockPos target) {
        BlockPos below = target.below();
        return below.getY() >= world.getMinY() && world.hasChunkAt(below)
                && world.getFluidState(below).isEmpty()
                && !(world.getBlockState(below).getBlock() instanceof FallingBlock)
                && world.getBlockState(below).isFaceSturdy(world, below, Direction.UP);
    }

    private static boolean endsShaft(ServerLevel world, BlockPos target, BlockState state, ItemStack pickaxe) {
        return state.is(Blocks.BEDROCK)
                ? state.getDestroySpeed(world, target) < 0 || !suitableTool(state, pickaxe) || !supportedBedrock(world, target)
                : state.is(Blocks.OBSIDIAN) && !suitableTool(state, pickaxe);
    }

    public static boolean natural(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                    && net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().endsWith("_ore")
                || state.is(Blocks.BEDROCK) || state.is(Blocks.OBSIDIAN) || state.is(Blocks.NETHER_QUARTZ_ORE) || state.is(Blocks.ANCIENT_DEBRIS)
                || state.is(Blocks.END_STONE) || state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.CLAY) || state.is(Blocks.TUFF) || state.is(Blocks.CALCITE);
    }
}
