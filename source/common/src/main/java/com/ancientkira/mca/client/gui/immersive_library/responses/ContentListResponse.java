package com.ancientkira.mca.client.gui.immersive_library.responses;

import com.ancientkira.mca.client.gui.immersive_library.types.LiteContent;

public record ContentListResponse(LiteContent[] contents) implements Response {

}
