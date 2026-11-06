package com.ancientkira.mca.server;

import com.ancientkira.mca.Config;
import com.ancientkira.mca.block.CorpseBlock;
import com.ancientkira.mca.block.TombstoneBlock;
import com.ancientkira.mca.registry.*;
import com.ancientkira.mca.util.WorldUtils;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.phys.*;
import java.util.*;

/** Permanent, copyable bodies retain appearance data independently of registry locations. */
public final class Corpses extends SavedData {
    private CompoundTag records = new CompoundTag();
    private CompoundTag handledDeaths = new CompoundTag();
    public Corpses(ServerLevel world) { }
    public Corpses(CompoundTag tag, HolderLookup.Provider provider) {
        records = tag.getCompound("Bodies").orElseGet(CompoundTag::new);
        handledDeaths = tag.getCompound("HandledDeaths").orElseGet(CompoundTag::new);
    }
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) { tag.put("Bodies", records.copy()); tag.put("HandledDeaths", handledDeaths.copy()); return tag; }
    public static Corpses get(ServerLevel level) { return WorldUtils.loadData(level.getServer().overworld(), Corpses::new, Corpses::new, "mca_corpses"); }
    /** A new actual death permits one new corpse; creative grave copies cannot reset this. */
    public static void beginDeath(Entity entity) {
        if (!(entity.level() instanceof ServerLevel world)) return;
        Corpses manager = get(world);
        manager.handledDeaths.remove(entity.getUUID().toString()); manager.setDirty();
    }
    private static String key(CompoundTag body) { return body.getStringOr("Token", ""); }
    public static CompoundTag body(ItemStack stack) { return stack.is(ItemsMCA.CORPSE) ? stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag() : new CompoundTag(); }
    private CompoundTag record(CompoundTag body) { return records.getCompound(key(body)).orElseGet(CompoundTag::new); }
    private boolean valid(ServerLevel world, CompoundTag body) {
        return Config.getInstance().enableCorpses && !key(body).isEmpty()
                && !body.getCompoundOrEmpty("Appearance").isEmpty();
    }
    public static ItemStack stack(CompoundTag body) {
        if (key(body).isEmpty()) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(ItemsMCA.CORPSE);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(body.copy()));
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.mca.corpse.named", body.getStringOr("Name", "")));
        return stack;
    }
    private static void location(CompoundTag record, ServerLevel world, BlockPos pos, String kind) {
        record.putString("Dimension", world.dimension().identifier().toString());
        record.putLong("Position", pos.asLong()); record.putString("Kind", kind);
    }
    private static ServerLevel world(ServerLevel from, CompoundTag record) {
        for (ServerLevel world : from.getServer().getAllLevels()) if (world.dimension().identifier().toString().equals(record.getStringOr("Dimension", ""))) return world;
        return null;
    }
    /** Burial requires diggable ground at the actual body position, even with corpses disabled. */
    public static boolean canBuryAt(ServerLevel world, BlockPos gravePos) {
        BlockPos pos = gravePos.below(2);
        if (world.isOutsideBuildHeight(pos) || !world.hasChunkAt(pos)) return false;
        BlockState ground = world.getBlockState(pos);
        return (ground.is(net.minecraft.tags.BlockTags.DIRT) || ground.is(Blocks.STONE)
                || ground.is(Blocks.SAND) || ground.is(Blocks.GRAVEL) || ground.is(Blocks.DEEPSLATE))
                && world.getBlockEntity(pos) == null
                && world.getBlockState(pos.below()).isFaceSturdy(world, pos.below(), Direction.UP)
                && world.getEntitiesOfClass(net.minecraft.world.entity.decoration.HangingEntity.class,
                        new AABB(pos).inflate(.1)).isEmpty();
    }

    public static boolean create(TombstoneBlock.Data grave) {
        if (!(grave.getLevel() instanceof ServerLevel world) || !Config.getInstance().enableCorpses) return false;
        if (grave.getStoredEntityUuid().flatMap(id -> com.ancientkira.mca.server.world.data.FamilyTree.get(world).getOrEmpty(id))
                .map(node -> !node.isDeceased()).orElse(false)) return false;
        Corpses manager = get(world);
        if (grave.getStoredEntityUuid().map(UUID::toString).map(manager.handledDeaths::contains).orElse(false)) return true;
        var stored = grave.createEntity(world, false);
        if (stored.isEmpty() || !(stored.get() instanceof LivingEntity entity)) return false;
        String victim = entity.getUUID().toString();
        // Keep the grave's supporting block intact; never replace containers or constructed floors.
        BlockPos pos = grave.getBlockPos().below(2);
        BlockState old = world.getBlockState(pos);
        if (!canBuryAt(world, grave.getBlockPos())) return false;
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, world.registryAccess());
        entity.saveWithoutId(output);
        CompoundTag appearance = output.buildResult(); appearance.putString("id", EntityType.getKey(entity.getType()).toString());
        CompoundTag body = new CompoundTag();
        body.putString("Token", UUID.randomUUID().toString()); body.putString("Victim", victim);
        body.putString("Name", grave.getEntityName().orElse(entity.getName().getString())); body.put("Appearance", appearance);
        body.putLong("Grave", grave.getBlockPos().asLong()); body.putString("GraveDimension", world.dimension().identifier().toString());
        location(body, world, pos, "BLOCK");
        CompoundTag entry = body.copy(); entry.store("Ground", BlockState.CODEC, old);
        manager.records.put(key(body), entry); manager.handledDeaths.putBoolean(victim, true); manager.setDirty();
        world.setBlockAndUpdate(pos, BlocksMCA.CORPSE.defaultBlockState());
        if (world.getBlockEntity(pos) instanceof CorpseBlock.Data data) data.setBody(body);
        return true;
    }
    private void removeBlock(ServerLevel source, CompoundTag entry, boolean restoreGround) {
        ServerLevel world = world(source, entry); BlockPos pos = BlockPos.of(entry.getLongOr("Position", 0));
        if (world != null && world.hasChunkAt(pos) && world.getBlockEntity(pos) instanceof CorpseBlock.Data data
                && key(data.body).equals(key(entry))) {
            world.setBlockAndUpdate(pos, restoreGround ? entry.read("Ground", BlockState.CODEC).orElse(Blocks.AIR.defaultBlockState()) : Blocks.AIR.defaultBlockState());
        }
    }
    public static List<ItemStack> drops(ServerLevel world, CorpseBlock.Data data) {
        return get(world).valid(world, data.body) ? List.of(stack(data.body)) : List.of();
    }
    public static void removed(ServerLevel world, BlockPos pos) {
        Corpses manager = get(world);
        for (String token : List.copyOf(manager.records.keySet())) {
            CompoundTag entry = manager.records.getCompound(token).orElseThrow();
            if (entry.getStringOr("Kind", "").equals("BLOCK") && world(world, entry) == world
                    && entry.getLongOr("Position", 0) == pos.asLong()) {
                manager.records.remove(token); manager.setDirty();
            }
        }
    }
    public static void tickCarried(ItemStack stack, ServerLevel world, Entity holder) {
        if (stack.is(ItemsMCA.CORPSE) && !get(world).valid(world, body(stack))) stack.setCount(0);
    }
    public static void tickItem(ItemEntity item) {
        if (!(item.level() instanceof ServerLevel world) || !item.getItem().is(ItemsMCA.CORPSE)) return;
        Corpses manager = get(world); CompoundTag body = body(item.getItem());
        if (!manager.valid(world, body)) { item.discard(); return; }
        item.setUnlimitedLifetime();
        CompoundTag previous = manager.record(body);
        if (!previous.getStringOr("Kind", "").equals("ITEM")
                || !previous.getStringOr("Holder", "").equals(item.getUUID().toString())) {
            body.putString("Token", UUID.randomUUID().toString()); body.remove("Expires");
            item.getItem().set(DataComponents.CUSTOM_DATA, CustomData.of(body));
            CompoundTag entry = body.copy(); location(entry, world, item.blockPosition(), "ITEM");
            entry.putString("Holder", item.getUUID().toString()); manager.records.put(key(body), entry); manager.setDirty();
        }
        if (!body.getBooleanOr("Sealed", false) && item.tickCount % 20 == 0) smell(world, item.position());
    }
    public static boolean place(ServerLevel world, BlockPos pos, ItemStack stack, Player player) {
        CompoundTag body = body(stack).copy(); Corpses manager = get(world);
        if (player == null || !manager.valid(world, body) || !world.getBlockState(pos).canBeReplaced()
                || !world.mayInteract(player, pos) || !player.mayUseItemAt(pos, net.minecraft.core.Direction.UP, stack)) return false;
        body.putString("Token", UUID.randomUUID().toString()); body.remove("Expires");
        BlockState state = BlocksMCA.CORPSE.defaultBlockState().setValue(CorpseBlock.WATERLOGGED,
                world.getFluidState(pos).is(net.minecraft.world.level.material.Fluids.WATER));
        // The sleeping model extends beyond its block: require floor support and room
        // for the whole body, rather than allowing it to project through a nearby wall.
        if (!state.canSurvive(world, pos)
                || world.getBlockCollisions(null, new AABB(pos.getX()-.25, pos.getY(), pos.getZ()-.75,
                        pos.getX()+1.25, pos.getY()+.75, pos.getZ()+1.75)).iterator().hasNext()) return false;
        if (!world.setBlockAndUpdate(pos, state)
                || !(world.getBlockEntity(pos) instanceof CorpseBlock.Data data)) return false;
        data.setBody(body);
        world.playSound(null, pos, net.minecraft.world.level.block.SoundType.WOOL.getPlaceSound(),
                net.minecraft.sounds.SoundSource.BLOCKS, .7F, 1.0F);
        CompoundTag entry = body.copy(); location(entry, world, pos, "BLOCK");
        entry.remove("Holder"); entry.remove("Ground"); manager.records.put(key(body), entry); manager.setDirty();
        if (!player.isCreative()) stack.shrink(1);
        return true;
    }
    static boolean isBuried(net.minecraft.world.level.BlockGetter world, BlockPos bodyPos) {
        BlockPos cover = bodyPos.above();
        return world.getBlockState(cover).isFaceSturdy(world, cover, Direction.DOWN);
    }
    private static void smell(ServerLevel world, Vec3 pos) {
        // Recheck actual cover, so exhuming a body makes it exposed again.
        if (isBuried(world, BlockPos.containing(pos))) return;
        for (LivingEntity entity : world.getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(3))) {
            if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) continue;
            entity.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
        }
    }
    public static void tickBlock(CorpseBlock.Data data) {
        if (!(data.getLevel() instanceof ServerLevel world)) return;
        Corpses manager = get(world); CompoundTag entry = manager.record(data.body);
        if (!manager.valid(world, data.body)) {
            if (!entry.isEmpty()) manager.removeBlock(world, entry, true);
            else world.removeBlock(data.getBlockPos(), false);
            return;
        }
        if (!entry.getStringOr("Kind", "").equals("BLOCK") || world(world, entry) != world
                || entry.getLongOr("Position", 0) != data.getBlockPos().asLong()) {
            CompoundTag copy = data.body.copy(); copy.putString("Token", UUID.randomUUID().toString()); copy.remove("Expires");
            data.setBody(copy); entry = copy.copy(); location(entry, world, data.getBlockPos(), "BLOCK");
            entry.remove("Holder"); entry.remove("Ground"); manager.records.put(key(copy), entry); manager.setDirty();
        }
        if (!data.body.getBooleanOr("Sealed", false) && world.getGameTime() % 20 == 0) smell(world, Vec3.atCenterOf(data.getBlockPos()));
    }
    public static Optional<BlockPos> anchor(ServerLevel world, UUID victim) {
        Corpses manager = get(world);
        for (String token : manager.records.keySet()) {
            CompoundTag entry = manager.records.getCompound(token).orElseThrow();
            if (!entry.getStringOr("Victim", "").equals(victim.toString()) || !manager.valid(world, entry)) continue;
            if (entry.getStringOr("Kind", "").equals("BLOCK") && world(world, entry) == world) {
                BlockPos pos = BlockPos.of(entry.getLongOr("Position", 0));
                if (world.hasChunkAt(pos) && world.getBlockEntity(pos) instanceof CorpseBlock.Data data && key(data.body).equals(token)) return Optional.of(pos);
            } else if (entry.getStringOr("Kind", "").equals("ITEM")) {
                try {
                    Entity item = world.getEntity(UUID.fromString(entry.getStringOr("Holder", "")));
                    if (item instanceof ItemEntity dropped && !dropped.isRemoved()) return Optional.of(item.blockPosition());
                } catch (IllegalArgumentException ignored) { }
            }
        }
        return Optional.empty();
    }
    public static void complete(Entity revived) {
        if (!(revived.level() instanceof ServerLevel world)) return;
        // Bodies are permanent memorials, including after revival. Only move a revived
        // entity if its actual spawn is blocked, so reviver use keeps the player's coordinates.
        if (!world.getBlockCollisions(revived, revived.getBoundingBox()).iterator().hasNext()) return;
        BlockPos pos = revived.blockPosition();
        for (int dy = 0; dy <= 4; dy++) for (int dx : new int[]{0,1,-1}) for (int dz : new int[]{0,1,-1}) {
            Vec3 candidate = new Vec3(pos.getX()+dx+.5, pos.getY()+dy+.1, pos.getZ()+dz+.5);
            if (world.noCollision(revived, revived.getBoundingBox().move(candidate.subtract(revived.position())))) {
                revived.snapTo(candidate.x, candidate.y, candidate.z); return;
            }
        }
    }
    public static void tick(ServerLevel world) {
        Corpses manager = get(world);
        for (String token : List.copyOf(manager.records.keySet())) {
            CompoundTag entry = manager.records.getCompound(token).orElseThrow();
            if (!manager.valid(world, entry)) {
                manager.removeBlock(world, entry, true);
                ServerLevel placedWorld = world(world, entry);
                if (entry.getStringOr("Kind", "").equals("BLOCK") && placedWorld != null
                        && !placedWorld.hasChunkAt(BlockPos.of(entry.getLongOr("Position", 0)))) continue;
            } else {
                ServerLevel placedWorld = world(world, entry);
                if (placedWorld == null) continue;
                if (entry.getStringOr("Kind", "").equals("BLOCK")) {
                    BlockPos pos = BlockPos.of(entry.getLongOr("Position", 0));
                    if (!placedWorld.hasChunkAt(pos)) continue;
                    if (placedWorld.getBlockEntity(pos) instanceof CorpseBlock.Data data && key(data.body).equals(token)) continue;
                } else if (entry.getStringOr("Kind", "").equals("ITEM")) {
                    // Dropped items may be unloaded. Their own ticks restore registration.
                    Entity entity;
                    try { entity = placedWorld.getEntity(UUID.fromString(entry.getStringOr("Holder", ""))); }
                    catch (IllegalArgumentException invalid) { entity = null; }
                    if (entity instanceof ItemEntity item && !item.isRemoved()) continue;
                }
            }
            manager.records.remove(token); manager.setDirty();
        }
    }
}
