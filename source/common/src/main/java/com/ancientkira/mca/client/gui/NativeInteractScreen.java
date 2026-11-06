package com.ancientkira.mca.client.gui;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.network.Network;
import com.ancientkira.mca.network.c2s.InteractionDialogueInitMessage;
/** Native mobs share the MCA conversation choices; shift-click preserves native trading/repair. */
public final class NativeInteractScreen extends InteractScreen {
    private final VillagerEntityMCA profile;
    public NativeInteractScreen(VillagerEntityMCA profile){super(profile);this.profile=profile;}
    @Override public void init(){super.init();Network.sendToServer(new InteractionDialogueInitMessage(profile.getUUID()));}
}
