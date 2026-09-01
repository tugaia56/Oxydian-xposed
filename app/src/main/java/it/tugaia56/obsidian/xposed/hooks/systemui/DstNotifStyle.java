package it.tugaia56.obsidian.xposed.hooks.systemui;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;

import android.content.res.XResources;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;

/**
 * Xposed hook: replaces SystemUI notification background drawables with one of
 * 28 DST Notification Style presets — i 10 originali (4 trasparenti reali OC NFN23-26
 * + 6 altri) più 18 nuovi ispirati agli stili reali OC NFN2-21 mancanti (struttura
 * semplificata dove OC usa layer XML compilati, ma stessa varietà visiva).
 *
 * All drawables are programmatic (GradientDrawable) — no XModuleResources.
 * Background color comes from OBS sBg, accent from sAccent.
 * Corner radius comes from DST_NOTIF_CORNER (int, dp; default 24).
 *
 * Targets: com.android.systemui
 * Resources replaced: notification_material_bg, notification_material_bg_monet
 *
 * Preset codes (DST_PRESET_NOTIF):
 *   DSTNFNTOT – Thin Outline Transparent   (transparent bg + 1dp accent stroke)
 *   DSTNFNO25 – Outline Transparent 25%    (#40000000 bg + 2dp accent stroke)
 *   DSTNFNO50 – Outline Transparent 50%    (#80000000 bg + 2dp accent stroke)
 *   DSTNFNO75 – Outline Transparent 75%    (#BF000000 bg + 2dp accent stroke)
 *   DSTNFNOAC – Outline Accent             (transparent bg + 2dp accent stroke)
 *   DSTNFNST  – Semi Transparent           (sBg at 50% alpha)
 *   DSTNFNTR  – Transparent               (fully transparent)
 *   DSTNFNPB  – Pitch Black               (#FF000000)
 *   DSTNFNMN  – Monet                     (sBg solid fill)
 *   DSTNFNAS  – Accent Solid              (sAccent solid fill)
 *   DSTNFNLYR – Layers                    (bg scuro esterno + bg inserto interno 4dp)
 *   DSTNFNTO2 – Thin Outline              (bg pieno + bordo accent 2dp)
 *   DSTNFNBTM – Bottom Outline            (accent + bg inset solo dal basso)
 *   DSTNFNNM1 – Neumorph                  (gradiente verticale bg → bg scurito)
 *   DSTNFNSTK – Stack                     (accent + bg inset solo dall'alto, effetto scalino)
 *   DSTNFNSS  – Side Stack                (accent e bg sfalsati in diagonale)
 *   DSTNFNOL4 – Outline Spesso            (bg pieno + bordo accent 4dp)
 *   DSTNFNLT1 – Lighty                    (anello vetro chiaro su base nera 25% trasparente)
 *   DSTNFNLT2 – Lighty v2                 (anello vetro chiaro su base nera 50% trasparente)
 *   DSTNFNLT3 – Lighty v3                 (anello vetro chiaro su base gradiente nero 25%→50%)
 *   DSTNFNNM2 – Neumorph Outline          (anello grigio spesso su base accent)
 *   DSTNFNCP1 – Cyberponk                 (bg + due barrette accent agli angoli opposti)
 *   DSTNFNCP2 – Cyberponk v2              (accent + due pannelli bg sfalsati)
 *   DSTNFNTL  – Thread Line               (bg + sottile linea accent centrata)
 *   DSTNFNFD  – Faded                     (gradiente verticale bg → trasparente)
 *   DSTNFNDB  – Dumbbell                  (gradiente orizzontale accent-bg-accent)
 *   DSTNFNDL  – Duoline                   (gradiente verticale accent-bg-accent)
 *   DSTNFNIOS – iOS                       (gradiente verticale bg chiarito → bg)
 *   DSTNFNDOT – Puntini                   (texture di puntini, a runtime)
 *   DSTNFNLNS – Righe                     (hatching diagonale, a runtime)
 *   DSTNFNGRN – Rumore                    (grana/rumore fine, a runtime)
 *   DSTNFNHRT – Cuori                     (griglia di semi "cuori", a runtime)
 *   DSTNFNDIA – Quadri                    (griglia di semi "quadri", a runtime)
 *   DSTNFNCLB – Fiori                     (griglia di semi "fiori", a runtime)
 *   DSTNFNSPD – Picche                    (griglia di semi "picche", a runtime)
 *   DSTNFNCHK – Scacchiera                (quadretti alternati, a runtime)
 *   DSTNFNWAV – Onde                      (linee sinusoidali orizzontali, a runtime)
 *   DSTNFNXH  – Intreccio                 (hatching incrociato 45°/135°, a runtime)
 * Tutte le texture sopra condividono Dimensione/Opacità/Colore/Bordo regolabili da
 * "Regolazioni varie" (ThemeStyleFragment).
 */
public class DstNotifStyle {

    private static final String PKG_SYSTEMUI = "com.android.systemui";
    private static final String PREF_PRESET  = "DST_PRESET_NOTIF";
    private static final String PREF_ACCENT1 = "DST_ACCENT1";
    private static final String PREF_BG      = "DST_BACKGROUND";
    private static final String PREF_CORNER  = "DST_NOTIF_CORNER";
    private static final String PREF_TEX_SIZE  = "DST_NOTIF_TEXTURE_SIZE";  // % — Puntini/Righe/Rumore
    private static final String PREF_TEX_ALPHA = "DST_NOTIF_TEXTURE_ALPHA"; // % — idem
    private static final String PREF_TEX_COLOR_MODE   = "DST_NOTIF_TEXTURE_COLOR_MODE";   // "accent"/"custom"
    private static final String PREF_TEX_COLOR_CUSTOM = "DST_NOTIF_TEXTURE_COLOR_CUSTOM";
    private static final String PREF_TEX_BORDER_ON     = "DST_NOTIF_TEXTURE_BORDER_ENABLED";
    private static final String PREF_TEX_BORDER_MODE   = "DST_NOTIF_TEXTURE_BORDER_MODE";   // "accent"/"custom"
    private static final String PREF_TEX_BORDER_CUSTOM = "DST_NOTIF_TEXTURE_BORDER_CUSTOM";
    private static final String PREFS_FILE   =
        "/data/user_de/0/it.tugaia56.obsidian/shared_prefs/it.tugaia56.obsidian_preferences.xml";

    private static final int DEFAULT_CORNER_DP = 24;
    private static final int DEFAULT_TEX_SIZE_PCT  = 100;
    private static final int DEFAULT_TEX_ALPHA_PCT = 25;

    private static final String[] NOTIF_DRAWABLES = {
        "notification_material_bg",
        "notification_material_bg_monet", // usata quando i colori dinamici (Monet) sono
                                           // attivi — di norma sempre, di default, su OOS.
                                           // Senza questa la sostituzione non viene mai
                                           // caricata e non ha alcun effetto visibile.
        "notification_bg_normal_color",
        "notification_row_appear_bg",
        "notif_background",
    };

    private static volatile String sPreset    = null;

    /** Called by DstNotifStyleMod to check if a preset is active. */
    public static boolean isEnabled() { return sPreset != null; }
    private static volatile int    sAccent    = 0xFF9C27B0;
    private static volatile int    sBg        = 0xFF1B2029;
    private static volatile int    sCornerDp  = DEFAULT_CORNER_DP;
    private static volatile int    sTexSizePct  = DEFAULT_TEX_SIZE_PCT;
    private static volatile int    sTexAlphaPct = DEFAULT_TEX_ALPHA_PCT;
    private static volatile int    sTexColor       = 0xFF9C27B0; // risolto: sAccent o custom
    private static volatile boolean sTexBorderOn   = false;
    private static volatile int    sTexBorderColor = 0xFF9C27B0; // risolto: sAccent o custom

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

            sPreset   = parseStringContent(xml, PREF_PRESET);
            sAccent   = parseInt(parseAttr(xml, PREF_ACCENT1, "value"), 0xFF9C27B0);
            sBg       = parseInt(parseAttr(xml, PREF_BG,      "value"), 0xFF1B2029);
            int corner = parseInt(parseAttr(xml, PREF_CORNER, "value"), DEFAULT_CORNER_DP);
            sCornerDp = (corner > 0) ? corner : DEFAULT_CORNER_DP;
            int texSize  = parseInt(parseAttr(xml, PREF_TEX_SIZE,  "value"), DEFAULT_TEX_SIZE_PCT);
            sTexSizePct  = (texSize > 0) ? texSize : DEFAULT_TEX_SIZE_PCT;
            sTexAlphaPct = parseInt(parseAttr(xml, PREF_TEX_ALPHA, "value"), DEFAULT_TEX_ALPHA_PCT);

            String texColorMode = parseStringContent(xml, PREF_TEX_COLOR_MODE);
            int texColorCustom  = parseInt(parseAttr(xml, PREF_TEX_COLOR_CUSTOM, "value"), sAccent);
            sTexColor = "custom".equals(texColorMode) ? texColorCustom : sAccent;

            sTexBorderOn = "true".equals(parseAttr(xml, PREF_TEX_BORDER_ON, "value"));
            String texBorderMode = parseStringContent(xml, PREF_TEX_BORDER_MODE);
            int texBorderCustom  = parseInt(parseAttr(xml, PREF_TEX_BORDER_CUSTOM, "value"), sAccent);
            sTexBorderColor = "custom".equals(texBorderMode) ? texBorderCustom : sAccent;

            XposedBridge.log("[ Obsidian ] DstNotifStyle.preload(file): preset=" + sPreset
                    + " corner=" + sCornerDp);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] DstNotifStyle.preload(file) ERROR: " + t + " — trying props");
            preloadFromProps();
        }
    }

    private static void preloadFromProps() {
        try {
            Class<?> sp    = XposedHelpers.findClass("android.os.SystemProperties", null);
            String preset  = (String) XposedHelpers.callStaticMethod(sp, "get", "persist.obsidian.dst.notif_preset", "");
            String a1Str   = (String) XposedHelpers.callStaticMethod(sp, "get", "persist.obsidian.dst.a1",           "");
            String bgStr   = (String) XposedHelpers.callStaticMethod(sp, "get", "persist.obsidian.dst.bg",            "");
            String cornStr = (String) XposedHelpers.callStaticMethod(sp, "get", "persist.obsidian.dst.notif_corner",  "24");
            String texSzStr = (String) XposedHelpers.callStaticMethod(sp, "get", "persist.obsidian.dst.notif_tex_size",  "");
            String texAlStr = (String) XposedHelpers.callStaticMethod(sp, "get", "persist.obsidian.dst.notif_tex_alpha", "");
            String texColModeStr = (String) XposedHelpers.callStaticMethod(sp, "get", "persist.obsidian.dst.notif_tex_col_mode", "");
            String texColStr     = (String) XposedHelpers.callStaticMethod(sp, "get", "persist.obsidian.dst.notif_tex_col",      "");
            String texBrdOnStr   = (String) XposedHelpers.callStaticMethod(sp, "get", "persist.obsidian.dst.notif_tex_brd_on",   "");
            String texBrdModeStr = (String) XposedHelpers.callStaticMethod(sp, "get", "persist.obsidian.dst.notif_tex_brd_mode", "");
            String texBrdColStr  = (String) XposedHelpers.callStaticMethod(sp, "get", "persist.obsidian.dst.notif_tex_brd_col",  "");
            XposedBridge.log("[ Obsidian ] DstNotifStyle.preloadFromProps: preset='" + preset + "'");
            if (preset.isEmpty()) return;
            sPreset = preset;
            if (!a1Str.isEmpty())   { try { sAccent   = Integer.parseInt(a1Str);   } catch (NumberFormatException ignored) {} }
            if (!bgStr.isEmpty())   { try { sBg       = Integer.parseInt(bgStr);    } catch (NumberFormatException ignored) {} }
            if (!cornStr.isEmpty()) {
                try {
                    int c = Integer.parseInt(cornStr);
                    sCornerDp = (c > 0) ? c : DEFAULT_CORNER_DP;
                } catch (NumberFormatException ignored) {}
            }
            if (!texSzStr.isEmpty()) {
                try {
                    int s = Integer.parseInt(texSzStr);
                    sTexSizePct = (s > 0) ? s : DEFAULT_TEX_SIZE_PCT;
                } catch (NumberFormatException ignored) {}
            }
            if (!texAlStr.isEmpty()) {
                try { sTexAlphaPct = Integer.parseInt(texAlStr); } catch (NumberFormatException ignored) {}
            }
            int texColorCustom = sAccent;
            if (!texColStr.isEmpty()) { try { texColorCustom = Integer.parseInt(texColStr); } catch (NumberFormatException ignored) {} }
            sTexColor = "custom".equals(texColModeStr) ? texColorCustom : sAccent;

            sTexBorderOn = "true".equals(texBrdOnStr);
            int texBorderCustom = sAccent;
            if (!texBrdColStr.isEmpty()) { try { texBorderCustom = Integer.parseInt(texBrdColStr); } catch (NumberFormatException ignored) {} }
            sTexBorderColor = "custom".equals(texBrdModeStr) ? texBorderCustom : sAccent;
            XposedBridge.log("[ Obsidian ] DstNotifStyle.preload(props): preset=" + sPreset
                    + " corner=" + sCornerDp);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] DstNotifStyle.preload(props) ERROR: " + t);
        }
    }

    /**
     * Rilegge i prefs da disco e costruisce il drawable per il preset attivo — usato dal
     * hook diretto su NotificationBackgroundView.setCustomBackground() (vedi DstNotifBgView),
     * il vero punto in cui OOS applica lo sfondo notifica su questa build (non passa per
     * risorse XML sostituibili via XResources — vedi diagnostica DIAG getDrawable).
     * Ritorna null se nessun preset è attivo (nessuna modifica da applicare).
     */
    public static Drawable currentDrawable(float density, boolean isNight) {
        // Con sistema in tema chiaro lasciamo la card notifica stock di OOS (che già la
        // gestisce correttamente in chiaro) invece di applicare un preset pensato/testato solo
        // per lo shade scuro — evita di dover indovinare colori "giusti" per ogni preset in
        // chiaro, e chi tiene il sistema scuro non vede alcun cambiamento.
        if (!isNight) return null;
        preloadFromFile();
        if (sPreset == null) return null;
        if (density <= 0f) density = 3.0f;
        return buildNotifBg(sPreset, sAccent, sBg, density, sCornerDp, sTexSizePct, sTexAlphaPct,
                sTexColor, sTexBorderOn, sTexBorderColor);
    }

    // ── Called from ResourceManager.handleInitPackageResources ───────────────

    public static void applyPreloaded(XC_InitPackageResources.InitPackageResourcesParam rp) {
        if (!PKG_SYSTEMUI.equals(rp.packageName)) return;
        XposedBridge.log("[ Obsidian ] DstNotifStyle.applyPreloaded: CALLED for systemui");
        preloadFromFile();
        XposedBridge.log("[ Obsidian ] DstNotifStyle.applyPreloaded: after preload preset=" + sPreset);
        if (sPreset == null) return;

        // Non catturare preset/accent/bg/cornerDp come "final" qui: newDrawable() viene
        // richiamato dal sistema ad ogni redraw/aggiornamento notifica, anche molto tempo
        // dopo che l'utente ha cambiato preset nell'app — senza un riavvio di SystemUI la
        // closure resterebbe con i valori "congelati" al primo avvio del processo, mostrando
        // sempre il vecchio preset. Rileggiamo da disco ad ogni chiamata così il preset
        // attivo è sempre quello corrente, senza bisogno di riavviare SystemUI.
        XResources.DrawableLoader loader = new XResources.DrawableLoader() {
            @Override
            public Drawable newDrawable(XResources res, int id) {
                boolean isNight = (res.getConfiguration().uiMode
                        & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                        == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                if (!isNight) return null; // sistema in chiaro: card stock di OOS
                preloadFromFile();
                String preset   = sPreset;
                int    accent   = sAccent;
                int    bg       = sBg;
                int    cornerDp = sCornerDp;
                int    texSize  = sTexSizePct;
                int    texAlpha = sTexAlphaPct;
                int    texColor = sTexColor;
                boolean texBorderOn = sTexBorderOn;
                int    texBorderColor = sTexBorderColor;
                if (preset == null) return new GradientDrawable();
                float density = res.getDisplayMetrics().density;
                if (density <= 0f) density = 3.0f;
                return buildNotifBg(preset, accent, bg, density, cornerDp, texSize, texAlpha,
                        texColor, texBorderOn, texBorderColor);
            }
        };

        for (String drawableName : NOTIF_DRAWABLES) {
            try {
                rp.res.setReplacement(PKG_SYSTEMUI, "drawable", drawableName, loader);
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] DstNotifStyle: error replacing " + drawableName + ": " + t);
            }
        }
    }

    // ── Drawable factory ──────────────────────────────────────────────────────

    /** Retrocompatibile — usa i default di dimensione/opacità/colore/bordo texture. */
    public static Drawable buildNotifBg(String preset, int accent, int bg,
                                          float density, int cornerDp) {
        return buildNotifBg(preset, accent, bg, density, cornerDp,
                DEFAULT_TEX_SIZE_PCT, DEFAULT_TEX_ALPHA_PCT, accent, false, accent);
    }

    /** Retrocompatibile — usa i default di colore/bordo texture (accento, bordo assente). */
    public static Drawable buildNotifBg(String preset, int accent, int bg,
                                          float density, int cornerDp,
                                          int texSizePct, int texAlphaPct) {
        return buildNotifBg(preset, accent, bg, density, cornerDp,
                texSizePct, texAlphaPct, accent, false, accent);
    }

    /** Public so the UI (preset preview picker) can render the exact same drawable used at
     *  runtime. texSizePct/texAlphaPct/texColor/texBorderOn/texBorderColor sono usati solo dai
     *  preset texture (Puntini/Righe/Rumore) — ignorati da tutti gli altri, innocuo passarli
     *  sempre. texColor è già risolto (accento o personalizzato), non una "mode" string. */
    public static Drawable buildNotifBg(String preset, int accent, int bg,
                                          float density, int cornerDp,
                                          int texSizePct, int texAlphaPct,
                                          int texColor, boolean texBorderOn, int texBorderColor) {
        Drawable d = buildNotifBgRaw(preset, accent, bg, density, cornerDp, texSizePct, texAlphaPct,
                texColor, texBorderOn, texBorderColor);
        if (d == null) return null;
        // NotificationBackgroundView.setCustomBackground() -> setTint() -> getStatefulBackgroundLayer()
        // reads layer index 1 of whatever LayerDrawable it's given. A bare GradientDrawable (or
        // fewer than 2 layers) throws IndexOutOfBoundsException there and crashes SystemUI
        // (confirmed on-device: crashed on every boot, on the always-inflated NotificationShelf,
        // putting LSPosed into safe mode) — every case below now guarantees ≥2 layers.
        // But it's worse than a crash risk: on real notifications, whatever content actually
        // SITS at layer index 1 never gets drawn at all, even when no crash occurs (confirmed by
        // user report across ~15 presets — e.g. Cyberponk's two identical bars, one at index 1,
        // one at index 2: only the index-2 bar ever shows live, despite both rendering correctly
        // in the picker's own preview dialog). Every multi-layer case below therefore keeps
        // index 1 an inert transparent guard (indexOneGuard()) and puts all real content at
        // index 0 or index ≥2. This wrapper only still needs to handle the plain
        // single-GradientDrawable presets (gradients with no distinct layers).
        if (d instanceof LayerDrawable) return d;
        Drawable filler = buildNotifBgRaw(preset, accent, bg, density, cornerDp, texSizePct, texAlphaPct,
                texColor, texBorderOn, texBorderColor);
        return tintBlockedLayer(new Drawable[]{filler != null ? filler : d, indexOneGuard(cornerDp * density), d});
    }

    private static Drawable buildNotifBgRaw(String preset, int accent, int bg,
                                          float density, int cornerDp,
                                          int texSizePct, int texAlphaPct,
                                          int texColor, boolean texBorderOn, int texBorderColor) {
        float r = cornerDp * density;
        float texSizeMul   = texSizePct / 100f;
        float texDensity   = density * texSizeMul; // trucco: le classi texture calcolano
                                                     // spacing/raggio come K*density — passare
                                                     // una density già scalata evita di toccare
                                                     // la formula interna di ciascuna.
        float texAlphaFrac = Math.max(0f, Math.min(1f, texAlphaPct / 100f));
        // Bordo texture: stesso trucco/spessore di "Bordo Sottile" (DSTNFNTO2, 2dp) — solo
        // quando attivo, altrimenti il riempimento pieno resta senza contorno come prima.
        int texBorderWidth = texBorderOn ? Math.round(2f * density) : 0;
        int texBorderStrokeColor = texBorderOn ? texBorderColor : 0;

        switch (preset) {
            case "DSTNFNTOT": // Thin Outline Transparent
                return shape(Color.TRANSPARENT, accent, Math.round(1f * density), r);

            case "DSTNFNO25": // "Trasparenza Alta con bordo" (25% opaco = molto trasparente).
                              // Riempimento nero, non accento — richiesta esplicita dell'utente
                              // (l'accento tinto rendeva il preset simile ad "Accento Solido").
                              // Il bordo resta accento per restare distinguibile dal pannello.
                return shape(withAlpha(Color.BLACK, 0.25f), accent, Math.round(2f * density), r);

            case "DSTNFNO50": // "Trasparenza Media con bordo"
                return shape(withAlpha(Color.BLACK, 0.50f), accent, Math.round(2f * density), r);

            case "DSTNFNO75": // "Trasparenza Bassa con bordo" (75% opaco = poco trasparente).
                return shape(withAlpha(Color.BLACK, 0.75f), accent, Math.round(2f * density), r);

            case "DSTNFNOAC": // Outline Accent (black bg, accent stroke)
                return shape(Color.BLACK, accent, Math.round(2f * density), r);

            case "DSTNFNST": { // Semi Transparent (sBg at 50% alpha)
                int stColor = (bg & 0x00FFFFFF) | 0x80000000;
                return shape(stColor, 0, 0, r);
            }

            case "DSTNFNTR": // Transparent
                return shape(Color.TRANSPARENT, 0, 0, r);

            case "DSTNFNPB": // Pitch Black
                return shape(0xFF000000, 0, 0, r);

            case "DSTNFNMN": // Monet (sBg solid)
                return shape(bg, 0, 0, r);

            case "DSTNFNAS": // Accent Solid
                return shape(accent, 0, 0, r);

            case "DSTNFNLYR": { // Layers — bg scurito esterno + bg inserto interno 4dp
                GradientDrawable outer = simpleShape(darken(bg, 0.85f), 0, 0, r);
                int inset = Math.round(4f * density);
                GradientDrawable inner = simpleShape(bg, 0, 0, Math.max(0f, r - inset));
                LayerDrawable layered = tintBlockedLayer(new Drawable[]{outer, indexOneGuard(r), inner});
                layered.setLayerInset(2, inset, inset, inset, inset);
                return layered;
            }

            case "DSTNFNTO2": // Thin Outline — bg pieno + bordo accent 2dp
                return shape(bg, accent, Math.round(2f * density), r);

            case "DSTNFNBTM": { // Bottom Outline — accent, bg inset solo dal basso
                GradientDrawable outer = simpleShape(accent, 0, 0, r);
                int inset = Math.round(4f * density);
                GradientDrawable inner = simpleShape(bg, 0, 0, r);
                LayerDrawable layered = tintBlockedLayer(new Drawable[]{outer, indexOneGuard(r), inner});
                layered.setLayerInset(2, 0, 0, 0, inset);
                return layered;
            }

            case "DSTNFNNM1": // Neumorph — gradiente verticale bg → bg scurito
                return gradient(GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{bg, darken(bg, 0.85f)}, r);

            case "DSTNFNSTK": { // Stack — accent, bg inset solo dall'alto (scalino)
                GradientDrawable outer = simpleShape(accent, 0, 0, r);
                int inset = Math.round(8f * density);
                GradientDrawable inner = simpleShape(bg, 0, 0, r);
                LayerDrawable layered = tintBlockedLayer(new Drawable[]{outer, indexOneGuard(r), inner});
                layered.setLayerInset(2, 0, inset, 0, 0);
                return layered;
            }

            case "DSTNFNSS": { // Side Stack — accent e bg sfalsati in diagonale
                int inset = Math.round(8f * density);
                GradientDrawable back = simpleShape(accent, 0, 0, r);
                GradientDrawable front = simpleShape(bg, 0, 0, r);
                LayerDrawable layered = tintBlockedLayer(new Drawable[]{back, indexOneGuard(r), front});
                layered.setLayerInset(0, 0, 0, inset, inset);
                layered.setLayerInset(2, inset, inset, 0, 0);
                return layered;
            }

            case "DSTNFNOL4": // Outline Spesso — bg pieno + bordo accent 4dp
                return shape(bg, accent, Math.round(4f * density), r);

            case "DSTNFNLT1": // Lighty — anello vetro chiaro su base nera trasparente (25%,
                              // come Trasparenza Alta con bordo — richiesta utente, via l'accento)
                return gradientRing(new int[]{0x99FFFFFF, 0xB3FFFFFF},
                        GradientDrawable.Orientation.TL_BR, Math.round(2f * density),
                        withAlpha(Color.BLACK, 0.25f), r);

            case "DSTNFNLT2": // Lighty v2 — anello vetro chiaro su base nera trasparente (50%,
                              // come Trasparenza Media con bordo — richiesta utente, via l'accento)
                return gradientRing(new int[]{0xB3FFFFFF, 0xCCFFFFFF},
                        GradientDrawable.Orientation.TL_BR, Math.round(2f * density),
                        withAlpha(Color.BLACK, 0.50f), r);

            case "DSTNFNLT3": { // Lighty v3 — anello vetro chiaro, base gradiente nero 25%→50%
                int ringWidth = Math.round(2f * density);
                GradientDrawable outer = gradient(GradientDrawable.Orientation.TL_BR,
                        new int[]{0xB3FFFFFF, 0xCCFFFFFF}, r);
                float innerRadius = Math.max(0f, r - ringWidth);
                GradientDrawable inner = gradient(GradientDrawable.Orientation.TL_BR,
                        new int[]{withAlpha(Color.BLACK, 0.25f), withAlpha(Color.BLACK, 0.50f)}, innerRadius);
                LayerDrawable layered = tintBlockedLayer(new Drawable[]{outer, indexOneGuard(r), inner});
                layered.setLayerInset(2, ringWidth, ringWidth, ringWidth, ringWidth);
                return layered;
            }

            case "DSTNFNNM2": // Neumorph Outline v2 — come Duoline (accent-bg-accent) ma radiale,
                              // raggio piccolo (60dp) per anelli concentrati invece di sfumati
                return radialGradient(new int[]{accent, bg, accent}, r, density * 0.6f);

            case "DSTNFNCP1": { // Cyberponk — bg + due barrette accent agli angoli opposti
                GradientDrawable base = simpleShape(bg, 0, 0, r);
                GradientDrawable bar1 = simpleShape(accent, 0, 0, Math.round(1f * density));
                GradientDrawable bar2 = simpleShape(accent, 0, 0, Math.round(1f * density));
                LayerDrawable layered = tintBlockedLayer(new Drawable[]{base, indexOneGuard(r), bar1, bar2});
                int barW = Math.round(84f * density), barH = Math.round(3f * density);
                // Entrambe sul lato destro (alto e basso): a sinistra ci sono sempre icona app
                // + titolo, che le coprirebbero comunque — a destra c'è solo il timestamp/
                // chevron in alto e spazio libero in basso.
                int barInset = Math.round(6f * density);
                layered.setLayerSize(2, barW, barH);
                layered.setLayerGravity(2, Gravity.TOP | Gravity.END);
                layered.setLayerInset(2, 0, barInset, barInset, 0);
                layered.setLayerSize(3, barW, barH);
                layered.setLayerGravity(3, Gravity.BOTTOM | Gravity.END);
                layered.setLayerInset(3, 0, 0, barInset, barInset);
                return layered;
            }

            case "DSTNFNCP2": { // Cyberponk v2 — accent + due pannelli bg sfalsati
                // insetBig segna dove finisce il pannello sinistro/inizia quello destro —
                // tenuto appena oltre la larghezza tipica di icona+padding (~52dp) così il
                // bordo sfalsato cade subito dopo l'icona (che ha il suo sfondo opaco, non le
                // serve il bg del pannello) invece che a metà del testo.
                int insetSmall = Math.round(3f * density), insetBig = Math.round(52f * density);
                GradientDrawable base = simpleShape(accent, 0, 0, r);
                GradientDrawable panel1 = simpleShape(bg, 0, 0, r);
                GradientDrawable panel2 = simpleShape(bg, 0, 0, r);
                LayerDrawable layered = tintBlockedLayer(new Drawable[]{base, indexOneGuard(r), panel1, panel2});
                layered.setLayerInset(2, 0, insetSmall, insetBig, insetSmall);
                layered.setLayerInset(3, insetBig, insetSmall, 0, insetSmall);
                return layered;
            }

            case "DSTNFNTL": { // Thread Line — bg + sottile linea accent centrata
                GradientDrawable base = simpleShape(bg, 0, 0, r);
                GradientDrawable line = simpleShape(accent, 0, 0, Math.round(2f * density));
                LayerDrawable layered = tintBlockedLayer(new Drawable[]{base, indexOneGuard(r), line});
                layered.setLayerSize(2, Math.round(200f * density), Math.round(4f * density));
                layered.setLayerGravity(2, Gravity.CENTER);
                return layered;
            }

            case "DSTNFNFD": // Faded — gradiente verticale bg → trasparente
                return gradient(GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{bg, Color.TRANSPARENT}, r);

            case "DSTNFNDB": // Dumbbell — gradiente orizzontale accent-bg-accent
                return gradient(GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{accent, bg, accent}, r);

            case "DSTNFNDL": // Duoline — gradiente verticale accent-bg-accent
                return gradient(GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{accent, bg, accent}, r);

            case "DSTNFNDOT": { // Puntini — pattern di puntini, sottile, sopra bg pieno
                                // (prototipo "Notif PNG background": texture disegnata a
                                // runtime invece di un vero asset PNG — stessa idea, coerente
                                // con tutto il resto del file che è 100% programmatico).
                                // Dimensione/Opacità/Colore/Bordo regolabili da "Regolazioni varie".
                GradientDrawable base = simpleShape(bg, texBorderStrokeColor, texBorderWidth, r);
                GradientDrawable dots = dotGridOverlay(withAlpha(texColor, texAlphaFrac), texDensity, r);
                return tintBlockedLayer(new Drawable[]{base, indexOneGuard(r), dots});
            }

            case "DSTNFNLNS": { // Righe — hatching diagonale sottile, sopra bg pieno
                GradientDrawable base = simpleShape(bg, texBorderStrokeColor, texBorderWidth, r);
                GradientDrawable lines = hatchOverlay(withAlpha(texColor, texAlphaFrac), texDensity, r);
                return tintBlockedLayer(new Drawable[]{base, indexOneGuard(r), lines});
            }

            case "DSTNFNGRN": { // Rumore — grana/rumore fine, sopra bg pieno
                GradientDrawable base = simpleShape(bg, texBorderStrokeColor, texBorderWidth, r);
                GradientDrawable grain = grainOverlay(texColor, texDensity, r, texAlphaFrac);
                return tintBlockedLayer(new Drawable[]{base, indexOneGuard(r), grain});
            }

            case "DSTNFNHRT": { // Cuori — griglia di semi "cuori", sopra bg pieno
                GradientDrawable base = simpleShape(bg, texBorderStrokeColor, texBorderWidth, r);
                GradientDrawable hearts = suitGridOverlay(withAlpha(texColor, texAlphaFrac), texDensity, r, DstNotifStyle::paintHeart);
                return tintBlockedLayer(new Drawable[]{base, indexOneGuard(r), hearts});
            }

            case "DSTNFNDIA": { // Quadri — griglia di semi "quadri", sopra bg pieno
                GradientDrawable base = simpleShape(bg, texBorderStrokeColor, texBorderWidth, r);
                GradientDrawable diamonds = suitGridOverlay(withAlpha(texColor, texAlphaFrac), texDensity, r, DstNotifStyle::paintDiamond);
                return tintBlockedLayer(new Drawable[]{base, indexOneGuard(r), diamonds});
            }

            case "DSTNFNCLB": { // Fiori — griglia di semi "fiori", sopra bg pieno
                GradientDrawable base = simpleShape(bg, texBorderStrokeColor, texBorderWidth, r);
                GradientDrawable clubs = suitGridOverlay(withAlpha(texColor, texAlphaFrac), texDensity, r, DstNotifStyle::paintClub);
                return tintBlockedLayer(new Drawable[]{base, indexOneGuard(r), clubs});
            }

            case "DSTNFNSPD": { // Picche — griglia di semi "picche", sopra bg pieno
                GradientDrawable base = simpleShape(bg, texBorderStrokeColor, texBorderWidth, r);
                GradientDrawable spades = suitGridOverlay(withAlpha(texColor, texAlphaFrac), texDensity, r, DstNotifStyle::paintSpade);
                return tintBlockedLayer(new Drawable[]{base, indexOneGuard(r), spades});
            }

            case "DSTNFNCHK": { // Scacchiera — quadretti alternati, sopra bg pieno
                GradientDrawable base = simpleShape(bg, texBorderStrokeColor, texBorderWidth, r);
                GradientDrawable checker = checkerOverlay(withAlpha(texColor, texAlphaFrac), texDensity, r);
                return tintBlockedLayer(new Drawable[]{base, indexOneGuard(r), checker});
            }

            case "DSTNFNWAV": { // Onde — linee sinusoidali orizzontali ripetute, sopra bg pieno
                GradientDrawable base = simpleShape(bg, texBorderStrokeColor, texBorderWidth, r);
                GradientDrawable waves = waveOverlay(withAlpha(texColor, texAlphaFrac), texDensity, r);
                return tintBlockedLayer(new Drawable[]{base, indexOneGuard(r), waves});
            }

            case "DSTNFNXH": { // Intreccio — hatching incrociato (45°+135°), sopra bg pieno
                GradientDrawable base = simpleShape(bg, texBorderStrokeColor, texBorderWidth, r);
                GradientDrawable cross = crossHatchOverlay(withAlpha(texColor, texAlphaFrac), texDensity, r);
                return tintBlockedLayer(new Drawable[]{base, indexOneGuard(r), cross});
            }

            case "DSTNFNIOS": // iOS — prima era bg chiarito → bg, troppo simile a Neumorph.
                              // Un bianco quasi pieno (primo tentativo) rendeva illeggibile il
                              // testo bianco di OOS — grigio medio: resta chiaro/diverso da
                              // tutti gli altri preset (scuri) ma abbastanza scuro da leggere.
                return gradient(GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{0xF2888888, 0xF2707070}, r);

            default:
                return null;
        }
    }

    /**
     * Costruisce lo sfondo notifica: un solo GradientDrawable con solid+stroke insieme (come il
     * vero popup_background_material.xml di OC — <shape> con <solid> e <stroke> sullo stesso
     * layer, non un <layer-list>). La vecchia versione separava bordo e riempimento in due
     * GradientDrawable dentro un LayerDrawable per evitare un presunto scarto di curvatura a
     * raggi piccoli — ma un LayerDrawable è esattamente la struttura per cui oggi abbiamo
     * scoperto che OOS non disegna mai il contenuto reale del layer indice 1 dal vivo. Un
     * GradientDrawable singolo con solid+stroke evita il problema alla radice, oltre a
     * combaciare con l'unico riferimento OC (type3_OxygenOS_16/popup_background_material.xml)
     * di come OOS si aspetta che siano fatti questi sfondi.
     */
    private static Drawable shape(int fill, int strokeColor, int strokeWidth, float cornerRadius) {
        return simpleShape(fill, strokeColor, strokeWidth, cornerRadius);
    }

    /** Layer trasparente pieno che occupa SOLO l'indice 1 di un LayerDrawable. Confermato su
     *  device reale: qualunque contenuto visivo messo all'indice 1 (sia con setLayerInset che
     *  con setLayerSize+setLayerGravity) non viene mai disegnato dal vivo da OOS — nel dialog
     *  di anteprima (che non passa dal vero NotificationBackgroundView) invece sì, quindi il
     *  bug non è nel drawable costruito ma in come OOS rielabora quello specifico layer.
     *  Indice 0 e indice 2+ funzionano sempre: ogni preset multi-layer tiene qui un layer
     *  vuoto all'indice 1 e sposta il contenuto vero altrove. */
    private static GradientDrawable indexOneGuard(float cornerRadius) {
        return simpleShape(Color.TRANSPARENT, 0, 0, cornerRadius);
    }

    /** GradientDrawable che blocca il setTint/setColorFilter di OOS — OOS applica un tint di
     *  sistema sul drawable dopo averlo caricato, bloccandolo il nostro colore resta quello
     *  scelto dall'utente. */
    private static GradientDrawable simpleShape(int fill, int strokeColor, int strokeWidth,
                                                 float cornerRadius) {
        GradientDrawable d = new GradientDrawable() {
            @Override public void setTint(int tintColor) { /* block OOS tint */ }
            @Override public void setTintList(ColorStateList tint) { /* block OOS tint */ }
            @Override public void setTintMode(PorterDuff.Mode tintMode) { /* block */ }
            @Override public void setColorFilter(ColorFilter cf) { /* block OOS colorFilter */ }
            @Override public void setColorFilter(int color, PorterDuff.Mode mode) { /* block */ }
            // OOS chiama setAlpha() come parte della sua animazione/gestione stato notifica,
            // sovrascrivendo il canale alpha del colore scelto (un preset al 25% finiva
            // renderizzato quasi a piena opacità, confermato su device con lettura pixel reale).
            @Override public void setAlpha(int alpha) { /* block OOS alpha override */ }
        };
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(cornerRadius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    /** LayerDrawable che blocca il setTint/setColorFilter di OOS — senza questo, anche se i
     *  singoli layer sono protetti, OOS può tintare/filtrare il wrapper stesso una volta
     *  caricato, sovrascrivendo l'aspetto scelto dall'utente. */
    private static LayerDrawable tintBlockedLayer(Drawable[] layers) {
        return new LayerDrawable(layers) {
            @Override public void setTint(int tintColor) { /* block OOS tint */ }
            @Override public void setTintList(ColorStateList tint) { /* block OOS tint */ }
            @Override public void setTintMode(PorterDuff.Mode tintMode) { /* block */ }
            @Override public void setColorFilter(ColorFilter cf) { /* block OOS colorFilter */ }
            @Override public void setColorFilter(int color, PorterDuff.Mode mode) { /* block */ }
            // OOS chiama setAlpha() come parte della sua animazione/gestione stato notifica,
            // sovrascrivendo il canale alpha del colore scelto (un preset al 25% finiva
            // renderizzato quasi a piena opacità, confermato su device con lettura pixel reale).
            @Override public void setAlpha(int alpha) { /* block OOS alpha override */ }
        };
    }

    /** Sfondo a gradiente (2 o più stop) con lo stesso blocco anti-tint di {@link #simpleShape}. */
    private static GradientDrawable gradient(GradientDrawable.Orientation orientation,
                                              int[] colors, float cornerRadius) {
        GradientDrawable d = new GradientDrawable(orientation, colors) {
            @Override public void setTint(int tintColor) { /* block OOS tint */ }
            @Override public void setTintList(ColorStateList tint) { /* block OOS tint */ }
            @Override public void setTintMode(PorterDuff.Mode tintMode) { /* block */ }
            @Override public void setColorFilter(ColorFilter cf) { /* block OOS colorFilter */ }
            @Override public void setColorFilter(int color, PorterDuff.Mode mode) { /* block */ }
            // OOS chiama setAlpha() come parte della sua animazione/gestione stato notifica,
            // sovrascrivendo il canale alpha del colore scelto (un preset al 25% finiva
            // renderizzato quasi a piena opacità, confermato su device con lettura pixel reale).
            @Override public void setAlpha(int alpha) { /* block OOS alpha override */ }
        };
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(cornerRadius);
        return d;
    }

    /** Come {@link #gradient}, ma radiale invece che lineare. Il raggio è FISSO (calcolato a
     *  costruzione, non in {@code onBoundsChange}): quella versione dinamica non scattava mai
     *  nella pipeline live delle notifiche (confermato — il raggio restava a 0, mostrando solo
     *  il primo colore/accento in tinta unita), a differenza del dialog di anteprima dove
     *  invece funzionava. Un raggio fisso, larghezza tipica di una notifica reale, è meno
     *  preciso ma non dipende da un callback che lì semplicemente non arriva. */
    private static GradientDrawable radialGradient(int[] colors, float cornerRadius, float density) {
        GradientDrawable d = new GradientDrawable() {
            @Override public void setTint(int tintColor) { /* block OOS tint */ }
            @Override public void setTintList(ColorStateList tint) { /* block OOS tint */ }
            @Override public void setTintMode(PorterDuff.Mode tintMode) { /* block */ }
            @Override public void setColorFilter(ColorFilter cf) { /* block OOS colorFilter */ }
            @Override public void setColorFilter(int color, PorterDuff.Mode mode) { /* block */ }
        };
        d.setColors(colors);
        d.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        d.setGradientRadius(100f * density);
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(cornerRadius);
        return d;
    }

    /** Anello sfumato: rettangolo esterno con fill a gradiente, contenuto interno più piccolo
     *  sovrapposto — il gradiente resta visibile solo come bordo di spessore {@code ringWidth}. */
    private static Drawable gradientRing(int[] ringColors, GradientDrawable.Orientation orientation,
                                          int ringWidth, int innerFill, float cornerRadius) {
        GradientDrawable outer = gradient(orientation, ringColors, cornerRadius);
        float innerRadius = Math.max(0f, cornerRadius - ringWidth);
        GradientDrawable inner = simpleShape(innerFill, 0, 0, innerRadius);
        LayerDrawable layered = tintBlockedLayer(new Drawable[]{outer, indexOneGuard(cornerRadius), inner});
        layered.setLayerInset(2, ringWidth, ringWidth, ringWidth, ringWidth);
        return layered;
    }

    /** Texture a puntini disegnata a runtime (niente asset PNG) — griglia di piccoli cerchi
     *  colore accento, spaziatura/raggio in dp così scala con la densità come tutto il resto
     *  del file. Le posizioni si calcolano ad ogni draw() da getBounds() invece che essere
     *  cachate in onBoundsChange: quel callback si è già dimostrato inaffidabile nella pipeline
     *  live delle notifiche (vedi radialGradient sopra), disegnare dal vivo lo evita alla radice.
     *  DEVE estendere GradientDrawable (non un Drawable qualsiasi): NotificationBackgroundView.
     *  updateBackgroundRadii() fa un cast diretto a GradientDrawable su ogni layer per leggerne
     *  gli angoli — confermato dal vivo con un crash reale di SystemUI (ClassCastException,
     *  mandato LSPosed in safe mode) quando questo layer era un Drawable generico. */
    private static GradientDrawable dotGridOverlay(int dotColor, float density, float cornerRadius) {
        return new DotGridDrawable(dotColor, density, cornerRadius);
    }

    /** Classe nominata (non anonima) apposta: un GradientDrawable anonimo perde il proprio
     *  draw() custom se il sistema lo ricostruisce dal ConstantState (successo confermato dal
     *  vivo — nessun crash dopo il fix del cast, ma i puntini non comparivano mai: colore/raggio
     *  d'angolo restavano giusti, segno che veniva ricreato un GradientDrawable "pulito" dallo
     *  stato salvato, perdendo la sottoclasse). getConstantState() qui sotto ricostruisce sempre
     *  un'istanza VERA di DotGridDrawable, mai il GradientDrawable base. */
    private static final class DotGridDrawable extends GradientDrawable {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float mSpacing, mRadius, mCornerRadius;
        private final int mDotColor;

        DotGridDrawable(int dotColor, float density, float cornerRadius) {
            mDotColor = dotColor;
            mSpacing = 14f * density;
            mRadius = 1.0f * density;
            mCornerRadius = cornerRadius;
            mPaint.setColor(dotColor);
            setShape(GradientDrawable.RECTANGLE);
            setColor(Color.TRANSPARENT);
            setCornerRadius(cornerRadius);
        }

        @Override public void draw(Canvas canvas) {
            super.draw(canvas); // riempimento trasparente della shape sottostante
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            for (float y = mSpacing / 2f; y < b.height(); y += mSpacing) {
                for (float x = mSpacing / 2f; x < b.width(); x += mSpacing) {
                    canvas.drawCircle(b.left + x, b.top + y, mRadius, mPaint);
                }
            }
        }

        @Override public void setTint(int tintColor) { /* block OOS tint */ }
        @Override public void setTintList(ColorStateList tint) { /* block OOS tint */ }
        @Override public void setTintMode(PorterDuff.Mode tintMode) { /* block */ }
        @Override public void setColorFilter(ColorFilter cf) { /* block OOS colorFilter */ }
        @Override public void setColorFilter(int color, PorterDuff.Mode mode) { /* block */ }
        // Stesso blocco anti-override di setAlpha usato da tutti gli altri preset — OOS
        // altrimenti sovrascrive il canale alpha scelto durante le sue animazioni.
        @Override public void setAlpha(int alpha) { /* block OOS alpha override */ }

        @Override public ConstantState getConstantState() {
            return new ConstantState() {
                @Override public Drawable newDrawable() {
                    // density passato = mSpacing/14f ricostruisce esattamente lo stesso mSpacing
                    // di partenza (mRadius ne è derivato con lo stesso fattore, coerente).
                    return new DotGridDrawable(mDotColor, mSpacing / 14f, mCornerRadius);
                }
                @Override public int getChangingConfigurations() { return 0; }
            };
        }
    }

    private static GradientDrawable hatchOverlay(int lineColor, float density, float cornerRadius) {
        return new HatchDrawable(lineColor, density, cornerRadius);
    }

    /** Righe diagonali sottili (45°) — stessa tecnica/gotcha di {@link DotGridDrawable}: classe
     *  nominata, deve estendere GradientDrawable, getConstantState() ricostruisce la sottoclasse
     *  vera. Disegna oltre i bordi (lunghezza = diagonale del rettangolo) prima di ruotare, così
     *  copre tutto l'angolo visibile indipendentemente da dove cade il centro di rotazione. */
    private static final class HatchDrawable extends GradientDrawable {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float mSpacing, mCornerRadius;
        private final int mLineColor;

        HatchDrawable(int lineColor, float density, float cornerRadius) {
            mLineColor = lineColor;
            mSpacing = 7f * density;
            mCornerRadius = cornerRadius;
            mPaint.setColor(lineColor);
            mPaint.setStrokeWidth(Math.max(1f, 1f * density));
            setShape(GradientDrawable.RECTANGLE);
            setColor(Color.TRANSPARENT);
            setCornerRadius(cornerRadius);
        }

        @Override public void draw(Canvas canvas) {
            super.draw(canvas);
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            float diag = (float) Math.sqrt((double) b.width() * b.width() + (double) b.height() * b.height());
            canvas.save();
            canvas.clipRect(b);
            canvas.rotate(45f, b.centerX(), b.centerY());
            float half = diag / 2f;
            for (float y = -half; y < half; y += mSpacing) {
                canvas.drawLine(b.centerX() - half, b.centerY() + y, b.centerX() + half, b.centerY() + y, mPaint);
            }
            canvas.restore();
        }

        @Override public void setTint(int tintColor) { /* block OOS tint */ }
        @Override public void setTintList(ColorStateList tint) { /* block OOS tint */ }
        @Override public void setTintMode(PorterDuff.Mode tintMode) { /* block */ }
        @Override public void setColorFilter(ColorFilter cf) { /* block OOS colorFilter */ }
        @Override public void setColorFilter(int color, PorterDuff.Mode mode) { /* block */ }
        @Override public void setAlpha(int alpha) { /* block OOS alpha override */ }

        @Override public ConstantState getConstantState() {
            return new ConstantState() {
                @Override public Drawable newDrawable() {
                    return new HatchDrawable(mLineColor, mSpacing / 7f, mCornerRadius);
                }
                @Override public int getChangingConfigurations() { return 0; }
            };
        }
    }

    private static GradientDrawable grainOverlay(int baseColor, float density, float cornerRadius,
                                                  float alphaFrac) {
        return new GrainDrawable(baseColor, density, cornerRadius, alphaFrac);
    }

    /** Grana/rumore — tanti puntini piccolissimi a posizione/dimensione/alpha pseudo-casuali ma
     *  RIPRODUCIBILI (seed fisso): senza un seed fisso il pattern cambierebbe ad ogni draw() (più
     *  volte al secondo durante animazioni) mostrando un rumore video fastidioso invece di una
     *  texture statica. Stessa tecnica/gotcha delle altre due texture qui sopra. */
    private static final class GrainDrawable extends GradientDrawable {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float mCornerRadius, mDensity, mAlphaFrac;
        private final int mBaseColor;

        GrainDrawable(int baseColor, float density, float cornerRadius, float alphaFrac) {
            mBaseColor = baseColor;
            mDensity = density;
            mCornerRadius = cornerRadius;
            mAlphaFrac = alphaFrac;
            setShape(GradientDrawable.RECTANGLE);
            setColor(Color.TRANSPARENT);
            setCornerRadius(cornerRadius);
        }

        @Override public void draw(Canvas canvas) {
            super.draw(canvas);
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            java.util.Random rnd = new java.util.Random(0x0B51D1A5); // seed fisso — riproducibile
            float cell = 6f * mDensity;
            int cols = Math.max(1, (int) (b.width() / cell));
            int rows = Math.max(1, (int) (b.height() / cell));
            int r0 = Color.red(mBaseColor), g0 = Color.green(mBaseColor), b0 = Color.blue(mBaseColor);
            // La finestra alpha per-puntino (min..max su 255) scala con l'opacità scelta
            // dall'utente — a mAlphaFrac=0.25 (default) resta ~20..69 come prima del cursore.
            int maxAlpha = Math.max(1, Math.round(255 * mAlphaFrac));
            int minAlpha = Math.max(0, maxAlpha - 50);
            for (int cy = 0; cy < rows; cy++) {
                for (int cx = 0; cx < cols; cx++) {
                    // Non un punto per cella: ~40% di riempimento, per un aspetto grana/rumore
                    // vero invece di una griglia regolare.
                    if (rnd.nextFloat() > 0.4f) continue;
                    float x = b.left + cx * cell + rnd.nextFloat() * cell;
                    float y = b.top + cy * cell + rnd.nextFloat() * cell;
                    float radius = (0.4f + rnd.nextFloat() * 0.5f) * mDensity;
                    int alpha = minAlpha + rnd.nextInt(Math.max(1, maxAlpha - minAlpha + 1));
                    mPaint.setColor(Color.argb(alpha, r0, g0, b0));
                    canvas.drawCircle(x, y, radius, mPaint);
                }
            }
        }

        @Override public void setTint(int tintColor) { /* block OOS tint */ }
        @Override public void setTintList(ColorStateList tint) { /* block OOS tint */ }
        @Override public void setTintMode(PorterDuff.Mode tintMode) { /* block */ }
        @Override public void setColorFilter(ColorFilter cf) { /* block OOS colorFilter */ }
        @Override public void setColorFilter(int color, PorterDuff.Mode mode) { /* block */ }
        @Override public void setAlpha(int alpha) { /* block OOS alpha override */ }

        @Override public ConstantState getConstantState() {
            return new ConstantState() {
                @Override public Drawable newDrawable() {
                    return new GrainDrawable(mBaseColor, mDensity, mCornerRadius, mAlphaFrac);
                }
                @Override public int getChangingConfigurations() { return 0; }
            };
        }
    }

    /** Disegna un simbolo (cuore/quadri/fiori/picche) centrato in (cx,cy), largo/alto ~size. */
    private interface SymbolPainter {
        void paint(Canvas canvas, float cx, float cy, float size, Paint paint);
    }

    private static GradientDrawable suitGridOverlay(int color, float density, float cornerRadius,
                                                      SymbolPainter painter) {
        return new SymbolGridDrawable(color, density, cornerRadius, painter);
    }

    /** Griglia di semi delle carte — stessa tecnica/gotcha di {@link DotGridDrawable}: classe
     *  nominata, deve estendere GradientDrawable, getConstantState() ricostruisce la sottoclasse
     *  vera. Ogni cella disegna il simbolo tramite {@link SymbolPainter} (cerchi/triangoli/rombi
     *  con canvas.drawCircle/drawPath, niente asset). */
    private static final class SymbolGridDrawable extends GradientDrawable {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float mSpacing, mSymbolSize, mCornerRadius;
        private final int mColor;
        private final SymbolPainter mPainter;

        SymbolGridDrawable(int color, float density, float cornerRadius, SymbolPainter painter) {
            mColor = color;
            mSpacing = 16f * density;
            mSymbolSize = 9f * density;
            mCornerRadius = cornerRadius;
            mPainter = painter;
            mPaint.setColor(color);
            mPaint.setStyle(Paint.Style.FILL);
            setShape(GradientDrawable.RECTANGLE);
            setColor(Color.TRANSPARENT);
            setCornerRadius(cornerRadius);
        }

        @Override public void draw(Canvas canvas) {
            super.draw(canvas);
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            for (float y = mSpacing / 2f; y < b.height(); y += mSpacing) {
                for (float x = mSpacing / 2f; x < b.width(); x += mSpacing) {
                    mPainter.paint(canvas, b.left + x, b.top + y, mSymbolSize, mPaint);
                }
            }
        }

        @Override public void setTint(int tintColor) { /* block OOS tint */ }
        @Override public void setTintList(ColorStateList tint) { /* block OOS tint */ }
        @Override public void setTintMode(PorterDuff.Mode tintMode) { /* block */ }
        @Override public void setColorFilter(ColorFilter cf) { /* block OOS colorFilter */ }
        @Override public void setColorFilter(int color, PorterDuff.Mode mode) { /* block */ }
        @Override public void setAlpha(int alpha) { /* block OOS alpha override */ }

        @Override public ConstantState getConstantState() {
            return new ConstantState() {
                @Override public Drawable newDrawable() {
                    return new SymbolGridDrawable(mColor, mSpacing / 16f, mCornerRadius, mPainter);
                }
                @Override public int getChangingConfigurations() { return 0; }
            };
        }
    }

    // ── Simboli — forme semplici (cerchi/triangoli/rombo), niente curve/bezier: robuste e
    // prevedibili a piccola scala, coerenti con lo stile "programmatico" di tutto il file. ──

    private static void paintHeart(Canvas c, float cx, float cy, float s, Paint p) {
        float r = s * 0.26f;
        c.drawCircle(cx - s * 0.22f, cy - s * 0.15f, r, p);
        c.drawCircle(cx + s * 0.22f, cy - s * 0.15f, r, p);
        Path tri = new Path();
        tri.moveTo(cx - s * 0.46f, cy - s * 0.05f);
        tri.lineTo(cx + s * 0.46f, cy - s * 0.05f);
        tri.lineTo(cx, cy + s * 0.48f);
        tri.close();
        c.drawPath(tri, p);
    }

    private static void paintSpade(Canvas c, float cx, float cy, float s, Paint p) {
        float r = s * 0.26f;
        c.drawCircle(cx - s * 0.22f, cy + s * 0.12f, r, p);
        c.drawCircle(cx + s * 0.22f, cy + s * 0.12f, r, p);
        Path tri = new Path();
        tri.moveTo(cx - s * 0.46f, cy + s * 0.02f);
        tri.lineTo(cx + s * 0.46f, cy + s * 0.02f);
        tri.lineTo(cx, cy - s * 0.5f);
        tri.close();
        c.drawPath(tri, p);
        c.drawRect(cx - s * 0.06f, cy + s * 0.15f, cx + s * 0.06f, cy + s * 0.48f, p);
    }

    private static void paintClub(Canvas c, float cx, float cy, float s, Paint p) {
        float r = s * 0.22f;
        c.drawCircle(cx, cy - s * 0.28f, r, p);
        c.drawCircle(cx - s * 0.22f, cy + s * 0.02f, r, p);
        c.drawCircle(cx + s * 0.22f, cy + s * 0.02f, r, p);
        c.drawRect(cx - s * 0.06f, cy + s * 0.05f, cx + s * 0.06f, cy + s * 0.46f, p);
    }

    private static void paintDiamond(Canvas c, float cx, float cy, float s, Paint p) {
        Path path = new Path();
        path.moveTo(cx, cy - s * 0.5f);
        path.lineTo(cx + s * 0.35f, cy);
        path.lineTo(cx, cy + s * 0.5f);
        path.lineTo(cx - s * 0.35f, cy);
        path.close();
        c.drawPath(path, p);
    }

    private static GradientDrawable checkerOverlay(int color, float density, float cornerRadius) {
        return new CheckerDrawable(color, density, cornerRadius);
    }

    /** Scacchiera — quadretti pieni su celle alterne (row+col pari). Stessa tecnica/gotcha
     *  delle altre texture: classe nominata, estende GradientDrawable, getConstantState()
     *  ricostruisce la sottoclasse vera. */
    private static final class CheckerDrawable extends GradientDrawable {
        private final Paint mPaint = new Paint();
        private final float mCell, mCornerRadius;
        private final int mColor;

        CheckerDrawable(int color, float density, float cornerRadius) {
            mColor = color;
            mCell = 10f * density;
            mCornerRadius = cornerRadius;
            mPaint.setColor(color);
            setShape(GradientDrawable.RECTANGLE);
            setColor(Color.TRANSPARENT);
            setCornerRadius(cornerRadius);
        }

        @Override public void draw(Canvas canvas) {
            super.draw(canvas);
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            int col = 0;
            for (float x = 0; x < b.width(); x += mCell, col++) {
                int row = 0;
                for (float y = 0; y < b.height(); y += mCell, row++) {
                    if ((row + col) % 2 != 0) continue;
                    canvas.drawRect(b.left + x, b.top + y,
                            b.left + Math.min(x + mCell, b.width()), b.top + Math.min(y + mCell, b.height()), mPaint);
                }
            }
        }

        @Override public void setTint(int tintColor) { /* block OOS tint */ }
        @Override public void setTintList(ColorStateList tint) { /* block OOS tint */ }
        @Override public void setTintMode(PorterDuff.Mode tintMode) { /* block */ }
        @Override public void setColorFilter(ColorFilter cf) { /* block OOS colorFilter */ }
        @Override public void setColorFilter(int color, PorterDuff.Mode mode) { /* block */ }
        @Override public void setAlpha(int alpha) { /* block OOS alpha override */ }

        @Override public ConstantState getConstantState() {
            return new ConstantState() {
                @Override public Drawable newDrawable() {
                    return new CheckerDrawable(mColor, mCell / 10f, mCornerRadius);
                }
                @Override public int getChangingConfigurations() { return 0; }
            };
        }
    }

    private static GradientDrawable waveOverlay(int color, float density, float cornerRadius) {
        return new WaveDrawable(color, density, cornerRadius);
    }

    /** Onde — linee sinusoidali orizzontali ripetute verticalmente, tracciate punto per punto
     *  (niente Path.quadTo/cubicTo: un campionamento lineare fitto è più semplice da ragionare
     *  ed è comunque indistinguibile a questa scala). Stessa tecnica/gotcha delle altre texture. */
    private static final class WaveDrawable extends GradientDrawable {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float mSpacing, mAmplitude, mWaveLength, mCornerRadius;
        private final int mColor;

        WaveDrawable(int color, float density, float cornerRadius) {
            mColor = color;
            mSpacing = 10f * density;
            mAmplitude = 2.5f * density;
            mWaveLength = 16f * density;
            mCornerRadius = cornerRadius;
            mPaint.setColor(color);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(Math.max(1f, 1f * density));
            setShape(GradientDrawable.RECTANGLE);
            setColor(Color.TRANSPARENT);
            setCornerRadius(cornerRadius);
        }

        @Override public void draw(Canvas canvas) {
            super.draw(canvas);
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            canvas.save();
            canvas.clipRect(b);
            for (float y = mSpacing / 2f; y < b.height() + mAmplitude; y += mSpacing) {
                Path p = new Path();
                boolean first = true;
                for (float x = 0; x <= b.width(); x += 4f) {
                    float wy = y + mAmplitude * (float) Math.sin(2f * Math.PI * x / mWaveLength);
                    if (first) { p.moveTo(b.left + x, b.top + wy); first = false; }
                    else p.lineTo(b.left + x, b.top + wy);
                }
                canvas.drawPath(p, mPaint);
            }
            canvas.restore();
        }

        @Override public void setTint(int tintColor) { /* block OOS tint */ }
        @Override public void setTintList(ColorStateList tint) { /* block OOS tint */ }
        @Override public void setTintMode(PorterDuff.Mode tintMode) { /* block */ }
        @Override public void setColorFilter(ColorFilter cf) { /* block OOS colorFilter */ }
        @Override public void setColorFilter(int color, PorterDuff.Mode mode) { /* block */ }
        @Override public void setAlpha(int alpha) { /* block OOS alpha override */ }

        @Override public ConstantState getConstantState() {
            return new ConstantState() {
                @Override public Drawable newDrawable() {
                    return new WaveDrawable(mColor, mSpacing / 10f, mCornerRadius);
                }
                @Override public int getChangingConfigurations() { return 0; }
            };
        }
    }

    private static GradientDrawable crossHatchOverlay(int color, float density, float cornerRadius) {
        return new CrossHatchDrawable(color, density, cornerRadius);
    }

    /** Intreccio — hatching incrociato, due passate di linee diagonali a 45° e 135° (riusa la
     *  stessa idea di {@link HatchDrawable} due volte in un solo draw()). Stessa tecnica/gotcha
     *  delle altre texture. */
    private static final class CrossHatchDrawable extends GradientDrawable {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float mSpacing, mCornerRadius;
        private final int mColor;

        CrossHatchDrawable(int color, float density, float cornerRadius) {
            mColor = color;
            mSpacing = 9f * density;
            mCornerRadius = cornerRadius;
            mPaint.setColor(color);
            mPaint.setStrokeWidth(Math.max(1f, 1f * density));
            setShape(GradientDrawable.RECTANGLE);
            setColor(Color.TRANSPARENT);
            setCornerRadius(cornerRadius);
        }

        @Override public void draw(Canvas canvas) {
            super.draw(canvas);
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            float diag = (float) Math.sqrt((double) b.width() * b.width() + (double) b.height() * b.height());
            float half = diag / 2f;
            for (float angle : new float[]{45f, -45f}) {
                canvas.save();
                canvas.clipRect(b);
                canvas.rotate(angle, b.centerX(), b.centerY());
                for (float y = -half; y < half; y += mSpacing) {
                    canvas.drawLine(b.centerX() - half, b.centerY() + y, b.centerX() + half, b.centerY() + y, mPaint);
                }
                canvas.restore();
            }
        }

        @Override public void setTint(int tintColor) { /* block OOS tint */ }
        @Override public void setTintList(ColorStateList tint) { /* block OOS tint */ }
        @Override public void setTintMode(PorterDuff.Mode tintMode) { /* block */ }
        @Override public void setColorFilter(ColorFilter cf) { /* block OOS colorFilter */ }
        @Override public void setColorFilter(int color, PorterDuff.Mode mode) { /* block */ }
        @Override public void setAlpha(int alpha) { /* block OOS alpha override */ }

        @Override public ConstantState getConstantState() {
            return new ConstantState() {
                @Override public Drawable newDrawable() {
                    return new CrossHatchDrawable(mColor, mSpacing / 9f, mCornerRadius);
                }
                @Override public int getChangingConfigurations() { return 0; }
            };
        }
    }

    private static int withAlpha(int color, float alphaFraction) {
        int a = Math.round(255 * alphaFraction);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static int darken(int color, float factor) {
        return Color.argb(Color.alpha(color),
                Math.round(Color.red(color) * factor),
                Math.round(Color.green(color) * factor),
                Math.round(Color.blue(color) * factor));
    }

    private static int lighten(int color, float factor) {
        return Color.argb(Color.alpha(color),
                Math.min(255, Math.round(Color.red(color) * factor)),
                Math.min(255, Math.round(Color.green(color) * factor)),
                Math.min(255, Math.round(Color.blue(color) * factor)));
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
