package com.ancientkira.mca.entity.ai;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.registry.ProfessionsMCA;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.lang.reflect.Method;
import java.util.Optional;

/** Optional integration with JEG's public mob firing API; no gun mod is required. */
public final class GunCompatibility {
    private GunCompatibility() {}
    private record Api(Method fire, Method stats, Method loaded) {}
    private static final ClassValue<Optional<Api>> APIS = new ClassValue<>() {
        @Override protected Optional<Api> computeValue(Class<?> type) {
            boolean jeg=false;
            for (Class<?> c=type;c!=null;c=c.getSuperclass()) if (c.getName().equals("ttv.migami.jeg.item.GunItem")) jeg=true;
            if (!jeg) return Optional.empty();
            try { return Optional.of(new Api(type.getMethod("fireAt",Level.class,LivingEntity.class,ItemStack.class,LivingEntity.class),
                    type.getMethod("getStats"),type.getMethod("usesLoadedAmmo"))); }
            catch (ReflectiveOperationException e) { return Optional.empty(); }
        }
    };
    public static boolean isGun(ItemStack stack) { return !stack.isEmpty() && APIS.get(stack.getItem().getClass()).isPresent(); }
    public static boolean eligible(VillagerEntityMCA person) {
        var p=person.getProfession();
        return person.getAgeState()!=com.ancientkira.mca.entity.ai.relationship.AgeState.BABY
                && (p==ProfessionsMCA.GUARD || p==ProfessionsMCA.ARCHER || p==ProfessionsMCA.PILLAGER || p==ProfessionsMCA.VINDICATOR);
    }
    public static InteractionHand hand(LivingEntity person) {
        if (!(person instanceof VillagerEntityMCA v) || !eligible(v)) return null;
        if (isGun(person.getMainHandItem())) return InteractionHand.MAIN_HAND;
        return isGun(person.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
    }
    public static boolean equipSupplied(VillagerEntityMCA person) {
        if (!eligible(person)) return false;
        if (person.getItemBySlot(person.getDominantSlot()).has(net.minecraft.core.component.DataComponents.DEATH_PROTECTION)) return true;
        if (isGun(person.getItemBySlot(person.getDominantSlot()))) return true;
        for (int i=0;i<person.getInventory().getContainerSize();i++) {
            ItemStack stack=person.getInventory().getItem(i);
            if (isGun(stack)) { person.setItemSlot(person.getDominantSlot(),stack); return true; }
        }
        return false;
    }
    private static Object stats(ItemStack stack) throws ReflectiveOperationException { return APIS.get(stack.getItem().getClass()).orElseThrow().stats().invoke(stack.getItem()); }
    public static int stat(ItemStack stack,String name,int fallback) {
        try { Object stats=stats(stack);return Math.max(1,((Number)stats.getClass().getMethod(name).invoke(stats)).intValue()); }
        catch (ReflectiveOperationException | RuntimeException e) { return fallback; }
    }
    @SuppressWarnings("unchecked")
    private static DataComponentType<Integer> ammoComponent(ItemStack stack) throws ReflectiveOperationException {
        Class<?> type=Class.forName("ttv.migami.jeg.init.ModDataComponents",true,stack.getItem().getClass().getClassLoader());
        Object supplier=type.getField("GUN_AMMO").get(null);
        return (DataComponentType<Integer>)((java.util.function.Supplier<?>)supplier).get();
    }
    public static int ammo(ItemStack stack) {
        try {
            Api api=APIS.get(stack.getItem().getClass()).orElseThrow();
            if (!(boolean)api.loaded().invoke(stack.getItem())) return Integer.MAX_VALUE;
            return stack.getOrDefault(ammoComponent(stack),0);
        } catch (ReflectiveOperationException | RuntimeException e) { return 0; }
    }
    public static boolean reload(ItemStack stack) {
        try { stack.set(ammoComponent(stack),stat(stack,"magazineSize",1));return true; }
        catch (ReflectiveOperationException | RuntimeException e) { return false; }
    }
    public static boolean fire(VillagerEntityMCA shooter,ItemStack stack,LivingEntity target) {
        try {
            APIS.get(stack.getItem().getClass()).orElseThrow().fire().invoke(stack.getItem(),shooter.level(),shooter,stack,target);
            if (ammo(stack)!=Integer.MAX_VALUE) stack.set(ammoComponent(stack),Math.max(0,ammo(stack)-1));
            Object stats=stats(stack);
            Object sound=stats.getClass().getMethod("fireSoundEvent").invoke(stats);
            if (sound instanceof Optional<?> optional && optional.orElse(null) instanceof net.minecraft.sounds.SoundEvent event)
                shooter.level().playSound(null,shooter,event,net.minecraft.sounds.SoundSource.HOSTILE,2F,1F);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            com.ancientkira.mca.MCA.LOGGER.warn("JEG could not fire for MCA villager {}",shooter.getUUID(),e);
            return false;
        }
    }
}
