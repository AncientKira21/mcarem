package com.ancientkira.mca.client.gui.widget;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class GeneSliderWidget extends AbstractSliderButton {
    private final Component prefix;
    private final Consumer<Double> callback;

    public GeneSliderWidget(int x, int y, int width, int height, Component text, double value, Consumer<Double> callback) {
        super(x, y, width, height, text, value);
        this.prefix = text;
        this.callback = callback;
        this.updateMessage();
    }

    @Override
    protected void applyValue() {
        if (callback != null) {
            callback.accept(value);
        }
    }

    @Override
    protected void updateMessage() {
        if (prefix != null) {
            setMessage(prefix);
        }
    }
}
