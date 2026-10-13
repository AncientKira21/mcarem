package com.ancientkira.mca.server;

import com.ancientkira.mca.server.world.data.PlayerSaveData;
import com.ancientkira.mca.MCA;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.registry.ProfessionsMCA;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;
import java.util.Optional;

public final class RaiderSocial {
    private RaiderSocial() { }
    private static final java.util.Map<Player, VillagerEntityMCA> OPEN_MENUS = new java.util.WeakHashMap<>();

    public static boolean isMenuOpen(Player player) {
        var person = OPEN_MENUS.get(player);
        if (person == null) return false;
        if (!player.isAlive() || player.isRemoved() || !person.isAlive() || person.level() != player.level()
                || person.distanceToSqr(player) > 16 * 16
                || person.getInteractions().getInteractingPlayer().orElse(null) != player) {
            OPEN_MENUS.remove(player);
            return false;
        }
        return true;
    }

    public static void onMenuClosed(VillagerEntityMCA person, Player player) {
        OPEN_MENUS.remove(player, person);
    }

    public static boolean isFriendly(VillagerEntityMCA person, Player player) {
        var memory = person.getVillagerBrain().getMemoriesForPlayer(player);
        return memory.getRaiderDisposition() > 0 || memory.getRaiderDisposition() == 0 && memory.getHearts() > 0;
    }

    public static boolean isFamily(VillagerEntityMCA raider, LivingEntity other) {
        var family = raider.getRelationships().getFamilyEntry();
        return family.isRelative(other.getUUID()) || family.partner().equals(other.getUUID());
    }

    public static boolean isAngryAt(VillagerEntityMCA raider, Player player) {
        if (player.isCreative() || player.isSpectator() || isFamily(raider, player) || isMenuOpen(player)) return false;
        int hearts = raider.getVillagerBrain().getMemoriesForPlayer(player).getHearts();
        if (isFriendly(raider, player)) return false;
        if (!(player instanceof ServerPlayer serverPlayer)) return hearts < 0;
        var data = PlayerSaveData.get(serverPlayer);
        return raider.getVillagerBrain().getMemoriesForPlayer(player).getRaiderDisposition() < 0
                || hearts < 0 || data.hasBefriendedVillager();
    }

    public static void onMenuOpened(VillagerEntityMCA raider, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !ProfessionsMCA.isRaider(raider.getProfession())) return;
        OPEN_MENUS.put(player, raider);
        for (var world : serverPlayer.level().getServer().getAllLevels()) {
            for (var entity : world.getAllEntities()) {
                if (entity instanceof VillagerEntityMCA other && ProfessionsMCA.isRaider(other.getProfession())) {
                    stopAttackingPlayer(other, player);
                }
            }
        }
    }

    private static void stopAttackingPlayer(VillagerEntityMCA raider, Player player) {
        if (raider.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).orElse(null) == player) {
            raider.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            raider.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            raider.getNavigation().stop();
            RaiderCombat.clear(raider);
        }
        if (raider.getBrain().getMemoryInternal(MemoryModuleType.HURT_BY_ENTITY).orElse(null) == player) {
            raider.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
        }
    }

    public static void onInteraction(VillagerEntityMCA person, ServerPlayer player, int hearts) {
        if (ProfessionsMCA.isRaider(person.getProfession())) {
            person.getVillagerBrain().getMemoriesForPlayer(player).setRaiderDisposition(hearts > 0 ? 1 : -1);
            if (hearts > 0) stopAttackingPlayer(person, player);
        } else if (hearts > 0) PlayerSaveData.get(player).recordVillagerFriendship();
    }

    public static void restoreKnownFriendships(VillagerEntityMCA person) {
        if (ProfessionsMCA.isRaider(person.getProfession())) return;
        for (var memory : person.getVillagerBrain().getMemories().values()) {
            if (memory == null || memory.getHearts() <= 0) continue;
            var data = PlayerSaveData.get((net.minecraft.server.level.ServerLevel) person.level(), memory.getPlayerUUID());
            if (!data.hasBefriendedVillager()) data.recordVillagerFriendship();
        }
    }

    public static void redeem(VillagerEntityMCA person) {
        if (!ProfessionsMCA.isRaider(person.getProfession())) return;
        person.setProfession(net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION
                .getValueOrThrow(net.minecraft.world.entity.npc.villager.VillagerProfession.NONE));
        person.setDespawnDelay(0);
        person.getResidency().seekHome();
    }

    public static void onMarriage(VillagerEntityMCA person, net.minecraft.world.entity.Entity spouse, boolean announce) {
        boolean raider = ProfessionsMCA.isRaider(person.getProfession());
        boolean spouseRaider = spouse instanceof VillagerEntityMCA other && ProfessionsMCA.isRaider(other.getProfession());
        if (raider && spouseRaider) {
            if (announce && com.ancientkira.mca.Config.getInstance().villagerMarriageNotification) {
                person.level().getServer().getPlayerList().broadcastSystemMessage(
                        net.minecraft.network.chat.Component.translatable("events.marry", person.getName(), spouse.getName()), false);
            }
        } else if (spouse instanceof Player || spouse instanceof VillagerEntityMCA) {
            if (raider) redeem(person);
            if (spouseRaider) redeem((VillagerEntityMCA) spouse);
        }
    }

    public static void tickKidnapping(VillagerEntityMCA captor) {
        if (!com.ancientkira.mca.Config.getInstance().enableRaiderKidnapping || captor.isNoAi()
                || captor.isPeacefulRaider() || captor.tickCount % 10 != 0) return;
        var target = captor.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (target instanceof VillagerEntityMCA victim && !victim.isKidnapped()
                && victim.getHealth() <= victim.getMaxHealth() * 0.25F
                && captor.distanceToSqr(victim) <= 4 && captor.hasLineOfSight(victim)) {
            tryKidnap(captor, victim);
        }
    }

    public static boolean tryKidnap(VillagerEntityMCA captor, VillagerEntityMCA victim) {
        if (com.ancientkira.mca.Config.getInstance().enableRaiderKidnapping && !captor.isNoAi()
                && victim.beginKidnapping(captor)) {
            captor.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            captor.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            captor.getNavigation().stop();
            RaiderCombat.clear(captor);
            return true;
        }
        return false;
    }

    public static boolean tryKidnapFromAttack(net.minecraft.world.entity.Entity attacker, VillagerEntityMCA victim) {
        if (attacker instanceof VillagerEntityMCA captor) return tryKidnap(captor, victim);
        if (attacker instanceof net.minecraft.world.entity.monster.Vex vex && vex.isAlive() && !vex.isNoAi()
                && vex.getOwner() instanceof VillagerEntityMCA owner && owner.isAlive()
                && tryKidnap(owner, victim)) {
            vex.setTarget(null);
            vex.setIsCharging(false);
            vex.getNavigation().stop();
            return true;
        }
        return false;
    }

    public static void tickSelfRedemption(VillagerEntityMCA person) {
        if (person.tickCount % 1200 != 0 || person.isPeacefulRaider() || person.isNoAi()
                || PillagerRaids.getRaid(person).isPresent()
                || person.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                || person.getInteractions().getInteractingPlayer().isPresent()
                || person.getResidency().getRegistryVillage().isEmpty()) return;
        double dailyChance = Math.max(0, Math.min(1, com.ancientkira.mca.Config.getInstance().raiderSelfRedemptionChancePerDay));
        double chance = 1 - Math.pow(1 - dailyChance, 1.0 / 20);
        if (person.getRandom().nextDouble() < chance) redeem(person);
    }

    public static boolean isDefender(LivingEntity entity) {
        return entity instanceof IronGolem || entity instanceof VillagerEntityMCA person && person.isGuard();
    }

    public static Optional<LivingEntity> pursuer(VillagerEntityMCA raider) {
        return raider.getBrain().getMemoryInternal(MemoryModuleType.NEAREST_LIVING_ENTITIES)
                .flatMap(list -> list.stream().filter(other -> isDefender(other) && other.isAlive()
                                && other.distanceToSqr(raider) <= 32 * 32 && isPursuing(other, raider))
                        .min(Comparator.comparingDouble(other -> other.getAttributeValue(Attributes.MOVEMENT_SPEED))));
    }

    private static boolean isPursuing(LivingEntity defender, VillagerEntityMCA raider) {
        if (defender instanceof Mob mob && mob.getTarget() == raider) return true;
        return defender instanceof VillagerEntityMCA person
                && person.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).orElse(null) == raider;
    }

    public static void updatePursuitSpeed(VillagerEntityMCA raider) {
        var speed = raider.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(MCA.locate("raider_pursuit_speed"));
        if (raider.isPeacefulRaider()) return;
        pursuer(raider).ifPresent(defender -> {
            double current = speed.getValue();
            double limit = defender.getAttributeValue(Attributes.MOVEMENT_SPEED) * 0.8;
            if (current > limit && current > 0) {
                speed.addTransientModifier(new AttributeModifier(MCA.locate("raider_pursuit_speed"),
                        limit / current - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        });
    }

}
