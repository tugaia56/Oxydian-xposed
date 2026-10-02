package it.tugaia56.obsidian.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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
 * Orologio personalizzato AOD — real hook sullo STESSO punto di aggancio di AodWeather
 * (AodClockLayout.initForAodApk/performTimeUpdate). Confermato via log dispositivo: quando
 * il device entra davvero in doze/AOD (non l'anteprima), OplusKeyguardStyleClock.
 * onUiStateChanged (usato da LockscreenClockMod per condividere lo stato con la Schermata di
 * Blocco) non viene MAI chiamato — l'AOD "schermo parziale" di questo OOS16 viene disegnato
 * da un percorso completamente separato (l'"AOD APK"), esattamente quello che AodWeather già
 * usa con successo. Stessi 61 stili/tag della Schermata di Blocco, stesse chiavi prefs AOD
 * già esposte da AodClockFragment (aod_custom_clock_*).
 */
public class AodClockMod extends XposedMods {

    private static final String KEY_SWITCH       = "aod_custom_clock_switch";
    private static final String KEY_STYLE        = "aod_custom_clock_style";
    private static final String KEY_COLOR_SWITCH = "aod_custom_color_switch";
    private static final String KEY_LINE_HEIGHT  = "aod_clock_line_height";
    private static final String KEY_TEXT_SCALING = "aod_text_scaling";
    private static final String KEY_FORMAT       = "aod_clock_custom_format";
    private static final String KEY_CUSTOM_FONT  = "aod_custom_font";

    private static final String COLOR_ACCENT1 = "aod_clock_color_code_accent1";
    private static final String COLOR_TEXT1   = "aod_clock_color_code_text1";

    // Stessi cursori "Margine superiore/inferiore AOD" esposti nella schermata SdB — un solo
    // controllo per l'utente, applicato ad entrambi i punti di aggancio AOD (schermo intero
    // condiviso con SdB, e questo, schermo parziale).
    private static final String KEY_TOP_MARGIN        = "lockscreen_top_margin";
    private static final String KEY_BOTTOM_MARGIN_AOD = "lockscreen_bottom_margin_aod";

    private static final String TAG_MARKER = "obsidian_aod_clock";

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

    /** Obsidian's own package context — needed to inflate our own layouts/fonts/Lottie assets. */
    private Context appContext;

    private ViewGroup mRootLayout;
    private View mInjectedClock;
    private int mInjectedStyle = -1;

    public AodClockMod(Context context) {
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
        mAccent1     = Xprefs.getInt(COLOR_ACCENT1, mAccent1) | 0xFF000000;
        mText1       = Xprefs.getInt(COLOR_TEXT1, mText1) | 0xFF000000;
        mScale       = Xprefs.getInt(KEY_TEXT_SCALING, 100) / 100f;
        mLineHeightDp= Xprefs.getInt(KEY_LINE_HEIGHT, 0);
        mDateFormat  = Xprefs.getString(KEY_FORMAT, "");
        mCustomFont  = Xprefs.getBoolean(KEY_CUSTOM_FONT, false);
        mTopMarginDp    = Xprefs.getInt(KEY_TOP_MARGIN, 0);
        mBottomMarginDp = Xprefs.getInt(KEY_BOTTOM_MARGIN_AOD, 40);
        placeClockView();
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Throwable t) { return def; }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        Class<?> aodClockLayout;
        try {
            aodClockLayout = findClass("com.oplus.systemui.aod.aodclock.off.AodClockLayout", lpparam.classLoader);
        } catch (Throwable t) {
            try {
                aodClockLayout = findClass("com.oplusos.systemui.aod.aodclock.off.AodClockLayout", lpparam.classLoader);
            } catch (Throwable t2) {
                dbg("AodClockLayout not found: " + t2);
                return;
            }
        }

        XposedBridge.hookAllMethods(aodClockLayout, "initForAodApk", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                it.tugaia56.obsidian.xposed.utils.KeyguardUiState.registerAodLayout(param.thisObject);
                try { onInit(param); } catch (Throwable t) { dbg("initForAodApk hook ERROR: " + t); }
            }
        });
        XposedBridge.hookAllMethods(aodClockLayout, "performTimeUpdate", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                it.tugaia56.obsidian.xposed.utils.KeyguardUiState.registerAodLayout(param.thisObject);
                try { if (mEnabled) placeClockView(); } catch (Throwable t) { dbg("performTimeUpdate hook ERROR: " + t); }
            }
        });
        XposedBridge.hookAllConstructors(aodClockLayout, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                it.tugaia56.obsidian.xposed.utils.KeyguardUiState.registerAodLayout(param.thisObject);
            }
        });
        // OOS 16.1: initForAodApk/performTimeUpdate non scattano a ogni AOD; questi sì.
        for (String m : new String[]{"setVisibility", "onAttachedToWindow", "showClock", "hideClock"}) {
            try {
                XposedBridge.hookAllMethods(aodClockLayout, m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        it.tugaia56.obsidian.xposed.utils.KeyguardUiState.registerAodLayout(param.thisObject);
                    }
                });
            } catch (Throwable t) { dbg("hook " + m + " failed: " + t); }
        }
        dbg("hooked AodClockLayout.initForAodApk/performTimeUpdate");
    }

    /** Trova il layout radice dell'AOD APK e nasconde l'orologio stock, esattamente come fa
     *  OC — la struttura interna (mAodViewFromApk -> v -> v2) è la stessa su questa famiglia
     *  di ROM, quindi si riusa la stessa navigazione invece di re-inventarla. */
    private void onInit(XC_MethodHook.MethodHookParam param) {
        if (!mEnabled) return;
        FrameLayout aodViewFromApk = (FrameLayout) getObjectField(param.thisObject, "mAodViewFromApk");
        mRootLayout = null;
        for (int i = 0; i < aodViewFromApk.getChildCount(); i++) {
            if (aodViewFromApk.getChildAt(i) instanceof ViewGroup v) {
                for (int j = 0; j < v.getChildCount(); j++) {
                    mRootLayout = v;
                    if (v.getChildAt(j) instanceof ViewGroup v2) {
                        for (int k = 0; k < v2.getChildCount(); k++) {
                            v.getChildAt(k).setVisibility(View.GONE);
                        }
                        break;
                    }
                }
            }
        }
        placeClockView();
    }

    private void placeClockView() {
        if (!mEnabled || mRootLayout == null) return;
        try {
            View existing = mRootLayout.findViewWithTag(TAG_MARKER);
            if (existing != null) {
                if (mInjectedClock == existing && mInjectedStyle == mStyle) {
                    applyStyling(existing);
                    applyMargins(existing);
                    return;
                }
                mRootLayout.removeView(existing);
            }
            mInjectedClock = null;
            mInjectedStyle = -1;

            View clockView = buildClockView();
            if (clockView == null) return;
            mInjectedClock = clockView;
            mInjectedStyle = mStyle;
            mRootLayout.addView(clockView, 0);
            applyMargins(clockView);
        } catch (Throwable t) {
            dbg("placeClockView ERROR: " + t);
        }
    }

    /** Va applicato DOPO che la view è agganciata a mRootLayout (addView) — appena
     *  inflazionata non ha ancora LayoutParams validi, vedi LockscreenClockMod. */
    private void applyMargins(View clockView) {
        ViewHelper.setMargins(clockView, mContext, 0, mTopMarginDp, 0, mBottomMarginDp);
    }

    /** Stesso schema di LockscreenClockMod: risolve "lockscreen_clock_&lt;stile&gt;" per nome,
     *  cosi i 61 stili sono condivisi tra Schermata di Blocco e AOD senza duplicare i layout. */
    private View buildClockView() {
        if (appContext == null) {
            dbg("appContext is null — cannot inflate our own layout");
            return null;
        }
        try {
            int layoutId = appContext.getResources().getIdentifier(
                    "lockscreen_clock_" + mStyle, "layout", appContext.getPackageName());
            if (layoutId == 0) {
                dbg("layout lockscreen_clock_" + mStyle + " not found");
                return null;
            }
            View view = LayoutInflater.from(appContext).inflate(layoutId, null);
            view.setTag(TAG_MARKER);
            applyStyling(view);
            return view;
        } catch (Throwable t) {
            dbg("inflate lockscreen_clock_" + mStyle + " failed: " + t);
            return null;
        }
    }

    private void applyStyling(View clockView) {
        if (!(clockView instanceof ViewGroup group)) return;

        int systemAccent;
        try { systemAccent = mContext.getColor(android.R.color.system_accent1_600); }
        catch (Throwable t) { systemAccent = 0xFF908DFF; }

        int accentColor = mCustomColor ? mAccent1 : systemAccent;
        int textColor = mCustomColor ? mText1 : 0xFFFFFFFF;
        ViewHelper.findViewWithTagAndChangeColor(clockView, "accent1", accentColor);
        ViewHelper.findViewWithTagAndChangeColor(clockView, "accent2", accentColor);
        ViewHelper.findViewWithTagAndChangeColor(clockView, "accent3", accentColor);
        ViewHelper.findViewWithTagAndChangeColor(clockView, "text1", textColor);

        if (mScale != 1f) ViewHelper.applyTextScalingRecursively(group, mScale);
        if (mLineHeightDp != 0) ViewHelper.applyTextMarginRecursively(mContext, group, mLineHeightDp);

        if (mCustomFont && appContext != null) {
            try {
                Typeface tf = ResourcesCompat.getFont(appContext, R.font.bebasneue_bold);
                if (tf != null) ViewHelper.applyFontRecursively(group, tf);
            } catch (Throwable ignored) {}
        }

        if (!TextUtils.isEmpty(mDateFormat)) {
            View dateView = ViewHelper.findViewWithTag(clockView, "textClockDate");
            if (dateView instanceof TextClock textClock) {
                try {
                    textClock.setFormat12Hour(mDateFormat);
                    textClock.setFormat24Hour(mDateFormat);
                } catch (Throwable ignored) {}
            }
        }

        injectLottie(clockView);
    }

    /** Costruita in Java, non dichiarata in XML — LayoutInflater non risolve
     *  com.airbnb.lottie.LottieAnimationView cross-processo via Xposed (classloader
     *  mismatch), stesso motivo/soluzione di LockscreenClockMod. */
    private void injectLottie(View clockView) {
        if (appContext == null) return;
        View slot = ViewHelper.findViewWithTag(clockView, "lottie");
        if (!(slot instanceof ViewGroup lottieSlot)) return;
        if (lottieSlot.getChildCount() > 0) return;
        int animRes = appContext.getResources().getIdentifier(
                "lottie_clock_style_" + mStyle, "raw", appContext.getPackageName());
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

    private static void dbg(String msg) {
        XposedBridge.log("[ Obsidian ] AodClockMod: " + msg);
    }

    @Override public boolean listensTo(String packageName) { return SYSTEM_UI.equals(packageName); }
}
