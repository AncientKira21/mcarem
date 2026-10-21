package com.ancientkira.mca.resources.data.skin;

import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import com.ancientkira.mca.entity.ai.relationship.Gender;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

public class Clothing extends SkinListEntry {
    public static final StreamCodec<ByteBuf, Clothing> STREAM_CODEC = StreamCodec.of(
            (out, value) -> {
                JsonObject json = value.toJson();
                json.addProperty("id", value.identifier);
                ByteBufCodecs.STRING_UTF8.encode(out, json.toString());
            },
            (in) -> {
                String json = ByteBufCodecs.STRING_UTF8.decode(in);
                JsonObject parsed = GsonHelper.parse(json);
                return new Clothing(parsed.get("id").getAsString(), parsed);
            }
    );

    @Nullable
    public final String profession;
    public final String displayName;
    public final int temperature;
    public final boolean exclude;
    public final boolean onlineAutomatic;
    public final String onlineAuthor;
    public final int onlineVersion;
    public final long onlineAvailableAfter;

    public Clothing(String identifier, @Nullable String profession, int temperature, boolean exclude, Gender gender) {
        this(identifier, profession, temperature, exclude, gender, "");
    }

    public Clothing(String identifier, @Nullable String profession, int temperature, boolean exclude, Gender gender, String displayName) {
        super(identifier, gender, 1.0f);
        this.displayName = displayName == null ? "" : displayName;
        this.profession = profession;
        this.temperature = temperature;
        this.exclude = exclude;
        this.onlineAutomatic = false;
        this.onlineAuthor = "";
        this.onlineVersion = -1;
        this.onlineAvailableAfter = 0;
    }

    public Clothing(String identifier, JsonObject object) {
        super(identifier, object);
        this.displayName = GsonHelper.getAsString(object, "name", GsonHelper.getAsString(object, "title", ""));

        this.profession = !object.has("profession") || object.get("profession").isJsonNull() ? null : GsonHelper.getAsString(object, "profession", null);
        this.exclude = GsonHelper.getAsBoolean(object, "exclude", false);
        this.temperature = GsonHelper.getAsInt(object, "temperature", 0);
        this.onlineAutomatic = GsonHelper.getAsBoolean(object, "onlineAutomatic", false);
        this.onlineAuthor = GsonHelper.getAsString(object, "onlineAuthor", "");
        this.onlineVersion = GsonHelper.getAsInt(object, "onlineVersion", -1);
        this.onlineAvailableAfter = object.has("onlineAvailableAfter") ? object.get("onlineAvailableAfter").getAsLong() : 0;
    }

    public String getDisplayName() {
        if (!displayName.isBlank()) return displayName;
        String path = getPath();
        String[] parts = path.split("/");
        String file = parts[parts.length - 1].replaceFirst("\\.[^.]+$", "");
        if (file.matches("[0-9]+") && parts.length > 1) {
            String category = parts[parts.length - 2];
            file = (category.equals("none") ? "Casual" : category) + " " + file;
        }
        String words = file.replace('_', ' ').replace('-', ' ').strip();
        return words.isEmpty() ? getIdentifier() : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    @Override
    public JsonObject toJson() {
        JsonObject j = super.toJson();
        if (!displayName.isBlank()) j.addProperty("name", displayName);
        j.addProperty("profession", profession);
        j.addProperty("exclude", exclude);
        j.addProperty("temperature", temperature);
        if (onlineAutomatic) {
            j.addProperty("onlineAutomatic", true);
            j.addProperty("onlineAuthor", onlineAuthor);
            j.addProperty("onlineVersion", onlineVersion);
            j.addProperty("onlineAvailableAfter", onlineAvailableAfter);
        }
        return j;
    }
}
