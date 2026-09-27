package it.tugaia56.obsidian.xposed.hooks.systemui;

import android.content.res.XResources;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;

import it.tugaia56.obsidian.xposed.ResourceManager;

/**
 * Xposed hook: replaces SystemUI wifi signal icon drawables with one of
 * Obsidian's DST wifi icon presets.
 *
 * Targets: com.android.systemui package
 *   — stat_signal_wifi_signal_0  (no signal / disconnected)
 *   — stat_signal_wifi_signal_1  (weak signal)
 *   — stat_signal_wifi_signal_2  (medium signal)
 *   — stat_signal_wifi_signal_3  (good signal)
 *   — stat_signal_wifi_signal_4  (full signal)
 *
 * Presets:
 *   DSTWIFI_AURORA     – filled arc rings (default-style but as vector)
 *   DSTWIFI_BARS       – rectangular signal bars
 *   DSTWIFI_BORDERLINE – outlined/stroked arc rings
 *
 * Uses the same ResourceManager.modRes-at-call-time pattern as DstCpbStyle
 * (see that class for the detailed rationale).
 */
public class DstWifiIconStyle {

    private static final String PKG_SYSTEMUI = "com.android.systemui";
    private static final String PKG_OBS      = "it.tugaia56.oxydian";
    private static final String PREF_PRESET  = "DST_PRESET_WIFI_ICON";
    private static final String PREFS_FILE   =
        "/data/user_de/0/it.tugaia56.oxydian/shared_prefs/it.tugaia56.oxydian_preferences.xml";

    /** Continuous icon scale, 1.0–1.5 (100%–150%). Below 1.0 is stock/original size. */
    public static final String PREF_ICON_SCALE = "wifi_icon_scale";

    // Drawable names to replace in com.android.systemui
    private static final String[] WIFI_TARGETS = {
        "stat_signal_wifi_signal_0",
        "stat_signal_wifi_signal_1",
        "stat_signal_wifi_signal_2",
        "stat_signal_wifi_signal_3",
        "stat_signal_wifi_signal_4",
    };

    // Corresponding Obsidian module drawable names (obs_wifi_{preset}_signal_{level})
    // Order mirrors OC's WIFI1-WIFI18 overlay folders.
    // 18 presets (Borderline removed — duplicate of Stroke, covered by "Applica entrambi").
    private static final String[] PRESET_NAMES = {
        "aurora", "bars", "dora", "faint_ui", "forlorn", "gradicon",
        "inside", "nothing_dot", "plumpy", "pui", "round", "sneaky",
        "stroke", "wavy", "weed", "xperia", "zigzag", "hos"
    };
    private static final String[] PRESET_KEYS = {
        "DSTWIFI_AURORA",      "DSTWIFI_BARS",       "DSTWIFI_DORA",
        "DSTWIFI_FAINT_UI",    "DSTWIFI_FORLORN",    "DSTWIFI_GRADICON",
        "DSTWIFI_INSIDE",      "DSTWIFI_NOTHING_DOT","DSTWIFI_PLUMPY",
        "DSTWIFI_PUI",         "DSTWIFI_ROUND",      "DSTWIFI_SNEAKY",
        "DSTWIFI_STROKE",      "DSTWIFI_WAVY",       "DSTWIFI_WEED",
        "DSTWIFI_XPERIA",      "DSTWIFI_ZIGZAG",     "DSTWIFI_HOS"
    };

    // Custom color for signal icons (Wi-Fi + Mobile share the same setting) — see
    // SignalStyleFragment "Colore Icone Segnale". Prefs are boolean/int, stored by Android as
    // self-closing <boolean name="X" value="true" /> / <int name="X" value="123" /> tags —
    // NOT the content-tag form ("<name>value</name>") used for strings, so they need their
    // own attribute-based parser (parseIntContent/parseStringContent below only handle content tags).
    public static final String PREF_COLOR_ON = "WIFI_ICON_CUSTOM_COLOR_ON";
    public static final String PREF_COLOR    = "WIFI_ICON_CUSTOM_COLOR";

    private static volatile boolean sPreloaded  = false;
    private static volatile String  sWifiPreset = null;
    static volatile float           sIconScale  = 1.0f;
    static volatile boolean         sColorOn    = false;
    static volatile int             sColor      = 0xFFFFFFFF;

    // ── Boot-time preload (file read) ─────────────────────────────────────────

    public static void preloadFromFile() {
        try {
            java.io.File f = new java.io.File(PREFS_FILE);
            if (!f.exists()) { preloadFromProps(); return; }
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            sWifiPreset = parseStringContent(sb.toString(), PREF_PRESET);
            sIconScale  = parseFloatAttr(sb.toString(), PREF_ICON_SCALE, 1.0f);
            sColorOn    = parseBoolAttr(sb.toString(), PREF_COLOR_ON, false);
            sColor      = parseIntAttr(sb.toString(), PREF_COLOR, 0xFFFFFFFF);
            sPreloaded  = true;
            XposedBridge.log("[ Obsidian ] DstWifiIconStyle.preload(file): preset=" + sWifiPreset + " scale=" + sIconScale
                    + " colorOn=" + sColorOn + " color=" + Integer.toHexString(sColor));
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] DstWifiIconStyle.preload(file) ERROR: " + t);
            preloadFromProps();
        }
    }

    private static void preloadFromProps() {
        try {
            Class<?> sp = XposedHelpers.findClass("android.os.SystemProperties", null);
            String preset = (String) XposedHelpers.callStaticMethod(sp, "get",
                    "persist.obsidian.dst.wifi_icon_preset", "");
            if (!preset.isEmpty()) {
                sWifiPreset = preset;
                sPreloaded  = true;
            }
            String colorOn = (String) XposedHelpers.callStaticMethod(sp, "get",
                    "persist.obsidian.dst.wifi_icon_color_on", "");
            String colorHex = (String) XposedHelpers.callStaticMethod(sp, "get",
                    "persist.obsidian.dst.wifi_icon_color", "");
            if (!colorOn.isEmpty())  sColorOn = "true".equals(colorOn);
            if (!colorHex.isEmpty()) {
                try { sColor = (int) Long.parseLong(colorHex, 16); } catch (NumberFormatException ignored) {}
            }
            String scaleStr = (String) XposedHelpers.callStaticMethod(sp, "get",
                    "persist.obsidian.dst.wifi_icon_scale", "");
            if (!scaleStr.isEmpty()) {
                try { sIconScale = Float.parseFloat(scaleStr); } catch (NumberFormatException ignored) {}
            }
            XposedBridge.log("[ Obsidian ] DstWifiIconStyle.preload(props): preset='" + preset
                    + "' colorOn=" + sColorOn + " color=" + Integer.toHexString(sColor));
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] DstWifiIconStyle.preload(props) ERROR: " + t);
        }
    }

    // ── Early registration (called from ResourceManager.handleInitPackageResources) ──

    public static void applyPreloaded(XC_InitPackageResources.InitPackageResourcesParam rp) {
        preloadFromFile();
        if (!sPreloaded || sWifiPreset == null) return;

        String presetName = presetDrawableName(sWifiPreset);
        if (presetName == null) return;

        java.util.List<XC_InitPackageResources.InitPackageResourcesParam> allRp =
                new java.util.ArrayList<>(ResourceManager.resparams.values());
        if (!allRp.contains(rp)) allRp.add(rp);

        for (XC_InitPackageResources.InitPackageResourcesParam target : allRp) {
            if (!PKG_SYSTEMUI.equals(target.packageName)) continue;
            try {
                for (int level = 0; level <= 4; level++) {
                    replaceWifiSignal(target, presetName, level);
                }
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] DstWifiIconStyle: error on " + target.packageName + ": " + t);
            }
        }
    }

    /**
     * Late re-registration — called from XPLauncher.initContext() after modRes is set.
     * Mirrors OC's initResources() from updatePrefs() to force cache invalidation.
     */
    public static void initResourcesLate() {
        if (!sPreloaded) preloadFromFile();
        if (!sPreloaded || sWifiPreset == null) return;

        String presetName = presetDrawableName(sWifiPreset);
        if (presetName == null) return;

        java.util.List<XC_InitPackageResources.InitPackageResourcesParam> allRp =
                new java.util.ArrayList<>(ResourceManager.resparams.values());

        XposedBridge.log("[ Obsidian ] DstWifiIconStyle.initResourcesLate: preset=" + sWifiPreset
                + " resparams=" + allRp.size()
                + " modRes=" + (ResourceManager.modRes != null ? "OK" : "NULL"));

        for (XC_InitPackageResources.InitPackageResourcesParam target : allRp) {
            if (!PKG_SYSTEMUI.equals(target.packageName)) continue;
            try {
                for (int level = 0; level <= 4; level++) {
                    replaceWifiSignal(target, presetName, level);
                }
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] DstWifiIconStyle.initResourcesLate error: " + t);
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Registers one setReplacement for stat_signal_wifi_signal_{level}.
     * The loader accesses ResourceManager.modRes at call time.
     */
    private static void replaceWifiSignal(
            XC_InitPackageResources.InitPackageResourcesParam rp,
            String presetName, int level) {

        final String target = WIFI_TARGETS[level];
        final String modName = "obs_wifi_" + presetName + "_signal_" + level;

        try {
            rp.res.setReplacement(PKG_SYSTEMUI, "drawable", target,
                new XResources.DrawableLoader() {
                    @Override
                    public Drawable newDrawable(XResources res, int id) {
                        android.content.res.Resources mr = ResourceManager.modRes;
                        if (mr == null) return null;
                        try {
                            int resId = mr.getIdentifier(modName, "drawable", PKG_OBS);
                            if (resId == 0) return null;
                            Drawable d = mr.getDrawable(resId, null);
                            if (sColorOn && d != null) {
                                d = d.mutate();
                                d.setTint(sColor);
                            }
                            return makeScaled(d, sIconScale);
                        } catch (Throwable t) {
                            XposedBridge.log("[ Obsidian ] DstWifiIconStyle: newDrawable error "
                                    + modName + ": " + t);
                            return null;
                        }
                    }
                });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] DstWifiIconStyle: setReplacement error "
                    + target + " → " + modName + ": " + t);
        }
    }

    /** Returns the drawable name part for a preset key, e.g. "aurora". */
    private static String presetDrawableName(String presetKey) {
        for (int i = 0; i < PRESET_KEYS.length; i++) {
            if (PRESET_KEYS[i].equals(presetKey)) return PRESET_NAMES[i];
        }
        return null;
    }

    public static String[] getPresetKeys()  { return PRESET_KEYS; }
    public static String[] getPresetNames() { return PRESET_NAMES; }

    private static Drawable makeScaled(Drawable base, float scale) {
        if (base == null || Math.abs(scale - 1f) < 0.01f) return base;
        final int w = Math.max(1, Math.round(base.getIntrinsicWidth()  * scale));
        final int h = Math.max(1, Math.round(base.getIntrinsicHeight() * scale));
        return new android.graphics.drawable.DrawableWrapper(base) {
            @Override public int getIntrinsicWidth()  { return w; }
            @Override public int getIntrinsicHeight() { return h; }
        };
    }

    private static boolean parseBoolAttr(String xml, String name, boolean def) {
        String needle = "name=\"" + name + "\"";
        int idx = xml.indexOf(needle);
        if (idx < 0) return def;
        int vi = xml.indexOf("value=\"", idx);
        if (vi < 0) return def;
        int vs = vi + 7;
        int ve = xml.indexOf("\"", vs);
        return ve > vs ? "true".equals(xml.substring(vs, ve).trim()) : def;
    }

    private static int parseIntAttr(String xml, String name, int def) {
        String needle = "name=\"" + name + "\"";
        int idx = xml.indexOf(needle);
        if (idx < 0) return def;
        int vi = xml.indexOf("value=\"", idx);
        if (vi < 0) return def;
        int vs = vi + 7;
        int ve = xml.indexOf("\"", vs);
        if (ve <= vs) return def;
        try { return Integer.parseInt(xml.substring(vs, ve).trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private static float parseFloatAttr(String xml, String name, float def) {
        String needle = "name=\"" + name + "\"";
        int idx = xml.indexOf(needle);
        if (idx < 0) return def;
        int vi = xml.indexOf("value=\"", idx);
        if (vi < 0) return def;
        int vs = vi + 7;
        int ve = xml.indexOf("\"", vs);
        if (ve <= vs) return def;
        try { return Float.parseFloat(xml.substring(vs, ve).trim()); }
        catch (NumberFormatException e) { return def; }
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
