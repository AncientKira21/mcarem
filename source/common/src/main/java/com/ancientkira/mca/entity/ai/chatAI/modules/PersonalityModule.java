package com.ancientkira.mca.entity.ai.chatAI.modules;

import com.ancientkira.mca.MCA;
import com.ancientkira.mca.registry.ProfessionsMCA;
import com.ancientkira.mca.server.RaiderSocial;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.relationship.AgeState;
import com.ancientkira.mca.entity.ai.relationship.Personality;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.List;

import static com.ancientkira.mca.entity.ai.chatAI.OpenAIChatAI.translate;

public class PersonalityModule {
    public static void apply(List<String> input, VillagerEntityMCA villager, ServerPlayer player) {
        boolean raider = ProfessionsMCA.isRaider(villager.getProfession());
        String role = raider ? "raider" : "villager";
        input.add("This is a conversation with a " + translate(villager.getGenetics().getGender().name())
                + " Minecraft " + role + " named $villager and the Player named $player. ");
        if (raider) {
            String job = villager.getProfession() == ProfessionsMCA.PILLAGER ? "pillager"
                    : villager.getProfession() == ProfessionsMCA.VINDICATOR ? "vindicator"
                    : villager.getProfession() == ProfessionsMCA.EVOKER ? "evoker" : "witch";
            input.add("Current character role: $villager is a " + job + " and belongs to the raiders. "
                    + "Speak as this raider, acknowledging your raider allegiance and profession rather than claiming to be an ordinary peaceful villager. "
                    + "You still have your own name, personality, feelings, and family relationships. "
                    + "Friendship does not automatically redeem you. You remain a raider until your profession actually changes. "
                    + "This current role takes precedence over outdated descriptions in earlier conversation. ");
            if (RaiderSocial.isFriendly(villager, player)) {
                input.add("You are friendly toward $player, while still belonging to the raiders. ");
            } else if (RaiderSocial.isAngryAt(villager, player)) {
                input.add("You are currently hostile toward $player. Respond with distrust or antagonism appropriate to your personality. ");
            } else {
                input.add("You have no current personal hostility toward $player; being a raider does not mean attacking everyone you meet. ");
            }
        } else {
            input.add("Your current role is a villager, not an active raider, even if earlier conversation described you as one. ");
        }

        Personality personality = villager.getVillagerBrain().getPersonality();
        String personalityName = personality.getId().getNamespace().equals(MCA.MOD_ID)
                ? personality.getId().getPath()
                : personality.getId().toString();
        input.add("$villager is " + translate(personalityName) + " and "
                + translate(villager.getVillagerBrain().getMood().getName()) + ". ");
        if (villager.getAgeState() == AgeState.BABY) {
            input.add("$villager is a baby. ");
        }
        if (villager.getAgeState() == AgeState.TODDLER) {
            input.add("$villager is a toddler. ");
        }
        if (villager.getAgeState() == AgeState.CHILD) {
            input.add("$villager is a child. ");
        }
        if (villager.getAgeState() == AgeState.TEEN) {
            input.add("$villager is a teen. ");
        } else if (!villager.getVillagerData().profession().is(VillagerProfession.NONE)) {
            input.add("$villager is a " + translate(villager.getProfession().name().getString()) + ". ");
        }
    }
}
