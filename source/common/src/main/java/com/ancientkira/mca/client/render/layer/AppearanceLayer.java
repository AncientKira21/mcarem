package com.ancientkira.mca.client.render.layer;

import com.ancientkira.mca.client.render.VillagerVisuals;
import com.ancientkira.mca.client.gui.immersive_library.SkinCache;
import com.ancientkira.mca.util.ImmersiveLibraryIds;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** Independent PNG layers for complete player skins and the four accessory slots. */
public final class AppearanceLayer<S extends HumanoidRenderState,M extends HumanoidModel<S>> extends SkinLayer<S,M> {
    private final int slot;
    public AppearanceLayer(RenderLayerParent<S,M> renderer,M model,int slot) {super(renderer,model);this.slot=slot;}
    @Override public void submit(com.mojang.blaze3d.vertex.PoseStack poseStack,net.minecraft.client.renderer.SubmitNodeCollector collector,int light,S state,float yRot,float xRot) {
        var visuals=VillagerVisuals.require(state);
        if((slot<0?visuals.classicSkin():visuals.accessories().get(slot)).isBlank())return;
        if(slot<0 && !classicReady(visuals.classicSkin()))return;
        super.submit(poseStack,collector,light,state,yRot,xRot);
    }
    public static boolean classicReady(String id) {
        if(id.isBlank())return false;
        var content=ImmersiveLibraryIds.contentId(id);
        if(content.isEmpty())return true;
        SkinCache.getTextureIdentifier(content.getAsInt());
        return SkinCache.isCompleteSkin(content.getAsInt());
    }
    @Override protected void prepareModel(S state) {
        if(slot<0){ super.prepareModel(state);setAllVisible(model,true);return; }
        setAllVisible(model,false);
        if(slot==3)model.body.visible=true;
        else {model.head.visible=true;model.hat.visible=true;}
    }
    @Override public int getColor(S state,float tickDelta) {
        var visuals=VillagerVisuals.require(state);
        return slot<0 && visuals.sicknessProgress()>0 ? com.ancientkira.mca.client.resources.ColorPalette.applyPurpleShift(0xFFFFFFFF,visuals.sicknessProgress()) : 0xFFFFFFFF;
    }
    @Override public Identifier getSkin(S state) {
        var visuals=VillagerVisuals.require(state);
        String id=slot<0?visuals.classicSkin():visuals.accessories().get(slot);
        if(id.isBlank())return null;
        var content=ImmersiveLibraryIds.contentId(id);
        Identifier texture=content.isPresent()?SkinCache.getTextureIdentifier(content.getAsInt()):cached(id,Identifier::parse);
        if(slot<0 && state instanceof com.ancientkira.mca.client.render.VillagerRenderState corpse && corpse.mcaCorpse)
            return com.ancientkira.mca.client.render.CorpseEyeTextures.closed(id,texture);
        return texture;
    }
}
