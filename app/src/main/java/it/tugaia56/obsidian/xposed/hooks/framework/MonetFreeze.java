package it.tugaia56.obsidian.xposed.hooks.framework;

import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.ResourceManager.resparams;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.content.res.XResources;
import android.graphics.Color;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import it.tugaia56.obsidian.utils.ColorUtils;
import it.tugaia56.obsidian.xposed.XposedMods;

public class MonetFreeze extends XposedMods {

    // ── ACCENT 1 ──────────────────────────────────────────────────────────────
    private static final String PREF_ACCENT1_ON    = "DST_ACCENT1_on";
    private static final String PREF_ACCENT1       = "DST_ACCENT1";

    // ── ACCENT 2 ──────────────────────────────────────────────────────────────
    private static final String PREF_ACCENT2_ON    = "DST_ACCENT2_on";
    private static final String PREF_ACCENT2       = "DST_ACCENT2";

    // ── ACCENT 3 (ripple) ─────────────────────────────────────────────────────
    private static final String PREF_ACCENT3_ON    = "DST_ACCENT3_on";
    private static final String PREF_ACCENT3       = "DST_ACCENT3";

    // ── BACKGROUND ────────────────────────────────────────────────────────────
    private static final String PREF_BG_ON         = "DST_BACKGROUND_on";
    private static final String PREF_BG            = "DST_BACKGROUND";

    // ── PIN ───────────────────────────────────────────────────────────────────
    private static final String PREF_PIN                   = "DST_PIN";
    private static final String PREF_PIN_CUSTOM_COLOR      = "DST_PIN_CUSTOM_COLOR";
    private static final String PREF_PIN_NUM               = "DST_PIN_NUM";
    private static final String PREF_PIN_NUM_CUSTOM_COLOR  = "DST_PIN_NUM_CUSTOM_COLOR";

    // Preloaded values (read from XML at boot, before Xprefs is available)
    private static volatile boolean sPreloaded              = false;
    private static volatile boolean sPreloadA1Enabled       = false;
    private static volatile int     sPreloadA1              = 0;
    private static volatile boolean sPreloadA2Enabled       = false;
    private static volatile int     sPreloadA2              = 0;
    private static volatile boolean sPreloadA3Enabled       = false;
    private static volatile int     sPreloadA3              = 0;
    private static volatile boolean sPreloadBgEnabled       = false;
    private static volatile int     sPreloadBg              = 0;
    private static volatile String  sPreloadPin             = null;
    private static volatile int     sPreloadPinCustomColor  = 0;
    private static volatile String  sPreloadPinNum          = null;
    private static volatile int     sPreloadPinNumCustomColor = 0;

    public MonetFreeze(Context context) { super(context); }

    @Override public void updatePrefs(String... Key) { initResources(); }

    @Override
    public void initResources() {
        var resParam = resparams.get(SYSTEM_UI);
        if (resParam == null) return;
        XResources xRes = resParam.res;

        if (Xprefs == null) return; // boot path handled by applyPreloaded

        boolean a1On = Xprefs.getBoolean(PREF_ACCENT1_ON, false);
        int     a1   = Xprefs.getInt(PREF_ACCENT1, Color.RED);
        boolean a2On = Xprefs.getBoolean(PREF_ACCENT2_ON, false);
        int     a2   = Xprefs.getInt(PREF_ACCENT2, 0xFF3700B3);
        boolean a3On = Xprefs.getBoolean(PREF_ACCENT3_ON, false);
        int     a3   = Xprefs.getInt(PREF_ACCENT3, 0x336200EE);
        boolean bgOn = Xprefs.getBoolean(PREF_BG_ON, false);
        int     bg   = Xprefs.getInt(PREF_BG, 0xFF1A1A2E);
        String pinPref = Xprefs.getString(PREF_PIN, null);
        String pinNumPref = Xprefs.getString(PREF_PIN_NUM, null);

        if (a1On) applyAccent1(xRes, a1);
        if (a2On) applyAccent2(xRes, a2);
        if (a3On) applyAccent3(xRes, a3);
        if (bgOn) applyBackground(xRes, bg);

        if (a1On) applyPin(xRes, pinPref, a1, pinNumPref);
    }

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {}
    @Override public boolean listensTo(String packageName) { return SYSTEM_UI.equals(packageName); }

    // ── Boot-time preload (before Xprefs available) ──────────────────────────

    public static void preloadFromFile() {
        final String xmlPath = "/data/user_de/0/it.tugaia56.obsidian/shared_prefs/"
                             + "it.tugaia56.obsidian_preferences.xml";
        try {
            java.io.File f = new java.io.File(xmlPath);
            if (!f.exists()) { preloadFromProps(); return; }
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            String xml = sb.toString();

            sPreloadA1Enabled = parseBoolAttr(xml, PREF_ACCENT1_ON);
            sPreloadA1        = parseInt(parseAttr(xml, PREF_ACCENT1, "value"), 0);
            sPreloadA2Enabled = parseBoolAttr(xml, PREF_ACCENT2_ON);
            sPreloadA2        = parseInt(parseAttr(xml, PREF_ACCENT2, "value"), 0);
            sPreloadA3Enabled = parseBoolAttr(xml, PREF_ACCENT3_ON);
            sPreloadA3        = parseInt(parseAttr(xml, PREF_ACCENT3, "value"), 0);
            sPreloadBgEnabled = parseBoolAttr(xml, PREF_BG_ON);
            sPreloadBg        = parseInt(parseAttr(xml, PREF_BG, "value"), 0);
            sPreloadPin       = parseStringContent(xml, PREF_PIN);
            sPreloadPinNum    = parseStringContent(xml, PREF_PIN_NUM);
            sPreloadPinCustomColor    = parseInt(parseAttr(xml, PREF_PIN_CUSTOM_COLOR, "value"), 0);
            sPreloadPinNumCustomColor = parseInt(parseAttr(xml, PREF_PIN_NUM_CUSTOM_COLOR, "value"), 0);
            sPreloaded = true;

            XposedBridge.log("[ Obsidian ] MonetFreeze.preload(file): a1=" + sPreloadA1Enabled
                    + " a2=" + sPreloadA2Enabled + " bg=" + sPreloadBgEnabled);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] MonetFreeze.preload(file) ERROR: " + t + " — trying props");
            preloadFromProps();
        }
    }

    /**
     * Fallback for boot: reads persist.obsidian.dst.* system properties written by the
     * app (via root shell / setprop) when DST colours are applied. Called when the XML
     * SharedPreferences file is not readable (EACCES) because initZygote runs too late.
     */
    private static void preloadFromProps() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method get = sp.getDeclaredMethod("get", String.class, String.class);
            get.setAccessible(true);

            String a1On  = (String) get.invoke(null, "persist.obsidian.dst.a1_on",  "");
            String a1    = (String) get.invoke(null, "persist.obsidian.dst.a1",      "");
            String a2On  = (String) get.invoke(null, "persist.obsidian.dst.a2_on",  "");
            String a2    = (String) get.invoke(null, "persist.obsidian.dst.a2",      "");
            String a3On  = (String) get.invoke(null, "persist.obsidian.dst.a3_on",  "");
            String a3    = (String) get.invoke(null, "persist.obsidian.dst.a3",      "");
            String bgOn  = (String) get.invoke(null, "persist.obsidian.dst.bg_on",  "");
            String bg    = (String) get.invoke(null, "persist.obsidian.dst.bg",      "");
            String pin   = (String) get.invoke(null, "persist.obsidian.dst.pin",     "");
            String pinNum= (String) get.invoke(null, "persist.obsidian.dst.pin_num", "");

            // If nothing was ever saved we get all empty strings — bail out
            if (a1On.isEmpty() && a2On.isEmpty() && bgOn.isEmpty()) return;

            sPreloadA1Enabled = "1".equals(a1On);
            sPreloadA1        = a1.isEmpty()  ? 0 : Integer.parseInt(a1);
            sPreloadA2Enabled = "1".equals(a2On);
            sPreloadA2        = a2.isEmpty()  ? 0 : Integer.parseInt(a2);
            sPreloadA3Enabled = "1".equals(a3On);
            sPreloadA3        = a3.isEmpty()  ? 0 : Integer.parseInt(a3);
            sPreloadBgEnabled = "1".equals(bgOn);
            sPreloadBg        = bg.isEmpty()  ? 0 : Integer.parseInt(bg);
            sPreloadPin       = pin.isEmpty()    ? null : pin;
            sPreloadPinNum    = pinNum.isEmpty() ? null : pinNum;
            sPreloaded = true;

            XposedBridge.log("[ Obsidian ] MonetFreeze.preload(props): a1=" + sPreloadA1Enabled
                    + " a2=" + sPreloadA2Enabled + " bg=" + sPreloadBgEnabled);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] MonetFreeze.preload(props) ERROR: " + t);
        }
    }

    public static void applyPreloaded(XC_InitPackageResources.InitPackageResourcesParam rp) {
        if (!SYSTEM_UI.equals(rp.packageName)) return;
        preloadFromFile(); // always re-read: zygote forks inherit stale sPreloaded=true
        if (!sPreloaded) return;
        XResources xRes = rp.res;

        if (sPreloadA1Enabled && sPreloadA1 != 0) {
            applyAccent1(xRes, sPreloadA1);
            applyPin(xRes, sPreloadPin, sPreloadA1, sPreloadPinNum);
        }
        if (sPreloadA2Enabled && sPreloadA2 != 0) applyAccent2(xRes, sPreloadA2);
        if (sPreloadA3Enabled && sPreloadA3 != 0) applyAccent3(xRes, sPreloadA3);
        if (sPreloadBgEnabled && sPreloadBg  != 0) applyBackground(xRes, sPreloadBg);
    }

    // ── Color application ────────────────────────────────────────────────────

    private static void applyAccent1(XResources xRes, int c) {
        setColor(xRes, "android", "accent_material_dark",  c);
        setColor(xRes, "android", "holo_blue_light",       c);
        setColor(xRes, "android", "system_secondary_container", c);
        setColor(xRes, "android", "system_accent1_100", c);
        setColor(xRes, "android", "system_accent1_200", c);
        setColor(xRes, "android", "system_accent1_300", c);
        setColor(xRes, "android", "system_accent1_400", c);
        setColor(xRes, "android", "system_accent1_500", c);
        setColor(xRes, "android", "system_accent1_600", c);
        setColor(xRes, "android", "system_accent1_700", c);
        setColor(xRes, "android", "system_accent2_100", c);
        setColor(xRes, "android", "system_accent2_200", c);
        setColor(xRes, "android", "system_accent2_300", c);
        setColor(xRes, "android", "system_accent2_400", c);
        setColor(xRes, "android", "system_accent2_500", c);
        setColor(xRes, "android", "system_accent2_600", c);
        setColor(xRes, "android", "system_accent2_700", c);
        setColor(xRes, "android", "system_accent3_100", c);
        setColor(xRes, "android", "system_accent3_200", c);
        setColor(xRes, "android", "system_accent3_300", c);
        setColor(xRes, "android", "system_accent3_400", c);
        setColor(xRes, "android", "system_accent3_500", c);
        setColor(xRes, "android", "system_accent3_600", c);
        setColor(xRes, "android", "system_accent3_700", c);
    }

    /** setReplacement wrapped in try/catch: skips resources absent in this firmware. */
    private static void setColor(XResources xRes, String pkg, String name, int color) {
        try { xRes.setReplacement(pkg, "color", name, color); }
        catch (Throwable ignored) {}
    }

    private static void applyAccent2(XResources xRes, int c) {
        xRes.setReplacement("android", "color", "accent_material_light", c);
    }

    private static void applyAccent3(XResources xRes, int c) {
        xRes.setReplacement("android", "color", "ripple_material_dark", c);
    }

    private static void applyBackground(XResources xRes, int bg) {
        // Base background resources
        xRes.setReplacement("android", "color", "background_dark",                 bg);
        xRes.setReplacement("android", "color", "background_device_default_dark",  bg);
        xRes.setReplacement("android", "color", "legacy_primary",                  bg);
        xRes.setReplacement("android", "color", "legacy_primary_dark",             bg);
        xRes.setReplacement("android", "color", "black",                           bg);
        xRes.setReplacement("android", "color", "primary_dark_material_dark",      bg);
        xRes.setReplacement("android", "color", "primary_material_dark",           bg);
        // Lighter variants (brightness-adjusted)
        xRes.setReplacement("android", "color", "background_floating_material_dark", ColorUtils.adjustColor(bg, 25));
        xRes.setReplacement("android", "color", "button_material_dark",             ColorUtils.adjustColor(bg, 30));
        xRes.setReplacement("android", "color", "holo_primary",                     ColorUtils.adjustColor(bg, 35));
        xRes.setReplacement("android", "color", "holo_light_primary_dark",          ColorUtils.adjustColor(bg, 40));
        xRes.setReplacement("android", "color", "button_material_light",            ColorUtils.adjustColor(bg, 45));
        xRes.setReplacement("android", "color", "holo_primary_dark",                ColorUtils.adjustColor(bg, 50));
        xRes.setReplacement("android", "color", "background_holo_dark",             ColorUtils.adjustColor(bg, 55));
        xRes.setReplacement("android", "color", "background_leanback_dark",         ColorUtils.adjustColor(bg, 60));
    }

    // ── PIN keyboard colors ───────────────────────────────────────────────────

    private static void applyPin(XResources xRes, String pinPref, int accent,
                                  String pinNumPref) {
        if (pinPref != null) {
            switch (pinPref) {
                case "DSTPINAccent":
                    applyPinBgColors(xRes, accent, false); break;
                case "DSTPINAccentShade":
                    applyPinBgColors(xRes, accent, true);  break;
                case "DSTPINCustom":
                    int c = Xprefs != null
                            ? Xprefs.getInt(PREF_PIN_CUSTOM_COLOR, Color.WHITE)
                            : sPreloadPinCustomColor;
                    if (c != 0) applyPinBgColors(xRes, c, false); break;
            }
        }
        if (pinNumPref != null) {
            switch (pinNumPref) {
                case "DSTNUMPINAccent":
                    applyPinNumColor(xRes, accent); break;
                case "DSTNUMPINAccentShade":
                    applyPinNumColor(xRes, 0x80000000 | (accent & 0x00FFFFFF)); break;
                case "DSTNUMPINCustom":
                    int nc = Xprefs != null
                            ? Xprefs.getInt(PREF_PIN_NUM_CUSTOM_COLOR, Color.WHITE)
                            : sPreloadPinNumCustomColor;
                    if (nc != 0) applyPinNumColor(xRes, nc); break;
            }
        }
    }

    private static void applyPinNumColor(XResources xRes, int color) {
        xRes.setReplacement(SYSTEM_UI, "color", "coui_numeric_keyboard_number_color",
                0xFF000000 | (color & 0x00FFFFFF));
    }

    private static void applyPinBgColors(XResources xRes, int accent, boolean shade) {
        int rgb    = accent & 0x00FFFFFF;
        int full   = 0xFF000000 | rgb;
        int ripple = 0x80000000 | rgb;
        int outer1 = 0xCC000000 | rgb;
        int outer2 = 0x40000000 | rgb;
        int outer3 = 0x21000000 | rgb;
        xRes.setReplacement(SYSTEM_UI, "color", "coui_numeric_keyboard_border_color",             full);
        xRes.setReplacement(SYSTEM_UI, "color", "coui_numeric_keyboard_inner_gradient_color_1",   ripple);
        xRes.setReplacement(SYSTEM_UI, "color", "coui_numeric_keyboard_inner_gradient_color_2",   ripple);
        xRes.setReplacement(SYSTEM_UI, "color", "coui_numeric_keyboard_upper_inner_shadow_color", shade ? ripple : full);
        xRes.setReplacement(SYSTEM_UI, "color", "coui_numeric_keyboard_outer_gradient_color_1",   outer1);
        xRes.setReplacement(SYSTEM_UI, "color", "coui_numeric_keyboard_outer_gradient_color_2",   outer2);
        xRes.setReplacement(SYSTEM_UI, "color", "coui_numeric_keyboard_outer_gradient_color_3",   outer3);
        xRes.setReplacement(SYSTEM_UI, "color", "coui_simple_lock_transparent_filled_rectangle_icon_color",   full);
        // 2026-09-08: era bianco fisso (0x33FFFFFF) — segnalato dall'utente, il pallino "vuoto"
        // (non ancora digitato) restava bianco invece di seguire l'accento come quello pieno.
        xRes.setReplacement(SYSTEM_UI, "color", "coui_simple_lock_transparent_outlined_rectangle_icon_color", outer2);
        // 2026-09-08: famiglia di risorse GEMELLA mai coperta, trovata via aapt2 dump reale su
        // SystemUI.apk (non per analogia) dopo che l'utente ha mandato una FOTO (non screenshot
        // — quello non cattura fedelmente questa animazione) mostrando i pallini del PIN teal
        // appena dopo il boot. "coui_simple_lock_*" (senza "transparent_") e soprattutto
        // "kgd_color_simple_lock_*" (prefisso "kgd" = KeyGuarD) sono probabilmente le risorse
        // VERE lette dalla schermata di sblocco PIN, distinte da quelle "transparent_" già
        // coperte sopra (che potrebbero servire a un altro elemento simple-lock).
        setColor(xRes, SYSTEM_UI, "coui_simple_lock_filled_rectangle_icon_color",        full);
        setColor(xRes, SYSTEM_UI, "coui_simple_lock_filled_rectangle_icon_dark_color",   full);
        setColor(xRes, SYSTEM_UI, "coui_simple_lock_outlined_rectangle_icon_color",      outer2);
        setColor(xRes, SYSTEM_UI, "coui_simple_lock_outlined_rectangle_icon_dark_color", outer2);
        setColor(xRes, SYSTEM_UI, "kgd_color_simple_lock_filled_rectangle_icon_color",   full);
        setColor(xRes, SYSTEM_UI, "kgd_color_simple_lock_outlined_rectangle_icon_color", outer2);
        xRes.setReplacement(SYSTEM_UI, "color", "coui_numeric_keyboard_dark_word_text_normal_color",       full);
        xRes.setReplacement(SYSTEM_UI, "color", "coui_numeric_keyboard_dark_word_text_normal_light_color", full);
    }

    // ── XML parsing helpers ───────────────────────────────────────────────────

    private static int parseInt(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    private static boolean parseBoolAttr(String xml, String name) {
        return "true".equalsIgnoreCase(parseAttr(xml, name, "value"));
    }

    private static String parseAttr(String xml, String name, String attr) {
        int idx = xml.indexOf("name=\"" + name + "\"");
        if (idx < 0) return null;
        String key = attr + "=\"";
        int s = xml.indexOf(key, idx);
        if (s < 0) return null;
        s += key.length();
        int e = xml.indexOf("\"", s);
        return e < 0 ? null : xml.substring(s, e);
    }

    private static String parseStringContent(String xml, String name) {
        String needle = "name=\"" + name + "\">";
        int idx = xml.indexOf(needle);
        if (idx < 0) return null;
        int s = idx + needle.length();
        int e = xml.indexOf("<", s);
        return e <= s ? null : xml.substring(s, e).trim();
    }
}
