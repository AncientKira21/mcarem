package com.ancientkira.mca.client.render;

import com.ancientkira.mca.client.model.VillagerEntityModelMCA;
import com.ancientkira.mca.client.model.ZombieVillagerEntityModelMCA;
import com.ancientkira.mca.client.render.layer.ClothingLayer;
import com.ancientkira.mca.client.render.layer.FaceLayer;
import com.ancientkira.mca.client.render.layer.HairLayer;
import com.ancientkira.mca.client.render.layer.SkinLayer;
import com.ancientkira.mca.entity.ZombieVillagerEntityMCA;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class ZombieVillagerEntityMCARenderer extends VillagerLikeEntityMCARenderer<ZombieVillagerEntityMCA> {
    public ZombieVillagerEntityMCARenderer(EntityRendererProvider.Context ctx) {
        super(ctx, createAnimationModel(ctx).hideWears());

        layers.add(0, new SkinLayer<>(this, createVisibleModel(VillagerEntityModelMCA.bodyData(CubeDeformation.NONE)).hideWears()));
        addLayer(new FaceLayer<>(this, createVisibleModel(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.01F))).hideWears(), "normal", false));
        addLayer(new ClothingLayer<>(this, createVisibleModel(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.075F))), "zombie"));
        addLayer(new HairLayer<>(this, createVisibleModel(VillagerEntityModelMCA.hairData(new CubeDeformation(0.1F)))));
        addLayer(new FaceLayer<>(this, createVisibleModel(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.14F))).hideWears(), "normal", true));
        addLayer(new com.ancientkira.mca.client.render.layer.AppearanceLayer<>(this, createVisibleModel(VillagerEntityModelMCA.bodyData(CubeDeformation.NONE)), -1));
        for(int slot=0;slot<4;slot++) {
            var accessoryMesh=VillagerEntityModelMCA.bodyData(new CubeDeformation(0.15F+slot*0.025F));
            if(slot==3) accessoryMesh.getRoot().getChild("body").addOrReplaceChild("backpack",
                    net.minecraft.client.model.geom.builders.CubeListBuilder.create().texOffs(16,16).addBox(-4,1,2,8,10,3),net.minecraft.client.model.geom.PartPose.ZERO);
            addLayer(new com.ancientkira.mca.client.render.layer.AppearanceLayer<>(this,createVisibleModel(accessoryMesh),slot));
        }
    }

    private static VillagerEntityModelMCA createAnimationModel(EntityRendererProvider.Context ctx) {
        return new ZombieVillagerEntityModelMCA(ctx.bakeLayer(ModelLayers.ZOMBIE));
    }

    private static VillagerEntityModelMCA createVisibleModel(MeshDefinition data) {
        VillagerEntityModelMCA model = new ZombieVillagerEntityModelMCA(LayerDefinition.create(data, 64, 64).bakeRoot());
        model.receiveDeferredAnimationPose();
        return model;
    }

    @Override
    public void extractRenderState(ZombieVillagerEntityMCA entity, VillagerRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.isAggressive = entity.isAggressive() || entity.getTarget() != null;
        state.isConverting = entity.isConverting() || entity.isUnderWaterConverting();
    }

    @Override
    protected boolean isShaking(VillagerRenderState state) {
        return state.isConverting;
    }
}
