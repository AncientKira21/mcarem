package com.ancientkira.mca.network.c2s;

import com.ancientkira.mca.MCA;
import com.ancientkira.mca.block.TombstoneBlock;
import com.ancientkira.mca.entity.Infectable;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.VillagerFactory;
import com.ancientkira.mca.entity.VillagerLike;
import com.ancientkira.mca.entity.ai.Genetics;
import com.ancientkira.mca.entity.ai.relationship.AgeState;
import com.ancientkira.mca.entity.ai.MemoryModuleTypeMCA;
import com.ancientkira.mca.entity.ai.relationship.Gender;
import com.ancientkira.mca.network.HandleablePayload;
import com.ancientkira.mca.server.world.data.FamilyTree;
import com.ancientkira.mca.server.world.data.FamilyTreeNode;
import com.ancientkira.mca.server.world.data.GraveyardManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.storage.TagValueInput;

import java.util.List;
import java.util.UUID;

public record VillagerReviverActionMessage(UUID villager, Action action) implements HandleablePayload {
    public static final CustomPacketPayload.Type<VillagerReviverActionMessage> TYPE =
            new CustomPacketPayload.Type<>(MCA.locate("villager_reviver_action"));
    public static final StreamCodec<FriendlyByteBuf, VillagerReviverActionMessage> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, VillagerReviverActionMessage::villager,
            ByteBufCodecs.idMapper(id -> Action.values()[id], Action::ordinal),
            VillagerReviverActionMessage::action,
            VillagerReviverActionMessage::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        if (action == Action.REVIVE_ALL || action == Action.REVIVE_VILLAGE) {
            com.ancientkira.mca.server.BulkRevival.enqueue(player, action == Action.REVIVE_VILLAGE);
            return;
        }
        reviveSingle(player, false, 0);
    }

    public void reviveSingle(ServerPlayer player, boolean quiet, int index) {
        FamilyTree tree = FamilyTree.get(player.level());
        FamilyTreeNode node = tree.getOrEmpty(villager).orElse(null);
        if (node == null || node.isPlayer() || !node.isDeceased()) {
            if (!quiet) player.sendSystemMessage(Component.translatable("gui.villager_reviver.error.not_deceased"), true);
            return;
        }

        if (action == Action.DELETE) {
            if (tree.removePermanently(villager)) {
                if (!quiet) player.sendSystemMessage(Component.translatable("gui.villager_reviver.delete.success", node.getName()), true);
            }
            return;
        }

        MinecraftServer server = player.level().getServer();
        StoredVillager stored = findStoredVillager(server, villager);
        Entity existing = findLoadedEntity(server, villager);
        if (existing instanceof VillagerLike<?>) {
            BlockPos pos = existing.blockPosition();
            restoreVillager(existing, pos);
            node.setDeceased(false);
            com.ancientkira.mca.server.RevivalCelebration.complete(existing, player.getUUID());
            TombstoneBlock.Data.playInstantRevivalEffect((ServerLevel) existing.level(), pos);
            if (!quiet) player.sendSystemMessage(Component.translatable("gui.villager_reviver.revive.success", node.getName()), true);
            return;
        }
        if (stored != null) {
            restoreVillager(stored.entity(), stored.pos());
            if (stored.level().addFreshEntity(stored.entity())) {
                stored.tombstone().cleanupAfterRevival();
                node.setDeceased(false);
                com.ancientkira.mca.server.RevivalCelebration.complete(stored.entity(), player.getUUID());
                TombstoneBlock.Data.playInstantRevivalEffect(stored.level(), stored.pos());
                if (!quiet) player.sendSystemMessage(Component.translatable("gui.villager_reviver.revive.success", node.getName()), true);
            } else {
                if (!quiet) player.sendSystemMessage(Component.translatable("gui.villager_reviver.error.spawn_failed"), true);
            }
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        var spawnPosition = player.position().add(player.getLookAngle().scale(2.0));
        var revivalData = node.getRevivalData();
        Entity recordedVillager = revivalData
                .flatMap(data -> EntityType.create(
                        TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), data),
                        level, EntitySpawnReason.LOAD))
                .orElse(null);
        if (revivalData.isPresent()) {
            if (!(recordedVillager instanceof VillagerLike<?>)) {
                MCA.LOGGER.error("Failed to load saved villager data for revival: {}", villager);
                if (!quiet) player.sendSystemMessage(Component.translatable("gui.villager_reviver.error.spawn_failed"), true);
                return;
            }
            BlockPos revivePos = BlockPos.containing(spawnPosition);
            recordedVillager.setUUID(villager);
            restoreVillager(recordedVillager, revivePos);
            if (quiet && !placeBatchSpawn(player, recordedVillager, index)) return;
            if (level.addFreshEntity(recordedVillager)) {
                node.setDeceased(false);
                com.ancientkira.mca.server.RevivalCelebration.complete(recordedVillager, player.getUUID());
                TombstoneBlock.Data.playInstantRevivalEffect(level, revivePos);
                if (!quiet) player.sendSystemMessage(Component.translatable("gui.villager_reviver.revive.success", node.getName()), true);
            } else {
                if (!quiet) player.sendSystemMessage(Component.translatable("gui.villager_reviver.error.spawn_failed"), true);
            }
            return;
        }

        Gender gender = node.gender().binary();
        VillagerEntityMCA revived = VillagerFactory.newVillager(level)
                .withGender(gender)
                .withName(node.getName())
                .withProfession(node.getProfession())
                .withPosition(spawnPosition)
                .build(EntitySpawnReason.COMMAND);
        revived.setUUID(villager);
        revived.finalizeSpawn(level, level.getCurrentDifficultyAt(revived.blockPosition()),
                EntitySpawnReason.COMMAND, null);
        revived.setAge(0);
        BlockPos revivePos = BlockPos.containing(spawnPosition);
        restoreVillager(revived, revivePos);
        var appearanceData = node.getAppearanceData();
        if (appearanceData.isPresent()) {
            restoreAppearance(revived, appearanceData.get());
        } else {
            inheritRelativeGenetics(server, node, revived);
            revived.randomizeSkin();
        }
        if (quiet && !placeBatchSpawn(player, revived, index)) return;
        if (level.addFreshEntity(revived)) {
            node.setDeceased(false);
            com.ancientkira.mca.server.RevivalCelebration.complete(revived, player.getUUID());
            TombstoneBlock.Data.playInstantRevivalEffect(level, revivePos);
            if (!quiet) player.sendSystemMessage(Component.translatable("gui.villager_reviver.revive.success", node.getName()), true);
        } else {
            if (!quiet) player.sendSystemMessage(Component.translatable("gui.villager_reviver.error.spawn_failed"), true);
        }
    }

    private static boolean placeBatchSpawn(ServerPlayer player, Entity entity, int index) {
        ServerLevel world = player.level();
        for (int attempt = 0; attempt < 128; attempt++) {
            int cell = index + attempt;
            BlockPos base = player.blockPosition().offset((cell % 21 - 10) * 2, 0, (cell / 21 - 10) * 2);
            for (int dy : new int[] {0, 1, -1, 2, -2, 3, -3}) {
                BlockPos pos = base.offset(0, dy, 0);
                if (!world.hasChunkAt(pos) || !world.getBlockState(pos.below()).isSolid()) continue;
                entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                if (world.noCollision(entity) && world.getEntities(entity, entity.getBoundingBox(), other -> other.isAlive()).isEmpty()) return true;
            }
        }
        return false;
    }

    private static StoredVillager findStoredVillager(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            for (BlockPos pos : GraveyardManager.get(level).findAll(GraveyardManager.TombstoneState.FILLED)) {
                if (!level.getChunkSource().hasChunk(
                        Math.floorDiv(pos.getX(), 16), Math.floorDiv(pos.getZ(), 16))) {
                    continue;
                }
                TombstoneBlock.Data tombstone = TombstoneBlock.Data.of(level.getBlockEntity(pos)).orElse(null);
                if (tombstone == null || !tombstone.storesEntity(uuid)) {
                    continue;
                }
                Entity entity = tombstone.createEntity(level, false).orElse(null);
                if (entity != null) {
                    return new StoredVillager(entity, level, pos, tombstone);
                }
            }
        }
        return null;
    }

    private static void restoreVillager(Entity entity, BlockPos pos) {
        entity.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        entity.clearFire();
        entity.setPortalCooldown();
        if (entity instanceof LivingEntity living) {
            living.setHealth(living.getMaxHealth());
            living.removeAllEffects();
            living.fallDistance = 0;
            living.deathTime = 0;
        }
        if (entity instanceof AgeableMob mob) {
            mob.setAge(mob.getAge());
        }
        if (entity instanceof VillagerEntityMCA villager) {
            villager.getBrain().eraseMemory(MemoryModuleTypeMCA.LAST_GRIEVE);
            villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_SITE);
            villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_POSITION);
            villager.getBrain().eraseMemory(MemoryModuleType.PATH);
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
            villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        }
        if (entity instanceof Infectable infectable) {
            infectable.setInfectionProgress(0);
        }
    }

    private static void restoreAppearance(VillagerLike<?> villager, net.minecraft.nbt.CompoundTag data) {
        data.getString("Clothes").ifPresent(villager::setClothes);
        data.getBoolean("ClothingLocked").ifPresent(villager::setClothingLocked);
        data.getString("Skin").ifPresent(villager::setSkin);
        data.getString("Hair").ifPresent(villager::setHair);
        data.getString("HairStyle").ifPresent(villager::setHairStyleId);
        data.getString("HairBase").ifPresent(hair -> villager.setLayeredHair(
                com.ancientkira.mca.resources.data.skin.LayeredHair.Category.BASE, hair));
        data.getString("HairBangs").ifPresent(hair -> villager.setLayeredHair(
                com.ancientkira.mca.resources.data.skin.LayeredHair.Category.BANGS, hair));
        data.getString("HairBack").ifPresent(hair -> villager.setLayeredHair(
                com.ancientkira.mca.resources.data.skin.LayeredHair.Category.BACK, hair));
        data.getString("HairFront").ifPresent(hair -> villager.setLayeredHair(
                com.ancientkira.mca.resources.data.skin.LayeredHair.Category.FRONT, hair));
        data.getString("HairExtra").ifPresent(hair -> villager.setLayeredHair(
                com.ancientkira.mca.resources.data.skin.LayeredHair.Category.EXTRA, hair));
        data.getInt("SkinColor").ifPresent(villager::setSkinDye);
        data.getInt("HairColor").ifPresent(villager::setHairDye);
        data.getInt("EyeColor").ifPresent(villager::setEyeDye);
        data.getInt("EyeColorLeft").ifPresent(villager::setEyeLeftDye);
        data.getInt("Gender").ifPresent(gender -> villager.getGenetics().setGender(Gender.byId(gender)));
        for (Genetics.GeneType gene : List.of(
                Genetics.SIZE, Genetics.WIDTH, Genetics.BREAST, Genetics.MELANIN,
                Genetics.HEMOGLOBIN, Genetics.EUMELANIN, Genetics.PHEOMELANIN,
                Genetics.SKIN, Genetics.FACE, Genetics.EYE_BRIGHTNESS,
                Genetics.VOICE, Genetics.VOICE_TONE
        )) {
            data.getFloat("Gene" + gene.key()).ifPresent(value -> villager.getGenetics().setGene(gene, value));
        }
        data.getInt("AgeState").ifPresent(ordinal -> {
            AgeState[] values = AgeState.values();
            if (ordinal >= 0 && ordinal < values.length) {
                villager.setAgeState(values[ordinal]);
            }
        });
        villager.updateAttributes();
    }

    private static void inheritRelativeGenetics(MinecraftServer server, FamilyTreeNode node, VillagerEntityMCA revived) {
        inheritRelativeGenetics(server, node, (VillagerLike<?>) revived);
    }

    private static void inheritRelativeGenetics(MinecraftServer server, FamilyTreeNode node, VillagerLike<?> revived) {
        var relatives = node.getAllRelatives(3)
                .map(id -> findLoadedEntity(server, id))
                .filter(VillagerLike.class::isInstance)
                .map(entity -> (VillagerLike<?>) entity)
                .distinct()
                .limit(2)
                .toList();
        if (relatives.size() == 2) {
            revived.getGenetics().combine(relatives.get(0).getGenetics(), relatives.get(1).getGenetics());
        } else if (relatives.size() == 1) {
            revived.getGenetics().combine(relatives.getFirst().getGenetics(), relatives.getFirst().getGenetics());
        }
    }

    private static Entity findLoadedEntity(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private record StoredVillager(Entity entity, ServerLevel level, BlockPos pos, TombstoneBlock.Data tombstone) {
    }

    @Override
    public CustomPacketPayload.Type<VillagerReviverActionMessage> type() {
        return TYPE;
    }

    public enum Action {
        REVIVE,
        DELETE,
        REVIVE_ALL,
        REVIVE_VILLAGE
    }
}
