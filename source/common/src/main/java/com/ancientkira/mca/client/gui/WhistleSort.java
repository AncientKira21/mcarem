package com.ancientkira.mca.client.gui;

import java.util.Comparator;
import java.util.function.Function;
import java.util.function.ToIntFunction;

public enum WhistleSort {
    AZ, ZA, OLDEST, YOUNGEST, ORIGINAL;

    public <T> Comparator<T> comparator(Function<T, String> name, ToIntFunction<T> age) {
        Comparator<T> alphabetical = Comparator.comparing(name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(name);
        // Positive Age values are breeding cooldowns, not an adult's date of birth.
        Comparator<T> byAge = Comparator.comparingInt(value -> Math.min(0, age.applyAsInt(value)));
        return switch (this) {
            case AZ -> alphabetical;
            case ZA -> alphabetical.reversed();
            case OLDEST -> byAge.reversed().thenComparing(alphabetical);
            case YOUNGEST -> byAge.thenComparing(alphabetical);
            case ORIGINAL -> (a, b) -> 0;
        };
    }
}
