package com.ancientkira.mca.entity;

/** Explicit editor fields take precedence over legacy space-separated names. */
public record NameParts(String first, String last) {
    public NameParts {
        first = first == null ? "" : first.strip();
        last = last == null ? "" : last.strip();
    }

    public static boolean disablesSurname(String text) {
        return text != null && !text.isEmpty() && text.isBlank();
    }

    public String full() {
        return (first + " " + last).strip();
    }

    public static NameParts resolve(String full, String first, String last) {
        String name = full == null ? "" : full.strip();
        NameParts explicit = new NameParts(first, last);
        if (!explicit.first.isEmpty() && explicit.full().equals(name)) return explicit;
        int split = name.indexOf(' ');
        return split < 0 ? new NameParts(name, "")
                : new NameParts(name.substring(0, split), name.substring(split + 1));
    }
}
