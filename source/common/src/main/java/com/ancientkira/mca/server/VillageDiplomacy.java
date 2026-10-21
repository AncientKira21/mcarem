package com.ancientkira.mca.server;

import com.ancientkira.mca.resources.Rank;
import com.ancientkira.mca.resources.Tasks;
import com.ancientkira.mca.server.world.data.*;
import com.ancientkira.mca.util.WorldUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Automatic wars wait for the affected monarchs; any refusal cancels the proposal. */
public final class VillageDiplomacy extends SavedData {
    private final ServerLevel world;
    private final Map<UUID, Proposal> proposals = new HashMap<>();
    private VillageDiplomacy(ServerLevel world) { this.world = world; }
    private VillageDiplomacy(ServerLevel world, CompoundTag data) {
        this(world);
        for (var value : data.getListOrEmpty("Proposals")) if (value instanceof CompoundTag tag) {
            tag.read("Id", UUIDUtil.CODEC).ifPresent(id -> {
                Proposal proposal = new Proposal(tag.getIntOr("First", -1), tag.getIntOr("Second", -1), tag.getLongOr("Expires", 0));
                read(tag, "Required", proposal.required); read(tag, "Approved", proposal.approved);
                proposals.put(id, proposal);
            });
        }
    }
    public static VillageDiplomacy get(ServerLevel world) {
        return WorldUtils.loadData(world, (tag, registries) -> new VillageDiplomacy(world, tag), VillageDiplomacy::new, "mca_diplomacy");
    }
    private static void read(CompoundTag data, String key, Set<UUID> ids) {
        data.getListOrEmpty(key).forEach(value -> value.asString().ifPresent(text -> { try { ids.add(UUID.fromString(text)); } catch (IllegalArgumentException ignored) { } }));
    }
    private static ListTag write(Set<UUID> ids) { ListTag list = new ListTag(); ids.forEach(id -> list.add(StringTag.valueOf(id.toString()))); return list; }
    public CompoundTag save(CompoundTag data, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        proposals.forEach((id, proposal) -> {
            CompoundTag tag = new CompoundTag(); tag.store("Id", UUIDUtil.CODEC, id);
            tag.putInt("First", proposal.first); tag.putInt("Second", proposal.second); tag.putLong("Expires", proposal.expires);
            tag.put("Required", write(proposal.required)); tag.put("Approved", write(proposal.approved)); list.add(tag);
        });
        data.put("Proposals", list); return data;
    }
    public boolean hasPending(int village) { return proposals.values().stream().anyMatch(p -> p.first == village || p.second == village); }
    public boolean requestWar(Village first, Village second) {
        var wars = VillageWarfare.get(world);
        if (wars.isFriendly(first.getId(), second.getId()) || hasPending(first.getId()) || hasPending(second.getId())) return false;
        var monarchs = world.getServer().getPlayerList().getPlayers().stream().filter(player ->
                Tasks.getRank(first, player) == Rank.MONARCH || Tasks.getRank(second, player) == Rank.MONARCH).toList();
        if (monarchs.isEmpty()) return begin(first, second);
        UUID id = UUID.randomUUID(); Proposal proposal = new Proposal(first.getId(), second.getId(), world.getGameTime() + 24000);
        monarchs.forEach(player -> proposal.required.add(player.getUUID()));
        proposals.put(id, proposal); setDirty();
        for (ServerPlayer monarch : monarchs) {
            PlayerSaveData.get(monarch).sendLetter(Component.translatable("mca.war.approval_letter", first.getName(), second.getName()),
                    answer(id, true).copy().append("   ").append(answer(id, false)));
            monarch.sendSystemMessage(Component.translatable("mca.war.approval_letter", first.getName(), second.getName())
                    .append(" ").append(answer(id, true)).append("   ").append(answer(id, false)));
        }
        return true;
    }
    private static Component answer(UUID id, boolean yes) {
        return Component.translatable(yes ? "gui.yes" : "gui.no").withStyle(style -> style
                .withColor(yes ? ChatFormatting.GREEN : ChatFormatting.RED).withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand("/mca warDecision " + (yes ? "yes " : "no ") + id)));
    }
    private boolean begin(Village first, Village second) {
        if (!VillageWarfare.get(world).declare(first, second.getId(), 3)) return false;
        world.getServer().getPlayerList().getPlayers().stream().filter(player ->
                Tasks.getRank(first, player) == Rank.MONARCH || Tasks.getRank(second, player) == Rank.MONARCH
                || player.level() == world && (player.blockPosition().distSqr(first.getCenter()) <= 512 * 512
                || player.blockPosition().distSqr(second.getCenter()) <= 512 * 512))
                .forEach(player -> PlayerSaveData.get(player).sendLetter(Component.translatable("mca.letter.war_warning", first.getName(), second.getName())));
        return true;
    }
    public static int respond(ServerPlayer player, UUID id, boolean accept) {
        for (var world : player.level().getServer().getAllLevels()) {
            var diplomacy = get(world); Proposal proposal = diplomacy.proposals.get(id);
            if (proposal == null) continue;
            var manager = VillageManager.get(world);
            Village first = manager.getOrEmpty(proposal.first).orElse(null), second = manager.getOrEmpty(proposal.second).orElse(null);
            if (!proposal.required.contains(player.getUUID()) || first == null || second == null
                    || world.getGameTime() >= proposal.expires
                    || (Tasks.getRank(first, player) != Rank.MONARCH && Tasks.getRank(second, player) != Rank.MONARCH)) {
                player.sendSystemMessage(Component.translatable("mca.war.approval_invalid")); return 0;
            }
            if (!accept) {
                diplomacy.proposals.remove(id); diplomacy.setDirty();
                diplomacy.record(first, second, Component.translatable("mca.war.declined", first.getName(), second.getName())); return 1;
            }
            if (VillageWarfare.get(world).isFriendly(first.getId(), second.getId())) {
                diplomacy.proposals.remove(id); diplomacy.setDirty();
                player.sendSystemMessage(Component.translatable("mca.war.approval_invalid")); return 0;
            }
            proposal.approved.add(player.getUUID()); diplomacy.setDirty();
            if (!proposal.approved.containsAll(proposal.required)) {
                player.sendSystemMessage(Component.translatable("mca.war.approval_waiting")); return 1;
            }
            diplomacy.proposals.remove(id); diplomacy.setDirty();
            if (!diplomacy.begin(first, second)) { player.sendSystemMessage(Component.translatable("mca.war.approval_invalid")); return 0; }
            return 1;
        }
        player.sendSystemMessage(Component.translatable("mca.war.approval_invalid")); return 0;
    }
    private void record(Village first, Village second, Component message) {
        first.getCivilRegistry().ifPresent(registry -> registry.addText(message));
        second.getCivilRegistry().ifPresent(registry -> registry.addText(message));
        world.getServer().getPlayerList().broadcastSystemMessage(message, false);
    }
    public void tick() {
        boolean changed = proposals.values().removeIf(p -> world.getGameTime() >= p.expires
                || VillageManager.get(world).getOrEmpty(p.first).isEmpty() || VillageManager.get(world).getOrEmpty(p.second).isEmpty()
                || VillageWarfare.get(world).isFriendly(p.first, p.second));
        if (changed) setDirty();
    }
    private static final class Proposal {
        final int first, second; final long expires;
        final Set<UUID> required = new HashSet<>(), approved = new HashSet<>();
        Proposal(int first, int second, long expires) { this.first = first; this.second = second; this.expires = expires; }
    }
}
