package com.ancientkira.mca.client.gui;

import com.ancientkira.mca.client.gui.widget.WidgetUtils;
import com.ancientkira.mca.client.resources.Icon;
import com.ancientkira.mca.entity.interaction.Constraint;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.*;

public abstract class AbstractDynamicScreen extends Screen {
    protected static final float iconScale = 1.5f;

    // Tracks which page we're on in the GUI for sending button events
    private String activeScreen = "main";
    private int actionPage;

    private int mouseX;
    private int mouseY;

    private Set<Constraint> constraints = new HashSet<>();

    protected AbstractDynamicScreen(Component title) {
        super(title);
    }

    public String getActiveScreen() {
        return activeScreen;
    }

    public Set<Constraint> getConstraints() {
        return constraints;
    }

    public void setConstraints(Set<Constraint> constraints) {
        this.constraints = constraints;
        setLayout(activeScreen);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    protected abstract void buttonPressed(MCAButton button);

    protected void disableButton(String id) {
        children().forEach(b -> {
            if (b instanceof ButtonEx) {
                if (((ButtonEx) b).getApiButton().identifier().equals(id)) {
                    ((ButtonEx) b).active = false;
                }
            }
        });
    }

    protected void disableAllButtons() {
        this.children().forEach(b -> {
            if (b instanceof AbstractWidget) {
                if (b instanceof ButtonEx) {
                    if (!((ButtonEx) b).getApiButton().identifier().equals("gui.button.backarrow")) {
                        ((AbstractWidget) b).active = false;
                    }
                } else {
                    ((AbstractWidget) b).active = false;
                }
            }
        });
    }

    /**
     * Adds API buttons to the GUI screen provided.
     *
     * @param guiKey String key for the GUI's buttons
     */
    public void setLayout(String guiKey) {
        if (!activeScreen.equals(guiKey)) actionPage = 0;
        activeScreen = guiKey;

        clearWidgets();
        MCAScreens.getInstance().getScreen(guiKey).ifPresent(buttons -> {
            if (guiKey.equals("locations")) {
                var actions = Arrays.stream(buttons).filter(b -> !b.identifier().equals("gui.button.backarrow"))
                        .filter(b -> !b.hideOnFail() || b.isValidForConstraint(getConstraints())).toList();
                int perPage = Math.max(1, Math.min(6, (height - 65) / 21));
                int pages = Math.max(1, (actions.size() + perPage - 1) / perPage);
                actionPage = Math.clamp(actionPage, 0, pages - 1);
                Arrays.stream(buttons).filter(b -> b.identifier().equals("gui.button.backarrow")).findFirst().ifPresent(b ->
                        addRenderableWidget(new ButtonEx(new MCAButton(b.identifier(), "top_right", -136, 0, 10, 0, 20, 20,
                                b.notifyServer(), b.targetServer(), b.constraints(), b.hideOnFail(), b.isInteraction()), this)));
                for (int i = 0; i < perPage && actionPage * perPage + i < actions.size(); i++) {
                    MCAButton b = actions.get(actionPage * perPage + i);
                    addRenderableWidget(new ButtonEx(new MCAButton(b.identifier(), "top_right", -112, 0, 10, i, 104, 20,
                            b.notifyServer(), b.targetServer(), b.constraints(), b.hideOnFail(), b.isInteraction()), this));
                }
                if (pages > 1) {
                    int y = 16 + perPage * 21;
                    Button previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> { actionPage--; setLayout(guiKey); })
                            .bounds(width - 112, y, 25, 20).build());
                    previous.active = actionPage > 0;
                    Button indicator = addRenderableWidget(Button.builder(Component.literal((actionPage + 1) + " / " + pages), b -> {})
                            .bounds(width - 85, y, 50, 20).build());
                    indicator.active = false;
                    Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> { actionPage++; setLayout(guiKey); })
                            .bounds(width - 33, y, 25, 20).build());
                    next.active = actionPage + 1 < pages;
                }
            } else {
                for (MCAButton b : buttons) addRenderableWidget(new ButtonEx(b, this));
            }
        });
    }

    protected void drawIcon(GuiGraphicsExtractor context, Identifier texture, String key) {
        Icon icon = MCAScreens.getInstance().getIcon(key);
        context.blit(RenderPipelines.GUI_TEXTURED, texture, (int) (icon.x() / iconScale), (int) (icon.y() / iconScale), icon.u(), icon.v(), 16, 16, 256, 256);
    }

    protected void drawHoveringIconText(GuiGraphicsExtractor context, Component text, String key) {
        Icon icon = MCAScreens.getInstance().getIcon(key);
        drawTooltip(context, text, icon.x() + 16, icon.y() + 20);
    }

    protected void drawHoveringIconText(GuiGraphicsExtractor context, List<Component> text, String key) {
        Icon icon = MCAScreens.getInstance().getIcon(key);
        drawTooltip(context, text, icon.x() + 16, icon.y() + 20);
    }

    protected void drawTooltip(GuiGraphicsExtractor context, Component text, int x, int y) {
        drawTooltip(context, List.of(text), x, y);
    }

    protected void drawTooltip(GuiGraphicsExtractor context, List<Component> text, int x, int y) {
        WidgetUtils.drawTooltip(context, font, text, x, y);
    }

    //checks if the mouse hovers over a specified button
    protected boolean hoveringOverIcon(String key) {
        Icon icon = MCAScreens.getInstance().getIcon(key);
        return hoveringOver(icon.x(), icon.y(), (int) (16 * iconScale), (int) (16 * iconScale));
    }

    //checks if the mouse hovers over a rectangle
    protected boolean hoveringOver(int x, int y, int w, int h) {
        return mouseX > x && mouseX < x + w && mouseY > y && mouseY < y + h;
    }

    private enum Alignment {
        TOP_LEFT(0.0f, 0.0f),
        TOP(0.5f, 0.0f),
        TOP_RIGHT(1.0f, 0.0f),
        RIGHT(1.0f, 0.5f),
        BOTTOM_RIGHT(1.0f, 1.0f),
        BOTTOM(0.5f, 1.0f),
        BOTTOM_LEFT(0.0f, 1.0f),
        LEFT(0.0f, 0.5f),
        CENTER(0.5f, 0.5f);

        static final Map<String, Alignment> alignments = new HashMap<>();

        static {
            for (Alignment a : Alignment.values()) {
                alignments.put(a.name().toLowerCase(Locale.ENGLISH), a);
            }
        }

        final float h;
        final float v;

        Alignment(float h, float v) {
            this.h = h;
            this.v = v;
        }
    }

    private static class ButtonEx extends Button {
        private final MCAButton apiButton;

        public ButtonEx(MCAButton apiButton, AbstractDynamicScreen screen) {
            super((int) (screen.width * Alignment.alignments.get(apiButton.align()).h + apiButton.x()),
                    (int) (screen.height * Alignment.alignments.get(apiButton.align()).v + apiButton.y()),
                    apiButton.width(),
                    apiButton.height(),
                    getButtonMessage(apiButton, screen),
                    a -> screen.buttonPressed(apiButton),
                    DEFAULT_NARRATION);
            this.apiButton = apiButton;

            // Remove the button if we specify it should not be present on constraint failure
            // Otherwise we just mark the button as disabled.
            if (!apiButton.isValidForConstraint(screen.getConstraints())) {
                if (apiButton.hideOnFail()) {
                    visible = false;
                }
                active = false;
            }
        }

        private static Component getButtonMessage(MCAButton apiButton, AbstractDynamicScreen screen) {
            if ("gui.button.ridehorse".equals(apiButton.identifier())
                    && screen.getConstraints().contains(Constraint.RIDING)) {
                return Component.translatable("gui.button.dismount");
            }
            return Component.translatable(apiButton.identifier());
        }

        public MCAButton getApiButton() {
            return apiButton;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            this.extractDefaultSprite(graphics);
            this.extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
        }
    }
}
