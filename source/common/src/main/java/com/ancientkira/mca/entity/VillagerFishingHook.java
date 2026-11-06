package com.ancientkira.mca.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

/** A real, tracked bobber owned by a villager rather than a fake player. */
public final class VillagerFishingHook extends Entity {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(VillagerFishingHook.class, EntityDataSerializers.INT);
    private Vec3 castStart, castEnd;
    private boolean reeling;
    public VillagerFishingHook(EntityType<? extends VillagerFishingHook> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(OWNER, -1); }
    public VillagerEntityMCA getOwner() { return level().getEntity(entityData.get(OWNER)) instanceof VillagerEntityMCA person ? person : null; }
    public void cast(VillagerEntityMCA person, Vec3 destination) {
        entityData.set(OWNER, person.getId());
        castStart = person.getEyePosition().add(0, -.35, 0); castEnd = destination;
        setPos(castStart);
    }
    public void reel() { reeling = true; }
    public boolean hasLanded() { return tickCount >= 15; }
    public void followFish(Entity fish) { setPos(fish.position().add(0, fish.getBbHeight() * .5, 0)); }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        VillagerEntityMCA owner = getOwner();
        if (owner == null || !owner.isAlive() || owner.distanceToSqr(this) > 24*24 || tickCount > 600) { discard(); return; }
        if (castStart == null || castEnd == null) { discard(); return; }
        if (!reeling) {
            double t = Math.min(1, tickCount / 15.0);
            setPos(castStart.lerp(castEnd, t).add(0, Math.sin(t*Math.PI)*.7 + (t == 1 ? Math.sin(tickCount*.2)*.035 : 0), 0));
        }
    }
    @Override protected void readAdditionalSaveData(ValueInput input) { }
    @Override protected void addAdditionalSaveData(ValueOutput output) { }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }
}
