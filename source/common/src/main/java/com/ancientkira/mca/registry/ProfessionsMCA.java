package com.ancientkira.mca.registry;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import com.ancientkira.mca.MCA;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public interface ProfessionsMCA {
    Map<Identifier, VillagerProfession> PROFESSIONS = new HashMap<>();

    Set<ResourceKey<VillagerProfession>> CAN_NOT_TRADE = new HashSet<>();
    Set<ResourceKey<VillagerProfession>> IS_IMPORTANT = new HashSet<>();
    Set<ResourceKey<VillagerProfession>> NEEDS_NO_HOME = new HashSet<>();

    VillagerProfession VINDICATOR = register("vindicator", false, true, true, poi -> false, poi -> false, SoundEvents.VINDICATOR_AMBIENT);
    VillagerProfession EVOKER = register("evoker", false, true, true, poi -> false, poi -> false, SoundEvents.EVOKER_AMBIENT);
    VillagerProfession WITCH = register("witch", false, true, true, poi -> false, poi -> false, SoundEvents.WITCH_AMBIENT);
    VillagerProfession PILLAGER = register("pillager", false, true, true, poi -> false, poi -> false, SoundEvents.PILLAGER_AMBIENT);
    VillagerProfession OUTLAW = register("outlaw", false, true, true, PoiType.NONE, VillagerProfession.ALL_ACQUIRABLE_JOBS, SoundEvents.VILLAGER_WORK_FARMER);
    VillagerProfession GUARD = register("guard", false, true, false, PoiType.NONE, VillagerProfession.ALL_ACQUIRABLE_JOBS, SoundEvents.VILLAGER_WORK_ARMORER);
    VillagerProfession ARCHER = register("archer", false, true, false, PoiType.NONE, VillagerProfession.ALL_ACQUIRABLE_JOBS, SoundEvents.VILLAGER_WORK_FLETCHER);
    VillagerProfession ADVENTURER = register(
            "adventurer",
            true,
            true,
            true,
            PoiType.NONE,
            VillagerProfession.ALL_ACQUIRABLE_JOBS,
            SoundEvents.VILLAGER_WORK_FLETCHER,
            TradeOffersMCA.ADVENTURER_TRADES
    );
    VillagerProfession MERCENARY = register("mercenary", false, true, true, PoiType.NONE, VillagerProfession.ALL_ACQUIRABLE_JOBS, SoundEvents.VILLAGER_WORK_FLETCHER);
    VillagerProfession WANDERING_TRADER = register("wandering_trader", true, true, true, PoiType.NONE,
            VillagerProfession.ALL_ACQUIRABLE_JOBS, SoundEvents.WANDERING_TRADER_AMBIENT);
    // Retain the old registry entry so existing worlds can migrate safely.
    VillagerProfession CULTIST = register(
            "cultist",
            true,
            true,
            true,
            PoiType.NONE,
            VillagerProfession.ALL_ACQUIRABLE_JOBS,
            SoundEvents.VILLAGER_WORK_FLETCHER,
            TradeOffersMCA.CULTIST_TRADES
    );

    static VillagerProfession register(String name, boolean canTradeWith, boolean important, boolean needsNoHome, Predicate<Holder<PoiType>> heldWorkstation, Predicate<Holder<PoiType>> acquirableWorkstation, @Nullable SoundEvent workSound) {
        return register(name, canTradeWith, important, needsNoHome, heldWorkstation, acquirableWorkstation, ImmutableSet.of(), ImmutableSet.of(), workSound, Int2ObjectMap.ofEntries());
    }

    static VillagerProfession register(
            String name,
            boolean canTradeWith,
            boolean important,
            boolean needsNoHome,
            Predicate<Holder<PoiType>> heldWorkstation,
            Predicate<Holder<PoiType>> acquirableWorkstation,
            @Nullable SoundEvent workSound,
            Int2ObjectMap<ResourceKey<TradeSet>> tradeSetsByLevel
    ) {
        return register(name, canTradeWith, important, needsNoHome, heldWorkstation, acquirableWorkstation, ImmutableSet.of(), ImmutableSet.of(), workSound, tradeSetsByLevel);
    }

    static VillagerProfession register(String name, boolean canTradeWith, boolean important, boolean needsNoHome, ResourceKey<PoiType> heldWorkstation, ImmutableSet<Item> gatherableItems, ImmutableSet<Block> secondaryJobSites, @Nullable SoundEvent workSound) {
        return register(name, canTradeWith, important, needsNoHome, (entry) -> {
            return entry.is(heldWorkstation);
        }, (entry) -> {
            return entry.is(heldWorkstation);
        }, gatherableItems, secondaryJobSites, workSound, Int2ObjectMap.ofEntries());
    }

    static VillagerProfession register(String name, boolean canTradeWith, boolean important, boolean needsNoHome, Predicate<Holder<PoiType>> heldWorkstation, Predicate<Holder<PoiType>> acquirableWorkstation, ImmutableSet<Item> gatherableItems, ImmutableSet<Block> secondaryJobSites, @Nullable SoundEvent workSound) {
        return register(name, canTradeWith, important, needsNoHome, heldWorkstation, acquirableWorkstation, gatherableItems, secondaryJobSites, workSound, Int2ObjectMap.ofEntries());
    }

    static VillagerProfession register(
            String name,
            boolean canTradeWith,
            boolean important,
            boolean needsNoHome,
            Predicate<Holder<PoiType>> heldWorkstation,
            Predicate<Holder<PoiType>> acquirableWorkstation,
            ImmutableSet<Item> gatherableItems,
            ImmutableSet<Block> secondaryJobSites,
            @Nullable SoundEvent workSound,
            Int2ObjectMap<ResourceKey<TradeSet>> tradeSetsByLevel
    ) {
        Identifier id = MCA.locate(name);
        ResourceKey<VillagerProfession> professionKey = ResourceKey.create(BuiltInRegistries.VILLAGER_PROFESSION.key(), id);
        VillagerProfession result = new VillagerProfession(
                Component.translatable("entity.minecraft.villager." + id.getNamespace() + "." + id.getPath()),
                heldWorkstation,
                acquirableWorkstation,
                gatherableItems,
                secondaryJobSites,
                workSound,
                tradeSetsByLevel
        );
        if (!canTradeWith) {
            CAN_NOT_TRADE.add(professionKey);
        }
        if (important) {
            IS_IMPORTANT.add(professionKey);
        }
        if (needsNoHome) {
            ProfessionsMCA.NEEDS_NO_HOME.add(professionKey);
        }
        PROFESSIONS.put(id, result);
        return result;
    }

    static boolean isRaider(VillagerProfession profession) {
        return profession == PILLAGER || profession == VINDICATOR || profession == EVOKER || profession == WITCH;
    }

    static boolean isSpellcaster(VillagerProfession profession) {
        return profession == EVOKER || profession == WITCH;
    }

    static String getFavoredBuilding(VillagerProfession profession) {
        Identifier professionId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        if (professionId != null && (
                professionId.equals(VillagerProfession.CARTOGRAPHER.identifier())
                        || professionId.equals(VillagerProfession.LIBRARIAN.identifier())
                        || professionId.equals(VillagerProfession.CLERIC.identifier()))) {
            return "library";
        } else if (GUARD == profession || ARCHER == profession) {
            return "inn";
        }
        return null;
    }

    static void registerProfessions(MCA.RegisterHelper<VillagerProfession> helper) {
        PROFESSIONS.forEach(helper::register);

        CAN_NOT_TRADE.add(VillagerProfession.NONE);
        CAN_NOT_TRADE.add(VillagerProfession.NITWIT);
    }
}
