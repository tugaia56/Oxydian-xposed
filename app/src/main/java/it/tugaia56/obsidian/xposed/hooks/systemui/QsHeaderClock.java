package it.tugaia56.obsidian.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getBooleanField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.BuildConfig;
import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.xposed.XposedMods;
import it.tugaia56.obsidian.xposed.utils.ChipStyleHelper;
import it.tugaia56.obsidian.xposed.utils.ViewHelper;

/**
 * Orologio personalizzato Intestazione QS — real hook, terza passata.
 *
 * Stili 0-8 sono un porting reale dei 9 layout OC (preview_header_clock_0..8.xml):
 * 0="Nessuno" (layout vuoto), 1-8 = gli 8 stili reali. Stesso meccanismo a tag di OC
 * (clock/date/text1/text2/accent1/accent2/accent3/textClockDate) via ViewHelper.
 * Stile 6 (avatar) usa @drawable/default_avatar statico — l'hook non applica ancora
 * l'avatar utente reale/personalizzato (feature OC a sé stante).
 *
 * "Modalità Orologio RED" usa il vero meccanismo OC: hook diretto su
 * OplusClockExImpl.setTextWithRedOneStyleInternal/setTextWithRedOneStyle (non più un
 * TextWatcher applicato a posteriori), con le 4 modalità reali (Predefinito/Disabilita/
 * Colore Accento/Colore Personalizzato).
 *
 * NOT ported: editor Background Chip (gradiente/stroke/angoli personalizzati — feature
 * OC a sé stante con ~24 campi per chip) — gli switch sono presenti in UI ma non ancora
 * applicati dall'hook.
 */
public class QsHeaderClock extends XposedMods {

    // ── Custom clock pref keys ─────────────────────────────────────────────────
    public static final String PREF_ENABLED     = "OBS_QS_CLOCK_ENABLED";
    public static final String PREF_STYLE       = "OBS_QS_CLOCK_STYLE";
    /** Unico switch che controlla tutti e 5 i colori (accent1/2/3 + text1/2) — reale OC
     *  (qs_header_clock_custom_color_switch), non due switch separati. */
    public static final String PREF_COLOR_ALL_ON = "OBS_QS_CLOCK_COLOR_ALL_ON";
    public static final String PREF_COLOR       = "OBS_QS_CLOCK_COLOR";       // text1
    public static final String PREF_TEXT2       = "OBS_QS_CLOCK_TEXT2";
    public static final String PREF_ACCENT      = "OBS_QS_CLOCK_ACCENT";      // accent1
    public static final String PREF_ACCENT2     = "OBS_QS_CLOCK_ACCENT2";
    public static final String PREF_ACCENT3     = "OBS_QS_CLOCK_ACCENT3";
    public static final String PREF_SCALE       = "OBS_QS_CLOCK_SCALE";
    public static final String PREF_TOP_MARGIN  = "OBS_QS_CLOCK_TOP_MARGIN";
    public static final String PREF_LEFT_MARGIN = "OBS_QS_CLOCK_LEFT_MARGIN";
    public static final String PREF_FORMAT       = "OBS_QS_CLOCK_FORMAT";
    public static final String PREF_CUSTOM_FONT  = "OBS_QS_CLOCK_CUSTOM_FONT";

    // ── Stock clock pref keys ─────────────────────────────────────────────────
    public static final String PREF_STOCK_COLOR_ON        = "OBS_QS_STOCK_COLOR_ON";
    public static final String PREF_STOCK_COLOR            = "OBS_QS_STOCK_COLOR";
    public static final String PREF_STOCK_HIDE_DATE        = "OBS_QS_STOCK_HIDE_DATE";
    public static final String PREF_STOCK_DATE_COLOR_ON    = "OBS_QS_STOCK_DATE_COLOR_ON";
    public static final String PREF_STOCK_DATE_COLOR       = "OBS_QS_STOCK_DATE_COLOR";
    public static final String PREF_STOCK_HIDE_CARRIER     = "OBS_QS_STOCK_HIDE_CARRIER";
    /** Stesso schema chiavi del chip Barra di stato — vedi ChipStyleHelper/ClockChipStyleFragment. */
    public static final String CLOCK_CHIP_PREFIX           = "qs_header_clock_background_chip";
    public static final String DATE_CHIP_PREFIX            = "qs_header_date_background_chip";
    public static final String PREF_STOCK_CLOCK_CHIP_ON    = CLOCK_CHIP_PREFIX + "_switch";
    public static final String PREF_STOCK_DATE_CHIP_ON     = DATE_CHIP_PREFIX + "_switch";
    /** "0".."3": Predefinito / Disabilita / Colore Accento / Colore Personalizzato — reale OC. */
    public static final String PREF_STOCK_RED_MODE          = "OBS_QS_STOCK_RED_MODE";
    public static final String PREF_STOCK_RED_COLOR         = "OBS_QS_STOCK_RED_COLOR";

    private static final String LAYOUT_PREFIX = "obs_qs_clock_style_";
    /** 0="Nessuno" (layout vuoto) + 1-8 = i reali 8 stili OC. */
    public static final int STYLE_COUNT = 9;

    // ── Runtime prefs — custom clock ─────────────────────────────────────────────
    private boolean mEnabled     = false;
    private int     mStyle       = 0;
    private boolean mColorAllOn  = false;
    private int     mColor      = Color.WHITE;
    private int     mText2      = Color.WHITE;
    private int     mAccent     = Color.WHITE;
    private int     mAccent2    = Color.WHITE;
    private int     mAccent3    = Color.WHITE;
    private boolean mColorOn    = false;
    private boolean mText2On    = false;
    private boolean mAccentOn   = false;
    private boolean mAccent2On  = false;
    private boolean mAccent3On  = false;
    private int     mScalePct   = 75;
    private int     mTopMargin  = 0;
    private int     mLeftMargin = 8;
    private String  mDateFormat = "";
    private boolean mCustomFont = false;

    // ── Runtime prefs — stock clock ──────────────────────────────────────────────
    private boolean mStockColorOn      = false;
    private int     mStockColor        = Color.WHITE;
    private boolean mStockHideDate     = false;
    private boolean mStockDateColorOn  = false;
    private int     mStockDateColor    = Color.WHITE;
    private boolean mStockHideCarrier  = false;
    private int     mRedOneMode        = 0;
    private int     mRedOneColor       = Color.RED;

    // ── Hooked state ──────────────────────────────────────────────────────────
    private Context appContext;
    private final List<LinearLayout> mContainers  = new ArrayList<>();
    private final List<TextView>     mStockClocks = new ArrayList<>();
    private final List<TextView>     mStockDates  = new ArrayList<>();
    private final List<TextView>     mStockCarriers = new ArrayList<>();
    /** Figli di qs_clock_and_date_container (tutti, inclusi quelli non-TextView). */
    private final List<View>         mStockViews  = new ArrayList<>();

    public QsHeaderClock(Context context) {
        super(context);
        try {
            appContext = context.createPackageContext(
                    BuildConfig.APPLICATION_ID,
                    Context.CONTEXT_IGNORE_SECURITY
            );
        } catch (PackageManager.NameNotFoundException ignored) {}
    }

    // ── updatePrefs ────────────────────────────────────────────────────────────

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mEnabled    = Xprefs.getBoolean(PREF_ENABLED, false);
        mStyle      = parseInt(Xprefs.getString(PREF_STYLE, "0"), 0);
        mColorAllOn = Xprefs.getBoolean(PREF_COLOR_ALL_ON, false);
        mColor      = resolveColor(PREF_COLOR, Color.WHITE);
        mText2      = resolveColor(PREF_TEXT2, Color.WHITE);
        mAccent     = resolveColor(PREF_ACCENT, Color.WHITE);
        mAccent2    = resolveColor(PREF_ACCENT2, Color.WHITE);
        mAccent3    = resolveColor(PREF_ACCENT3, Color.WHITE);
        mColorOn    = Xprefs.getBoolean(PREF_COLOR + "_on", false);
        mText2On    = Xprefs.getBoolean(PREF_TEXT2 + "_on", false);
        mAccentOn   = Xprefs.getBoolean(PREF_ACCENT + "_on", false);
        mAccent2On  = Xprefs.getBoolean(PREF_ACCENT2 + "_on", false);
        mAccent3On  = Xprefs.getBoolean(PREF_ACCENT3 + "_on", false);
        mScalePct   = Xprefs.getInt(PREF_SCALE, 75);
        mTopMargin  = Xprefs.getInt(PREF_TOP_MARGIN, 0);
        mLeftMargin = Xprefs.getInt(PREF_LEFT_MARGIN, 8);
        mDateFormat = Xprefs.getString(PREF_FORMAT, "");
        mCustomFont = Xprefs.getBoolean(PREF_CUSTOM_FONT, false);

        mStockColorOn      = Xprefs.getBoolean(PREF_STOCK_COLOR_ON, false);
        mStockColor        = resolveColor(PREF_STOCK_COLOR, Color.WHITE);
        mStockHideDate     = Xprefs.getBoolean(PREF_STOCK_HIDE_DATE, false);
        mStockDateColorOn  = Xprefs.getBoolean(PREF_STOCK_DATE_COLOR_ON, false);
        mStockDateColor    = resolveColor(PREF_STOCK_DATE_COLOR, Color.WHITE);
        mStockHideCarrier  = Xprefs.getBoolean(PREF_STOCK_HIDE_CARRIER, false);
        mRedOneMode        = parseInt(Xprefs.getString(PREF_STOCK_RED_MODE, "0"), 0);
        mRedOneColor       = Xprefs.getInt(PREF_STOCK_RED_COLOR, Color.RED);

        updateClockView();
        applyStockPrefs();
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Throwable t) { return def; }
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

    @Override
    public boolean listensTo(String packageName) {
        return SYSTEM_UI.equals(packageName);
    }

    // ── Hook installation ──────────────────────────────────────────────────────

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        hookFooterImpl(lpparam);
        hookSimpleHeader(lpparam);
        hookPluginController(lpparam);
        hookRedOne(lpparam);
    }

    private void hookFooterImpl(XC_LoadPackage.LoadPackageParam lp) {
        for (String name : new String[]{
                "com.oplus.systemui.qs.OplusQSFooterImpl",
                "com.oplusos.systemui.qs.OplusQSFooterImpl"}) {
            try {
                Class<?> cls = findClass(name, lp.classLoader);
                findAndHookMethod(cls, "onFinishInflate", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            FrameLayout footer = (FrameLayout) param.thisObject;
                            if (footer.findViewWithTag("obs_qs_clock_container") != null) {
                                updateClockView();
                                return;
                            }
                            LinearLayout container = buildContainer();
                            grabStockViewsFromFields(param.thisObject, footer);
                            footer.addView(container, footer.getChildCount());
                            mContainers.add(container);
                            updateClockView();
                            applyStockPrefs();
                        } catch (Throwable t) {
                            XposedBridge.log("[ Obsidian QsHeaderClock ] FooterImpl: " + t);
                        }
                    }
                });
                try {
                    hookAllMethods(cls, "updateResources", new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            updateClockView();
                            applyStockPrefs();
                        }
                    });
                } catch (Throwable ignored) {}
                XposedBridge.log("[ Obsidian QsHeaderClock ] hooked FooterImpl: " + name);
                break;
            } catch (Throwable ignored) {}
        }
    }

    @SuppressLint("DiscouragedApi")
    private void hookSimpleHeader(XC_LoadPackage.LoadPackageParam lp) {
        try {
            Class<?> cls = findClass(
                    "com.oplus.systemui.separate.OplusQSSimpleHeader", lp.classLoader);
            hookAllMethods(cls, "onInit", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        FrameLayout view = (FrameLayout) param.thisObject;
                        int containerId = mContext.getResources().getIdentifier(
                                "button_container_parent", "id", SYSTEM_UI);
                        if (containerId == 0) return;
                        LinearLayout btnParent = view.findViewById(containerId);
                        if (btnParent == null) return;

                        if (btnParent.findViewWithTag("obs_qs_clock_container") != null) {
                            grabStockViewsFromFields(param.thisObject, view);
                            updateClockView();
                            return;
                        }

                        LinearLayout container = buildContainer();
                        grabStockViewsFromFields(param.thisObject, view);

                        btnParent.post(() -> {
                            if (btnParent.findViewWithTag("obs_qs_clock_container") == null) {
                                btnParent.addView(container, 0);
                                mContainers.add(container);
                            }
                            updateClockView();
                            applyStockPrefs();
                        });
                    } catch (Throwable t) {
                        XposedBridge.log("[ Obsidian QsHeaderClock ] SimpleHeader.onInit: " + t);
                    }
                }
            });
            hookAllMethods(cls, "updateTextColor", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (mEnabled) setStockVisible(false);
                    else applyStockPrefs();
                }
            });
            XposedBridge.log("[ Obsidian QsHeaderClock ] hooked OplusQSSimpleHeader");
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian QsHeaderClock ] OplusQSSimpleHeader: " + t.getMessage());
        }
    }

    @SuppressLint("DiscouragedApi")
    private void hookPluginController(XC_LoadPackage.LoadPackageParam lp) {
        // Rinominata da OOS in un update SystemUI: ContainerViewController -> Component
        // (osservato 2026-09-27, ClassNotFoundException sul nome vecchio). Prova entrambi.
        Class<?> cls = tryFindClass(lp,
                "com.oplus.systemui.plugins.qs.quickentrance.OplusQSQuickEntranceComponent",
                "com.oplus.systemui.plugins.qs.quickentrance.OplusQSQuickEntranceContainerViewController");
        if (cls == null) {
            XposedBridge.log("[ Obsidian QsHeaderClock ] OplusQSQuickEntranceComponent/ContainerViewController: not found");
            return;
        }
        try {
            // 2026-09-27: rewrite Kotlin — non esiste più un onInit(View)/campo "view": la
            // classe espone getter diretti (getClockContainer/getClockView/getDateView/
            // getCarrierView). Agganciamo getClockContainer(), che restituisce già il
            // ViewGroup giusto come risultato — niente più findViewById per id/nome.
            hookAllMethods(cls, "getClockContainer", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object result = param.getResult();
                        if (!(result instanceof ViewGroup)) return;
                        ViewGroup clockDateContainer = (ViewGroup) result;

                        if (clockDateContainer.findViewWithTag("obs_qs_clock_container") != null) {
                            updateClockView();
                            return;
                        }

                        for (int i = 0; i < clockDateContainer.getChildCount(); i++) {
                            View child = clockDateContainer.getChildAt(i);
                            if (!mStockViews.contains(child)) mStockViews.add(child);
                        }
                        grabStockViewsFromFields(param.thisObject, clockDateContainer);
                        grabStockViewsFromGetters(param.thisObject);

                        LinearLayout container = buildContainer();
                        clockDateContainer.addView(container, 0);
                        mContainers.add(container);
                        updateClockView();
                        applyStockPrefs();
                    } catch (Throwable t) {
                        XposedBridge.log("[ Obsidian QsHeaderClock ] PluginController.getClockContainer: " + t);
                    }
                }
            });
            hookAllMethods(cls, "updateColor", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (mEnabled) setStockVisible(false);
                    else applyStockPrefs();
                }
            });
            XposedBridge.log("[ Obsidian QsHeaderClock ] hooked " + cls.getSimpleName());
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian QsHeaderClock ] " + cls.getSimpleName() + ": " + t.getMessage());
        }
    }

    /** Getter diretti della nuova OplusQSQuickEntranceComponent — più affidabili dei nomi
     *  di campo indovinati in grabStockViewsFromFields (Kotlin non li espone uguali). */
    private void grabStockViewsFromGetters(Object component) {
        if (mStockClocks.isEmpty()) {
            try {
                Object v = callMethod(component, "getClockView");
                if (v instanceof TextView) mStockClocks.add((TextView) v);
            } catch (Throwable ignored) {}
        }
        if (mStockDates.isEmpty()) {
            try {
                Object v = callMethod(component, "getDateView");
                if (v instanceof TextView) mStockDates.add((TextView) v);
            } catch (Throwable ignored) {}
        }
        if (mStockCarriers.isEmpty()) {
            try {
                Object v = callMethod(component, "getCarrierView");
                if (v instanceof TextView) mStockCarriers.add((TextView) v);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Vero meccanismo OC per "Modalità Orologio RED": hook diretto sul metodo che l'orologio
     * stock usa per colorare la cifra "1" (invece di un TextWatcher applicato a posteriori).
     */
    private void hookRedOne(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> clockExClass = tryFindClass(lp,
                "com.oplus.systemui.common.clock.OplusClockExImpl",
                "com.oplusos.systemui.ext.BaseClockExt");
        if (clockExClass == null) {
            dbg("OplusClockExImpl/BaseClockExt not found — Modalità Orologio RED non applicabile su questa build");
            return;
        }
        try {
            hookAllMethods(clockExClass, "setTextWithRedOneStyleInternal", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try { onRedOneInternal(p); } catch (Throwable t) { dbg("redOneInternal: " + t); }
                }
            });
        } catch (Throwable ignored) {}
        try {
            hookAllMethods(clockExClass, "setTextWithRedOneStyle", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try { onRedOneStyle(p); } catch (Throwable t) { dbg("redOneStyle: " + t); }
                }
            });
        } catch (Throwable ignored) {}
    }

    private void onRedOneInternal(XC_MethodHook.MethodHookParam p) {
        if (p.args.length < 2 || !(p.args[0] instanceof TextView)) return;
        TextView textView = (TextView) p.args[0];
        if (mEnabled || mRedOneMode == 1) {
            p.setResult(null);
            if (mEnabled) {
                textView.setText("");
                textView.setTextColor(Color.TRANSPARENT);
            }
            return;
        }
        if (mRedOneMode == 2 || mRedOneMode == 3) {
            applyRedOneSpan(textView, (CharSequence) p.args[1]);
            p.setResult(null);
        }
        // mode 0 (Predefinito): lascia correre il metodo stock originale, non tocchiamo nulla.
    }

    /** Variante OOS16 (SDK 36+) — richiede mIsDateTimePanel==true e restituisce un boolean. */
    private void onRedOneStyle(XC_MethodHook.MethodHookParam p) {
        if (Build.VERSION.SDK_INT < 36) return;
        boolean isDateTimePanel;
        try { isDateTimePanel = getBooleanField(p.thisObject, "mIsDateTimePanel"); }
        catch (Throwable t) { return; }
        if (!isDateTimePanel) return;
        if (p.args.length < 2 || !(p.args[0] instanceof TextView)) return;
        TextView textView = (TextView) p.args[0];

        if (mEnabled || mRedOneMode == 1) {
            p.setResult(true);
            if (mEnabled) {
                textView.setText("");
                textView.setTextColor(Color.TRANSPARENT);
            }
            return;
        }
        if (mRedOneMode == 2 || mRedOneMode == 3) {
            applyRedOneSpan(textView, (CharSequence) p.args[1]);
            p.setResult(true);
            return;
        }
        p.setResult(false); // mode 0: lascia fare allo stock
    }

    private void applyRedOneSpan(TextView textView, CharSequence charSequence) {
        StringBuilder sb = new StringBuilder(charSequence);
        int length = sb.length();
        for (int i = 0; i < length; i++) {
            if (sb.charAt(i) == ':') { sb.replace(i, i + 1, "‎∶"); break; }
        }

        int systemAccent;
        try { systemAccent = mContext.getColor(android.R.color.system_accent1_600); }
        catch (Throwable t) { systemAccent = Color.RED; }
        int colorToApply = mRedOneMode == 2 ? systemAccent : mRedOneColor;

        SpannableString spannableString = new SpannableString(sb);
        for (int i = 0; i < 2 && i < length; i++) {
            if (sb.charAt(i) == '1') {
                spannableString.setSpan(new ForegroundColorSpan(colorToApply), i, i + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        textView.setText(spannableString, TextView.BufferType.SPANNABLE);
    }

    // ── View helpers ───────────────────────────────────────────────────────────

    private LinearLayout buildContainer() {
        LinearLayout c = new LinearLayout(mContext);
        c.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        c.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        c.setVisibility(View.GONE);
        c.setTag("obs_qs_clock_container");
        return c;
    }

    private void grabStockViewsFromFields(Object obj, ViewGroup root) {
        for (String field : new String[]{"mClockView", "clockView", "mQSClock", "mClock"}) {
            try {
                TextView tv = (TextView) getObjectField(obj, field);
                if (tv != null && !mStockClocks.contains(tv)) { mStockClocks.add(tv); break; }
            } catch (Throwable ignored) {}
        }
        if (mStockClocks.isEmpty()) {
            for (String idName : new String[]{"qs_footer_clock", "qs_date_clock", "oplus_clock"}) {
                try {
                    int id = mContext.getResources().getIdentifier(idName, "id", SYSTEM_UI);
                    if (id == 0) continue;
                    TextView tv = root.findViewById(id);
                    if (tv != null) { mStockClocks.add(tv); break; }
                } catch (Throwable ignored) {}
            }
        }
        for (String field : new String[]{"mQsDateView", "dateView", "mDateView", "mQSDate", "mDate"}) {
            try {
                TextView tv = (TextView) getObjectField(obj, field);
                if (tv != null && !mStockDates.contains(tv)) { mStockDates.add(tv); break; }
            } catch (Throwable ignored) {}
        }
        if (mStockDates.isEmpty()) {
            for (String idName : new String[]{"oplus_date", "qs_date_text", "qs_footer_date"}) {
                try {
                    int id = mContext.getResources().getIdentifier(idName, "id", SYSTEM_UI);
                    if (id == 0) continue;
                    TextView tv = root.findViewById(id);
                    if (tv != null) { mStockDates.add(tv); break; }
                } catch (Throwable ignored) {}
            }
        }
        for (String field : new String[]{"mOplusQSCarrier", "mCarrierText", "carrierText"}) {
            try {
                TextView tv = (TextView) getObjectField(obj, field);
                if (tv != null && !mStockCarriers.contains(tv)) { mStockCarriers.add(tv); break; }
            } catch (Throwable ignored) {}
        }
        if (mStockCarriers.isEmpty()) {
            for (String idName : new String[]{"qs_footer_carrier_text", "qs_carrier_text"}) {
                try {
                    int id = mContext.getResources().getIdentifier(idName, "id", SYSTEM_UI);
                    if (id == 0) continue;
                    TextView tv = root.findViewById(id);
                    if (tv != null) { mStockCarriers.add(tv); break; }
                } catch (Throwable ignored) {}
            }
        }
    }

    // ── Update logic ───────────────────────────────────────────────────────────

    private void updateClockView() {
        if (mContainers.isEmpty()) return;
        for (LinearLayout container : mContainers) {
            container.removeAllViews();
            if (!mEnabled) {
                container.setVisibility(View.GONE);
                setStockVisible(true);
                continue;
            }
            setStockVisible(false);
            View clockView = inflateClockStyle();
            if (clockView == null) {
                container.setVisibility(View.GONE);
                continue;
            }
            applyColors(clockView);
            applyScale(clockView);
            applyFont(clockView);
            applyFormat(clockView);
            applyContainerMargins(container);
            container.addView(clockView);
            container.setVisibility(View.VISIBLE);
        }
    }

    @SuppressLint("DiscouragedApi")
    private View inflateClockStyle() {
        if (appContext == null) return null;
        int style = Math.max(0, Math.min(STYLE_COUNT - 1, mStyle));
        try {
            int resId = appContext.getResources().getIdentifier(
                    LAYOUT_PREFIX + style, "layout", BuildConfig.APPLICATION_ID);
            if (resId == 0) return null;
            return LayoutInflater.from(appContext).inflate(resId, null, false);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian QsHeaderClock ] inflate: " + t);
            return null;
        }
    }

    private void applyColors(View view) {
        boolean night = mContext.getResources().getConfiguration().isNightModeActive();
        int def = night ? Color.WHITE : Color.BLACK;
        int accent1 = (mColorAllOn && mAccentOn)  ? mAccent  : def;
        int accent2 = (mColorAllOn && mAccent2On) ? mAccent2 : def;
        int accent3 = (mColorAllOn && mAccent3On) ? mAccent3 : def;
        int text1   = (mColorAllOn && mColorOn)   ? mColor   : def;
        int text2   = (mColorAllOn && mText2On)   ? mText2   : def;
        ViewHelper.findViewWithTagAndChangeColor(view, "accent1", accent1);
        ViewHelper.findViewWithTagAndChangeColor(view, "accent2", accent2);
        ViewHelper.findViewWithTagAndChangeColor(view, "accent3", accent3);
        ViewHelper.findViewWithTagAndChangeColor(view, "text1", text1);
        ViewHelper.findViewWithTagAndChangeColor(view, "text2", text2);
    }

    private void applyScale(View view) {
        if (mScalePct == 100 || !(view instanceof ViewGroup)) return;
        ViewHelper.applyTextScalingRecursively((ViewGroup) view, mScalePct / 100f);
    }

    private void applyFont(View view) {
        if (!mCustomFont || appContext == null || !(view instanceof ViewGroup)) return;
        try {
            Typeface tf = ResourcesCompat.getFont(appContext, R.font.bebasneue_bold);
            if (tf != null) ViewHelper.applyFontRecursively((ViewGroup) view, tf);
        } catch (Throwable ignored) {}
    }

    private void applyFormat(View view) {
        if (mDateFormat.isEmpty()) return;
        View dateView = ViewHelper.findViewWithTag(view, "textClockDate");
        if (dateView instanceof android.widget.TextClock textClock) {
            try {
                textClock.setFormat12Hour(mDateFormat);
                textClock.setFormat24Hour(mDateFormat);
            } catch (Throwable ignored) {}
        }
    }

    private void applyContainerMargins(LinearLayout container) {
        ViewGroup.LayoutParams lp = container.getLayoutParams();
        if (!(lp instanceof ViewGroup.MarginLayoutParams)) return;
        ((ViewGroup.MarginLayoutParams) lp).setMargins(dp(mLeftMargin), dp(mTopMargin), 0, 0);
        container.setLayoutParams(lp);
    }

    private void setStockVisible(boolean visible) {
        int vis = visible ? View.VISIBLE : View.INVISIBLE;
        for (TextView tv : mStockClocks) if (tv != null) tv.setVisibility(vis);
        for (TextView tv : mStockDates)  if (tv != null) tv.setVisibility(vis);
        for (View v : mStockViews)
            if (v != null && !"obs_qs_clock_container".equals(v.getTag()))
                v.setVisibility(vis);
    }

    /**
     * Applica le preferenze dell'orologio stock: colore orologio, colore data, nascondi data,
     * nascondi operatore. "Modalità Orologio RED" è gestita direttamente dall'hook
     * setTextWithRedOneStyle(Internal) — non da qui.
     */
    private void applyStockPrefs() {
        if (mEnabled) return; // stock nascosto quando il clock custom è attivo

        if (mStockColorOn) {
            for (TextView tv : mStockClocks) if (tv != null) tv.setTextColor(mStockColor);
            for (View v : mStockViews) {
                if (v instanceof TextView && !"obs_qs_clock_container".equals(v.getTag()))
                    ((TextView) v).setTextColor(mStockColor);
            }
        }

        int dateVis = mStockHideDate ? View.GONE : View.VISIBLE;
        for (TextView tv : mStockDates) if (tv != null) tv.setVisibility(dateVis);
        if (!mStockHideDate && mStockDateColorOn) {
            for (TextView tv : mStockDates) if (tv != null) tv.setTextColor(mStockDateColor);
        }

        int carrierVis = mStockHideCarrier ? View.GONE : View.VISIBLE;
        for (TextView tv : mStockCarriers) if (tv != null) tv.setVisibility(carrierVis);

        for (TextView tv : mStockClocks) ChipStyleHelper.apply(tv, mContext, CLOCK_CHIP_PREFIX);
        if (!mStockHideDate) {
            for (TextView tv : mStockDates) ChipStyleHelper.apply(tv, mContext, DATE_CHIP_PREFIX);
        }
    }

    private Class<?> tryFindClass(XC_LoadPackage.LoadPackageParam lp, String... names) {
        for (String name : names) {
            try { return findClass(name, lp.classLoader); } catch (Throwable ignored) {}
        }
        return null;
    }

    private void dbg(String msg) {
        XposedBridge.log("[ Obsidian QsHeaderClock ] " + msg);
    }

    private int dp(int dp) {
        return Math.round(dp * mContext.getResources().getDisplayMetrics().density);
    }
}
