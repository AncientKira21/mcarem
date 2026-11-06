package com.ancientkira.mca.client.gui;

import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.network.Network;
import com.ancientkira.mca.network.c2s.CallToPlayerMessage;
import com.ancientkira.mca.network.c2s.GetFamilyRequest;
import com.ancientkira.mca.registry.EntitiesMCA;
import com.ancientkira.mca.util.compat.ButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.storage.TagValueInput;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class WhistleScreen extends Screen {
    private List<String> keys = new ArrayList<>();
    private final List<String> originalKeys = new ArrayList<>();
    private WhistleSort sort = WhistleSort.AZ;
    private CompoundTag villagerData = new CompoundTag();

    private VillagerEntityMCA dummy;

    private ButtonWidget selectionLeftButton;
    private ButtonWidget selectionRightButton;
    private ButtonWidget villagerNameButton;
    private ButtonWidget callButton;
    private int loadingAnimationTicks;
    private int selectedIndex;
    private final List<Component> names = new ArrayList<>();
    private final List<ButtonWidget> nameOptions = new ArrayList<>();
    private net.minecraft.client.gui.components.EditBox nameSearch;
    private boolean namesOpen;
    private int nameOffset;

    private void closeNames() {
        namesOpen = false;
        nameOptions.forEach(this::removeWidget);
        nameOptions.clear();
        if (nameSearch != null) removeWidget(nameSearch);
        nameSearch = null;
    }

    private int visibleNameCount() {
        return Math.min(keys.size(), Math.max(1, (height / 2 + 65 - 25) / 22));
    }

    private void showNames() {
        if (nameSearch == null) {
            namesOpen = true;
            nameSearch = addRenderableWidget(new net.minecraft.client.gui.components.EditBox(font,
                    width / 2 - 100, 28, 200, 20, Component.translatable("gui.search")));
            nameSearch.setHint(Component.translatable("gui.search"));
            nameSearch.setResponder(query -> { nameOffset = 0; updateNameOptions(); });
            setFocused(nameSearch);
        }
        updateNameOptions();
    }

    private void updateNameOptions() {
        nameOptions.forEach(this::removeWidget);
        nameOptions.clear();
        String query = nameSearch.getValue().strip().toLowerCase(java.util.Locale.ROOT);
        List<Integer> matches = java.util.stream.IntStream.range(0, names.size())
                .filter(i -> names.get(i).getString().toLowerCase(java.util.Locale.ROOT).contains(query))
                .boxed().toList();
        int count = Math.min(matches.size(), Math.max(1, (height / 2 + 65 - 54) / 22));
        nameOffset = Math.clamp(nameOffset, 0, Math.max(0, matches.size() - count));
        for (int row = 0; row < count; row++) {
            int index = matches.get(nameOffset + row);
            ButtonWidget option = new ButtonWidget(width / 2 - 100, 54 + row * 22, 200, 20,
                    names.get(index), b -> {
                        selectedIndex = index;
                        closeNames();
                        setVillagerData(index);
                    });
            nameOptions.add(addRenderableWidget(option));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (com.ancientkira.mca.client.gui.widget.DropdownWidget.scroll(this, scrollY)) return true;
        if (namesOpen) {
            nameOffset += scrollY > 0 ? -1 : scrollY < 0 ? 1 : 0;
            showNames();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public WhistleScreen() {
        super(Component.translatable("gui.whistle.title"));
    }

    @Override
    public void tick() {
        super.tick();

        if (loadingAnimationTicks != -1) {
            loadingAnimationTicks++;
        }

        if (loadingAnimationTicks >= 20) {
            loadingAnimationTicks = 0;
        }
    }

    @Override
    public void init() {
        closeNames();
        com.ancientkira.mca.client.gui.widget.DropdownWidget.close(this);
        Network.sendToServer(new GetFamilyRequest());

        selectionLeftButton = addRenderableWidget(new ButtonWidget(width / 2 - 123, height / 2 + 65, 20, 20, Component.literal("<<"), b -> {
            closeNames();
            if (selectedIndex == 0) {
                selectedIndex = keys.size() - 1;
            } else {
                selectedIndex--;
            }
            setVillagerData(selectedIndex);
        }));
        selectionRightButton = addRenderableWidget(new ButtonWidget(width / 2 + 103, height / 2 + 65, 20, 20, Component.literal(">>"), b -> {
            closeNames();
            if (selectedIndex == keys.size() - 1) {
                selectedIndex = 0;
            } else {
                selectedIndex++;
            }
            setVillagerData(selectedIndex);
        }));
        villagerNameButton = addRenderableWidget(new ButtonWidget(width / 2 - 100, height / 2 + 65, 200, 20, Component.literal(""), b -> {
            if (namesOpen) {
                closeNames();
            } else {
                if (names.isEmpty()) {
                    for (int index = 0; index < keys.size(); index++) {
                        setVillagerData(index);
                        names.add(dummy.getDisplayName());
                    }
                    setVillagerData(selectedIndex);
                }
                nameOffset = selectedIndex;
                showNames();
            }
        }));

        callButton = addRenderableWidget(new ButtonWidget(width / 2 - 140, height / 2 + 90, 60, 20, Component.translatable("gui.button.call"), (b) -> {
            Network.sendToServer(new CallToPlayerMessage(UUID.fromString(keys.get(selectedIndex))));
            Objects.requireNonNull(this.minecraft).setScreen(null);
        }));

        addRenderableWidget(new com.ancientkira.mca.client.gui.widget.DropdownWidget<>(this,
                widget -> addRenderableWidget(widget), this::removeWidget,
                width / 2 - 70, height / 2 + 90, 140, 20,
                List.of(WhistleSort.values()), sort,
                value -> Component.translatable("gui.whistle.sort", Component.translatable("gui.whistle.sort." + value.name().toLowerCase(java.util.Locale.ROOT))),
                value -> { sort = value; sortVillagers(); }));

        addRenderableWidget(new ButtonWidget(width / 2 + 80, height / 2 + 90, 60, 20, Component.translatable("gui.button.exit"), b -> Objects.requireNonNull(this.minecraft).setScreen(null)));

        toggleButtons(false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float offset) {
        super.extractRenderState(context, mouseX, mouseY, offset);

        context.centeredText(font, Component.translatable("gui.whistle.title"), width / 2, namesOpen ? 10 : height / 2 - 100, 0xffffff);

        if (loadingAnimationTicks != -1) {
            String loadingMsg = new String(new char[(loadingAnimationTicks / 5) % 4]).replace("\0", ".");
            context.text(font, Component.translatable("gui.loading").append(Component.literal(loadingMsg)), width / 2 - 20, height / 2 - 10, 0xffffff);
        } else if (!namesOpen) {
            if (keys.isEmpty()) {
                context.centeredText(font, Component.translatable("gui.whistle.noFamily"), width / 2, height / 2 + 50, 0xffffff);
            } else {
                context.centeredText(font, (selectedIndex + 1) + " / " + keys.size(), width / 2, height / 2 + 50, 0xffffff);
            }
        }

        if (dummy != null && !namesOpen) {
            int posX = width / 2;
            int posY = height / 2;
            InventoryScreen.extractEntityInInventoryFollowsMouse(context, posX - 30, posY - 70, posX + 30, posY + 45, 60, 0, mouseX, mouseY, dummy);
        }
    }

    public void setVillagerData(@NotNull CompoundTag data) {
        closeNames();
        names.clear();
        villagerData = data;
        originalKeys.clear();
        originalKeys.addAll(data.keySet());
        loadingAnimationTicks = -1;
        selectedIndex = 0;
        keys.clear();
        sortVillagers();
    }

    private void sortVillagers() {
        String selected = keys.isEmpty() ? null : keys.get(selectedIndex);
        closeNames();
        keys = new ArrayList<>(originalKeys);
        java.util.Map<String, Component> labels = new java.util.HashMap<>();
        for (String key : keys) {
            var data = villagerData.getCompoundOrEmpty(key);
            labels.put(key, com.ancientkira.mca.entity.VillagerLike.parseCustomName(
                    Objects.requireNonNull(minecraft.level).registryAccess(), data)
                    .orElse(Component.translatable("entity.mca.male_villager")));
        }
        keys.sort(sort.comparator(key -> labels.get(key).getString(),
                key -> villagerData.getCompoundOrEmpty(key).getIntOr("Age", 0)));
        names.clear();
        for (String key : keys) names.add(labels.get(key));
        selectedIndex = selected == null ? 0 : Math.max(0, keys.indexOf(selected));
        nameOffset = 0;
        setVillagerData(selectedIndex);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (com.ancientkira.mca.client.gui.widget.DropdownWidget.click(this, event, doubleClick)) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (com.ancientkira.mca.client.gui.widget.DropdownWidget.escape(this, event.key())) return true;
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        com.ancientkira.mca.client.gui.widget.DropdownWidget.close(this);
        super.removed();
    }

    private void setVillagerData(int index) {
        if (!keys.isEmpty()) {
            CompoundTag firstData = villagerData.getCompound(keys.get(index)).orElseGet(CompoundTag::new);

            dummy = EntitiesMCA.PERSON.create(Minecraft.getInstance().level, EntitySpawnReason.LOAD);
            dummy.load(TagValueInput.create(ProblemReporter.DISCARDING, dummy.registryAccess(), firstData));

            villagerNameButton.setMessage(dummy.getDisplayName());

            toggleButtons(true);
        } else {
            toggleButtons(false);
        }
    }

    private void toggleButtons(boolean enabled) {
        villagerNameButton.active = enabled;
        selectionLeftButton.active = enabled;
        selectionRightButton.active = enabled;
        callButton.active = enabled;
    }
}
