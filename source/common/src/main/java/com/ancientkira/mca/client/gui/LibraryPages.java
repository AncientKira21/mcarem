package com.ancientkira.mca.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;

/** One page supplies both controls and previews, including the pinned worn asset. */
public final class LibraryPages {
    private LibraryPages() { }
    public static <T> List<T> page(List<T> matching, int page, int size, boolean alreadyPaged,
                                   Optional<T> worn, ToIntFunction<T> id) {
        if (size <= 0) return List.of();
        long start = alreadyPaged ? 0 : Math.max(0L, (long) page) * size;
        int from = (int) Math.min(matching.size(), start);
        List<T> result = new ArrayList<>(matching.subList(from, Math.min(matching.size(), from + size)));
        if (page == 0) worn.ifPresent(selected -> {
            result.removeIf(content -> id.applyAsInt(content) == id.applyAsInt(selected));
            result.addFirst(selected);
            if (result.size() > size) result.removeLast();
        });
        return List.copyOf(result);
    }
}
