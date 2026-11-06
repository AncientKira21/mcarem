package com.ancientkira.mca.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;
import com.ancientkira.mca.util.compat.ButtonWidget;

public final class MedicalInsuranceScreen extends Screen {
    private final Screen parent;
    private final ListTag policies;
    private int page;
    public MedicalInsuranceScreen(Screen parent, ListTag policies) {
        super(Component.translatable("mca.insurance.details")); this.parent=parent; this.policies=policies.copy();
    }
    @Override public void init() {
        addRenderableWidget(new ButtonWidget(width/2-40,height-30,80,20,Component.translatable("gui.back"),b->onClose()));
        addRenderableWidget(new ButtonWidget(width/2-100,height-30,40,20,Component.literal("‹"),b->{page=Math.max(0,page-1);}));
        addRenderableWidget(new ButtonWidget(width/2+60,height-30,40,20,Component.literal("›"),b->{page=Math.min(Math.max(0,(policies.size()-1)/2),page+1);}));
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractRenderState(GuiGraphicsExtractor context,int mouseX,int mouseY,float delta) {
        super.extractRenderState(context,mouseX,mouseY,delta);
        context.centeredText(font,title,width/2,16,0xffffffff);
        int x=Math.max(12,width/2-150), y=40, maxWidth=Math.min(300,width-24);
        if(policies.isEmpty()) context.centeredText(font,Component.translatable("mca.insurance.none"),width/2,y,0xffffffff);
        for(int i=page*2;i<Math.min(policies.size(),page*2+2);i++) {
            CompoundTag policy=policies.getCompound(i).orElseThrow();
            Component[] lines={ Component.translatable("mca.insurance.cleric",policy.getStringOr("Name","")),
                Component.translatable("mca.insurance."+policy.getStringOr("Status","none")),
                policy.getBooleanOr("Family",false) ? Component.translatable("mca.insurance.duration.family") : Component.translatable("mca.insurance.duration.paid", Component.translatable("mca.insurance.no_expiry")) };
            for(Component line:lines) for(var wrapped:font.split(line,maxWidth)) {context.text(font,wrapped,x,y,0xffffffff);y+=11;}
            String village=policy.getStringOr("Village","");
            if(!village.isEmpty()) {for(var wrapped:font.split(Component.literal(village),maxWidth)){context.text(font,wrapped,x,y,0xffbbbbbb);y+=11;}}
            y+=9;
        }
    }
}
