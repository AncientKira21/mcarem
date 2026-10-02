package com.ancientkira.mca.client.gui;

import com.ancientkira.mca.network.Network;
import com.ancientkira.mca.network.c2s.SetTargetMessage;

import java.util.UUID;

public class VillagerTrackerSearchScreen extends FamilyTreeSearchScreen {
    @Override
    void selectVillager(String name, UUID villager) {
        Network.sendToServer(new SetTargetMessage(name, villager));
        onClose();
    }
}
