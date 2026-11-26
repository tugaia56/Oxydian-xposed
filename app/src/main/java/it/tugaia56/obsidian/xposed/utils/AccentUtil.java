package it.tugaia56.obsidian.xposed.utils;

import android.app.AndroidAppHelper;
import android.app.Application;
import android.content.res.Resources;

/**
 * Accento di sistema scelto in Oxydian Theme (colore "accent_material_dark" del sistema), per i mod che
 * non hanno un accento proprio di Oxydian: prima ricadevano sul viola predefinito #908DFF anche se in
 * Oxydian Theme era stato scelto un altro colore.
 */
public final class AccentUtil {
    public static final int DEFAULT = 0xFF908DFF;

    private AccentUtil() {}

    /** Colore "Opacita' tocco" di Oxydian Theme (ripple_material_dark): accento con la trasparenza scelta. */
    public static int rippleColor() {
        try {
            int id = Resources.getSystem().getIdentifier("ripple_material_dark", "color", "android");
            if (id == 0) return (systemAccent() & 0xFFFFFF) | 0x80000000;
            Application app = AndroidAppHelper.currentApplication();
            Resources r = app != null ? app.getResources() : Resources.getSystem();
            return r.getColor(id, null);
        } catch (Throwable t) {
            return (DEFAULT & 0xFFFFFF) | 0x80000000;
        }
    }

    /** Sfondo scelto in Oxydian Theme (persist.oxytheme.bg), opaco; se non c'e' il valore dato. */
    public static int themeBackground(int def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            String v = (String) sp.getMethod("get", String.class, String.class).invoke(null, "persist.oxytheme.bg", "");
            if (v != null && v.length() >= 6) return 0xFF000000 | (int) Long.parseLong(v.substring(v.length() - 6), 16);
        } catch (Throwable ignored) {}
        return def;
    }

    /** Accento scelto in Oxydian Theme (persist.oxytheme.accent), opaco; senza Theme il valore dato. Vale nell'app e nei ganci. */
    public static int themeAccent(int def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            String v = (String) sp.getMethod("get", String.class, String.class).invoke(null, "persist.oxytheme.accent", "");
            if (v != null && v.length() >= 6) return 0xFF000000 | (int) Long.parseLong(v.substring(v.length() - 6), 16);
        } catch (Throwable ignored) {}
        return def;
    }

    public static int systemAccent() {
        try {
            int id = Resources.getSystem().getIdentifier("accent_material_dark", "color", "android");
            if (id == 0) return DEFAULT;
            Application app = AndroidAppHelper.currentApplication();
            Resources r = app != null ? app.getResources() : Resources.getSystem();
            return r.getColor(id, null) | 0xFF000000;
        } catch (Throwable t) {
            return DEFAULT;
        }
    }
}
