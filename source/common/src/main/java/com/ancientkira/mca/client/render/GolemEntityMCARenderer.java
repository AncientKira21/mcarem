package com.ancientkira.mca.client.render;

import com.ancientkira.mca.MCA;
import net.minecraft.client.model.animal.golem.IronGolemModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.resources.Identifier;

/** Player skin UVs stretched through part scales, preserving golem proportions and animation. */
public final class GolemEntityMCARenderer extends IronGolemRenderer {
    public GolemEntityMCARenderer(EntityRendererProvider.Context context) {
        super(context);
        MeshDefinition mesh = new MeshDefinition();
        var root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0,0)
                .addBox(-4,-8,-4,8,8,8), PartPose.offset(0,-7,-2));
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16,16)
                .addBox(-4,0,-2,8,12,4), pose(0,-9,0,2.25F,1.42F,2.75F));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40,16)
                .addBox(-4,-1,-2,4,12,4), pose(-9,-7,0,1,2.5F,1.5F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(32,48)
                .addBox(0,-1,-2,4,12,4), pose(9,-7,0,1,2.5F,1.5F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0,16)
                .addBox(-2,0,-2,4,12,4), pose(-4,8,0,1.5F,1.333333F,1.25F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16,48)
                .addBox(-2,0,-2,4,12,4), pose(4,8,0,1.5F,1.333333F,1.25F));
        model = new IronGolemModel(LayerDefinition.create(mesh,64,64).bakeRoot());
        // Vanilla crack overlays use iron-golem UVs and cannot fit player skin UVs.
        layers.clear();
    }
    private static PartPose pose(float x,float y,float z,float sx,float sy,float sz) {
        return new PartPose(x,y,z,0,0,0,sx,sy,sz);
    }
    @Override public Identifier getTextureLocation(IronGolemRenderState state) {
        return MCA.locate("textures/entity/golem/person.png");
    }
}
