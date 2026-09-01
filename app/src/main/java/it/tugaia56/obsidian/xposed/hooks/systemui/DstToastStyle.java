package it.tugaia56.obsidian.xposed.hooks.systemui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

import android.content.res.XResources;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;

/**
 * Xposed hook: replaces SystemUI toast drawables with one of 12 DST Toast presets —
 * ispirati ai 12 stili reali TSTFRM1-12 di OC (struttura semplificata dove OC usa layer
 * multipli/asset compilati, ma stessa varietà visiva: 12 stili distinti, non 8).
 * All drawables are programmatic (GradientDrawable / LayerDrawable) — no XModuleResources.
 * Background and accent colors come from the OBS user-set values (sBg / sAccent).
 *
 * Targets: com.android.systemui
 * Resources replaced:
 *   toast_frame, toast_double_line_bg, toast_mutl_line_bg,
 *   toast_sub_double_line_bg, toast_sub_mutl_line_bg
 *
 * Preset codes (stored in DST_PRESET_TOAST):
 *   DSTTST1  – Solido              BG fill, angoli 24dp
 *   DSTTST2  – Bordo Sottile       BG fill + stroke accent 1.5dp
 *   DSTTST3  – Bordo Spesso        BG fill + stroke accent 4dp
 *   DSTTST4  – Accento Solido      Accent fill, angoli 24dp
 *   DSTTST5  – Semi Trasparente    #80000000 fill
 *   DSTTST6  – Accento Sfumato     Gradiente diagonale accent → accent scuro
 *   DSTTST7  – Vetro               Gradiente accent + velo semi-trasparente (glass)
 *   DSTTST8  – Grigio              Anello grigio (BDBDBD→F0F0F0) su base accent — non più
 *                                  un riempimento pieno chiaro: il testo resta sull'accent
 *   DSTTST9  – Accento Doppio      Strisce accent sopra/sotto, corpo BG al centro
 *   DSTTST10 – Neutro Trasparente  Grigio scuro neutro al 60% alpha
 *   DSTTST11 – Accento + Overlay   Gradiente accent alpha + velo nero 30%
 *   DSTTST12 – Pillola Asimmetrica Angoli disuguali, gradiente accent alpha + corpo BG
 */
public class DstToastStyle {

    private static final String PKG_SYSTEMUI = "com.android.systemui";
    private static final String PREF_PRESET  = "DST_PRESET_TOAST";
    private static final String PREF_ACCENT1 = "DST_ACCENT1";
    private static final String PREF_BG      = "DST_BACKGROUND";
    private static final String PREF_CORNER  = "DST_TOAST_CORNER";
    private static final String PREFS_FILE   =
        "/data/user_de/0/it.tugaia56.obsidian/shared_prefs/it.tugaia56.obsidian_preferences.xml";
    private static final int DEFAULT_CORNER_DP = 24;

    // Toast drawables to replace in com.android.systemui
    private static final String[] TOAST_DRAWABLES = {
        "toast_frame",
        "toast_double_line_bg",
        "toast_mutl_line_bg",
        "toast_sub_double_line_bg",
        "toast_sub_mutl_line_bg",
    };

    private static volatile String sPreset   = null;
    private static volatile int    sAccent   = 0xFF9C27B0;
    private static volatile int    sBg       = 0xFF1B2029;
    private static volatile int    sCornerDp = DEFAULT_CORNER_DP;

    // ── Boot-time preload ────────────────────────────────────────────────────

    public static void preloadFromFile() {
        try {
            java.io.File f = new java.io.File(PREFS_FILE);
            if (!f.exists()) { preloadFromProps(); return; }
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            String xml = sb.toString();

            sPreset = parseStringContent(xml, PREF_PRESET);
            sAccent = parseInt(parseAttr(xml, PREF_ACCENT1, "value"), 0xFF9C27B0);
            sBg     = parseInt(parseAttr(xml, PREF_BG,      "value"), 0xFF1B2029);
            int corner = parseInt(parseAttr(xml, PREF_CORNER, "value"), DEFAULT_CORNER_DP);
            sCornerDp = (corner > 0) ? corner : DEFAULT_CORNER_DP;
            XposedBridge.log("[ Obsidian ] DstToastStyle.preload(file): preset=" + sPreset);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] DstToastStyle.preload(file) ERROR: " + t + " — trying props");
            preloadFromProps();
        }
    }

    private static void preloadFromProps() {
        try {
            Class<?> sp  = XposedHelpers.findClass("android.os.SystemProperties", null);
            String preset = (String) XposedHelpers.callStaticMethod(sp, "get", "persist.obsidian.dst.toast_preset", "");
            String a1Str  = (String) XposedHelpers.callStaticMethod(sp, "get", "persist.obsidian.dst.a1", "");
            String bgStr  = (String) XposedHelpers.callStaticMethod(sp, "get", "persist.obsidian.dst.bg", "");
            String cornStr = (String) XposedHelpers.callStaticMethod(sp, "get", "persist.obsidian.dst.toast_corner", "24");
            XposedBridge.log("[ Obsidian ] DstToastStyle.preloadFromProps: preset='" + preset + "'");
            if (preset.isEmpty()) return;
            sPreset = preset;
            if (!a1Str.isEmpty()) { try { sAccent = Integer.parseInt(a1Str); } catch (NumberFormatException ignored) {} }
            if (!bgStr.isEmpty())  { try { sBg     = Integer.parseInt(bgStr);  } catch (NumberFormatException ignored) {} }
            if (!cornStr.isEmpty()) {
                try {
                    int c = Integer.parseInt(cornStr);
                    sCornerDp = (c > 0) ? c : DEFAULT_CORNER_DP;
                } catch (NumberFormatException ignored) {}
            }
            XposedBridge.log("[ Obsidian ] DstToastStyle.preload(props): preset=" + sPreset);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] DstToastStyle.preload(props) ERROR: " + t);
        }
    }

    // ── Called from ResourceManager.handleInitPackageResources ───────────────

    public static void applyPreloaded(XC_InitPackageResources.InitPackageResourcesParam rp) {
        if (!PKG_SYSTEMUI.equals(rp.packageName)) return;
        XposedBridge.log("[ Obsidian ] DstToastStyle.applyPreloaded: CALLED for systemui");
        preloadFromFile();
        XposedBridge.log("[ Obsidian ] DstToastStyle.applyPreloaded: after preload preset=" + sPreset);
        if (sPreset == null) return;

        // Rilettura ad ogni chiamata (non "final" catturato una volta sola) — vedi lo stesso
        // commento in DstNotifStyle.applyPreloaded: senza questo il toast resterebbe fissato
        // al preset attivo al primo avvio di SystemUI, ignorando i cambi successivi.
        XResources.DrawableLoader loader = new XResources.DrawableLoader() {
            @Override
            public Drawable newDrawable(XResources res, int id) {
                // Sistema in chiaro: lasciamo il toast stock di OOS (già corretto in chiaro)
                // invece di applicare un preset pensato/testato solo per il toast scuro — vedi
                // lo stesso gate in DstNotifStyle.
                boolean isNight = (res.getConfiguration().uiMode
                        & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                        == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                if (!isNight) return null;
                preloadFromFile();
                String preset  = sPreset;
                int    accent  = sAccent;
                int    bg      = sBg;
                int    cornerDp = sCornerDp;
                if (preset == null) return new GradientDrawable();
                float density = res.getDisplayMetrics().density;
                if (density <= 0f) density = 3.0f;
                return buildToastBg(preset, accent, bg, density, cornerDp);
            }
        };

        for (String drawableName : TOAST_DRAWABLES) {
            try {
                rp.res.setReplacement(PKG_SYSTEMUI, "drawable", drawableName, loader);
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] DstToastStyle: error replacing " + drawableName + ": " + t);
            }
        }
    }

    // ── Drawable factory ──────────────────────────────────────────────────────

    /** Retrocompatibile — usa il raggio di default (24dp). */
    public static Drawable buildToastBg(String preset, int accent, int bg, float density) {
        return buildToastBg(preset, accent, bg, density, DEFAULT_CORNER_DP);
    }

    /** Public so the UI (preset preview picker) can render the exact same drawable used at runtime. */
    public static Drawable buildToastBg(String preset, int accent, int bg, float density, int cornerDp) {
        float corner = cornerDp * density;

        switch (preset) {
            case "DSTTST1": // Solido (BG color)
                return shape(bg, 0, 0, corner);

            case "DSTTST2": { // Outline 1.5dp accent
                int stroke = Math.round(1.5f * density);
                return shape(bg, accent, stroke, corner);
            }

            case "DSTTST3": { // Outline 4dp accent
                int stroke = Math.round(4f * density);
                return shape(bg, accent, stroke, corner);
            }

            case "DSTTST4": // Accent Solido
                return shape(accent, 0, 0, corner);

            case "DSTTST5": // Semi Trasparente
                return shape(0x80000000, 0, 0, corner);

            case "DSTTST6": // Accento Sfumato — gradiente diagonale accent → accent scuro
                return gradient(GradientDrawable.Orientation.TL_BR,
                        new int[]{accent, darken(accent, 0.6f)}, corner);

            case "DSTTST7": { // Vetro — gradiente accent + velo semi-trasparente inset
                GradientDrawable base = gradient(GradientDrawable.Orientation.TL_BR,
                        new int[]{accent, darken(accent, 0.7f)}, corner);
                int glassInset = Math.round(1f * density);
                GradientDrawable glass = simpleShape(0x99FFFFFF, 0, 0,
                        Math.max(0f, corner - glassInset));
                LayerDrawable layered = tintBlockedLayer(new Drawable[]{base, glass});
                layered.setLayerInset(1, glassInset, glassInset, glassInset, glassInset);
                return layered;
            }

            case "DSTTST8": { // Grigio — anello (non riempimento pieno) su base accent, così
                               // il testo resta leggibile sull'accent e non sul grigio chiaro
                int ring = Math.round(3f * density);
                return gradientRing(new int[]{0xFFBDBDBD, 0xFFF0F0F0},
                        GradientDrawable.Orientation.BOTTOM_TOP, ring, accent, corner);
            }

            case "DSTTST9": { // Accento Doppio — strisce accent sopra/sotto, corpo BG al centro
                GradientDrawable outer = simpleShape(accent, 0, 0, corner);
                int strip = Math.round(6f * density);
                GradientDrawable inner = simpleShape(bg, 0, 0, Math.max(0f, corner - strip));
                LayerDrawable layered = tintBlockedLayer(new Drawable[]{outer, inner});
                layered.setLayerInset(1, 0, strip, 0, strip);
                return layered;
            }

            case "DSTTST10": // Neutro Trasparente — grigio scuro neutro al 60% alpha
                return shape(0x99424242, 0, 0, corner);

            case "DSTTST11": { // Accento + Overlay — gradiente accent alpha + velo nero 30%
                GradientDrawable base = gradient(GradientDrawable.Orientation.TL_BR,
                        new int[]{withAlpha(accent, 0.8f), withAlpha(accent, 0.4f)}, corner);
                int inset = Math.round(4f * density);
                GradientDrawable overlay = simpleShape(0x4D000000, 0, 0,
                        Math.max(0f, corner - inset));
                LayerDrawable layered = tintBlockedLayer(new Drawable[]{base, overlay});
                layered.setLayerInset(1, inset, inset, inset, inset);
                return layered;
            }

            case "DSTTST12": { // Pillola Asimmetrica — angoli disuguali, accent alpha + corpo BG
                float rTL = corner, rTR = corner * 0.6f, rBL = corner * 0.6f, rBR = corner;
                GradientDrawable outer = newProtectedShape(GradientDrawable.Orientation.TL_BR,
                        new int[]{withAlpha(accent, 0.85f), withAlpha(accent, 0.5f)});
                outer.setCornerRadii(new float[]{rTL, rTL, rTR, rTR, rBR, rBR, rBL, rBL});
                int inset = Math.round(4f * density);
                GradientDrawable inner = newProtectedShape();
                inner.setColor(bg);
                float iTL = Math.max(0f, rTL - inset), iTR = Math.max(0f, rTR - inset);
                float iBL = Math.max(0f, rBL - inset), iBR = Math.max(0f, rBR - inset);
                inner.setCornerRadii(new float[]{iTL, iTL, iTR, iTR, iBR, iBR, iBL, iBL});
                LayerDrawable layered = tintBlockedLayer(new Drawable[]{outer, inner});
                layered.setLayerInset(1, inset, inset, inset, inset);
                return layered;
            }

            default:
                return null;
        }
    }

    private static GradientDrawable shape(int fill, int strokeColor, int strokeWidth,
                                          float cornerRadius) {
        return simpleShape(fill, strokeColor, strokeWidth, cornerRadius);
    }

    private static GradientDrawable simpleShape(int fill, int strokeColor, int strokeWidth,
                                                 float cornerRadius) {
        GradientDrawable d = newProtectedShape();
        d.setColor(fill);
        d.setCornerRadius(cornerRadius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    /** Sfondo a gradiente (2+ stop) con lo stesso blocco anti-tint di {@link #simpleShape}. */
    private static GradientDrawable gradient(GradientDrawable.Orientation orientation,
                                              int[] colors, float cornerRadius) {
        GradientDrawable d = newProtectedShape(orientation, colors);
        d.setCornerRadius(cornerRadius);
        return d;
    }

    /** Anello sfumato (non uno stroke a colore singolo): rettangolo esterno con fill a
     *  gradiente, contenuto interno più piccolo sovrapposto — il gradiente resta visibile
     *  solo come bordo di spessore {@code ringWidth}. */
    private static Drawable gradientRing(int[] ringColors, GradientDrawable.Orientation orientation,
                                          int ringWidth, int innerFill, float cornerRadius) {
        GradientDrawable outer = gradient(orientation, ringColors, cornerRadius);
        float innerRadius = Math.max(0f, cornerRadius - ringWidth);
        GradientDrawable inner = simpleShape(innerFill, 0, 0, innerRadius);
        LayerDrawable layered = tintBlockedLayer(new Drawable[]{outer, inner});
        layered.setLayerInset(1, ringWidth, ringWidth, ringWidth, ringWidth);
        return layered;
    }

    /** GradientDrawable che blocca il setTint/setColorFilter di OOS — OOS applica un tint di
     *  sistema sul drawable dopo averlo caricato; bloccandolo il nostro colore resta quello
     *  scelto dall'utente. Raggi/colore vanno impostati dal chiamante. */
    private static GradientDrawable newProtectedShape() {
        GradientDrawable d = new GradientDrawable() {
            @Override public void setTint(int tintColor) { /* block OOS tint */ }
            @Override public void setTintList(ColorStateList tint) { /* block OOS tint */ }
            @Override public void setTintMode(PorterDuff.Mode tintMode) { /* block */ }
            @Override public void setColorFilter(ColorFilter cf) { /* block OOS colorFilter */ }
            @Override public void setColorFilter(int color, PorterDuff.Mode mode) { /* block */ }
        };
        d.setShape(GradientDrawable.RECTANGLE);
        return d;
    }

    private static GradientDrawable newProtectedShape(GradientDrawable.Orientation orientation, int[] colors) {
        GradientDrawable d = new GradientDrawable(orientation, colors) {
            @Override public void setTint(int tintColor) { /* block OOS tint */ }
            @Override public void setTintList(ColorStateList tint) { /* block OOS tint */ }
            @Override public void setTintMode(PorterDuff.Mode tintMode) { /* block */ }
            @Override public void setColorFilter(ColorFilter cf) { /* block OOS colorFilter */ }
            @Override public void setColorFilter(int color, PorterDuff.Mode mode) { /* block */ }
        };
        d.setShape(GradientDrawable.RECTANGLE);
        return d;
    }

    /** LayerDrawable che blocca il setTint/setColorFilter di OOS a livello di wrapper — senza
     *  questo, anche con i singoli layer protetti, OOS può tintare il LayerDrawable stesso. */
    private static LayerDrawable tintBlockedLayer(Drawable[] layers) {
        return new LayerDrawable(layers) {
            @Override public void setTint(int tintColor) { /* block OOS tint */ }
            @Override public void setTintList(ColorStateList tint) { /* block OOS tint */ }
            @Override public void setTintMode(PorterDuff.Mode tintMode) { /* block */ }
            @Override public void setColorFilter(ColorFilter cf) { /* block OOS colorFilter */ }
            @Override public void setColorFilter(int color, PorterDuff.Mode mode) { /* block */ }
        };
    }

    private static int darken(int color, float factor) {
        return Color.argb(Color.alpha(color),
                Math.round(Color.red(color) * factor),
                Math.round(Color.green(color) * factor),
                Math.round(Color.blue(color) * factor));
    }

    private static int withAlpha(int color, float alphaFraction) {
        int a = Math.round(255 * alphaFraction);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    // ── XML parse helpers ─────────────────────────────────────────────────────

    private static int parseInt(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
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
