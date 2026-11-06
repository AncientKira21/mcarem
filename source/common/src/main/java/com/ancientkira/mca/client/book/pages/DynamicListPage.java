package com.ancientkira.mca.client.book.pages;

import net.minecraft.network.chat.Component;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

public class DynamicListPage extends CenteredListPage {
    private final Function<Page, List<Component>> generator;

    public DynamicListPage(String title, Function<Page, List<Component>> generator) {
        super(title, new LinkedList<>());

        this.generator = generator;
    }

    @Override
    public List<Component> writtenPages(boolean dynamic) {
        return dynamic ? writtenPages(generator.apply(this)) : super.writtenPages(false);
    }

    @Override
    public void open(boolean back) {
        text.clear();
        text.addAll(generator.apply(this));

        super.open(back);
    }
}
