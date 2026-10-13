package com.ancientkira.mca.item;

/** Retains the existing Pillager egg class while sharing profession-aware spawning. */
public final class PillagerSpawnEggItem extends ProfessionSpawnEggItem {
    public PillagerSpawnEggItem(Properties properties) {
        super(properties, "mca:pillager");
    }
}
