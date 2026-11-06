package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import java.util.*;

/** Optional Power Coils Reborn support; only coils actually supplied to the villager are used. */
public final class PowerCoilCompatibility {
    private record Held(ItemStack coil, ItemStack previous) { }
    private static final Map<VillagerEntityMCA,Held> HELD = new WeakHashMap<>();
    private PowerCoilCompatibility() { }
    private static String kind(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("pcoil") ? id.getPath() : "";
    }
    private static double setting(String name, double fallback) {
        for (String prefix : List.of("com.ancientkira.pcoil.configuration.", "com.ancientkira.pcoil.shared.configuration.")) {
            try {
                Object value = Class.forName(prefix + "PowerCoilsServerConfigConfiguration").getField(name).get(null);
                return ((Number)value.getClass().getMethod("get").invoke(value)).doubleValue();
            } catch (ReflectiveOperationException | ClassCastException ignored) { }
        }
        return fallback;
    }
    public static void tick(VillagerEntityMCA person) {
        if (person.level().isClientSide() || person.tickCount % 5 != 0) return;
        boolean moving = person.isSprinting() || person.getNavigation().isInProgress()
                || person.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
        boolean falling = !person.onGround() && person.getDeltaMovement().y < -.15 && !person.isInWater();
        ItemStack chosen = ItemStack.EMPTY;
        int best = 0;
        if (!person.isSleeping() && !person.isKidnapped() && person.isAlive()
                && !person.getItemBySlot(person.getOpposingSlot()).has(net.minecraft.core.component.DataComponents.DEATH_PROTECTION)
                && !person.isBlocking()) {
            for (int slot=0; slot<person.getInventory().getContainerSize(); slot++) {
                ItemStack stack = person.getInventory().getItem(slot);
                String kind = kind(stack);
                int score = switch (kind) {
                    case "regeneration_coil" -> person.getHealth()<person.getMaxHealth() ? 5 : 0;
                    case "fusion_coil" -> moving || falling ? 4 : 0;
                    case "gravity_coil", "snowy_gravity_coil" -> falling || person.horizontalCollision && moving ? 3 : 0;
                    case "speed_coil" -> moving ? 2 : 0;
                    default -> 0;
                };
                if(score>best){best=score;chosen=stack;}
            }
        }
        Held old = HELD.get(person);
        if(chosen.isEmpty()) {
            if(old!=null && person.getItemBySlot(person.getOpposingSlot())==old.coil())
                person.setItemSlot(person.getOpposingSlot(), old.previous());
            HELD.remove(person); return;
        }
        ItemStack previous = old==null ? person.getItemBySlot(person.getOpposingSlot()) : old.previous();
        HELD.put(person,new Held(chosen,previous));
        person.setItemSlot(person.getOpposingSlot(),chosen);
        String kind=kind(chosen);
        if(kind.equals("speed_coil") || kind.equals("fusion_coil")) {
            String config=kind.equals("fusion_coil")?"FUSION_COIL_SPEED":"SPEED_COIL_SPEED";
            person.addEffect(new MobEffectInstance(MobEffects.SPEED,6,Math.max(0,(int)setting(config,5)-1),false,false));
        }
        if(kind.contains("gravity") || kind.equals("fusion_coil")) {
            String config=kind.equals("fusion_coil")?"FUSION_COIL_JUMP":kind.equals("snowy_gravity_coil")?"SNOWY_GRAVITY_COIL_JUMP":"GRAVITY_COIL_JUMP";
            person.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST,6,Math.max(0,(int)setting(config,3)-1),false,false));
            if(falling) {person.fallDistance=0;person.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,6,0,false,false));}
        }
        if(kind.equals("regeneration_coil") && person.tickCount%10==0)
            person.heal((float)setting("REGEN_COIL_HEAL",2)/2);
    }
}
