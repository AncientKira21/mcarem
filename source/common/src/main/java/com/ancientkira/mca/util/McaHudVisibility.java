package com.ancientkira.mca.util;

import com.ancientkira.mca.mixin.client.MixinHudAccessor;

public final class McaHudVisibility {
    private McaHudVisibility() {
    }

    public static boolean isHidden(Object hud) {
        return ((MixinHudAccessor) hud).mca$isHidden();
    }

    public static void setHidden(Object hud, boolean hidden) {
        ((MixinHudAccessor) hud).mca$setHidden(hidden);
    }
}
