package com.ancientkira.mca.entity;

import com.ancientkira.mca.util.LimitedLinkedHashMap;
import net.minecraft.network.chat.ComponentContents;

public class CommonSpeechManager {
    public static final CommonSpeechManager INSTANCE = new CommonSpeechManager();
    public final LimitedLinkedHashMap<ComponentContents, String> translations = new LimitedLinkedHashMap<>(100);
    public String lastResolvedKey;
}
