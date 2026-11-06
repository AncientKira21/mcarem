package com.ancientkira.mca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ancientkira.mca.Config;
import com.ancientkira.mca.client.gui.VillagerEditorScreen;
import com.ancientkira.mca.client.model.VillagerEntityBaseModelMCA;
import com.ancientkira.mca.client.model.VillagerEntityModelMCA;
import com.ancientkira.mca.entity.Infectable;
import com.ancientkira.mca.entity.VillagerLike;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.jetbrains.annotations.Nullable;
import java.util.Set;

@SuppressWarnings({"rawtypes", "unchecked"})
public class VillagerLikeEntityMCARenderer<T extends Mob & VillagerLike<T>>
    extends HumanoidMobRenderer<T, VillagerRenderState, VillagerEntityModelMCA> {
    private static final Identifier TEXTURE = Identifier.parse("textures/entity/steve.png");

    public VillagerLikeEntityMCARenderer(EntityRendererProvider.Context ctx, VillagerEntityModelMCA model) {
        super(ctx, model, 0.5F);
        addLayer(new HumanoidArmorLayer<>(this, createArmorModelSet(), ctx.getEquipmentRenderer()));
    }

    private ArmorModelSet<VillagerEntityBaseModelMCA> createArmorModelSet() {
        return new ArmorModelSet<>(
            createArmorModel(0.55F, EquipmentSlot.HEAD),
            createArmorModel(0.55F, EquipmentSlot.CHEST),
            createArmorModel(0.3F, EquipmentSlot.LEGS),
            createArmorModel(0.55F, EquipmentSlot.FEET)
        );
    }

    private VillagerEntityBaseModelMCA createArmorModel(float dilation, EquipmentSlot slot) {
        MeshDefinition mesh = VillagerEntityBaseModelMCA.getModelData(new CubeDeformation(dilation));
        if (slot == EquipmentSlot.HEAD) {
            mesh.getRoot().retainPartsAndChildren(armorParts(slot));
        } else {
            mesh.getRoot().retainExactParts(armorParts(slot));
        }
        return new VillagerEntityBaseModelMCA(LayerDefinition.create(mesh, 64, 32).bakeRoot())
                .receiveDeferredAnimationPose();
    }

    private Set<String> armorParts(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> Set.of("head");
            case CHEST -> Set.of("body", "left_arm", "right_arm", "breasts");
            case LEGS -> Set.of("left_leg", "right_leg", "body");
            case FEET -> Set.of("left_leg", "right_leg");
            default -> throw new IllegalStateException("No armor model parts for slot: " + slot);
        };
    }

    @Override
    protected HumanoidModel.ArmPose getArmPose(T mob, HumanoidArm arm) {
        ItemStack itemStack = mob.getItemHeldByArm(arm);
        InteractionHand hand = arm == mob.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        if (mob.isUsingItem() && mob.getUsedItemHand() == hand) {
            ItemUseAnimation anim = itemStack.getUseAnimation();
            if(itemStack.is(net.minecraft.world.item.Items.TRIDENT))return HumanoidModel.ArmPose.THROW_TRIDENT;
            if (anim == ItemUseAnimation.BLOCK) {
                return HumanoidModel.ArmPose.BLOCK;
            }
            if (anim == ItemUseAnimation.BOW) {
                return HumanoidModel.ArmPose.BOW_AND_ARROW;
            }
            if (anim == ItemUseAnimation.CROSSBOW) {
                return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
            }
        }
        if (!(mob.isUsingItem() && mob.getUseItem().has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS))
                && itemStack.getItem() instanceof net.minecraft.world.item.CrossbowItem
                && !itemStack.getOrDefault(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES,
                        net.minecraft.world.item.component.ChargedProjectiles.EMPTY).isEmpty()) {
            return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }
        if (!(mob.isUsingItem() && mob.getUseItem().has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS))
                && com.ancientkira.mca.entity.ai.GunCompatibility.isGun(itemStack)) return HumanoidModel.ArmPose.BOW_AND_ARROW;
        return super.getArmPose(mob, arm);
    }

    @Override
    protected void setupRotations(VillagerRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
        super.setupRotations(state, poseStack, bodyRot, entityScale);
        // Use the same whole-body swimming tilt and offset as Minecraft's player renderer.
        if (state.swimAmount > 0 && !state.hasPose(Pose.SLEEPING) && state.deathTime <= 0) {
            float targetAngle = state.isInWater ? -90.0F - state.xRot : -90.0F;
            poseStack.mulPose(Axis.XP.rotationDegrees(state.swimAmount * targetAngle));
            if (state.isVisuallySwimming) poseStack.translate(0.0F, -1.0F, 0.3F);
        }
    }

    @Override
    public VillagerRenderState createRenderState() {
        return new VillagerRenderState();
    }

    @Override
    public void extractRenderState(T entity, VillagerRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.mcaAnimationId = entity.getUUID();
        state.mcaRunning = entity.isSprinting() || entity.getSpeed() > entity.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) * 0.75;
        state.mcaClimbing = entity.onClimbable();
        state.mcaInWater = entity.isInWater();
        state.mcaFalling = !entity.onGround() && !entity.isInWater() && !entity.isInLava() && entity.getDeltaMovement().y < -0.1;
        state.mcaSleeping = entity.isSleeping();
        state.mcaHurtFace = entity.hurtTime > 0;
        state.mcaScaredFace = entity.getVillagerBrain().isPanicking();
        if (entity.isSleeping() || (entity instanceof com.ancientkira.mca.entity.VillagerEntityMCA patient && patient.isRestingInInfirmary()
                && entity.level().getBlockState(entity.blockPosition()).is(net.minecraft.tags.BlockTags.BEDS))) {
            state.pose = net.minecraft.world.entity.Pose.SLEEPING;
            state.mcaSleeping = true;
            var bed = entity.level().getBlockState(entity.blockPosition());
            if (com.ancientkira.mca.server.BedCompatibility.isBed(bed)) {
                state.bedOrientation = bed.getValue(net.minecraft.world.level.block.BedBlock.FACING);
            } else if (entity.getSleepingPos().isPresent()) {
                var sleepBed = entity.level().getBlockState(entity.getSleepingPos().get());
                if (com.ancientkira.mca.server.BedCompatibility.isBed(sleepBed)) {
                    state.bedOrientation = sleepBed.getValue(net.minecraft.world.level.block.BedBlock.FACING);
                }
            }
            if (state.bedOrientation == null) {
                state.bedOrientation = entity.getDirection();
            }
        }
        state.mcaSwinging = entity.getAttackAnim(partialTicks) > 0;
        if (state.nameTag != null) {
            state.nameTag = net.minecraft.network.chat.Component.literal(
                    com.ancientkira.mca.entity.FamilyNames.nameTag(state.nameTag.getString(),
                            entity.getCustomName() == null ? null : entity.getCustomName().getString(),
                            com.ancientkira.mca.entity.FamilyNames.firstName(entity)))
                    .withStyle(state.nameTag.getStyle());
        }
        state.isVillageDancing = entity instanceof com.ancientkira.mca.entity.VillagerEntityMCA dancer && dancer.isVillageDancing();
        state.danceStyle = com.ancientkira.mca.entity.ai.DanceMotion.style(entity.getUUID(), entity.level().getGameTime());
        state.dancePhase = com.ancientkira.mca.entity.ai.DanceMotion.phase(entity.getUUID());
        state.isCastingSpell = entity instanceof com.ancientkira.mca.entity.VillagerEntityMCA person && person.isCastingSpell();
        state.isKidnapped = entity instanceof com.ancientkira.mca.entity.VillagerEntityMCA captive && captive.isKidnapped();
        VillagerStateHolder holder = VillagerStateHolder.require(state);
        holder.mca$setVillagerRenderData(VillagerRenderData.create(VillagerLike.PlayerModel.VILLAGER, entity));
        state.panicAnimationProgress = entity instanceof com.ancientkira.mca.entity.VillagerEntityMCA captive && captive.isKidnapped()
                ? 1.0F : entity.getVillagerBrain().getPanicAnimationProgress(partialTicks);
        VillagerRenderStateHooks.extractScaledBounds(entity, state);
    }

    @Override
    protected void scale(VillagerRenderState state, PoseStack matrices) {
        VillagerVisuals visuals = VillagerVisuals.require(state);
        float height = visuals.rawVerticalScaleFactor();
        float width = visuals.rawHorizontalScaleFactor();
        matrices.scale(width, height, width);
        if (visuals.baby() && !state.isPassenger && !state.hasPose(Pose.SLEEPING)) {
            matrices.translate(0.0F, 0.6F, 0.0F);
        }
    }

    @Nullable
    @Override
    protected RenderType getRenderType(VillagerRenderState state, boolean showBody, boolean translucent, boolean showOutlines) {
        //setting the type to null prevents it from rendering
        //we need a skin layer anyway because of the color
        return null;
    }

    @Override
    protected boolean shouldShowName(T villager, double distanceToCameraSq) {
        Player player = Minecraft.getInstance().player;
        return villager.getCustomName() != null
               && !(Minecraft.getInstance().screen instanceof VillagerEditorScreen)
               && player != null
               && !Minecraft.getInstance().options.hideGui
               && Config.getInstance().showNameTags
               && player.distanceToSqr(villager) < Math.pow(Config.getInstance().nameTagDistance, 2.0f)
               && !villager.isInvisibleTo(player);
    }

    @Override
    public Identifier getTextureLocation(VillagerRenderState state) {
        if (state instanceof VillagerStateHolder holder) {
            VillagerVisuals visuals = holder.mca$getVisuals();
            if (visuals != null) {
                return DynamicSkinCache.getOrCreateStitchedSkin(visuals);
            }
        }
        return TEXTURE;
    }

    @Override
    protected boolean isShaking(VillagerRenderState state) {
        var visuals = VillagerVisuals.require(state);
        return Math.max(visuals.infectionProgress(), visuals.sicknessProgress()) > Infectable.FEVER_THRESHOLD;
    }
}
