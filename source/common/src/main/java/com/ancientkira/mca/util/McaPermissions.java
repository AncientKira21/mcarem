package com.ancientkira.mca.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

public final class McaPermissions {
    private McaPermissions() {}
    public static boolean lanCheats(net.minecraft.server.MinecraftServer server) {
        try {
            return (boolean) server.getClass().getMethod("getGuestCommandAccess").invoke(server);
        } catch (ReflectiveOperationException ignored) {
            try {
                var players = server.getPlayerList();
                return (boolean) players.getClass().getMethod("isAllowCommandsForAllPlayers").invoke(players);
            } catch (ReflectiveOperationException unavailable) { return false; }
        }
    }
    public static boolean cheats(ServerPlayer player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                && (!player.level().getServer().isSingleplayer() || player.level().getServer().getWorldData().isAllowCommands()
                    || lanCheats(player.level().getServer()));
    }
}
