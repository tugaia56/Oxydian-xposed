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

    private static volatile Runnable sOnChange;
    private static boolean sRootBusy, sRootSeeded;

    /** Chiamato (sul thread principale) quando nell'app cambia l'accento o lo sfondo scelti in Oxydian Theme. */
    public static void setOnChange(Runnable r) { sOnChange = r; }

    private static boolean inApp() {
        try { return it.tugaia56.obsidian.Obsidian.get() != null; } catch (Throwable t) { return false; }
    }

    /**
     * Nell'app Oxydian le proprieta' di sistema "persist.oxytheme.*" non si possono leggere (SELinux): si leggono da root,
     * in secondo piano, e l'ultimo valore resta salvato per il prossimo avvio.
     */
    private static void refreshViaRoot() {
        if (sRootBusy) return;
        sRootBusy = true;
        new Thread(() -> {
            try {
                java.util.List<String> out = com.topjohnwu.superuser.Shell.cmd(
                        "for k in accent bg wifi_icon_color mobile_icon_color nav_icon_color; do echo \"$k=$(getprop persist.oxytheme.$k)\"; done")
                        .exec().getOut();
                java.util.Map<String, String> m = new java.util.HashMap<>();
                for (String l : out) { int i = l.indexOf('='); if (i > 0) m.put(l.substring(0, i), l.substring(i + 1)); }
                if (m.isEmpty()) return;
                Integer a = parse(m.get("accent")), b = parse(m.get("bg"));
                boolean changed;
                synchronized (ThemeOverride.class) {
                    changed = !java.util.Objects.equals(a, sAccent) || !java.util.Objects.equals(b, sBg);
                    sAccent = a; sBg = b;
                    sWifiIcon = parse(m.get("wifi_icon_color"));
                    sMobileIcon = parse(m.get("mobile_icon_color"));
                    sNavIcon = parse(m.get("nav_icon_color"));
                    sRootSeeded = true;
                }
                try {
                    ObsidianPrefs.putString("theme_override_cache", m.get("accent") + "|" + m.get("bg") + "|" + m.get("wifi_icon_color")
                            + "|" + m.get("mobile_icon_color") + "|" + m.get("nav_icon_color"));
                } catch (Throwable ignored) {}
                Runnable r = sOnChange;
                if (changed && r != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
            } catch (Throwable ignored) {
            } finally {
                sRootBusy = false;
            }
        }).start();
    }

    private static void load() {
        long now = SystemClock.elapsedRealtime();
        if (now - sAt < TTL_MS) return;
        sAt = now;
        if (inApp()) {
            if (!sRootSeeded) {
                // primo uso dopo l'avvio: l'ultimo valore salvato, in attesa della lettura da root
                try {
                    String c = ObsidianPrefs.getString("theme_override_cache", "");
                    if (!c.isEmpty()) {
                        String[] p = c.split("[|]", -1);
                        if (p.length >= 5) {
                            sAccent = parse(p[0]); sBg = parse(p[1]); sWifiIcon = parse(p[2]); sMobileIcon = parse(p[3]); sNavIcon = parse(p[4]);
                            sRootSeeded = true;
                        }
                    }
                } catch (Throwable ignored) {}
            }
            refreshViaRoot();
            return;
        }
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
