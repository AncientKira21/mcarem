package com.ancientkira.mca.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class McaRegistryCompat {
    private McaRegistryCompat() {
    }

    @SuppressWarnings("unchecked")
    public static <T extends Entity> EntityType<T> entityType(String id) {
        return (EntityType<T>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(id));
    }

    public static Block block(String id) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id));
    }

    public static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
    }
}
