package com.ancientkira.mca;

import com.ancientkira.mca.entity.VillagerLike;
import com.ancientkira.mca.network.ClientHandler;
import com.ancientkira.mca.network.ClientHandlerImpl;

import java.util.Optional;
import java.util.UUID;

public abstract class ClientProxyAbstractImpl extends ClientProxy.Impl {
    private ClientHandler networkHandler;

    @Override
    public final synchronized ClientHandler getNetworkHandler() {
        if (networkHandler == null) {
            networkHandler = new ClientHandlerImpl();
        }
        return networkHandler;
    }

    @Override
    public final Optional<VillagerLike<?>> getPlayerData(UUID uuid) {
        return MCAClient.getPlayerData(uuid);
    }
}
