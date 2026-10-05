package it.tugaia56.obsidian.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Runtime theme helper that reads the user's DST colour selections and exposes
 * them to the app's UI adapters, so every screen in Obsidian reflects the same
 * accent/background palette chosen in the DST settings.
 *
 * Pref keys mirror DarkShadowUtils.PREF_PREFIX + "ACCENT1" / "BACKGROUND".
 */
public class ObsidianTheme {

    // ── Defaults (match colors.xml / obs_primary / obs_background) ────────────
    /** Default accent — soft lavender, matches obs_primary (#908DFF). */
    public static final int DEFAULT_ACCENT = 0xFF908DFF;
    /** Default background — dark navy, matches obs_background (#1B2029). */
    public static final int DEFAULT_BG     = 0xFF1B2029;
    /** Default light-mode background — soft off-white. */
    public static final int DEFAULT_BG_LIGHT = 0xFFF3F3F7;

    // ── App theme mode (Sistema/Chiaro/Scuro) ───────────────────────────────
    // Independent from the DST "Colore Sfondo" override above — this picks between the
    // app's OWN dark and light palette, DST_BACKGROUND still applies on top of whichever
    // base is active. "0"=Segui il Sistema (default), "1"=Chiaro, "2"=Scuro.
    public static final String KEY_THEME_MODE = "OBS_APP_THEME_MODE";
    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT  = 1;
    public static final int THEME_DARK   = 2;

    /** Cached each time {@link #refreshThemeMode} runs — every no-arg color getter below
     *  reads this instead of taking a Context, so none of the ~40 existing call sites
     *  throughout the app need to change. */
    private static boolean sDarkMode = true;

    /** Call once at app startup (before the first screen draws) and again whenever the
     *  user changes the Tema preference. Applies {@link AppCompatDelegate}'s night mode
     *  (covers system dialogs/keyboard) and refreshes the cached {@link #sDarkMode} flag
     *  used by every color getter in this class. */
    public static void refreshThemeMode(Context ctx) {
        int mode = themeMode();
        switch (mode) {
            case THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            case THEME_DARK  -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            default          -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
        sDarkMode = switch (mode) {
            case THEME_LIGHT -> false;
            case THEME_DARK  -> true;
            default -> {
                int night = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                yield night == Configuration.UI_MODE_NIGHT_YES;
            }
        };
    }

    public static int themeMode() {
        return ObsidianPrefs.getInt(KEY_THEME_MODE, THEME_SYSTEM);
    }

    /**
     * Keeps the DST "Preset Sfondo" custom-background toggle (separate, pre-existing
     * feature) in sync with the effective Tema state: on in dark, off in light — so a
     * custom dark preset doesn't keep masking Chiaro the way it did before this existed,
     * without ever discarding the chosen colour (only the on/off flag is touched, never
     * the stored colour itself, so switching back to Scuro restores exactly what was there).
     *
     * Deliberately NOT called from {@link #refreshThemeMode} itself — that runs on every
     * app launch regardless of mode, and unconditionally syncing there would silently
     * undo a manual "Disabilita" the user made afterward while staying on the same
     * explicit Chiaro/Scuro choice. Call this only right after the user actively picks a
     * Tema, and (for "Segui il Sistema" specifically) again on every app resume — see
     * {@code MainActivity.onResume()} — so it keeps tracking the system's actual current
     * state across day/night transitions that happen while the app isn't open, without
     * needing a live configuration-change listener.
     */
    public static void syncBackgroundPresetToTheme() {
        ObsidianPrefs.putBoolean("DST_BACKGROUND_on", sDarkMode);
    }

    public static boolean isDarkMode() {
        return sDarkMode;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the active accent colour.
     * Uses DST ACCENT1 if the user enabled it; otherwise {@link #DEFAULT_ACCENT}.
     */
    public static int accentColor() {
        Integer ov = ThemeOverride.accent();
        if (ov != null) return ensureOpaque(ov);
        if (ObsidianPrefs.getBoolean("DST_ACCENT1_on", false))
            return ensureOpaque(ObsidianPrefs.getInt("DST_ACCENT1", DEFAULT_ACCENT));
        return DEFAULT_ACCENT;
    }

    /** Default — indaco, come ACCENT2 di DarkShadowUtils. */
    public static final int DEFAULT_ACCENT2 = 0xFF3700B3;

    /** Come accentColor(), ma per l'Accento Secondario (DST_ACCENT2). */
    public static int accentColor2() {
        if (ObsidianPrefs.getBoolean("DST_ACCENT2_on", false))
            return ensureOpaque(ObsidianPrefs.getInt("DST_ACCENT2", DEFAULT_ACCENT2));
        return DEFAULT_ACCENT2;
    }

    /**
     * Returns the active screen-background colour.
     * Uses DST BACKGROUND if the user enabled it (regardless of light/dark — an explicit
     * custom background always wins); otherwise the light or dark default depending on
     * the active Tema mode ({@link #isDarkMode()}).
     */
    public static int bgColor() {
        Integer ovBg = ThemeOverride.bg();
        if (ovBg != null) return ensureOpaque(ovBg);
        if (ObsidianPrefs.getBoolean("DST_BACKGROUND_on", false))
            return ensureOpaque(ObsidianPrefs.getInt("DST_BACKGROUND", DEFAULT_BG));
        return sDarkMode ? DEFAULT_BG : DEFAULT_BG_LIGHT;
    }

    /** Per-channel RGB offsets (from the user's own Substratum sub-theme's background_dark→
     *  background_leanback_dark chain, all derived from the same #1b2029 base) reproducing
     *  the same brightening ramp on top of whatever {@link #bgColor()} actually is right now,
     *  instead of hardcoding her sub-theme's literal hex values. */
    private static final int[][] BG_PRESET_DELTAS = {
            {0, 0, 0},
            {7, 6, 6},
            {9, 8, 9},
            {13, 12, 13},
            {17, 17, 17},
            {21, 21, 22},
            {26, 25, 27},
            {30, 30, 31},
            {35, 34, 36},
    };

    /** Quick-pick swatches for "Colore personalizzato" pickers tied to the background: the
     *  same brightness ramp as {@link #BG_PRESET_DELTAS}, computed from the live
     *  {@link #bgColor()} instead of a fixed hex, plus a fixed neutral gray and transparent. */
    public static int[] bgDerivedPresets() {
        int base = bgColor();
        int r = (base >> 16) & 0xFF, g = (base >> 8) & 0xFF, b = base & 0xFF;
        int[] presets = new int[BG_PRESET_DELTAS.length + 2];
        for (int i = 0; i < BG_PRESET_DELTAS.length; i++) {
            int[] d = BG_PRESET_DELTAS[i];
            int rr = Math.min(255, r + d[0]);
            int gg = Math.min(255, g + d[1]);
            int bb = Math.min(255, b + d[2]);
            presets[i] = 0xFF000000 | (rr << 16) | (gg << 8) | bb;
        }
        presets[BG_PRESET_DELTAS.length] = 0xFF9CA1AD; // darker_gray, fisso
        presets[BG_PRESET_DELTAS.length + 1] = 0x00000000; // transparent
        return presets;
    }

    /**
     * Text/icon colour meant to sit on {@link #bgColor()}/{@link #cardColor()} — white on a
     * dark background, near-black on a light one. Deliberately reads the *actual* background
     * colour's luminance rather than the raw Tema flag: a custom DST "Colore Sfondo" always
     * wins over Tema for {@link #bgColor()} (see there), and it can be dark even while Tema
     * is set to Chiaro — text needs to follow what's really underneath it, not the setting
     * that got overridden, or it reads as low-contrast/washed-out against its own background.
     */
    public static int textColor() {
        return isDark(bgColor()) ? 0xFFFFFFFF : 0xFF1B1B1F;
    }

    /** Like {@link #textColor()} but at a given alpha (0-255) — for hint/secondary text,
     *  icon tints, dividers, etc. that were previously {@code Color.argb(a,255,255,255)}. */
    public static int textColor(int alpha) {
        return (textColor() & 0x00FFFFFF) | (alpha << 24);
    }

    /**
     * Returns a colour that stands out slightly from {@link #bgColor()} for use as
     * row / card backgrounds inside a RecyclerView. Brightness is raised by 5 % over a dark
     * background (cards read as lighter than the background) and lowered by 5 % over a light
     * one (cards read as a soft grey against a near-white background) — same idea, opposite
     * direction so the card never washes out into its background at either end. Driven by the
     * same actual-luminance check as {@link #textColor()}, for the same DST-override reason.
     */
    public static int cardColor() {
        int bg = bgColor();
        float[] hsv = new float[3];
        Color.colorToHSV(bg, hsv);
        hsv[2] = isDark(bg) ? Math.min(1f, hsv[2] + 0.05f) : Math.max(0f, hsv[2] - 0.05f);
        return Color.HSVToColor(hsv);
    }

    /** Perceived-luminance check (ITU-R BT.601) — true if {@code color} reads as dark enough
     *  that white text/cards should sit on top of it rather than near-black ones. */
    private static boolean isDark(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        return luminance < 0.5;
    }

    /**
     * Returns a dimmed (30 % alpha) version of the accent for switch/slider
     * off-state tracks and similar inactive indicators.
     */
    public static int accentDim() {
        int a = accentColor();
        return (a & 0x00FFFFFF) | (77 << 24);  // ~30 % opacity
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    /** Background for in-app dialogs that used to set the static, always-dark
     *  {@code obs_dialog_bg} drawable resource — built at runtime from {@link #bgColor()}
     *  so it follows Tema (and any DST override) instead of staying dark-only. */
    public static GradientDrawable dialogBackground(Context ctx) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor());
        bg.setCornerRadius(dp(ctx, 28));
        return bg;
    }

    /** Text colour for rows inside a *plain* system dialog (no custom Obsidian background,
     *  e.g. a stock {@code AlertDialog.Builder} with no {@code setBackgroundDrawable} call) —
     *  those dialogs already follow day/night automatically via the Material dialog style, so
     *  their row text must follow the same {@code ?android:attr/textColorPrimary} rather than
     *  {@link #textColor()}, which follows {@link #bgColor()} and can disagree with system
     *  day/night when a DST background override is active.
     *
     *  @deprecated superseded by {@link #themeDialog(AlertDialog)} — on this OEM's ROM, plain
     *  dialogs do NOT reliably follow {@code AppCompatDelegate}'s night-mode override when the
     *  device's own system dark mode disagrees with it (confirmed on-device 2026-08-24: a plain
     *  {@code MaterialAlertDialogBuilder} rendered a fully dark shell with invisible dark-on-dark
     *  text while Tema was set to Chiaro and system dark mode was on). Kept only so existing call
     *  sites still compile; new code should call {@link #themeDialog(AlertDialog)} instead, which
     *  forces both the shell and the text explicitly rather than trusting system attrs. */
    @Deprecated
    public static int systemDialogTextColor(Context ctx) {
        android.content.res.TypedArray ta = ctx.obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
        int color = ta.getColor(0, textColor());
        ta.recycle();
        return color;
    }

    /**
     * Forces an already-{@code .show()}n {@link AlertDialog} to actually follow Tema, on top of
     * whatever the OS/Material would have resolved on its own — see the deprecation note on
     * {@link #systemDialogTextColor} for why this exists. Sets the window background explicitly
     * via {@link #dialogBackground} and recursively recolours every {@link TextView} in the decor
     * tree to {@link #textColor()}, skipping {@link Button}s so the accent-coloured action buttons
     * (APPLICA/ESCI etc., which already render correctly via {@code ?attr/colorPrimary}) are left
     * alone. Safe to call on dialogs that already set their own row colours explicitly — recolouring
     * an already-correct {@code TextView} to the same value is a no-op visually.
     */
    public static void themeDialog(android.app.Dialog d) {
        if (d.getWindow() == null) return;
        d.getWindow().setBackgroundDrawable(dialogBackground(d.getContext()));
        recolorDialogText(d.getWindow().getDecorView());
    }

    private static void recolorDialogText(android.view.View v) {
        if (v instanceof android.widget.Button) return;
        if (v instanceof android.widget.TextView tv) tv.setTextColor(textColor());
        if (v instanceof ViewGroup vg) {
            for (int i = 0; i < vg.getChildCount(); i++) recolorDialogText(vg.getChildAt(i));
        }
    }

    // ── Row grouping (merged-card look, matches OC/OOS category cards) ─────────

    /**
     * Position of a row within a visual group of rows that should render as one
     * continuous card (no gap, only the group's outer corners rounded) instead of
     * each row being its own separate rounded pill.
     * {@link #SINGLE} reproduces the old always-standalone-pill look — the default
     * on every existing item, so screens that never opt in are unaffected.
     */
    public enum GroupPos { SINGLE, TOP, MIDDLE, BOTTOM }

    /** Builds the background drawable for a row at the given group position. */
    public static GradientDrawable groupBackground(Context ctx, GroupPos pos) {
        float radius = dp(ctx, 12);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cardColor());
        boolean roundTop    = pos == GroupPos.SINGLE || pos == GroupPos.TOP;
        boolean roundBottom = pos == GroupPos.SINGLE || pos == GroupPos.BOTTOM;
        float top    = roundTop    ? radius : 0f;
        float bottom = roundBottom ? radius : 0f;
        bg.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
        return bg;
    }

    /** Like {@link #groupBackground} but lighter (10% instead of 5%) with a subtle border —
     *  for "options revealed by tapping a row above" content, so it visually reads as
     *  belonging to that row instead of blending into the rest of the card. */
    public static GradientDrawable nestedGroupBackground(Context ctx, GroupPos pos) {
        float radius = dp(ctx, 12);
        GradientDrawable bg = new GradientDrawable();
        int base = bgColor();
        float[] hsv = new float[3];
        Color.colorToHSV(base, hsv);
        hsv[2] = isDark(base) ? Math.min(1f, hsv[2] + 0.10f) : Math.max(0f, hsv[2] - 0.10f);
        bg.setColor(Color.HSVToColor(hsv));
        bg.setStroke(dp(ctx, 1), textColor(0x26));
        boolean roundTop    = pos == GroupPos.SINGLE || pos == GroupPos.TOP;
        boolean roundBottom = pos == GroupPos.SINGLE || pos == GroupPos.BOTTOM;
        float top    = roundTop    ? radius : 0f;
        float bottom = roundBottom ? radius : 0f;
        bg.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
        return bg;
    }

    /** Applies the correct margins for a row at the given group position. */
    public static void applyGroupMargins(Context ctx, ViewGroup.MarginLayoutParams lp, GroupPos pos) {
        int mH   = dp(ctx, 12);
        int mTop = (pos == GroupPos.SINGLE || pos == GroupPos.TOP) ? dp(ctx, 3) : 0;
        int mBot = (pos == GroupPos.SINGLE || pos == GroupPos.BOTTOM) ? dp(ctx, 3) : 0;
        lp.setMargins(mH, mTop, mH, mBot);
    }

    // ── Dimension helper ──────────────────────────────────────────────────────

    /** Converts dp to pixels using the given Context's display metrics. */
    public static int dp(Context ctx, float dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                ctx.getResources().getDisplayMetrics());
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /** Forces full opacity on a colour that might have been stored with alpha = 0. */
    private static int ensureOpaque(int color) {
        return color | 0xFF000000;
    }
}
