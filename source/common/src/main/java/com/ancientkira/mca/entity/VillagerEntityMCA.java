package com.ancientkira.mca.entity;

import com.mojang.serialization.MapCodec;
import com.ancientkira.mca.Config;
import com.ancientkira.mca.MCA;
import com.ancientkira.mca.server.PillagerRaids;
import com.ancientkira.mca.MCAClient;
import com.ancientkira.mca.entity.ai.*;
import com.ancientkira.mca.entity.ai.brain.VillagerBrain;
import com.ancientkira.mca.entity.ai.brain.VillagerTasksMCA;
import com.ancientkira.mca.entity.ai.chatAI.ChatAIContext;
import com.ancientkira.mca.entity.ai.navigation.MCAGroundPathNavigation;
import com.ancientkira.mca.entity.ai.relationship.*;
import com.ancientkira.mca.entity.interaction.VillagerCommandHandler;
import com.ancientkira.mca.registry.*;
import com.ancientkira.mca.resources.Names;
import com.ancientkira.mca.resources.Rank;
import com.ancientkira.mca.resources.Tasks;
import com.ancientkira.mca.server.world.data.FamilyTree;
import com.ancientkira.mca.server.world.data.FamilyTreeNode;
import com.ancientkira.mca.server.world.data.Village;
import com.ancientkira.mca.server.world.data.Building;
import com.ancientkira.mca.server.world.data.VillageManager;
import com.ancientkira.mca.server.world.data.VillagerTrackerManager;
import com.ancientkira.mca.util.InventoryUtils;
import com.ancientkira.mca.util.network.datasync.CDataManager;
import com.ancientkira.mca.util.network.datasync.CDataParameter;
import com.ancientkira.mca.util.network.datasync.CParameter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;


public class VillagerEntityMCA extends Villager implements VillagerLike<VillagerEntityMCA>, MenuProvider, CompassionateEntity<BreedableRelationship>, CrossbowAttackMob {
    private static final float FRIENDLY_ARROW_UNCERTAINTY = 2.0F;
    private static final CDataParameter<Float> INFECTION_PROGRESS = CParameter.create("InfectionProgress", 0.0f);
    private static final CDataParameter<Integer> GROWTH_AMOUNT = CParameter.create("GrowthAmount", -AgeState.getMaxAge());
    private static final float VEHICLE_ATTACHMENT_Y = 0.6F;
    public static final String MCA_DATA_KEY = "MCAData";
    public static final int MAX_NICKNAME_LENGTH = 32;
    static final String CHAT_AI_PROMPT_KEY = "ChatAIPrompt";
    static final String NICKNAMES_KEY = "nicknames";
    private static final CDataManager<VillagerEntityMCA> DATA = createTrackedData(new CDataManager.Builder<>(
            VillagerEntityMCA.class,
            serializer -> SynchedEntityData.defineId(VillagerEntityMCA.class, serializer)
    )).build();
    private static final EntityDataAccessor<Boolean> CASTING_SPELL = SynchedEntityData.defineId(VillagerEntityMCA.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CAPTIVE = SynchedEntityData.defineId(VillagerEntityMCA.class, EntityDataSerializers.BOOLEAN);
    private long shieldDisabledUntil;
    private long kidnappedUntil;
    private int traderLlamaCount;
    private CompoundTag captivityState = new CompoundTag();
    private static final int RECALCULATE_DIMENSIONS_EVERY_N_TICKS = 100;
    public final ConversationManager conversationManager = new ConversationManager(this);
    private String chatAIPrompt = "";
    final Identifier EXTRA_HEALTH_EFFECT_ID = MCA.locate("trait_health");
    private final VillagerBrain<VillagerEntityMCA> mcaBrain = new VillagerBrain<>(this);
    private final LongTermMemory longTermMemory = new LongTermMemory(this);
    private final Genetics genetics = new Genetics(this);
    private final Traits traits = new Traits(this);
    private final Residency residency = new Residency(this);
    private final BreedableRelationship relations = new BreedableRelationship(this);
    private final VillagerCommandHandler interactions = new VillagerCommandHandler(this);
    private final UpdatableInventory inventory = new UpdatableInventory(27);
    private final VillagerDimensions.Mutable dimensions = new VillagerDimensions.Mutable(AgeState.UNASSIGNED);
    private final ArcherMoveControl archerMoveControl;
    long lastCooldown = 0L;
    private PlayerModel playerModel;
    private int despawnDelay;
    private int burned;
    private long lastHit = 0;
    private int prevGrowthAmount;
    private boolean interactedWith;
    private int lastAppliedHealthLevel = Integer.MIN_VALUE;
    private double lastAppliedHealthBonus = Double.NaN;

    @SuppressWarnings("deprecation")
    public static CompoundTag readMcaSaveData(ValueInput input) {
        return input.read(MCA_DATA_KEY, CompoundTag.CODEC)
                .or(() -> input.read(MapCodec.assumeMapUnsafe(CompoundTag.CODEC)))
                .orElseGet(CompoundTag::new);
    }

    public static void storeMcaSaveData(ValueOutput output, CompoundTag nbt) {
        output.store(MCA_DATA_KEY, CompoundTag.CODEC, nbt);
    }
    private final Map<UUID, String> nicknames = new HashMap<>();

    public VillagerEntityMCA(EntityType<VillagerEntityMCA> type, Level w, Gender gender) {
        super(type, w);
        this.archerMoveControl = new ArcherMoveControl(this);
        this.moveControl = this.archerMoveControl;
        genetics.setGender(gender);
        this.setPathfindingMalus(PathType.WATER_BORDER, 16.0F);
        this.setPathfindingMalus(PathType.TRAPDOOR, 8.0F);
        this.setPathfindingMalus(PathType.ON_TOP_OF_TRAPDOOR, 8.0F);
        this.getNavigation().setRequiredPathLength((float) Config.getInstance().getVillagerPathfindingDistance());
    }

    public ArcherMoveControl getArcherMoveControl() {
        return archerMoveControl;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new MCAGroundPathNavigation(this, level);
    }

    @Override
    public void setJumping(boolean jumping) {
        boolean navigationControlsClimb = this.getNavigation() instanceof MCAGroundPathNavigation navigation
                && navigation.isControllingClimbable();
        super.setJumping(jumping && !navigationControlsClimb);
    }

    public static <E extends Entity> CDataManager.Builder<E> createTrackedData(CDataManager.Builder<E> builder) {
        return VillagerLike.createTrackedData(builder).addAll(INFECTION_PROGRESS, GROWTH_AMOUNT)
                .add(Residency::createTrackedData)
                .add(BreedableRelationship::createTrackedData);
    }

    private static boolean canEat(ItemStack i) {
        return canEat(i, i.get(DataComponents.FOOD));
    }

    private static boolean canEat(ItemStack i, @Nullable FoodProperties foodProperties) {
        return foodProperties != null
                && foodProperties.nutrition() > 0
                && !hasDangerousConsumeEffects(i)
                && !hasDangerousStewEffects(i);
    }

    private static boolean hasDangerousConsumeEffects(ItemStack stack) {
        Consumable consumable = stack.get(DataComponents.CONSUMABLE);
        if (consumable == null) {
            return false;
        }

        return consumable.onConsumeEffects().stream()
                .filter(ApplyStatusEffectsConsumeEffect.class::isInstance)
                .map(ApplyStatusEffectsConsumeEffect.class::cast)
                .flatMap(effect -> effect.effects().stream())
                .map(MobEffectInstance::getEffect)
                .anyMatch(StatusEffectDangerSet.IS_DANGER::contains);
    }

    private static boolean hasDangerousStewEffects(ItemStack stack) {
        SuspiciousStewEffects stewEffects = stack.get(DataComponents.SUSPICIOUS_STEW_EFFECTS);
        if (stewEffects == null) {
            return false;
        }

        return stewEffects.effects().stream()
                .map(SuspiciousStewEffects.Entry::effect)
                .anyMatch(StatusEffectDangerSet.IS_DANGER::contains);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Villager.createAttributes()
                .add(Attributes.ATTACK_DAMAGE, 3.0f)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0f)
                .add(Attributes.MAX_HEALTH, Config.getInstance().villagerMaxHealth)
                .add(Attributes.FOLLOW_RANGE, Config.getInstance().getVillagerFollowRange());
    }

    @Override
    public CDataManager<VillagerEntityMCA> getTypeDataManager() {
        return DATA;
    }

    @Override
    public PlayerModel getPlayerModel() {
        return playerModel;
    }

    @Override
    public boolean isBurned() {
        return burned > 0;
    }

    @Override
    public void restock() {
        super.restock();

        if (!level().isClientSide()) {
            Optional<Village> village = residency.getHomeVillage();
            if (village.isPresent() && Config.getInstance().villagerRestockNotification) {
                village.get().broadCastMessage((ServerLevel) level(), "events.restock", getName().getString());
            }
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);

        getTypeDataManager().register(builder);
        builder.define(CASTING_SPELL, false);
        builder.define(CAPTIVE, false);
    }

    public void setCastingSpell(boolean casting) {
        entityData.set(CASTING_SPELL, casting);
    }

    public boolean isCastingSpell() {
        return entityData.get(CASTING_SPELL) && getProfession() == ProfessionsMCA.EVOKER && !isPeacefulRaider();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Brain<Villager> makeBrain(Brain.Packed packedBrain) {
        return (Brain<Villager>) (Brain<?>) VillagerTasksMCA.initializeTasks(this, VillagerTasksMCA.createProfile().makeBrain(this, packedBrain));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void refreshBrain(ServerLevel world) {
        Brain<VillagerEntityMCA> brain = getMCABrain();
        Optional<Player> followingPlayer = brain.getMemoryInternal(MemoryModuleTypeMCA.PLAYER_FOLLOWING);
        brain.stopAll(world, this);
        this.brain = VillagerTasksMCA.createProfile().makeBrain(this, brain.pack());
        followingPlayer.ifPresent(player -> getMCABrain().setMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING, player));
        VillagerTasksMCA.initializeTasks(this, getMCABrain());
    }

    @SuppressWarnings("unchecked")
    public Brain<VillagerEntityMCA> getMCABrain() {
        return (Brain<VillagerEntityMCA>) brain;
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
    public HumanoidArm getMainArm() {
        return getTraits().hasTrait(Traits.LEFT_HANDED) ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
    }

    @Override
    public BreedableRelationship getRelationships() {
        return relations;
    }

    @Override
    public VillagerBrain<?> getVillagerBrain() {
        return mcaBrain;
    }

    public LongTermMemory getLongTermMemory() {
        return longTermMemory;
    }

    public Residency getResidency() {
        return residency;
    }

    @Override
    public VillagerCommandHandler getInteractions() {
        return interactions;
    }

    @Override
    protected Component getTypeName() {
        return getProfessionText();
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnType, SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, groupData);

        initialize(spawnType);

        setAgeState(AgeState.byCurrentAge(getAge()));

        FamilyTreeNode entry = getRelationships().getFamilyEntry();
        if (!FamilyTreeNode.isValid(entry.father()) && !FamilyTreeNode.isValid(entry.mother())) {
            FamilyTree tree = FamilyTree.get(level.getLevel());
            FamilyTreeNode father = tree.getOrCreate(UUID.randomUUID(), Names.pickCitizenName(Gender.MALE), Gender.MALE);
            FamilyTreeNode mother = tree.getOrCreate(UUID.randomUUID(), Names.pickCitizenName(Gender.FEMALE), Gender.FEMALE);
            father.setDeceased(true);
            mother.setDeceased(true);
            entry.setFather(father);
            entry.setMother(mother);
        }

        return data;
    }

    public final VillagerProfession getProfession() {
        return getVillagerData().profession().value();
    }

    public final void setProfession(VillagerProfession profession) {
        if (profession == ProfessionsMCA.CULTIST) profession = ProfessionsMCA.WANDERING_TRADER;
        boolean settlingTrader = getProfession() == ProfessionsMCA.WANDERING_TRADER && profession != ProfessionsMCA.WANDERING_TRADER;
        if (ProfessionsMCA.isRaider(getProfession()) && !ProfessionsMCA.isRaider(profession)) {
            com.ancientkira.mca.server.PillagerRaids.leaveProfession(this);
            var speed = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            if (speed != null) speed.removeModifier(com.ancientkira.mca.MCA.locate("raider_pursuit_speed"));
            if (level() instanceof ServerLevel world && world.getEntity(getUUID()) == this) {
                getResidency().getRegistryVillage().flatMap(Village::getCivilRegistry)
                        .ifPresent(registry -> registry.addText(Component.translatable("mca.raider.left", getName())));
                world.getServer().getPlayerList().broadcastSystemMessage(
                        Component.translatable("mca.raider.left", getName()), false);
            }
        }
        setVillagerData(getVillagerData().withProfession(BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(profession)));
        if (settlingTrader) {
            settleTraderLlamas();
            forceRandomizeClothes();
            offers = null;
        }
        refreshBrain((ServerLevel) level());
    }

    @Override
    public Identifier getProfessionId() {
        return BuiltInRegistries.VILLAGER_PROFESSION.getKey(getProfession());
    }

    private ResourceKey<VillagerProfession> getProfessionKey() {
        return getVillagerData().profession().unwrapKey().orElse(VillagerProfession.NONE);
    }

    @Override
    public boolean isProfessionImportant() {
        return ProfessionsMCA.IS_IMPORTANT.contains(getProfessionKey());
    }

    @Override
    public boolean requiresHome() {
        return (isPeacefulRaider() || !ProfessionsMCA.NEEDS_NO_HOME.contains(getProfessionKey())) && getDespawnDelay() <= 0;
    }

    @Override
    public boolean canTradeWithProfession() {
        return !ProfessionsMCA.CAN_NOT_TRADE.contains(getProfessionKey()) || (offers != null && !offers.isEmpty());
    }

    @Override
    public void setVillagerData(VillagerData data) {
        boolean hasChanged = !level().isClientSide() && getProfession() != data.profession().value() && data.profession().value() != ProfessionsMCA.OUTLAW;
        boolean leavingRaiders = ProfessionsMCA.isRaider(getProfession()) && !ProfessionsMCA.isRaider(data.profession().value());
        super.setVillagerData(data);
        if (hasChanged) {
            if (!leavingRaiders) randomizeClothes();
            getRelationships().getFamilyEntry().setProfession(data.profession().value());
        }
    }

    @Override
    protected int getBabyStartAge() {
        return -AgeState.getMaxAge();
    }

    @Override
    public void setAge(int age) {
        super.setAge(age);

        // high quality iguana tweaks reborn LivestockSlowdownFeature fix
        if (age != -2) {
            setTrackedValue(GROWTH_AMOUNT, age);
            setAgeState(AgeState.byCurrentAge(age));

            AgeState current = getAgeState();

            AgeState next = current.getNext();
            if (current != next) {
                dimensions.interpolate(current, next, AgeState.getDelta(age));
            } else {
                dimensions.set(current);
            }
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (target == this || target.getUUID().equals(getUUID())) return false;
        boolean hurt = super.doHurtTarget(level, target);
        if (hurt) {
            attackedEntity(target);
        }
        return hurt;
    }

    public void onRangedAttackLanded(Entity target) {
        attackedEntity(target);
    }

    private void attackedEntity(Entity target) {
        if (target instanceof Player player) {
            pardonPlayers(player);
        }
    }

    /**
     * decrease the personal bounty counter by one
     */
    private void pardonPlayers() {
        pardonPlayers(1);
    }

    public void pardonPlayers(int amount) {
        int bounty = getSmallBounty();
        if (bounty <= amount) {
            getBrain().eraseMemory(MemoryModuleTypeMCA.SMALL_BOUNTY);
            if (getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).filter(Player.class::isInstance).isPresent()) {
                getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
            getBrain().eraseMemory(MemoryModuleTypeMCA.HIT_BY_PLAYER);
        } else {
            getBrain().setMemory(MemoryModuleTypeMCA.SMALL_BOUNTY, bounty - amount);
        }
    }

    private void pardonPlayers(Player attacker) {
        pardonPlayers();
        int bounty = getSmallBounty();
        if (bounty <= getMaxWarnings(attacker)) {
            if (getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).filter(Player.class::isInstance).isPresent()) {
                getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
        }
    }

    public boolean canInteractWithItemStackInHand(ItemStack stack) {
        return stack.getItem() != ItemsMCA.VILLAGER_EDITOR
               && stack.getItem() != ItemsMCA.NEEDLE_AND_THREAD
               && stack.getItem() != ItemsMCA.COMB
               && stack.getItem() != ItemsMCA.POTION_OF_FEMININITY
               && stack.getItem() != ItemsMCA.POTION_OF_MASCULINITY;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 pos) {
        // This allows hitbox interactions to be ignored if the player is carrying a child villager.
        if (isKidnapped()) {
            if (!level().isClientSide()) rescueFromKidnapping();
            return InteractionResult.SUCCESS;
        }
        if (getVehicle() != null && getVehicle().equals(player)) return InteractionResult.PASS;

        ItemStack stack = player.getItemInHand(hand);
        // Editor tools must take priority over dialogue and vanilla mob interactions.
        if (stack.is(ItemsMCA.VILLAGER_EDITOR) && isAlive()) {
            return stack.interactLivingEntity(player, this, hand);
        }
        boolean isOnBlacklist = Config.getInstance().villagerInteractionItemBlacklist.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (hand.equals(InteractionHand.MAIN_HAND) && !isOnBlacklist && !stack.is(TagsMCA.Items.VILLAGER_EGGS) && canInteractWithItemStackInHand(stack) && !getVillagerBrain().isPanicking()) {
            //make sure dialogueType is synced in case the client needs it
            getDialogueType(player);

            if (player.isShiftKeyDown()) {
                if (!level().isClientSide() && canTradeWithProfession()) {
                    getInteractions().stopInteracting();
                    startTrading(player);
                }
            } else {
                playWelcomeSound();
                interactedWith = true;
                return interactions.interactAt(player, pos, hand);
            }
        }
        return super.interact(player, hand, pos);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        // This allows hitbox interactions to be ignored if the player is carrying a child villager.
        if (isKidnapped()) {
            if (!level().isClientSide()) rescueFromKidnapping();
            return InteractionResult.SUCCESS;
        }
        if (getVehicle() != null && getVehicle().equals(player)) return InteractionResult.PASS;

        ItemStack stack = player.getItemInHand(hand);
        // Editor tools must take priority over dialogue and vanilla mob interactions.
        if (stack.is(ItemsMCA.VILLAGER_EDITOR) && isAlive()) {
            return stack.interactLivingEntity(player, this, hand);
        }
        if (!stack.is(TagsMCA.Items.VILLAGER_EGGS) && isAlive() && !isTrading() && !isSleeping() && canInteractWithItemStackInHand(stack) && !getVillagerBrain().isPanicking()) {
            if (isBaby()) {
                playNoTradeResponse();
            } else if (!level().isClientSide()) {
                boolean hasOffers = hasTradeOffers();
                if (hand == InteractionHand.MAIN_HAND) {
                    if (!hasOffers && !level().isClientSide()) {
                        playNoTradeResponse();
                    }

                    player.awardStat(Stats.TALKED_TO_VILLAGER);
                }

                if (hasOffers && !level().isClientSide()) {
                    startTrading(player);
                }
            }
            return level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    public void disableCombatShield(int ticks) {
        if (ticks > 0) shieldDisabledUntil = Math.max(shieldDisabledUntil, level().getGameTime() + ticks);
    }

    public boolean usesCombatShield() {
        return getProfession() == ProfessionsMCA.GUARD || getProfession() == ProfessionsMCA.VINDICATOR;
    }

    public boolean isUsingCombatShield() {
        return isUsingItem() && getUseItem().has(DataComponents.BLOCKS_ATTACKS);
    }

    private void tickCombatShield() {
        if (!usesCombatShield() || isPeacefulRaider() || isNoAi() || level().getGameTime() < shieldDisabledUntil) {
            if (isUsingCombatShield()) stopUsingItem();
            return;
        }
        if (!getMainHandItem().has(DataComponents.BLOCKS_ATTACKS) && !getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)) return;
        LivingEntity enemy = getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).orElse(null);
        Entity arrow = level().getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class,
                        getBoundingBox().inflate(16), projectile -> projectile.getOwner() != this
                                && incomingArrowTime(projectile.position(), projectile.getDeltaMovement(),
                                    position().add(0, getBbHeight() * 0.5, 0), getBbWidth() * 0.5 + 0.8) >= 0
                                && hasLineOfSight(projectile))
                .stream().min(java.util.Comparator.comparingDouble(projectile ->
                        incomingArrowTime(projectile.position(), projectile.getDeltaMovement(),
                                position().add(0, getBbHeight() * 0.5, 0), getBbWidth() * 0.5 + 0.8))).orElse(null);
        Entity threat = arrow != null ? arrow : enemy;
        boolean defend = arrow != null || enemy != null && enemy.isAlive() && distanceToSqr(enemy) <= 144
                && hasLineOfSight(enemy) && tickCount % 30 < 20;
        if (!defend) {
            if (isUsingCombatShield()) stopUsingItem();
            return;
        }
        getLookControl().setLookAt(threat, 90.0F, 90.0F);
        if (arrow != null) {
            float facing = (float) (Math.atan2(arrow.getZ() - getZ(), arrow.getX() - getX()) * 180 / Math.PI) - 90;
            setYRot(facing);
            setYBodyRot(facing);
            setYHeadRot(facing);
            if (isUsingItem() && !isUsingCombatShield()) stopUsingItem();
        }
        if (isUsingItem()) return;
        for (InteractionHand hand : InteractionHand.values()) {
            if (getItemInHand(hand).has(DataComponents.BLOCKS_ATTACKS)) {
                startUsingItem(hand);
                return;
            }
        }
    }

    private static double incomingArrowTime(Vec3 arrow, Vec3 velocity, Vec3 target, double radius) {
        double speedSquared = velocity.lengthSqr();
        if (speedSquared < 0.01) return -1;
        double time = target.subtract(arrow).dot(velocity) / speedSquared;
        if (time < 0 || time > 12) return -1;
        Vec3 predicted = arrow.add(velocity.scale(time)).add(0, -0.025 * time * time, 0);
        return predicted.distanceToSqr(target) <= radius * radius ? time : -1;
    }

    private void prepareTotem() {
        if (isKidnapped() || isNoAi() || getMainHandItem().has(DataComponents.DEATH_PROTECTION)
                || getOffhandItem().has(DataComponents.DEATH_PROTECTION)) return;
        var bag = getInventory();
        for (int slot = 0; slot < bag.getContainerSize(); slot++) {
            ItemStack supplied = bag.getItem(slot);
            if (!supplied.has(DataComponents.DEATH_PROTECTION)) continue;
            ItemStack replaced = getItemBySlot(getOpposingSlot());
            boolean alreadyStored = false;
            boolean hasRoom = supplied.getCount() == 1 || replaced.isEmpty();
            for (int other = 0; other < bag.getContainerSize(); other++) {
                if (bag.getItem(other) == replaced) alreadyStored = true;
                if (bag.getItem(other).isEmpty()) hasRoom = true;
            }
            if (!hasRoom && !alreadyStored) continue;
            if (isUsingCombatShield()) stopUsingItem();
            ItemStack totem = bag.removeItem(slot, 1);
            if (!replaced.isEmpty() && !alreadyStored) bag.addItem(replaced);
            setItemSlot(getOpposingSlot(), totem);
            return;
        }
    }

    @Override
    protected void blockUsingItem(ServerLevel level, LivingEntity attacker) {
        super.blockUsingItem(level, attacker);
        ItemStack shield = getItemBlockingWith();
        var blocking = shield == null ? null : shield.get(DataComponents.BLOCKS_ATTACKS);
        float seconds = attacker.getSecondsToDisableBlocking();
        if (blocking != null && seconds > 0.0F) {
            int ticks = Math.round(seconds * blocking.disableCooldownScale() * 20.0F);
            if (ticks > 0) {
                shieldDisabledUntil = Math.max(shieldDisabledUntil, level.getGameTime() + ticks);
                blocking.disable(level, this, seconds, shield);
            }
        }
    }

    @Override
    public float applyItemBlocking(ServerLevel level, DamageSource source, float damage) {
        ItemStack shield = getItemBlockingWith();
        var blocking = shield == null ? null : shield.get(DataComponents.BLOCKS_ATTACKS);
        InteractionHand hand = getUsedItemHand();
        float blocked = super.applyItemBlocking(level, source, damage);
        if (blocking != null && blocked > 0) {
            int wear = blocking.itemDamage().apply(blocked);
            if (wear > 0) shield.hurtAndBreak(wear, this,
                    hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
        return blocked;
    }

    public boolean hasTradeOffers() {
        return !getOffers().isEmpty();
    }

    @Override
    public MerchantOffers getOffers() {
        MerchantOffers offers = super.getOffers();
        if (getProfession() == ProfessionsMCA.WANDERING_TRADER && offers.isEmpty() && !level().isClientSide()) {
            var template = EntityType.WANDERING_TRADER.create(level(), EntitySpawnReason.EVENT);
            if (template != null) {
                offers = template.getOffers();
                setOffers(offers);
            }
        }
        int previousSize = offers.size();
        offers.removeIf(offer -> offer.getResult().isEmpty());

        int removed = previousSize - offers.size();
        if (removed > 0) {
            MCA.LOGGER.warn("Removed {} invalid villager trade(s) with empty result for villager {} (profession {}, level {})",
                    removed, getUUID(), getProfessionId(), getVillagerData().level());
        }
        return offers;
    }

    @Override
    public VillagerEntityMCA getBreedOffspring(ServerLevel level, AgeableMob partner) {
        VillagerEntityMCA child = partner instanceof VillagerEntityMCA partnerVillager
                ? relations.getPregnancy().createChild(Gender.getRandom(), partnerVillager)
                : relations.getPregnancy().createChild(Gender.getRandom());

        child.setVillagerData(child.getVillagerData().withType(getRandomType(partner)));

        child.finalizeSpawn(level, level.getCurrentDifficultyAt(child.blockPosition()), EntitySpawnReason.BREEDING, null);
        return child;
    }

    @Override
    protected void onOffspringSpawnedFromEgg(Player player, Mob child) {
        super.onOffspringSpawnedFromEgg(player, child);

        if (child instanceof VillagerEntityMCA villager && villager.getCustomName() == null) {
            villager.setCustomName(Component.literal(villager.getRelationships().getFamilyEntry().getName()));
        }
    }

    private Holder<VillagerType> getRandomType(AgeableMob partner) {
        double d = random.nextDouble();

        if (d < 0.5D) {
            return level().registryAccess().getOrThrow(VillagerType.byBiome(level().getBiome(blockPosition())));
        }

        if (d < 0.75D) {
            return getVillagerData().type();
        }

        return ((Villager) partner).getVillagerData().type();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damageAmount) {
        if (isKidnapped() || source.getEntity() == this) return false;
        // no baby squishes
        if (getVehicle() instanceof Player) {
            return super.hurtServer(level, source, 0.0f);
        }

        // you can't hit babies!
        // TODO: Verify the `isUnblockable` replacement for 1.19.4, ensure same behavior
        if (!Config.getInstance().canHurtBabies && !source.is(DamageTypeTags.BYPASSES_SHIELD) && getAgeState() == AgeState.BABY) {
            if (source.getEntity() instanceof Player && requestCooldown()) {
                sendEventMessage(Component.translatable("villager.baby_hit"));
            }
            return super.hurtServer(level, source, 0.0f);
        }

        // Guards take 50% less damage
        if (getProfession() == ProfessionsMCA.GUARD) {
            damageAmount *= 0.5f;
        }

        if (getTraits().hasTrait(Traits.TOUGH)) {
            damageAmount *= 0.75f;
        }

        if (!level().isClientSide()) {
            //scream and loose hearts
            if (source.getEntity() instanceof Player player) {
                if (level().getGameTime() - lastHit > 40) {
                    lastHit = level().getGameTime();
                    if (!isGuard() && !ProfessionsMCA.isRaider(getProfession()) && requestCooldown()) {
                        if (getHealth() < getMaxHealth() / 2) {
                            sendChatMessage(player, "villager.badly_hurt");
                        } else {
                            sendChatMessage(player, "villager.hurt");
                        }
                    }
                }

                //loose hearts, the weaker the villager, the more it is scared. The first hit might be an accident.
                int trustIssues = (int) ((1.0 - getHealth() / getMaxHealth() * 0.75) * (3.0 + 2.0 * damageAmount));
                getVillagerBrain().getMemoriesForPlayer(player).modHearts(-trustIssues);
            }

            //infect the villager
            if (source.getDirectEntity() instanceof Zombie
                && getProfession() != ProfessionsMCA.GUARD
                && Config.getInstance().enableInfection
                && random.nextFloat() < Config.getInstance().zombieBiteInfectionChance
                && random.nextFloat() > (getVillagerData().level() - 1) * Config.getInstance().infectionChanceDecreasePerLevel
                && (getResidency().getHomeVillage().filter(v -> v.hasBuilding("infirmary")).isEmpty() || random.nextBoolean())) {
                setInfected(true);
                sendChatToAllAround("villager.bitten");
                MCA.LOGGER.info("{} has been infected", getName());
            }
        }

        Entity attacker = source.getEntity();

        // Golems pardon ordinary villagers, but must continue fighting raider professions.
        if (attacker instanceof IronGolem golem && !ProfessionsMCA.isRaider(getProfession())) {
            golem.stopBeingAngry();
            damageAmount *= 0.0f;
        }

        if (damageAmount > 0 && (damageAmount >= getHealth() + getAbsorptionAmount()
                || getHealth() <= getMaxHealth() * 0.25F)) prepareTotem();
        boolean hurt = super.hurtServer(level, source, damageAmount);
        if (hurt && attacker instanceof VillagerEntityMCA enemy
                && com.ancientkira.mca.server.VillageWarfare.isEnemy(this, enemy)) {
            if (isSleeping()) stopSleeping();
            getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, enemy);
            getBrain().setMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY, enemy);
            setTarget(enemy);
        }

        // Notify the surrounding guards when a villager is attacked. Yoinks!
        if (hurt && !level().isClientSide() && attacker instanceof LivingEntity livingEntity && !isHostile() && !isFriend(attacker.getType())) {
            int victimBountyBeforeHit = getBrain().getMemoryInternal(MemoryModuleTypeMCA.HIT_BY_PLAYER)
                    .filter(previous -> previous == livingEntity).isPresent() ? getSmallBounty() : 0;

            // remember the specific attacker
            getBrain().setMemory(MemoryModuleTypeMCA.HIT_BY_PLAYER, Optional.of(livingEntity));
            getBrain().setMemory(MemoryModuleTypeMCA.SMALL_BOUNTY, victimBountyBeforeHit + 1);

            Vec3 pos = position();
            level().getEntitiesOfClass(VillagerEntityMCA.class, new AABB(pos, pos).inflate(32)).forEach(v -> {
                if (v.isAlive() && !v.isHostile() && this.distanceToSqr(v) <= 1024) {
                    if (attacker instanceof Player player) {
                        if (v.isGuard()) {
                            int previousBounty = v == this ? victimBountyBeforeHit
                                    : v.getBrain().getMemoryInternal(MemoryModuleTypeMCA.HIT_BY_PLAYER)
                                            .filter(previous -> previous == livingEntity).isPresent() ? v.getSmallBounty() : 0;
                            int bounty = isAlive() ? previousBounty + 1 : Math.max(3, previousBounty + 1);
                            v.getBrain().setMemory(MemoryModuleTypeMCA.SMALL_BOUNTY, bounty);
                            v.getBrain().setMemory(MemoryModuleTypeMCA.HIT_BY_PLAYER, livingEntity);
                            int maxWarning = Math.max(0, v.getMaxWarnings(player));
                            if (bounty > maxWarning) {
                                // ok, that was enough
                                v.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, livingEntity);
                            } else {
                                // Approach to warn the attacker without abandoning an active fight.
                                if (!v.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
                                    v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(livingEntity, true));
                                    v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(livingEntity, false), 0.75F, 3));
                                }
                                if (bounty == 1 || bounty == maxWarning) {
                                    v.sendChatMessage(player, bounty == 1 ? "villager.warning.first" : "villager.warning.last");
                                }
                            }
                        }
                    } else if (v.isGuard()) {
                        // non players get attacked straight away
                        v.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, livingEntity);
                    }
                }
            });
        }

        return hurt;
    }

    private boolean requestCooldown() {
        if (level().getGameTime() - lastCooldown > 100) {
            lastCooldown = level().getGameTime();
            return true;
        } else {
            return false;
        }
    }

    private void settleTraderLlamas() {
        if (!(level() instanceof ServerLevel world)) return;
        for (var llama : world.getEntitiesOfClass(net.minecraft.world.entity.animal.equine.TraderLlama.class,
                getBoundingBox().inflate(64), companion -> companion.getLeashHolder() == this)) {
            var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registryAccess());
            llama.saveWithoutId(output);
            var saved = output.buildResult();
            saved.remove("UUID");
            llama.convertTo(EntityType.LLAMA, ConversionParams.single(llama, true, true), converted -> {
                converted.load(TagValueInput.create(ProblemReporter.DISCARDING, registryAccess(), saved));
                converted.setPersistenceRequired();
            });
        }
    }

    @Override
    public Vec3 getRopeHoldPosition(float partialTick) {
        // Attach companions' leads to the person's hand at its interpolated world position.
        return getPosition(partialTick).add(0.0, getBbHeight() * 0.55, 0.0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(0, new TraderDrinkGoal(this,
                net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION,
                        net.minecraft.world.item.alchemy.Potions.INVISIBILITY),
                net.minecraft.sounds.SoundEvents.WANDERING_TRADER_DISAPPEARED,
                person -> person.level().isDarkOutside() && !person.isInvisible()));
        goalSelector.addGoal(0, new TraderDrinkGoal(this, new ItemStack(Items.MILK_BUCKET),
                net.minecraft.sounds.SoundEvents.WANDERING_TRADER_REAPPEARED,
                person -> person.level().isBrightOutside() && person.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)));
    }

    private static final class TraderDrinkGoal extends net.minecraft.world.entity.ai.goal.UseItemGoal<VillagerEntityMCA> {
        private final VillagerEntityMCA person;
        private ItemStack previousHand = ItemStack.EMPTY;
        TraderDrinkGoal(VillagerEntityMCA person, ItemStack drink, net.minecraft.sounds.SoundEvent sound,
                        java.util.function.Predicate<VillagerEntityMCA> condition) {
            super(person, drink, sound, entity -> entity.getProfession() == ProfessionsMCA.WANDERING_TRADER
                    && !entity.isKidnapped() && !entity.isTrading() && !entity.isUsingItem()
                    && entity.getInteractions().getInteractingPlayer().isEmpty() && condition.test(entity));
            this.person = person;
        }
        @Override public void start() {
            previousHand = person.getMainHandItem().copy();
            super.start();
        }
        @Override public void stop() {
            if (person.isUsingItem()) person.stopUsingItem();
            super.stop();
            person.setItemSlot(EquipmentSlot.MAINHAND, previousHand);
            previousHand = ItemStack.EMPTY;
        }
        @Override public boolean canContinueToUse() {
            return person.getProfession() == ProfessionsMCA.WANDERING_TRADER && !person.isKidnapped()
                    && super.canContinueToUse();
        }
    }

    private void tickTraderLlamas() {
        if (getProfession() == ProfessionsMCA.WANDERING_TRADER && tickCount % 20 == 0
                && !isClothingLocked() && !getClothes().contains("/cultist/")
                && level() instanceof ServerLevel clothingWorld && clothingWorld.getEntity(getUUID()) == this) {
            forceRandomizeClothes();
        }
        if (getProfession() != ProfessionsMCA.WANDERING_TRADER
                || tickCount > 1 && tickCount % 20 != 0 || !(level() instanceof ServerLevel world)
                || world.getEntity(getUUID()) != this) return;
        for (var companion : world.getEntitiesOfClass(net.minecraft.world.entity.animal.equine.TraderLlama.class,
                getBoundingBox().inflate(32), llama -> llama.getLeashHolder() == this)) {
            // Refresh the attachment after both entities have entered client tracking.
            if (tickCount % 40 == 0) companion.setLeashedTo(this, true);
            if (companion.distanceToSqr(this) > 4 * 4 && !companion.isVehicle()) {
                companion.getNavigation().moveTo(this, 1.0);
            }
        }
        if (traderLlamaCount >= 2) return;
        for (int attempt = 0; attempt < 16 && traderLlamaCount < 2; attempt++) {
            BlockPos pos = blockPosition().offset(random.nextInt(9) - 4, random.nextInt(3) - 1, random.nextInt(9) - 4);
            if (!world.hasChunkAt(pos) || !world.getBlockState(pos.below()).isSolid()) continue;
            var companion = EntityType.TRADER_LLAMA.create(world, EntitySpawnReason.EVENT);
            if (!(companion instanceof net.minecraft.world.entity.animal.equine.TraderLlama llama)) return;
            llama.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            if (!world.noCollision(llama)) continue;
            llama.finalizeSpawn(world, world.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
            llama.setDespawnDelay(getDespawnDelay() > 0 ? getDespawnDelay() : 48000);
            if (world.addFreshEntity(llama)) {
                llama.setLeashedTo(this, true);
                traderLlamaCount++;
            }
        }
    }

    public boolean isKidnapped() { return level().isClientSide() ? entityData.get(CAPTIVE) : kidnappedUntil > 0; }

    public boolean beginKidnapping(VillagerEntityMCA captor) {
        if (level().isClientSide() || isKidnapped() || !isAlive() || ProfessionsMCA.isRaider(getProfession())
                || !ProfessionsMCA.isRaider(captor.getProfession()) || captor.isPeacefulRaider()
                || com.ancientkira.mca.server.RaiderSocial.isFamily(captor, this)) return false;
        com.ancientkira.mca.server.ClericAid.onCaptured(this);
        captivityState = new CompoundTag();
        captivityState.putString("OriginalProfession", getProfessionId().toString());
        getResidency().getRegistryVillage().ifPresent(village -> captivityState.putInt("RegistryVillage", village.getId()));
        captivityState.putBoolean("NoAI", isNoAi());
        captivityState.putBoolean("Invisible", isInvisible());
        captivityState.putBoolean("Silent", isSilent());
        captivityState.putBoolean("NoGravity", isNoGravity());
        captivityState.putBoolean("NoPhysics", noPhysics);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            captivityState.store("Equipment_" + slot.getName(), ItemStack.OPTIONAL_CODEC,
                    registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), getItemBySlot(slot).copy());
            setItemSlot(slot, ItemStack.EMPTY);
        }
        kidnappedUntil = level().getGameTime() + 2400;
        entityData.set(CAPTIVE, true);
        sendChatToAllAround("villager.scream");
        setPersistenceRequired();
        setNoAi(true);
        setInvisible(true);
        setSilent(true);
        setNoGravity(true);
        noPhysics = true;
        setDeltaMovement(Vec3.ZERO);
        getNavigation().stop();
        getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        getInteractions().stopInteracting();
        announceKidnappingEvent(Component.translatable("mca.raider.kidnapped", getName(), captor.getName()));
        placeCaptiveAtOutpost();
        VillagerTrackerManager.get((ServerLevel) level()).recordCaptive(this);
        return true;
    }

    private void placeCaptiveAtOutpost() {
        captivityState.putBoolean("PlacementChecked", true);
        findKidnapOutpostPosition().ifPresent(pos -> {
            captivityState.putLong("HoldingPosition", pos.asLong());
            stopRiding();
            setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            setInvisible(false);
            noPhysics = false;
        });
    }

    private Optional<BlockPos> findKidnapOutpostPosition() {
        if (!(level() instanceof ServerLevel world)) return Optional.empty();
        var manager = VillageManager.get(world);
        var outposts = manager.findVillages(village -> true)
                .flatMap(village -> village.getBuildings().values().stream()
                        .filter(building -> building.getType().equals("pillager_outpost")
                                && village.canRegisterOutpost(building)))
                .sorted(java.util.Comparator.comparingDouble(building -> building.getSourceBlock().distSqr(blockPosition())))
                .toList();
        for (Building outpost : outposts) {
            var rooms = manager.findVillages(village -> village.getBuildings().containsValue(outpost))
                    .flatMap(village -> village.getBuildings().values().stream())
                    .filter(room -> room.getStructureId() == outpost.getStructureId())
                    .sorted(java.util.Comparator.comparingInt(Building::getId)).toList();
            var floors = new java.util.ArrayList<>(rooms.stream().map(Building::getFloorNumber).distinct().toList());
            java.util.Collections.shuffle(floors, new java.util.Random(random.nextLong()));
            for (int floor : floors) {
                for (Building room : rooms) {
                    if (room.getFloorNumber() != floor) continue;
                    BlockPos min = room.getPos0();
                    BlockPos max = room.getPos1();
                    // Use a common two-block grid so adjacent rooms retain the same rows.
                    int firstX = min.getX() + Math.floorMod(-min.getX(), 2);
                    int firstZ = min.getZ() + Math.floorMod(-min.getZ(), 2);
                    for (int z = firstZ; z <= max.getZ(); z += 2) {
                        for (int x = firstX; x <= max.getX(); x += 2) {
                            for (int dy = 0; dy <= 2; dy++) {
                                int y = room.getSourceBlock().getY() + (dy == 0 ? 0 : dy == 1 ? -1 : 1);
                                BlockPos pos = new BlockPos(x, y, z);
                                if (!room.containsPos(pos)) continue;
                                world.getChunk(x >> 4, z >> 4);
                                if (!world.getBlockState(pos.below()).isFaceSturdy(world, pos.below(), net.minecraft.core.Direction.UP)) continue;
                                var box = getBoundingBox().move(x + 0.5 - getX(), y - getY(), z + 0.5 - getZ());
                                if (world.noCollision(this, box)
                                        && world.getEntities(this, box.inflate(0.25), entity -> entity.isAlive() && !entity.isSpectator()).isEmpty()) {
                                    return Optional.of(pos);
                                }
                            }
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private void announceKidnappingEvent(Component message) {
        if (level() instanceof ServerLevel world && world.getEntity(getUUID()) == this) {
            VillageManager.get(world).getOrEmpty(captivityState.getIntOr("RegistryVillage", -1))
                    .or(() -> getResidency().getRegistryVillage()).flatMap(Village::getCivilRegistry)
                    .ifPresent(registry -> registry.addText(message));
            world.getServer().getPlayerList().broadcastSystemMessage(message, false);
        }
    }

    private void restoreFromCaptivity() {
        kidnappedUntil = 0;
        entityData.set(CAPTIVE, false);
        setNoAi(captivityState.getBooleanOr("NoAI", false));
        setInvisible(captivityState.getBooleanOr("Invisible", false));
        setSilent(captivityState.getBooleanOr("Silent", false));
        setNoGravity(captivityState.getBooleanOr("NoGravity", false));
        noPhysics = captivityState.getBooleanOr("NoPhysics", false);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setItemSlot(slot, captivityState.read("Equipment_" + slot.getName(), ItemStack.OPTIONAL_CODEC,
                    registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE)).orElse(ItemStack.EMPTY));
        }
        clearCaptivityOrders();
    }

    private void clearCaptivityOrders() {
        getVillagerBrain().setMoveState(com.ancientkira.mca.entity.ai.MoveState.MOVE, null);
        getVillagerBrain().abandonJob();
        getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        getNavigation().stop();
        stopRiding();
    }

    public boolean rescueFromKidnapping() {
        if (!isKidnapped() || level().isClientSide()) return false;
        restoreFromCaptivity();
        com.ancientkira.mca.server.ClericAid.onRescued(this);
        VillagerTrackerManager.get((ServerLevel) level()).clearCaptive(getUUID());
        announceKidnappingEvent(Component.translatable("mca.raider.rescued", getName()));
        captivityState = new CompoundTag();
        getRelationships().getFamilyEntry().setAppearanceData(this);
        return true;
    }

    private void tickKidnapping() {
        if (!isKidnapped() || level().isClientSide()) return;
        if (VillagerTrackerManager.get((ServerLevel) level()).fulfillRescue(this)) return;
        if (level().getGameTime() < kidnappedUntil) {
            if (tickCount % 100 == 0 && level() instanceof ServerLevel world) {
                world.playSound(null, getX(), getY(), getZ(), getDeathSound(), getSoundSource(), getSoundVolume(), getVoicePitch());
                if (tickCount % 200 == 0) world.players().stream()
                        .filter(player -> !player.isSpectator() && distanceToSqr(player) <= 16 * 16)
                        .forEach(player -> sendChatMessage(player, "villager.badly_hurt"));
            }
            if (!captivityState.getBooleanOr("PlacementChecked", false)) {
                placeCaptiveAtOutpost();
                VillagerTrackerManager.get((ServerLevel) level()).recordCaptive(this);
            }
            if (captivityState.contains("HoldingPosition")) {
                BlockPos holding = BlockPos.of(captivityState.getLongOr("HoldingPosition", 0));
                setPos(holding.getX() + 0.5, holding.getY(), holding.getZ() + 0.5);
            }
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        restoreFromCaptivity();
        String originalProfession = captivityState.getStringOr("OriginalProfession", getProfessionId().toString());
        VillagerProfession raiderProfession = switch (originalProfession) {
            case "minecraft:cleric" -> ProfessionsMCA.WITCH;
            case "mca:guard" -> ProfessionsMCA.VINDICATOR;
            case "mca:archer" -> ProfessionsMCA.PILLAGER;
            default -> switch (random.nextInt(4)) {
                case 0 -> ProfessionsMCA.PILLAGER;
                case 1 -> ProfessionsMCA.VINDICATOR;
                case 2 -> ProfessionsMCA.EVOKER;
                default -> ProfessionsMCA.WITCH;
            };
        };
        VillagerTrackerManager.get((ServerLevel) level()).clearCaptive(getUUID());
        setProfession(raiderProfession);
        clearCaptivityOrders();
        getVillagerBrain().modifyMoodValue(-1000);
        getRelationships().getFamilyEntry().setAppearanceData(this);
        announceKidnappingEvent(Component.translatable("mca.raider.returned", getName(),
                Component.translatable("entity.minecraft.villager." + getProfessionId().toString().replace(':', '.'))));
        captivityState = new CompoundTag();
    }

    public boolean isGuard() {
        return getProfession() == ProfessionsMCA.GUARD || getProfession() == ProfessionsMCA.ARCHER;
    }

    public int getSmallBounty() {
        return Objects.requireNonNull(getBrain().getMemoryInternal(MemoryModuleTypeMCA.SMALL_BOUNTY)).orElse(0);
    }

    public boolean isHitBy(ServerPlayer player) {
        return Objects.requireNonNull(getBrain().getMemoryInternal(MemoryModuleTypeMCA.HIT_BY_PLAYER)).filter(v -> v == player).isPresent();
    }

    private int getMaxWarnings(Player attacker) {
        return 2;
    }

    @Override
    public void aiStep() {
        int oldAge = getAge();
        updateSwingTime();

        super.aiStep();
        tickKidnapping();
        if (isKidnapped()) return;

        if (getTraits().hasTrait(Traits.NO_AGING)) {
            setAge(oldAge);
        }

        burned--;
        if (isOnFire()) {
            burned = Config.getInstance().burnedClothingTickLength;
        }
        if (burned > 0) {
            spawnBurntParticles();
        }

        if (!level().isClientSide()) {
            if (tickCount % 200 == 0
                    && getHealth() < getMaxHealth()
                    && !getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
                // if the villager has food they should try to eat.
                ItemStack food = getMainHandItem();
                FoodProperties foodProperties = food.get(DataComponents.FOOD);
                if (canEat(food, foodProperties)) {
                    eat(food, foodProperties);
                } else {
                    //noinspection ConstantConditions
                    if (!findAndEquipToMain(VillagerEntityMCA::canEat)) {
                        heal(1); // natural regeneration
                    }
                }
            }

            if (tickCount % 5 == 0) {
                com.ancientkira.mca.entity.ai.brain.tasks.CloseRememberedDoorsTask.tickDoors((ServerLevel) level(), this);
            }
            tickTraderLlamas();
            tickDespawnDelay();

            residency.tick();

            relations.tick(tickCount);
            if (tickCount % 1200 == 0) {
                com.ancientkira.mca.server.VillagerProposals.tick(this);
                com.ancientkira.mca.server.FamilyRequests.tick(this);
            }
            if (tickCount % 100 == 0) {
                com.ancientkira.mca.server.RaiderSocial.restoreKnownFriendships(this);
            }
            com.ancientkira.mca.server.PillagerRaids.tick(this);
            com.ancientkira.mca.server.VillageWarfare.tickCombat(this);

            com.ancientkira.mca.server.ClericAid.tick(this);
            com.ancientkira.mca.server.CaptiveRescue.tick(this);
            tickCombatShield();
            inventory.update(this);

            if (tickCount % Config.getInstance().pardonPlayerTicks == 0) {
                pardonPlayers();
            }

            // Brain and pregnancy depend on the above states, so we tick them last
            // Every 1 second
            mcaBrain.think();

            // pop a item from the desaturation queue
            if (tickCount % Config.getInstance().giftDesaturationReset == 0) {
                getRelationships().getGiftSaturation().pop();
            }

            // track the position from time to time
            if (interactedWith && tickCount % Config.getInstance().trackVillagerPositionEveryNTicks == 0) {
                VillagerTrackerManager.update(this);
            }
        }
    }

    protected boolean findAndEquipToMain(Predicate<ItemStack> predicate) {
        int slot = InventoryUtils.getFirstSlotContainingItem(getInventory(), predicate);

        if (slot > -1) {
            ItemStack replacement = getInventory().getItem(slot).split(1);

            if (!replacement.isEmpty()) {
                setItemInHand(getDominantHand(), replacement);
                return true;
            }
        }

        return false;
    }

    @Override
    public void tick() {
        super.tick();
        mcaBrain.tickPanicAnimation();

        // update visual age
        int age = getTrackedValue(GROWTH_AMOUNT);
        if (age / RECALCULATE_DIMENSIONS_EVERY_N_TICKS != prevGrowthAmount / RECALCULATE_DIMENSIONS_EVERY_N_TICKS) {
            prevGrowthAmount = age;
            refreshDimensions();
        }

        if (level().isClientSide()) {
            // procreate anim
            if (relations.isProcreating()) {
                yHeadRot += 50;
            }

            // mood particles
            Mood mood = mcaBrain.getMood();
                if (mood.getParticle() != null && this.tickCount % mood.getParticleInterval() == 0 && getRandom().nextBoolean()) {
                    addParticlesAroundSelf(mood.getParticle());
                }
            } else {
                // infection
                float infection = getInfectionProgress();
                if (infection > 0 && this.tickCount % 20 == 0) {
                    if (infection > FEVER_THRESHOLD && getRandom().nextInt(25) == 0) {
                        sendChatToAllAround("villager.sickness");
                    }

                infection += 1.0f / Config.getInstance().infectionTime;
                setInfectionProgress(infection);

                if (infection > 1.0f) {
                    convertTo(EntityType.ZOMBIE_VILLAGER, ConversionParams.single(this, false, false), mob -> {
                    });
                }
            }

            // panic screams
            if (this.tickCount % 90 == 0 && mcaBrain.isPanicking()) {
                sendChatToAllAround("villager.scream");
            }

            // sirben noises
            if (this.tickCount % 60 == 0 && random.nextInt(50) == 0 && traits.hasTrait(Traits.SIRBEN)) {
                sendChatToAllAround("sirben");
            }

            updateLevelHealthBonus();

            //twice a day, randomize the mood a bit
            if (this.tickCount % 12000 == 0) {
                int base = Math.round(mcaBrain.getMoodValue() / 12.0f);
                int value = random.nextInt(7) - 3;
                mcaBrain.modifyMoodValue(value - base);
            }
        }
    }

    private void updateLevelHealthBonus() {
        int level = this.getVillagerData().level() - 1;
        double bonus = Config.getInstance().villagerHealthBonusPerLevel * level;

        if (level == lastAppliedHealthLevel && bonus == lastAppliedHealthBonus) {
            return;
        }

        lastAppliedHealthLevel = level;
        lastAppliedHealthBonus = bonus;

        AttributeInstance instance = this.getAttributes().getInstance(Attributes.MAX_HEALTH);
        if (instance == null) {
            return;
        }

        instance.removeModifier(EXTRA_HEALTH_EFFECT_ID);

        if (bonus != 0.0D) {
            instance.addTransientModifier(new AttributeModifier(
                    EXTRA_HEALTH_EFFECT_ID,
                    bonus,
                    AttributeModifier.Operation.ADD_VALUE
            ));
        }
    }

    @Override
    public void refreshDimensions() {
        AgeState current = getAgeState();
        AgeState next = current.getNext();

        // either interpolate or set if final age is reached
        if (next != current) {
            dimensions.interpolate(current, next, AgeState.getDelta(getTrackedValue(GROWTH_AMOUNT)));
        } else {
            dimensions.set(current);
        }

        // todo calculateDimensions call move, move sets some flags, but since it's a "fake" move no collision happen
        // without collision the pathfinder skips the frame, causing children to not move
        // there are more flags affected, none of them seem to affect the game tho
        boolean oldOnGround = this.onGround();
        super.refreshDimensions();
        this.setOnGround(oldOnGround);
    }

    private void eat(ItemStack stack, FoodProperties foodProperties) {
        heal(foodProperties.nutrition());
        setItemInHand(getDominantHand(), stack.finishUsingItem(level(), this));
    }

    @Override
    public void rideTick() {
        super.rideTick();

        Entity vehicle = getVehicle();

        if (vehicle instanceof PathfinderMob pathAwareEntity) {
            yBodyRot = pathAwareEntity.yBodyRot;
        }

        if (vehicle instanceof Player player) {
            List<Entity> passengers = vehicle.getPassengers();

            float yaw = -player.yBodyRot * 0.017453292F;

            boolean left = passengers.get(0) == this;
            boolean head = passengers.size() > 2 && passengers.get(2) == this;

            Vec3 offset = head ? new Vec3(0, 0.55f, 0) : new Vec3(left ? 0.4F : -0.4F, 0.05f, 0).yRot(yaw);

            // todo currently only client side
            if (isClientSide()) {
                VillagerLike<?> playerData = MCAClient.getGeneticsRendererData(vehicle.getUUID()).orElse(null);
                if (playerData != null) {
                    float height = playerData.getRawVerticalScaleFactor();
                    offset = offset.multiply(1.0f, height, 1.0f);
                    offset = offset.add(0, (height - 1) * 1.5 - 0.7, 0);
                }
            }

            Vec3 pos = this.position();
            this.setPosRaw(pos.x() + offset.x(), pos.y() + offset.y(), pos.z() + offset.z());

            if (vehicle.isShiftKeyDown()) {
                stopRiding();
            }
        }
    }

    @Override
    public boolean startRiding(Entity entityToRide, boolean force, boolean sendEventAndTriggers) {
        boolean mounted = super.startRiding(entityToRide, force, sendEventAndTriggers);
        if (mounted && entityToRide instanceof Player) {
            refreshDimensions();
        }
        return mounted;
    }

    @Override
    public void stopRiding() {
        boolean wasRidingPlayer = getVehicle() instanceof Player;
        super.stopRiding();
        if (wasRidingPlayer) {
            refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        Entity vehicle = getVehicle();
        if (vehicle instanceof Player) {
            return SLEEPING_DIMENSIONS;
        }

        if (pose == Pose.SLEEPING) {
            return SLEEPING_DIMENSIONS;
        }

        float height = getVerticalScaleFactor() * 2.0F;
        float width = getHorizontalScaleFactor() * 0.6F;

        return EntityDimensions.scalable(width, height).withAttachments(EntityAttachments.builder()
                .attach(EntityAttachment.VEHICLE, 0.0F, getRawVerticalScaleFactor() * VEHICLE_ATTACHMENT_Y, 0.0F));
    }

    @Override
    protected boolean considersEntityAsAlly(Entity other) {
        if (PillagerRaids.isJohnny(this) && other instanceof LivingEntity living) {
            return PillagerRaids.isRaiderTarget(living);
        }
        if (ProfessionsMCA.isRaider(getProfession()) && other instanceof LivingEntity living
                && com.ancientkira.mca.server.RaiderSocial.isFamily(this, living)) return true;
        if (ProfessionsMCA.isRaider(getProfession()) && getTeam() == null && other.getTeam() == null) {
            if (other instanceof VillagerEntityMCA person && ProfessionsMCA.isRaider(person.getProfession())) return true;
            if (other instanceof net.minecraft.world.entity.raid.Raider) return true;
            if (other instanceof net.minecraft.world.entity.monster.Vex vex && vex.getOwner() != null) {
                return considersEntityAsAlly(vex.getOwner());
            }
        }
        return super.considersEntityAsAlly(other);
    }

    @Override
    protected float getDamageAfterMagicAbsorb(DamageSource source, float amount) {
        float damage = super.getDamageAfterMagicAbsorb(source, amount);
        if (getProfession() == ProfessionsMCA.WITCH) {
            if (source.getEntity() == this) return 0;
            if (source.is(net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO)) damage *= 0.15F;
        }
        return damage;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel world, DamageSource cause, boolean recentlyHit) {
        super.dropCustomDeathLoot(world, cause, recentlyHit);
        if (ProfessionsMCA.isRaider(getProfession())) {
            com.ancientkira.mca.server.PillagerRaids.dropCaptainLoot(this, world);
            if (getProfession() != ProfessionsMCA.PILLAGER) {
                String role = getProfessionId().getPath();
                dropFromLootTable(world, cause, recentlyHit,
                        net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                                Identifier.withDefaultNamespace("entities/" + role)));
            }
        }
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel world) {
        return getProfession() == ProfessionsMCA.EVOKER ? 10 : ProfessionsMCA.isRaider(getProfession()) ? 5 : super.getBaseExperienceReward(world);
    }

    @Override
    public void die(DamageSource cause) {
        com.ancientkira.mca.server.PillagerRaids.onDeath(this, cause);
        // deselect equipment as this messes with MobEntities equipment dropping
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setItemSlot(slot, ItemStack.EMPTY);
        }

        //death message
        if (!level().isClientSide()) {
            getResidency().getRegistryVillage().flatMap(Village::getCivilRegistry).ifPresent(r -> r.addText(getCombatTracker().getDeathMessage()));
        }

        super.die(cause);

        if (level().isClientSide()) {
            return;
        }

        //drop stuff
        InventoryUtils.dropAllItems(this, inventory);

        //alert family and nearby villagers
        relations.onDeath(cause);

        Optional<Village> village = residency.getHomeVillage();
        if (village.isPresent() && cause.getEntity() != null && level() instanceof ServerLevel serverLevel) {
            serverLevel.players().forEach(player -> {
                Rank relationToVillage = Tasks.getRank(village.get(), player);
                Identifier causeId = EntityType.getKey(cause.getEntity().getType());
                CriterionMCA.FATE.trigger(player, causeId, relationToVillage);
            });
        }

        //move out
        residency.leaveHome();

        if (interactedWith) {
            VillagerTrackerManager.update(this);
        }
    }


    @Override
    public boolean teleportTo(ServerLevel destination, double x, double y, double z,
                              java.util.Set<net.minecraft.world.entity.Relative> relatives, float yaw, float pitch, boolean force) {
        // Cross-dimension teleportation copies this entity's data to a new entity.
        if (destination != level() && canTeleport(level(), destination)) rescueFromKidnapping();
        boolean teleported = super.teleportTo(destination, x, y, z, relatives, yaw, pitch, force);
        if (teleported) rescueFromKidnapping();
        return teleported;
    }

    @Override
    public void teleportTo(double destX, double destY, double destZ) {
        rescueFromKidnapping();
        if (isPassenger()) {
            Entity rootVehicle = getRootVehicle();
            if (rootVehicle instanceof Mob) {
                rootVehicle.teleportTo(destX, destY, destZ);
                return; // villagers can travel by teleporting, so make sure they take their mount with
            }
        }

        super.teleportTo(destX, destY, destZ);
    }

    @Override
    public SoundEvent getDeathSound() {
        if (Config.getInstance().useMCAVoices) {
            return getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_SCREAM : SoundsMCA.VILLAGER_FEMALE_SCREAM;
        } else if (Config.getInstance().useVanillaVoices) {
            return super.getDeathSound();
        } else {
            return SoundsMCA.SILENT;
        }
    }

    public SoundEvent getSurprisedSound() {
        if (Config.getInstance().useMCAVoices) {
            return getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_SURPRISE : SoundsMCA.VILLAGER_FEMALE_SURPRISE;
        } else {
            return SoundsMCA.SILENT;
        }
    }

    @Nullable
    @Override
    protected final SoundEvent getAmbientSound() {
        if (Config.getInstance().useMCAVoices) {
            //baby sounds
            if (getAgeState() == AgeState.BABY) {
                return SoundsMCA.VILLAGER_BABY_LAUGH;
            }

            //snoring
            if (isSleeping()) {
                return getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_SNORE : SoundsMCA.VILLAGER_FEMALE_SNORE;
            }

            //scream in terror and pain
            if (getVillagerBrain().isPanicking()) {
                return getDeathSound();
            }

            //coughing
            if (isInfected() && random.nextBoolean()) {
                return getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_COUGH : SoundsMCA.VILLAGER_FEMALE_COUGH;
            }

            //sirben
            if (random.nextBoolean() && getTraits().hasTrait(Traits.SIRBEN)) {
                return getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_SIRBEN : SoundsMCA.VILLAGER_FEMALE_SIRBEN;
            }

            //generic mood sounds
            Mood mood = getVillagerBrain().getMood();
            if (mood.getSoundInterval() > 0 && tickCount % mood.getSoundInterval() == 0) {
                return getGenetics().getGender() == Gender.MALE ? mood.getSoundMale() : mood.getSoundFemale();
            }

            return SoundsMCA.SILENT;
        } else if (Config.getInstance().useVanillaVoices) {
            return super.getAmbientSound();
        } else {
            return SoundsMCA.SILENT;
        }
    }

    @Override
    protected final SoundEvent getHurtSound(DamageSource cause) {
        if (Config.getInstance().useMCAVoices) {
            return getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_HURT : SoundsMCA.VILLAGER_FEMALE_HURT;
        } else if (Config.getInstance().useVanillaVoices) {
            return super.getHurtSound(cause);
        } else {
            return SoundsMCA.SILENT;
        }
    }

    public final void playWelcomeSound() {
        if (Config.getInstance().useMCAVoices && !getVillagerBrain().isPanicking() && getAgeState() != AgeState.BABY) {
            playSound(getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_GREET : SoundsMCA.VILLAGER_FEMALE_GREET, getSoundVolume(), getVoicePitch());
        }
    }

    public final void playSurprisedSound() {
        if (Config.getInstance().useMCAVoices) {
            playSound(getSurprisedSound(), getSoundVolume(), getVoicePitch());
        }
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        if (Config.getInstance().useMCAVoices) {
            return getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_YES : SoundsMCA.VILLAGER_FEMALE_YES;
        } else if (Config.getInstance().useVanillaVoices) {
            return super.getNotifyTradeSound();
        } else {
            return SoundsMCA.SILENT;
        }
    }

    private void playNoTradeResponse() {
        setUnhappyCounter(40);
        if (!level().isClientSide()) {
            playSound(getNoSound(), getSoundVolume(), getVoicePitch());
        }
    }

    public SoundEvent getNoSound() {
        if (Config.getInstance().useMCAVoices) {
            return getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_NO : SoundsMCA.VILLAGER_FEMALE_NO;
        } else if (Config.getInstance().useVanillaVoices) {
            return SoundEvents.VILLAGER_NO;
        } else {
            return SoundsMCA.SILENT;
        }
    }

    @Override
    protected SoundEvent getTradeUpdatedSound(boolean sold) {
        if (Config.getInstance().useMCAVoices) {
            return sold ? getNotifyTradeSound() : getNoSound();
        } else if (Config.getInstance().useVanillaVoices) {
            return super.getTradeUpdatedSound(sold);
        } else {
            return SoundsMCA.SILENT;
        }
    }

    @Override
    public void playCelebrateSound() {
        if (Config.getInstance().useMCAVoices) {
            playSound(getGenetics().getGender() == Gender.MALE ? SoundsMCA.VILLAGER_MALE_CELEBRATE : SoundsMCA.VILLAGER_FEMALE_CELEBRATE, getSoundVolume(), getVoicePitch());
        } else if (Config.getInstance().useVanillaVoices) {
            super.playCelebrateSound();
        } else {
            playSound(SoundsMCA.SILENT, getSoundVolume(), getVoicePitch());
        }
    }

    @Override
    public float getVoicePitch() {
        float r = (random.nextFloat() - 0.5f) * 0.05f;
        float g = (genetics.getGene(Genetics.VOICE) - 0.5f) * 0.3f;
        float a = Mth.lerp(AgeState.getDelta(tickCount), getAgeState().getPitch(), getAgeState().getNext().getPitch());
        return a + r + g;
    }

    @Override
    public final Component getDisplayName() {
        Component name = super.getDisplayName();

        if (getVillagerBrain() != null) {
            MoveState state = getVillagerBrain().getMoveState();
            if (state != MoveState.MOVE) {
                name = name.plainCopy().append(" (").append(state.getName()).append(")");
            }
            Chore chore = getVillagerBrain().getCurrentJob();
            if (chore != Chore.NONE) {
                name = name.plainCopy().append(" (").append(chore.getName()).append(")");
            }
        }

        if (isInfected()) {
            return name.plainCopy().withStyle(ChatFormatting.GREEN);
        } else if (getProfession() == ProfessionsMCA.OUTLAW) {
            return name.plainCopy().withStyle(ChatFormatting.RED);
        }
        return name;
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
    public float getInfectionProgress() {
        return getTrackedValue(INFECTION_PROGRESS);
    }

    @Override
    public void setInfectionProgress(float progress) {
        setTrackedValue(INFECTION_PROGRESS, progress);
    }

    @Override
    public void playSpeechEffect() {
        if (isSpeechImpaired()) {
            playSound(SoundEvents.ZOMBIE_AMBIENT, getSoundVolume(), getVoicePitch());
        }
    }

    // we make it public here
    @Override
    public void addParticlesAroundSelf(ParticleOptions parameters) {
        super.addParticlesAroundSelf(parameters);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int i, Inventory playerInventory, Player playerEntity) {
        return ChestMenu.threeRows(i, playerInventory, inventory);
    }

    @Override
    public VillagerDimensions getVillagerDimensions() {
        return dimensions;
    }

    @Override
    public boolean setAgeState(AgeState state) {
        if (VillagerLike.super.setAgeState(state)) {
            if (!level().isClientSide()) {
                // trigger grow up advancements
                relations.getParents()
                        .filter(ServerPlayer.class::isInstance)
                        .map(ServerPlayer.class::cast).forEach(
                                e -> CriterionMCA.CHILD_AGE_STATE_CHANGE.trigger(e, state.name())
                        );

                if (state == AgeState.ADULT) {
                    // Notify player parents of the age up and set correct dialogue type.
                    relations.getParents()
                            .filter(Player.class::isInstance)
                            .map(Player.class::cast).forEach(
                                    p -> sendEventMessage(Component.translatable("notify.child.grownup", getName()), p)
                            );
                }

                refreshBrain((ServerLevel) level());

                getVillagerBrain().randomize(state);

                // set age specific clothes
                randomizeClothes();
            }
            return true;
        }

        return false;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> par) {
        if (getTypeDataManager().isParam(AGE_STATE, par)
                || getTypeDataManager().isParam(Genetics.SIZE.getParam(), par)
                || getTypeDataManager().isParam(Genetics.WIDTH.getParam(), par)) {
            refreshDimensions();
        }

        super.onSyncedDataUpdated(par);
    }

    @Override
    public SimpleContainer getInventory() {
        return inventory;
    }

    public void setInventory(UpdatableInventory inventory) {
        CompoundTag nbt = new CompoundTag();
        InventoryUtils.saveToNBT(this.registryAccess(), inventory, nbt);
        InventoryUtils.readFromNBT(this.registryAccess(), this.inventory, nbt);
    }

    public void moveTowards(BlockPos pos, float speed, int closeEnoughDist) {
        this.brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, speed, closeEnoughDist));
        this.lookAt(pos);
    }

    public void moveTowards(BlockPos pos, float speed) {
        moveTowards(pos, speed, 1);
    }

    public void moveTowards(BlockPos pos) {
        moveTowards(pos, 0.5F);
    }

    public void lookAt(BlockPos pos) {
        this.brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(pos));
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case Status.MCA_VILLAGER_NEG_INTERACTION ->
                    level().addAlwaysVisibleParticle(ParticleTypesMCA.NEG_INTERACTION, true, getX(), getEyeY() + 0.5, getZ(), 0, 0, 0);
            case Status.MCA_VILLAGER_POS_INTERACTION ->
                    level().addAlwaysVisibleParticle(ParticleTypesMCA.POS_INTERACTION, true, getX(), getEyeY() + 0.5, getZ(), 0, 0, 0);
            case Status.MCA_VILLAGER_TRAGEDY -> this.addParticlesAroundSelf(ParticleTypes.DAMAGE_INDICATOR);
            default -> super.handleEntityEvent(id);
        }
    }

    public void onInvChange(Container inventoryFromListener) {
        //nop
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    @Nullable
    public <T extends Mob> T convertTo(EntityType<T> type, ConversionParams params, ConversionParams.AfterConversion<T> afterConversion) {
        boolean convertingToZombieVillager = !isRemoved() && type.equals(EntityType.ZOMBIE_VILLAGER);
        if (convertingToZombieVillager) {
            residency.leaveHome();
        }
        EntityType<? extends Mob> convertedType = convertingToZombieVillager ? getGenetics().getGender().getZombieType() : type;

        UUID oldUuid = getUUID();

        return (T) super.convertTo((EntityType) convertedType, params, mob -> {
            ((ConversionParams.AfterConversion) afterConversion).finalizeConversion(mob);

            if (mob instanceof VillagerLike<?> zombie) {
                zombie.copyVillagerAttributesFrom(this);
            }

            if (mob instanceof ZombieVillager zombie) {
                ServerLevel serverLevel = (ServerLevel) level();
                zombie.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(zombie.blockPosition()), EntitySpawnReason.CONVERSION, new Zombie.ZombieGroupData(false, true));
                zombie.setVillagerData(getVillagerData());
                zombie.setGossips(getGossips().copy());
                zombie.setTradeOffers(getOffers().copy());
                zombie.setVillagerXp(getVillagerXp());
                zombie.setUUID(oldUuid);
                zombie.setPersistenceRequired();

                level().levelEvent(null, 1026, this.blockPosition(), 0);
            }

            if (mob instanceof ZombieVillagerEntityMCA zombie) {
                zombie.setInventory(inventory);
            }

            this.discard();
        });
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        CompoundTag nbt = readMcaSaveData(input);
        super.readAdditionalSaveData(input);

        // Profession eggs supply partial entity data after finalizeSpawn. Preserve generated
        // appearance for missing keys, while honoring explicit saved/editor values.
        CompoundTag trackedData = new CompoundTag();
        getTypeDataManager().save(this, trackedData);
        // An explicit Traits/Memories compound replaces the old value, including removals.
        for (String key : nbt.keySet()) {
            trackedData.put(key, java.util.Objects.requireNonNull(nbt.get(key)).copy());
        }
        getTypeDataManager().load(this, trackedData);
        traderLlamaCount = Math.max(0, Math.min(2, nbt.getIntOr("TraderLlamaCount", 0)));
        kidnappedUntil = nbt.getLongOr("KidnappedUntil", 0);
        entityData.set(CAPTIVE, kidnappedUntil > 0);
        captivityState = nbt.getCompound("CaptivityState").map(CompoundTag::copy).orElseGet(CompoundTag::new);
        if (isKidnapped()) noPhysics = isInvisible();
        shieldDisabledUntil = level().getGameTime() + Math.max(0, nbt.getIntOr("ShieldCooldown", 0));
        relations.readFromNbt(nbt);
        longTermMemory.readFromNbt(nbt);
        com.ancientkira.mca.server.PillagerRaids.read(this, nbt);
        readNicknames(nbt);
        chatAIPrompt = nbt.getString(CHAT_AI_PROMPT_KEY).orElse("");

        playerModel = PlayerModel.byId(nbt.getIntOr("PlayerModel", 0));

        updateAttributes();

        inventory.clearContent();
        InventoryUtils.readFromNBT(this.registryAccess(), inventory, nbt);

        if (nbt.contains("DespawnDelay")) {
            this.despawnDelay = nbt.getIntOr("DespawnDelay", this.despawnDelay);
        }

        if (nbt.contains("InteractedWith")) {
            this.interactedWith = nbt.getBooleanOr("InteractedWith", this.interactedWith);
        }

        if (nbt.contains("Clothes")) {
            validateClothes();
        }

        if (getProfession() == ProfessionsMCA.CULTIST) {
            setProfession(ProfessionsMCA.WANDERING_TRADER);
            setOffers(new MerchantOffers());
        }

        if (getVillagerBrain().getPersonality() == Personality.UNASSIGNED) {
            getVillagerBrain().randomize();
        }
    }

    @Override
    public void readAdditionalSaveDataForEditor(CompoundTag nbt) {
        readAdditionalSaveData(TagValueInput.create(ProblemReporter.DISCARDING, registryAccess(), nbt));
    }

    @Override
    public final void addAdditionalSaveData(ValueOutput output) {
        CompoundTag nbt = new CompoundTag();
        super.addAdditionalSaveData(output);

        nbt.putInt("TraderLlamaCount", traderLlamaCount);
        nbt.putLong("KidnappedUntil", kidnappedUntil);
        nbt.put("CaptivityState", captivityState.copy());
        nbt.putInt("ShieldCooldown", (int) Math.max(0, shieldDisabledUntil - level().getGameTime()));
        relations.writeToNbt(nbt);
        longTermMemory.writeToNbt(nbt);
        com.ancientkira.mca.server.PillagerRaids.write(this, nbt);
        writeNicknames(nbt);

        getTypeDataManager().save(this, nbt);
        InventoryUtils.saveToNBT(this.registryAccess(), inventory, nbt);
        nbt.putString(CHAT_AI_PROMPT_KEY, getChatAIPrompt());

        nbt.putInt("DespawnDelay", this.despawnDelay);
        nbt.putBoolean("InteractedWith", this.interactedWith);

        if (interactedWith) {
            VillagerTrackerManager.update(this);
        }

        storeMcaSaveData(output, nbt);
    }

    public String getChatAIPrompt() {
        if (chatAIPrompt.isBlank()) {
            chatAIPrompt = ChatAIContext.createVillagerPrompt();
        }
        return chatAIPrompt;
    }

    public void setChatAIPrompt(String chatAIPrompt) {
        this.chatAIPrompt = chatAIPrompt;
    }

    private boolean isCarriedByPlayer() {
        return getVehicle() instanceof Player;
    }

    /**
     * Vanilla excludes passengers from entity chunk storage. MCA children riding
     * players are not restored from player data, so they must remain save roots.
     */
    @Override
    public boolean shouldBeSaved() {
        if (!isCarriedByPlayer()) {
            return super.shouldBeSaved();
        }

        Entity.RemovalReason removalReason = getRemovalReason();
        return removalReason == null || removalReason.shouldSave();
    }

    @Override
    public boolean save(ValueOutput output) {
        return isCarriedByPlayer() ? saveAsPassenger(output) : super.save(output);
    }

    @Override
    public void writeAdditionalConversionData(CompoundTag output) {
        output.putString(CHAT_AI_PROMPT_KEY, chatAIPrompt);
        writeNicknames(output);
    }

    @Override
    public void readAdditionalConversionData(CompoundTag input) {
        chatAIPrompt = input.getString(CHAT_AI_PROMPT_KEY).orElse("");
        readNicknames(input);
    }

    @Override
    public boolean isHostile() {
        return getProfession() == ProfessionsMCA.OUTLAW
                || ProfessionsMCA.isRaider(getProfession()) && !isPeacefulRaider();
    }

    public boolean isPeacefulRaider() {
        return ProfessionsMCA.isRaider(getProfession()) && level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL;
    }

    //friends will not get slapped in revenge
    public boolean isFriend(EntityType<?> type) {
        return type == EntityType.IRON_GOLEM || type == EntitiesMCA.PERSON;
    }

    public boolean canFireProjectileWeapon(ProjectileWeaponItem weapon) {
        return true;
    }

    @Override
    public void setChargingCrossbow(boolean charging) {
        //nop
    }

    @Override
    public void onCrossbowAttackPerformed() {
        //nop
    }

    @Override
    public void thunderHit(ServerLevel world, LightningBolt lightning) {
        getTraits().addTrait(Traits.ELECTRIFIED);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void performRangedAttack(LivingEntity target, float pullProgress) {
        if (target == this || target.getUUID().equals(getUUID())) return;
        if (this.level().isClientSide() || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        setTarget(target);
        InteractionHand weaponHand = RangedWeaponHelper.getWeaponHoldingHand(this);
        if (weaponHand == null) {
            return;
        }

        ItemStack weaponStack = this.getItemInHand(weaponHand);
        if (weaponStack.getItem() instanceof CrossbowItem crossbow) {
            crossbow.performShooting(
                    this.level(),
                    this,
                    weaponHand,
                    weaponStack,
                    1.75F,
                    14 - this.level().getDifficulty().getId() * 4,
                    target
            );
            this.onCrossbowAttackPerformed();
            return;
        }

        if (weaponStack.getItem() instanceof BowItem) {
            ItemStack projectile = this.getProjectile(weaponStack);
            AbstractArrow arrowEntity = ProjectileUtil.getMobArrow(this, projectile, pullProgress, weaponStack);
            double xd = target.getX() - this.getX();
            double yd = getFriendlyArrowAimY(target) - arrowEntity.getY();
            double zd = target.getZ() - this.getZ();
            double distanceToTarget = Math.sqrt(xd * xd + zd * zd);
            Projectile.spawnProjectileUsingShoot(
                    arrowEntity, serverLevel, projectile, xd, yd + distanceToTarget * 0.2F, zd, 1.6F, FRIENDLY_ARROW_UNCERTAINTY
            );
            this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
        }
    }

    private static double getFriendlyArrowAimY(LivingEntity target) {
        return target.getY(target.getBbHeight() <= 1.0F ? 0.5D : 1.0D / 3.0D);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ItemStack getProjectile(ItemStack stack) {
        if (stack.getItem() instanceof ProjectileWeaponItem weapon) {
            Predicate<ItemStack> predicate = weapon.getSupportedHeldProjectiles();
            ItemStack itemStack = ProjectileWeaponItem.getHeldProjectile(this, predicate);
            return itemStack.isEmpty() ? new ItemStack(Items.ARROW) : itemStack;
        } else {
            return ItemStack.EMPTY;
        }
    }

    private void tickDespawnDelay() {
        if (this.despawnDelay > 0 && !this.isTrading() && --this.despawnDelay == 0) {
            if (getRelationships().getPartner().isPresent() || getVillagerBrain().getMemories().values().stream().anyMatch(m -> random.nextInt(Config.getInstance().marriageHeartsRequirement) < m.getHearts())) {
                setProfession(BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE).value());
                setDespawnDelay(0);
            } else {
                this.discard();
            }
        }
    }

    public int getDespawnDelay() {
        return this.despawnDelay;
    }

    public void setDespawnDelay(int despawnDelay) {
        this.despawnDelay = despawnDelay;
    }

    public void makeMercenary() {
        setProfession(ProfessionsMCA.MERCENARY);

        inventory.addItem(new ItemStack(Items.IRON_SWORD));
        inventory.addItem(new ItemStack(Items.IRON_AXE));
        inventory.addItem(new ItemStack(Items.IRON_PICKAXE));
        inventory.addItem(new ItemStack(Items.IRON_HOE));
        inventory.addItem(new ItemStack(Items.FISHING_ROD));
        inventory.addItem(new ItemStack(Items.BREAD, 16));
    }

    public void customLevelUp() {
        this.setVillagerData(this.getVillagerData().withLevel(this.getVillagerData().level() + 1));
        this.updateTrades((ServerLevel) this.level());
    }

    public String getNickname(UUID playerUUID) {
        return nicknames.getOrDefault(playerUUID, "");
    }

    public void setNickname(UUID playerUUID, String nickname) {
        String value = nickname.strip();
        if (value.length() > MAX_NICKNAME_LENGTH) {
            return;
        }

        if (value.isEmpty()) {
            nicknames.remove(playerUUID);
        } else {
            nicknames.put(playerUUID, value);
        }
    }

    private void readNicknames(CompoundTag nbt) {
        nicknames.clear();

        CompoundTag data = nbt.getCompound(NICKNAMES_KEY).orElseGet(CompoundTag::new);
        for (String playerUUID : data.keySet()) {
            try {
                setNickname(UUID.fromString(playerUUID), data.getString(playerUUID).orElse(""));
            } catch (IllegalArgumentException exception) {
                MCA.LOGGER.warn("Ignoring invalid nickname player UUID '{}'", playerUUID);
            }
        }
    }

    private void writeNicknames(CompoundTag nbt) {
        CompoundTag data = new CompoundTag();
        nicknames.forEach((playerUUID, nickname) -> data.putString(playerUUID.toString(), nickname));
        nbt.put(NICKNAMES_KEY, data);
    }
}
