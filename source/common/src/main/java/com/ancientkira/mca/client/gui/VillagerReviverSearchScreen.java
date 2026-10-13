package com.ancientkira.mca.client.gui;

import com.ancientkira.mca.network.FamilyTreeSearchEntry;
import com.ancientkira.mca.util.compat.ButtonWidget;
import com.ancientkira.mca.network.Network;
import com.ancientkira.mca.network.c2s.FamilyTreeUUIDLookup;
import com.ancientkira.mca.network.c2s.VillagerReviverActionMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class VillagerReviverSearchScreen extends FamilyTreeSearchScreen {
    public VillagerReviverSearchScreen() {
        super(Component.translatable("gui.villager_reviver.title"));
    }

    private void reviveBatch(VillagerReviverActionMessage.Action action) {
        Network.sendToServer(new VillagerReviverActionMessage(new UUID(0, 0), action));
        onClose();
    }

    private int refreshTicks;

    @Override
    public void init() {
        super.init();
        for (var child : children()) {
            if (child instanceof ButtonWidget button && button.getMessage().equals(Component.translatable("gui.done"))) {
                button.setX(width / 2 + 54);
                button.setWidth(100);
            }
        }
        addRenderableWidget(new ButtonWidget(width / 2 - 154, height / 2 + 82, 100, 20,
                Component.translatable("gui.villager_reviver.all"), button -> reviveBatch(VillagerReviverActionMessage.Action.REVIVE_ALL)));
        addRenderableWidget(new ButtonWidget(width / 2 - 50, height / 2 + 82, 100, 20,
                Component.translatable("gui.villager_reviver.village"), button -> reviveBatch(VillagerReviverActionMessage.Action.REVIVE_VILLAGE)));
        searchVillager(searchTerm);
    }

    @Override
    public void tick() {
        super.tick();
        if (++refreshTicks >= 40) {
            refreshTicks = 0;
            searchVillager(searchTerm);
        }
    }

    @Override
    protected Component getSearchHint() {
        return Component.translatable("gui.villager_reviver.hint");
    }

    @Override
    public boolean acceptsDeceasedVillagersOnly() {
        return true;
    }

    @Override
    protected void searchVillager(String search) {
        searchTerm = search;
        Network.sendToServer(new FamilyTreeUUIDLookup(search, true));
    }

    @Override
    protected void selectVillager(String name, UUID villager) {
        Network.sendToServer(new VillagerReviverActionMessage(villager, VillagerReviverActionMessage.Action.REVIVE));
        onClose();
    }

    @Override
    protected boolean deleteVillager(FamilyTreeSearchEntry villager) {
        Minecraft client = Minecraft.getInstance();
        client.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                Network.sendToServer(new VillagerReviverActionMessage(
                        villager.uuid(), VillagerReviverActionMessage.Action.DELETE));
                removeSearchResult(villager.uuid());
            }
            client.setScreen(this);
        }, Component.translatable("gui.villager_reviver.delete.title"),
                Component.translatable("gui.villager_reviver.delete.confirm", villager.name()),
                Component.translatable("gui.villager_reviver.delete.yes"), CommonComponents.GUI_CANCEL));
        return true;
    }
}
