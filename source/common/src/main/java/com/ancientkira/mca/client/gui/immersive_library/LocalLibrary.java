package com.ancientkira.mca.client.gui.immersive_library;

import com.ancientkira.mca.MCA;
import com.ancientkira.mca.client.gui.SkinLibraryScreen.SkinType;
import com.ancientkira.mca.client.gui.immersive_library.types.LiteContent;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;

/** Instance-local imports use negative cache IDs and are never requested from the online service. */
public final class LocalLibrary {
    private static final Path INDEX=Path.of("immersive_library","local.json");
    private static List<LiteContent> entries;
    private static final Path OVERRIDE_FILE=INDEX.resolveSibling("local-selections.json");
    private static JsonObject selections;
    private static final ThreadLocal<Boolean> PREVIEW_SELECTION=ThreadLocal.withInitial(()->false);
    public static <T> T withoutSelections(java.util.function.Supplier<T> draw) {
        boolean before=PREVIEW_SELECTION.get();PREVIEW_SELECTION.set(true);
        try{return draw.get();}finally{PREVIEW_SELECTION.set(before);}
    }
    private LocalLibrary() {}
    private static JsonObject selections() {
        if(selections==null) {
            selections=new JsonObject();
            if(Files.exists(OVERRIDE_FILE))try {selections=JsonParser.parseString(Files.readString(OVERRIDE_FILE)).getAsJsonObject();}
            catch(Exception error){MCA.LOGGER.warn("Could not read local appearance selections",error);}
        }
        return selections;
    }
    public static String selected(java.util.UUID entity,SkinType type,String fallback) {
        if(PREVIEW_SELECTION.get())return fallback;
        var row=selections().getAsJsonObject(entity.toString());
        return row!=null && row.has(type.tag())?row.get(type.tag()).getAsString():fallback;
    }
    public static void select(java.util.UUID entity,SkinType type,String id) {
        var all=selections();var row=all.getAsJsonObject(entity.toString());
        if(row==null){row=new JsonObject();all.add(entity.toString(),row);}
        if(id==null)row.remove(type.tag());else row.addProperty(type.tag(),id);
        if(id!=null && (type==SkinType.CLOTHING || type==SkinType.HAIR))row.remove(SkinType.CLASSIC.tag());
        persistSelections();
    }
    public static void clear(java.util.UUID entity) {selections().remove(entity.toString());persistSelections();}
    private static void persistSelections() {
        try {Files.createDirectories(INDEX.getParent());Files.writeString(OVERRIDE_FILE,selections().toString());}
        catch(IOException error){MCA.LOGGER.warn("Could not save local appearance selection",error);}
    }

    public static List<LiteContent> contents() {
        if(entries==null) {
            entries=new ArrayList<>();
            if(Files.exists(INDEX))try {
                for(var row:JsonParser.parseString(Files.readString(INDEX)).getAsJsonArray()) {
                    var j=row.getAsJsonObject();
                    if(j.get("id").getAsInt()>=0)continue;
                    entries.add(new LiteContent(j.get("id").getAsInt(),0,j.get("author").getAsString(),0,
                            Set.of("mcarem", j.get("kind").getAsString()),j.get("title").getAsString(),1));
                }
            }catch(Exception error){MCA.LOGGER.warn("Could not read local appearance library",error);}
        }
        return List.copyOf(entries);
    }
    public static void save(Workspace workspace,String author)throws IOException {
        contents();
        int id=entries.stream().mapToInt(LiteContent::contentid).min().orElse(0)-1;
        if(workspace.skinType==null || workspace.title.isBlank())throw new IOException("Choose a content type and name first.");
        int region = workspace.skinType.coverageRegion();
        if (region != 0 && (AccessoryTextureCoverage.classify(workspace.currentImage.getWidth(), workspace.currentImage.getHeight(),
                (x, y) -> (workspace.currentImage.getPixel(x, y) >>> 24) & 255) & region) == 0) {
            throw new IOException("The texture extends outside the selected accessory's head or face region.");
        }
        if(workspace.skinType==SkinType.EYES && !EyeTextureCoverage.qualifies(workspace.currentImage.getWidth(),workspace.currentImage.getHeight(),
                (x,y)->(workspace.currentImage.getPixel(x,y)>>>24)&255)) throw new IOException("Eyes must have pixels only on the front of the face.");
        if(workspace.skinType==SkinType.CLASSIC && !ClassicSkinCoverage.qualifies(workspace.currentImage.getWidth(),workspace.currentImage.getHeight(),
                (x,y)->(workspace.currentImage.getPixel(x,y)>>>24)&255))
            throw new IOException("Classic skins must fully cover the base head, torso, arms and legs.");
        Files.createDirectories(INDEX.getParent());
        workspace.currentImage.writeToFile(INDEX.getParent().resolve(id+".png"));
        Files.writeString(INDEX.getParent().resolve(id+".json"),new Gson().toJson(new com.ancientkira.mca.client.resources.SkinMeta(workspace.profession,workspace.temperature,workspace.gender.getId(),(float)workspace.chance,workspace.untinted)));
        Files.writeString(INDEX.getParent().resolve(id+".version"),"1");
        entries.add(new LiteContent(id,0,author,0,Set.of("mcarem", workspace.skinType.tag()),workspace.title,1));
        JsonArray out=new JsonArray();
        for(var entry:entries){JsonObject j=new JsonObject();j.addProperty("id",entry.contentid());j.addProperty("title",entry.title());j.addProperty("author",entry.username());j.addProperty("kind",SkinType.of(entry).tag());out.add(j);}
        Files.writeString(INDEX,out.toString());
        com.ancientkira.mca.client.resources.ClientSkinCatalog.refreshEditorEntries();
    }
}
