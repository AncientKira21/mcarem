package com.ancientkira.mca.client.gui;

import com.ancientkira.mca.Config;
import com.ancientkira.mca.util.compat.ButtonWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.lang.reflect.*;
import java.util.*;

/** Local configuration editor; server-owned values take effect after reopening the world. */
public final class McaConfigScreen extends Screen {
    private final Screen parent;
    private final List<ButtonWidget> rows = new ArrayList<>();
    private EditBox search;
    private int offset;
    public McaConfigScreen(Screen parent) { super(Component.literal("MCA Remastered Settings")); this.parent = parent; }
    static String label(Field field) {
        if (field.getName().equals("enableSilvernaturalPack")) return "Silvernatural's pack";
        if (field.getName().equals("showOwnLibrarySubmissions")) return "Show my submissions";
        if (field.getName().equals("usePlayerAnimationsForVillagers")) return "Use CPA animations for villagers";
        return field.getName().replaceAll("([a-z0-9])([A-Z])", "$1 $2");
    }
    static Component description(Field field) {
        return Component.translatable("config.mca." + field.getName() + ".description");
    }

    static List<Field> settings() {
        return Arrays.stream(Config.class.getFields()).filter(f -> !Modifier.isStatic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers()))
                .filter(f -> f.getType()==boolean.class || f.getType()==int.class || f.getType()==float.class || f.getType()==double.class
                        || f.getName().equals("onlineTTSModel") || f.getName().equals("voiceboxUrl"))
                .filter(f -> !f.getName().equals("version"))
                .sorted(Comparator.comparing(f -> f.getName().equals("usePlayerAnimationsForVillagers") ? "" : f.getName())).toList();
    }
    @Override protected void init() {
        search=addRenderableWidget(new EditBox(font,width/2-150,34,300,20,Component.translatable("gui.search")));
        search.setHint(Component.translatable("gui.search"));
        search.setResponder(q->{offset=0;refresh();});
        addRenderableWidget(new ButtonWidget(width/2-75,height-27,150,20,Component.translatable("gui.done"),b->onClose()));
        refresh();
    }
    private void refresh() {
        rows.forEach(this::removeWidget); rows.clear();
        String query=search.getValue().toLowerCase(Locale.ROOT).strip();
        var fields=settings().stream().filter(f->(label(f) + " " + description(f).getString()).toLowerCase(Locale.ROOT).contains(query)).toList();
        int count=Math.max(1,(height-112)/23);
        offset=Math.clamp(offset,0,Math.max(0,fields.size()-count));
        for(int i=0;i<count && offset+i<fields.size();i++) {
            Field field=fields.get(offset+i);
            try {
                Object value=field.get(Config.getInstance());
                String text=label(field)+": "+(value instanceof Boolean b ? b?"ON":"OFF":value);
                rows.add(addRenderableWidget(new ButtonWidget(width/2-150,60+i*23,300,20,Component.literal(text),b->{
                    try {
                        if(field.getType()==boolean.class){field.setBoolean(Config.getInstance(),!field.getBoolean(Config.getInstance()));Config.getInstance().save();com.ancientkira.mca.client.resources.ClientSkinCatalog.refreshEditorEntries();refresh();}
                        else minecraft.setScreen(new ValueScreen(this,field));
                    }catch(IllegalAccessException e){throw new IllegalStateException(e);}
                },description(field))));
            }catch(IllegalAccessException e){throw new IllegalStateException(e);}
        }
    }
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){offset+=sy>0?-1:sy<0?1:0;refresh();return true;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){super.extractRenderState(g,x,y,d);g.centeredText(font,title,width/2,13,0xffffffff);g.centeredText(font,Component.literal("Local settings · Reopen world for server changes"),width/2,height-43,0xffaaaaaa);}
    private static final class ValueScreen extends Screen {
        private final Screen parent; private final Field field; private EditBox value; private Component error=Component.empty();
        ValueScreen(Screen parent,Field field){super(Component.literal(label(field)));this.parent=parent;this.field=field;}
        @Override protected void init(){
            value=addRenderableWidget(new EditBox(font,width/2-150,65,300,20,title));value.setMaxLength(500);
            value.setTooltip(net.minecraft.client.gui.components.Tooltip.create(description(field)));
            try{value.setValue(field.get(Config.getInstance()).toString());}catch(IllegalAccessException e){throw new IllegalStateException(e);}
            addRenderableWidget(new ButtonWidget(width/2-155,100,150,20,Component.literal("Save"),b->{
                try {
                    String text=value.getValue().strip();Object parsed;
                    if(field.getType()==int.class) parsed=Integer.valueOf(text);
                    else if(field.getType()==float.class){float n=Float.parseFloat(text);if(!Float.isFinite(n))throw new IllegalArgumentException();parsed=n;}
                    else if(field.getType()==double.class){double n=Double.parseDouble(text);if(!Double.isFinite(n))throw new IllegalArgumentException();parsed=n;}
                    else {
                        if(field.getName().equals("onlineTTSModel")&&!Set.of("default","elevenlabs","voicebox","realtime","player2").contains(text))throw new IllegalArgumentException();
                        if(field.getName().equals("voiceboxUrl")){var uri=java.net.URI.create(text);if(uri.getHost()==null||!Set.of("http","https").contains(uri.getScheme()))throw new IllegalArgumentException();}
                        parsed=text;
                    }
                    field.set(Config.getInstance(),parsed);Config.getInstance().save();onClose();
                }catch(IllegalArgumentException|IllegalAccessException e){error=Component.literal("Invalid value");}
            }));
            addRenderableWidget(new ButtonWidget(width/2+5,100,150,20,Component.translatable("gui.cancel"),b->onClose()));
        }
        @Override public void onClose(){minecraft.setScreen(parent);}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){super.extractRenderState(g,x,y,d);g.centeredText(font,title,width/2,35,0xffffffff);g.centeredText(font,error,width/2,135,0xffff7777);}
    }
}
