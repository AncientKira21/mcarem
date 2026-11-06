package com.ancientkira.mca.client.book.pages;

import net.minecraft.network.chat.Component;

import java.util.List;

public abstract class ListPage extends Page {
    final List<Component> text;

    int page;

    public ListPage(List<Component> text) {
        this.text = text;
    }

    @Override
    public void open(boolean back) {
        page = back ? (text.size() - 1) / getEntriesPerPage() : 0;
    }

    @Override
    public boolean previousPage() {
        if (page > 0) {
            page--;
            return false;
        } else {
            return true;
        }
    }

    @Override
    public boolean nextPage() {
        if (page < (text.size() - 1) / getEntriesPerPage()) {
            page++;
            return false;
        } else {
            return true;
        }
    }

    protected Component writtenTitle() { return null; }

    @Override
    public List<Component> writtenPages(boolean dynamic) { return writtenPages(text); }

    protected List<Component> writtenPages(List<Component> entries) {
        List<Component> pages=new java.util.ArrayList<>();
        int size=Math.max(1,getEntriesPerPage());
        for(int start=0;start<Math.max(1,entries.size());start+=size){
            var page=Component.empty();Component title=writtenTitle();
            if(title!=null)page.append(title.copy().withStyle(net.minecraft.ChatFormatting.BLACK)).append("\n\n");
            for(int i=start;i<Math.min(entries.size(),start+size);i++){
                if(i>start)page.append("\n");page.append(entries.get(i).copy());
            }
            pages.add(page);
        }
        return pages;
    }

    abstract int getEntriesPerPage();
}
