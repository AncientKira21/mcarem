package com.ancientkira.mca.entity.ai;

import com.ancientkira.mca.Config;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.server.world.data.GraveyardManager;
import com.ancientkira.mca.server.world.data.Village;
import com.ancientkira.mca.server.world.data.VillageManager;
import com.ancientkira.mca.util.network.datasync.CDataManager;
import com.ancientkira.mca.util.network.datasync.CDataParameter;
import com.ancientkira.mca.util.network.datasync.CParameter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Villagers need a place to live too.
 */
public class Residency {
    private static final CDataParameter<Integer> VILLAGE = CParameter.create("HomeVillage", -1);
    private static final CDataParameter<Integer> RETURN_VILLAGE=CParameter.create("ReturnVillage",-1);
    private final VillagerEntityMCA entity;

    public Residency(VillagerEntityMCA entity) {
        this.entity = entity;
    }

    public static <E extends Entity> CDataManager.Builder<E> createTrackedData(CDataManager.Builder<E> builder) {
        return builder.addAll(VILLAGE,RETURN_VILLAGE);
    }

    public BlockPos getWorkplace() {
        return entity.getBrain()
                .getMemoryInternal(MemoryModuleType.JOB_SITE)
                .map(GlobalPos::pos)
                .orElse(BlockPos.ZERO);
    }

    public void setWorkplace(ServerPlayer player) {
        PoiManager pointOfInterestStorage = ((ServerLevel) player.level()).getPoiManager();
        VillagerProfession noneProfession = BuiltInRegistries.VILLAGER_PROFESSION.getValueOrThrow(VillagerProfession.NONE);
        pointOfInterestStorage.findClosest(noneProfession.acquirableJobSite(), a -> true, entity.blockPosition(), 8, PoiManager.Occupancy.HAS_SPACE).ifPresentOrElse(blockPos -> {
                    pointOfInterestStorage.getType(blockPos).ifPresent(pointOfInterestType -> {
                        pointOfInterestStorage.take(noneProfession.acquirableJobSite(), (registryEntry, blockPos2) -> {
                            return blockPos2.equals(blockPos);
                        }, blockPos, 1);

                        // Forget current site
                        entity.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
                        entity.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
                        entity.releasePoi(MemoryModuleType.JOB_SITE);
                        entity.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);

                        // Set
                        GlobalPos globalPos = GlobalPos.of(player.level().dimension(), blockPos);
                        entity.getBrain().setMemory(MemoryModuleType.JOB_SITE, globalPos);
                        player.level().broadcastEntityEvent(entity, (byte) 14);
                        MinecraftServer minecraftServer = player.level().getServer();
                        Optional.ofNullable(minecraftServer.getLevel(globalPos.dimension())).flatMap(world -> {
                            return world.getPoiManager().getType(globalPos.pos());
                        }).flatMap(registryEntry -> {
                            return BuiltInRegistries.VILLAGER_PROFESSION.stream().filter(profession -> {
                                return profession.heldJobSite().test(registryEntry);
                            }).findFirst();
                        }).ifPresent(profession -> {
                            VillagerProfession oldProfession = entity.getVillagerData().profession().value();
                            // A workplace assignment replaces follow/stay orders, including for children.
                            entity.getVillagerBrain().setMoveState(MoveState.MOVE, player);
                            if (oldProfession == profession) {
                                return;
                            }
                            int level = entity.getVillagerData().level();
                            entity.setVillagerData(entity.getVillagerData().withProfession(BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(profession)).withLevel(1));
                            entity.setOffers(null);
                            entity.getOffers();
                            for (int l = 1; l < level; l++) {
                                entity.customLevelUp();
                            }
                            entity.refreshBrain((ServerLevel) player.level());
                        });

                        // Success
                        entity.sendChatMessage(player, "interaction.setworkplace.success");
                    });
                },
                () -> {
                    entity.sendChatMessage(player, "interaction.setworkplace.failed");
                });
    }

    /** Event records may belong to a nearby village even when a traveler/raider has no home. */
    public Optional<Village> getRegistryVillage() {
        return getHomeVillage().or(() -> VillageManager.get((ServerLevel) entity.level())
                .findNearestVillage(entity.blockPosition(), 32));
    }

    /** Local defenders can draw supplies before their home registration completes. */
    public Optional<Village> getEquipmentVillage() {
        return getHomeVillage().or(() -> VillageManager.get((ServerLevel) entity.level()).findNearestVillage(entity));
    }

    public Optional<Village> getHomeVillage() {
        VillageManager manager = VillageManager.get((ServerLevel) entity.level());

        // Occupied rooms may have no POI (especially basements). Retry these
        // positions so an entrance discovered later can establish their floor.
        manager.reportBuilding(entity.blockPosition());
        return manager.getOrEmpty(entity.getTrackedValue(VILLAGE));
    }

    /**
     * Joins the closest village, if in range
     */
    public void seekHome() { seekHome(false); }

    private void seekHome(boolean explicitRelocation) {
        if (entity.requiresHome()) {
            VillageManager manager = VillageManager.get((ServerLevel) entity.level());
            Optional<Village> current = getHomeVillage();
            Optional<Village> target = (!explicitRelocation ? manager.getOrEmpty(entity.getTrackedValue(RETURN_VILLAGE)) : Optional.<Village>empty())
                    .or(()-> (entity.isGuard() && current.isPresent() && !explicitRelocation ? current : getHome()
                    .filter(home -> home.dimension() == entity.level().dimension())
                    .flatMap(home -> manager.findNearestVillage(home.pos(), Village.BORDER_MARGIN))
                    .or(() -> current.filter(village -> village.isWithinBorder(entity)))
                    .or(() -> manager.findNearestVillage(entity))));

            target.ifPresent(v -> {
                if (current.filter(existing -> existing.getId() == v.getId()).isEmpty()) {
                    leaveHome();
                }
                v.updateResident(entity);
                entity.setTrackedValue(VILLAGE, v.getId());
            });
        }
    }

    public void returnAfterRelease(Village home,Village released){
        entity.setTrackedValue(RETURN_VILLAGE,home.getId());
        if(entity.getVillagerBrain().getCurrentJob()!=Chore.NONE)entity.getVillagerBrain().abandonJob();
        for(var memory:java.util.List.of(MemoryModuleType.HOME,MemoryModuleType.JOB_SITE)){
            var pos=entity.getBrain().getMemoryInternal(memory);
            if(pos!=null && pos.filter(site->released.isWithinBorder(site.pos(),0)).isPresent()){
                entity.releasePoi(memory);entity.getBrain().eraseMemory(memory);
            }
        }
    }
    public void tickReturn(){
        int id=entity.getTrackedValue(RETURN_VILLAGE);
        if(id<0||entity.tickCount%20!=0||entity.getVillagerBrain().getMoveState()!=MoveState.MOVE
                ||entity.getVillagerBrain().isPanicking()||entity.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET))return;
        var village=VillageManager.get((ServerLevel)entity.level()).getOrEmpty(id);
        if(village.isEmpty()||village.get().isWithinBorder(entity.blockPosition(),0)){entity.setTrackedValue(RETURN_VILLAGE,-1);return;}
        var claimed=entity.getBrain().getMemoryInternal(MemoryModuleType.HOME);
        if(claimed!=null && claimed.filter(pos->!village.get().isWithinBorder(pos.pos(),0)).isPresent()){
            entity.releasePoi(MemoryModuleType.HOME);entity.getBrain().eraseMemory(MemoryModuleType.HOME);
        }
        var target=net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosTowards(entity,32,16,
                net.minecraft.world.phys.Vec3.atBottomCenterOf(village.get().getCenter()),Math.PI*0.5);
        if(target!=null)entity.getBrain().setMemory(MemoryModuleType.WALK_TARGET,new net.minecraft.world.entity.ai.memory.WalkTarget(target,0.5F,1));
    }

    public void joinVillage(Village village){entity.setTrackedValue(RETURN_VILLAGE,-1);entity.setTrackedValue(VILLAGE,village.getId());village.updateResident(entity);village.confirmVoluntaryResident(entity.getUUID());village.markDirty();}

    public void remapVillage(int oldId, int newId) {
        if (entity.getTrackedValue(VILLAGE) == oldId) entity.setTrackedValue(VILLAGE, newId);
    }

    public void leaveHome() {
        Optional<Village> village = getHomeVillage();
        village.ifPresent(v -> {
            v.removeResident(entity);
        });
        entity.setTrackedValue(VILLAGE, -1);
    }

    public void tick() {
        //report buildings close by
        if (entity.tickCount % 600 == 0 && entity.requiresHome()) {
            Optional<Village> village = getHomeVillage();
            if (village.isEmpty() && Config.getInstance().enableAutoScanByDefault || village.filter(Village::isAutoScan).isPresent()) {
                reportBuildings();
            }

            //seek a home
            if (village.isEmpty()) {
                seekHome();
            }
        }

        //slowly inject village boni
        if (entity.tickCount % 1200 == 0) {
            getHomeVillage().ifPresentOrElse(village -> {
                //update the reputation
                entity.level().players().forEach(player -> {
                    //currently, only hearts are considered, maybe additional factors can affect that too
                    int hearts = entity.getVillagerBrain().getMemoriesForPlayer(player).getHearts();
                    village.setReputation(player, entity, hearts);
                });
            }, this::leaveHome);
        }
    }

    //report potential buildings within this villagers reach
    private void reportBuildings() {
        VillageManager manager = VillageManager.get((ServerLevel) entity.level());

        //fetch all near POIs
        Stream<BlockPos> stream = ((ServerLevel) entity.level()).getPoiManager().findAll(
                type -> true,
                p -> !manager.cache.contains(p),
                entity.blockPosition(),
                48,
                PoiManager.Occupancy.ANY);

        //check if it is a building
        stream.forEach(manager::reportBuilding);

        // also add tombstones
        GraveyardManager.get((ServerLevel) entity.level()).reportToVillageManager(entity);
    }

    public Optional<GlobalPos> getHome() {
        return entity.getMCABrain().getMemoryInternal(MemoryModuleType.HOME);
    }

    private static boolean validateBedPoi(ServerLevel level, BlockPos blockPos) {
        BlockState blockState = level.getBlockState(blockPos);
        return blockState.is(BlockTags.BEDS)
                && blockState.hasProperty(BedBlock.OCCUPIED)
                && !blockState.getValue(BedBlock.OCCUPIED);
    }

    public void setHome(ServerPlayer player) {
        if (!entity.requiresHome()) {
            entity.sendChatMessage(player, "interaction.sethome.temporary");
            return;
        }

        // also trigger a building refresh, because why not
        VillageManager manager = VillageManager.get((ServerLevel) player.level());
        manager.processBuilding(player.blockPosition(), true, false);

        seekHome();

        ServerLevel level = (ServerLevel) player.level();
        PoiManager poiManager = level.getPoiManager();
        Optional<GlobalPos> previousHome = entity.getBrain().getMemoryInternal(MemoryModuleType.HOME);
        poiManager.take(
                registryEntry -> registryEntry.is(PoiTypes.HOME),
                (registryEntry, blockPos) -> validateBedPoi(level, blockPos),
                player.blockPosition(),
                8
        ).ifPresentOrElse(claimedHome -> {
            entity.sendChatMessage(player, "interaction.sethome.success");

            boolean reclaimedSameHome = previousHome
                    .map(home -> home.dimension().equals(level.dimension()) && home.pos().equals(claimedHome))
                    .orElse(false);
            if (!reclaimedSameHome) {
                entity.releasePoi(MemoryModuleType.HOME);
            }
            entity.getBrain().eraseMemory(MemoryModuleType.HOME);

            entity.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), claimedHome));
            if (entity instanceof com.ancientkira.mca.entity.VillagerEntityMCA person) com.ancientkira.mca.server.PortalHomecoming.rememberHome(person);
            entity.getBrain().setMemory(MemoryModuleTypeMCA.FORCED_HOME, true);

            seekHome();
        }, () -> {
            entity.getBrain().eraseMemory(MemoryModuleTypeMCA.FORCED_HOME);

            getHomeVillage().map(v -> v.getBuildingAt(entity.blockPosition())).filter(Optional::isPresent).map(Optional::get).filter(b -> b.getBuildingType().noBeds()).ifPresentOrElse(building -> {
                entity.sendChatMessage(player, "interaction.sethome.bedfail." + building.getBuildingType().name());
            }, () -> {
                entity.sendChatMessage(player, "interaction.sethome.bedfail");
            });
        });
    }

    public void goHome(Player player) {
        entity.getVillagerBrain().setMoveState(MoveState.MOVE, player);
        entity.getInteractions().stopInteracting();
        getHome().filter(p -> p.dimension() == entity.level().dimension()).ifPresentOrElse(home -> {
            entity.moveTowards(home.pos());
            entity.sendChatMessage(player, "interaction.gohome.success");
        }, () -> entity.sendChatMessage(player, "interaction.gohome.fail.nohome"));
    }
}
