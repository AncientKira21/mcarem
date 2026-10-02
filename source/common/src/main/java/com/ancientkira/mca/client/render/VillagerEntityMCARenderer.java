package com.ancientkira.mca.client.render;

import com.ancientkira.mca.client.model.VillagerEntityModelMCA;
import com.ancientkira.mca.client.render.layer.ClothingLayer;
import com.ancientkira.mca.client.render.layer.FaceLayer;
import com.ancientkira.mca.client.render.layer.HairLayer;
import com.ancientkira.mca.client.render.layer.SkinLayer;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class VillagerEntityMCARenderer extends VillagerLikeEntityMCARenderer<VillagerEntityMCA> {
    public VillagerEntityMCARenderer(EntityRendererProvider.Context ctx) {
        super(ctx, createAnimationModel(ctx).hideWears());

        layers.add(0, new SkinLayer<>(this, createVisibleModel(VillagerEntityModelMCA.bodyData(CubeDeformation.NONE)).hideWears()));
        addLayer(new FaceLayer<>(this, createVisibleModel(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.01F))).hideWears(), "normal"));
        addLayer(new ClothingLayer<>(this, createVisibleModel(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.0625F))), "normal"));
        addLayer(new HairLayer<>(this, createVisibleModel(VillagerEntityModelMCA.hairData(new CubeDeformation(0.125F)))));
    }

    private static VillagerEntityModelMCA createAnimationModel(EntityRendererProvider.Context ctx) {
        return new VillagerEntityModelMCA(ctx.bakeLayer(ModelLayers.PLAYER));
    }

    private static VillagerEntityModelMCA createVisibleModel(MeshDefinition data) {
        VillagerEntityModelMCA model = new VillagerEntityModelMCA(LayerDefinition.create(data, 64, 64).bakeRoot());
        model.receiveDeferredAnimationPose();
        return model;
    }
}
