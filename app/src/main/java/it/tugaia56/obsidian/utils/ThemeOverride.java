package it.tugaia56.obsidian.utils;

import android.os.SystemClock;

/**
 * Colori scelti in Oxydian Theme (app separata): accento e sfondo di sistema. Oxydian Theme li
 * pubblica come proprieta' di sistema (persist.oxytheme.accent / persist.oxytheme.bg, esadecimale
 * AARRGGBB oppure vuote = predefinito) e qui vengono letti da tutte le parti di Oxydian che prima
 * usavano "Preset accento" e "Preset sfondo" (hook e interfaccia). Vuoto = si usa il preset interno.
 */
public final class ThemeOverride {
    private static final String KEY_ACCENT = "persist.oxytheme.accent";
    private static final String KEY_BG = "persist.oxytheme.bg";
    private static final long TTL_MS = 3000;

    private static long sAt = -TTL_MS;
    private static Integer sAccent, sBg, sWifiIcon, sMobileIcon, sNavIcon;

    private ThemeOverride() {}

    /** Accento scelto in Oxydian Theme, o null se e' sul predefinito. */
    public static synchronized Integer accent() {
        load();
        return sAccent;
    }

    /** Sfondo di sistema scelto in Oxydian Theme, o null se e' sul predefinito. */
    public static synchronized Integer bg() {
        load();
        return sBg;
    }

    /** Colore delle icone Wi-Fi scelto in Oxydian Theme, o null. */
    public static synchronized Integer wifiIconColor() {
        load();
        return sWifiIcon;
    }

    /** Colore dell'icona del segnale mobile scelto in Oxydian Theme, o null. */
    public static synchronized Integer mobileIconColor() {
        load();
        return sMobileIcon;
    }

    /** Colore delle icone della barra di navigazione scelto in Oxydian Theme, o null. */
    public static synchronized Integer navIconColor() {
        load();
        return sNavIcon;
    }

    private static void load() {
        long now = SystemClock.elapsedRealtime();
        if (now - sAt < TTL_MS) return;
        sAt = now;
        sAccent = parse(get(KEY_ACCENT));
        sBg = parse(get(KEY_BG));
        sWifiIcon = parse(get("persist.oxytheme.wifi_icon_color"));
        sMobileIcon = parse(get("persist.oxytheme.mobile_icon_color"));
        sNavIcon = parse(get("persist.oxytheme.nav_icon_color"));
    }

    private static Integer parse(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() != 6 && s.length() != 8) return null;
        try {
            return (int) (Long.parseLong(s, 16) | 0xFF000000L);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String get(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class, String.class).invoke(null, key, "");
            if (v != null) return (String) v;
        } catch (Throwable ignored) {}
        try {
            // processi dove le API nascoste sono bloccate: via Xposed (se presente)
            Class<?> sp = de.robv.android.xposed.XposedHelpers.findClass("android.os.SystemProperties", null);
            Object v = de.robv.android.xposed.XposedHelpers.callStaticMethod(sp, "get", key, "");
            if (v != null) return (String) v;
        } catch (Throwable ignored) {}
        return "";
    }
}
