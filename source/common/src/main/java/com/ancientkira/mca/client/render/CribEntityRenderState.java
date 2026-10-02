package com.ancientkira.mca.client.render;

import com.ancientkira.mca.entity.CribEntity;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.jspecify.annotations.Nullable;

public class CribEntityRenderState extends EntityRenderState {
    public @Nullable CribEntity crib;
    public float yRot;
    public final ItemStackRenderState babyItem = new ItemStackRenderState();
}
