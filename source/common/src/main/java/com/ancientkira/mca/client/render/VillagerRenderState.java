package com.ancientkira.mca.client.render;

import net.minecraft.client.renderer.entity.state.ZombieRenderState;

public class VillagerRenderState extends ZombieRenderState {
    public java.util.UUID mcaAnimationId;
    public boolean mcaRunning, mcaClimbing, mcaInWater, mcaFalling, mcaSleeping, mcaSwinging;
    public boolean mcaHurtFace, mcaScaredFace;
    public boolean mcaCorpse;
    public boolean isVillageDancing;
    public int danceStyle;
    public float dancePhase;
    public boolean isCastingSpell;
    public boolean isKidnapped;
    public float panicAnimationProgress;
}
