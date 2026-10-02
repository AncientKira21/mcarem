package com.ancientkira.mca.neoforge;

import com.ancientkira.mca.PlatformHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

public class NeoforgePlatformHelper extends PlatformHelper {
    @Override
    protected boolean isModLoadedUncached(String namespace) {
        return ModList.get().isLoaded(namespace);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLEnvironment.isProduction();
    }
}
