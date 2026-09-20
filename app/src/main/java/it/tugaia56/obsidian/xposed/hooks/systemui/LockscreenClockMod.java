package it.tugaia56.obsidian.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextClock;

import androidx.core.content.res.ResourcesCompat;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.BuildConfig;
import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.xposed.XposedMods;
import it.tugaia56.obsidian.xposed.utils.ViewHelper;

/**
 * Orologio personalizzato Schermata di Blocco + AOD — real hook. 61 stili portati da OC
 * (res/layout/lockscreen_clock_0.xml .. lockscreen_clock_60.xml, indice 0-based — "Stile
 * orologio N" nella UI = indice N-1). Il layout viene risolto per nome ("lockscreen_clock_" +
 * stile) invece di un R.layout.* fisso, così aggiungere/rimuovere stili è solo questione di
 * file XML, senza toccare questa classe.
 *
 * Injection point mirrors OC's LockscreenClock: OplusKeyguardStyleBaseClock.getView() /
 * .setTime() on OOS16 (SDK 35+) — the exact class names are OC's own reverse-engineering,
 * reused here since Obsidian targets the same ROM family. Confirmed working on-device for
 * the lockscreen case.
 *
 * AOD support: OC treats lockscreen+AOD as the SAME injected view instance, which reacts to
 * OplusKeyguardStyleClock.onUiStateChanged(int state) (SHADE=1/LS=2/AOD=3) to swap its own
 * styling rather than being re-created. We do the same here: getView(1) injects/styles the
 * view using whichever prefs (Lockscreen vs AOD) match the currently-tracked UI state, and
 * onUiStateChanged re-applies styling in place when the state flips. If the UI-state class
 * can't be found on this build, we degrade gracefully to lockscreen-only behaviour (the
 * already-confirmed-working path) rather than risk breaking it. Switching style mid-session
 * (pref change, or LS/AOD using different styles) discards the old view and rebuilds — see
 * mInjectedStyle.
 *
 * NOT ported in this first pass (deliberately, to keep the first working version small):
 *  - clock-height recalculation hooks (KeyguardStyleClockControllerImpl / AodClockLayout /
 *    AodData) — some styles may visually clip/overlap until these are added;
 *  - custom user/device text and custom image pickers — few of the 61 layouts have views
 *    tagged for those, so there's nothing to apply them to yet on most styles.
 */
public class LockscreenClockMod extends XposedMods {

    private static final int UI_STATE_SHADE = 1;
    private static final int UI_STATE_LS    = 2;
    private static final int UI_STATE_AOD   = 3;

    private static final String KEY_SWITCH       = "lockscreen_custom_clock_switch";
    private static final String KEY_STYLE        = "lockscreen_custom_clock_style";
    private static final String KEY_COLOR_SWITCH = "lockscreen_custom_color_switch";
    private static final String KEY_LINE_HEIGHT  = "lockscreen_clock_line_height";
    private static final String KEY_TEXT_SCALING = "lockscreen_text_scaling";
    private static final String KEY_FORMAT       = "lockscreen_clock_custom_format";
    private static final String KEY_CUSTOM_FONT  = "lockscreen_custom_font";

    private static final String KEY_TOP_MARGIN         = "lockscreen_top_margin";
    private static final String KEY_BOTTOM_MARGIN      = "lockscreen_bottom_margin";
    private static final String KEY_BOTTOM_MARGIN_AOD  = "lockscreen_bottom_margin_aod";

    private static final String COLOR_ACCENT1 = "lockscreen_clock_color_code_accent1";
    private static final String COLOR_TEXT1   = "lockscreen_clock_color_code_text1";

    private static final String AOD_KEY_SWITCH       = "aod_custom_clock_switch";
    private static final String AOD_KEY_STYLE        = "aod_custom_clock_style";
    private static final String AOD_KEY_COLOR_SWITCH = "aod_custom_color_switch";
    private static final String AOD_KEY_LINE_HEIGHT  = "aod_clock_line_height";
    private static final String AOD_KEY_TEXT_SCALING = "aod_text_scaling";
    private static final String AOD_KEY_FORMAT       = "aod_clock_custom_format";
    private static final String AOD_KEY_CUSTOM_FONT  = "aod_custom_font";

    private static final String AOD_COLOR_ACCENT1 = "aod_clock_color_code_accent1";
    private static final String AOD_COLOR_TEXT1   = "aod_clock_color_code_text1";

    private static final String TAG_MARKER = "obsidian_lockscreen_clock";

    private boolean mEnabled;
    private int mStyle;
    private boolean mCustomColor;
    private int mAccent1 = 0xFF908DFF, mText1 = 0xFFFFFFFF;
    private float mScale = 1f;
    private int mLineHeightDp;
    private String mDateFormat = "";
    private boolean mCustomFont;
    private int mTopMarginDp;
    private int mBottomMarginDp = 40;
    private int mBottomMarginAodDp = 40;

    private boolean mAodEnabled;
    private int mAodStyle;
    private boolean mAodCustomColor;
    private int mAodAccent1 = 0xFF908DFF, mAodText1 = 0xFFFFFFFF;
    private float mAodScale = 1f;
    private int mAodLineHeightDp;
    private String mAodDateFormat = "";
    private boolean mAodCustomFont;

    /** Tracked via onUiStateChanged; defaults to LS since that's the confirmed-working path
     *  and getView(1) is typically first invoked for the lockscreen state. */
    private int mUiState = UI_STATE_LS;

    /** Obsidian's own package context — needed to inflate our own layouts/fonts/Lottie assets
     *  (mContext is SystemUI's context and can't resolve our resource IDs). */
    private Context appContext;

    public LockscreenClockMod(Context context) {
        super(context);
        try {
            appContext = context.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY);
        } catch (PackageManager.NameNotFoundException ignored) {}
    }

    @Override
    public void updatePrefs(String... key) {
        if (Xprefs == null) return;
        mEnabled     = Xprefs.getBoolean(KEY_SWITCH, false);
        mStyle       = parseInt(Xprefs.getString(KEY_STYLE, "0"), 0);
        mCustomColor = Xprefs.getBoolean(KEY_COLOR_SWITCH, false);
        mAccent1     = resolveColor(COLOR_ACCENT1, mAccent1) | 0xFF000000;
        mText1       = resolveColor(COLOR_TEXT1, mText1) | 0xFF000000;
        mScale       = Xprefs.getInt(KEY_TEXT_SCALING, 100) / 100f;
        mLineHeightDp= Xprefs.getInt(KEY_LINE_HEIGHT, 0);
        mDateFormat  = Xprefs.getString(KEY_FORMAT, "");
        mCustomFont  = Xprefs.getBoolean(KEY_CUSTOM_FONT, false);
        mTopMarginDp        = Xprefs.getInt(KEY_TOP_MARGIN, 0);
        mBottomMarginDp     = Xprefs.getInt(KEY_BOTTOM_MARGIN, 40);
        mBottomMarginAodDp  = Xprefs.getInt(KEY_BOTTOM_MARGIN_AOD, 40);

        mAodEnabled     = Xprefs.getBoolean(AOD_KEY_SWITCH, false);
        mAodStyle       = parseInt(Xprefs.getString(AOD_KEY_STYLE, "0"), 0);
        mAodCustomColor = Xprefs.getBoolean(AOD_KEY_COLOR_SWITCH, false);
        mAodAccent1     = resolveColor(AOD_COLOR_ACCENT1, mAodAccent1) | 0xFF000000;
        mAodText1       = resolveColor(AOD_COLOR_TEXT1, mAodText1) | 0xFF000000;
        mAodScale       = Xprefs.getInt(AOD_KEY_TEXT_SCALING, 100) / 100f;
        mAodLineHeightDp= Xprefs.getInt(AOD_KEY_LINE_HEIGHT, 0);
        mAodDateFormat  = Xprefs.getString(AOD_KEY_FORMAT, "");
        mAodCustomFont  = Xprefs.getBoolean(AOD_KEY_CUSTOM_FONT, false);

        refreshForCurrentState();
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Throwable t) { return def; }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!SYSTEM_UI.equals(lp.packageName)) return;
        if (Build.VERSION.SDK_INT < 35) {
            dbg("SDK " + Build.VERSION.SDK_INT + " < 35, this hook only targets OOS16-style clock plugin — skipped");
            return;
        }

        Class<?> baseClockClass = tryFindClass(lp,
                "com.oplus.keyguard.OplusKeyguardStyleBaseClock",
                "com.oplus.keyguard.comm.OplusKeyguardStyleWrapper");
        if (baseClockClass == null) {
            dbg("OplusKeyguardStyleBaseClock/Wrapper not found — clock injection unavailable on this build");
            return;
        }

        hookAllMethods(baseClockClass, "getView", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try { onGetView(p); } catch (Throwable t) { dbg("getView hook failed: " + t); }
            }
        });
        // No setTime hook needed: the injected TextClock views tick themselves via Android's
        // own TIME_TICK-driven ticker, same as any other TextClock on screen.

        Class<?> uiStateClass = tryFindClass(lp, "com.oplus.keyguard.OplusKeyguardStyleClock");
        if (uiStateClass == null) {
            dbg("OplusKeyguardStyleClock not found — AOD/lockscreen state tracking unavailable, defaulting to lockscreen-only behaviour");
        } else {
            hookAllMethods(uiStateClass, "onUiStateChanged", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try { onUiStateChanged(p); } catch (Throwable t) { dbg("onUiStateChanged hook failed: " + t); }
                }
            });
        }
    }

    private void onUiStateChanged(XC_MethodHook.MethodHookParam p) {
        if (p.args.length == 0 || !(p.args[0] instanceof Integer)) return;
        mUiState = (Integer) p.args[0];
        refreshForCurrentState();
    }

    // ── View injection ──────────────────────────────────────────────────────────

    private View mInjectedClock;
    private int mInjectedStyle = -1;
    private ViewGroup mContainer;

    /** Calibrazioni manuali per stile (2026-09-18) — con stili orologio più alti del previsto i
     *  widget di LockscreenWidgetsMod/LockscreenWeather finivano sopra al calendario (bug reale,
     *  confermato live). Leggere l'altezza vera a runtime si è rivelato instabile (cambia durante
     *  l'animazione d'ingresso dell'orologio, causando ai widget di scivolare fuori schermo) —
     *  niente calcolo dinamico, solo valori fissi testati a occhio uno stile alla volta, come già
     *  fatto altrove nel progetto (PILL_ASPECT, HANDLER_BORDER_SCALE, ecc.). Chiave = indice
     *  0-based dello stile ("Stile orologio N" in UI = index N-1); valore = margine superiore
     *  totale in dp (SOSTITUISCE base+slider utente per gli stili qui presenti, non si somma).
     *  Stili non presenti: fallback al comportamento esistente (150dp fisso + slider utente). */
    private static final java.util.Map<Integer, Integer> STYLE_WIDGET_MARGIN_DP = new java.util.HashMap<>();
    static {
        // Stile 7: la riga widget sta più in basso del meteo (come nel caso generale, dove la
        // differenza di base è 100dp: meteo -200, widget -100) — stesso valore del meteo (260)
        // li faceva finire alla stessa altezza, sbagliato (segnalato dall'utente 2026-09-18).
        STYLE_WIDGET_MARGIN_DP.put(6, 360); // Stile 7 — 260 (meteo) + 100 di differenza standard
    }
    /** Stessa idea, ma per il widget Meteo (LockscreenWeather) — può stare più vicino
     *  all'orologio del resto dei widget (LockscreenWidgetsMod), quindi tabella separata. Vuota
     *  finché l'utente non testa/conferma valori specifici. */
    private static final java.util.Map<Integer, Integer> STYLE_WEATHER_MARGIN_DP = new java.util.HashMap<>();
    static {
        STYLE_WEATHER_MARGIN_DP.put(6, 260); // Stile 7 — 150 base + 110 di slider confermati dall'utente
    }

    /** Null se non calibrato per questo stile — i chiamanti ricadono sul loro comportamento
     *  esistente (base fissa + slider utente) in quel caso. */
    public static Integer getCalibratedWidgetMarginDp(int style) {
        return STYLE_WIDGET_MARGIN_DP.get(style);
    }

    public static Integer getCalibratedWeatherMarginDp(int style) {
        return STYLE_WEATHER_MARGIN_DP.get(style);
    }

    public static int getCurrentStyle() {
        return parseInt(Xprefs.getString(KEY_STYLE, "0"), 0);
    }

    private boolean isEnabledForState(int state) {
        return state == UI_STATE_AOD ? mAodEnabled : mEnabled;
    }

    private int styleForState(int state) {
        return state == UI_STATE_AOD ? mAodStyle : mStyle;
    }

    private void onGetView(XC_MethodHook.MethodHookParam p) {
        if (p.args.length == 0 || !(p.args[0] instanceof Integer)) return;
        int viewType = (Integer) p.args[0];
        if (viewType != 1) return; // 1 == lockscreen/AOD clock plugin slot, per OC's reverse engineering

        if (!(p.getResult() instanceof ViewGroup container)) return;
        mContainer = container;
        applyForState(container, mUiState);
    }

    /** Re-applies visibility/styling for the given UI state onto the last-known container,
     *  without waiting for a fresh getView() call — mirrors OC's LockscreenView, where the
     *  SAME injected view reacts to state changes instead of being torn down/rebuilt. */
    private void refreshForCurrentState() {
        if (mContainer != null) applyForState(mContainer, mUiState);
    }

    private void applyForState(ViewGroup container, int state) {
        boolean enabled = isEnabledForState(state);
        int style = styleForState(state);

        // Hide whatever stock views are already there when our clock is enabled — restore
        // them when disabled, matching OC's "BY_OC" tag bookkeeping. Must also skip siblings
        // injected by other Obsidian hooks sharing this same container (e.g. LockscreenWeather's
        // "obsidian_lockscreen_weather" widget) — otherwise they get mistaken for stock views
        // and hidden.
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            Object tag = child.getTag();
            if (TAG_MARKER.equals(tag) || "obsidian_lockscreen_weather".equals(tag)) continue;
            if (enabled) {
                if (child.getTag() == null) child.setTag("obsidian_stock_" + i);
                child.setVisibility(View.GONE);
            } else if (child.getTag() != null && child.getTag().toString().startsWith("obsidian_stock_")) {
                child.setVisibility(View.VISIBLE);
            }
        }

        if (!enabled) {
            if (mInjectedClock != null) {
                ViewGroup parent = (ViewGroup) mInjectedClock.getParent();
                if (parent != null) parent.removeView(mInjectedClock);
                mInjectedClock = null;
                mInjectedStyle = -1;
            }
            return;
        }

        View existing = ViewHelper.findViewWithTag(container, TAG_MARKER);
        if (existing != null) {
            if (mInjectedStyle == style) {
                // Stesso stile già iniettato (es. si rientra nello stesso stato) — ri-applica
                // solo lo styling, dato che le impostazioni SdB/AOD possono differire.
                applyStyling(existing, state);
                applyMargins(existing, state);
                return;
            }
            // Lo stile è cambiato — la vecchia gerarchia di view non è più quella giusta,
            // va ricostruita da zero con il layout nuovo.
            ViewGroup parent = (ViewGroup) existing.getParent();
            if (parent != null) parent.removeView(existing);
            mInjectedClock = null;
            mInjectedStyle = -1;
        }

        // 2026-09-20: il plugin orologio puo' consegnarci un container NUOVO a ogni getView() (a
        // raffica durante sblocco/schermo acceso). Prima ri-gonfiavamo e aggiungevamo un orologio
        // nuovo ogni volta, senza mai rimuovere il precedente: centinaia di TextClock agganciati,
        // ognuno con i suoi receiver (TIME_TICK/TIME_SET/...) -> "Too many receivers, total of
        // 1000" in SystemUI, crash a ripetizione e ANR "NotificationShade is not responding" allo
        // sblocco. Ora riusiamo l'orologio gia' costruito, spostandolo nel nuovo container.
        if (mInjectedClock != null && mInjectedStyle == style) {
            ViewGroup oldParent = (ViewGroup) mInjectedClock.getParent();
            if (oldParent != container) {
                if (oldParent != null) oldParent.removeView(mInjectedClock);
                container.addView(mInjectedClock);
                applyStyling(mInjectedClock, state);
                applyMargins(mInjectedClock, state);
            }
            return;
        }
        if (mInjectedClock != null) {
            ViewGroup oldParent = (ViewGroup) mInjectedClock.getParent();
            if (oldParent != null) oldParent.removeView(mInjectedClock);
            mInjectedClock = null;
            mInjectedStyle = -1;
        }

        View clockView = buildClockView(state);
        if (clockView == null) return;

        mInjectedClock = clockView;
        mInjectedStyle = style;
        container.addView(clockView);
        applyMargins(clockView, state);
        dbg("Stile " + (style + 1) + " injected into container=" + container.getClass().getSimpleName() + " state=" + state);
    }

    /** Margine superiore/inferiore impostabili dall'utente — vanno applicati DOPO che la view
     *  è agganciata al container (container.addView), non prima: appena inflazionata non ha
     *  ancora LayoutParams validi (getLayoutParams() torna null finché non è attaccata a un
     *  parent), e ViewHelper.setMargins su LayoutParams null causerebbe un crash. */
    private void applyMargins(View clockView, int state) {
        boolean isAod = state == UI_STATE_AOD;
        int bottom = isAod ? mBottomMarginAodDp : mBottomMarginDp;
        ViewHelper.setMargins(clockView, mContext, 0, mTopMarginDp, 0, bottom);
    }

    /** Risolve "lockscreen_clock_&lt;stile&gt;" per nome invece di un R.layout.* fisso — con
     *  61 stili non è pratico avere una costante per ognuno, e i nuovi si aggiungono solo
     *  copiando il layout XML, senza toccare questo codice. */
    private View buildClockView(int state) {
        if (appContext == null) {
            dbg("appContext is null — cannot inflate our own layout");
            return null;
        }
        int style = styleForState(state);
        try {
            int layoutId = appContext.getResources().getIdentifier(
                    "lockscreen_clock_" + style, "layout", appContext.getPackageName());
            if (layoutId == 0) {
                dbg("layout lockscreen_clock_" + style + " not found");
                return null;
            }
            LayoutInflater inflater = LayoutInflater.from(appContext);
            View view = inflater.inflate(layoutId, null);
            view.setTag(TAG_MARKER);
            applyStyling(view, state);
            return view;
        } catch (Throwable t) {
            dbg("inflate lockscreen_clock_" + style + " failed: " + t);
            return null;
        }
    }

    private void applyStyling(View clockView, int state) {
        if (!(clockView instanceof ViewGroup group)) return;

        boolean isAod = state == UI_STATE_AOD;
        boolean customColor  = isAod ? mAodCustomColor  : mCustomColor;
        int     accent1      = isAod ? mAodAccent1      : mAccent1;
        int     text1        = isAod ? mAodText1        : mText1;
        float   scale        = isAod ? mAodScale         : mScale;
        int     lineHeightDp = isAod ? mAodLineHeightDp : mLineHeightDp;
        boolean customFont   = isAod ? mAodCustomFont   : mCustomFont;
        String  dateFormat   = isAod ? mAodDateFormat   : mDateFormat;

        int systemAccent;
        try { systemAccent = mContext.getColor(android.R.color.system_accent1_600); }
        catch (Throwable t) { systemAccent = 0xFF908DFF; }

        int accentColor = customColor ? accent1 : systemAccent;
        int textColor = customColor ? text1 : 0xFFFFFFFF;
        ViewHelper.findViewWithTagAndChangeColor(clockView, "accent1", accentColor);
        // Alcuni stili (es. 61) hanno un secondo/terzo elemento decorativo taggato
        // accent2/accent3 (OC li ricolora sempre, non solo accent1) — senza questo
        // restano nel colore di default del drawable invece che nell'accento del tema.
        ViewHelper.findViewWithTagAndChangeColor(clockView, "accent2", accentColor);
        ViewHelper.findViewWithTagAndChangeColor(clockView, "accent3", accentColor);
        ViewHelper.findViewWithTagAndChangeColor(clockView, "text1", textColor);

        if (scale != 1f) ViewHelper.applyTextScalingRecursively(group, scale);
        if (lineHeightDp != 0) ViewHelper.applyTextMarginRecursively(mContext, group, lineHeightDp);

        if (customFont && appContext != null) {
            try {
                Typeface tf = ResourcesCompat.getFont(appContext, R.font.bebasneue_bold);
                if (tf != null) ViewHelper.applyFontRecursively(group, tf);
            } catch (Throwable ignored) {}
        }

        if (!TextUtils.isEmpty(dateFormat)) {
            View dateView = ViewHelper.findViewWithTag(clockView, "textClockDate");
            if (dateView instanceof TextClock textClock) {
                try {
                    textClock.setFormat12Hour(dateFormat);
                    textClock.setFormat24Hour(dateFormat);
                } catch (Throwable ignored) {}
            }
        }

        injectLottie(clockView, state);
    }

    /** Built directly in Java, not declared in the XML — LayoutInflater can't resolve
     *  com.airbnb.lottie.LottieAnimationView from XML cross-process via Xposed (classloader
     *  mismatch, confirmed via device log), so it's constructed here instead, same as OC does.
     *  L'animazione è specifica per stile ("lottie_clock_style_&lt;stile&gt;", solo alcuni
     *  stili ce l'hanno) — nessun raw trovato per questo stile è normale, non un errore. */
    private void injectLottie(View clockView, int state) {
        if (appContext == null) return;
        View slot = ViewHelper.findViewWithTag(clockView, "lottie");
        if (!(slot instanceof ViewGroup lottieSlot)) return;
        if (lottieSlot.getChildCount() > 0) return; // already injected — avoid duplicating on re-style
        int style = styleForState(state);
        int animRes = appContext.getResources().getIdentifier(
                "lottie_clock_style_" + style, "raw", appContext.getPackageName());
        if (animRes == 0) return;
        try {
            com.airbnb.lottie.LottieAnimationView lottie = new com.airbnb.lottie.LottieAnimationView(appContext);
            lottie.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            lottie.setAnimation(animRes);
            lottie.setRepeatCount(com.airbnb.lottie.LottieDrawable.INFINITE);
            lottie.playAnimation();
            lottieSlot.addView(lottie);
        } catch (Throwable t) {
            dbg("Lottie injection failed: " + t);
        }
    }

    private Class<?> tryFindClass(XC_LoadPackage.LoadPackageParam lp, String... names) {
        for (String name : names) {
            try { return findClass(name, lp.classLoader); } catch (Throwable ignored) {}
        }
        return null;
    }

    private static void dbg(String msg) {
        Log.d("ObsidianLockscreenClock", msg);
        XposedBridge.log("[ Obsidian ] LockscreenClockMod: " + msg);
    }

    private int resolveColor(String key, int def) {
        if (Xprefs.getBoolean(key + "_use_accent", false)) return appAccentColor();
        return Xprefs.getInt(key, def);
    }

    private int appAccentColor() {
        if (Xprefs.getBoolean("DST_ACCENT1_on", false)) {
            return Xprefs.getInt("DST_ACCENT1", 0xFF908DFF) | 0xFF000000;
        }
        return 0xFF908DFF;
    }

    @Override public boolean listensTo(String packageName) { return SYSTEM_UI.equals(packageName); }
}
