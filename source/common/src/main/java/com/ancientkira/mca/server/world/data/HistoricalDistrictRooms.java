package com.ancientkira.mca.server.world.data;

import java.util.*;
import java.util.function.*;

/** Reconcile historical districts with rescanned room/structure identities. */
public final class HistoricalDistrictRooms {
    private HistoricalDistrictRooms() {}
    public static <T> List<T> select(Collection<T> live, Collection<T> historic,
            ToIntFunction<T> structure, BiPredicate<T,T> sameSite) {
        Map<Integer,Set<Integer>> sites = new HashMap<>();
        for (T old : historic) {
            Set<Integer> located = sites.computeIfAbsent(structure.applyAsInt(old), id -> new HashSet<>());
            for (T room : live) if (sameSite.test(old,room)) located.add(structure.applyAsInt(room));
        }
        Set<Integer> selected = new HashSet<>();
        sites.forEach((oldStructure,located) -> {
            // An attached room may have vanished while another floor still identifies the building.
            if (located.isEmpty()) selected.add(oldStructure);
            else selected.addAll(located);
        });
        return live.stream().filter(room -> selected.contains(structure.applyAsInt(room))).toList();
    }
}
