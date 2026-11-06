package com.ancientkira.mca.client.book.pages;

import com.ancientkira.mca.client.gui.ExtendedBookScreen;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public abstract class Page {
    /** Vanilla-compatible text for lecterns; independent of rendering and GUI state. */
    public java.util.List<net.minecraft.network.chat.Component> writtenPages(boolean dynamic) {
        return java.util.List.of(net.minecraft.network.chat.Component.empty());
    }
    public abstract void render(ExtendedBookScreen screen, GuiGraphicsExtractor context, int mouseX, int mouseY, float delta);

    public void visitText(ExtendedBookScreen screen, ActiveTextCollector collector) {
        // N/A
    }

    public void open(boolean back) {
        // N/A
    }

    public boolean previousPage() {
        return true;
    }

    public boolean nextPage() {
        return true;
    }
}
