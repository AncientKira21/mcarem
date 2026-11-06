package com.ancientkira.mca.client.gui.immersive_library;

import java.util.function.IntBinaryOperator;

/** A classic skin supplies every base-model face; transparent clothing overlays do not qualify. */
public final class ClassicSkinCoverage {
    private ClassicSkinCoverage() {}
    private static final int[][] FACES = {
        {8,0,24,8},{0,8,32,16},
        {20,16,36,20},{16,20,40,32},
        {4,16,12,20},{0,20,16,32},
        {20,48,28,52},{16,52,32,64}
    };
    public static boolean qualifies(int width,int height,IntBinaryOperator alpha) {
        if(width<64 || width!=height || width%64!=0)return false;
        int scale=width/64;
        if(!covers(FACES,scale,alpha))return false;
        return covers(new int[][]{{44,16,52,20},{40,20,56,32},{36,48,44,52},{32,52,48,64}},scale,alpha)
                || covers(new int[][]{{44,16,50,20},{40,20,54,32},{36,48,42,52},{32,52,46,64}},scale,alpha);
    }
    private static boolean covers(int[][] faces,int scale,IntBinaryOperator alpha) {
        for(int[] face:faces) for(int y=face[1]*scale;y<face[3]*scale;y++)
            for(int x=face[0]*scale;x<face[2]*scale;x++) if(alpha.applyAsInt(x,y)<250)return false;
        return true;
    }
}
