package com.ancientkira.mca.server;

import com.ancientkira.mca.Config;
import com.ancientkira.mca.ducks.IVillagerEntity;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.VillagerFactory;
import com.ancientkira.mca.entity.ZombieVillagerEntityMCA;
import com.ancientkira.mca.entity.ZombieVillagerFactory;
import com.ancientkira.mca.entity.ai.relationship.Gender;
import com.ancientkira.mca.server.world.data.Nationality;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;

import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SpawnQueue {
    private static final SpawnQueue INSTANCE = new SpawnQueue();
    private final ConcurrentLinkedQueue<net.minecraft.world.entity.raid.Raider> raiderSpawnQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader> traderSpawnQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Villager> villagerSpawnQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ZombieVillager> zombieVillagerSpawnQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Zombie> zombieSpawnList = new ConcurrentLinkedQueue<>();

    public static SpawnQueue getInstance() {
        return INSTANCE;
    }

    public boolean isQueuedRaider(Entity entity) {
        return entity instanceof net.minecraft.world.entity.raid.Raider raider
                && raiderSpawnQueue.contains(raider);
    }

    public static boolean shouldGetConverted(Entity entity) {
        if (Config.getInstance().fractionOfVanillaVillages <= 0) {
            return true;
        } else {
            int i = Nationality.get((ServerLevel) entity.level()).getRegionId(entity.blockPosition());
            return Math.floorMod(i, 100) >= Config.getInstance().fractionOfVanillaVillages * 100.0;
        }
    }

    public void tick() {
        com.ancientkira.mca.MCA.getServer().ifPresent(BulkRevival::tick);
        com.ancientkira.mca.MCA.getServer().ifPresent(server ->
                com.ancientkira.mca.server.world.data.VillagerTrackerManager.get(server.overworld()).tickRescueRequests(server));
        convertWanderingTrader();
        net.minecraft.world.entity.raid.Raider pillager = raiderSpawnQueue.poll();
        if (pillager != null && raiderProfession(pillager) == null) {
            pillager.level().addFreshEntity(pillager);
            pillager = null;
        }
        if (pillager != null) {
            ServerLevel world = (ServerLevel) pillager.level();
            VillagerEntityMCA person = VillagerFactory.newVillager(world)
                    .withUUID(pillager.getUUID())
                    .withName(pillager.hasCustomName() ? pillager.getName().getString() : null)
                    .withAge(0).withPosition(pillager)
                    .withProfession(raiderProfession(pillager))
                    .build(EntitySpawnReason.CONVERSION);
            person.setUUID(pillager.getUUID());
            // Vanilla raiders have no MCA appearance to preserve during conversion.
            person.initialize(EntitySpawnReason.EVENT);
            person.finalizeSpawn(world, world.getCurrentDifficultyAt(person.blockPosition()), EntitySpawnReason.CONVERSION, null);
            person.setProfession(raiderProfession(pillager));
            person.setYRot(pillager.getYRot());
            person.setXRot(pillager.getXRot());
            person.setDeltaMovement(pillager.getDeltaMovement());
            person.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(pillager.getMaxHealth());
            person.setHealth(pillager.getHealth());
            copyPastaIntensifies(person, pillager);
            pillager.getActiveEffects().forEach(effect -> person.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect)));
            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                net.minecraft.world.item.ItemStack stack = pillager.getItemBySlot(slot).copy();
                person.setItemSlot(slot, stack);
                if (!stack.isEmpty()) person.getInventory().addItem(stack.copy());
            }
            if (world.addFreshEntity(person)) {
                PillagerRaids.transfer(pillager, person);
                Entity vehicle = pillager.getVehicle();
                pillager.stopRiding();
                if (vehicle != null) person.startRiding(vehicle);
                for (Entity passenger : java.util.List.copyOf(pillager.getPassengers())) passenger.startRiding(person);
                pillager.discard();
            } else {
                raiderSpawnQueue.add(pillager);
            }
        }

        // After testing with 10k chunk radius with chunky pregen it seems the checks are not needed.
        // The queue system did not work properly and would build up overtime if villages arnt loaded near the player, and would clear on server stop resulting in empty villages anyways.
        // The chunk loaded check apparently does not function properly in this context, this is shown by villagers spawning fine without it, but NEVER spawn with it if far away and JUST generated.
        Villager ve = villagerSpawnQueue.poll();
        if (ve != null) {
            ve.discard();
            VillagerEntityMCA villager = VillagerFactory.newVillager(ve.level())
                    .withName(ve.hasCustomName() ? ve.getName().getString() : null)
                    .withGender(Gender.getRandom())
                    .withAge(ve.getAge())
                    .withPosition(ve)
                    .withType(ve.getVillagerData().type().value())
                    .withProfession(ve.getVillagerData().profession().value(), ve.getVillagerData().level(), ve.getOffers())
                    .spawn(((IVillagerEntity) ve).mca$getSpawnReason());

            copyPastaIntensifies(villager, ve);
        }

        ZombieVillager zve = zombieVillagerSpawnQueue.poll();
        if (zve != null) {
            zve.discard();
            ZombieVillagerEntityMCA villager = ZombieVillagerFactory.newVillager(zve.level())
                    .withName(zve.hasCustomName() ? zve.getName().getString() : null)
                    .withGender(Gender.getRandom())
                    .withPosition(zve)
                    .withType(zve.getVillagerData().type().value())
                    .withProfession(zve.getVillagerData().profession().value(), zve.getVillagerData().level())
                    .spawn(((IVillagerEntity) zve).mca$getSpawnReason());

            copyPastaIntensifies(villager, zve);
        }

        Zombie ze = zombieSpawnList.poll();
        if (ze != null) {
            ze.discard();
            ZombieVillagerEntityMCA villager = ZombieVillagerFactory.newVillager(ze.level())
                    .withName(ze.hasCustomName() ? ze.getName().getString() : null)
                    .withGender(Gender.getRandom())
                    .withPosition(ze)
                    .withType(BuiltInRegistries.VILLAGER_TYPE.getOrThrow(VillagerType.byBiome(ze.level().getBiome(ze.blockPosition()))).value())
                    .withProfession(BuiltInRegistries.VILLAGER_PROFESSION.getRandom(ze.getRandom()).map(Holder::value).orElseGet(() -> BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE).value()))
                    .spawn(EntitySpawnReason.NATURAL);

            copyPastaIntensifies(villager, ze);
        }
    }

    private void convertWanderingTrader() {
        var trader = traderSpawnQueue.poll();
        if (trader == null) return;
        ServerLevel world = (ServerLevel) trader.level();
        if (!Config.getInstance().overwriteOriginalWanderingTraders) {
            world.addFreshEntity(trader);
            return;
        }
        VillagerEntityMCA person = VillagerFactory.newVillager(world)
                .withUUID(trader.getUUID())
                .withName(trader.hasCustomName() ? trader.getName().getString() : null)
                .withAge(0).withPosition(trader)
                .withProfession(com.ancientkira.mca.registry.ProfessionsMCA.WANDERING_TRADER)
                .build(EntitySpawnReason.CONVERSION);
        person.initialize(EntitySpawnReason.EVENT);
        person.finalizeSpawn(world, world.getCurrentDifficultyAt(person.blockPosition()), EntitySpawnReason.CONVERSION, null);
        person.setProfession(com.ancientkira.mca.registry.ProfessionsMCA.WANDERING_TRADER);
        person.setOffers(trader.getOffers().copy());
        person.setDespawnDelay(trader.getDespawnDelay());
        person.setYRot(trader.getYRot());
        person.setXRot(trader.getXRot());
        person.setDeltaMovement(trader.getDeltaMovement());
        person.setHealth(trader.getHealth());
        copyPastaIntensifies(person, trader);
        trader.getActiveEffects().forEach(effect -> person.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect)));
        if (world.addFreshEntity(person)) {
            person.adoptTraderLlamas(trader);
            Entity vehicle = trader.getVehicle();
            trader.stopRiding();
            if (vehicle != null) person.startRiding(vehicle);
            for (Entity passenger : java.util.List.copyOf(trader.getPassengers())) passenger.startRiding(person);
            trader.discard();
        } else traderSpawnQueue.add(trader);
    }

    private void copyPastaIntensifies(PathfinderMob villager, PathfinderMob entity) {
        if (entity.isPersistenceRequired()) {
            villager.setPersistenceRequired();
        }
        if (entity.isInvulnerable()) {
            villager.setInvulnerable(true);
        }
        if (entity.isNoAi()) {
            villager.setNoAi(true);
        }

        for (String tag : entity.entityTags()) {
            villager.addTag(tag);
        }
    }

    public boolean addVillager(Entity entity) {
        if (entity.getClass() == net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader.class
                && Config.getInstance().overwriteOriginalWanderingTraders && entity.level() instanceof ServerLevel
                && !Config.getInstance().villagerDimensionBlacklist.contains(entity.level().dimension().identifier().toString())) {
            var trader = (net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader) entity;
            return traderSpawnQueue.contains(trader) || traderSpawnQueue.add(trader);
        }
        if (entity instanceof net.minecraft.world.entity.raid.Raider raider
                && raiderProfession(raider) != null && entity.level() instanceof ServerLevel
                && !raiderSpawnQueue.contains(raider)) {
            return raiderSpawnQueue.add(raider);
        }
        if (entity instanceof IVillagerEntity villagerEntity && !handlesSpawnReason(villagerEntity.mca$getSpawnReason())) {
            return false;
        }
        if (Config.getInstance().villagerDimensionBlacklist.contains(entity.level().dimension().identifier().toString())) {
            return false;
        }
        if (Config.getInstance().overwriteOriginalVillagers
            && (entity.getClass().equals(Villager.class) ||
                Config.getInstance().moddedVillagerWhitelist.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()) && entity instanceof Villager)
            && shouldGetConverted(entity)
            && !villagerSpawnQueue.contains(entity)) {
            return villagerSpawnQueue.add((Villager) entity);
        }
        if (Config.getInstance().overwriteOriginalZombieVillagers
            && (entity.getClass().equals(ZombieVillager.class) ||
                Config.getInstance().moddedZombieVillagerWhitelist.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()) && entity instanceof ZombieVillager)
            && Config.getInstance().fractionOfVanillaZombies < entity.getRandom().nextFloat()
            && !zombieVillagerSpawnQueue.contains(entity)) {
            return zombieVillagerSpawnQueue.add((ZombieVillager) entity);
        }
        if (Config.getInstance().overwriteAllZombiesWithZombieVillagers
            && entity.getClass().equals(Zombie.class)
            && !zombieSpawnList.contains(entity)) {
            return zombieSpawnList.add((Zombie) entity);
        }
        return false;
    }

    public static VillagerProfession raiderProfession(net.minecraft.world.entity.raid.Raider raider) {
        Config config = Config.getInstance();
        Class<?> type = raider.getClass();
        if (type == net.minecraft.world.entity.monster.illager.Pillager.class && config.overwriteOriginalPillagers)
            return com.ancientkira.mca.registry.ProfessionsMCA.PILLAGER;
        if (type == net.minecraft.world.entity.monster.illager.Vindicator.class && config.overwriteOriginalVindicators)
            return com.ancientkira.mca.registry.ProfessionsMCA.VINDICATOR;
        if (type == net.minecraft.world.entity.monster.illager.Evoker.class && config.overwriteOriginalEvokers)
            return com.ancientkira.mca.registry.ProfessionsMCA.EVOKER;
        if (type == net.minecraft.world.entity.monster.Witch.class && config.overwriteOriginalWitches)
            return com.ancientkira.mca.registry.ProfessionsMCA.WITCH;
        return null;
    }

    private boolean handlesSpawnReason(EntitySpawnReason reason) {
        return Config.getInstance().allowedSpawnReasons.contains(reason.name().toLowerCase(Locale.ROOT));
    }

    public void convert(Villager villager) {
        villagerSpawnQueue.add(villager);
    }
}
