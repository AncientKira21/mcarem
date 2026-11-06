package com.ancientkira.mca.entity.interaction.gifts;

/** RGB comparison shared by named colors and custom-dyed gifts. */
public final class FavoriteColorMatch {
    private FavoriteColorMatch() {}

    public static int bonus(int favorite, int gift) {
        int red = ((favorite >> 16) & 255) - ((gift >> 16) & 255);
        int green = ((favorite >> 8) & 255) - ((gift >> 8) & 255);
        int blue = (favorite & 255) - (gift & 255);
        int distance = red * red + green * green + blue * blue;
        return distance <= 24 * 24 ? 10 : distance <= 120 * 120 ? 5 : 0;
    }
}
