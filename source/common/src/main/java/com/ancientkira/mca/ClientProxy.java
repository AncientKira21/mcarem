package com.ancientkira.mca;

import com.ancientkira.mca.entity.VillagerLike;
import com.ancientkira.mca.network.ClientHandler;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public class ClientProxy {
    private static Impl INSTANCE = new Impl();

    @Nullable
    public static Player getClientPlayer() {
        return INSTANCE.getClientPlayer();
    }

    public static ClientHandler getNetworkHandler() {
        return INSTANCE.getNetworkHandler();
    }

    public static Optional<VillagerLike<?>> getPlayerData(UUID uuid) {
        return INSTANCE.getPlayerData(uuid);
    }

    public static void playVillagerGreeting(com.ancientkira.mca.entity.VillagerEntityMCA villager) {
        INSTANCE.playVillagerGreeting(villager);
    }

    public static void playVillagerHurt(com.ancientkira.mca.entity.VillagerEntityMCA villager) {
        INSTANCE.playVillagerHurt(villager);
    }

    public static void playVillagerScream(com.ancientkira.mca.entity.VillagerEntityMCA villager) {
        INSTANCE.playVillagerScream(villager);
    }

    public static class Impl {
        protected Impl() {
            INSTANCE = this;
        }

        public void playVillagerScream(com.ancientkira.mca.entity.VillagerEntityMCA villager) { }

        public void playVillagerHurt(com.ancientkira.mca.entity.VillagerEntityMCA villager) { }

        public void playVillagerGreeting(com.ancientkira.mca.entity.VillagerEntityMCA villager) { }

        public Player getClientPlayer() {
            return null;
        }

        public ClientHandler getNetworkHandler() {
            return null;
        }

        public Optional<VillagerLike<?>> getPlayerData(UUID uuid) {
            return Optional.empty();
        }
    }
}
