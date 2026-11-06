package com.ancientkira.mca.client.render;

import com.ancientkira.mca.block.CorpseBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Reuses the deceased person's renderer, textures and sleeping pose without spawning an entity. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class CorpseBlockEntityRenderer implements BlockEntityRenderer<CorpseBlock.Data, CorpseBlockEntityRenderer.State> {
    public CorpseBlockEntityRenderer(BlockEntityRendererProvider.Context context) { }
    public static final class State extends BlockEntityRenderState {
        EntityRenderer renderer;
        LivingEntityRenderState person;
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(CorpseBlock.Data block, State state, float partialTick, Vec3 camera, @Nullable CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(block, state, partialTick, camera, breaking);
        state.renderer = null; state.person = null;
        if (block.getLevel() == null) return;
        if (block.preview == null) {
            CompoundTag tag = block.body.getCompound("Appearance").orElseGet(CompoundTag::new).copy();
            Entity loaded = EntityType.create(TagValueInput.create(ProblemReporter.DISCARDING, block.getLevel().registryAccess(), tag), block.getLevel(), EntitySpawnReason.LOAD).orElse(null);
            if (!(loaded instanceof LivingEntity person)) return;
            person.setHealth(person.getMaxHealth()); person.deathTime=0; person.hurtTime=0;
            person.removeAllEffects(); person.setInvisible(false); person.setPose(Pose.SLEEPING);
            person.setId(-1000000 - (block.getBlockPos().hashCode() & 0x3fffffff));
            person.setDeltaMovement(Vec3.ZERO);
            person.snapTo(block.getBlockPos().getX()+.5, block.getBlockPos().getY(), block.getBlockPos().getZ()+.5);
            person.xOld=person.getX(); person.yOld=person.getY(); person.zOld=person.getZ();
            block.preview=person;
        }
        EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(block.preview);
        if (!(renderer.createRenderState(block.preview, 0) instanceof LivingEntityRenderState person)) return;
        if (person instanceof VillagerRenderState villager) {
            villager.mcaCorpse=true; villager.mcaHurtFace=false; villager.mcaScaredFace=false;
        }
        person.pose=Pose.SLEEPING; person.bedOrientation=Direction.NORTH;
        person.deathTime=0; person.ageInTicks=0; person.nameTag=null; person.lightCoords=state.lightCoords;
        state.renderer=renderer; state.person=person;
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.renderer == null || state.person == null) return;
        pose.pushPose(); pose.translate(.5, .15, .5);
        state.renderer.submit(state.person, pose, collector, camera);
        pose.popPose();
    }
}
