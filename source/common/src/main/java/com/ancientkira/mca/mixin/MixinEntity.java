package com.ancientkira.mca.mixin;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.Traits;
import com.ancientkira.mca.entity.ai.relationship.AgeState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class MixinEntity {

    @org.spongepowered.asm.mixin.Shadow private com.google.common.collect.ImmutableList<Entity> passengers;

    @Inject(method="canAddPassenger(Lnet/minecraft/world/entity/Entity;)Z",at=@At("HEAD"),cancellable=true)
    private void mca$shareSaddledMount(Entity passenger,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> result) {
        Entity mount=(Entity)(Object)this;
        if(com.ancientkira.mca.server.VillagerMounts.sharesSeat(mount,passenger))result.setReturnValue(passengers.size()<2);
    }

    @Inject(method="addPassenger(Lnet/minecraft/world/entity/Entity;)V",at=@At("TAIL"))
    private void mca$lastBoarderDrives(Entity passenger,CallbackInfo callback) {
        Entity mount=(Entity)(Object)this;
        if(!mount.level().isClientSide() && com.ancientkira.mca.server.VillagerMounts.sharesSeat(mount,passenger)) {
            java.util.List<Entity> ordered=new java.util.ArrayList<>(passengers);
            ordered.remove(passenger);ordered.addFirst(passenger);
            passengers=com.google.common.collect.ImmutableList.copyOf(ordered);
        }
    }

    @Inject(method="getPassengerRidingPosition(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/Vec3;",at=@At("RETURN"),cancellable=true)
    private void mca$rearSaddleSeat(Entity passenger,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.phys.Vec3> result) {
        Entity mount=(Entity)(Object)this;
        if(com.ancientkira.mca.server.VillagerMounts.saddled(mount) && com.ancientkira.mca.server.VillagerMounts.hasVillager(mount)
                && !(mount instanceof net.minecraft.world.entity.animal.camel.Camel) && passengers.indexOf(passenger)==1) {
            double yaw=Math.toRadians(mount.getYRot());
            result.setReturnValue(result.getReturnValue().add(Math.sin(yaw)*.55,0,-Math.cos(yaw)*.55));
        }
    }

    @Redirect(
            method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityType;canSerialize()Z")
    )
    private boolean mca$allowCarriedVillagersToRidePlayers(EntityType<?> type, Entity entityToRide, boolean force, boolean sendEventAndTriggers) {
        // Players are non-serializable entities, but MCA uses them as temporary carry vehicles for children.
        if (force && entityToRide instanceof Player && (Object) this instanceof VillagerEntityMCA villager) {
            AgeState ageState = villager.getAgeState();
            if (ageState == AgeState.BABY || ageState == AgeState.TODDLER || ageState == AgeState.CHILD) {
                return true;
            }
        }

        return type.canSerialize();
    }

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void mca$underwaterEntityCollision(Entity entity, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self.isInWater() || entity.isInWater()) {
            if (self.isPassengerOfSameVehicle(entity) || self.noPhysics || entity.noPhysics) return;
            double dx = entity.getX() - self.getX();
            double dy = entity.getY() - self.getY();
            double dz = entity.getZ() - self.getZ();
            double distSqr = dx * dx + dy * dy + dz * dz;
            if (distSqr >= 0.0001D && distSqr < 4.0D) {
                double dist = Math.sqrt(distSqr);
                double strength = (1.0D - dist / 2.0D) * 0.1D;
                dx /= dist;
                dy /= dist;
                dz /= dist;
                self.push(-dx * strength, -dy * strength, -dz * strength);
                entity.push(dx * strength, dy * strength, dz * strength);
                ci.cancel();
            }
        }
    }
}
