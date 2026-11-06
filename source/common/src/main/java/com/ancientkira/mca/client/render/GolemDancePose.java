package com.ancientkira.mca.client.render;
import java.util.*;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
public final class GolemDancePose {
    private static final Map<IronGolemRenderState,Float> PHASE=new WeakHashMap<>();
    public static void set(IronGolemRenderState state,Float phase){if(phase==null)PHASE.remove(state);else PHASE.put(state,phase);}
    public static Float phase(IronGolemRenderState state){return PHASE.get(state);}
}
