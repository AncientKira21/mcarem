package com.ancientkira.mca.resources;

import com.google.gson.JsonElement;
import com.ancientkira.mca.MCA;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.*;

public class Supporters extends SimpleJsonResourceReloadListener<JsonElement> {
    public static final Identifier ID = MCA.locate("api/supporters");
    static final RandomSource rng = RandomSource.create();
    private static Supporters INSTANCE;
    private static final Map<String,List<String>> BUNDLED_GROUPS=new java.util.concurrent.ConcurrentHashMap<>();
    private final List<String> supporters = new ArrayList<>();
    private final Map<String, List<String>> supporterGroups = new HashMap<>();

    public Supporters() {
        super(ExtraCodecs.JSON, FileToIdConverter.json(ID.getPath()));
        INSTANCE = this;
    }

    public static String getRandomSupporter() {
        return INSTANCE.pickSupporter();
    }

    public static List<String> getSupporterGroup(String group) {
        if(INSTANCE!=null)return INSTANCE.supporterGroups.getOrDefault(group,List.of());
        // Dedicated servers do not load client resource listeners. Use the bundled credits there.
        return BUNDLED_GROUPS.computeIfAbsent(group,key -> {
            Identifier id=Identifier.parse(key);
            try(var stream=Supporters.class.getResourceAsStream("/assets/"+id.getNamespace()+"/api/supporters/"+id.getPath()+".json")) {
                if(stream==null)return List.of();
                try(var reader=new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)) {
                    var names=new ArrayList<String>();
                    for(var entry:com.google.gson.JsonParser.parseReader(reader).getAsJsonArray())names.add(entry.getAsString());
                    return List.copyOf(names);
                }
            } catch(java.io.IOException | RuntimeException exception){return List.of();}
        });
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager, ProfilerFiller profiler) {
        supporters.clear();
        supporterGroups.clear();
        for (Map.Entry<Identifier, JsonElement> pair : prepared.entrySet()) {
            List<String> strings = supporterGroups.computeIfAbsent(pair.getKey().toString(), x -> new LinkedList<>());
            for (JsonElement e : pair.getValue().getAsJsonArray()) {
                supporters.add(e.getAsString());
                strings.add(e.getAsString());
            }
        }
    }

    public String pickSupporter() {
        return PoolUtil.pickOne(supporters, "someone", rng);
    }
}
