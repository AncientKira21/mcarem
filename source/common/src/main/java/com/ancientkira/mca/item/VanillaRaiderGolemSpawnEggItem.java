package com.ancientkira.mca.item;

import com.ancientkira.mca.server.GolemAllies;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class VanillaRaiderGolemSpawnEggItem extends SpawnEggItem {
    public VanillaRaiderGolemSpawnEggItem(Properties properties) {
        super(properties.spawnEgg(EntityType.IRON_GOLEM).component(DataComponents.ENTITY_DATA,
                TypedEntityData.of(EntityType.IRON_GOLEM, data())));
    }

    public static CompoundTag data() {
        CompoundTag data = new CompoundTag();
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(GolemAllies.RAIDER));
        data.put("Tags", tags);
        data.putString("id", "minecraft:iron_golem");
        ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, Component.translatable("entity.mca.raider_golem").withStyle(ChatFormatting.RED))
                .result().ifPresent(tag -> data.put("CustomName", tag));
        return data;
    }

    public static void configureGolem(net.minecraft.world.entity.Entity golem) {
        golem.addTag(GolemAllies.RAIDER);
        ((com.ancientkira.mca.entity.RaiderGolemAppearance) golem).mca$setGreyRaider(true);
        golem.setCustomName(Component.translatable("entity.mca.raider_golem").withStyle(ChatFormatting.RED));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel world)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack itemInHand = context.getItemInHand();
        BlockPos clickedPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();
        BlockState blockState = level.getBlockState(clickedPos);
        BlockEntity blockEntity = level.getBlockEntity(clickedPos);

        if (blockEntity instanceof Spawner spawner) {
            if (!world.isSpawnerBlockEnabled()) {
                if (context.getPlayer() instanceof ServerPlayer sp) {
                    sp.sendSystemMessage(Component.translatable("advMode.notEnabled.spawner"));
                }
                return InteractionResult.FAIL;
            }
            if (blockEntity instanceof SpawnerBlockEntity spawnerBlockEntity) {
                var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                        net.minecraft.util.ProblemReporter.DISCARDING, world.registryAccess());
                spawnerBlockEntity.getSpawner().save(output);
                CompoundTag entity = data();
                var spawnData = new net.minecraft.world.level.SpawnData(entity, java.util.Optional.empty(), java.util.Optional.empty());
                output.store("SpawnData", net.minecraft.world.level.SpawnData.CODEC, spawnData);
                output.store("SpawnPotentials", net.minecraft.world.level.SpawnData.LIST_CODEC,
                        net.minecraft.util.random.WeightedList.of(spawnData));
                spawnerBlockEntity.getSpawner().load(world, clickedPos,
                        net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,
                                world.registryAccess(), output.buildResult()));
                spawnerBlockEntity.setChanged();
                world.sendBlockUpdated(clickedPos, blockState, blockState, 3);
            } else {
                spawner.setEntityId(EntityType.IRON_GOLEM, level.getRandom());
                level.sendBlockUpdated(clickedPos, blockState, blockState, 3);
            }
            level.gameEvent(context.getPlayer(), GameEvent.BLOCK_CHANGE, clickedPos);
            itemInHand.consume(1, context.getPlayer());
            return InteractionResult.SUCCESS;
        }

        BlockPos spawnPos = blockState.getCollisionShape(level, clickedPos).isEmpty() ? clickedPos : clickedPos.relative(clickedFace);
        var golem = EntityType.IRON_GOLEM.create(world, EntitySpawnReason.SPAWN_ITEM_USE);
        if (golem != null) {
            golem.snapTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0.0F, 0.0F);
            configureGolem(golem);
            world.addFreshEntityWithPassengers(golem);
            itemInHand.consume(1, context.getPlayer());
            level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, spawnPos);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        BlockHitResult hitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel world)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos clickedPos = hitResult.getBlockPos();
        if (!(level.getBlockState(clickedPos).getBlock() instanceof LiquidBlock)) {
            return InteractionResult.PASS;
        }
        if (!level.mayInteract(player, clickedPos) || !player.mayUseItemAt(clickedPos, hitResult.getDirection(), itemInHand)) {
            return InteractionResult.FAIL;
        }
        var golem = EntityType.IRON_GOLEM.create(world, EntitySpawnReason.SPAWN_ITEM_USE);
        if (golem != null) {
            golem.snapTo(clickedPos.getX() + 0.5, clickedPos.getY(), clickedPos.getZ() + 0.5, 0.0F, 0.0F);
            configureGolem(golem);
            world.addFreshEntityWithPassengers(golem);
            itemInHand.consume(1, player);
            player.awardStat(Stats.ITEM_USED.get(this));
            level.gameEvent(player, GameEvent.ENTITY_PLACE, clickedPos);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }
}
