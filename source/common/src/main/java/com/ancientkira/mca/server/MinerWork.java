package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.TaskUtils;
import com.ancientkira.mca.server.world.data.VillageManager;
import com.ancientkira.mca.util.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.Comparator;
import java.util.Optional;

public final class MinerWork {
    private MinerWork() { }
    public static Optional<BlockPos> furnace(ServerLevel world, VillagerEntityMCA person) {
        var site = person.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE)
                .filter(pos -> pos.dimension() == world.dimension() && world.getBlockState(pos.pos()).is(Blocks.FURNACE))
                .map(net.minecraft.core.GlobalPos::pos);
        if (site.isPresent()) return site;
        return TaskUtils.getNearbyBlocks(person.blockPosition(), world, state -> state.is(Blocks.FURNACE), 24, 8)
                .stream().min(Comparator.comparingDouble(pos -> pos.distSqr(person.blockPosition())));
    }
    public static boolean protectedGround(ServerLevel world, VillagerEntityMCA person, BlockPos pos) {
        var block = world.getBlockState(pos);
        if (block.is(Blocks.DIRT_PATH)) return true;
        return VillageManager.get(world).findNearestVillage(person).filter(village -> block.is(village.getPathBlock())
                || village.getBuildingAt(pos).isPresent()).isPresent();
    }
    public static void exhaustEntrance(VillagerEntityMCA person, BlockPos entrance) {
        if (entrance == null) return;
        var state = person.getMiningWorkState();
        var exhausted = state.getCompoundOrEmpty("ExhaustedEntrances");
        exhausted.putBoolean(entrance.getX() + ":" + entrance.getZ(), true);
        state.put("ExhaustedEntrances", exhausted);
    }
    private static boolean exhausted(VillagerEntityMCA person, BlockPos pos) {
        var exhausted = person.getMiningWorkState().getCompoundOrEmpty("ExhaustedEntrances");
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            if (exhausted.contains((pos.getX() + dx) + ":" + (pos.getZ() + dz))) return true;
        }
        return false;
    }
    public static Optional<BlockPos> entrance(ServerLevel world, VillagerEntityMCA person, BlockPos furnace) {
        return entrance(world, person, furnace, pos -> true);
    }
    public static Optional<BlockPos> entrance(ServerLevel world, VillagerEntityMCA person, BlockPos furnace,
                                              java.util.function.Predicate<BlockPos> usable) {
        for (int radius = 2; radius <= 24; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                BlockPos column = furnace.offset(dx, 0, dz);
                if (exhausted(person, column) || !world.hasChunkAt(column)) continue;
                BlockPos feet = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
                if (feet.getY() > furnace.getY() + 1 || !world.canSeeSky(feet) || !world.isEmptyBlock(feet) || !world.isEmptyBlock(feet.above())
                        || !world.getBlockState(feet.below()).isFaceSturdy(world, feet.below(), Direction.UP)
                        || protectedGround(world, person, feet) || protectedGround(world, person, feet.below())) continue;
                if (!usable.test(feet)) continue;
                var path = person.getNavigation().createPath(feet, 0);
                if (path != null && path.canReach()) return Optional.of(feet);
            }
        }
        return Optional.empty();
    }
    public static ItemStack suppliedPickaxe(VillagerEntityMCA person, ItemStack previous) {
        var supplies = person.getResidency().getHomeVillage().map(v -> v.getVillageGuardsManager());
        int units = supplies.map(v -> v.getSupplyUnits()).orElse(0);
        var item = units >= 4 ? Items.NETHERITE_PICKAXE : units >= 2 ? Items.DIAMOND_PICKAXE : Items.IRON_PICKAXE;
        boolean enchanted = supplies.map(v -> v.suppliesEnchantedEquipment()).orElse(false);
        var registry = person.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var efficiency = registry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY);
        if (!previous.isEmpty() && previous.is(item)
                && (net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(efficiency, previous) > 0) == enchanted) return previous;
        ItemStack result = new ItemStack(item);
        if (enchanted) {
            result.enchant(efficiency, 3);
            result.enchant(registry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING), 3);
        }
        return result;
    }
    private static boolean ore(ItemStack stack) {
        String name = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return stack.is(Items.RAW_IRON) || stack.is(Items.RAW_GOLD) || stack.is(Items.RAW_COPPER) || name.endsWith("_ore");
    }
    public static boolean hasOre(VillagerEntityMCA person) { return InventoryUtils.stream(person.getInventory()).anyMatch(MinerWork::ore); }
    /** Returns true while there is smelting work; inputs remain private to the villager. */
    public static boolean smelt(ServerLevel world, VillagerEntityMCA person) {
        ItemStack input = InventoryUtils.stream(person.getInventory()).filter(MinerWork::ore).findFirst().orElse(ItemStack.EMPTY);
        if (input.isEmpty()) return false;
        var recipe = world.getServer().getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), world);
        if (recipe.isEmpty()) return false;
        var state = person.getMiningWorkState();
        int fuel = state.getIntOr("FuelUses", 0);
        if (fuel <= 0) {
            ItemStack coal = InventoryUtils.stream(person.getInventory())
                    .filter(stack -> stack.is(Items.COAL) || stack.is(Items.CHARCOAL)).findFirst().orElse(ItemStack.EMPTY);
            if (coal.isEmpty()) return false;
            coal.shrink(1); fuel = 8; state.putInt("FuelUses", fuel);
        }
        int progress = state.getIntOr("Progress", 0) + 1;
        state.putInt("Progress", progress);
        if (progress >= recipe.get().value().cookingTime()) {
            ItemStack result = recipe.get().value().assemble(new SingleRecipeInput(input));
            if (result.isEmpty()) return false;
            if (!person.getInventory().canAddItem(result)) return false;
            input.shrink(1);
            var remainder = person.getInventory().addItem(result);
            if (!remainder.isEmpty()) person.spawnAtLocation(world, remainder);
            state.putInt("FuelUses", fuel - 1); state.putInt("Progress", 0);
            person.swing(person.getDominantHand());
        }
        return true;
    }
}
