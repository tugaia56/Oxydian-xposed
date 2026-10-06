package it.tugaia56.obsidian.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.view.View;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * "Separati" QS — porting reale (solo la parte che funziona su SDK36/OOS16) di OC's
 * SeparateQsCustomization: nascondi pulsante modifica/menù (OplusQSQuickEntranceComponent,
 * nome OOS16) + larghezza personalizzata della zona che apre le Impostazioni Rapide invece
 * delle notifiche in un tocco/swipe verso il basso (OplusPanelViewPagerController.isRightArea,
 * stabile su OOS15/16 — nessun cambio di nome tra SDK35 e 36, confermato in OC da due hook
 * indipendenti che lo usano). L'editor a griglia dei riquadri personalizzati di OC (righe,
 * posizione celle, widget aggiuntivi) resta fuori — OC stesso lo disabilita (mCustomLayout
 * forzato a false) su ogni SDK diverso da 35, stesso vicolo cieco già trovato per
 * QSTiles/QsTilesMod: nessun riferimento funzionante da portare per SDK36/OOS16.
 */
public class QsSeparateMod extends XposedMods {

    // Chiavi pref — la UI (QsTilesCustomizeFragment, sezione "Impostazioni Rapide Separati")
    // usa le stesse stringhe letterali; prima del 2026-08-20 erano lette da QsSeparateModsFragment
    // (schermata dedicata ora eliminata, opzioni portate dentro Personalizza Riquadri).
    private static final String PREF_HIDE_EDIT        = "OBS_QS_SEPARATE_HIDE_EDIT";
    private static final String PREF_HIDE_MENU        = "OBS_QS_SEPARATE_HIDE_MENU";
    private static final String PREF_HIDE_SETTINGS    = "OBS_QS_SEPARATE_HIDE_SETTINGS";
    private static final String PREF_CUSTOM_WIDTH_ON  = "OBS_QS_SEPARATE_WIDTH_ON";
    private static final String PREF_CUSTOM_WIDTH_VAL = "OBS_QS_SEPARATE_WIDTH_VALUE";
    /** Master della sezione (switch+tap-nome in UI, 2026-08-20) — default true per non
     *  disabilitare in silenzio le opzioni già configurate da chi aggiorna. */
    private static final String PREF_MASTER_ON        = "OBS_QS_SEPARATE_MASTER_ON";

    // Sfondo dei 3 pulsanti (Modifica/Menù/Impostazioni) — stesso schema Accento/Personalizzato
    // delle altre sezioni (2026-08-22). Default accent=true (nessuna differenza visiva finché
    // non si passa a Personalizzato).
    public static final String PREF_EDIT_BG_ACCENT     = "OBS_QS_SEPARATE_EDIT_BG_ACCENT";
    public static final String PREF_EDIT_BG_COLOR      = "OBS_QS_SEPARATE_EDIT_BG_COLOR";
    public static final String PREF_MENU_BG_ACCENT     = "OBS_QS_SEPARATE_MENU_BG_ACCENT";
    public static final String PREF_MENU_BG_COLOR      = "OBS_QS_SEPARATE_MENU_BG_COLOR";
    public static final String PREF_SETTINGS_BG_ACCENT = "OBS_QS_SEPARATE_SETTINGS_BG_ACCENT";
    public static final String PREF_SETTINGS_BG_COLOR  = "OBS_QS_SEPARATE_SETTINGS_BG_COLOR";
    /** Switch individuale per pulsante (2026-08-22) — permette di attivare/disattivare lo
     *  sfondo colorato di ognuno indipendentemente, anche a sezione master attiva. Default
     *  true: col master acceso ci si aspetta tutti e 3 colorati finché non se ne spegne uno. */
    public static final String PREF_EDIT_BG_ON         = "OBS_QS_SEPARATE_EDIT_BG_ON";
    public static final String PREF_MENU_BG_ON         = "OBS_QS_SEPARATE_MENU_BG_ON";
    public static final String PREF_SETTINGS_BG_ON     = "OBS_QS_SEPARATE_SETTINGS_BG_ON";
    /** Master della sotto-sezione "Colore sfondo pulsanti" (switch+tap-nome in UI) — default
     *  false: è una funzione nuova, a differenza degli altri master di questo file non deve
     *  attivarsi in silenzio per chi aggiorna. Spento → sfondo nativo, nessun colore forzato. */
    public static final String PREF_BTN_BG_ON           = "OBS_QS_SEPARATE_BTN_BG_ON";

    private boolean mHideEdit;
    private boolean mHideMenu;
    private boolean mHideSettings;
    private boolean mCustomWidthOn;
    private float   mCustomWidthFraction = 0.5f;
    private boolean mBtnBgOn;
    private boolean mEditBgOn = true, mMenuBgOn = true, mSettingsBgOn = true;
    private boolean mEditBgAccent = true, mMenuBgAccent = true, mSettingsBgAccent = true;
    private int mEditBgColor = 0xFFFFFFFF, mMenuBgColor = 0xFFFFFFFF, mSettingsBgColor = 0xFFFFFFFF;

    private View mEditButton;
    private View mMenuButton;
    /** Icone dei pulsanti Menù/Impostazioni — è la View passata a MixColorTileDrawable.Builder
     *  (vedi handleLoadPackage), non il contenitore: mMenuButton/il FrameLayout di Impostazioni
     *  non hanno un proprio drawable di sfondo. */
    private View mMenuIconView;
    private View mSettingsIconView;

    /** Drawable (istanza) → "edit"/"menu"/"settings", popolata da MixColorTileDrawable$Builder.build
     *  in base a quale delle 3 View sopra ha ricevuto quel drawable — vedi commento sotto sul
     *  perché setBackgroundTintList da solo non ha alcun effetto su questo drawable. */
    private final Map<Object, String> mButtonDrawableOwner = new WeakHashMap<>();

    public QsSeparateMod(Context context) { super(context); }

    @Override
    public void updatePrefs(String... key) {
        if (Xprefs == null) return;
        mHideEdit = Xprefs.getBoolean(PREF_HIDE_EDIT, false);
        mHideMenu = Xprefs.getBoolean(PREF_HIDE_MENU, false);
        mHideSettings = Xprefs.getBoolean(PREF_HIDE_SETTINGS, false);
        mCustomWidthOn = Xprefs.getBoolean(PREF_CUSTOM_WIDTH_ON, false);
        mCustomWidthFraction = Xprefs.getInt(PREF_CUSTOM_WIDTH_VAL, 50) / 100f;
        mBtnBgOn = Xprefs.getBoolean(PREF_BTN_BG_ON, false);
        mEditBgOn = Xprefs.getBoolean(PREF_EDIT_BG_ON, true);
        mEditBgAccent = Xprefs.getBoolean(PREF_EDIT_BG_ACCENT, true);
        mEditBgColor = Xprefs.getInt(PREF_EDIT_BG_COLOR, 0xFFFFFFFF);
        mMenuBgOn = Xprefs.getBoolean(PREF_MENU_BG_ON, true);
        mMenuBgAccent = Xprefs.getBoolean(PREF_MENU_BG_ACCENT, true);
        mMenuBgColor = Xprefs.getInt(PREF_MENU_BG_COLOR, 0xFFFFFFFF);
        mSettingsBgOn = Xprefs.getBoolean(PREF_SETTINGS_BG_ON, true);
        mSettingsBgAccent = Xprefs.getBoolean(PREF_SETTINGS_BG_ACCENT, true);
        mSettingsBgColor = Xprefs.getInt(PREF_SETTINGS_BG_COLOR, 0xFFFFFFFF);
        if (!Xprefs.getBoolean(PREF_MASTER_ON, true)) {
            mHideEdit = false;
            mHideMenu = false;
            mHideSettings = false;
            mCustomWidthOn = false;
        }
        setupButtons();
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!SYSTEM_UI.equals(lp.packageName)) return;

        // ── Nascondi modifica/menù + cattura icone per lo sfondo colorato ───────
        // Il pulsante "Impostazioni" (settingsButtonIcon) vive in questa STESSA classe, non nel
        // footer QS come inizialmente ipotizzato — R.id.quicksettings_settings_button_icon,
        // trovato in OplusQSQuickEntranceComponent.java decompilato (hook su
        // OplusQSFooterViewController.onInit non scattava mai, confermato via logcat).
        Class<?> quickEntrance = tryFindClass(lp,
                "com.oplus.systemui.plugins.qs.quickentrance.OplusQSQuickEntranceComponent",
                "com.oplus.systemui.plugins.qs.quickentrance.OplusQSQuickEntranceContainerViewController");
        dbg("DIAG quickEntrance class = " + (quickEntrance != null ? quickEntrance.getName() : "NULL"));
        if (quickEntrance != null) {
            hookAllMethods(quickEntrance, "onInit", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        mEditButton = (View) getObjectField(p.thisObject, "editBtn");
                        mMenuButton = (View) getObjectField(p.thisObject, "moreBtn");
                        mMenuIconView = (View) getObjectField(p.thisObject, "moreBtnIcon");
                        mSettingsIconView = (View) getObjectField(p.thisObject, "settingsButtonIcon");
                        setupButtons();
                    } catch (Throwable t) { dbg("editBtn/moreBtn capture failed: " + t); }
                }
            });
        } else {
            dbg("OplusQSQuickEntranceComponent/ContainerViewController non trovata — nascondi modifica/menù non disponibile");
        }

        // ── Sfondo reale dei 3 pulsanti — via MixColorTileDrawable ──────────────
        // setBackgroundTintList NON ha alcun effetto su questo drawable (confermato via log:
        // bg dei 3 pulsanti è com.oplus.systemui.qs.base.res.drawable.MixColorTileDrawable, tint
        // applicato ma invisibile) — dipinge da sé un maskColor letto da una mappa
        // DrawableState->Triple(BlurMixConfig, Integer maskColor, StrokeParamsTemplate), bypassando
        // il meccanismo standard di tinting. Stesso identico drawable/meccanismo già risolto per
        // lo sfondo dei riquadri QS "in evidenza" (QsTilesCustomizeMod.hookTileBgHighlight,
        // project_qs_tile_bg_color.md) — qui riprodotto per le 3 View dei pulsanti: si marca quale
        // drawable appartiene a quale pulsante al momento della creazione (Builder.build(View,...)),
        // poi si riscrive il maskColor in onStateChange prima che lo consumi.
        Class<?> mixColorCls = tryFindClass(lp, "com.oplus.systemui.qs.base.res.drawable.MixColorTileDrawable");
        Class<?> mixBuilderCls = tryFindClass(lp, "com.oplus.systemui.qs.base.res.drawable.MixColorTileDrawable$Builder");
        Class<?> tripleCls = tryFindClass(lp, "kotlin.Triple");
        dbg("DIAG mixColorCls=" + (mixColorCls != null) + " mixBuilderCls=" + (mixBuilderCls != null) + " tripleCls=" + (tripleCls != null));
        if (mixColorCls != null && mixBuilderCls != null && tripleCls != null) {
            try {
                Constructor<?> tripleCtor = tripleCls.getConstructor(Object.class, Object.class, Object.class);
                hookAllMethods(mixBuilderCls, "build", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        Object view = p.args.length > 0 ? p.args[0] : null;
                        Object result = p.getResult();
                        if (view == null || result == null) return;
                        if (view == mEditButton) mButtonDrawableOwner.put(result, "edit");
                        else if (view == mMenuIconView) mButtonDrawableOwner.put(result, "menu");
                        else if (view == mSettingsIconView) mButtonDrawableOwner.put(result, "settings");
                    }
                });
                hookAllMethods(mixColorCls, "onStateChange", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (!mBtnBgOn) return;
                        String owner = mButtonDrawableOwner.get(p.thisObject);
                        if (owner == null) return;
                        boolean on; boolean accent; int custom;
                        switch (owner) {
                            case "edit":     on = mEditBgOn;     accent = mEditBgAccent;     custom = mEditBgColor;     break;
                            case "menu":     on = mMenuBgOn;     accent = mMenuBgAccent;     custom = mMenuBgColor;     break;
                            default:         on = mSettingsBgOn; accent = mSettingsBgAccent; custom = mSettingsBgColor; break;
                        }
                        if (!on) return;
                        int color = accent ? appAccentColor() : custom;
                        try {
                            Object stateListConfig = getObjectField(p.thisObject, "stateListConfig");
                            if (!(stateListConfig instanceof Map)) return;
                            @SuppressWarnings("unchecked")
                            Map<Object, Object> map = (Map<Object, Object>) stateListConfig;
                            for (Map.Entry<Object, Object> e : map.entrySet()) {
                                Object triple = e.getValue();
                                Object first = callMethod(triple, "getFirst");
                                Object third = callMethod(triple, "getThird");
                                e.setValue(tripleCtor.newInstance(first, color, third));
                            }
                        } catch (Throwable t) { dbg("button MixColorTileDrawable override failed: " + t); }
                    }
                });
            } catch (Throwable t) { dbg("MixColorTileDrawable hook install failed: " + t); }
        } else {
            dbg("MixColorTileDrawable/Builder/Triple non trovate — colore sfondo pulsanti non disponibile");
        }

        // ── Larghezza zona tendina QS ────────────────────────────────────────────
        Class<?> panelViewPagerController = tryFindClass(lp,
                "com.oplus.systemui.separate.OplusPanelViewPagerController");
        dbg("DIAG panelViewPagerController class = " + (panelViewPagerController != null ? panelViewPagerController.getName() : "NULL"));
        if (panelViewPagerController != null) {
            hookAllMethods(panelViewPagerController, "isRightArea", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!mCustomWidthOn || p.args.length == 0) return;
                    try {
                        float x = (float) p.args[0];
                        Object centralSurfaces = getObjectField(p.thisObject, "centralSurfaces");
                        float displayWidth = centralSurfaces != null
                                ? (float) callMethod(centralSurfaces, "getDisplayWidth") : 1080f;
                        p.setResult(x >= displayWidth * (1f - mCustomWidthFraction));
                    } catch (Throwable t) { dbg("isRightArea override failed: " + t); }
                }
            });
        } else {
            dbg("OplusPanelViewPagerController non trovata — larghezza zona tendina non disponibile");
        }
    }

    private void setupButtons() {
        if (mEditButton != null) {
            mEditButton.setVisibility(mHideEdit ? View.GONE : View.VISIBLE);
        }
        if (mMenuButton != null) {
            mMenuButton.setVisibility(mHideMenu ? View.GONE : View.VISIBLE);
        }
        if (mSettingsIconView != null) {
            // si nasconde il contenitore (quicksettings_settings_button) e non solo l'icona, cosi'
            // matita e menu si avvicinano invece di lasciare un buco al centro
            View target = mSettingsIconView;
            try {
                if (mSettingsIconView.getParent() instanceof View p && p.getId() != View.NO_ID
                        && "quicksettings_settings_button".equals(
                                p.getResources().getResourceEntryName(p.getId()))) target = p;
            } catch (Throwable ignored) {}
            target.setVisibility(mHideSettings ? View.GONE : View.VISIBLE);
        }
        // Il colore si riapplica da sé al prossimo onStateChange (il drawable è già marcato in
        // mButtonDrawableOwner) — non serve invalidare qui: succede naturalmente ogni volta che
        // la tendina QS viene aperta/animata.
    }

    /** Legge l'accento via Xprefs (XSharedPreferences) invece di ObsidianTheme/ObsidianPrefs —
     *  quest'ultima richiede il Context dell'app Obsidian (Obsidian.get()...), disponibile solo
     *  nel processo Obsidian, non in SystemUI: usarla da un Mod causa NoClassDefFoundError a
     *  ogni chiamata. Stesso pattern di LockscreenWidgetsMod/VolumePanelMod/QsWidgetsMod/ecc. */
    private int appAccentColor() {
        if (Xprefs.getBoolean("DST_ACCENT1_on", false)) {
            return Xprefs.getInt("DST_ACCENT1", 0xFF908DFF) | 0xFF000000;
        }
        return 0xFF908DFF;
    }

    private Class<?> tryFindClass(XC_LoadPackage.LoadPackageParam lp, String... names) {
        for (String name : names) {
            try { return findClass(name, lp.classLoader); } catch (Throwable ignored) {}
        }
        return null;
    }

    private static void dbg(String msg) {
        XposedBridge.log("[ Obsidian ] QsSeparateMod: " + msg);
    }

    @Override public boolean listensTo(String packageName) { return SYSTEM_UI.equals(packageName); }
}
