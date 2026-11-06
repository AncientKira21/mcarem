package com.ancientkira.mca.item;

import com.ancientkira.mca.client.book.Book;
import com.ancientkira.mca.client.book.pages.TextPage;
import com.ancientkira.mca.network.Network;
import com.ancientkira.mca.network.s2c.OpenGuiRequest;
import com.ancientkira.mca.registry.DataComponentsMCA;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Consumer;

public class ExtendedWrittenBookItem extends WrittenBookItem {
    private final Book book;

    public ExtendedWrittenBookItem(Properties settings, Book book) {
        super(settings.component(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT,
                writtenContent(book, book.writtenPages(false))));
        this.book = book;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay, Consumer<Component> consumer, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipDisplay, consumer, tooltipFlag);

        if (book.getBookAuthor() != null) {
            consumer.accept(book.getBookAuthor());
        }
    }

    @Override
    public InteractionResult use(Level world, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            Network.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.BOOK), serverPlayer);
        }

        return world.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    private static net.minecraft.world.item.component.WrittenBookContent writtenContent(Book book,List<Component> pages) {
        String title=com.ancientkira.mca.client.book.BookText.title(book.getBookName());
        if(title.length()>32)title=title.substring(0,32);
        return new net.minecraft.world.item.component.WrittenBookContent(
                net.minecraft.server.network.Filterable.passThrough(title),
                com.ancientkira.mca.client.book.BookText.author(book.getBookAuthor()),0,
                (pages.isEmpty()?List.of(Component.empty()):pages).stream()
                        .map(net.minecraft.server.network.Filterable::passThrough).toList(),true);
    }

    /** Refresh before insertion, preserving the original item and MCA-specific components. */
    public void prepareLectern(ItemStack stack,Player player) {
        List<Component> pages;
        if(this instanceof CivilRegistry && player instanceof ServerPlayer serverPlayer) {
            pages=com.ancientkira.mca.server.world.data.PlayerSaveData.get(serverPlayer)
                    .getRegistryVillage(serverPlayer.level()).flatMap(com.ancientkira.mca.server.world.data.Village::getCivilRegistry)
                    .map(registry -> registry.getPage(0,100)).filter(lines -> !lines.isEmpty())
                    .orElseGet(() -> List.of(Component.translatable("civil_registry.empty")));
        } else pages=getBook(stack).writtenPages(true);
        stack.set(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT,writtenContent(book,pages));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    public Book getBook(ItemStack item) {
        List<Component> content = item.get(DataComponentsMCA.BOOK_PAGES);
        if (content != null) {
            //seems like a vanilla book, let's convert it into the extended book format
            Book book = this.book.copy();

            //add our text pages
            for (Component page : content) {
                book.addPage(new TextPage(page));
            }

            return book;
        } else {
            return book;
        }
    }
}

