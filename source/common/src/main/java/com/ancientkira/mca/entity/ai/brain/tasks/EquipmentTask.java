package com.ancientkira.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import com.ancientkira.mca.entity.EquipmentSet;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.ActivitiesMCA;
import com.ancientkira.mca.entity.ai.MemoryModuleTypeMCA;
import com.ancientkira.mca.util.InventoryUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;

import java.util.function.Function;
import java.util.function.Predicate;

public class EquipmentTask extends Behavior<VillagerEntityMCA> {
    private static final int COOLDOWN = 100;
    private static final int CHECK_INTERVAL = 20;
    private final Predicate<VillagerEntityMCA> condition;
    private final Function<VillagerEntityMCA, EquipmentSet> equipmentSet;
    private int lastEquipTime;
    private boolean lastArmorWearState;
    private boolean lastArmorRemoved;
    private int lastCheckTick = -CHECK_INTERVAL;
    private boolean cachedConditionResult;
    private EquipmentSet cachedEquipmentSet;
    private EquipmentSet appliedEquipmentSet;
    private boolean appliedEnchantments;

    public EquipmentTask(Predicate<VillagerEntityMCA> condition, Function<VillagerEntityMCA, EquipmentSet> set) {
        super(ImmutableMap.of(MemoryModuleTypeMCA.WEARS_ARMOR, MemoryStatus.REGISTERED));
        this.condition = condition;
        equipmentSet = set;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA villager) {
        //armor visibility settings have been changed
        if (lastArmorRemoved != villager.isArmorRemovedByOrder() || lastArmorWearState != villager.getVillagerBrain().getArmorWear()) {
            return true;
        }

        // Check condition with cache to avoid repeated expensive tests
        if (villager.tickCount - lastCheckTick >= CHECK_INTERVAL) {
            lastCheckTick = villager.tickCount;
            cachedConditionResult = condition.test(villager);
            cachedEquipmentSet = cachedConditionResult ? orientForDominantHand(villager, equipmentSet.apply(villager)) : null;
        }

        EquipmentSet set = cachedEquipmentSet;
        if (isNakedCombatSet(set, villager)) {
            return false;
        }

        boolean preserveMourningHands = isPeacefullyGrieving(villager);
        boolean present = villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.WEARS_ARMOR).isPresent();
        if (cachedConditionResult) {
            lastEquipTime = villager.tickCount;
            return !present || set != null && !preserveMourningHands
                    && (!set.equals(appliedEquipmentSet) || shouldEnchant(villager) != appliedEnchantments
                    || isMissingRequestedHandItem(villager, set) || hasUnequippedShield(villager) || hasUnequippedGun(villager));
        } else if (villager.tickCount - lastEquipTime > COOLDOWN) {
            return present;
        } else {
            return false;
        }
    }

    private void equipBestArmor(VillagerEntityMCA villager, EquipmentSlot slot, Item fallback) {
        ItemStack stack = InventoryUtils.getBestArmor(villager.getInventory(), slot).orElse(fallback == null ? ItemStack.EMPTY : new ItemStack(fallback));
        if (fallback != null && !stack.isEmpty() && new ItemStack(fallback).getMaxDamage() > stack.getMaxDamage()) {
            stack = new ItemStack(fallback);
        }
        villager.setItemSlot(slot, enchantSupplied(villager, stack, slot));
    }

    private void equipBestWeapon(VillagerEntityMCA villager, Item fallback) {
        if (com.ancientkira.mca.entity.ai.GunCompatibility.equipSupplied(villager)) return;
        if (villager.getItemBySlot(villager.getDominantSlot()).has(net.minecraft.core.component.DataComponents.DEATH_PROTECTION)) return;
        ItemStack stack = InventoryUtils.getBestSword(villager.getInventory()).orElse(fallback == null ? ItemStack.EMPTY : new ItemStack(fallback));
        if (fallback != null && new ItemStack(fallback).getMaxDamage() > stack.getMaxDamage()) stack = new ItemStack(fallback);
        villager.setItemSlot(villager.getDominantSlot(), enchantSupplied(villager, stack, villager.getDominantSlot()));
    }

    private void equipBestRanged(VillagerEntityMCA villager, Item fallback) {
        if (com.ancientkira.mca.entity.ai.GunCompatibility.equipSupplied(villager)) return;
        if (villager.getItemBySlot(villager.getDominantSlot()).has(net.minecraft.core.component.DataComponents.DEATH_PROTECTION)) return;
        ItemStack stack = InventoryUtils.getBestRanged(villager.getInventory()).orElse(fallback == null ? ItemStack.EMPTY : new ItemStack(fallback));
        villager.setItemSlot(villager.getDominantSlot(), enchantSupplied(villager, stack, villager.getDominantSlot()));
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        super.start(world, villager, time);

        lastArmorWearState = villager.getVillagerBrain().getArmorWear();
        lastArmorRemoved = villager.isArmorRemovedByOrder();
        boolean wear = cachedConditionResult;
        EquipmentSet set = cachedEquipmentSet;

        if ((wear || villager.getVillagerBrain().getArmorWear()) && set == null) {
            set = orientForDominantHand(villager, equipmentSet.apply(villager));
            cachedEquipmentSet = set;
        }

        if (isNakedCombatSet(set, villager)) {
            return;
        }

        appliedEquipmentSet = set;
        appliedEnchantments = shouldEnchant(villager);

        //remember last state
        if (wear) {
            villager.getBrain().setMemory(MemoryModuleTypeMCA.WEARS_ARMOR, true);
        } else {
            villager.getBrain().eraseMemory(MemoryModuleTypeMCA.WEARS_ARMOR);
        }

        // Peaceful grieving owns both hand slots so routine equipment refreshes
        // cannot remove or replace the flower. Combat still takes priority.
        if (!isPeacefullyGrieving(villager)) {
            if (wear) {
                if (isRequestedItem(set.getMainHand()) && set.getMainHand() instanceof ProjectileWeaponItem) {
                    equipBestRanged(villager, set.getMainHand());
                } else if (isRequestedItem(set.getMainHand())) {
                    equipBestWeapon(villager, set.getMainHand());
                } else {
                    villager.setItemSlot(villager.getDominantSlot(), ItemStack.EMPTY);
                }
                ItemStack offHand = new ItemStack(set.getGetOffHand());
                if (villager.usesCombatShield()) {
                    if (villager.getItemBySlot(villager.getOpposingSlot()).has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS)) {
                        offHand = villager.getItemBySlot(villager.getOpposingSlot());
                    }
                    for (int slot = 0; slot < villager.getInventory().getContainerSize(); slot++) {
                        ItemStack supplied = villager.getInventory().getItem(slot);
                        if (supplied.has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS)) {
                            offHand = supplied;
                            break;
                        }
                    }
                }
                if (villager.isArmorRemovedByOrder() && offHand.has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS)) {
                    if (!keepSuppliedShieldIfBagFull(villager, offHand)) offHand = ItemStack.EMPTY;
                }
                if (!villager.getItemBySlot(villager.getOpposingSlot()).has(net.minecraft.core.component.DataComponents.DEATH_PROTECTION)) {
                    villager.setItemSlot(villager.getOpposingSlot(), offHand);
                }
            } else {
                if (!villager.getItemBySlot(villager.getDominantSlot()).has(net.minecraft.core.component.DataComponents.DEATH_PROTECTION)) {
                    villager.setItemInHand(villager.getDominantHand(), ItemStack.EMPTY);
                }
                if (!villager.getItemBySlot(villager.getOpposingSlot()).has(net.minecraft.core.component.DataComponents.DEATH_PROTECTION)
                        && !(villager.getVillagerBrain().getArmorWear()
                            && villager.getItemBySlot(villager.getOpposingSlot()).has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS))) {
                    if(!keepSuppliedShieldIfBagFull(villager,villager.getItemBySlot(villager.getOpposingSlot())))
                        villager.setItemInHand(villager.getOpposingHand(), ItemStack.EMPTY);
                }
            }
        }

        //armor
        if (!villager.isArmorRemovedByOrder() && (wear || villager.getVillagerBrain().getArmorWear())) {
            if (!com.ancientkira.mca.registry.ProfessionsMCA.isRaider(villager.getProfession())
                    || !villager.getItemBySlot(EquipmentSlot.HEAD).is(net.minecraft.tags.ItemTags.BANNERS)) {
                equipBestArmor(villager, EquipmentSlot.HEAD, set.getHead());
            }
            equipBestArmor(villager, EquipmentSlot.CHEST, set.getChest());
            equipBestArmor(villager, EquipmentSlot.LEGS, set.getLegs());
            equipBestArmor(villager, EquipmentSlot.FEET, set.getFeet());
        } else {
            villager.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            villager.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            villager.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            villager.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        }
    }

    private static boolean shouldEnchant(VillagerEntityMCA person) {
        return person.isGuard() && person.getResidency().getEquipmentVillage()
                .map(v -> v.getVillageGuardsManager().suppliesEnchantedEquipment()).orElse(false);
    }

    private static ItemStack enchantSupplied(VillagerEntityMCA person, ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty() || !shouldEnchant(person)) return stack;
        ItemStack enchanted = stack.copy();
        var registry = person.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var key = slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET
                ? net.minecraft.world.item.enchantment.Enchantments.PROTECTION
                : stack.getItem() instanceof ProjectileWeaponItem ? net.minecraft.world.item.enchantment.Enchantments.POWER
                : net.minecraft.world.item.enchantment.Enchantments.SHARPNESS;
        var enchantment = registry.getOrThrow(key);
        int level = key == net.minecraft.world.item.enchantment.Enchantments.PROTECTION ? 4 : 3;
        enchanted.enchant(enchantment, Math.max(level,
                net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(enchantment, enchanted)));
        var unbreaking = registry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING);
        enchanted.enchant(unbreaking, Math.max(3,
                net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(unbreaking, enchanted)));
        return enchanted;
    }

    private static boolean isNakedCombatSet(EquipmentSet set, VillagerEntityMCA villager) {
        return (set == null || set == EquipmentSet.NAKED)
                && !villager.isKidnapped()
                && (villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).isPresent()
                    || com.ancientkira.mca.entity.ai.brain.VillagerTasksMCA.needsDefensiveEquipment(villager));
    }

    private static EquipmentSet orientForDominantHand(VillagerEntityMCA villager, EquipmentSet set) {
        // EquipmentSet hand entries describe weapon/shield roles. The equip code
        // already maps those roles to the villager's dominant/opposing slots.
        return set;
    }

    private static boolean isPeacefullyGrieving(VillagerEntityMCA villager) {
        return villager.getBrain().isActive(ActivitiesMCA.GRIEVE)
               && villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).isEmpty()
               && !villager.getVillagerBrain().isPanicking();
    }

    private static boolean keepSuppliedShieldIfBagFull(VillagerEntityMCA person,ItemStack shield){
        var data=shield.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if(data==null || !data.copyTag().getBooleanOr("mcaSuppliedShield",false))return false;
        var inventory=person.getInventory();
        for(int i=0;i<inventory.getContainerSize();i++)if(inventory.getItem(i)==shield)return false;
        return !inventory.addItem(shield.copy()).isEmpty();
    }

    private static boolean hasUnequippedShield(VillagerEntityMCA person) {
        if (person.isArmorRemovedByOrder() || !person.usesCombatShield()
                || person.getItemBySlot(person.getOpposingSlot()).has(net.minecraft.core.component.DataComponents.DEATH_PROTECTION)
                || person.getItemBySlot(person.getOpposingSlot()).has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS)) return false;
        for (int i = 0; i < person.getInventory().getContainerSize(); i++) {
            if (person.getInventory().getItem(i).has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS)) return true;
        }
        return false;
    }

    private static boolean hasUnequippedGun(VillagerEntityMCA person){
        if(!com.ancientkira.mca.entity.ai.GunCompatibility.eligible(person)
                || com.ancientkira.mca.entity.ai.GunCompatibility.hand(person)!=null)return false;
        for(int i=0;i<person.getInventory().getContainerSize();i++)
            if(com.ancientkira.mca.entity.ai.GunCompatibility.isGun(person.getInventory().getItem(i)))return true;
        return false;
    }

    private static boolean isMissingRequestedHandItem(VillagerEntityMCA villager, EquipmentSet set) {
        return isMissingRequestedItem(villager.getItemBySlot(villager.getDominantSlot()), set.getMainHand())
                || !villager.isArmorRemovedByOrder() && isRequestedItem(set.getGetOffHand()) && villager.getItemBySlot(villager.getOpposingSlot()).isEmpty();
    }

    private static boolean isMissingRequestedItem(ItemStack equipped, Item requested) {
        if (!isRequestedItem(requested)) {
            return false;
        }
        if (com.ancientkira.mca.entity.ai.GunCompatibility.isGun(equipped)) return false;
        if (requested instanceof ProjectileWeaponItem) {
            return !(equipped.getItem() instanceof ProjectileWeaponItem);
        }
        return equipped.isEmpty();
    }

    private static boolean isRequestedItem(Item item) {
        return item != null && item != Items.AIR;
    }
}
