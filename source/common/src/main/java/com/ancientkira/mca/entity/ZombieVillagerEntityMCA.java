package com.ancientkira.mca.entity;

import net.minecraft.server.level.ServerLevel;
import com.ancientkira.mca.Config;
import com.ancientkira.mca.entity.ai.Genetics;
import com.ancientkira.mca.entity.ai.Relationship;
import com.ancientkira.mca.entity.ai.Traits;
import com.ancientkira.mca.entity.ai.brain.VillagerBrain;
import com.ancientkira.mca.entity.ai.relationship.AgeState;
import com.ancientkira.mca.entity.ai.relationship.CompassionateEntity;
import com.ancientkira.mca.entity.ai.relationship.Gender;
import com.ancientkira.mca.entity.interaction.ZombieCommandHandler;
import com.ancientkira.mca.registry.TagsMCA;
import com.ancientkira.mca.util.InventoryUtils;
import com.ancientkira.mca.util.network.datasync.CDataManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

public class ZombieVillagerEntityMCA extends ZombieVillager implements VillagerLike<ZombieVillagerEntityMCA>, CompassionateEntity<Relationship<ZombieVillagerEntityMCA>> {
    private static final CDataManager<ZombieVillagerEntityMCA> DATA = VillagerEntityMCA.createTrackedData(new CDataManager.Builder<>(
            ZombieVillagerEntityMCA.class,
            serializer -> SynchedEntityData.defineId(ZombieVillagerEntityMCA.class, serializer)
    )).build();
    private static final float VEHICLE_ATTACHMENT_Y = 0.6F;
    private final VillagerBrain<ZombieVillagerEntityMCA> mcaBrain = new VillagerBrain<>(this);

    private final Genetics genetics = new Genetics(this);
    private final Traits traits = new Traits(this);

    private final Relationship<ZombieVillagerEntityMCA> relations = new Relationship<>(this);

    private final ZombieCommandHandler interactions = new ZombieCommandHandler(this);
    private final UpdatableInventory inventory = new UpdatableInventory(27);
    private String chatAIPrompt = "";
    private CompoundTag nicknameData = new CompoundTag();
    private int burned;

    public ZombieVillagerEntityMCA(EntityType<? extends ZombieVillager> type, Level world, Gender gender) {
        super(type, world);
        genetics.setGender(gender);
        setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);

        getTypeDataManager().register(builder);
    }

    @Override
    public CDataManager<ZombieVillagerEntityMCA> getTypeDataManager() {
        return DATA;
    }

    @Override
    public Genetics getGenetics() {
        return genetics;
    }

    @Override
    public Traits getTraits() {
        return traits;
    }

    @Override
    public VillagerBrain<?> getVillagerBrain() {
        return mcaBrain;
    }

    @Override
    public ZombieCommandHandler getInteractions() {
        return interactions;
    }

    @Override
    public boolean isBurned() {
        return burned > 0;
    }

    @Override
    public Relationship<ZombieVillagerEntityMCA> getRelationships() {
        return relations;
    }

    @Override
    public float getInfectionProgress() {
        if (com.ancientkira.mca.compat.SickenedCompat.enabled() && getTrackedValue(VillagerLike.SICKENED))
            return getTrackedValue(VillagerLike.SICKNESS_ZOMBIE_PROGRESS);
        return 1.0f;
    }

    @Override
    public void setInfectionProgress(float progress) {
        if (com.ancientkira.mca.compat.SickenedCompat.enabled() && getTrackedValue(VillagerLike.SICKENED))
            setTrackedValue(VillagerLike.SICKNESS_ZOMBIE_PROGRESS, com.ancientkira.mca.compat.SickenedCompat.sanitize(progress));
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        Component cleaned = VillagerLike.cleanCustomName(name);
        super.setCustomName(cleaned);

        if (cleaned != null) {
            setName(cleaned.getString());
        }
    }

    @Override
    public Component getDisplayName() {
        return super.getDisplayName().copy().withStyle(com.ancientkira.mca.compat.SickenedCompat.sick(this) ? ChatFormatting.DARK_PURPLE : ChatFormatting.RED);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {

        if (pose == Pose.SLEEPING) {
            return SLEEPING_DIMENSIONS;
        }

        boolean useRawDimensions = getAgeState() == AgeState.TEEN || getAgeState() == AgeState.ADULT;
        float height = (useRawDimensions ? getRawVerticalScaleFactor() : getVerticalScaleFactor()) * 2.0F;
        float width = getHorizontalScaleFactor() * 0.6F;

        return EntityDimensions.scalable(width, height).withAttachments(EntityAttachments.builder()
                .attach(EntityAttachment.VEHICLE, 0.0F, getRawVerticalScaleFactor() * VEHICLE_ATTACHMENT_Y, 0.0F));
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == Status.MCA_VILLAGER_GREETING) {
            com.ancientkira.mca.ClientProxy.playVillagerGreeting(this);
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public final InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand.equals(InteractionHand.MAIN_HAND) && !stack.is(TagsMCA.Items.ZOMBIE_EGGS) && stack.getItem() != Items.GOLDEN_APPLE) {
            if (!level().isClientSide() && getAgeState() != AgeState.BABY) {
                level().broadcastEntityEvent(this, Status.MCA_VILLAGER_GREETING);
            }
            if (player instanceof ServerPlayer) {
                sendChatMessage(player, "zombie.greeting");
            }
        }
        return super.mobInteract(player, hand);
    }

    @Nullable
    @Override
    @SuppressWarnings("deprecation")
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData entityData) {
        SpawnGroupData data = super.finalizeSpawn(world, difficulty, spawnReason, entityData);

        if (getAgeState() == AgeState.UNASSIGNED) {
            if (random.nextFloat() < Config.getInstance().babyZombieChance) {
                setAgeState(isBaby() ? AgeState.BABY : AgeState.random());
            } else {
                setAgeState(AgeState.ADULT);
            }
        }

        initialize(spawnReason);
        setPersistenceRequired();

        return data;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onOffspringSpawnedFromEgg(Player player, Mob child) {
        ServerLevelAccessor serverLevel = (ServerLevelAccessor) level();
        child.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(child.blockPosition()), EntitySpawnReason.SPAWN_ITEM_USE, null);
    }

    @Override
    public void travel(net.minecraft.world.phys.Vec3 input) {
        if (isSleeping()) {
            setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            setXxa(0); setYya(0); setZza(0);
            return;
        }
        super.travel(input);
    }

    private long nextPlea;

    @Override
    public void aiStep() {
        if (isPassiveBaby()) {
            setTarget(null);
            setAggressive(false);
        }
        if (isSleeping()) {
            getNavigation().stop();
            setTarget(null);
            setJumping(false);
            setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            setXxa(0); setYya(0); setZza(0);
        }
        super.aiStep();

        if (level() instanceof ServerLevel world) {
            com.ancientkira.mca.server.InfirmaryCare.tickZombie(this);
            if (com.ancientkira.mca.server.InfirmaryCare.isZombiePatient(this)) {
                setRemainingFireTicks(0);
            }
            if (tickCount % 20 == 0 && world.getGameTime() >= nextPlea) {
                boolean threatened = world.getEntitiesOfClass(VillagerEntityMCA.class, getBoundingBox().inflate(16),
                        person -> person.isGuard() && person.isAlive() && (person.getTarget() == this
                            || person.getBrain().getMemoryInternal(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET).orElse(null) == this)).size() > 0;
                if (threatened) {
                    nextPlea = world.getGameTime() + 120 + getRandom().nextInt(100);
                    sendChatToAllAround("zombie.plead");
                }
            }
        }
        burned--;
        if (isOnFire()) {
            burned = Config.getInstance().burnedClothingTickLength;
        }
        if (burned > 0) {
            spawnBurntParticles();
        }
    }

    private long nextHurtSpeech;

    @Override
    public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(world, source, amount);
        if (hurt && amount > 0 && isAlive() && world.getGameTime() >= nextHurtSpeech) {
            nextHurtSpeech = world.getGameTime() + 20;
            sendChatToAllAround(getHealth() <= 4.0F ? "villager.badly_hurt" : "villager.hurt");
        }
        return hurt;
    }

    public boolean isPassiveBaby() {
        return isBaby() || getAgeState() == AgeState.BABY;
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(isPassiveBaby() ? null : target);
    }

    @Override
    public void setAggressive(boolean aggressive) {
        super.setAggressive(aggressive && !isPassiveBaby());
    }

    @Override
    public boolean doHurtTarget(ServerLevel world, Entity target) {
        return !isPassiveBaby() && super.doHurtTarget(world, target);
    }

    @Override
    public boolean isHostile() {
        return !isPassiveBaby() && !isSleeping() && !com.ancientkira.mca.server.InfirmaryCare.isZombiePatient(this);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (isPassiveBaby() || isSleeping() || com.ancientkira.mca.server.InfirmaryCare.isZombiePatient(this)) {
            return false;
        }
        return super.canAttack(target);
    }

    @Override
    public void die(DamageSource cause) {
        // A faction conversion is not a family death and must not drop copied inventory.
        if (com.ancientkira.mca.compat.SickenedCompat.onDeath(this, cause)) return;
        super.die(cause);

        if (level().isClientSide()) {
            return;
        }

        relations.onDeath(cause);
        InventoryUtils.dropAllItems(this, inventory);


    }

    public void setInventory(UpdatableInventory inventory) {
        CompoundTag nbt = new CompoundTag();
        InventoryUtils.saveToNBT(this.registryAccess(), inventory, nbt);
        InventoryUtils.readFromNBT(this.registryAccess(), this.inventory, nbt);
    }

    @SuppressWarnings({"unchecked", "rawtypes", "RedundantSuppression"})
    @Override
    @Nullable
    public <T extends Mob> T convertTo(EntityType<T> type, ConversionParams params, ConversionParams.AfterConversion<T> afterConversion) {
        EntityType<? extends Mob> convertedType = !isRemoved() && type.equals(EntityType.VILLAGER) ? getGenetics().getGender().getVillagerType() : type;

        UUID oldUuid = getUUID();

        return (T) super.convertTo((EntityType) convertedType, params, mob -> {
            ((ConversionParams.AfterConversion) afterConversion).finalizeConversion(mob);

            if (mob instanceof VillagerLike<?> villager) {
                villager.copyVillagerAttributesFrom(this);
                if (mob instanceof VillagerEntityMCA) {
                    villager.setInfected(false);
                    com.ancientkira.mca.compat.SickenedCompat.afterZombieCure(this, villager);
                }
                mob.setUUID(oldUuid);
            }

            if (mob instanceof VillagerEntityMCA villager) {
                villager.setUUID(oldUuid);
                villager.setInventory(inventory);
                villager.setAge(getAgeState().toAge());
            }

            if (mob instanceof ZombieVillagerEntityMCA zombie) {
                zombie.setInventory(inventory);
                zombie.setVillagerData(getVillagerData());
                zombie.setVillagerXp(getVillagerXp());
                zombie.setPersistenceRequired();
            }

            com.ancientkira.mca.server.InfirmaryCare.dischargeZombie(this);
            this.discard();
        });
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        CompoundTag nbt = VillagerEntityMCA.readMcaSaveData(input);
        super.readAdditionalSaveData(input);
        getTypeDataManager().load(this, nbt);
        relations.readFromNbt(nbt);
        chatAIPrompt = nbt.getString(VillagerEntityMCA.CHAT_AI_PROMPT_KEY).orElse("");
        nicknameData = nbt.getCompound(VillagerEntityMCA.NICKNAMES_KEY).orElseGet(CompoundTag::new).copy();

        updateAttributes();

        inventory.clearContent();
        InventoryUtils.readFromNBT(this.registryAccess(), inventory, nbt);

        validateClothes();
    }

    @Override
    public void writeAdditionalConversionData(CompoundTag output) {
        output.putString(VillagerEntityMCA.CHAT_AI_PROMPT_KEY, chatAIPrompt);
        output.put(VillagerEntityMCA.NICKNAMES_KEY, nicknameData.copy());
    }

    @Override
    public void readAdditionalConversionData(CompoundTag input) {
        chatAIPrompt = input.getString(VillagerEntityMCA.CHAT_AI_PROMPT_KEY).orElse("");
        nicknameData = input.getCompound(VillagerEntityMCA.NICKNAMES_KEY).orElseGet(CompoundTag::new).copy();
    }

    @Override
    public void readAdditionalSaveDataForEditor(CompoundTag nbt) {
        readAdditionalSaveData(TagValueInput.create(ProblemReporter.DISCARDING, registryAccess(), nbt));
        VillagerLike.parseCustomName(registryAccess(), nbt).ifPresent(this::setCustomName);
    }

    @Override
    public final void addAdditionalSaveData(ValueOutput output) {
        CompoundTag nbt = new CompoundTag();
        super.addAdditionalSaveData(output);
        getTypeDataManager().save(this, nbt);
        relations.writeToNbt(nbt);
        InventoryUtils.saveToNBT(this.registryAccess(), inventory, nbt);
        nbt.putString(VillagerEntityMCA.CHAT_AI_PROMPT_KEY, chatAIPrompt);
        nbt.put(VillagerEntityMCA.NICKNAMES_KEY, nicknameData.copy());
        VillagerEntityMCA.storeMcaSaveData(output, nbt);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> par) {
        if (getTypeDataManager().isParam(AGE_STATE, par) || getTypeDataManager().isParam(Genetics.SIZE.getParam(), par)) {
            refreshDimensions();
        }

        super.onSyncedDataUpdated(par);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    public void startConvertingByCleric(@Nullable UUID clericId, int conversionDelay) {
        ((com.ancientkira.mca.mixin.ZombieVillagerAccessor) this).mca$startConverting(clericId, conversionDelay);
    }

    protected boolean shouldDespawnInPeaceful() {
        return !isPersistenceRequired();
    }
}
