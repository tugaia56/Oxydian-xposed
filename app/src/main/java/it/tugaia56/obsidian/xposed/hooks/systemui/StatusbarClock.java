package it.tugaia56.obsidian.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.annotation.SuppressLint;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Status bar clock customisation:
 *  – Position: left / center / right         (pref "status_bar_clock", values 2/1/0)
 *  – Font size: 12–20 sp                     (pref "status_bar_clock_size", int)
 *  – Extra padding: 0–16 dp                  (pref "status_bar_clock_padding", int)
 *  – Show seconds                            (pref "status_bar_clock_seconds", boolean)
 *  – AM/PM style                             (pref "status_bar_am_pm", "0"=norm/"1"=small/"2"=hidden)
 *  – Date display: off / small / normal      (pref "status_bar_clock_date_display", "0/1/2")
 *  – Date position: before / after           (pref "status_bar_clock_date_position", "0/1")
 *  – Custom text before clock                (pref "sbc_before_clock_format", String)
 *  – Before text small (70%)                 (pref "sbc_before_small", boolean)
 *  – Custom text after clock                 (pref "sbc_after_clock_format", String)
 *  – After text small (70%)                  (pref "sbc_after_small", boolean)
 *  – Auto-hide when launcher visible         (pref "status_bar_clock_auto_hide_launcher", boolean)
 *  – Auto-hide on interval                   (pref "status_bar_clock_auto_hide", boolean)
 *  – Custom color                            (pref "status_bar_custom_clock_color" bool +
 *                                                  "status_bar_clock_color" int)
 */
public class StatusbarClock extends XposedMods {

    // Clock position
    private static final int POS_RIGHT  = 0;
    private static final int POS_CENTER = 1;
    private static final int POS_LEFT   = 2;

    // Date display
    private static final int DATE_GONE  = 0;
    private static final int DATE_SMALL = 1;

    // Date position
    private static final int DATE_BEFORE = 0;

    // AM/PM style
    private static final int AM_PM_NORMAL = 0;
    private static final int AM_PM_SMALL  = 1;
    private static final int AM_PM_GONE   = 2;

    // Auto-hide defaults (seconds)
    private static final int DEFAULT_HIDE_DURATION = 60;
    private static final int DEFAULT_SHOW_DURATION = 5;

    // ── Prefs ──────────────────────────────────────────────────────────────────

    private int     mPosition    = POS_LEFT;
    private int     mSize        = 12;
    private int     mPadding     = 0;
    private boolean mCustomColor = false;
    private int     mColor       = Color.WHITE;

    // Background chip (reale OC status_bar_clock_background_chip)
    private boolean mChipOn           = false;
    private int     mChipStyle        = 0; // 0=pieno 1=contorno 2=misto
    private boolean mChipFillAccent   = true;
    private int     mChipFillColor    = Color.WHITE;
    private boolean mChipStrokeAccent = true;
    private int     mChipStrokeColor  = Color.WHITE;
    private int     mChipStrokeWidth  = 2;  // dp
    private boolean mChipRoundCorners = false;
    private int     mChipCorner       = 14; // dp
    private int     mChipMarginTop    = 0, mChipMarginLeft = 0, mChipMarginRight = 0, mChipMarginBottom = 0;
    private int     mChipPadTop       = 0, mChipPadLeft    = 0, mChipPadRight    = 0, mChipPadBottom    = 0;

    private boolean mShowSeconds = false;
    private int     mAmPmStyle   = AM_PM_GONE;

    private int     mDateDisplay = DATE_GONE;
    private int     mDatePos     = DATE_BEFORE;
    private String  mDateFormat  = "EEE, d MMM";

    private String  mBeforeClock = "";
    private boolean mBeforeSmall = false;
    private String  mAfterClock  = "";
    private boolean mAfterSmall  = false;

    // Switch diagnostici 2026-09-25 (troncamento "09:..." mai risolto del tutto, vedi 09-20):
    // spengono posizione/dimensione/padding/chip (Stile Orologio) o secondi/AM-PM/testo
    // prima-dopo/data (Stile Data) senza dover disattivare l'intero modulo per testare.
    private boolean mClockStyleOn = true;
    private boolean mDateStyleOn  = true;

    private boolean mAutoHideLauncher = false;
    private boolean mAutoHide         = false;
    private int     mHideDuration     = DEFAULT_HIDE_DURATION;
    private int     mShowDuration     = DEFAULT_SHOW_DURATION;

    // Computed before/after (resolved from date + custom text prefs)
    private String  mComputedBefore      = "";
    private boolean mComputedBeforeSmall = false;
    private String  mComputedAfter       = "";
    private boolean mComputedAfterSmall  = false;

    // ── Views ──────────────────────────────────────────────────────────────────

    private TextView     mClockView      = null;
    private Object       mSbFragment     = null;
    private ViewGroup    mStartSide      = null;
    private View         mCenteredArea   = null;
    private LinearLayout mSystemIconArea = null;
    private int          mStartPadding   = 0;

    // ── Auto-hide ──────────────────────────────────────────────────────────────

    private final Handler autoHideHandler  = new Handler(Looper.getMainLooper());
    private boolean       mScreenOn        = true;
    private boolean       mLauncherVisible = false;

    // Initialised in constructor to avoid illegal forward reference
    private Runnable mHideRunnable;
    private Runnable mShowRunnable;

    private final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mClockView == null) return;
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mScreenOn = false;
                autoHideHandler.removeCallbacks(mHideRunnable);
                autoHideHandler.removeCallbacks(mShowRunnable);
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                mScreenOn = true;
                updateAutoHide();
            }
        }
    };

    // ── Constructor ────────────────────────────────────────────────────────────

    public StatusbarClock(Context context) {
        super(context);

        // Runnables must be assigned here (not as field initializers) to avoid
        // illegal forward references between mHideRunnable and mShowRunnable.
        mHideRunnable = () -> {
            if (mClockView != null) mClockView.setVisibility(View.INVISIBLE);
            autoHideHandler.postDelayed(mShowRunnable, mHideDuration * 1000L);
        };
        mShowRunnable = () -> {
            if (mClockView != null) mClockView.setVisibility(View.VISIBLE);
            if (mAutoHide) autoHideHandler.postDelayed(mHideRunnable, mShowDuration * 1000L);
        };

        try {
            int id = mContext.getResources().getIdentifier(
                    "status_bar_clock_starting_padding", "dimen", mContext.getPackageName());
            if (id != 0)
                mStartPadding = mContext.getResources().getDimensionPixelSize(id);
        } catch (Throwable ignored) {}
    }

    // ── XposedMods ─────────────────────────────────────────────────────────────

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;

        mClockStyleOn = Xprefs.getBoolean("status_bar_clock_style_enabled", true);
        mDateStyleOn  = Xprefs.getBoolean("status_bar_clock_date_style_enabled", true);

        mPosition    = Integer.parseInt(Xprefs.getString("status_bar_clock", String.valueOf(POS_LEFT)));
        mSize        = Xprefs.getInt("status_bar_clock_size",    12);
        mPadding     = Xprefs.getInt("status_bar_clock_padding", 0);
        mCustomColor = Xprefs.getBoolean("status_bar_custom_clock_color", false);
        mColor       = Xprefs.getBoolean("status_bar_clock_color_use_accent", false)
                ? appAccentColor() : Xprefs.getInt("status_bar_clock_color", Color.WHITE);

        mChipOn           = Xprefs.getBoolean("status_bar_clock_background_chip_switch", false);
        mChipStyle        = parseIntSafe(Xprefs.getString("status_bar_clock_background_chip_style", "0"), 0);
        mChipFillAccent   = Xprefs.getBoolean("status_bar_clock_background_chip_fill_accent", true);
        mChipFillColor    = Xprefs.getInt("status_bar_clock_background_chip_color", Color.WHITE);
        mChipStrokeAccent = Xprefs.getBoolean("status_bar_clock_background_chip_stroke_accent", true);
        mChipStrokeColor  = Xprefs.getInt("status_bar_clock_background_chip_stroke_color", Color.WHITE);
        mChipStrokeWidth  = Xprefs.getInt("status_bar_clock_background_chip_stroke_width", 2);
        mChipRoundCorners = Xprefs.getBoolean("status_bar_clock_background_chip_round_corners", false);
        mChipCorner       = Xprefs.getInt("status_bar_clock_background_chip_corner", 14);
        mChipMarginTop    = Xprefs.getInt("status_bar_clock_background_chip_margin_top", 0);
        mChipMarginLeft   = Xprefs.getInt("status_bar_clock_background_chip_margin_left", 0);
        mChipMarginRight  = Xprefs.getInt("status_bar_clock_background_chip_margin_right", 0);
        mChipMarginBottom = Xprefs.getInt("status_bar_clock_background_chip_margin_bottom", 0);
        mChipPadTop       = Xprefs.getInt("status_bar_clock_background_chip_padding_top", 0);
        mChipPadLeft      = Xprefs.getInt("status_bar_clock_background_chip_padding_left", 0);
        mChipPadRight     = Xprefs.getInt("status_bar_clock_background_chip_padding_right", 0);
        mChipPadBottom    = Xprefs.getInt("status_bar_clock_background_chip_padding_bottom", 0);

        mShowSeconds = Xprefs.getBoolean("status_bar_clock_seconds", false);
        mAmPmStyle   = Integer.parseInt(Xprefs.getString("status_bar_am_pm", String.valueOf(AM_PM_GONE)));

        mDateDisplay = Integer.parseInt(Xprefs.getString("status_bar_clock_date_display", "0"));
        mDatePos     = Integer.parseInt(Xprefs.getString("status_bar_clock_date_position", "0"));
        mDateFormat  = Xprefs.getString("status_bar_clock_date_format", "EEE, d MMM");

        mBeforeClock = Xprefs.getString("sbc_before_clock_format", "");
        mBeforeSmall = Xprefs.getBoolean("sbc_before_small", false);
        mAfterClock  = Xprefs.getString("sbc_after_clock_format", "");
        mAfterSmall  = Xprefs.getBoolean("sbc_after_small", false);

        mAutoHideLauncher = Xprefs.getBoolean("status_bar_clock_auto_hide_launcher", false);
        mAutoHide         = Xprefs.getBoolean("status_bar_clock_auto_hide", false);
        mHideDuration     = Xprefs.getSliderInt("status_bar_clock_auto_hide_hduration", DEFAULT_HIDE_DURATION);
        mShowDuration     = Xprefs.getSliderInt("status_bar_clock_auto_hide_sduration", DEFAULT_SHOW_DURATION);

        buildComputedTexts();

        if (Key.length > 0) {
            switch (Key[0]) {
                case "status_bar_clock_style_enabled":
                case "status_bar_clock_date_style_enabled":
                    // Riapplica tutto — i metodi sotto controllano già i due switch
                    // internamente, quindi questo copre sia l'accensione che lo spegnimento.
                    // Spegnere non "ripristina" retroattivamente un layout già modificato
                    // (es. altezza/minWidth della View già impostati) — potrebbe servire un
                    // riavvio di SystemUI per tornare 100% allo stock, come altre opzioni qui.
                    placeClock();
                    setClockSize();
                    updateChip();
                    refreshClock();
                    break;
                case "status_bar_clock":
                    placeClock();
                    break;
                case "status_bar_clock_size":
                case "status_bar_clock_padding":
                    setClockSize();
                    break;
                case "status_bar_clock_auto_hide":
                case "status_bar_clock_auto_hide_launcher":
                    updateAutoHide();
                    break;
                case "status_bar_clock_background_chip_switch":
                case "status_bar_clock_background_chip_style":
                case "status_bar_clock_background_chip_fill_accent":
                case "status_bar_clock_background_chip_color":
                case "status_bar_clock_background_chip_stroke_accent":
                case "status_bar_clock_background_chip_stroke_color":
                case "status_bar_clock_background_chip_stroke_width":
                case "status_bar_clock_background_chip_round_corners":
                case "status_bar_clock_background_chip_corner":
                case "status_bar_clock_background_chip_margin_top":
                case "status_bar_clock_background_chip_margin_left":
                case "status_bar_clock_background_chip_margin_right":
                case "status_bar_clock_background_chip_margin_bottom":
                case "status_bar_clock_background_chip_padding_top":
                case "status_bar_clock_background_chip_padding_left":
                case "status_bar_clock_background_chip_padding_right":
                case "status_bar_clock_background_chip_padding_bottom":
                    updateChip();
                    break;
                default:
                    refreshClock();
                    break;
            }
        } else {
            // No specific key: called from the post-Xprefs refresh pass (background thread).
            // All view operations must run on the main thread.
            new Handler(Looper.getMainLooper()).post(() -> {
                placeClock();
                setClockSize();
                refreshClock();
                updateAutoHide();
                updateChip();
            });
        }
    }

    private static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (Throwable t) { return def; }
    }

    /** Reads clock font size live from Xprefs, falls back to cached mSize. */
    private int liveClockSize() {
        if (!mClockStyleOn) return 0; // spegne anche i 3 hook StatClock (onMeasure/config/minWidth) sotto
        try {
            return (Xprefs != null) ? Xprefs.getInt("status_bar_clock_size", mSize) : mSize;
        } catch (Throwable t) {
            return mSize;
        }
    }

    /** Read an int pref directly from the XML file — no ContentProvider needed. */
    private static int readIntFromPrefsFile(String key, int def) {
        try {
            java.io.File f = new java.io.File(
                "/data/user_de/0/it.tugaia56.obsidian/shared_prefs/it.tugaia56.obsidian_preferences.xml");
            if (!f.canRead()) return def;
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            String xml = sb.toString();
            String tag = "name=\"" + key + "\"";
            int idx = xml.indexOf(tag);
            if (idx < 0) return def;
            String rest = xml.substring(idx + tag.length());
            int vi = rest.indexOf("value=\"");
            if (vi < 0) return def;
            rest = rest.substring(vi + 7);
            int end = rest.indexOf('"');
            if (end < 0) return def;
            return Integer.parseInt(rest.substring(0, end).trim());
        } catch (Throwable t) {
            return def;
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!SYSTEM_UI.equals(lp.packageName)) return;

        // Pre-populate mSize from the prefs file before ContentProvider is available.
        // Without this, the clock shows stock size for 1-3 s after SystemUI restart.
        int preloadedSize = readIntFromPrefsFile("status_bar_clock_size", 0);
        if (preloadedSize > 0) {
            mSize = preloadedSize;
            log("[ Obsidian ] StatusbarClock: preloaded mSize=" + mSize);
        }

        // Register screen on/off receiver
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            mContext.registerReceiver(mScreenReceiver, filter);
        } catch (Throwable ignored) {}

        // ── 1. CollapsedStatusBarFragment: grab views ─────────────────────────

        try {
            Class<?> CollapsedSBF = findClass(
                    "com.android.systemui.statusbar.phone.fragment.CollapsedStatusBarFragment",
                    lp.classLoader);

            hookAllMethods(CollapsedSBF, "onViewCreated", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    mSbFragment = p.thisObject;

                    try {
                        mClockView = (TextView) getObjectField(p.thisObject, "mClockView");
                    } catch (Throwable t) {
                        log("[ Obsidian ] StatusbarClock: mClockView not found: " + t);
                        return;
                    }

                    ViewGroup mStatusBar = null;
                    try {
                        mStatusBar = (ViewGroup) getObjectField(mSbFragment, "mStatusBar");
                    } catch (Throwable ignored) {}

                    if (mStatusBar != null) {
                        int sideId = mContext.getResources().getIdentifier(
                                "status_bar_start_side_except_heads_up", "id",
                                mContext.getPackageName());
                        if (sideId == 0) sideId = mContext.getResources().getIdentifier(
                                "status_bar_left_side", "id", mContext.getPackageName());
                        if (sideId != 0)
                            mStartSide = mStatusBar.findViewById(sideId);

                        int iconId = mContext.getResources().getIdentifier(
                                "statusIcons", "id", mContext.getPackageName());
                        if (iconId == 0) iconId = mContext.getResources().getIdentifier(
                                "system_icon_area", "id", mContext.getPackageName());
                        if (iconId != 0)
                            mSystemIconArea = mStatusBar.findViewById(iconId);
                    }

                    try {
                        mCenteredArea = (View) ((View) getObjectField(
                                p.thisObject, "mCenteredIconArea")).getParent();
                    } catch (Throwable ignored) {
                        if (mStatusBar != null) {
                            LinearLayout center = new LinearLayout(mContext);
                            FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT);
                            lp2.gravity = Gravity.CENTER;
                            center.setLayoutParams(lp2);
                            mStatusBar.addView(center);
                            mCenteredArea = center;
                        }
                    }

                    placeClock();
                    setClockSize();
                    refreshClock();
                    updateAutoHide();
                    updateChip();
                }
            });
        } catch (Throwable t) {
            log("[ Obsidian ] StatusbarClock: CollapsedStatusBarFragment hook failed: " + t);
        }

        // ── 2. Clock.updateClockVisibility: apply custom text color ───────────

        try {
            Class<?> ClockClass = findClass(
                    "com.android.systemui.statusbar.policy.Clock", lp.classLoader);

            hookAllMethods(ClockClass, "updateClockVisibility", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    if (p.thisObject != mClockView || !mCustomColor || mClockView == null) return;
                    mClockView.post(() -> mClockView.setTextColor(mColor));
                }
            });
        } catch (Throwable t) {
            log("[ Obsidian ] StatusbarClock: updateClockVisibility hook failed: " + t);
        }

        // ── 2b. TextView.setTextSize: dirottiamo OGNI chiamata sull'orologio ──
        //    Confermato funzionante 2026-09-25 (poi tolto per errore durante il porting da OC,
        //    rimesso): qualcosa di nativo continua a reimporre una dimensione diversa dalla
        //    nostra in punti che i soli hook su StatClock non coprono — dirottare il setter
        //    stesso garantisce che nessuno possa più imporne una diversa.
        try {
            hookAllMethods(TextView.class, "setTextSize", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    if (p.thisObject != mClockView || !mClockStyleOn) return;
                    int sz = liveClockSize();
                    if (sz <= 0) return;
                    if (p.args.length == 2 && p.args[0] instanceof Integer && p.args[1] instanceof Float) {
                        p.args[0] = TypedValue.COMPLEX_UNIT_SP;
                        p.args[1] = (float) sz;
                    } else if (p.args.length == 1 && p.args[0] instanceof Float) {
                        p.args[0] = (float) sz;
                    }
                }
            });
        } catch (Throwable t) {
            log("[ Obsidian ] StatusbarClock: setTextSize hijack hook failed: " + t);
        }

        // ── 3a. Clock.getSmallTime BEFORE: toggle seconds field ───────────────

        try {
            Class<?> ClockClass = findClass(
                    "com.android.systemui.statusbar.policy.Clock", lp.classLoader);

            hookAllMethods(ClockClass, "getSmallTime", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    if (p.thisObject != mClockView || !mDateStyleOn) return;
                    try {
                        setObjectField(p.thisObject, "mShowSeconds", mShowSeconds);
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            log("[ Obsidian ] StatusbarClock: getSmallTime before-hook failed: " + t);
        }

        // ── 3b. Clock.getSmallTime AFTER: text size, before/after, color ──────

        try {
            Class<?> ClockClass = findClass(
                    "com.android.systemui.statusbar.policy.Clock", lp.classLoader);

            hookAllMethods(ClockClass, "getSmallTime", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    if (p.thisObject != mClockView) return;

                    TextView tv = (TextView) p.thisObject;
                    if (mClockStyleOn) tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, mSize);

                    CharSequence original = (CharSequence) p.getResult();
                    if (original == null) return;

                    boolean hasBefore = !mComputedBefore.isEmpty();
                    boolean hasAfter  = !mComputedAfter.isEmpty();
                    boolean hasAmPm   = mDateStyleOn && (mAmPmStyle != AM_PM_GONE);
                    if (!mCustomColor && !hasBefore && !hasAfter && !hasAmPm) return;

                    SpannableStringBuilder result = new SpannableStringBuilder();

                    // Before text
                    if (hasBefore) {
                        result.append(buildSpan(formatText(mComputedBefore), mComputedBeforeSmall));
                        result.append(" ");
                    }

                    // Clock text
                    SpannableStringBuilder clockSpan = SpannableStringBuilder.valueOf(original);
                    if (mCustomColor) {
                        clockSpan.setSpan(new ForegroundColorSpan(mColor), 0, clockSpan.length(),
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    result.append(clockSpan);

                    // AM/PM
                    if (hasAmPm) {
                        String ampm = new SimpleDateFormat("a", Locale.getDefault()).format(new Date());
                        SpannableStringBuilder ampmSpan = new SpannableStringBuilder(ampm);
                        if (mAmPmStyle == AM_PM_SMALL) {
                            ampmSpan.setSpan(new RelativeSizeSpan(0.75f), 0, ampmSpan.length(),
                                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        if (mCustomColor) {
                            ampmSpan.setSpan(new ForegroundColorSpan(mColor), 0, ampmSpan.length(),
                                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        result.append(ampmSpan);
                    }

                    // After text
                    if (hasAfter) {
                        result.append(" ");
                        result.append(buildSpan(formatText(mComputedAfter), mComputedAfterSmall));
                    }

                    p.setResult(result);
                }
            });
        } catch (Throwable t) {
            log("[ Obsidian ] StatusbarClock: getSmallTime after-hook failed: " + t);
        }

        // ── 4. StatClock hooks (OC approach for SDK36) ───────────────────────────
        //    On OOS16/SDK36, StatClock.onMeasure() resets the text size to stock (12sp)
        //    on every layout pass. Hooking setTextSize on TextView causes oscillation.
        //    OC's fix: afterHook onMeasure + updateConfigurationChanged + updateMinWidth
        //    to re-assert our size after the system resets it (same as OC's implementation).

        try {
            Class<?> StatClock = findClass(
                    "com.oplus.systemui.statusbar.widget.StatClock", lp.classLoader);

            // onMeasure: fires on every layout pass — re-assert size after measurement
            hookAllMethods(StatClock, "onMeasure", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    if (mClockView == null || p.thisObject != mClockView) return;
                    int sz = liveClockSize();
                    if (sz <= 0) return;
                    TextView tv = (TextView) p.thisObject;
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sz);
                    float textWidth = measureExpectedClockWidth(sz);
                    int w = (int)(textWidth + 0.5f) + tv.getPaddingLeft() + tv.getPaddingRight();
                    if (tv.getMinimumWidth() != w) tv.setMinimumWidth(w);
                    try { callMethod(p.thisObject, "setMeasuredDimension", w, p.args[1]); }
                    catch (Throwable ignored) {}
                }
            });

            // updateConfigurationChanged: called on config changes (rotation, font scale…)
            hookAllMethods(StatClock, "updateConfigurationChanged", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    if (mClockView == null || p.thisObject != mClockView) return;
                    int sz = liveClockSize();
                    if (sz <= 0) return;
                    TextView tv = (TextView) p.thisObject;
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sz);
                    try { tv.postInvalidate(); } catch (Throwable ignored) {}
                }
            });

            // updateMinWidth: re-assert size and correct minimum width
            hookAllMethods(StatClock, "updateMinWidth", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    if (mClockView == null || p.thisObject != mClockView) return;
                    int sz = liveClockSize();
                    if (sz <= 0) return;
                    TextView tv = (TextView) p.thisObject;
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sz);
                    // Stessa logica di onMeasure sopra: TextPaint fresco, non tv.getText().
                    float textWidth = measureExpectedClockWidth(sz);
                    int w = (int)(textWidth + 0.5f) + tv.getPaddingLeft() + tv.getPaddingRight();
                    tv.setMinimumWidth(w);
                }
            });

            log("[ Obsidian ] StatusbarClock: StatClock onMeasure/configChanged/minWidth hooked");
        } catch (Throwable t) {
            log("[ Obsidian ] StatusbarClock: StatClock hooks failed: " + t);
        }

        // ── 4b. StartSideExceptHeadsUpLayout.onLayout: il vero blocco ─────────
        //    2026-09-25: confermato via dump nativo (View.toString(), non un log nostro) che i
        //    bounds REALI assegnati all'orologio restano stretti (es. "0,0-117,91") a
        //    prescindere da qualunque larghezza chiediamo in onMeasure/setMinimumWidth — il
        //    genitore (questa classe custom Oplus, il contenitore "start_side_except_heads_up")
        //    decide i bounds finali nel proprio onLayout con una logica sua che non consulta più
        //    la nostra larghezza dopo la fase di misura. Fix: dopo che il nativo ha fatto il suo
        //    onLayout, ri-posizioniamo NOI il solo orologio con la larghezza vera che ci serve —
        //    è il primo figlio (index 1), quindi allargarlo a destra non sposta nulla a sinistra.
        try {
            Class<?> StartSide = findClass(
                    "com.oplus.systemui.statusbar.widget.StartSideExceptHeadsUpLayout", lp.classLoader);
            hookAllMethods(StartSide, "onLayout", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    if (mClockView == null || !mClockStyleOn) return;
                    if (mClockView.getParent() != p.thisObject) return;
                    int sz = liveClockSize();
                    if (sz <= 0) return;
                    float textWidth = measureExpectedClockWidth(sz);
                    int desired = (int) (textWidth + 0.5f)
                            + mClockView.getPaddingLeft() + mClockView.getPaddingRight();
                    int l = mClockView.getLeft(), t = mClockView.getTop(), b = mClockView.getBottom();
                    if (mClockView.getWidth() < desired) {
                        mClockView.layout(l, t, l + desired, b);
                    }
                }
            });
            log("[ Obsidian ] StatusbarClock: StartSideExceptHeadsUpLayout.onLayout hooked");
        } catch (Throwable t) {
            log("[ Obsidian ] StatusbarClock: StartSideExceptHeadsUpLayout hook failed: " + t);
        }

        // ── 5. Auto-hide launcher: TaskStackListenerImpl ──────────────────────

        try {
            Class<?> TaskStackListenerImpl = findClass(
                    "com.android.wm.shell.common.TaskStackListenerImpl", lp.classLoader);

            hookAllMethods(TaskStackListenerImpl, "onTaskMovedToFront", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    if (!mAutoHideLauncher || mClockView == null) return;
                    try {
                        Object taskInfo  = p.args[0];
                        Intent baseIntent = (Intent) callMethod(taskInfo, "getBaseIntent");
                        if (baseIntent == null) return;
                        String pkg = baseIntent.getPackage();
                        if (pkg == null && baseIntent.getComponent() != null)
                            pkg = baseIntent.getComponent().getPackageName();
                        if (pkg == null) return;

                        final boolean launcher = isLauncherPackage(pkg);
                        mLauncherVisible = launcher;
                        final int vis = launcher ? View.INVISIBLE : View.VISIBLE;
                        mClockView.post(() -> mClockView.setVisibility(vis));
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}
    }

    /** Misura la larghezza del testo COMPLETO atteso (prima + ora + am/pm + dopo), ricostruito
     *  da zero con la stessa logica dell'hook 3b su getSmallTime() — non legge tv.getText()
     *  (poteva riflettere un frame precedente, non ancora aggiornato con "prima"/"dopo"/data).
     *  Usa un TextPaint NUOVO dimensionato via TypedValue.applyDimension (stesso approccio di
     *  OC), non tv.getPaint(). 2026-09-25: root-causa vera del troncamento "09:..." trovata
     *  DOPO: il genitore (StartSideExceptHeadsUpLayout) assegna bounds finali fissi nel proprio
     *  onLayout ignorando qualunque larghezza chiesta qui — vedi l'hook su onLayout più sotto,
     *  quello risolve davvero. Questo metodo resta comunque corretto/necessario: gli serve un
     *  numero preciso da passare a quell'hook. */
    private float measureExpectedClockWidth(int sizeSp) {
        android.text.TextPaint textPaint = new android.text.TextPaint();
        float textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp,
                mContext.getResources().getDisplayMetrics());
        textPaint.setTextSize(textSizePx);
        textPaint.setTypeface(mClockView.getTypeface());

        StringBuilder sb = new StringBuilder();
        boolean hasBefore = mDateStyleOn && !mComputedBefore.isEmpty();
        boolean hasAfter  = mDateStyleOn && !mComputedAfter.isEmpty();
        boolean hasAmPm   = mDateStyleOn && (mAmPmStyle != AM_PM_GONE);
        if (hasBefore) { sb.append(formatText(mComputedBefore)); sb.append(" "); }
        String pattern = mShowSeconds ? "HH:mm:ss" : "HH:mm";
        sb.append(new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date()));
        if (hasAmPm) sb.append(new SimpleDateFormat("a", Locale.getDefault()).format(new Date()));
        if (hasAfter) { sb.append(" "); sb.append(formatText(mComputedAfter)); }

        float maxWidth = 0f;
        for (String line : sb.toString().split("\n")) {
            maxWidth = Math.max(maxWidth, textPaint.measureText(line));
        }
        return maxWidth;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Resolve computed before/after texts.
     * Custom text overrides date display when non-empty.
     */
    private void buildComputedTexts() {
        if (!mDateStyleOn) {
            mComputedBefore = ""; mComputedBeforeSmall = false;
            mComputedAfter  = ""; mComputedAfterSmall  = false;
            return;
        }
        boolean hasCustom = !mBeforeClock.isEmpty() || !mAfterClock.isEmpty();
        if (hasCustom) {
            mComputedBefore      = mBeforeClock;
            mComputedBeforeSmall = mBeforeSmall;
            mComputedAfter       = mAfterClock;
            mComputedAfterSmall  = mAfterSmall;
        } else if (mDateDisplay != DATE_GONE) {
            boolean small = (mDateDisplay == DATE_SMALL);
            String fmt = (mDateFormat != null && !mDateFormat.isEmpty()) ? mDateFormat : "EEE, d MMM";
            if (mDatePos == DATE_BEFORE) {
                mComputedBefore      = fmt;
                mComputedBeforeSmall = small;
                mComputedAfter       = "";
                mComputedAfterSmall  = false;
            } else {
                mComputedBefore      = "";
                mComputedBeforeSmall = false;
                mComputedAfter       = fmt;
                mComputedAfterSmall  = small;
            }
        } else {
            mComputedBefore = ""; mComputedBeforeSmall = false;
            mComputedAfter  = ""; mComputedAfterSmall  = false;
        }
    }

    /** Build a spannable applying optional relative-size and color spans. */
    private SpannableStringBuilder buildSpan(String text, boolean small) {
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        int len = sb.length();
        if (small)       sb.setSpan(new RelativeSizeSpan(0.75f),            0, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (mCustomColor) sb.setSpan(new ForegroundColorSpan(mColor),       0, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    /**
     * Format text: try as SimpleDateFormat pattern; fall back to literal.
     */
    private String formatText(String pattern) {
        if (pattern == null || pattern.isEmpty()) return "";
        try {
            return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date());
        } catch (Throwable ignored) {
            return pattern;
        }
    }

    /** Check whether a package is the default launcher. */
    private boolean isLauncherPackage(String pkg) {
        try {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> launchers = mContext.getPackageManager()
                    .queryIntentActivities(homeIntent, 0);
            for (ResolveInfo ri : launchers) {
                if (pkg.equals(ri.activityInfo.packageName)) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /** Start / stop interval auto-hide. */
    private void updateAutoHide() {
        autoHideHandler.removeCallbacks(mHideRunnable);
        autoHideHandler.removeCallbacks(mShowRunnable);
        if (mClockView == null) return;
        if (mAutoHide && mScreenOn) {
            autoHideHandler.postDelayed(mHideRunnable, mShowDuration * 1000L);
        } else if (!mAutoHide && !mAutoHideLauncher) {
            mClockView.setVisibility(View.VISIBLE);
        }
    }

    /** Move the clock to the requested position area. */
    @SuppressLint("RtlHardcoded")
    private void placeClock() {
        if (mClockView == null || !mClockStyleOn) return;
        ViewGroup parent = (ViewGroup) mClockView.getParent();
        ViewGroup target = null;
        Integer   index  = null;
        int extraPx = dp2px(mPadding);
        int chipL = mChipOn ? dp2px(mChipPadLeft)   : 0;
        int chipT = mChipOn ? dp2px(mChipPadTop)    : 0;
        int chipR = mChipOn ? dp2px(mChipPadRight)  : 0;
        int chipB = mChipOn ? dp2px(mChipPadBottom) : 0;

        switch (mPosition) {
            case POS_LEFT:
                target = mStartSide;
                index  = 1;
                mClockView.setPadding(mStartPadding + chipL, chipT, mStartPadding + extraPx + chipR, chipB);
                break;
            case POS_CENTER:
                target = (mCenteredArea instanceof ViewGroup) ? (ViewGroup) mCenteredArea : null;
                mClockView.setPadding(mStartPadding + chipL, chipT, mStartPadding + extraPx + chipR, chipB);
                break;
            case POS_RIGHT:
                if (mSystemIconArea != null)
                    target = (ViewGroup) mSystemIconArea.getParent();
                mClockView.setPadding(mStartPadding + chipL, chipT, extraPx + chipR, chipB);
                break;
        }

        if (target != null && parent != null) {
            parent.removeView(mClockView);
            if (index != null) target.addView(mClockView, index);
            else                target.addView(mClockView);
        }

        applyChipMargins();
    }

    /** Applica i margini del chip (indipendenti dal padding interno) come margini di layout
     *  reali sulla view dell'orologio. */
    private void applyChipMargins() {
        if (mClockView == null) return;
        ViewGroup.LayoutParams lp = mClockView.getLayoutParams();
        if (!(lp instanceof ViewGroup.MarginLayoutParams mlp)) return;
        if (mChipOn) {
            mlp.setMargins(dp2px(mChipMarginLeft), dp2px(mChipMarginTop),
                    dp2px(mChipMarginRight), dp2px(mChipMarginBottom));
        } else {
            mlp.setMargins(0, 0, 0, 0);
        }
        mClockView.setLayoutParams(mlp);
    }

    /** Apply font size and trigger layout. */
    @SuppressLint("RtlHardcoded")
    private void setClockSize() {
        if (mClockView == null || !mClockStyleOn) return;
        if (mSize > 12) {
            ViewGroup.LayoutParams p = mClockView.getLayoutParams();
            if (p != null) {
                p.height = ViewGroup.LayoutParams.MATCH_PARENT;
                mClockView.setLayoutParams(p);
            }
            switch (mPosition) {
                case POS_LEFT:
                    mClockView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                    mClockView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                    break;
                case POS_CENTER:
                    mClockView.setGravity(Gravity.CENTER);
                    mClockView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                    break;
                case POS_RIGHT:
                    mClockView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                    mClockView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
                    break;
            }
            mClockView.setIncludeFontPadding(false);
        }
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, mSize);
        mClockView.post(mClockView::requestLayout);
        refreshClock();
    }

    /** Force the clock to redraw. */
    private void refreshClock() {
        if (mClockView == null) return;
        mClockView.post(() -> {
            try {
                Object calendar = getObjectField(mClockView, "mCalendar");
                callMethod(calendar, "setTimeInMillis", System.currentTimeMillis());
                callMethod(mClockView, "updateClock");
            } catch (Throwable ignored) {}
            if (mCustomColor) mClockView.setTextColor(mColor);
            // 2026-09-25: garantisce un passaggio di misura FRESCO con il testo vero appena
            // impostato — la guardia in onMeasure/updateMinWidth sopra salta il calcolo della
            // larghezza quando il testo non è ancora pronto (View appena creata), quindi senza
            // questo requestLayout esplicito quel passaggio corretto potrebbe non arrivare mai.
            mClockView.requestLayout();
        });
    }

    /** Applica/rimuove il chip di sfondo dietro l'orologio (reale OC
     *  status_bar_clock_background_chip): stile pieno/contorno/misto, colore di riempimento
     *  e colore del bordo indipendenti (accento o personalizzati), angoli, margini, padding. */
    private void updateChip() {
        if (mClockView == null) return;
        if (!mChipOn || !mClockStyleOn) {
            mClockView.setBackground(null);
            placeClock();
            return;
        }
        int fillColor   = mChipFillAccent   ? systemAccentColor() : mChipFillColor;
        int strokeColor = mChipStrokeAccent ? systemAccentColor() : mChipStrokeColor;
        int fill = Color.TRANSPARENT;
        int strokeWidth = 0;
        switch (mChipStyle) {
            case 1: // contorno
                strokeWidth = dp2px(mChipStrokeWidth);
                break;
            case 2: // misto
                fill = fillColor;
                strokeWidth = dp2px(mChipStrokeWidth);
                break;
            default: // pieno
                fill = fillColor;
                break;
        }
        float corner = mChipRoundCorners ? dp2px(mChipCorner) : 0f;
        mClockView.setBackground(tintBlockedChip(fill, strokeColor, strokeWidth, corner));
        placeClock();
    }

    private int systemAccentColor() {
        try { return mContext.getColor(android.R.color.system_accent1_600); }
        catch (Throwable t) { return Color.WHITE; }
    }

    /** GradientDrawable che blocca il tint/colorFilter di sistema di OOS, come in
     *  DstDialogStyle — altrimenti OOS sovrascrive il colore scelto dall'utente. */
    private static GradientDrawable tintBlockedChip(int fill, int strokeColor, int strokeWidth,
                                                      float cornerRadius) {
        GradientDrawable d = new GradientDrawable() {
            @Override public void setTint(int tintColor) { /* block OOS tint */ }
            @Override public void setTintList(ColorStateList tint) { /* block OOS tint */ }
            @Override public void setTintMode(PorterDuff.Mode tintMode) { /* block */ }
            @Override public void setColorFilter(ColorFilter cf) { /* block OOS colorFilter */ }
            @Override public void setColorFilter(int color, PorterDuff.Mode mode) { /* block */ }
        };
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(cornerRadius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    private int dp2px(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                mContext.getResources().getDisplayMetrics()));
    }

    private int appAccentColor() {
        if (Xprefs.getBoolean("DST_ACCENT1_on", false)) {
            return Xprefs.getInt("DST_ACCENT1", 0xFF908DFF) | 0xFF000000;
        }
        return 0xFF908DFF;
    }

    @Override
    public boolean listensTo(String packageName) { return SYSTEM_UI.equals(packageName); }
}
