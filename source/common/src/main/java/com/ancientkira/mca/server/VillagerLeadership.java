package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.relationship.AgeState;
import com.ancientkira.mca.registry.ProfessionsMCA;
import com.ancientkira.mca.resources.Rank;
import com.ancientkira.mca.resources.Tasks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.core.registries.BuiltInRegistries;

public final class VillagerLeadership {
    private VillagerLeadership() { }
    public static void elect(VillagerEntityMCA candidate, ServerPlayer player, String role) {
        if (!role.equals("mayor") && !role.equals("ruler")) return;
        var village = candidate.getResidency().getHomeVillage();
        if (village.isEmpty() || candidate.getAgeState() != AgeState.ADULT
                || !candidate.getVillagerData().profession().is(VillagerProfession.NONE)) return;
        Rank required = role.equals("ruler") ? Rank.MONARCH : Rank.MAYOR;
        if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                && !Tasks.getRank(village.get(), player).isAtLeast(required)) return;
        var profession = role.equals("ruler") ? ProfessionsMCA.RULER : ProfessionsMCA.MAYOR;
        // A village elects one incumbent for each office; replacing one frees their old job.
        for (var person : village.get().getResidents(player.level())) {
            if (person.getProfession() == profession)
                person.setProfession(BuiltInRegistries.VILLAGER_PROFESSION.getValueOrThrow(VillagerProfession.NONE));
        }
        candidate.getVillagerBrain().abandonJob();
        candidate.setProfession(profession);
        Component message = Component.translatable("mca.leader.elected", candidate.getName().getString(),
                profession.name(), village.get().getName());
        village.get().getCivilRegistry().ifPresent(registry -> registry.addText(message));
        player.level().getServer().getPlayerList().broadcastSystemMessage(message, false);
    }
}
