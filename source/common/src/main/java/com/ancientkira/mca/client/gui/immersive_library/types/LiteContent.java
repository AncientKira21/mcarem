package com.ancientkira.mca.client.gui.immersive_library.types;

import java.util.HashSet;
import java.util.Set;

public record LiteContent(int contentid,int userid,String username,int likes,Set<String> tags,String title,int version,Integer users) implements Tagged {
    public LiteContent { tags=tags==null?new HashSet<>():new HashSet<>(tags);title=title==null?"Unnamed asset":title;username=username==null?"Unknown":username; }
    public LiteContent(int contentid,int userid,String username,int likes,Set<String> tags,String title,int version) {
        this(contentid,userid,username,likes,tags,title,version,null);
    }
}
