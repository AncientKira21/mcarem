package com.ancientkira.mca.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.Level;
import com.ancientkira.mca.server.GolemAllies;

/** Separate identity keeps vanilla golems and their renderer available. */
public final class GolemEntityMCA extends IronGolem {
    public GolemEntityMCA(EntityType<? extends IronGolem> type, Level level) { this(type, level, false); }
    public GolemEntityMCA(EntityType<? extends IronGolem> type, Level level, boolean raider) {
        super(type, level);
        if (raider) addTag(GolemAllies.RAIDER);
    }
}
