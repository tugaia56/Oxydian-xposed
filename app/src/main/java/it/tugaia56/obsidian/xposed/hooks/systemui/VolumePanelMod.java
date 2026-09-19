package it.tugaia56.obsidian.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getBooleanField;
import static de.robv.android.xposed.XposedHelpers.getFloatField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.ImageView;

import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.utils.Constants;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Volume panel mods (SystemUI):
 *   – Panel position  (isOplusVolumeKeyInRight)
 *   – Timeout         (computeTimeoutH)
 *   – Disable warning (showSafetyWarningH / onShowSafetyWarning)
 *   – Slider colors   (OplusVolumeSeekBar + initRow)
 */
public class VolumePanelMod extends XposedMods {

    private static final String LISTEN = Constants.Packages.SYSTEM_UI;

    // ── Pref keys (saved by VolumePanelFragment / read here via Xprefs) ───────
    public static final String PREF_POSITION        = "volume_panel_position";           // "0"|"1"|"2"
    public static final String PREF_TIMEOUT         = "volume_panel_timeout";            // int seconds
    public static final String PREF_CUSTOM_PROGRESS = "volume_panel_seekbar_color_enabled";
    public static final String PREF_PROGRESS_COLOR  = "volume_panel_seekbar_color";      // int ARGB
    public static final String PREF_CUSTOM_BG       = "volume_panel_seekbar_bg_color_enabled";
    public static final String PREF_BG_COLOR        = "volume_panel_seekbar_bg_color";   // int ARGB
    /** "0"=predefinito "1"=scura "2"=bianca "3"=accento "4"=personalizzata — stessa
     *  struttura di qs_brightness_icon_mode in QsTilesCustomizeMod, su richiesta esplicita
     *  dell'utente ("prepara la stessa opzione per cursore Volume"). */
    public static final String PREF_ICON_MODE       = "qs_volume_icon_mode";
    public static final String PREF_ICON_COLOR      = "qs_volume_icon_custom_color";
    /** "custom"|"image" — sotto PREF_CUSTOM_BG (on/off). Reale, non un tentativo alla cieca come
     *  lo "sfondo pannello" tolto prima: COUIVerticalSeekBar (classe base di OplusVolumeSeekBar,
     *  jadx) ha un vero metodo pubblico setInactiveTrackDrawable(Drawable) — se non null, onDraw()
     *  clippa alla stessa forma arrotondata del background e disegna QUELLO invece del riempimento
     *  flat mBackgroundPaint. */
    public static final String PREF_BG_MODE          = "volume_panel_seekbar_bg_mode"; // "custom"|"gradient"|"image"
    public static final String PREF_BG_GRADIENT_INDEX = "volume_panel_seekbar_bg_gradient_index";
    /** 2026-09-15: modello di ritaglio riscritto su richiesta dell'utente — foto intera visibile
     *  in anteprima con un riquadro trascinabile (non più uno slider di posizione su una card già
     *  ritagliata). Centro del riquadro come frazione dell'immagine (0..100, 50=centro) più
     *  dimensione del riquadro (100..200%, vedi PREF_BG_IMAGE_CROP_SIZE) — stesso rapporto
     *  d'aspetto della barra reale, mai lo slider "zoom" precedente (quello imponeva sempre un
     *  margine fisso; qui l'utente vede/sceglie il riquadro direttamente, niente margine nascosto
     *  da indovinare). */
    public static final String PREF_BG_IMAGE_CROP_CX   = "volume_panel_seekbar_bg_image_crop_cx";
    public static final String PREF_BG_IMAGE_CROP_CY   = "volume_panel_seekbar_bg_image_crop_cy";
    /** 100 = riquadro più piccolo/stretto (più zoom, "grandezza naturale" della barra), 200 =
     *  riquadro fino alla dimensione massima che rientra nella foto (meno zoom, si vede più foto
     *  — "aumentare il rimpicciolimento da 100 a 200" chiesto dall'utente). */
    public static final String PREF_BG_IMAGE_CROP_SIZE = "volume_panel_seekbar_bg_image_crop_size";
    private static final String BG_IMAGE_SUBPATH      = ".obsidian/volume_panel_seekbar_bg_image";
    /** Preset gradient — 2 colori, diagonale, stessa famiglia di grigi scuri già usata altrove nel
     *  progetto (BG_PRESET_COLORS in PowerMenuFragment ecc.) più un preset legato all'accento
     *  dell'utente. Indice = ordine di questo array, condiviso con la UI. */
    public static final int[][] BG_GRADIENT_PRESETS = {
            {0xFF1B2029, 0xFF0D0F14}, // Notte
            {0xFF282C36, 0xFF141619}, // Grafite
            {0xFF3E424D, 0xFF1B2029}, // Ardesia
            {0xFF9CA1AD, 0xFF3E424D}, // Nebbia
            {0xFF0F2027, 0xFF2C5364}, // Oceano
            {0xFFFF5E62, 0xFFFF9966}, // Tramonto
            {0xFF00C9FF, 0xFF92FE9D}, // Aurora
            {0xFFFF7E5F, 0xFFFEB47E}, // Corallo
            {0xFF7F00FF, 0xFFE100FF}, // Viola elettrico
            {0xFF11998E, 0xFF38EF7D}, // Foresta
            {0xFF2C3E50, 0xFF2C3E50}, // Antracite (colore fisso — stessi 2 valori = niente gradiente)
            {0xFF1B3A5C, 0xFF1B3A5C}, // Cobalto
            {0xFF1B5C3A, 0xFF1B5C3A}, // Smeraldo
            {0xFF5C1B3A, 0xFF5C1B3A}, // Vinaccia
    };
    /** Gradiente personalizzato (2+ colori scelti dall'utente, ciascuno con una posizione 0-100%,
     *  direzione Orizzontale/Verticale) — alternativa ai Preset fissi, richiesta esplicita:
     *  "posso mettere più di due colori? ed assegnare una percentuale?". Formato stringa:
     *  "AARRGGBB,pos;AARRGGBB,pos;..." — vedi parseGradientStops()/serializeGradientStops(),
     *  duplicate anche in VolumePanelColorsFragment/GradientStopsFragment per indipendenza (stesso
     *  motivo di altre classi duplicate nel progetto, es. DstDialogStyle). */
    public static final String PREF_BG_GRADIENT_CUSTOM_STOPS    = "volume_panel_seekbar_bg_gradient_custom_stops";
    public static final String PREF_BG_GRADIENT_CUSTOM_VERTICAL = "volume_panel_seekbar_bg_gradient_custom_vertical";
    public static final String PREF_PROGRESS_LINE_GRADIENT_CUSTOM_STOPS    = "volume_panel_seekbar_progress_line_gradient_custom_stops";
    public static final String PREF_PROGRESS_LINE_GRADIENT_CUSTOM_VERTICAL = "volume_panel_seekbar_progress_line_gradient_custom_vertical";

    /** Bordo della barra — stesso 2-way Accento/Personalizzato del bordo pallino Power Menu.
     *  COUIVerticalSeekBar.onDraw() non ha un proprio stroke: disegnato a mano dopo il native
     *  onDraw usando i suoi stessi campi mBackgroundRect/mCurBackgroundRadius (via reflection). */
    public static final String PREF_BORDER_ON         = "volume_panel_border_enabled";
    public static final String PREF_BORDER_USE_ACCENT = "volume_panel_border_use_accent";
    public static final String PREF_BORDER_CUSTOM     = "volume_panel_border_custom_color";
    /** "Progresso a riga" — col riempimento pieno il colore/gradiente di sfondo scompare sotto
     *  l'accento man mano che il volume sale ("la barra progresso accento è quasi stonata" contro
     *  i preset colorati). Sul livello attuale (mProgressColor trasparente, il fill nativo diventa
     *  invisibile) si disegna a mano una sottile riga bianca orizzontale in cima all'area riempita
     *  (mProgressRect.top, stesso campo che COUIVerticalSeekBar aggiorna già ogni frame per
     *  seguire il volume) — il preset di sfondo resta sempre visibile sotto per intero. */
    public static final String PREF_PROGRESS_LINE     = "volume_panel_seekbar_progress_line_enabled";
    public static final String PREF_PROGRESS_LINE_USE_ACCENT = "volume_panel_seekbar_progress_line_use_accent";
    public static final String PREF_PROGRESS_LINE_CUSTOM     = "volume_panel_seekbar_progress_line_custom_color";
    /** "Riempimento" — riusa la STESSA lista di preset gradiente di "Immagine sfondo"
     *  (BG_GRADIENT_PRESETS/resolveGradientColors), ma con un indice indipendente: la riga può
     *  avere un preset diverso da quello effettivamente attivo sullo sfondo. mode "color" = il
     *  vecchio comportamento (Accento/Personalizzato); "gradient" = LinearGradient orizzontale. */
    public static final String PREF_PROGRESS_LINE_FILL_MODE = "volume_panel_seekbar_progress_line_fill_mode";
    public static final String PREF_PROGRESS_LINE_GRADIENT_INDEX = "volume_panel_seekbar_progress_line_gradient_index";
    /** Due assi indipendenti, combinabili: PREF_PROGRESS_LINE_FULL (riga sottile vs riempie
     *  tutta l'area — "non solo la righetta, ma tutta la parte del progresso") e
     *  PREF_PROGRESS_LINE_WAVE (bordo dritto vs a onda — nel riempimento pieno è il bordo SUPERIORE
     *  mobile, non gli angoli: quelli restano dritti/non arrotondati su richiesta esplicita
     *  "non mi piace il bordo arrotondato"; nella riga è la riga stessa). */
    public static final String PREF_PROGRESS_LINE_FULL = "volume_panel_seekbar_progress_line_full";
    public static final String PREF_PROGRESS_LINE_WAVE = "volume_panel_seekbar_progress_line_wave";
    /** Spessore condiviso da riga e bordo — "fare la riga spessa come il bordo": stesso valore,
     *  non solo "simile", così restano visivamente coerenti se uno dei due cambia in futuro. */
    private static final float BAR_STROKE_DP = 1.5f;

    // ── Runtime state ─────────────────────────────────────────────────────────
    private int     mPosition        = 0;
    private int     mTimeoutMs       = 3_000;
    private boolean mCustomProgress  = false;
    private int     mProgressColor   = 0xFFFFFFFF;
    private boolean mCustomBg        = false;
    private int     mBgColor         = 0xFF808080;
    private String  mBgMode          = "custom";
    private int     mBgGradientIndex = 0;
    private int     mBgImageCropCx   = 50;
    private int     mBgImageCropCy   = 50;
    private int     mBgImageCropSize = 100;
    private int     mIconMode        = 0;
    private int     mIconColor       = 0xFFFFFFFF;
    private boolean mBorderOn        = false;
    private boolean mBorderUseAccent = true;
    private int     mBorderCustomColor = 0xFF908DFF;
    private boolean mProgressLineOn  = false;
    private boolean mProgressLineUseAccent = true;
    private int     mProgressLineCustomColor = 0xFFFFFFFF;
    private String  mProgressLineFillMode = "color"; // "color" | "gradient" | "gradient_custom"
    private int     mProgressLineGradientIndex = 0;
    private boolean mProgressLineFull = false;
    private boolean mProgressLineWave = false;
    private String  mBgGradientCustomStops = "";
    private boolean mBgGradientCustomVertical = true;
    private String  mProgressLineGradientCustomStops = "";
    private boolean mProgressLineGradientCustomVertical = false;
    private android.graphics.Bitmap mBgImageBitmap = null;
    private long    mBgImageBitmapMtime = -1;

    /** Reference to OplusVolumeDialogImpl for live-update of colors. */
    private Object  mOVDI            = null;

    public VolumePanelMod(Context context) { super(context); }

    // ── updatePrefs ──────────────────────────────────────────────────────────

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mPosition       = Integer.parseInt(Xprefs.getString(PREF_POSITION, "0"));
        mTimeoutMs      = Xprefs.getInt(PREF_TIMEOUT, 3) * 1_000;
        mCustomProgress = Xprefs.getBoolean(PREF_CUSTOM_PROGRESS, false);
        mProgressColor  = Xprefs.getBoolean(PREF_PROGRESS_COLOR + "_use_accent", false)
                ? appAccentColor() : Xprefs.getInt(PREF_PROGRESS_COLOR, 0xFFFFFFFF);
        mCustomBg       = Xprefs.getBoolean(PREF_CUSTOM_BG, false);
        mBgColor        = Xprefs.getInt(PREF_BG_COLOR, 0xFF808080);
        mBgMode         = Xprefs.getString(PREF_BG_MODE, "custom");
        mBgGradientIndex = Xprefs.getInt(PREF_BG_GRADIENT_INDEX, 0);
        mBgGradientCustomStops = Xprefs.getString(PREF_BG_GRADIENT_CUSTOM_STOPS, "");
        mBgGradientCustomVertical = Xprefs.getBoolean(PREF_BG_GRADIENT_CUSTOM_VERTICAL, true);
        mBgImageCropCx = Xprefs.getInt(PREF_BG_IMAGE_CROP_CX, 50);
        mBgImageCropCy = Xprefs.getInt(PREF_BG_IMAGE_CROP_CY, 50);
        mBgImageCropSize = Xprefs.getInt(PREF_BG_IMAGE_CROP_SIZE, 100);
        try { mIconMode = Integer.parseInt(Xprefs.getString(PREF_ICON_MODE, "0")); } catch (Throwable t) { mIconMode = 0; }
        mIconColor      = Xprefs.getInt(PREF_ICON_COLOR, 0xFFFFFFFF);
        mBorderOn         = Xprefs.getBoolean(PREF_BORDER_ON, false);
        mBorderUseAccent  = Xprefs.getBoolean(PREF_BORDER_USE_ACCENT, true);
        mBorderCustomColor = Xprefs.getInt(PREF_BORDER_CUSTOM, 0xFF908DFF);
        mProgressLineOn = Xprefs.getBoolean(PREF_PROGRESS_LINE, false);
        mProgressLineUseAccent = Xprefs.getBoolean(PREF_PROGRESS_LINE_USE_ACCENT, true);
        mProgressLineCustomColor = Xprefs.getInt(PREF_PROGRESS_LINE_CUSTOM, 0xFFFFFFFF);
        mProgressLineFillMode = Xprefs.getString(PREF_PROGRESS_LINE_FILL_MODE, "color");
        mProgressLineGradientIndex = Xprefs.getInt(PREF_PROGRESS_LINE_GRADIENT_INDEX, 0);
        mProgressLineGradientCustomStops = Xprefs.getString(PREF_PROGRESS_LINE_GRADIENT_CUSTOM_STOPS, "");
        mProgressLineGradientCustomVertical = Xprefs.getBoolean(PREF_PROGRESS_LINE_GRADIENT_CUSTOM_VERTICAL, false);
        mProgressLineFull = Xprefs.getBoolean(PREF_PROGRESS_LINE_FULL, false);
        mProgressLineWave = Xprefs.getBoolean(PREF_PROGRESS_LINE_WAVE, false);
        refreshBgImageBitmap();

        // Live-update color on open dialog if already constructed
        if (Key.length > 0 && mOVDI != null) applyColorsToAll();
    }

    // ── Sfondo barra: immagine (setInactiveTrackDrawable) ────────────────────

    private static android.graphics.Bitmap decodeSampledBitmap(java.io.File f) {
        try {
            android.util.DisplayMetrics dm = android.content.res.Resources.getSystem().getDisplayMetrics();
            int reqW = dm.widthPixels, reqH = dm.heightPixels;
            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
                android.graphics.BitmapFactory.decodeStream(fis, null, bounds);
            }
            int sample = 1;
            int halfW = bounds.outWidth / 2, halfH = bounds.outHeight / 2;
            while (halfW / sample >= reqW && halfH / sample >= reqH) sample *= 2;
            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inSampleSize = sample;
            try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
                return android.graphics.BitmapFactory.decodeStream(fis, null, opts);
            }
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] VolumePanelMod decodeSampledBitmap: " + t);
            return null;
        }
    }

    private void refreshBgImageBitmap() {
        if (!"image".equals(mBgMode)) {
            mBgImageBitmap = null;
            mBgImageBitmapMtime = -1;
            return;
        }
        java.io.File f = new java.io.File(android.os.Environment.getExternalStorageDirectory(), BG_IMAGE_SUBPATH);
        if (!f.exists()) {
            mBgImageBitmap = null;
            mBgImageBitmapMtime = -1;
            return;
        }
        long mtime = f.lastModified();
        if (mBgImageBitmap != null && mtime == mBgImageBitmapMtime) return;
        android.graphics.Bitmap bmp = decodeSampledBitmap(f);
        if (bmp != null) {
            mBgImageBitmap = bmp;
            mBgImageBitmapMtime = mtime;
        }
    }

    /** Crop su qualunque bounds gli vengano dati (setDrawableBounds() di COUIVerticalSeekBar
     *  chiama setBounds() con l'area reale del background prima del draw). Modello "riquadro"
     *  (2026-09-15, sostituisce il vecchio gravity+zoom): centro cxFrac/cyFrac (0..1, frazione
     *  dell'immagine) + sizePercent, percentuale DIRETTA del riquadro massimo che rientra nella
     *  sorgente mantenendo l'aspect ratio della barra (200%=massimo, 100%=metà, scendibile sotto
     *  100 per uno zoom ancora più stretto — "voglio la possibilità di avere un ritaglio più
     *  piccolo", richiesto dopo la prima versione 100..200 che aveva un minimo troppo alto).
     *  Stessi numeri che l'utente vede e trascina nell'anteprima "foto intera + riquadro". */
    private static class CenterCropBitmapDrawable extends android.graphics.drawable.Drawable {
        private final android.graphics.Bitmap mBmp;
        private final float mCx, mCy;   // 0..1, centro del riquadro come frazione dell'immagine
        private final int   mSizePercent; // 20..200, frazione diretta del riquadro massimo
        private final Object mSeekBar; // riferimento vero, per leggere mBackgroundRect (vedi sotto)
        private final android.graphics.Paint mPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        CenterCropBitmapDrawable(android.graphics.Bitmap bmp) { this(bmp, 0.5f, 0.5f, 100, null); }
        CenterCropBitmapDrawable(android.graphics.Bitmap bmp, float cx, float cy, int sizePercent, Object seekBar) {
            mBmp = bmp; mCx = cx; mCy = cy; mSizePercent = sizePercent; mSeekBar = seekBar;
        }
        /** Il vero clip visibile che COUIVerticalSeekBar.onDraw() applica prima di chiamare
         *  mInactiveTrackDrawable.draw() è basato su mBackgroundRect (+ raggio), NON sui bounds
         *  che riceviamo noi via getBounds() (impostati da setDrawableBounds() con una semplice
         *  (0,0,getWidth(),getHeight()) — un rettangolo più GRANDE del vero clip arrotondato).
         *  Usando i nostri bounds per calcolare le proporzioni del ritaglio, la finestra risultava
         *  sempre più "larga" del previsto e il centro/dimensione scelti dall'utente venivano
         *  tagliati ai bordi dal clip reale, più piccolo — "la barra ha img ancora più piccola del
         *  ritaglio" segnalato dall'utente. Fix: leggere mBackgroundRect via reflection e usare
         *  QUELLO per il rapporto d'aspetto, non i bounds del Drawable. */
        private android.graphics.Rect resolveVisibleRect(android.graphics.Rect fallback) {
            if (mSeekBar == null) return fallback;
            try {
                Object rectObj = getObjectField(mSeekBar, "mBackgroundRect");
                if (rectObj instanceof android.graphics.Rect r && r.width() > 0 && r.height() > 0) return r;
            } catch (Throwable ignored) {}
            return fallback;
        }
        @Override public void draw(android.graphics.Canvas canvas) {
            android.graphics.Rect b = resolveVisibleRect(getBounds());
            if (b.width() <= 0 || b.height() <= 0) return;
            int bw = mBmp.getWidth(), bh = mBmp.getHeight();
            float srcAspect = (float) bw / bh, dstAspect = (float) b.width() / b.height();
            float maxCropW, maxCropH; // finestra massima che rientra nella sorgente a dstAspect
            if (srcAspect > dstAspect) { maxCropH = bh; maxCropW = bh * dstAspect; }
            else { maxCropW = bw; maxCropH = bw / dstAspect; }
            // Invertito su richiesta dell'utente: valore slider più alto = più zoom (immagine più
            // ingrandita, riquadro più piccolo), non il contrario — semantica "zoom fotocamera".
            float frac = Math.max(0.05f, Math.min(1f, (220 - mSizePercent) / 200f));
            float cropW = maxCropW * frac, cropH = maxCropH * frac;
            float left = bw * mCx - cropW / 2f, top = bh * mCy - cropH / 2f;
            left = Math.max(0f, Math.min(bw - cropW, left));
            top  = Math.max(0f, Math.min(bh - cropH, top));
            android.graphics.Rect src = new android.graphics.Rect(
                    Math.round(left), Math.round(top), Math.round(left + cropW), Math.round(top + cropH));
            canvas.drawBitmap(mBmp, src, b, mPaint);
        }
        @Override public void setAlpha(int alpha) { mPaint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { mPaint.setColorFilter(cf); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** Indice == BG_GRADIENT_PRESETS.length è il preset dinamico "Accento" (accento→scuro), non
     *  inseribile nell'array costante perché appAccentColor() cambia a runtime. Condiviso da
     *  buildBgGradientDrawable() (Immagine sfondo) e hookProgressLine() (Riga → riempimento
     *  Preset) — stessa lista, indici indipendenti. */
    private int[] resolveGradientColors(int index) {
        if (index == BG_GRADIENT_PRESETS.length) {
            return new int[]{appAccentColor(), 0xFF1B2029};
        } else if (index >= 0 && index < BG_GRADIENT_PRESETS.length) {
            return BG_GRADIENT_PRESETS[index];
        } else {
            return BG_GRADIENT_PRESETS[0];
        }
    }

    private android.graphics.drawable.GradientDrawable buildBgGradientDrawable() {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR, resolveGradientColors(mBgGradientIndex));
        gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        return gd;
    }

    /** Parsing del formato "AARRGGBB,pos;AARRGGBB,pos;..." condiviso con la UI (vedi
     *  GradientStopsFragment) — ordina per posizione (LinearGradient richiede stop crescenti) e
     *  scarta silenziosamente entry malformate invece di crashare sul draw. Meno di 2 stop validi
     *  → null (fallback al preset fisso, gestito dal chiamante). */
    private static final class GradientStops {
        final int[] colors;
        final float[] positions;
        GradientStops(int[] c, float[] p) { colors = c; positions = p; }
    }

    private static GradientStops parseGradientStops(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        java.util.List<int[]> parsed = new java.util.ArrayList<>(); // [0]=color,[1]=position(0-100)
        for (String part : raw.split(";")) {
            String[] kv = part.split(",");
            if (kv.length != 2) continue;
            try {
                int color = (int) Long.parseLong(kv[0], 16);
                int pos = Math.max(0, Math.min(100, Integer.parseInt(kv[1])));
                parsed.add(new int[]{color, pos});
            } catch (Throwable ignored) {}
        }
        if (parsed.size() < 2) return null;
        parsed.sort((a, b) -> Integer.compare(a[1], b[1]));
        int[] colors = new int[parsed.size()];
        float[] positions = new float[parsed.size()];
        for (int i = 0; i < parsed.size(); i++) {
            colors[i] = parsed.get(i)[0];
            positions[i] = parsed.get(i)[1] / 100f;
        }
        return new GradientStops(colors, positions);
    }

    /** Disegna un LinearGradient con stop/posizioni arbitrarie — a differenza di GradientDrawable
     *  (che distribuisce i colori sempre in modo uniforme), qui la posizione di ciascuno stop è
     *  quella scelta dall'utente. Usato SOLO in modalità "gradient_custom"; i preset fissi restano
     *  su GradientDrawable (buildBgGradientDrawable), invariato. La forma (pillola) è già garantita
     *  dal clip nativo che COUIVerticalSeekBar applica a qualunque Drawable passato a
     *  setInactiveTrackDrawable, quindi qui si riempie tutto il bounds senza un proprio round-rect. */
    private static class CustomGradientDrawable extends android.graphics.drawable.Drawable {
        private final int[] mColors;
        private final float[] mPositions;
        private final boolean mVertical;
        private final android.graphics.Paint mPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        CustomGradientDrawable(int[] colors, float[] positions, boolean vertical) {
            mColors = colors; mPositions = positions; mVertical = vertical;
        }
        @Override public void draw(android.graphics.Canvas canvas) {
            android.graphics.Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            float x1 = mVertical ? b.left : b.right;
            float y1 = mVertical ? b.bottom : b.top;
            mPaint.setShader(new android.graphics.LinearGradient(b.left, b.top, x1, y1,
                    mColors, mPositions, android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRect(b, mPaint);
        }
        @Override public void setAlpha(int alpha) { mPaint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { mPaint.setColorFilter(cf); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    // ── handleLoadPackage ────────────────────────────────────────────────────

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        hookPosition(lpparam.classLoader);
        hookTimeout(lpparam.classLoader);
        hookColors(lpparam.classLoader);
        hookBorder(lpparam.classLoader);
        hookProgressLine(lpparam.classLoader);
    }

    /** "Progresso a riga" — drawActiveTrack È ridichiarato in OplusVolumeSeekBar (chiama
     *  super.drawActiveTrack() per primo, poi eventuale overlay "super volume"), quindi si hooka
     *  qui direttamente, dopo che il fill nativo (reso trasparente sopra) ha già girato.
     *  mProgressRect.top segue il livello volume ad ogni frame (lo stesso campo che il
     *  framework aggiorna in measureAndLayout/onDraw), quindi la riga si muove da sola. */
    private void hookProgressLine(ClassLoader cl) {
        try {
            Class<?> volumeSeekBarCls = findClass("com.oplus.systemui.volume.OplusVolumeSeekBar", cl);
            hookAllMethods(volumeSeekBarCls, "drawActiveTrack", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!mProgressLineOn) return;
                    try {
                        android.graphics.Canvas canvas = (android.graphics.Canvas) p.args[0];
                        Object progressRectObj = getObjectField(p.thisObject, "mProgressRect");
                        Object bgRectObj = getObjectField(p.thisObject, "mBackgroundRect");
                        if (!(progressRectObj instanceof android.graphics.Rect progressRect)) return;
                        // Larghezza presa da mBackgroundRect (tutta la barra, bordo a bordo) invece
                        // che da mProgressRect — solo il TOP (il livello volume) serve dal secondo.
                        android.graphics.Rect widthRect = (bgRectObj instanceof android.graphics.Rect r) ? r : progressRect;
                        float density = android.content.res.Resources.getSystem().getDisplayMetrics().density;
                        float top = progressRect.top;

                        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                        android.graphics.Rect gradRect = mProgressLineFull ? progressRect : widthRect;
                        if ("gradient_custom".equals(mProgressLineFillMode)) {
                            GradientStops stops = parseGradientStops(mProgressLineGradientCustomStops);
                            if (stops != null) {
                                float x1 = mProgressLineGradientCustomVertical ? gradRect.left : gradRect.right;
                                float y1 = mProgressLineGradientCustomVertical ? gradRect.bottom : gradRect.top;
                                paint.setShader(new android.graphics.LinearGradient(
                                        gradRect.left, gradRect.top, x1, y1,
                                        stops.colors, stops.positions, android.graphics.Shader.TileMode.CLAMP));
                            } else {
                                paint.setColor(mProgressLineUseAccent ? appAccentColor() : mProgressLineCustomColor);
                            }
                        } else if ("gradient".equals(mProgressLineFillMode)) {
                            int[] colors = resolveGradientColors(mProgressLineGradientIndex);
                            paint.setShader(new android.graphics.LinearGradient(
                                    gradRect.left, gradRect.top, gradRect.right, mProgressLineFull ? gradRect.bottom : gradRect.top,
                                    colors, null, android.graphics.Shader.TileMode.CLAMP));
                        } else {
                            paint.setColor(mProgressLineUseAccent ? appAccentColor() : mProgressLineCustomColor);
                        }

                        if (mProgressLineFull) {
                            // "non solo la righetta, ma tutta la parte del progresso" — riempie
                            // l'intera area invece della sottile riga. Il bordo INFERIORE resta
                            // arrotondato perché è quello vero del riquadro (clip sulla stessa
                            // forma pill di Bordo/Sfondo, mBackgroundRect+mCurBackgroundRadius);
                            // il bordo SUPERIORE mobile è dritto o a onda MA MAI arrotondato — su
                            // richiesta esplicita "non mi piace il bordo arrotondato" a quel bordo.
                            float radius = getFloatField(p.thisObject, "mCurBackgroundRadius");
                            android.graphics.Path clip = new android.graphics.Path();
                            clip.addRoundRect(widthRect.left, widthRect.top, widthRect.right, widthRect.bottom,
                                    radius, radius, android.graphics.Path.Direction.CW);
                            canvas.save();
                            canvas.clipPath(clip);
                            if (mProgressLineWave) {
                                canvas.drawPath(buildWaveFillPath(widthRect.left, widthRect.right, top, widthRect.bottom, density), paint);
                            } else {
                                canvas.drawRect(widthRect.left, top, widthRect.right, widthRect.bottom, paint);
                            }
                            canvas.restore();
                            return;
                        }

                        float lineHeight = BAR_STROKE_DP * density; // "spessa come il bordo" — stessa costante
                        if (mProgressLineWave) {
                            paint.setStyle(android.graphics.Paint.Style.STROKE);
                            paint.setStrokeWidth(lineHeight);
                            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                            canvas.drawPath(buildWavePath(widthRect.left, widthRect.right, top, density), paint);
                        } else {
                            canvas.drawRoundRect(widthRect.left, top - lineHeight / 2f, widthRect.right,
                                    top + lineHeight / 2f, lineHeight / 2f, lineHeight / 2f, paint);
                        }
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] VolumePanelMod: hookProgressLine failed: " + t);
        }
    }

    /** "Riga ondulata" — sinusoide approssimata a segmenti campionati ogni ~2dp (Path.lineTo,
     *  non archi/bezier — con ANTI_ALIAS e un campionamento fitto il risultato è visivamente
     *  indistinguibile da una vera curva, molto più semplice da calcolare). Ampiezza/lunghezza
     *  d'onda fissi (non esposti come slider, stesso approccio di HANDLER_BORDER_SCALE altrove:
     *  tarati a vista, non richiesti come regolabili). */
    private android.graphics.Path buildWavePath(float left, float right, float centerY, float density) {
        android.graphics.Path path = new android.graphics.Path();
        float amplitude = 3f * density;
        float wavelength = 18f * density;
        float width = right - left;
        if (width <= 0) { path.moveTo(left, centerY); path.lineTo(right, centerY); return path; }
        int steps = Math.max(2, Math.round(width / (density * 2f)));
        for (int i = 0; i <= steps; i++) {
            float x = left + width * i / steps;
            float y = centerY + amplitude * (float) Math.sin(2 * Math.PI * (x - left) / wavelength);
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        return path;
    }

    /** Stessa sinusoide di buildWavePath(), ma CHIUSA verso il basso — usata dal riempimento
     *  pieno ondulato: il bordo superiore mobile è la stessa onda, poi il tracciato scende fino
     *  a "bottom" e si richiude, così drawPath (Style.FILL) riempie tutta l'area sotto l'onda. */
    private android.graphics.Path buildWaveFillPath(float left, float right, float centerY, float bottom, float density) {
        android.graphics.Path path = buildWavePath(left, right, centerY, density);
        path.lineTo(right, bottom);
        path.lineTo(left, bottom);
        path.close();
        return path;
    }

    /** "Bordo barra" — COUIVerticalSeekBar.onDraw() non ha un proprio stroke, quindi si disegna a
     *  mano SOPRA il native onDraw (afterHookedMethod, stesso Canvas), riusando gli stessi campi
     *  che il framework usa per il riempimento (mBackgroundRect/mCurBackgroundRadius, via
     *  reflection — jadx su COUIVerticalSeekBar.java, entrambi campi public). onDraw è ereditato,
     *  non ridichiarato in OplusVolumeSeekBar: hook sulla classe base COUIVerticalSeekBar,
     *  scoped via instanceof a OplusVolumeSeekBar per non toccare altri cursori COUI del sistema
     *  (es. luminosità, una classe diversa). */
    private void hookBorder(ClassLoader cl) {
        try {
            Class<?> volumeSeekBarCls = findClass("com.oplus.systemui.volume.OplusVolumeSeekBar", cl);
            Class<?> couiSeekBarCls = findClass("com.coui.appcompat.seekbar.COUIVerticalSeekBar", cl);
            hookAllMethods(couiSeekBarCls, "onDraw", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!mBorderOn || !volumeSeekBarCls.isInstance(p.thisObject)) return;
                    try {
                        android.graphics.Canvas canvas = (android.graphics.Canvas) p.args[0];
                        Object rectObj = getObjectField(p.thisObject, "mBackgroundRect");
                        if (!(rectObj instanceof android.graphics.Rect rect)) return;
                        float radius = getFloatField(p.thisObject, "mCurBackgroundRadius");
                        float density = android.content.res.Resources.getSystem().getDisplayMetrics().density;
                        float strokeWidth = BAR_STROKE_DP * density;
                        float inset = strokeWidth / 2f;
                        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                        paint.setStyle(android.graphics.Paint.Style.STROKE);
                        paint.setStrokeWidth(strokeWidth);
                        paint.setColor(mBorderUseAccent ? appAccentColor() : mBorderCustomColor);
                        canvas.drawRoundRect(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset,
                                radius, radius, paint);
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] VolumePanelMod: hookBorder failed: " + t);
        }
    }

    // ── Position hook ─────────────────────────────────────────────────────────

    private void hookPosition(ClassLoader cl) {
        for (String cn : new String[]{
                "com.oplusos.systemui.common.feature.FeatureOption",
                "com.oplus.systemui.common.feature.FeatureOption"}) {
            try {
                hookAllMethods(findClass(cn, cl), "isOplusVolumeKeyInRight", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (mPosition == 0) return;
                        p.setResult(mPosition == 1); // 1=right, 2=left
                    }
                });
                XposedBridge.log("[ Obsidian ] VolumePanelMod: position hooked via " + cn);
                return;
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] VolumePanelMod: position skip " + cn + ": " + t);
            }
        }
    }

    // ── Timeout hook ──────────────────────────────────────────────────────────

    // OOS16 names the method computeTimeoutH$2 (compiler-generated variant of AOSP's
    // computeTimeoutH). hookAllMethods() uses exact name matching and misses $N variants.
    // We walk the full hierarchy ourselves, log every timeout-related method (DIAG), and
    // hook any that contains "computetimeout" in its name and returns int.

    private void hookTimeout(ClassLoader cl) {
        XC_MethodHook computeHook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                // computeTimeoutH$2 spara ad ogni apertura reale del pannello (verificato nel
                // log, a differenza di initRow che sembra scattare una volta sola molto presto
                // all'avvio — troppo presto se l'utente non tocca più l'interruttore dopo il
                // boot). Approfittiamone per riapplicare qui i colori ad ogni apertura, invece
                // di fidarci solo di initRow. p.thisObject è già l'OVDI reale.
                // 2026-09-16: mancava mIconMode qui — con SOLO l'icona personalizzata (niente
                // Progresso/Sfondo custom) initRow restava l'unica via, quindi l'icona partiva
                // scura ad ogni apertura reale e si correggeva solo al primo tocco/regolazione
                // (che rifà scattare computeTimeoutH$2, es. reset del timeout auto-dismiss).
                if (mCustomProgress || mCustomBg || mIconMode != 0) applyColorsToAll(p.thisObject);

                int ms = (Xprefs != null) ? Xprefs.getInt(PREF_TIMEOUT, 3) * 1_000 : mTimeoutMs;
                if (ms == 3_000) return; // stock timeout — don't override
                // Accessibility hovering: use recommended 16s
                try {
                    if (getBooleanField(p.thisObject, "mHovering")) {
                        p.setResult(callMethod(getObjectField(p.thisObject, "mAccessibilityMgr"),
                                "getRecommendedTimeoutMillis", 16000, 4));
                        return;
                    }
                } catch (Throwable ignored) {}
                // Expanded panel or normal: our custom timeout (or accessibility 5s if expanded)
                try {
                    synchronized (getObjectField(p.thisObject, "mSafetyWarningLock")) {
                        if (getBooleanField(p.thisObject, "mExpanded")) {
                            p.setResult(callMethod(getObjectField(p.thisObject, "mAccessibilityMgr"),
                                    "getRecommendedTimeoutMillis", 5000, 4));
                        } else {
                            p.setResult(ms);
                        }
                    }
                } catch (Throwable t) {
                    p.setResult(ms); // fallback: just return our timeout
                    XposedBridge.log("[ Obsidian ] VolumePanelMod: computeTimeoutH fallback: " + t);
                }
            }
        };

        for (String cn : new String[]{
                "com.oplus.systemui.volume.OplusVolumeDialogImpl",
                "com.oplusos.systemui.volume.VolumeDialogImplEx",
                "com.android.systemui.volume.VolumeDialogImpl"}) {
            try {
                Class<?> cls = findClass(cn, cl);
                int n = 0;

                // Walk the full hierarchy: log all timeout-related methods (DIAG) and
                // hook any int-returning method whose name contains "computetimeout".
                // This catches computeTimeoutH, computeTimeoutH$2, and any future variant.
                for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
                    for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                        String lo = m.getName().toLowerCase();
                        if (lo.contains("timeout") || lo.contains("computetime")) {
                            boolean hook = lo.contains("computetimeout")
                                    && m.getReturnType() == int.class;
                            XposedBridge.log("[ Obsidian ] VolumePanelMod: DIAG "
                                    + c.getSimpleName() + "." + m.getName()
                                    + " [" + m.getReturnType().getSimpleName() + "]"
                                    + (hook ? " ← hooking" : ""));
                            if (hook) {
                                m.setAccessible(true);
                                XposedBridge.hookMethod(m, computeHook);
                                n++;
                            }
                        }
                    }
                }

                XposedBridge.log("[ Obsidian ] VolumePanelMod: timeout hooks=" + n + " via " + cn);
                if (n > 0) return;
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] VolumePanelMod: timeout skip " + cn + ": " + t);
            }
        }
    }


    // ── Slider color hooks ────────────────────────────────────────────────────

    private void hookColors(ClassLoader cl) {
        // Track OVDI instance via constructor + apply colors per-row via initRow
        for (String cn : new String[]{
                "com.oplus.systemui.volume.OplusVolumeDialogImpl",
                "com.oplusos.systemui.volume.VolumeDialogImplEx"}) {
            try {
                Class<?> cls = findClass(cn, cl);
                // Capture instance at construction time
                for (java.lang.reflect.Constructor<?> ctor : cls.getDeclaredConstructors()) {
                    XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            mOVDI = p.thisObject;
                        }
                    });
                }
                // Apply colors per-row after initRow (args[0] = VolumeRow on OOS15)
                hookAllMethods(cls, "initRow", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (p.args.length > 0) applyColorsToRow(p.args[0]);
                    }
                });
                XposedBridge.log("[ Obsidian ] VolumePanelMod: color OVDI hook via " + cn);
                break;
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] VolumePanelMod: color OVDI skip " + cn + ": " + t);
            }
        }

        // OOS16: OplusVolumeRow.initRow — row object is thisObject, not args[0]
        try {
            Class<?> rowCls = findClass("com.oplus.systemui.volume.view.OplusVolumeRow", cl);
            hookAllMethods(rowCls, "initRow", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    applyColorsToRow(p.thisObject);
                }
            });
            XposedBridge.log("[ Obsidian ] VolumePanelMod: color hooked via OplusVolumeRow");
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] VolumePanelMod: color skip OplusVolumeRow: " + t);
        }

        // Background blur color via onPreDraw
        try {
            Class<?> seekBar = findClass("com.oplus.systemui.volume.OplusVolumeSeekBar", cl);
            hookAllMethods(seekBar, "onPreDraw", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!mCustomBg) return;
                    try {
                        Object blur = callMethod(p.thisObject, "getBackgroundBlurDrawable");
                        if (blur != null) callMethod(blur, "setColor", mBgColor);
                    } catch (Throwable ignored) {}
                }
            });
            XposedBridge.log("[ Obsidian ] VolumePanelMod: seekBar bg hooked");
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] VolumePanelMod: seekBar bg skip: " + t);
        }
    }

    /** Apply progress/bg colors to a single VolumeRow. */
    private void applyColorsToRow(Object volumeRow) {
        try {
            Object slider = getObjectField(volumeRow, "slider");
            XposedBridge.log("[ Obsidian ] VolumePanelMod: DIAG applyColorsToRow mCustomProgress=" + mCustomProgress
                    + " mProgressColor=#" + Integer.toHexString(mProgressColor)
                    + " mCustomBg=" + mCustomBg + " mBgColor=#" + Integer.toHexString(mBgColor));
            if (mProgressLineOn) {
                try {
                    // Trasparente: il riempimento nativo diventa invisibile, la riga bianca la
                    // disegna hookProgressLine() sopra (drawActiveTrack, dopo il fill nativo).
                    callMethod(slider, "setProgressColor", ColorStateList.valueOf(0x00000000));
                } catch (Throwable t) {
                    XposedBridge.log("[ Obsidian ] VolumePanelMod: DIAG setProgressColor(transparent) FAILED: " + t);
                }
            } else if (mCustomProgress) {
                try {
                    callMethod(slider, "setProgressColor", ColorStateList.valueOf(mProgressColor));
                    XposedBridge.log("[ Obsidian ] VolumePanelMod: DIAG setProgressColor OK");
                } catch (Throwable t) {
                    XposedBridge.log("[ Obsidian ] VolumePanelMod: DIAG setProgressColor FAILED: " + t);
                }
            }
            if (mCustomBg && "image".equals(mBgMode)) {
                try {
                    if (mBgImageBitmap == null) refreshBgImageBitmap(); // retry sincrono
                    if (mBgImageBitmap != null) {
                        callMethod(slider, "setInactiveTrackDrawable", new CenterCropBitmapDrawable(
                                mBgImageBitmap, mBgImageCropCx / 100f, mBgImageCropCy / 100f, mBgImageCropSize, slider));
                    }
                } catch (Throwable t) {
                    XposedBridge.log("[ Obsidian ] VolumePanelMod: DIAG setInactiveTrackDrawable FAILED: " + t);
                }
            } else if (mCustomBg && "gradient".equals(mBgMode)) {
                try {
                    callMethod(slider, "setInactiveTrackDrawable", buildBgGradientDrawable());
                } catch (Throwable t) {
                    XposedBridge.log("[ Obsidian ] VolumePanelMod: DIAG gradient setInactiveTrackDrawable FAILED: " + t);
                }
            } else if (mCustomBg && "gradient_custom".equals(mBgMode)) {
                try {
                    GradientStops stops = parseGradientStops(mBgGradientCustomStops);
                    if (stops != null) {
                        callMethod(slider, "setInactiveTrackDrawable",
                                new CustomGradientDrawable(stops.colors, stops.positions, mBgGradientCustomVertical));
                    }
                } catch (Throwable t) {
                    XposedBridge.log("[ Obsidian ] VolumePanelMod: DIAG custom gradient setInactiveTrackDrawable FAILED: " + t);
                }
            } else if (mCustomBg) {
                try {
                    callMethod(slider, "setInactiveTrackDrawable", (Object) null); // esce dalla modalità immagine se attiva prima
                    callMethod(slider, "setSeekBarBackgroundColor", ColorStateList.valueOf(mBgColor));
                    XposedBridge.log("[ Obsidian ] VolumePanelMod: DIAG setSeekBarBackgroundColor OK");
                } catch (Throwable t) {
                    XposedBridge.log("[ Obsidian ] VolumePanelMod: DIAG setSeekBarBackgroundColor FAILED: " + t);
                }
            }
            applyIconColor(volumeRow);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] VolumePanelMod: applyRow error: " + t);
        }
    }

    // ── Colore icona cursore — icon è una com.oplus.systemui.volume.OplusEffectiveAnimationView
    // extends com.oplus.anim.EffectiveAnimationView (fork OEM di Lottie), che disegna via un
    // proprio EffectiveAnimationDrawable interno: né setColorFilter né setImageTintList hanno
    // effetto (confermati entrambi via log — nessuna eccezione, nessun cambiamento visivo,
    // 2026-08-20 mattina), perché il Drawable dipinge ogni livello con i suoi paint interni,
    // ignorando il tint/filtro standard di ImageView.
    //
    // Il vero meccanismo (trovato nel sorgente decompilato di EffectiveAnimationView, riga che
    // applica l'attributo XML lottie_colorFilter all'inflate):
    //   effectiveDrawable.addValueCallback(new KeyPath("**"), EffectiveAnimationProperty.COLOR_FILTER,
    //           new EffectiveValueCallback(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP)))
    // "**" è il KeyPath jolly standard di Lottie (tutti i livelli). addValueCallback mette in coda
    // la richiesta se la composizione non è ancora pronta (lazyCompositionTasks), quindi è sicuro
    // chiamarlo subito dopo initRow senza attese. Le classi sono nel fork OEM "com.oplus.anim.*",
    // non nella libreria Lottie originale "com.airbnb.lottie.*" (entrambe presenti nell'app,
    // package diversi — quella giusta è quella usata dalla classe reale della vista).
    private void applyIconColor(Object volumeRow) {
        if (mIconMode == 0) return; // predefinito, non toccare
        try {
            Object icon = callMethod(volumeRow, "getIcon");
            if (icon == null) return;
            int color = switch (mIconMode) {
                case 4 -> mIconColor;
                case 3 -> appAccentColor();
                case 2 -> 0xFFFFFFFF;
                default -> 0xFF404040; // scura
            };
            // Sempre entrambe le strade: applyLottieColorFilter "riesce" sempre (il campo
            // effectiveDrawable non è mai null) ma non ha alcun effetto quando l'icona reale
            // in vista non è una composizione Lottie ma un drawable statico applicato via
            // setImageResource (caso di Suoneria/Notifica/Sveglia in OplusVolumeRow — solo
            // Contenuti multimediali usa davvero un'icona/app dinamica) — senza il colorFilter
            // diretto quelle 3 righe restavano sempre del colore stock, mai bianche/accento.
            applyLottieColorFilter(icon, color);
            if (icon instanceof ImageView) {
                ((ImageView) icon).setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
            }
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] VolumePanelMod: icon color failed: " + t);
        }
    }

    /** true se il colore è stato applicato via addValueCallback (percorso Lottie reale). */
    private boolean applyLottieColorFilter(Object iconView, int color) {
        try {
            ClassLoader cl = iconView.getClass().getClassLoader();
            Object drawable = getObjectField(iconView, "effectiveDrawable");
            if (drawable == null) return false;
            Class<?> keyPathCls = findClass("com.oplus.anim.model.KeyPath", cl);
            Class<?> valueCallbackCls = findClass("com.oplus.anim.value.EffectiveValueCallback", cl);
            Class<?> propertyCls = findClass("com.oplus.anim.EffectiveAnimationProperty", cl);
            Object keyPath = keyPathCls.getConstructor(String[].class)
                    .newInstance((Object) new String[]{"**"});
            Object colorFilterProperty = de.robv.android.xposed.XposedHelpers.getStaticObjectField(propertyCls, "COLOR_FILTER");
            android.graphics.PorterDuffColorFilter filter = new android.graphics.PorterDuffColorFilter(
                    color, android.graphics.PorterDuff.Mode.SRC_ATOP);
            Object valueCallback = valueCallbackCls.getConstructor(Object.class).newInstance(filter);
            callMethod(drawable, "addValueCallback", keyPath, colorFilterProperty, valueCallback);
            return true;
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] VolumePanelMod: Lottie colorFilter failed: " + t);
            return false;
        }
    }

    private int appAccentColor() {
        if (Xprefs.getBoolean("DST_ACCENT1_on", false)) {
            return Xprefs.getInt("DST_ACCENT1", 0xFF908DFF) | 0xFF000000;
        }
        return 0xFF908DFF;
    }

    /** Live-refresh colors on all currently visible rows. */
    private void applyColorsToAll() {
        if (mOVDI != null) applyColorsToAll(mOVDI);
    }

    @SuppressWarnings("unchecked")
    private void applyColorsToAll(Object ovdi) {
        if (ovdi == null) return;
        try {
            List<Object> rows = (List<Object>) getObjectField(ovdi, "mRows");
            for (Object row : rows) applyColorsToRow(row);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] VolumePanelMod: applyColorsToAll error: " + t);
        }
    }

    @Override
    public boolean listensTo(String packageName) { return LISTEN.equals(packageName); }
}
