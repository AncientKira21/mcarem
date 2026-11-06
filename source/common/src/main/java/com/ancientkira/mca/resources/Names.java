package com.ancientkira.mca.resources;

import com.google.gson.JsonElement;
import com.ancientkira.mca.Config;
import com.ancientkira.mca.MCA;
import com.ancientkira.mca.entity.ai.relationship.Gender;
import com.ancientkira.mca.server.world.data.Nationality;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class Names extends SimpleJsonResourceReloadListener<JsonElement> {
    public static final Map<String, Map<Gender, WeightedPool<String>>> NAMES_MAP = new HashMap<>();
    public static final List<String> REGION_NAMES = new LinkedList<>();
    public static final Identifier ID = MCA.locate("mca_names");
    static final RandomSource random = RandomSource.create();

    public Names() {
        super(ExtraCodecs.JSON, FileToIdConverter.json(ID.getPath()));
    }

    public static String getCitizenNation(Entity entity) {
        if (NAMES_MAP.isEmpty()) return "modernusa";
        if (REGION_NAMES.isEmpty()) return NAMES_MAP.keySet().iterator().next();
        if (Config.getInstance().useModernUSANamesOnly) {
            return "modernusa";
        } else {
            // Favor the nearest player's selected language while retaining some regional variety.
            if (entity.level() instanceof ServerLevel world) {
                var player = entity instanceof net.minecraft.server.level.ServerPlayer self ? self
                        : world.players().stream().min(Comparator.comparingDouble(p -> p.distanceToSqr(entity))).orElse(null);
                Nationality nationality = Nationality.get(world);
                if (player != null) nationality.rememberNameLanguage(player.clientInformation().language());
                String preferred = regionForLanguage(player == null ? nationality.getNameLanguage() : player.clientInformation().language());
                if (preferred != null && NAMES_MAP.containsKey(preferred) && random.nextFloat() < 0.85F) return preferred;
            }
            int i = Nationality.get((ServerLevel) entity.level()).getRegionId(entity.blockPosition());
            return REGION_NAMES.get(Math.floorMod(i, REGION_NAMES.size()));
        }
    }

    public static String regionForLanguage(String language) {
        String code = language.toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (code.split("_")[0]) {
            case "en" -> code.equals("en_gb") ? "greatbritain" : "modernusa";
            case "de" -> "germany"; case "fr" -> "france"; case "es" -> "spain";
            case "pt" -> "portugal"; case "it" -> "italy"; case "nl" -> "thenetherlands";
            case "sv" -> "sweden"; case "da" -> "denmark"; case "nb", "nn", "no" -> "norway";
            case "fi" -> "finland"; case "is" -> "iceland"; case "pl" -> "poland";
            case "cs" -> "czechrepublic"; case "sk" -> "slovakia"; case "hu" -> "hungary";
            case "ro" -> "romania"; case "bg" -> "bulgaria"; case "el" -> "greece";
            case "ru" -> "russia"; case "uk" -> "ukraine"; case "be" -> "belarus";
            case "sr" -> "serbia"; case "hr" -> "croatia"; case "bs" -> "bosnia";
            case "sl" -> "slovenia"; case "sq" -> "albania"; case "mk" -> "macedonia";
            case "tr" -> "turkey"; case "ar" -> "arabia"; case "he" -> "israel";
            case "zh" -> "china"; case "ja" -> "japan"; case "ko" -> "korea";
            case "vi" -> "vietnam"; case "hi" -> "india"; case "et" -> "estonia";
            case "lv" -> "latvia"; case "lt" -> "lithuania"; case "ka" -> "georgia";
            case "hy" -> "armenia"; case "az" -> "azerbaijan"; case "kk" -> "kazakhstan";
            default -> null;
        };
    }

    public static String pickCitizenName(@NotNull Gender gender, Entity entity) {
        return pickCitizenNameInRegion(gender, NAMES_MAP.isEmpty() ? null : getCitizenNation(entity));
    }

    public static String pickCitizenNameInRegion(@NotNull Gender gender, String region) {
        if (NAMES_MAP.isEmpty()) return "Unnamed";
        Map<Gender, WeightedPool<String>> pools = NAMES_MAP.get(region);
        if (pools == null) pools = NAMES_MAP.values().iterator().next();
        WeightedPool<String> pool = pools.get(gender.binary());
        return pool == null ? "Unnamed" : pool.pickOne();
    }

    public static String pickCitizenName(@NotNull Gender gender) {
        return NAMES_MAP.isEmpty() ? "Unnamed" : NAMES_MAP.get(REGION_NAMES.get(random.nextInt(REGION_NAMES.size()))).get(gender.binary()).pickOne();
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager, ProfilerFiller profiler) {
        NAMES_MAP.clear();
        for (Map.Entry<Identifier, JsonElement> entry : prepared.entrySet()) {
            String[] split = entry.getKey().getPath().split("/");
            Gender gender = Gender.byName(split[1]);

            Map<Gender, WeightedPool<String>> map = NAMES_MAP.computeIfAbsent(split[0], a -> new HashMap<>());

            WeightedPool.Mutable<String> names = new WeightedPool.Mutable<>("?");
            for (Map.Entry<String, JsonElement> elementEntry : entry.getValue().getAsJsonObject().entrySet()) {
                names.add(elementEntry.getKey(), (float) Math.pow(elementEntry.getValue().getAsInt(), 0.5));
            }

            map.put(gender, names);
        }

        REGION_NAMES.clear();
        Arrays.stream(NAMES_MAP.keySet().toArray()).sorted().forEach(n -> REGION_NAMES.add((String) n));
    }
}
