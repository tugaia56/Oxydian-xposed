package it.tugaia56.obsidian.utils;

import android.graphics.Color;

public class ColorUtils {

    /**
     * Adjust a color (same algorithm as OC's ColorUtils.adjustColor):
     *  -100..+100  → each RGB channel scaled by (1 + amount/100), matching OC behaviour
     *  1000..1255  → alpha override: alpha = (amount - 1000), RGB kept from color
     */
    public static int adjustColor(int color, int amount) {
        if (amount >= 1000) {
            int alpha = Math.min(255, amount - 1000);
            return (color & 0x00FFFFFF) | (alpha << 24);
        }
        float percent = amount;
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        r = (int) Math.min(255, Math.max(0, r + (r * percent / 100)));
        g = (int) Math.min(255, Math.max(0, g + (g * percent / 100)));
        b = (int) Math.min(255, Math.max(0, b + (b * percent / 100)));
        return Color.argb(Color.alpha(color), r, g, b);
    }

    /**
     * Blend a color towards white (amount &gt; 0) or black (amount &lt; 0) by |amount|%,
     * keeping the same hue/alpha. Unlike adjustColor()'s multiplicative scaling — which
     * can't push an already-saturated channel (e.g. blue=255) any higher — this linearly
     * interpolates each channel towards 255 or 0, so it reaches genuinely light/dark
     * tones at the extremes. Used for the Material You tonal palette (system_accentN_*):
     * light tones (100/200/300) need a real near-white background, dark tones (600/700)
     * a real near-black one, so text and container never collapse to the same flat color.
     */
    public static int blendTone(int color, int amount) {
        float fraction = Math.max(-100, Math.min(100, amount)) / 100f;
        int target = fraction >= 0 ? 255 : 0;
        float f = Math.abs(fraction);
        int r = Math.round(Color.red(color)   + (target - Color.red(color))   * f);
        int g = Math.round(Color.green(color) + (target - Color.green(color)) * f);
        int b = Math.round(Color.blue(color)  + (target - Color.blue(color))  * f);
        return Color.argb(Color.alpha(color), r, g, b);
    }

    /** Multiply the alpha channel by factor (0..1). */
    public static int adjustAlpha(int color, float factor) {
        int alpha = Math.min(255, (int) (Color.alpha(color) * factor));
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    /** Darken a color for pressed state: HSV value multiplied by factor. */
    public static int adjustColorForPressed(int baseColor, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(baseColor, hsv);
        hsv[2] = Math.max(0f, Math.min(1f, hsv[2] * factor));
        return Color.HSVToColor(hsv);
    }

    public static String toHex(int color) {
        return String.format("#%08X", color);
    }
}
