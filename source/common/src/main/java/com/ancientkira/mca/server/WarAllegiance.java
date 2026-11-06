package com.ancientkira.mca.server;
import java.util.Set;
import java.util.UUID;
/** A roster assignment wins over location/home changes during a battle. */
public final class WarAllegiance {
    private WarAllegiance() { }
    public static int side(UUID id, Set<UUID> first, Set<UUID> second, int home, int firstVillage, int secondVillage) {
        boolean a=first.contains(id), b=second.contains(id);
        if(a!=b)return a?1:2;
        if(home==firstVillage)return 1;
        if(home==secondVillage)return 2;
        return 0;
    }
}
