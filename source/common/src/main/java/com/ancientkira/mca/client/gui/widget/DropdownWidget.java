package com.ancientkira.mca.client.gui.widget;

import com.ancientkira.mca.util.compat.ButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.*;

/** Shared searchable popup; wheel events and clicks stay inside the open menu. */
public final class DropdownWidget<T> extends ButtonWidget {
    private static final Map<Screen,DropdownWidget<?>> OPEN = new WeakHashMap<>();
    private final Screen owner;
    private final Consumer<AbstractWidget> add, remove;
    private final List<T> values;
    private final Function<T,Component> label;
    private final Consumer<T> changed;
    private final List<AbstractWidget> options = new ArrayList<>();
    private T selected;
    private int offset;
    private String query = "";
    private EditBox search;

    public DropdownWidget(Screen owner, Consumer<AbstractWidget> add, Consumer<AbstractWidget> remove,
            int x, int y, int width, int height, List<T> values, T selected, Function<T,Component> label, Consumer<T> changed) {
        super(x,y,width,height,label.apply(selected).copy().append(" ▾"),button -> ((DropdownWidget<?>)button).toggle());
        this.owner=owner; this.add=add; this.remove=remove; this.values=List.copyOf(values);
        this.selected=selected; this.label=label; this.changed=changed;
    }
    private void toggle() {
        if (OPEN.get(owner)==this) { close(owner); return; }
        close(owner); OPEN.put(owner,this); query="";
        offset=Math.max(0,values.indexOf(selected)-3); showOptions();
        owner.setFocused(search); search.setFocused(true);
    }
    private void showOptions() {
        boolean focused=search!=null && search.isFocused();
        int cursor=search==null ? query.length() : search.getCursorPosition();
        options.forEach(remove); options.clear();
        List<T> filtered=values.stream().filter(value -> label.apply(value).getString().toLowerCase(Locale.ROOT)
                .contains(query.toLowerCase(Locale.ROOT))).toList();
        int visible=Math.min(Math.max(1,Math.min(8,(owner.height-56)/20)),filtered.size());
        offset=Math.clamp(offset,0,Math.max(0,filtered.size()-visible));
        int total=(visible+1+(filtered.size()>visible ? 1:0))*20;
        int y=getY()+getHeight()+1;
        if (y+total>owner.height-4) y=Math.max(4,getY()-total-1);
        int menuWidth=Math.max(100,getWidth());
        int x=Math.clamp(getX(),4,Math.max(4,owner.width-menuWidth-4));
        search=new EditBox(Minecraft.getInstance().font,x,y,menuWidth,18,Component.translatable("gui.villager_editor.search"));
        search.setHint(Component.translatable("gui.villager_editor.search")); search.setMaxLength(128); search.setValue(query);
        search.setCursorPosition(Math.min(cursor,query.length()));
        search.setResponder(text -> { query=text; offset=0; showOptions(); });
        options.add(search); add.accept(search);
        for (int i=0;i<visible;i++) {
            T value=filtered.get(offset+i);
            ButtonWidget option=new ButtonWidget(x,y+(i+1)*20,menuWidth,20,label.apply(value),button -> {
                selected=value; setMessage(label.apply(value).copy().append(" ▾")); close(owner); changed.accept(value);
            });
            option.active=!Objects.equals(value,selected); options.add(option); add.accept(option);
        }
        if (filtered.size()>visible) {
            int half=menuWidth/2;
            ButtonWidget up=new ButtonWidget(x,y+(visible+1)*20,half,20,Component.literal("▲"),button -> { offset--; showOptions(); });
            ButtonWidget down=new ButtonWidget(x+half,y+(visible+1)*20,menuWidth-half,20,Component.literal("▼"),button -> { offset++; showOptions(); });
            up.active=offset>0; down.active=offset+visible<filtered.size();
            options.add(up); options.add(down); add.accept(up); add.accept(down);
        }
        if (focused) { owner.setFocused(search); search.setFocused(true); }
    }
    /** Extract after 3D previews so popup widgets occupy a higher GUI stratum. */
    public static void extractOverlay(Screen owner, net.minecraft.client.gui.GuiGraphicsExtractor context,
                                      int mouseX, int mouseY, float delta) {
        DropdownWidget<?> open = OPEN.get(owner);
        if (open == null || open.options.isEmpty()) return;
        context.nextStratum();
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = 0, bottom = 0;
        for (AbstractWidget option : open.options) {
            left = Math.min(left, option.getX());
            top = Math.min(top, option.getY());
            right = Math.max(right, option.getX() + option.getWidth());
            bottom = Math.max(bottom, option.getY() + option.getHeight());
        }
        context.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFF202020);
        for (AbstractWidget option : open.options) option.extractRenderState(context, mouseX, mouseY, delta);
    }
    public static boolean isOpen(Screen owner) { return OPEN.containsKey(owner); }
    public static void close(Screen owner) {
        DropdownWidget<?> open=OPEN.remove(owner);
        if (open!=null) {
            if (owner.getFocused()==open.search) owner.setFocused(null);
            open.options.forEach(open.remove); open.options.clear(); open.search=null;
        }
    }
    public static boolean click(Screen owner,MouseButtonEvent event,boolean doubleClick) {
        DropdownWidget<?> open=OPEN.get(owner); if (open==null) return false;
        for (AbstractWidget option:List.copyOf(open.options)) if (option.isMouseOver(event.x(),event.y())) {
            option.mouseClicked(event,doubleClick);
            if (option==open.search) { owner.setFocused(option); option.setFocused(true); }
            return true;
        }
        close(owner); return true;
    }
    public static boolean scroll(Screen owner,double amount) {
        DropdownWidget<?> open=OPEN.get(owner); if (open==null) return false;
        open.offset+=amount>0 ? -1 : amount<0 ? 1 : 0; open.showOptions(); return true;
    }
    public static boolean escape(Screen owner,int key) {
        if (key==256 && OPEN.containsKey(owner)) { close(owner); return true; } return false;
    }
}
