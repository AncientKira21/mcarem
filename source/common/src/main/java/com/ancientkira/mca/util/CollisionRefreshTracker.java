package com.ancientkira.mca.util;

import java.util.Map;
import java.util.WeakHashMap;

/** One local refresh after a stable collision, never a continuous rebuild loop. */
public final class CollisionRefreshTracker {
    private final Map<Object, State> states=new WeakHashMap<>();
    private static final class State {
        double x,y,z;
        long stableSince,nextAllowed;
        boolean refreshed;
    }
    public boolean shouldRefresh(Object key,long tick,double x,double y,double z,boolean colliding) {
        State state=states.get(key);
        if(state==null){state=new State();state.x=x;state.y=y;state.z=z;state.stableSince=tick;states.put(key,state);return false;}
        double dx=x-state.x,dy=y-state.y,dz=z-state.z;
        if(!colliding || dx*dx+dy*dy+dz*dz>.04 || tick<state.stableSince) {
            state.x=x;state.y=y;state.z=z;state.stableSince=tick;state.refreshed=false;return false;
        }
        if(state.refreshed || tick-state.stableSince<20 || tick<state.nextAllowed)return false;
        state.refreshed=true;state.nextAllowed=tick+200;return true;
    }
}
