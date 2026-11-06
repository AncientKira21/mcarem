package com.ancientkira.mca.entity;
import com.ancientkira.mca.Config;
import com.ancientkira.mca.registry.ProfessionsMCA;
import net.minecraft.world.entity.LivingEntity;
/** Shared eligibility and random rolls for zombie and sickness exposure. */
public final class InfectionRules {
    private InfectionRules() {}
    public static boolean roll(LivingEntity victim) {
        int level=1;
        if(victim instanceof VillagerLike<?> person) {
            if(person.getVillagerData().profession().value()==ProfessionsMCA.GUARD)return false;
            level=person.getVillagerData().level();
        }
        Config config=Config.getInstance();
        return config.enableInfection && victim.getRandom().nextFloat()<config.zombieBiteInfectionChance
                && victim.getRandom().nextFloat()>(level-1)*config.infectionChanceDecreasePerLevel;
    }
}
