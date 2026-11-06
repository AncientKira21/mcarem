package com.ancientkira.mca.entity.ai.chatAI;

import com.ancientkira.mca.Config;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.util.WorldUtils;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class ChatAI {
    /**
     * Max range to find a villager in
     */
    private static final int VILLAGER_SEARCH_RANGE = 32;

    /**
     * Max time until a conversation is considered invalid
     */
    private static final int CONVERSATION_TIME = 20 * 120;

    /**
     * Max distance until a conversation is considered invalid
     */
    private static final int CONVERSATION_DISTANCE = 16;

    /**
     * Map of villager UUIDs to strategies (i.e. managed by InworldAI or GPT3)
     */
    private static final Map<UUID, ChatAIStrategy> strategies = new ConcurrentHashMap<>();

    /**
     * Current conversation of player. <p>
     * A player can max. have 1 conversation at all times.
     */
    private static final Map<UUID, OpenConversation> currentConversations = new ConcurrentHashMap<>();


    /**
     * Gets an answer for a specific message for a villager from a player with the villager-specific chat strategy
     *
     * @param player   ServerPlayerEntity of the player
     * @param villager VillagerEntityMCA of the villager
     * @param msg      Message in question
     * @return {@code Optional.EMPTY} if answer couldn't be generated, Optional containing answer String otherwise.
     */
    public static Optional<String> answer(ServerPlayer player, VillagerEntityMCA villager, String msg) {
        var server = player.level().getServer();
        ChatAIStrategy strategy = server.submit(() -> {
            if (!canReply(player, villager)) return null;
            currentConversations.put(player.getUUID(), new OpenConversation(
                    villager.getUUID(), villager.level().getGameTime()));
            return computeStrategyIfAbsent(villager.getUUID());
        }).join();
        return strategy == null ? Optional.empty() : strategy.answer(player, villager, msg);
    }

    /**
     * Searches Config for a map entry for UUID, uses Inworld with said entry if found, else GPT3 (default)
     *
     * @param villagerID UUID of villager
     * @return Object implementing the ChatAIStrategy interface
     */
    private static ChatAIStrategy computeStrategyIfAbsent(UUID villagerID) {
        return strategies.computeIfAbsent(villagerID, v -> {
            String inworldResourceName = Config.getInstance().inworldAIResourceNames.getOrDefault(v, "");
            return inworldResourceName.isEmpty() ? new OpenAIChatAI() : new InworldAI(inworldResourceName);
        });
    }

    /**
     * Clears the strategy for a specific villager
     *
     * @param villagerID UUID of the villager
     */
    public static void clearStrategy(UUID villagerID) {
        strategies.remove(villagerID);
    }

    public static boolean canReply(ServerPlayer player, VillagerEntityMCA villager) {
        return Config.getInstance().enableVillagerChatAI && player.isAlive() && villager.isAlive()
                && !villager.isRemoved() && player.level() == villager.level()
                && player.level().getServer().getPlayerList().getPlayer(player.getUUID()) == player
                && villager.distanceToSqr(player) <= VILLAGER_SEARCH_RANGE * VILLAGER_SEARCH_RANGE;
    }

    private static String getName(VillagerEntityMCA villager) {
        return normalizeString(villager.getName().getString());
    }

    /**
     * Checks if the message contains the name of any specific villagers and that villager is nearby. First match.
     * If not, checks if the player has a valid active conversation with a nearby villager.
     *
     * @param player The player in the conversation
     * @param msg    The message
     * @return {@code Optional.Empty} if no valid villager was found, Optional containing the VillagerEntityMCA object otherwise
     */
    public static Optional<VillagerEntityMCA> getVillagerForConversation(ServerPlayer player, String msg) {
        UUID playerUUID = player.getUUID();
        // Get nearby villagers
        List<VillagerEntityMCA> nearbyVillagers = WorldUtils.getCloseEntities(player.level(), player, VILLAGER_SEARCH_RANGE, VillagerEntityMCA.class);

        String normalizedMsg = normalizeString(msg);
        OpenConversation conversation = currentConversations.get(playerUUID);
        Optional<VillagerEntityMCA> current = conversation == null ? Optional.empty() : nearbyVillagers.stream()
                .filter(villager -> conversation.villagerUUID.equals(villager.getUUID()))
                .filter(villager -> isInConversationWith(player, villager))
                .findFirst();

        for (VillagerEntityMCA villager : nearbyVillagers) {
            String nickname = normalizeString(villager.getNickname(playerUUID));
            String name = getName(villager);
            if (addresses(normalizedMsg, nickname) || addresses(normalizedMsg, name)
                    || addresses(normalizedMsg, name.split(" ", 2)[0])) {
                return Optional.of(villager);
            }
        }
        if (current.isPresent()) return current;

        for (VillagerEntityMCA villager : nearbyVillagers) {
            String nickname = normalizeString(villager.getNickname(playerUUID));
            if (containsWholeWord(normalizedMsg, nickname)) return Optional.of(villager);
            for (String part : getName(villager).split(" ")) {
                if (containsWholeWord(normalizedMsg, part)) return Optional.of(villager);
            }
        }

        return Optional.empty();
    }

    private static boolean addresses(String message, String name) {
        return !name.isBlank() && (message.startsWith(name + ",") || message.startsWith(name + ":"));
    }

    private static boolean containsWholeWord(String text, String word) {
        return !word.isBlank() && Pattern.compile("\\b" + Pattern.quote(word) + "\\b")
                .matcher(text)
                .find();
    }

    /**
     * Checks if a player is in a conversation with a villager
     *
     * @param player   ServerPlayerEntity of the player to be checked
     * @param villager VillagerEntityMCA entity of the villager to be checked
     * @return {@code true} if all the following conditions are met: <p>
     * 1. Villager is within {@value CONVERSATION_DISTANCE} blocks of the player<p>
     * 2. Last conversation interaction with this villager wasn't longer than {@value CONVERSATION_TIME} ago
     */
    private static boolean isInConversationWith(ServerPlayer player, VillagerEntityMCA villager) {
        OpenConversation conversation = currentConversations.getOrDefault(player.getUUID(), new OpenConversation(villager.getUUID(), 0L));
        return villager.distanceTo(player) < CONVERSATION_DISTANCE
               && villager.level().getGameTime() < conversation.lastInteractionTime + CONVERSATION_TIME;
    }

    /**
     * Scans the local area in a {@value #VILLAGER_SEARCH_RANGE} block range of the player for a villager with searchName. <p>
     * searchName is {@link #normalizeString normalized}.
     *
     * @param player     ServerPlayerEntity object of the reference player
     * @param searchName Name of the villager
     * @return Optional containing the VillagerEntityMCA of the first villager with the matching name, empty Optional otherwise
     */
    public static Optional<VillagerEntityMCA> findVillagerInArea(ServerPlayer player, String searchName) {
        List<VillagerEntityMCA> entities = WorldUtils.getCloseEntities(player.level(), player, VILLAGER_SEARCH_RANGE, VillagerEntityMCA.class);

        // Get specific villager
        String normalizedSearchName = normalizeString(searchName);

        // Go through list, look for first match for name
        for (VillagerEntityMCA villager : entities) {
            String villagerName = getName(villager);
            if (normalizedSearchName.equals(villagerName)) {
                return Optional.of(villager);
            }
        }
        return Optional.empty();
    }

    /** Finds the nearest MCA villager available to the player for context editing. */
    public static Optional<VillagerEntityMCA> findClosestVillager(ServerPlayer player) {
        return WorldUtils.getCloseEntities(player.level(), player, VILLAGER_SEARCH_RANGE, VillagerEntityMCA.class).stream()
                .min(Comparator.comparingDouble(player::distanceToSqr));
    }

    /**
     * Normalizes the String according to NFD and removes any accents, umlauts, etc.
     *
     * @param string The String to be normalized
     * @see <a href="https://unicode.org/reports/tr15/#Examples">Unicode Normalization Forms</a>
     */
    private static String normalizeString(String string) {
        return com.ancientkira.mca.MCA.normalizeString(string);
    }

    /**
     * Information needed to manage an open conversation.
     *
     * @param villagerUUID        UUID of the villager the conversation is with
     * @param lastInteractionTime Timestamp of the last interaction with the villager
     */
    private record OpenConversation(UUID villagerUUID, Long lastInteractionTime) {
    }

}
