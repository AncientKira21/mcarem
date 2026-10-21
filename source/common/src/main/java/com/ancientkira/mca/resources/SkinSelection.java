package com.ancientkira.mca.resources;

import com.ancientkira.mca.MCA;
import com.ancientkira.mca.entity.VillagerLike;
import com.ancientkira.mca.entity.ai.relationship.AgeState;
import com.ancientkira.mca.entity.ai.relationship.Gender;
import com.ancientkira.mca.resources.data.skin.Clothing;
import com.ancientkira.mca.resources.data.skin.LayeredHair;
import com.ancientkira.mca.resources.data.skin.SkinListEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public final class SkinSelection {
    private SkinSelection() {
    }

    public static boolean matchesGender(SkinListEntry entry, Gender gender) {
        Gender entryGender = entry.getGender();
        return entryGender == Gender.NEUTRAL || gender == Gender.NEUTRAL || gender == Gender.UNASSIGNED || entryGender == gender;
    }

    public static boolean canWearClothing(Clothing clothing, Gender gender) {
        // This shirtless outfit is the sole exception to shared clothing.
        return !clothing.getIdentifier().equals("mca:skins/clothing/normal/male/shepherd/1.png")
                || gender == Gender.MALE;
    }

    public static boolean isEditorClothing(Clothing clothing) {
        return !clothing.exclude || java.util.Set.of("mca:baby", "mca:toddler", "mca:child", "mca:teen")
                .contains(clothing.profession == null ? "" : clothing.profession);
    }

    public static List<Clothing> editorClothing(Collection<Clothing> available, Gender gender) {
        return available.stream()
                .filter(clothing -> canWearClothing(clothing, gender))
                .filter(SkinSelection::isEditorClothing)
                .sorted((a, b) -> SkinListEntry.compareIdentifiers(a.getIdentifier(), b.getIdentifier()))
                .toList();
    }

    public static List<Clothing> clothingForVillager(Collection<Clothing> available, VillagerLike<?> villager, Map<String, String> professionConversions) {
        available = available.stream().filter(c -> com.ancientkira.mca.Config.getInstance().enableSilvernaturalPack
                || !"Silvernatural".equalsIgnoreCase(c.onlineAuthor)).toList();
        Gender gender = villager.getGenetics().getGender();
        String actualProfession = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().profession().value()).toString();
        if (com.ancientkira.mca.Config.getInstance().automaticOnlineClothing
                && villager.getAgeState() != AgeState.BABY && !actualProfession.equals("mca:wandering_trader")) {
            List<Clothing> online = available.stream().filter(clothing -> clothing.onlineAutomatic
                    && clothing.onlineAvailableAfter <= System.currentTimeMillis()
                    && actualProfession.equals(clothing.profession) && !clothing.exclude
                    && canWearClothing(clothing, gender)).toList();
            if (!online.isEmpty()) return online;
        }
        available = available.stream().filter(clothing -> !clothing.onlineAutomatic).toList();
        String agePool = agePool(villager.getAgeState());
        if (agePool != null) {
            return clothingForProfession(available, gender, agePool);
        }

        String profession = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().profession().value()).toString();
        if (profession.equals("mca:wandering_trader")) {
            return clothingForProfession(available, gender, profession);
        }
        List<Clothing> options = clothingForProfession(available, gender, mapProfession(profession, professionConversions));
        return options.isEmpty() ? clothingForProfession(available, gender, mapProfession("minecraft:none", professionConversions)) : options;
    }

    public static List<Clothing> clothingForProfession(Collection<Clothing> available, Gender gender, @Nullable String profession) {
        return available.stream()
                .filter(clothing -> canWearClothing(clothing, gender))
                .filter(clothing -> !clothing.onlineAutomatic
                        || com.ancientkira.mca.Config.getInstance().automaticOnlineClothing
                            && clothing.onlineAvailableAfter <= System.currentTimeMillis()
                            && profession != null && profession.equals(clothing.profession))
                .filter(clothing -> !"mca:wandering_trader".equals(profession)
                        || clothing.getIdentifier().contains("/cultist/"))
                .filter(clothing -> clothing.profession == null
                        || profession == null && !clothing.exclude
                        || profession != null && (clothing.profession.equals(profession) || clothing.profession.equals(profession.replace(":", "."))))
                .sorted((a, b) -> SkinListEntry.compareIdentifiers(a.getIdentifier(), b.getIdentifier()))
                .toList();
    }

    @Nullable
    public static String agePool(AgeState ageState) {
        return switch (ageState) {
            case BABY -> MCA.locate("baby").toString();
            case TODDLER -> MCA.locate("toddler").toString();
            case CHILD, TEEN -> MCA.locate("child").toString();
            default -> null;
        };
    }

    public static String mapProfession(String profession, Map<String, String> professionConversions) {
        return professionConversions.getOrDefault(profession, professionConversions.getOrDefault("default", profession));
    }

    public static <T extends SkinListEntry> List<T> forGender(Collection<T> entries, Gender gender) {
        return entries.stream()
                .filter(entry -> matchesGender(entry, gender))
                .toList();
    }

    public static List<LayeredHair> layeredHair(Collection<LayeredHair> entries, LayeredHair.Category category, Gender gender) {
        return entries.stream()
                .filter(hair -> hair.getCategory() == category)
                .filter(hair -> matchesGender(hair, gender))
                .sorted((a, b) -> SkinListEntry.compareIdentifiers(a.getIdentifier(), b.getIdentifier()))
                .toList();
    }

    public static List<String> sortedIds(Collection<? extends SkinListEntry> entries, Gender gender) {
        return entries.stream()
                .filter(entry -> matchesGender(entry, gender))
                .map(SkinListEntry::getIdentifier)
                .distinct()
                .sorted(SkinListEntry::compareIdentifiers)
                .toList();
    }

    public static Optional<String> pickWeightedId(Collection<? extends SkinListEntry> entries) {
        WeightedPool<String> pool = toPool(entries, "");
        String id = pool.pickOne();
        return MCA.isBlankString(id) ? Optional.empty() : Optional.of(id);
    }

    public static <T extends SkinListEntry> Optional<String> pickDifferentWeightedId(Collection<T> entries, Predicate<T> current) {
        List<T> filtered = entries.stream().filter(current.negate()).toList();
        Optional<String> selected = pickWeightedId(filtered);
        return selected.isPresent() ? selected : pickWeightedId(entries);
    }

    public static Optional<String> pickLayeredHair(Collection<LayeredHair> entries, LayeredHair.Category category) {
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        if (!category.isRequired() && API.getRng().nextFloat() > maxChance(entries)) {
            return Optional.of("");
        }
        return pickWeightedId(entries);
    }

    public static Optional<String> cycleValue(List<String> values, String selected, int offset) {
        if (values.isEmpty()) {
            return Optional.empty();
        }
        int index = values.indexOf(selected);
        int next = index < 0 ? (offset < 0 ? values.size() - 1 : 0) : Math.floorMod(index + offset, values.size());
        return Optional.of(values.get(next));
    }

    public static WeightedPool<String> toPool(Collection<? extends SkinListEntry> entries, String defaultValue) {
        return entries.stream().collect(() -> new WeightedPool.Mutable<>(defaultValue),
                (pool, entry) -> pool.add(entry.getIdentifier(), entry.getChance()),
                (a, b) -> a.entries.addAll(b.entries));
    }

    public static float maxChance(Collection<? extends SkinListEntry> entries) {
        return entries.stream()
                .map(SkinListEntry::getChance)
                .max(Float::compare)
                .orElse(0.0F);
    }
}
