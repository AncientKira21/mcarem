package com.ancientkira.mca.client.render;

import com.ancientkira.mca.entity.ReaperAttackState;
import net.minecraft.client.renderer.entity.state.UndeadRenderState;

public class GrimReaperRenderState extends UndeadRenderState {
    public ReaperAttackState attackState = ReaperAttackState.IDLE;
}
