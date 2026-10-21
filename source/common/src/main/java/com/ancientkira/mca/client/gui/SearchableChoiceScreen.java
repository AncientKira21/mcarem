package com.ancientkira.mca.client.gui;

import com.ancientkira.mca.util.compat.ButtonWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Search stays above the options and keeps its focus while results change. */
public final class SearchableChoiceScreen extends Screen {
    public record Choice(String id, String label) { }
    private final Screen parent;
    private final Consumer<Choice> choose;
    private List<Choice> choices;
    private final List<ButtonWidget> options = new ArrayList<>();
    private EditBox search;
    private int offset;
    private Runnable reload;
    private String selectedId;
    private Component status = Component.empty();

    public SearchableChoiceScreen(Screen parent, Component title, List<Choice> choices, Consumer<Choice> choose) {
        super(title); this.parent = parent; this.choices = List.copyOf(choices); this.choose = choose;
    }
    public void setChoices(List<Choice> choices) { this.choices = List.copyOf(choices); offset = 0; if (search != null) refresh(); }
    public void setSelectedId(String selectedId) { this.selectedId = selectedId; if (search != null) refresh(); }
    public void setReload(Runnable reload) { this.reload = reload; }
    public void setStatus(Component status) { this.status = status; }
    @Override protected void init() {
        search = addRenderableWidget(new EditBox(font, width / 2 - 130, 32, 260, 20, Component.translatable("gui.search")));
        search.setHint(Component.translatable("gui.search"));
        search.setMaxLength(100);
        search.setResponder(query -> { offset = 0; refresh(); });
        addRenderableWidget(new ButtonWidget(width / 2 - 105, height - 28, 100, 20, Component.translatable("gui.back"), b -> onClose()));
        if (reload != null) addRenderableWidget(new ButtonWidget(width / 2 + 5, height - 28, 100, 20,
                Component.translatable("gui.villager_editor.voices_reload"), b -> reload.run()));
        refresh(); setInitialFocus(search);
    }
    private void refresh() {
        options.forEach(this::removeWidget); options.clear();
        String query = search.getValue().toLowerCase(Locale.ROOT).strip();
        var filtered = choices.stream().filter(c -> c.label().toLowerCase(Locale.ROOT).contains(query)
                || c.id().toLowerCase(Locale.ROOT).contains(query)).toList();
        int rows = Math.max(1, (height - 110) / 22);
        offset = Math.clamp(offset, 0, Math.max(0, filtered.size() - rows));
        for (int row = 0; row < rows && offset + row < filtered.size(); row++) {
            Choice choice = filtered.get(offset + row);
            ButtonWidget option = new ButtonWidget(width / 2 - 130, 60 + row * 22, 260, 20,
                    Component.literal(choice.label()), b -> { choose.accept(choice); onClose(); });
            option.active = !java.util.Objects.equals(selectedId, choice.id());
            options.add(addRenderableWidget(option));
        }
    }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) {
        offset += sy > 0 ? -1 : sy < 0 ? 1 : 0; refresh(); return true;
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        super.extractRenderState(graphics, x, y, delta);
        graphics.centeredText(font, title, width / 2, 12, 0xffffffff);
        graphics.centeredText(font, status, width / 2, height - 44, 0xffaaaaaa);
    }
}
