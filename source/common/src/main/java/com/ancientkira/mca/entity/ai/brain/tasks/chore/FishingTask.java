package com.ancientkira.mca.entity.ai.brain.tasks.chore;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.VillagerFishingHook;
import com.ancientkira.mca.entity.ai.Chore;
import com.ancientkira.mca.entity.ai.brain.VillagerTasksMCA;
import com.ancientkira.mca.registry.EntitiesMCA;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Cast, hook and reel actual fish. There is no fishing loot-table fallback. */
public final class FishingTask extends Behavior<VillagerEntityMCA> {
    private final boolean professional;
    private boolean fishing;
    public boolean isFishing() { return fishing; }
    private AbstractFish fish;
    private VillagerFishingHook hook;
    private BlockPos bank;
    private ItemStack previousHand = ItemStack.EMPTY;
    private ItemStack rod = ItemStack.EMPTY;
    private boolean borrowed, supplied, wasNoAi;
    private long nextSearch, reelAt;
    private int reelTicks;
    public FishingTask() { this(false); }
    public FishingTask(boolean professional) {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED), 1200);
        this.professional = professional;
    }
    private boolean assigned(VillagerEntityMCA person) {
        return professional ? person.getProfession() == BuiltInRegistries.VILLAGER_PROFESSION.getValueOrThrow(VillagerProfession.FISHERMAN)
                && person.getVillagerBrain().getCurrentJob() == Chore.NONE && person.getBrain().isActive(Activity.WORK)
                : person.getVillagerBrain().getCurrentJob() == Chore.FISH;
    }
    private boolean available(VillagerEntityMCA person) {
        return assigned(person) && !person.isSleeping() && !person.isKidnapped()
                && !VillagerTasksMCA.hasMovementOrder(person) && !person.getVillagerBrain().isPanicking()
                && !person.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && person.getInteractions().getInteractingPlayer().isEmpty();
    }
    @Override protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA person) {
        if (!available(person) || world.getGameTime() < nextSearch) return false;
        nextSearch = world.getGameTime() + 80;
        fish = world.getEntitiesOfClass(AbstractFish.class, person.getBoundingBox().inflate(12, 5, 12),
                f -> f.isAlive() && f.isInWater() && !f.isPassenger() && !f.isNoAi() && !f.isPersistenceRequired())
                .stream().min(Comparator.comparingDouble(person::distanceToSqr)).orElse(null);
        if (fish == null) return false;
        bank = findBank(world, person, fish);
        return bank != null;
    }
    private BlockPos findBank(ServerLevel world, VillagerEntityMCA person, AbstractFish target) {
        BlockPos surface = target.blockPosition();
        for (int up = 0; up < 8 && world.getFluidState(surface.above()).is(FluidTags.WATER); up++) surface = surface.above();
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(surface.offset(-5,-2,-5), surface.offset(5,2,5))) {
            if (!world.hasChunkAt(pos) || !world.getFluidState(pos).isEmpty()
                    || !world.getBlockState(pos.below()).isFaceSturdy(world, pos.below(), net.minecraft.core.Direction.UP)
                    || pos.distSqr(target.blockPosition()) > 64) continue;
            if (world.noCollision(person, person.getBoundingBox().move(Vec3.atBottomCenterOf(pos).subtract(person.position())))) candidates.add(pos.immutable());
        }
        candidates.sort(Comparator.comparingDouble(p -> p.distSqr(person.blockPosition())));
        for (BlockPos pos : candidates.stream().limit(6).toList()) {
            var path = person.getNavigation().createPath(pos, 1);
            if (pos.distSqr(person.blockPosition()) < 2 || path != null && path.canReach()) return pos;
        }
        return null;
    }
    @Override protected void start(ServerLevel world, VillagerEntityMCA person, long time) {
        fishing = true;
        previousHand = person.getItemInHand(person.getDominantHand());
        rod = previousHand;
        borrowed = false; supplied = false; reelTicks = 0; hook = null; wasNoAi = false;
        if (!(previousHand.getItem() instanceof FishingRodItem)) {
            for (int slot = 0; slot < person.getInventory().getContainerSize(); slot++) {
                if (person.getInventory().getItem(slot).getItem() instanceof FishingRodItem) {
                    rod = person.getInventory().removeItem(slot, 1);
                    person.setItemInHand(person.getDominantHand(), rod);
                    borrowed = true; return;
                }
            }
            if (professional) { rod = new ItemStack(Items.FISHING_ROD); person.setItemInHand(person.getDominantHand(), rod); supplied = true; }
            else {
                person.getVillagerBrain().getJobAssigner().ifPresent(player -> person.sendChatMessage(player, "chore.fishing.norod"));
                person.getVillagerBrain().abandonJob();
            }
        }
    }
    @Override protected boolean canStillUse(ServerLevel world, VillagerEntityMCA person, long time) {
        return available(person) && fish != null && fish.isAlive() && fish.level() == world
                && person.distanceToSqr(fish) < 24*24 && person.getItemInHand(person.getDominantHand()).getItem() instanceof FishingRodItem;
    }
    @Override protected void tick(ServerLevel world, VillagerEntityMCA person, long time) {
        person.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(fish, true));
        if (hook == null && person.blockPosition().distSqr(bank) > 2) {
            person.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(bank, .5F, 1)); return;
        }
        if (hook == null) {
            if (!person.hasLineOfSight(fish)) { fish = null; return; }
            person.getNavigation().stop(); person.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            BlockPos surface = fish.blockPosition();
            for (int up = 0; up < 8 && world.getFluidState(surface.above()).is(FluidTags.WATER); up++) surface = surface.above();
            hook = new VillagerFishingHook(EntitiesMCA.VILLAGER_FISHING_HOOK, world);
            hook.cast(person, new Vec3(fish.getX(), surface.getY()+.85, fish.getZ()));
            world.addFreshEntity(hook); person.swing(person.getDominantHand());
            person.playSound(SoundEvents.FISHING_BOBBER_THROW, 1, 1);
            reelAt = time + 35 + person.getRandom().nextInt(35); return;
        }
        if (hook.isRemoved()) { fish = null; return; }
        if (time < reelAt || !hook.hasLanded()) return;
        if (reelTicks == 0) {
            if (hook.distanceToSqr(fish) > 16) { fish = null; return; }
            wasNoAi = fish.isNoAi(); fish.setNoAi(true); hook.reel();
            person.swing(person.getDominantHand()); person.playSound(SoundEvents.FISHING_BOBBER_RETRIEVE, 1, 1);
            world.sendParticles(ParticleTypes.SPLASH, hook.getX(), hook.getY(), hook.getZ(), 6, .2, .1, .2, .05);
        }
        reelTicks++;
        Vec3 pull = person.position().add(0,.6,0).subtract(fish.position());
        fish.setDeltaMovement(pull.normalize().scale(.3)); hook.followFish(fish);
        if (reelTicks > 100) { fish.setNoAi(wasNoAi); fish = null; return; }
        if (person.distanceToSqr(fish) <= 4) {
            var area = fish.getBoundingBox().inflate(1.5);
            Set<UUID> existing = new HashSet<>();
            world.getEntitiesOfClass(ItemEntity.class, area).forEach(item -> existing.add(item.getUUID()));
            fish.hurtServer(world, world.damageSources().mobAttack(person), Float.MAX_VALUE);
            if (!fish.isAlive()) {
                for (ItemEntity drop : world.getEntitiesOfClass(ItemEntity.class, area, item -> !existing.contains(item.getUUID()))) {
                    ItemStack remainder = person.getInventory().addItem(drop.getItem().copy());
                    if (remainder.isEmpty()) drop.discard(); else drop.setItem(remainder);
                }
                person.getItemInHand(person.getDominantHand()).hurtAndBreak(1, person, person.getDominantSlot());
            }
            fish.setNoAi(wasNoAi); fish = null;
        }
    }
    @Override protected void stop(ServerLevel world, VillagerEntityMCA person, long time) {
        if (hook != null) hook.discard();
        if (fish != null && reelTicks > 0) fish.setNoAi(wasNoAi);
        fishing = false;
        boolean restoreHand = person.getItemInHand(person.getDominantHand()) == rod
                || person.getItemInHand(person.getDominantHand()).isEmpty();
        if (borrowed && !rod.isEmpty()) {
            ItemStack remainder = person.getInventory().addItem(rod);
            if (!remainder.isEmpty()) world.addFreshEntity(new ItemEntity(world, person.getX(),person.getY(),person.getZ(),remainder));
        }
        if ((borrowed || supplied) && restoreHand) person.setItemInHand(person.getDominantHand(), previousHand);
        rod = ItemStack.EMPTY;
        person.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET); person.getNavigation().stop();
        fish = null; hook = null; bank = null; previousHand = ItemStack.EMPTY;
        nextSearch = time + 40;
    }
}
