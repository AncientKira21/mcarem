package com.ancientkira.mca.client.gui.immersive_library;

import java.util.function.IntBinaryOperator;

/** Classifies visible skin pixels by the model regions they can cover. */
public final class AccessoryTextureCoverage {
    public static final int HEAD = 1;
    public static final int UPPER_HEAD = 2;
    public static final int FACE = 4;

    private AccessoryTextureCoverage() {}

    public static int classify(int width, int height, IntBinaryOperator alpha) {
        if (width < 64 || width != height || width % 64 != 0) return 0;
        int scale = width / 64;
        int categories = HEAD | UPPER_HEAD | FACE;
        boolean visible = false;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (alpha.applyAsInt(x, y) == 0) continue;
                visible = true;
                int u = x / scale;
                int v = y / scale;
                boolean head = v >= 8 && v < 16
                        || v < 8 && (u >= 8 && u < 24 || u >= 40 && u < 56);
                if (!head) return 0;
                if (v < 8 || v >= 14) categories &= ~UPPER_HEAD;
                if (v < 8 || !(u >= 8 && u < 16 || u >= 40 && u < 48)) categories &= ~FACE;
            }
        }
        return visible ? categories : 0;
    }
}
