package com.ancientkira.mca.block;

import com.ancientkira.mca.registry.ItemsMCA;
import com.ancientkira.mca.server.Corpses;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.*;
import java.util.List;

/** A permanent, waterloggable memorial body, never an actual ticking villager. */
public final class CorpseBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty WATERLOGGED =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;
    public static final MapCodec<CorpseBlock> CODEC = simpleCodec(CorpseBlock::new);
    public CorpseBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false));
    }
    @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WATERLOGGED);
    }
    @Override protected net.minecraft.world.level.material.FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? net.minecraft.world.level.material.Fluids.WATER.getSource(false)
                : super.getFluidState(state);
    }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }
    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
            BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, net.minecraft.util.RandomSource random) {
        if (state.getValue(WATERLOGGED)) ticks.scheduleTick(pos, net.minecraft.world.level.material.Fluids.WATER,
                net.minecraft.world.level.material.Fluids.WATER.getTickDelay(level));
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return state.getValue(WATERLOGGED) ? net.minecraft.world.level.material.Fluids.WATER.defaultFluidState().createLegacyBlock()
                    : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected net.minecraft.world.InteractionResult useItemOn(ItemStack held, BlockState state,
            Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Data data)) return net.minecraft.world.InteractionResult.PASS;
        boolean sealed = data.body.getBooleanOr("Sealed", false);
        var plastic = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                com.ancientkira.mca.MCA.locate("corpse_sealing_plastic"));
        boolean seal = !sealed && held.is(plastic);
        boolean unseal = sealed && held.is(net.minecraft.tags.ItemTags.AXES);
        if (!seal && !unseal) return super.useItemOn(held, state, level, pos, player, hand, hit);
        if (!(level instanceof ServerLevel world)) return net.minecraft.world.InteractionResult.SUCCESS;
        if (!world.mayInteract(player, pos) || !player.mayUseItemAt(pos, hit.getDirection(), held)) return net.minecraft.world.InteractionResult.FAIL;
        var body = data.body.copy();
        var ops = world.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        if (seal) {
            body.putBoolean("Sealed", true);
            body.store("WrappingPlastic", ItemStack.CODEC, ops, held.copyWithCount(1));
            data.setBody(body);
            if (!player.isCreative()) held.shrink(1);
        } else {
            ItemStack returned = body.read("WrappingPlastic", ItemStack.CODEC, ops).orElse(ItemStack.EMPTY);
            body.remove("Sealed"); body.remove("WrappingPlastic");
            data.setBody(body);
            if (!returned.isEmpty() && !player.getInventory().add(returned)) player.spawnAtLocation(world, returned, .5F);
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(
                net.minecraft.network.chat.Component.translatable(seal ? "message.mca.corpse.sealed" : "message.mca.corpse.unsealed"), true);
        world.playSound(null, pos, net.minecraft.sounds.SoundEvents.WOOL_PLACE,
                net.minecraft.sounds.SoundSource.BLOCKS, .7F, 1.0F);
        return net.minecraft.world.InteractionResult.SUCCESS;
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block.box(2, 0, 0, 14, 5, 16);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new Data(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : (world, pos, blockState, data) -> Corpses.tickBlock((Data)data);
    }
    @Override protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return level.getBlockEntity(pos) instanceof Data data ? Corpses.stack(data.body) : ItemStack.EMPTY;
    }
    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof Data data
                && data.getLevel() instanceof ServerLevel world) return Corpses.drops(world, data);
        return List.of();
    }
    @Override public void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
        Corpses.removed(world, pos);
    }
    public static final class Data extends BlockEntity {
        public CompoundTag body = new CompoundTag();
        public net.minecraft.world.entity.LivingEntity preview;
        public Data(BlockPos pos, BlockState state) { super(BlockEntityTypesMCA.CORPSE, pos, state); }
        public void setBody(CompoundTag value) {
            body = value.copy(); preview = null; setChanged();
            if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        @Override protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input); body = input.read("Body", CompoundTag.CODEC).orElseGet(CompoundTag::new); preview = null;
        }
        @Override protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output); output.store("Body", CompoundTag.CODEC, body);
        }
        @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }
        @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this, BlockEntity::getUpdateTag); }
    }
}
