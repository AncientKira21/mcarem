package com.ancientkira.mca.server;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.relationship.AgeState;
import com.ancientkira.mca.util.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Automatic buckets are virtual; player-supplied inventory buckets are exchanged normally. */
public final class WaterBucketClutch {
    private WaterBucketClutch() {}
    /** Permission is specific to this villager; it never changes the world's gamerule. */
    private static boolean permitted(ServerLevel world,VillagerEntityMCA person) {
        if(VillagerGriefing.allowed(person)) {
            person.getWaterClutchState().remove("PermissionPlayer");
            person.getWaterClutchState().remove("PermissionPromptAfter");
            return true;
        }
        var state=person.getWaterClutchState();
        if(state.contains("ClutchPermission"))return state.getBooleanOr("ClutchPermission",false);
        if(world.getGameTime()<state.getLongOr("PermissionPromptAfter",0))return false;
        state.putLong("PermissionPromptAfter",world.getGameTime()+1200);
        var player=world.getServer().getPlayerList().getPlayers().stream()
                .filter(p->p.level()==world && p.distanceToSqr(person)<=64*64
                    && p.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                .min(java.util.Comparator.comparingDouble(person::distanceToSqr)).orElse(null);
        if(player==null)return false;
        state.putString("PermissionPlayer",player.getUUID().toString());
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("mca.clutch.permission",person.getName())
                .append(" ").append(permissionButton(person,true)).append(" ").append(permissionButton(person,false)));
        return false;
    }
    private static net.minecraft.network.chat.Component permissionButton(VillagerEntityMCA person,boolean allow) {
        return net.minecraft.network.chat.Component.translatable(allow?"gui.yes":"gui.no").withStyle(style->style.withUnderlined(true)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/mca clutch "+(allow?"yes ":"no ")+person.getUUID())));
    }
    public static int respond(net.minecraft.server.level.ServerPlayer player,java.util.UUID id,boolean allow) {
        if(!(player.level().getEntity(id) instanceof VillagerEntityMCA person))return 0;
        var state=person.getWaterClutchState();
        if(!state.getStringOr("PermissionPlayer","").equals(player.getUUID().toString()))return 0;
        state.putBoolean("ClutchPermission",allow);
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(allow?"mca.clutch.allowed":"mca.clutch.denied",person.getName()));
        return 1;
    }
    private static int bucket(VillagerEntityMCA person, net.minecraft.world.item.Item item) {
        return InventoryUtils.getFirstSlotContainingItem(person.getInventory(), stack -> stack.is(item));
    }
    private static void showBucket(VillagerEntityMCA person,net.minecraft.world.item.Item item) {
        var state=person.getWaterClutchState();
        if(!state.getBooleanOr("ClutchHandSaved",false)) {
            var held=person.getItemInHand(person.getDominantHand());
            if(!held.isEmpty())state.store("ClutchPreviousHand",ItemStack.OPTIONAL_CODEC,person.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),held.copy());
            else state.remove("ClutchPreviousHand");
            state.putBoolean("ClutchHandSaved",true);
        }
        person.setItemInHand(person.getDominantHand(),new ItemStack(item));
    }
    private static void restoreHand(VillagerEntityMCA person) {
        var state=person.getWaterClutchState();
        if(!state.getBooleanOr("ClutchHandSaved",false))return;
        person.setItemInHand(person.getDominantHand(),state.read("ClutchPreviousHand",ItemStack.OPTIONAL_CODEC,person.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE)).orElse(ItemStack.EMPTY));
        state.remove("ClutchPreviousHand");state.remove("ClutchHandSaved");state.remove("RestoreClutchHandAt");
    }
    private static boolean canPlace(ServerLevel world, BlockPos water) {
        return world.hasChunkAt(water) && world.getBlockState(water).canBeReplaced() && world.getFluidState(water).isEmpty()
                && !world.getBlockState(water.below()).getCollisionShape(world, water.below()).isEmpty()
                && !world.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, water);
    }
    private static boolean placeWater(ServerLevel world,VillagerEntityMCA person,BlockPos water) {
        int slot=bucket(person,Items.WATER_BUCKET);
        if(!canPlace(world,water) || !permitted(world,person))return false;
        showBucket(person,Items.WATER_BUCKET);
        if(!((net.minecraft.world.item.BucketItem)Items.WATER_BUCKET).emptyContents(person,world,water,null)) { restoreHand(person); return false; }
        if(slot>=0)person.getInventory().setItem(slot,new ItemStack(Items.BUCKET));
        person.getWaterClutchState().putBoolean("VirtualPlacement",slot<0);
        showBucket(person,Items.BUCKET);
        person.getWaterClutchState().putLong("RestoreClutchHandAt",world.getGameTime()+200);
        var state=person.getWaterClutchState();state.putBoolean("Placed",true);state.remove("WaterContactAt");
        state.putInt("X",water.getX());state.putInt("Y",water.getY());state.putInt("Z",water.getZ());
        state.putString("Dimension",world.dimension().identifier().toString());state.putLong("Expires",world.getGameTime()+200);
        person.swing(person.getDominantHand());return true;
    }
    /** Called at actual collision, before vanilla computes landing damage. No water means no protection. */
    public static void beforeLanding(VillagerEntityMCA person,boolean onGround,double downwardMotion) {
        if(!(person.level() instanceof ServerLevel world) || !onGround || downwardMotion>=0 || person.fallDistance<=2
                || !person.isAlive() || person.isNoAi() || person.isKidnapped() || person.isSleeping()
                || person.isPassenger() || person.getAgeState()==AgeState.BABY)return;
        var state=person.getWaterClutchState();
        if(!state.getBooleanOr("Placed",false)) {
            var box=person.getBoundingBox();
            search:for(BlockPos pos:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY+.01,box.minZ),
                    BlockPos.containing(box.maxX,box.minY+.01,box.maxZ))) {
                if(placeWater(world,person,pos.immutable()))break search;
            }
        }
        BlockPos water=new BlockPos(state.getIntOr("X",0),state.getIntOr("Y",0),state.getIntOr("Z",0));
        if(state.getBooleanOr("Placed",false) && state.getStringOr("Dimension","").equals(world.dimension().identifier().toString())
                && world.getFluidState(water).is(FluidTags.WATER)
                && person.getBoundingBox().intersects(new AABB(water))) {
            person.fallDistance=0;
            if(!state.contains("WaterContactAt"))state.putLong("WaterContactAt",world.getGameTime());
        }
    }
    public static void afterLanding(VillagerEntityMCA person) {
        if(!(person.level() instanceof ServerLevel world))return;
        var state=person.getWaterClutchState();
        if(!state.getBooleanOr("Placed",false) || !state.contains("WaterContactAt") || person.fallDistance>=.5F)return;
        BlockPos water=new BlockPos(state.getIntOr("X",0),state.getIntOr("Y",0),state.getIntOr("Z",0));
        if(state.getStringOr("Dimension","").equals(world.dimension().identifier().toString())
                && person.getBoundingBox().intersects(new AABB(water))) scoop(world,person,water);
    }
    private static boolean scoop(ServerLevel world,VillagerEntityMCA person,BlockPos water) {
        int empty=bucket(person,Items.BUCKET);
        boolean virtual=person.getWaterClutchState().getBooleanOr("VirtualPlacement",false);
        if((!virtual && empty<0) || !world.getFluidState(water).is(FluidTags.WATER) || !world.getFluidState(water).isSource())return false;
        if(!virtual) {
        ItemStack stack=person.getInventory().getItem(empty);
        if(stack.getCount()==1)person.getInventory().setItem(empty,new ItemStack(Items.WATER_BUCKET));
        else {
            if(!person.getInventory().canAddItem(new ItemStack(Items.WATER_BUCKET)))return false;
            stack.shrink(1);person.getInventory().addItem(new ItemStack(Items.WATER_BUCKET));
        }
        }
        showBucket(person,Items.WATER_BUCKET);
        person.getWaterClutchState().putLong("RestoreClutchHandAt",world.getGameTime()+10);
        person.getWaterClutchState().remove("VirtualPlacement");
        world.setBlock(water,Blocks.AIR.defaultBlockState(),3);
        person.swing(person.getDominantHand());person.playSound(SoundEvents.BUCKET_FILL,1,1);
        person.getWaterClutchState().remove("Placed");person.getWaterClutchState().remove("Escape");return true;
    }
    public static boolean active(VillagerEntityMCA person) { return person.getWaterClutchState().getBooleanOr("Escape", false); }
    public static void tick(VillagerEntityMCA person) {
        if (!(person.level() instanceof ServerLevel world)) return;
        var state = person.getWaterClutchState();
        long now = world.getGameTime();
        // No automatic inventory item: the built-in clutch equipment is virtual.
        if(state.contains("RestoreClutchHandAt") && now>=state.getLongOr("RestoreClutchHandAt",0))restoreHand(person);
        if (active(person)) {
            boolean hop = state.getBooleanOr("Hop", false);
            boolean landed = now > state.getLongOr("EscapeStarted", 0)+2 && person.onGround();
            if (now > state.getLongOr("EscapeUntil", 0) || person.isInWater() || person.isInLava()) state.remove("Escape");
            else if (landed) {
                if (hop && person.getY() >= state.getIntOr("HopY", 0)-.15) {
                    state.remove("Hop"); state.putLong("EscapeStarted", now);
                    person.jumpFromGround();
                } else state.remove("Escape");
            }
            if (active(person) && person.hurtTime <= 7) {
                hop = state.getBooleanOr("Hop", false);
                double dx = state.getIntOr(hop ? "HopX" : "EscapeX", 0)+.5-person.getX();
                double dz = state.getIntOr(hop ? "HopZ" : "EscapeZ", 0)+.5-person.getZ();
                // Horizontal air control only: Minecraft owns vertical jump motion and gravity.
                Vec3 velocity = person.getDeltaMovement();
                person.setDeltaMovement(Math.clamp(dx, -.24, .24), velocity.y, Math.clamp(dz, -.24, .24));
            }
        }
        if (state.getBooleanOr("Placed", false)) {
            BlockPos water = new BlockPos(state.getIntOr("X", 0), state.getIntOr("Y", 0), state.getIntOr("Z", 0));
            if (!state.getStringOr("Dimension", "").equals(world.dimension().identifier().toString())) { state.remove("Placed"); return; }
            boolean contacted = person.isInWater() && person.fallDistance < .5F
                    && person.getBoundingBox().intersects(new AABB(water));
            if (contacted) scoop(world,person,water);
            else if (now > state.getLongOr("Expires",0)) state.remove("Placed");
            return;
        }
        if (!person.isAlive() || person.isNoAi() || person.isKidnapped() || person.isPassenger()
                || person.isSleeping() || person.getAgeState() == AgeState.BABY
                 ) return;
        Vec3 motion = person.getDeltaMovement();
        if (!person.onGround() && motion.y < -.05
                && (active(person) && !state.getBooleanOr("Hop",false) && person.getY() < state.getDoubleOr("LaunchY",person.getY())
                    || person.fallDistance + Math.max(0, -motion.y) > 2.0F)) {
            showBucket(person,Items.WATER_BUCKET);
            if(!state.contains("RestoreClutchHandAt"))state.putLong("RestoreClutchHandAt",now+20);
            // Predict the next tick's horizontal position so a fleeing jumper does not pour behind itself.
            BlockPos feet = BlockPos.containing(person.getX() + motion.x, person.getY(), person.getZ() + motion.z);
            int reach = 4;
            for (int drop = 0; drop <= reach; drop++) {
                BlockPos water = feet.below(drop);
                if (active(person) && water.getY() > state.getDoubleOr("LaunchY",person.getY())-2) continue;
                if (!canPlace(world, water) || person.position().distanceToSqr(Vec3.atBottomCenterOf(water)) > 4.5*4.5) continue;
                placeWater(world,person,water);
                return;
            }
        }
        if (person.onGround() && now >= state.getLongOr("NextEscape", 0) && tryDescent(world, person, now)) return;
        if (person.onGround() && now >= state.getLongOr("NextEscape", 0)
                && person.getHealth() <= person.getMaxHealth() * .25F
                && !com.ancientkira.mca.entity.ai.brain.VillagerTasksMCA.hasMovementOrder(person)) tryEscape(world, person, now);
    }
    private static boolean tryDescent(ServerLevel world, VillagerEntityMCA person, long now) {
        if (person.hurtTime > 0 || person.getVillagerBrain().getMoveState() == com.ancientkira.mca.entity.ai.MoveState.STAY) return false;
        var state=person.getWaterClutchState();
        if(now<state.getLongOr("NextDropPlan",0))return false;
        state.putLong("NextDropPlan",now+20);
        var walk = person.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
        BlockPos origin=person.blockPosition();
        BlockPos destination=walk.map(target->target.getTarget().currentBlockPosition()).orElse(null);
        if(destination==null || destination.getY()>origin.getY()-3) {
            destination=person.getResidency().getHome().filter(home->home.dimension()==world.dimension() && home.pos().getY()<=origin.getY()-3)
                    .map(net.minecraft.core.GlobalPos::pos).orElse(null);
            if(destination==null) {
                int floor=0;
                for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)
                    if(!world.getBlockState(origin.offset(x,-1,z)).getCollisionShape(world,origin.offset(x,-1,z)).isEmpty())floor++;
                if(floor>5)return false; // Normal roofs and floors do not trigger idle cliff jumps.
            }
        }
        BlockPos goal=destination;
        var path=person.getNavigation().getPath();
        if(path!=null && !path.isDone() && path.canReach() && !person.getNavigation().isStuck()
                && person.getBrain().getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE).isEmpty())return false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Vec3 horizontal = new Vec3(direction.getStepX(), 0, direction.getStepZ());
            if (goal != null && horizontal.dot(Vec3.atBottomCenterOf(goal).subtract(person.position())) <= 0) continue;
            for (int distance = 1; distance <= 3; distance++) {
                BlockPos column = origin.relative(direction, distance);
                if (!world.isEmptyBlock(column) || !world.isEmptyBlock(column.above())) continue;
                for (int depth = 1; column.getY()-depth > world.getMinY(); depth++) {
                    BlockPos landing = column.below(depth);
                    if (!world.hasChunkAt(landing) || !world.getFluidState(landing).isEmpty()) break;
                    if (!world.isEmptyBlock(landing)) break;
                    if (!canPlace(world, landing)) continue;
                    if (depth < 3) break;
                    if (!com.ancientkira.mca.entity.ai.navigation.TerrainSafety.clear(world, landing)
                            || com.ancientkira.mca.entity.ai.navigation.TerrainSafety.lavaNear(world, new AABB(landing), 1)) break;
                    BlockPos hop = null;
                    boolean clear = world.noBlockCollision(person, person.getBoundingBox().move(0, 1.25, 0));
                    for (int step=1; clear && step<distance; step++) {
                        BlockPos between = origin.relative(direction, step);
                        if (!world.getBlockState(between).getCollisionShape(world, between).isEmpty()) {
                            BlockPos top = between.above();
                            if (hop != null || !com.ancientkira.mca.entity.ai.navigation.TerrainSafety.solidFloor(world, top)
                                    || !com.ancientkira.mca.entity.ai.navigation.TerrainSafety.clear(world, top)
                                    || !world.noBlockCollision(person, person.getBoundingBox().move(Vec3.atBottomCenterOf(top).subtract(person.position())).move(0, 1.25, 0))) clear=false;
                            else hop=top;
                        }
                    }
                    if (!clear) break;
                    if (!permitted(world,person)) return false;
                    person.getNavigation().stop();
                    stateEscape(person,column,now);
                    if (hop != null) {
                        state.putBoolean("Hop",true); state.putInt("HopX",hop.getX());
                        state.putInt("HopY",hop.getY()); state.putInt("HopZ",hop.getZ());
                    }
                    person.jumpFromGround(); return true;
                }
            }
        }
        return false;
    }
    private static void tryEscape(ServerLevel world, VillagerEntityMCA person, long now) {
        if (VillageWarfare.isFunFighting(person))return;
        var threat = person.getLastHurtByMob();
        if (threat == null || !threat.isAlive() || person.tickCount - person.getLastHurtByMobTimestamp() > 100
                || person.distanceToSqr(threat) > 64) return;
        var origin = person.blockPosition();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Vec3 horizontal = new Vec3(direction.getStepX(), 0, direction.getStepZ());
            if (horizontal.dot(person.position().subtract(threat.position())) <= 0) continue;
            BlockPos column = origin.relative(direction);
            if (!world.isEmptyBlock(column) || !world.isEmptyBlock(column.above())) continue;
            for (int depth = 1; depth <= 12; depth++) {
                BlockPos landing = column.below(depth);
                if (!world.hasChunkAt(landing) || !world.getFluidState(landing).isEmpty()) break;
                if (!world.isEmptyBlock(landing)) break;
                if (!world.getBlockState(landing.below()).isFaceSturdy(world, landing.below(), Direction.UP)) continue;
                if (depth < 4 || !canPlace(world, landing)
                        || com.ancientkira.mca.entity.ai.navigation.TerrainSafety.lavaNear(world, new AABB(landing), 1)) break;
                if (!world.noBlockCollision(person, person.getBoundingBox().move(horizontal.scale(.5)).move(0, .45, 0))) break;
                if (!permitted(world,person)) return;
                person.getNavigation().stop(); person.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                // Step over the lip, then fall vertically into the checked water column.
                stateEscape(person, column, now);
                person.setDeltaMovement(horizontal.scale(.24));
                person.jumpFromGround();
                return;
            }
        }
    }
    private static void stateEscape(VillagerEntityMCA person, BlockPos column, long now) {
        var state = person.getWaterClutchState();
        state.remove("Vault"); state.remove("Hop");
        state.putDouble("LaunchY",person.getY());
        state.putLong("NextEscape", now + 200);
        state.putBoolean("Escape", true);
        state.putLong("EscapeStarted", now); state.putLong("EscapeUntil", now + 100);
        state.putInt("EscapeX", column.getX()); state.putInt("EscapeZ", column.getZ());
    }
}
