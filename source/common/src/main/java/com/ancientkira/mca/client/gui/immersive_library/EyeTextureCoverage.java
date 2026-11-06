package com.ancientkira.mca.client.gui.immersive_library;
import java.util.function.IntBinaryOperator;
/** Eye textures have visible pixels only on the base head's front face. */
public final class EyeTextureCoverage {
    private EyeTextureCoverage() { }
    public static boolean qualifies(int width,int height,IntBinaryOperator alpha) {
        if(width<64 || width!=height || width%64!=0)return false;
        int scale=width/64;boolean visible=false;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)if(alpha.applyAsInt(x,y)>0) {
            if(x<8*scale || x>=16*scale || y<8*scale || y>=16*scale)return false;
            visible=true;
        }
        return visible;
    }
}
