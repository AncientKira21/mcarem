package com.ancientkira.mca.client.model;

import com.ancientkira.mca.client.render.VillagerRenderState;
import com.ancientkira.mca.client.render.VillagerVisuals;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.zombie.ZombieModel;

public class ZombieVillagerEntityModelMCA extends VillagerEntityModelMCA {
    private final ZombieModel<VillagerRenderState> vanillaZombie;
    public ZombieVillagerEntityModelMCA(ModelPart tree) {
        super(tree);
        vanillaZombie = new ZombieModel<>(tree);
    }
    @Override public void setupAnim(VillagerRenderState state) {
        super.setupAnim(state);
        if (!hasDeferredPose(state) && !state.hasPose(net.minecraft.world.entity.Pose.SLEEPING) && !state.mcaSleeping) {
            vanillaZombie.setupAnim(state);
            applyVillagerDimensions(VillagerVisuals.require(state));
        }
        // Vanilla zombie animation resets the legs after the shared baby pose.
        applyBabySitting(state);
        syncWearParts();
    }
}
