package com.ancientkira.mca.client.resources;

/** Closes painted eyes in the face UVs only; preserves the rest of a classic skin. */
public final class ClosedEyePixels {
    private ClosedEyePixels() { }
    public static void apply(int[] pixels, int width, int height) {
        if (width < 64 || width % 64 != 0 || height < width / 2 || pixels.length != width * height) return;
        int scale = width / 64;
        for (int outer : new int[]{0,32}) for (int side=0; side<2; side++) {
            int startX = (outer + (side==0 ? 8 : 12))*scale;
            int endX = startX+4*scale;
            int minX=endX, maxX=-1, minY=14*scale, maxY=-1;
            for (int y=11*scale; y<14*scale; y++) for (int x=startX; x<endX; x++) {
                int color=pixels[y*width+x], r=(color>>>16)&255, g=(color>>>8)&255, blue=color&255;
                if ((color>>>24)>=240 && Math.min(r,Math.min(g,blue))>=190
                        && Math.max(r,Math.max(g,blue))-Math.min(r,Math.min(g,blue))<=24) {
                    minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
                }
            }
            if(maxX<0) continue;
            // Include the adjacent iris, which need not contain any white pixels.
            if(side==0) maxX=Math.min(endX-1,maxX+scale); else minX=Math.max(startX,minX-scale);
            int lineY=maxY;
            for(int x=minX;x<=maxX;x++) {
                int cheek=pixels[14*scale*width+x];
                if((cheek>>>24)<240) cheek=pixels[14*scale*width+x-outer*scale];
                if((cheek>>>24)<240) continue;
                for(int y=minY;y<=maxY;y++) pixels[y*width+x]=cheek;
                int r=((cheek>>>16)&255)/3, g=((cheek>>>8)&255)/3, blue=(cheek&255)/3;
                for(int y=Math.max(minY,lineY-scale+1);y<=lineY;y++) pixels[y*width+x]=0xff000000|(r<<16)|(g<<8)|blue;
            }
        }
    }
}
