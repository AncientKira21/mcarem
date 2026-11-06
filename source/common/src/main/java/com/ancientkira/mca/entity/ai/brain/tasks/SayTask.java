package com.ancientkira.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;

import java.util.function.Predicate;

public class SayTask extends Behavior<VillagerEntityMCA> {
    private final String phrase;
    private final int interval;
    private final Predicate<VillagerEntityMCA> condition;

    private long lastShout;

    public SayTask(String phrase) {
        this(phrase, 0, (v) -> true);
    }

    public SayTask(String phrase, int interval, Predicate<VillagerEntityMCA> condition) {
        super(ImmutableMap.of());
        this.phrase = phrase;
        this.interval = interval;
        this.condition = condition;
    }

    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA entity) {
        return !com.ancientkira.mca.server.VillageMusic.attending(entity) && entity.level().getGameTime() - lastShout > interval && condition.test(entity);
    }

    protected void start(ServerLevel world, VillagerEntityMCA entity, long time) {
        if(phrase.equals("villager.attack"))com.ancientkira.mca.server.TacticalCommands.shout(entity);
        else entity.sendChatToAllAround(phrase);
        lastShout = entity.level().getGameTime();
    }
}
