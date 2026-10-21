package com.ancientkira.mca.entity.interaction;

import com.ancientkira.mca.entity.VillagerLike;
import com.ancientkira.mca.network.Network;
import com.ancientkira.mca.network.s2c.OpenGuiRequest;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public abstract class EntityCommandHandler<T extends Entity & VillagerLike<?>> {
    protected final T entity;
    @Nullable
    protected Player interactingPlayer;

    public EntityCommandHandler(T entity) {
        this.entity = entity;
    }

    public Optional<Player> getInteractingPlayer() {
        return Optional.ofNullable(interactingPlayer).filter(player -> player.containerMenu != null
                && player.isAlive() && !player.isRemoved() && player.level() == entity.level()
                && player.distanceToSqr(entity) <= 64);
    }

    public void stopInteracting() {
        stopInteracting(true);
    }

    public void stopInteracting(boolean closeContainer) {
        if (entity instanceof com.ancientkira.mca.entity.VillagerEntityMCA person && interactingPlayer != null) {
            com.ancientkira.mca.server.RaiderSocial.onMenuClosed(person, interactingPlayer);
        }
        if (!entity.level().isClientSide()) {
            if (closeContainer && interactingPlayer instanceof ServerPlayer serverPlayer) {
                Network.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.CLOSE, entity), serverPlayer);
                if (serverPlayer.containerMenu != serverPlayer.inventoryMenu) {
                    serverPlayer.closeContainer();
                }
            }
        }
        interactingPlayer = null;
    }

    public InteractionResult interactAt(Player player, Vec3 pos, @NotNull InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            Network.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.INTERACT, entity), serverPlayer);
        }
        interactingPlayer = player;
        if (entity instanceof com.ancientkira.mca.entity.VillagerEntityMCA person) {
            com.ancientkira.mca.server.RaiderSocial.onMenuOpened(person, player);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Called on the server to respond to button events.
     */
    public boolean handle(ServerPlayer player, String command) {
        return false;
    }
}

