package com.ancientkira.mca.client.book;

import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import java.util.Map;
import java.util.HashMap;

/** Bundled English fallback also works before client languages exist, including server metadata. */
public final class BookText {
    private BookText() {}
    private static final Map<String,String> ENGLISH=load();
    private static Map<String,String> load(){
        Map<String,String> result=new HashMap<>();
        for(String namespace:new String[]{"mca","mca_books"}){
            try(var stream=BookText.class.getResourceAsStream("/assets/"+namespace+"/lang/en_us.json")){
                if(stream==null)continue;
                try(var reader=new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)){
                    JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(e->result.put(e.getKey(),e.getValue().getAsString()));
                }
            }catch(java.io.IOException exception){throw new IllegalStateException("Cannot read bundled MCA book language",exception);}
        }
        return Map.copyOf(result);
    }
    public static net.minecraft.network.chat.MutableComponent translated(String key){return Component.translatableWithFallback(key,ENGLISH.getOrDefault(key,key));}
    public static Component paper(Component value){
        if(value.getContents() instanceof TranslatableContents text && text.getKey().startsWith("mca.books.")) {
            String english=ENGLISH.getOrDefault(text.getKey(),value.getString());
            return Component.literal(english.replaceAll("(?i)§[0-9a-f]","")).withStyle(net.minecraft.ChatFormatting.BLACK);
        }
        return value.copy().withStyle(net.minecraft.ChatFormatting.BLACK);
    }
    public static String title(String name){
        return ENGLISH.getOrDefault("item.mca.book_"+name,ENGLISH.getOrDefault("item.mca."+name,name.replace('_',' ')));
    }
    public static String author(Component value){
        if(value==null)return "MCA";
        return value.getContents() instanceof TranslatableContents text?ENGLISH.getOrDefault(text.getKey(),value.getString()):value.getString();
    }
}
