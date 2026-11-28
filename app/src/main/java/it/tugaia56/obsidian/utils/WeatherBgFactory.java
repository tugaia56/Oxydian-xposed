package it.tugaia56.obsidian.utils;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

/**
 * Le 9 varianti di "Sfondo" condivise tra Meteo (CurrentWeatherView.updateWeatherBg) e i
 * widget di "Aggiungi Widget" — varianti 0-3 (nessuno/box/box arrotondato/pillola) sono
 * drawable XML statici (R.drawable.weather_bg_*, risolti dal chiamante con le proprie
 * risorse), 4-8 (accento pieno/gradiente/bordo) sono pure costruzioni Java qui, cosi
 * qualunque widget può riusarle passando solo i colori accento e la densità (px = dp *
 * density) invece di dipendere da una View specifica.
 */
public class WeatherBgFactory {

    public static final int COUNT = 9;

    public static int paddingHDp(int selection) {
        return switch (selection) {
            case 1, 2 -> 12;
            case 3 -> 17;
            case 4, 5, 6, 7, 8 -> 10;
            default -> 0;
        };
    }

    public static int paddingVDp(int selection) {
        return switch (selection) {
            case 1, 2, 3 -> 8;
            case 4, 5, 6, 7, 8 -> 6;
            default -> 0;
        };
    }

    /** 255 per tutte le varianti tranne la 5 (accento pieno semi-trasparente). */
    public static int alpha(int selection) {
        return selection == 5 ? 160 : 255;
    }

    /** Sfondo per le varianti 4-8 (accento/gradiente/bordo) — null per le altre (0-3), che il
     *  chiamante risolve da drawable XML statici (R.drawable.weather_bg_box/_round/_pill). */
    public static Drawable buildAccentDrawable(int selection, int accent1, int accent2, float density) {
        return switch (selection) {
            case 4, 5 -> accentBox(accent1, density);
            case 6 -> gradientBox(accent1, Color.TRANSPARENT, density);
            case 7 -> borderBox(accentBox(accent1, density), density);
            // Bordo Accento Principale -> Accento Secondario, riempimento interno blu notte
            // di Obsidian invece del neutro scuro di sistema.
            case 8 -> borderBox(gradientBox(accent1, accent2, density), density);
            default -> null;
        };
    }

    private static GradientDrawable accentBox(int accentColor, float density) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(30 * density);
        d.setColor(accentColor);
        return d;
    }

    private static GradientDrawable gradientBox(int fromColor, int toColor, float density) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.BL_TR, new int[]{fromColor, toColor});
        d.setCornerRadius(30 * density);
        return d;
    }

    /** Riempimento blu notte di Obsidian con un bordo sottile di accentDrawable (1.5dp). */
    private static Drawable borderBox(Drawable accentDrawable, float density) {
        GradientDrawable inner = new GradientDrawable();
        inner.setShape(GradientDrawable.RECTANGLE);
        inner.setCornerRadius(30 * density);
        inner.setColor(it.tugaia56.obsidian.xposed.utils.AccentUtil.themeBackground(0xFF1B2029));   // sfondo del Theme

        LayerDrawable layered = new LayerDrawable(new Drawable[]{accentDrawable, inner});
        int inset = Math.round(1.5f * density);
        layered.setLayerInset(1, inset, inset, inset, inset);
        return layered;
    }
}
