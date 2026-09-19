package it.tugaia56.obsidian.utils;

import android.content.Context;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import it.tugaia56.obsidian.ui.models.DarkShadowItem;

public class DarkShadowUtils {

    public static final String PREF_PREFIX               = "DST_";
    public static final String PREF_PIN                  = "DST_PIN";
    public static final String PREF_PIN_NUM              = "DST_PIN_NUM";
    public static final String PREF_PIN_CUSTOM_COLOR     = "DST_PIN_CUSTOM_COLOR";
    public static final String PREF_PIN_NUM_CUSTOM_COLOR = "DST_PIN_NUM_CUSTOM_COLOR";

    private static final String PKG_FRAMEWORK = "android";

    // ── Resource lists ────────────────────────────────────────────────────────

    /** ACCENT1 — main dark accent, flat (no tonal variation needed for these) */
    private static final List<String> ACCENT1_RES = Arrays.asList(
        "accent_material_dark",
        "holo_blue_light",
        "system_secondary_container"
    );

    /**
     * Material You tonal palette (system_accentN_100..700) — 2026-09-12: these were
     * previously flattened to the SAME exact accent color as everything else in
     * ACCENT1_RES. That broke real UI (Google Dialer's selected tab: light container
     * fill using _100 + dark text using _700, both landing on the identical color =
     * invisible text — found live, confirmed via cmd overlay lookup + screenshot pixel
     * sampling). Stock Android gives each numbered tone a different LIGHTNESS of the
     * same hue specifically so container/text pairs contrast — _100 is quite light,
     * _700 quite dark, matching the real system_accent1_* dump values on this device
     * (_100=0xffe2dfff light lilac ... _700=0xff2d00e5 near-black purple). Re-creates
     * that same shape around the user's own accent hue via ColorUtils.blendTone()
     * (real white/black blend, not adjustColor()'s multiplicative scaling — which can't
     * lighten an already-saturated channel like blue=255 in #908DFF any further).
     */
    private static final Map<String, Integer> ACCENT_TONE_ADJUST;
    static {
        ACCENT_TONE_ADJUST = new LinkedHashMap<>();
        for (String family : new String[]{"system_accent1_", "system_accent2_", "system_accent3_"}) {
            ACCENT_TONE_ADJUST.put(family + "100", 100);
            ACCENT_TONE_ADJUST.put(family + "200", 0);
            ACCENT_TONE_ADJUST.put(family + "300", 22);
            ACCENT_TONE_ADJUST.put(family + "400", 8);
            ACCENT_TONE_ADJUST.put(family + "500", 0);
            ACCENT_TONE_ADJUST.put(family + "600", 0);
            ACCENT_TONE_ADJUST.put(family + "700", 0);
        }
    }

    /** ACCENT2 — light accent variant */
    private static final List<String> ACCENT2_RES = Arrays.asList(
        "accent_material_light"
    );

    /** ACCENT3 — ripple / touch feedback */
    private static final List<String> ACCENT3_RES = Arrays.asList(
        "ripple_material_dark"
    );

    /** BACKGROUND — system dark background resources (same as OC) */
    private static final List<String> BACKGROUND_RES = Arrays.asList(
        "background_dark",
        "background_device_default_dark",
        "legacy_primary",
        "legacy_primary_dark",
        "black",
        "primary_dark_material_dark",
        "primary_material_dark",
        // Sfondo di fallback di molti AlertDialog stock (es. "Usa USB per") — il tema
        // Substratum dell'utente lo mappa a background_dark in tutti i pacchetti, ma DST
        // non lo fabbricava ancora: restava al valore Material hardcoded #ff303030.
        "material_grey_850"
    );

    /**
     * Background lighter variants (cards, buttons, dialogs, etc.).
     * Values are brightness offsets: +25% = slightly lighter than base, etc.
     */
    private static final Map<String, Integer> BACKGROUND_ADJUST;
    static {
        BACKGROUND_ADJUST = new LinkedHashMap<>();
        BACKGROUND_ADJUST.put("background_floating_material_dark", 25);
        BACKGROUND_ADJUST.put("button_material_dark",              30);
        BACKGROUND_ADJUST.put("holo_primary",                      35);
        BACKGROUND_ADJUST.put("holo_light_primary_dark",           40);
        BACKGROUND_ADJUST.put("button_material_light",             45);
        BACKGROUND_ADJUST.put("holo_primary_dark",                 50);
        BACKGROUND_ADJUST.put("background_holo_dark",              55);
        BACKGROUND_ADJUST.put("background_leanback_dark",          60);
    }

    // ── Item factory ─────────────────────────────────────────────────────────

    public static List<DarkShadowItem> getItems(Context ctx) {
        List<DarkShadowItem> list = new ArrayList<>();

        // ── Accent 1: Accento Principale ────────────────────────────────────
        int c1 = ObsidianPrefs.getInt(PREF_PREFIX + "ACCENT1", 0xFF6200EE);
        boolean on1 = ObsidianPrefs.getBoolean(PREF_PREFIX + "ACCENT1_on", false);
        list.add(new DarkShadowItem(
                ctx.getString(it.tugaia56.obsidian.R.string.section_accent1),
                "ACCENT1",
                List.of(PKG_FRAMEWORK), ACCENT1_RES, ACCENT_TONE_ADJUST,
                c1, on1));

        // ── Accent 2: Accento Chiaro ─────────────────────────────────────────
        int c2 = ObsidianPrefs.getInt(PREF_PREFIX + "ACCENT2", 0xFF3700B3);
        boolean on2 = ObsidianPrefs.getBoolean(PREF_PREFIX + "ACCENT2_on", false);
        list.add(new DarkShadowItem(
                ctx.getString(it.tugaia56.obsidian.R.string.section_accent2),
                "ACCENT2",
                List.of(PKG_FRAMEWORK), ACCENT2_RES, null,
                c2, on2));

        // ── Accent 3: Ripple ─────────────────────────────────────────────────
        int c3 = ObsidianPrefs.getInt(PREF_PREFIX + "ACCENT3", 0x336200EE);
        boolean on3 = ObsidianPrefs.getBoolean(PREF_PREFIX + "ACCENT3_on", false);
        list.add(new DarkShadowItem(
                ctx.getString(it.tugaia56.obsidian.R.string.section_accent3),
                "ACCENT3",
                List.of(PKG_FRAMEWORK), ACCENT3_RES, null,
                c3, on3));

        // ── Background ───────────────────────────────────────────────────────
        // Applies FabricatedOverlay to framework background resources (like OC).
        // The saved color is also read by the QsBackground hook for solid QS BG.
        int bg = ObsidianPrefs.getInt(PREF_PREFIX + "BACKGROUND", 0xFF1A1A2E);
        boolean bgOn = ObsidianPrefs.getBoolean(PREF_PREFIX + "BACKGROUND_on", false);
        list.add(new DarkShadowItem(
                ctx.getString(it.tugaia56.obsidian.R.string.dst_background),
                "BACKGROUND",
                List.of(PKG_FRAMEWORK), BACKGROUND_RES, BACKGROUND_ADJUST,
                bg, bgOn));

        return list;
    }

    public static void saveColor(DarkShadowItem item) {
        ObsidianPrefs.putInt(PREF_PREFIX + item.getOverlayName(), item.getColor());
        ObsidianPrefs.putBoolean(PREF_PREFIX + item.getOverlayName() + "_on", true);
    }
}
