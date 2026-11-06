package com.ancientkira.mca.client.render;

import com.ancientkira.mca.MCA;
import com.ancientkira.mca.client.gui.immersive_library.SkinCache;
import com.ancientkira.mca.client.resources.ClosedEyePixels;
import com.ancientkira.mca.util.ImmersiveLibraryIds;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.util.HashMap;
import java.util.Map;

/** Client-only derived textures; the saved or uploaded skin is never changed. */
public final class CorpseEyeTextures {
    private static final Map<Identifier,Identifier> CACHE=new HashMap<>();
    private CorpseEyeTextures() { }
    public static Identifier closed(String skin, Identifier original) {
        if(CACHE.containsKey(original)) return CACHE.get(original);
        var client=Minecraft.getInstance();
        NativeImage image=null;
        try {
            var content=ImmersiveLibraryIds.contentId(skin);
            if(content.isPresent()) {
                var source=SkinCache.getImage(content.getAsInt());
                if(source.isEmpty())return original;
                image=new NativeImage(source.get().getWidth(),source.get().getHeight(),true);
                image.copyFrom(source.get());
            } else {
                try(var stream=client.getResourceManager().open(original)) { image=NativeImage.read(stream); }
            }
            int width=image.getWidth(),height=image.getHeight();
            int[] pixels=new int[width*height];
            for(int y=0;y<height;y++)for(int x=0;x<width;x++)pixels[y*width+x]=image.getPixel(x,y);
            ClosedEyePixels.apply(pixels,width,height);
            for(int y=0;y<height;y++)for(int x=0;x<width;x++)image.setPixel(x,y,pixels[y*width+x]);
            Identifier id=MCA.locate("dynamic/corpse_eyes/"+java.util.UUID.nameUUIDFromBytes(original.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            client.getTextureManager().register(id,new DynamicTexture(id::toString,image));
            image=null; CACHE.put(original,id); return id;
        } catch(java.io.IOException|RuntimeException error) {
            MCA.LOGGER.warn("Could not close corpse skin eyes for {}", original, error);
            return original;
        } finally {if(image!=null)image.close();}
    }
    public static void clear() {
        var textures=Minecraft.getInstance().getTextureManager();
        CACHE.values().forEach(textures::release);CACHE.clear();
    }
}
