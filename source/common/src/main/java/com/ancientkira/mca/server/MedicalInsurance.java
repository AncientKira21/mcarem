package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.server.world.data.PlayerSaveData;
import com.ancientkira.mca.server.world.data.Village;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.item.alchemy.Potions;
import java.util.*;

/** A service purchase: the merchant preview is consumed, and only the persisted policy remains. */
public final class MedicalInsurance {
    private static final String MARKER = "mcaMedicalInsurance";
    private static final Map<VillagerEntityMCA, ServerPlayer> PATIENTS = new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private static final Map<VillagerEntityMCA, Long> NEXT_TREATMENT = new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private MedicalInsurance() {}
    public static boolean isPolicy(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBooleanOr(MARKER, false);
    }
    public static boolean insured(VillagerEntityMCA cleric, ServerPlayer player) {
        return PlayerSaveData.get(player).hasMedicalPolicy(cleric.getUUID())
                || cleric.getRelationships().getFamilyEntry().isFamilyMember(player.getUUID());
    }
    public static boolean eligible(VillagerEntityMCA cleric, ServerPlayer player) {
        return insured(cleric, player) && cleric.getResidency().getHomeVillage()
                .or(() -> Village.findNearest(cleric)).map(v -> v.getReputation(player) >= 0).orElse(false);
    }
    public static void prepareOffers(VillagerEntityMCA cleric, MerchantOffers offers) {
        if (cleric.level().isClientSide()) return;
        if (!cleric.getVillagerData().profession().is(VillagerProfession.CLERIC)
                || !(cleric.getTradingPlayer() instanceof ServerPlayer player)) return;
        if (insured(cleric, player)) { offers.removeIf(o -> isPolicy(o.getResult())); return; }
        if (offers.stream().anyMatch(o -> isPolicy(o.getResult()))) return;
        ItemStack preview = new ItemStack(Items.PAPER);
        CompoundTag tag = new CompoundTag(); tag.putBoolean(MARKER, true);
        preview.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        preview.set(DataComponents.CUSTOM_NAME, Component.translatable("mca.insurance.trade"));
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 16), preview, 1, 0, 0));
    }
    public static void purchase(VillagerEntityMCA cleric, MerchantOffer offer) {
        if (isPolicy(offer.getResult()) && cleric.getTradingPlayer() instanceof ServerPlayer player) {
            PlayerSaveData.get(player).insureWith(cleric.getUUID());
            player.sendSystemMessage(Component.translatable("mca.insurance.purchased", cleric.getDisplayName()));
        }
    }
    public static void consumePreview(net.minecraft.world.entity.player.Player player, ItemStack carried) {
        if (isPolicy(carried)) carried.setCount(0);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isPolicy(player.getInventory().getItem(i))) player.getInventory().setItem(i, ItemStack.EMPTY);
        }
        if (isPolicy(player.containerMenu.getCarried())) player.containerMenu.setCarried(ItemStack.EMPTY);
    }
    public static String status(ServerPlayer player) {
        var village = Village.findNearest(player);
        if (village.isEmpty()) return "none";
        boolean covered = false;
        for (var e : player.level().getAllEntities()) {
            if (e instanceof VillagerEntityMCA cleric && cleric.isAlive()
                    && cleric.getVillagerData().profession().is(VillagerProfession.CLERIC)
                    && cleric.getResidency().getHomeVillage().filter(v -> v.getId() == village.get().getId()).isPresent()
                    && insured(cleric, player)) { covered = true; break; }
        }
        return !covered ? "none" : village.get().getReputation(player) < 0 ? "suspended" : "active";
    }
    private static boolean needsCare(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && (player.getHealth() < player.getMaxHealth() * .85F
                || player.hasEffect(MobEffects.WITHER) || player.isOnFire());
    }
    public static boolean isHelping(VillagerEntityMCA cleric) { return PATIENTS.containsKey(cleric); }
    public static void tick(VillagerEntityMCA cleric) {
        if (!(cleric.level() instanceof ServerLevel world)) return;
        ServerPlayer patient = PATIENTS.get(cleric);
        if (!cleric.isAlive() || cleric.isKidnapped() || cleric.isNoAi()
                || !cleric.getVillagerData().profession().is(VillagerProfession.CLERIC) || InfirmaryCare.isPatient(cleric)) {
            if (patient != null) finish(cleric); return;
        }
        if (patient != null && (patient.level() != world || patient.distanceToSqr(cleric) > 16384
                || !needsCare(patient) || !eligible(cleric, patient))) { finish(cleric); return; }
        if (patient == null) {
            if (cleric.tickCount % 20 != 0 || ClericAid.isHelping(cleric)
                    || cleric.getInteractions().getInteractingPlayer().isPresent()) return;
            patient = world.players().stream().filter(p -> needsCare(p) && p.distanceToSqr(cleric) <= 16384
                    && eligible(cleric, p) && !PATIENTS.containsValue(p))
                    .min(Comparator.comparingDouble(cleric::distanceToSqr)).orElse(null);
            if (patient == null) return;
            PATIENTS.put(cleric, patient);
            NEXT_TREATMENT.put(cleric, world.getGameTime() + 20);
            cleric.sendChatToAllAround("villager.cleric_response");
        }
        if (cleric.isSleeping()) cleric.stopSleeping();
        cleric.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        cleric.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(patient, true));
        if (patient.distanceToSqr(cleric) > 4 || !cleric.hasLineOfSight(patient)) {
            cleric.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(patient, false), .65F, 1)); return;
        }
        cleric.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET); cleric.getNavigation().stop();
        if (world.getGameTime() < NEXT_TREATMENT.getOrDefault(cleric, 0L)) return;
        if (patient.hasEffect(MobEffects.WITHER)) {
            patient.removeEffect(MobEffects.WITHER);
            world.sendParticles(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, Items.MILK_BUCKET),
                    patient.getX(), patient.getY() + 1, patient.getZ(), 8, .15, .1, .15, .02);
            patient.playSound(net.minecraft.sounds.SoundEvents.GENERIC_DRINK.value(), 1, 1);
        } else {
            RaiderCombat.throwPotion(world, cleric, patient,
                    patient.isOnFire() && !patient.hasEffect(MobEffects.FIRE_RESISTANCE) ? Potions.FIRE_RESISTANCE
                            : patient.getHealth() <= patient.getMaxHealth() * .5F ? Potions.STRONG_HEALING
                            : !patient.hasEffect(MobEffects.REGENERATION) ? Potions.REGENERATION : Potions.HEALING);
        }
        NEXT_TREATMENT.put(cleric, world.getGameTime() + 60);
    }
    private static void finish(VillagerEntityMCA cleric) {
        PATIENTS.remove(cleric); NEXT_TREATMENT.remove(cleric);
        cleric.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        cleric.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET); cleric.getNavigation().stop();
        cleric.getBrain().updateActivityFromSchedule(cleric.level().environmentAttributes(), cleric.level().getGameTime(), cleric.position());
    }
}
