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
