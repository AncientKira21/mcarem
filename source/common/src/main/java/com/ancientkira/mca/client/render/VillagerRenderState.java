package com.ancientkira.mca.client.render;

import net.minecraft.client.renderer.entity.state.UndeadRenderState;

public class VillagerRenderState extends UndeadRenderState {
    public java.util.UUID mcaAnimationId;
    public boolean mcaRunning, mcaClimbing, mcaInWater, mcaFalling, mcaSleeping, mcaSwinging;
    public boolean isConverting;
    public boolean isVillageDancing;
    public boolean isCastingSpell;
    public boolean isKidnapped;
    public float panicAnimationProgress;
}
