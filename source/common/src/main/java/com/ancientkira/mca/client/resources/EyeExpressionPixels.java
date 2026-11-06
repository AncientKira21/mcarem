package com.ancientkira.mca.client.resources;

/** Runtime expression masks in standard player-head UVs, scaled for HD eye textures. */
public final class EyeExpressionPixels {
    private EyeExpressionPixels() {}
    public static int scaredPixel(int width,int x,int y,int minX,int minY,int maxX,int maxY,boolean leftEye) {
        int scale=Math.max(1,width/64);
        int top=maxY-Math.max(maxY-minY+1,2*scale)+1;
        if(x<minX || x>maxX || y<top || y>maxY)return 0;
        boolean iris=leftEye ? x>maxX-scale : x<minX+scale;
        return iris && y>=top+(maxY-top+1)/2 ? 0xff808080 : 0xffffffff;
    }
    public static int pixel(String expression,int width,int x,int y) {
        int scale=width/64;
        if(scale<1)return 0;
        x/=scale;y/=scale;
        if(y<10 || y>13)return 0;
        int center=x<=10?9:14;
        if(x<8 || x>15 || x==11 || x==12)return 0;
        if(expression.equals("hurt")) {
            return y>=11 && Math.abs(x-center)==Math.abs(y-12) ? 0xff000000 : 0;
        }
        if(expression.equals("scared")) {
            if(y==10)return 0xff000000;
            if(x==center && y>=12)return 0xff808080;
            return 0xffffffff;
        }
        return 0;
    }
}
