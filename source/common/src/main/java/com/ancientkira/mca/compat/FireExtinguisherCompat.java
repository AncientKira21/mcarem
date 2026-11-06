package com.ancientkira.mca.compat;

import com.ancientkira.mca.MCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Optional integration: no Fire Extinguisher classes are linked when the mod is absent. */
public final class FireExtinguisherCompat {
    private static boolean checked;
    private static Method extinguish;
    private static Field radiusX, radiusY, radiusZ, cooldown, refillEnabled;
    private FireExtinguisherCompat() {}

    public static boolean available() {
        if (!checked) {
            checked = true;
            if (!BuiltInRegistries.ITEM.containsKey(Identifier.parse("fire_extinguisher:fire_extinguisher"))) return false;
            try {
                Class<?> detection = Class.forName("de.markusbordihn.fireextinguisher.utils.FireDetection");
                extinguish = detection.getMethod("extinguishWithin", Level.class, BlockPos.class, int.class, int.class, int.class);
                Class<?> config = Class.forName("de.markusbordihn.fireextinguisher.config.FireExtinguisherConfig");
                radiusX = config.getField("fireExtinguisherRadiusX");
                radiusY = config.getField("fireExtinguisherRadiusY");
                radiusZ = config.getField("fireExtinguisherRadiusZ");
                cooldown = config.getField("fireExtinguisherCooldownTicks");
                refillEnabled = config.getField("fireHydrantRefillEnabled");
            } catch (ReflectiveOperationException | LinkageError error) {
                extinguish = null;
                MCA.LOGGER.warn("Fire Extinguisher integration unavailable; firefighters will use water", error);
            }
        }
        return extinguish != null;
    }

    public static ItemStack equipment() {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse("fire_extinguisher:fire_extinguisher")).getDefaultInstance();
    }
    public static boolean isExtinguisher(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("fire_extinguisher")
                && (id.getPath().equals("fire_extinguisher") || id.getPath().equals("fire_extinguisher_copper"));
    }
    public static boolean empty(ItemStack stack) {
        return stack.isEmpty() || stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage();
    }
    public static boolean hydrant(ServerLevel world, BlockPos pos) {
        return world.hasChunkAt(pos) && BuiltInRegistries.BLOCK.getKey(world.getBlockState(pos).getBlock())
                .equals(Identifier.parse("fire_extinguisher:fire_hydrant"));
    }
    public static boolean canRefill() {
        try { return available() && refillEnabled.getBoolean(null); }
        catch (IllegalAccessException error) { return false; }
    }
    public static int cooldown() {
        try { return Math.max(1, cooldown.getInt(null)); }
        catch (IllegalAccessException error) { return 20; }
    }
    public static int spray(ServerLevel world, BlockPos fire) {
        if (!available()) return 0;
        try {
            return (int) extinguish.invoke(null, world, fire, radiusX.getInt(null), radiusY.getInt(null), radiusZ.getInt(null));
        } catch (ReflectiveOperationException | LinkageError error) {
            extinguish = null; // Disable a broken optional API rather than logging every tick.
            MCA.LOGGER.warn("Could not spray firefighter extinguisher; falling back to water", error);
            return 0;
        }
    }
}
