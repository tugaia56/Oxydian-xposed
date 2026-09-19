package it.tugaia56.obsidian.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedBridge.hookAllConstructors;
import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.SystemClock;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.BuildConfig;
import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.weather.WeatherCache;
import it.tugaia56.obsidian.weather.WeatherIconPacks;
import it.tugaia56.obsidian.weather.WeatherInfo;
import it.tugaia56.obsidian.xposed.XposedMods;
import it.tugaia56.obsidian.utils.WeatherBgFactory;
import it.tugaia56.obsidian.xposed.utils.ActivityLauncherUtils;
import it.tugaia56.obsidian.xposed.views.DeviceStatGaugeView;

/**
 * Aggiungi Widget (Schermata di Blocco) — porting fedele di OC (LockscreenWidgets +
 * LockscreenWidgetsView): widget grandi = ExtendedFloatingActionButton (icona + testo,
 * stesso componente Material che usa OC tramite la sua classe ExtendedFAB), widget mini =
 * semplice ImageView circolare. Tipi collegati alle sole API pubbliche Android (avvio app,
 * torcia, suoneria, media) o al singolo aggancio reflection ben noto (l'ActivityStarter di
 * SystemUI). Wi-Fi/Dati Mobili/Bluetooth/Hotspot/Controlli Casa richiederebbero "rubare" via
 * reflection controller interni di SystemUI specifici per OEM/versione OOS (non ancora
 * mappati per OOS16) — restano nascosti (invisibili, non un'icona rotta) finché non vengono
 * aggiunti in un secondo giro.
 *
 * Stesso punto di aggancio di LockscreenClockMod/LockscreenWeather
 * (OplusKeyguardStyleBaseClock.getView / OplusKeyguardStyleClock.onUiStateChanged) — solo
 * per lo stato Schermata di Blocco (i widget non compaiono in AOD, come in OC).
 */
public class LockscreenWidgetsMod extends XposedMods {

    private static final int UI_STATE_LS = 2;

    private static final String KEY_ENABLED    = "lockscreen_widgets_enabled";
    private static final String KEY_TOP_MARGIN = "lockscreen_widgets_top_margin";
    private static final String[] SLOT_KEYS = {
            "main_custom_widgets1", "main_custom_widgets2",
            "custom_widgets1", "custom_widgets2", "custom_widgets3", "custom_widgets4",
    };
    /** Solo i primi 2 slot (main_custom_widgets1/2) sono "grandi" — il resto è "mini". */
    private static final int MAIN_SLOT_COUNT = 2;
    /** OC: kg_widget_circle_size = 68dp, scalato ×0.85 per SDK 35+ (kgwidgets_dimens.xml /
     *  LockscreenWidgetsView) — qui il ramo SDK<35 è già escluso a monte (handleLoadPackage),
     *  quindi il fattore è sempre applicato: 68×0.85 ≈ 58dp. */
    private static final int MINI_SIZE_DP = 58;
    /** OC: kg_widget_main_height = 88dp, kg_widget_main_width = 175dp (usata solo quando c'è
     *  un solo widget grande attivo — con 2 si passa a peso 1, vedi rebuildWidgetRows).
     *  Altezza ridotta rispetto al valore OC (88dp era troppo) — portata quasi al livello dei
     *  mini widget (58dp) su richiesta esplicita, invece di inseguire il valore di OC. */
    private static final int MAIN_HEIGHT_DP = 60;
    private static final int MAIN_WIDTH_DP = 175;
    /** OC: kg_widgets_main_margin_start/end = kg_widgets_margin_horizontal = 12dp. */
    private static final int WIDGET_MARGIN_DP = 12;
    /** OC: kg_widget_margin_bottom = 6dp — spazio tra la riga grande e quella mini quando
     *  sono entrambe visibili. */
    private static final int ROW_GAP_DP = 6;
    private static final String KEY_SCALE = "widget_scale"; // 50-100 %

    private static final String KEY_COLOR_SWITCH = "lockscreen_widgets_custom_color";
    /** "Sfondo Personalizzato" — stesse 9 varianti di "Sfondo Meteo" (WeatherBgFactory,
     *  R.array.lockscreen_weather_bg_entries), applicate qui a grandi e mini invece che al
     *  widget Meteo. "0" = nessuno (colore normale via Colori Personalizzati). */
    private static final String KEY_BG_SWITCH    = "lockscreen_widgets_custom_bg";
    private static final String KEY_BG_SELECTION = "lockscreen_widgets_bg_selection";
    private static final String KEY_BIG_ACTIVE          = "lockscreen_widgets_big_active";
    private static final String KEY_BIG_INACTIVE        = "lockscreen_widgets_big_inactive";
    private static final String KEY_BIG_ICON_ACTIVE     = "lockscreen_widgets_big_icon_active";
    private static final String KEY_BIG_ICON_INACTIVE   = "lockscreen_widgets_big_icon_inactive";
    private static final String KEY_SMALL_ACTIVE        = "lockscreen_widgets_small_active";
    private static final String KEY_SMALL_INACTIVE      = "lockscreen_widgets_small_inactive";
    private static final String KEY_SMALL_ICON_ACTIVE   = "lockscreen_widgets_small_icon_active";
    private static final String KEY_SMALL_ICON_INACTIVE = "lockscreen_widgets_small_icon_inactive";
    /** Indice "App Personalizzata" nell'array lockscreen_widget_entries — il pacchetto scelto
     *  vive in "<chiave_slot>_app_package" (vedi LockscreenWidgetsFragment). */
    private static final int TYPE_CUSTOM_APP = 1;

    private static final String TAG_MARKER = "obsidian_lockscreen_widgets";
    /** Stesso pref condiviso della sezione Meteo (LockscreenWeather/AodWeather). */
    private static final String KEY_WEATHER_ICON_PACK = "weather_icon_pack";

    // ── Widget Dispositivo (batteria/RAM/volume/temperatura) ───────────────────────────────
    private static final String KEY_DEVICE_ENABLED      = "lockscreen_device_widget";
    private static final String KEY_DEVICE_STYLE        = "device_widget_style"; // "0"=circolare "1"=lineare
    private static final String KEY_DEVICE_CUSTOM_COLOR = "device_widget_custom_color";
    private static final String KEY_DEVICE_COLOR_CIRCULAR = "device_widget_color_circular";
    private static final String KEY_DEVICE_COLOR_LINEAR   = "device_widget_color_linear";
    private static final String KEY_DEVICE_TEXT_COLOR   = "device_widget_text_color";
    private static final String KEY_DEVICE_NAME         = "device_widget_name";
    /** 6 slot indipendenti come i widget principali — ognuno "0"=Nessuno o 1..4
     *  (Batteria/RAM/Volume/Temperatura). */
    private static final String[] KEY_DEVICE_TYPES = {
            "device_widget_type1", "device_widget_type2", "device_widget_type3",
            "device_widget_type4", "device_widget_type5", "device_widget_type6",
    };

    private boolean mEnabled;
    private final int[] mSlotType = new int[SLOT_KEYS.length];
    private final String[] mSlotApp = new String[SLOT_KEYS.length];
    private int mTopMargin;

    private String mWeatherIconPack = WeatherIconPacks.DEFAULT;

    private boolean mCustomColor;
    private boolean mCustomBg;
    private int mBgSelection;
    private int mBigActive, mBigInactive, mBigIconActive, mBigIconInactive;
    private int mSmallActive, mSmallInactive, mSmallIconActive, mSmallIconInactive;
    private int mScalePercent = 100;

    private boolean mDeviceEnabled;
    private int mDeviceStyle; // 0=circolare 1=lineare
    private boolean mDeviceCustomColor;
    private int mDeviceColorCircular, mDeviceColorLinear, mDeviceTextColor;
    private String mDeviceName = "";
    private final int[] mDeviceTypes = new int[KEY_DEVICE_TYPES.length];

    private Context appContext;
    private Context mMaterialContext;
    private ViewGroup mContainer;
    /** Contenitore esterno (verticale) taggato — dentro ci sono il blocco Widget Dispositivo
     *  (nome + 2 gauge) e la riga dei pulsanti "Aggiungi Widget", uno sopra l'altro. */
    private LinearLayout mWidgetRow;
    private LinearLayout mDeviceBlock;
    private TextView mDeviceNameView;
    private LinearLayout mDeviceRow;
    /** Come OC (LockscreenWidgetsView): due righe indipendenti, non una sola — quella dei
     *  widget grandi (max 2, ExtendedFAB) sopra, quella dei mini (max 4, cerchi icona) sotto.
     *  Ognuna nascosta (GONE) per conto proprio se non ha slot attivi, così con soli mini
     *  widget attivi non resta uno spazio vuoto dove sarebbe stata la riga grande. */
    private LinearLayout mMainRow;
    private LinearLayout mMiniRow;
    private boolean mDeviceRowDirty = true;
    /** getView(1) spara in continuazione (decine di volte al secondo, ad ogni frame
     *  dell'animazione dell'orologio) — ricostruire i widget ad ogni giro li distrugge prima
     *  che il render thread riesca mai a comporne un frame: risultato, invisibili nonostante
     *  risultino "visible"/dimensionati correttamente nei log. Vanno ricostruiti solo quando
     *  cambia davvero qualcosa (nuova riga, prefs, stato torcia/suoneria/meteo). */
    private boolean mRowDirty = true;
    private Object mActivityStarter;
    private ActivityLauncherUtils mLauncher;

    private CameraManager mCameraManager;
    private String mTorchCameraId;
    private boolean mTorchOn;
    private AudioManager mAudioManager;
    private WifiManager mWifiManager;
    private BluetoothAdapter mBluetoothAdapter;
    /** Il riquadro QS "Controlli Casa" stesso (OplusDeviceControlsTile/DeviceControlsTile) — qui
     *  non serve un controller separato, si richiama direttamente il suo handleClick(Expandable),
     *  che apre l'Activity giusta da solo. Resta null (widget nascosto) se il riquadro non esiste
     *  su questa build — es. gate regionale FeatureOption.isExpRegion() lato OOS. */
    private Object mDeviceControlsTile;

    public LockscreenWidgetsMod(Context context) {
        super(context);
        try {
            appContext = context.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY);
            mMaterialContext = new ContextThemeWrapper(appContext, R.style.Theme_Obsidian);
        } catch (PackageManager.NameNotFoundException ignored) {}
    }

    @Override
    public void updatePrefs(String... key) {
        if (Xprefs == null) return;
        mEnabled = Xprefs.getBoolean(KEY_ENABLED, false);
        for (int i = 0; i < SLOT_KEYS.length; i++) {
            mSlotType[i] = parseInt(Xprefs.getString(SLOT_KEYS[i], "0"), 0);
            mSlotApp[i] = Xprefs.getString(SLOT_KEYS[i] + "_app_package", "");
        }
        mTopMargin = Xprefs.getInt(KEY_TOP_MARGIN, 0);
        mWeatherIconPack = Xprefs.getString(KEY_WEATHER_ICON_PACK, WeatherIconPacks.DEFAULT);

        mCustomColor = Xprefs.getBoolean(KEY_COLOR_SWITCH, false);
        mCustomBg = Xprefs.getBoolean(KEY_BG_SWITCH, false);
        mBgSelection = parseInt(Xprefs.getString(KEY_BG_SELECTION, "0"), 0);
        mBigActive          = colorIfOn(KEY_BIG_ACTIVE, 0xFF908DFF);
        mBigInactive        = colorIfOn(KEY_BIG_INACTIVE, 0x40FFFFFF);
        mBigIconActive      = colorIfOn(KEY_BIG_ICON_ACTIVE, 0xFFFFFFFF);
        mBigIconInactive    = colorIfOn(KEY_BIG_ICON_INACTIVE, 0xFFFFFFFF);
        mSmallActive        = colorIfOn(KEY_SMALL_ACTIVE, 0xFF908DFF);
        mSmallInactive      = colorIfOn(KEY_SMALL_INACTIVE, 0x40FFFFFF);
        mSmallIconActive    = colorIfOn(KEY_SMALL_ICON_ACTIVE, 0xFFFFFFFF);
        mSmallIconInactive  = colorIfOn(KEY_SMALL_ICON_INACTIVE, 0xFFFFFFFF);
        mScalePercent = Xprefs.getInt(KEY_SCALE, 100);

        mDeviceEnabled = Xprefs.getBoolean(KEY_DEVICE_ENABLED, false);
        mDeviceStyle = parseInt(Xprefs.getString(KEY_DEVICE_STYLE, "0"), 0);
        mDeviceCustomColor = Xprefs.getBoolean(KEY_DEVICE_CUSTOM_COLOR, false);
        mDeviceColorCircular = colorIfOn(KEY_DEVICE_COLOR_CIRCULAR, 0xFF908DFF);
        mDeviceColorLinear   = colorIfOn(KEY_DEVICE_COLOR_LINEAR, 0xFF908DFF);
        mDeviceTextColor     = colorIfOn(KEY_DEVICE_TEXT_COLOR, 0xFFFFFFFF);
        mDeviceName = Xprefs.getString(KEY_DEVICE_NAME, "");
        for (int i = 0; i < KEY_DEVICE_TYPES.length; i++) {
            // Default: primi due slot Batteria/RAM (comportamento di prima), il resto Nessuno.
            int def = i == 0 ? 1 : i == 1 ? 2 : 0;
            mDeviceTypes[i] = parseInt(Xprefs.getString(KEY_DEVICE_TYPES[i], String.valueOf(def)), def);
        }

        mRowDirty = true;
        mDeviceRowDirty = true;
        refreshForCurrentState();
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Throwable t) { return def; }
    }

    /** Ogni swatch di "Colori Personalizzati" ha il suo switch attivo/inattivo (come i colori
     *  DST) — se l'utente lo spegne va usato il default, non l'ultimo colore salvato. */
    /** Shared by every DarkShadow-swatch colour in this Mod — updating this one helper wires up
     *  Accento/Personalizzato for all of them at once. */
    private int colorIfOn(String key, int def) {
        if (!Xprefs.getBoolean(key + "_on", false)) return def;
        if (Xprefs.getBoolean(key + "_use_accent", false)) return appAccentColor();
        return Xprefs.getInt(key, def);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!SYSTEM_UI.equals(lp.packageName)) return;
        if (Build.VERSION.SDK_INT < 35) {
            dbg("SDK " + Build.VERSION.SDK_INT + " < 35, skipped");
            return;
        }

        try {
            mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
            for (String id : mCameraManager.getCameraIdList()) {
                Boolean hasFlash = mCameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(hasFlash)) { mTorchCameraId = id; break; }
            }
        } catch (Throwable t) {
            dbg("camera init failed: " + t);
        }
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        try {
            mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        } catch (Throwable t) { dbg("WifiManager init failed: " + t); }
        try {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        } catch (Throwable t) { dbg("BluetoothAdapter init failed: " + t); }

        // Controlli Casa — a differenza di Wi-Fi/Bluetooth non esiste un servizio di sistema
        // pubblico equivalente: l'unico modo è agganciare l'istanza già viva del vero riquadro
        // QS nativo (costruita da SystemUI stessa quando quel riquadro esiste). Se l'utente non
        // ha quel riquadro tra le Impostazioni Rapide (o su questa build/regione non esiste
        // affatto), l'oggetto non viene mai costruito e il widget resta semplicemente nascosto.
        // Dati Mobili/Hotspot (stessa tecnica, via CellularTile/OplusHotspotTile) tolti: anche
        // con un ascoltatore live (provato per Hotspot, HotspotController.addCallback) lo stato
        // mostrato non seguiva in modo affidabile i cambi fatti dal riquadro QS nativo —
        // giudicati inutili così com'erano.
        Class<?> deviceControlsTile = tryFindClass(lp,
                "com.oplus.systemui.qs.tiles.OplusDeviceControlsTile", "com.android.systemui.qs.tiles.DeviceControlsTile");
        if (deviceControlsTile != null) {
            hookAllConstructors(deviceControlsTile, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    mDeviceControlsTile = p.thisObject;
                    mRowDirty = true;
                    refreshForCurrentState();
                }
            });
        } else {
            dbg("DeviceControlsTile non trovata — widget Controlli Casa non disponibile");
        }

        // L'ActivityStarter reale di SystemUI — un oggetto interno non pubblico, l'unico modo
        // di avviare app "dismissando" la keyguard correttamente. Senza, i widget-app non
        // partono (vedi ActivityLauncherUtils).
        try {
            Class<?> interactor = findClass(
                    "com.android.systemui.keyguard.domain.interactor.KeyguardQuickAffordanceInteractor",
                    lp.classLoader);
            hookAllConstructors(interactor, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        mActivityStarter = getObjectField(p.thisObject, "activityStarter");
                        mLauncher = new ActivityLauncherUtils(mContext, mActivityStarter);
                        refreshForCurrentState();
                    } catch (Throwable t) { dbg("activityStarter capture failed: " + t); }
                }
            });
        } catch (Throwable t) {
            dbg("KeyguardQuickAffordanceInteractor not found: " + t);
        }

        Class<?> baseClockClass = tryFindClass(lp,
                "com.oplus.keyguard.OplusKeyguardStyleBaseClock",
                "com.oplus.keyguard.comm.OplusKeyguardStyleWrapper");
        if (baseClockClass == null) {
            dbg("OplusKeyguardStyleBaseClock/Wrapper not found — widget injection unavailable");
            return;
        }
        hookAllMethods(baseClockClass, "getView", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try { onGetView(p); } catch (Throwable t) { dbg("getView hook failed: " + t); }
            }
        });

        Class<?> uiStateClass = tryFindClass(lp, "com.oplus.keyguard.OplusKeyguardStyleClock");
        if (uiStateClass != null) {
            hookAllMethods(uiStateClass, "onUiStateChanged", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.args.length == 0 || !(p.args[0] instanceof Integer)) return;
                    mUiState = (Integer) p.args[0];
                    refreshForCurrentState();
                }
            });
        }
    }

    private int mUiState = UI_STATE_LS;

    private void onGetView(XC_MethodHook.MethodHookParam p) {
        if (p.args.length == 0 || !(p.args[0] instanceof Integer)) return;
        if ((Integer) p.args[0] != 1) return;
        if (!(p.getResult() instanceof ViewGroup container)) return;
        mContainer = container;
        applyForState();
    }

    private void refreshForCurrentState() {
        if (mContainer != null) applyForState();
    }

    /** Solo Schermata di Blocco — niente widget in AOD, come in OC. Contenitore esterno
     *  verticale: Widget Dispositivo (nome + 2 gauge) sopra, riga pulsanti "Aggiungi Widget"
     *  sotto — ognuno mostrato solo se il proprio switch è acceso. */
    private void applyForState() {
        if (mContainer == null) return;
        boolean show = (mEnabled || mDeviceEnabled) && mUiState == UI_STATE_LS;

        View existing = it.tugaia56.obsidian.xposed.utils.ViewHelper.findViewWithTag(mContainer, TAG_MARKER);
        if (!show) {
            if (existing instanceof ViewGroup existingRow) {
                ViewGroup parent = (ViewGroup) existingRow.getParent();
                if (parent != null) parent.removeView(existingRow);
            }
            mWidgetRow = null;
            mDeviceBlock = null;
            mMainRow = null;
            mMiniRow = null;
            return;
        }

        if (existing instanceof LinearLayout existingRow) {
            mWidgetRow = existingRow;
        } else {
            // ClockContainer (OEM) rimette a GONE, nel proprio onLayout(), qualunque figlio
            // oltre il secondo "extra" che non riconosce (il primo, quello del Meteo, resta
            // intoccato) — un setVisibility() esplicito ad ogni giro non basta perché viene
            // sovrascritto DOPO, nel passaggio di layout del contenitore stesso. Unico modo
            // affidabile: intercettare la chiamata e ignorarla.
            mWidgetRow = new LinearLayout(mContext) {
                @Override public void setVisibility(int visibility) {
                    super.setVisibility(View.VISIBLE);
                }
            };
            mWidgetRow.setTag(TAG_MARKER);
            mWidgetRow.setOrientation(LinearLayout.VERTICAL);
            mWidgetRow.setGravity(Gravity.CENTER_HORIZONTAL);
            // Il vecchio mWidgetRow non è stato ritrovato via tag — l'OEM lo ha rimosso/
            // sostituito (non solo nascosto). mDeviceBlock/mMainRow/mMiniRow, se già
            // costruiti, sono rimasti agganciati a QUELLA vecchia istanza ormai orfana:
            // vanno ricreati e riaggiunti al nuovo mWidgetRow, altrimenti restano
            // "esistenti" (non null) ma invisibili per sempre — è esattamente la sparizione
            // dopo qualche secondo.
            mDeviceBlock = null;
            mMainRow = null;
            mMiniRow = null;
            mRowDirty = true;
            mDeviceRowDirty = true;
            mContainer.addView(mWidgetRow);
            // Come OC (LockscreenWidgets.placeLockscreenWidgets): bringToFront() subito, poi di
            // nuovo dopo il layout — altrimenti il contenitore OEM può ridisegnare sopra.
            mWidgetRow.bringToFront();
            final ViewGroup containerRef = mContainer;
            mWidgetRow.post(() -> {
                mWidgetRow.bringToFront();
                containerRef.invalidate();
                containerRef.requestLayout();
            });
        }
        mWidgetRow.bringToFront();

        if (mDeviceEnabled) {
            if (mDeviceBlock == null) {
                mDeviceBlock = buildDeviceBlock();
                mWidgetRow.addView(mDeviceBlock, 0);
                mDeviceRowDirty = true;
            }
            if (mDeviceRowDirty) {
                refreshDeviceBlock();
                mDeviceRowDirty = false;
            }
        } else if (mDeviceBlock != null) {
            mWidgetRow.removeView(mDeviceBlock);
            mDeviceBlock = null;
        }

        if (mEnabled) {
            if (mMainRow == null) {
                mMainRow = new LinearLayout(mContext);
                mMainRow.setOrientation(LinearLayout.HORIZONTAL);
                mMainRow.setGravity(Gravity.CENTER_HORIZONTAL);
                // MATCH_PARENT, non WRAP_CONTENT: serve spazio reale da distribuire tra i
                // pulsanti con layout_weight=1, altrimenti collassano a larghezza zero.
                mWidgetRow.addView(mMainRow, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                mRowDirty = true;
            }
            if (mMiniRow == null) {
                mMiniRow = new LinearLayout(mContext);
                mMiniRow.setOrientation(LinearLayout.HORIZONTAL);
                mMiniRow.setGravity(Gravity.CENTER_HORIZONTAL);
                mWidgetRow.addView(mMiniRow, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                mRowDirty = true;
            }
            if (mRowDirty) {
                rebuildWidgetRows();
                mRowDirty = false;
            }
        } else if (mMainRow != null) {
            mWidgetRow.removeView(mMainRow);
            mWidgetRow.removeView(mMiniRow);
            mMainRow = null;
            mMiniRow = null;
        }

        applyMargin();
    }

    /** Come OC (LockscreenWidgetsView.createMainWidgetsContainer/createSecondaryWidgetsContainer):
     *  due righe indipendenti dentro lo stesso contenitore verticale, non una riga sola che
     *  "va a capo" — quella che sembra "auto-wrap quando la prima riga è piena" è in realtà
     *  sempre così: se ci sono widget grandi E mini, occupano due righe fisse; se c'è solo un
     *  tipo, l'altra riga resta GONE invece di lasciare uno spazio vuoto. */
    private void rebuildWidgetRows() {
        if (mMainRow == null || mMiniRow == null) return;
        mMainRow.removeAllViews();
        mMiniRow.removeAllViews();
        int margin = dp(WIDGET_MARGIN_DP);

        // Se c'è un solo widget grande attivo, il peso lo farebbe allungare su tutta la riga
        // (nessun altro peso con cui condividere lo spazio) — come in OC, il peso si usa solo
        // quando ce ne sono almeno due; con uno solo si torna alla larghezza fissa.
        int activeMainCount = 0;
        for (int i = 0; i < MAIN_SLOT_COUNT && i < SLOT_KEYS.length; i++) {
            if (mSlotType[i] != 0) activeMainCount++;
        }
        int activeMiniCount = 0;
        for (int i = MAIN_SLOT_COUNT; i < SLOT_KEYS.length; i++) {
            if (mSlotType[i] != 0) activeMiniCount++;
        }

        for (int i = 0; i < SLOT_KEYS.length; i++) {
            int type = mSlotType[i];
            if (type == 0) continue; // "Nessuno"
            SlotResources res = resolveSlot(type, mSlotApp[i]);
            if (res == null) continue; // fase 2 non ancora collegata — nascosto, non rotto

            boolean isMain = i < MAIN_SLOT_COUNT;
            View widget = isMain ? buildMainWidget(res) : buildMiniWidget(res);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) widget.getLayoutParams();
            if (isMain) {
                // Stessa tecnica di ExtendedFAB di OC: larghezza 0 + peso 1 così il
                // LinearLayout divide lo spazio esattamente a metà tra i pulsanti grandi,
                // indipendentemente da quanto testo/riga ha ciascuno. Altezza fissa per lo
                // stesso motivo (non WRAP_CONTENT, che varia con 1 o 2 righe di testo).
                if (activeMainCount >= 2) {
                    lp.width = 0;
                    lp.weight = 1f;
                } else {
                    lp.width = dp(MAIN_WIDTH_DP);
                }
                lp.height = dp(MAIN_HEIGHT_DP);
            }
            lp.setMargins(margin, 0, margin, 0);
            // Etichette di lunghezza diversa (1 riga vs 2) danno pulsanti di altezza diversa —
            // vanno centrati verticalmente tra loro, non allineati in alto, altrimenti sembrano
            // sfalsati quando uno dei due va a capo e l'altro no.
            lp.gravity = Gravity.CENTER_VERTICAL;
            (isMain ? mMainRow : mMiniRow).addView(widget, lp);
        }

        mMainRow.setVisibility(activeMainCount > 0 ? View.VISIBLE : View.GONE);
        mMiniRow.setVisibility(activeMiniCount > 0 ? View.VISIBLE : View.GONE);
        // Spazio tra le due righe solo quando sono entrambe visibili — altrimenti (una sola
        // riga) non deve avanzare margine sopra il resto del contenuto.
        LinearLayout.LayoutParams mainLp = (LinearLayout.LayoutParams) mMainRow.getLayoutParams();
        mainLp.bottomMargin = (activeMainCount > 0 && activeMiniCount > 0) ? dp(ROW_GAP_DP) : 0;
        mMainRow.setLayoutParams(mainLp);
    }

    // ── Widget Dispositivo: nome + fino a 6 gauge (batteria/RAM/volume/temperatura) ────────

    private LinearLayout buildDeviceBlock() {
        LinearLayout block = new LinearLayout(mContext);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.CENTER_HORIZONTAL);
        block.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mDeviceNameView = new TextView(mContext);
        mDeviceNameView.setTextSize(13);
        mDeviceNameView.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.bottomMargin = dp(6);
        block.addView(mDeviceNameView, nameLp);

        mDeviceRow = new LinearLayout(mContext);
        mDeviceRow.setOrientation(LinearLayout.HORIZONTAL);
        mDeviceRow.setGravity(Gravity.CENTER_HORIZONTAL);
        block.addView(mDeviceRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return block;
    }

    private void refreshDeviceBlock() {
        if (mDeviceBlock == null) return;
        mDeviceNameView.setText(mDeviceName != null && !mDeviceName.isEmpty() ? mDeviceName : Build.MODEL);
        mDeviceNameView.setTextColor(mDeviceCustomColor ? mDeviceTextColor : 0xFFFFFFFF);

        boolean circular = mDeviceStyle == 0;
        DeviceStatGaugeView.Style style = circular ? DeviceStatGaugeView.Style.CIRCULAR : DeviceStatGaugeView.Style.LINEAR;
        int progressColor = mDeviceCustomColor
                ? (circular ? mDeviceColorCircular : mDeviceColorLinear)
                : appAccentColor();
        int textColor = mDeviceCustomColor ? mDeviceTextColor : 0xFFFFFFFF;

        List<Integer> types = new ArrayList<>();
        for (int type : mDeviceTypes) if (type != 0) types.add(type);
        int shown = types.size();

        mDeviceRow.removeAllViews();
        if (circular) {
            // Più gauge attivi ci sono, più piccoli/vicini devono stare per entrarci tutti.
            int scale = shown <= 2 ? 100 : shown <= 4 ? 78 : shown == 5 ? 62 : 52;
            int gap = dp(shown <= 4 ? 8 : shown == 5 ? 4 : 2);
            int gaugeW = dp(72) * scale / 100;
            int gaugeH = dp(72) * scale / 100;
            mDeviceRow.setGravity(Gravity.CENTER_HORIZONTAL);
            mDeviceRow.setWeightSum(0);
            for (int type : types) mDeviceRow.addView(
                    buildGauge(type, style, progressColor, textColor, gaugeW, gaugeH, gap));
        } else {
            // Lineare: metà a sinistra, metà a destra, con uno spazio vuoto reale in mezzo —
            // altrimenti se il contenuto di ogni metà riempie tutta la sua metà di riga, le
            // due metà finiscono comunque attaccate e sembra un'unica riga continua.
            int half = (shown + 1) / 2;
            int perHalf = Math.max(Math.max(half, shown - half), 1);
            int gap = dp(perHalf <= 2 ? 8 : 4);
            int gaugeW = Math.max(dp(130) / perHalf - gap, dp(44));
            int gaugeH = dp(44) * (perHalf <= 1 ? 100 : 80) / 100;

            LinearLayout left = new LinearLayout(mContext);
            left.setOrientation(LinearLayout.HORIZONTAL);
            left.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            LinearLayout right = new LinearLayout(mContext);
            right.setOrientation(LinearLayout.HORIZONTAL);
            right.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

            for (int i = 0; i < types.size(); i++) {
                View g = buildGauge(types.get(i), style, progressColor, textColor, gaugeW, gaugeH, gap);
                (i < half ? left : right).addView(g);
            }
            LinearLayout.LayoutParams halfLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            mDeviceRow.addView(left, halfLp);
            View spacer = new View(mContext);
            mDeviceRow.addView(spacer, new LinearLayout.LayoutParams(dp(32), 1));
            mDeviceRow.addView(right, halfLp);
        }
    }

    private View buildGauge(int typeIdx, DeviceStatGaugeView.Style style, int progressColor, int textColor,
                             int w, int h, int gap) {
        DeviceStatGaugeView gauge = new DeviceStatGaugeView(mContext);
        configureGauge(gauge, typeIdx, style, progressColor, textColor);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.leftMargin = gap; lp.rightMargin = gap;
        gauge.setLayoutParams(lp);
        return gauge;
    }

    private void configureGauge(DeviceStatGaugeView gauge, int typeIdx, DeviceStatGaugeView.Style style,
                                 int progressColor, int textColor) {
        DeviceStatGaugeView.StatType type = switch (typeIdx) {
            case 2 -> DeviceStatGaugeView.StatType.RAM;
            case 3 -> DeviceStatGaugeView.StatType.VOLUME;
            case 4 -> DeviceStatGaugeView.StatType.TEMPERATURE;
            case 5 -> DeviceStatGaugeView.StatType.WIFI;
            case 6 -> DeviceStatGaugeView.StatType.BLUETOOTH;
            default -> DeviceStatGaugeView.StatType.BATTERY;
        };
        gauge.setType(type);
        gauge.setStyle(style);
        gauge.setColors(progressColor, textColor);
        gauge.setIcon(resolveDrawable(switch (type) {
            case RAM -> "ic_widget_ram";
            case VOLUME -> "ic_sysui_volume";
            case TEMPERATURE -> "ic_widget_temperature";
            case WIFI -> "ic_widget_wifi";
            case BLUETOOTH -> "ic_widget_bluetooth";
            default -> "ic_battery";
        }));
    }

    // ── Widget grande: ExtendedFloatingActionButton (icona + testo), come ExtendedFAB di OC ──

    private View buildMainWidget(SlotResources res) {
        ExtendedFloatingActionButton fab = new ExtendedFloatingActionButton(
                mMaterialContext != null ? mMaterialContext : mContext);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fab.setLayoutParams(lp);

        // Senza Colori Personalizzati l'accento va usato solo per lo stato attivo — altrimenti
        // (es. Torcia spenta) il pulsante resta comunque colorato e sembra sempre "attivo".
        int bgColor   = mCustomColor ? (res.active ? mBigActive : mBigInactive)
                                      : (res.active ? appAccentColor() : 0x40FFFFFF);
        int iconColor = mCustomColor ? (res.active ? mBigIconActive : mBigIconInactive) : 0xFFFFFFFF;

        applyFabBackground(fab, bgColor);
        if (res.icon != null) {
            fab.setIcon(res.tintable ? tint(res.icon, iconColor) : res.icon);
        }
        fab.setIconTint(res.tintable ? ColorStateList.valueOf(iconColor) : null);
        fab.setText(res.label);
        fab.setTextColor(iconColor);
        fab.setAllCaps(false);
        fab.setTextSize(12);
        fab.setSingleLine(false);
        fab.setMaxLines(3);
        fab.setLineSpacing(0, 0.95f);
        // Larghezza e altezza uguali per tutti: gestite in rebuildWidgetRows() con
        // layout_weight=1 (come ExtendedFAB di OC) invece di min/max width sul singolo
        // pulsante — è il LinearLayout a distribuire lo spazio in parti uguali, garantito.
        fab.setMaxWidth(dp(MAIN_WIDTH_DP));
        int vPad = dp(10);
        fab.setPadding(fab.getPaddingLeft(), vPad, fab.getPaddingRight(), vPad);
        fab.extend();
        fab.setOnClickListener(res.onClick);
        if (res.onLongClick != null) fab.setOnLongClickListener(res.onLongClick);
        return fab;
    }

    // ── Widget mini: semplice cerchio icona, come i mini widget di OC ──────────────────────

    private View buildMiniWidget(SlotResources res) {
        int sizeDp = MINI_SIZE_DP * mScalePercent / 100;
        int size = dp(sizeDp);
        int padding = dp(sizeDp / 4);

        int bgColor   = mCustomColor ? (res.active ? mSmallActive : mSmallInactive)
                                      : (res.active ? appAccentColor() : 0x40FFFFFF);
        int iconColor = mCustomColor ? (res.active ? mSmallIconActive : mSmallIconInactive) : 0xFFFFFFFF;

        ImageView iv = new ImageView(appContext != null ? appContext : mContext);
        iv.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        iv.setPadding(padding, padding, padding, padding);
        Drawable customBg = customWidgetBg();
        if (customBg != null) {
            iv.setBackground(customBg);
        } else {
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(bgColor);
            iv.setBackground(bg);
        }
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (res.icon != null) iv.setImageDrawable(res.icon);
        if (res.tintable) iv.setColorFilter(iconColor);
        iv.setOnClickListener(res.onClick);
        if (res.onLongClick != null) iv.setOnLongClickListener(res.onLongClick);
        return iv;
    }

    private Drawable tint(Drawable d, int color) {
        Drawable wrapped = d.mutate();
        wrapped.setTint(color);
        return wrapped;
    }

    private int appAccentColor() {
        if (Xprefs.getBoolean("DST_ACCENT1_on", false)) {
            return Xprefs.getInt("DST_ACCENT1", 0xFF908DFF) | 0xFF000000;
        }
        return 0xFF908DFF;
    }

    /** Come appAccentColor(), ma per l'Accento Secondario (DST_ACCENT2) — usato dalle
     *  varianti a gradiente/bordo di "Sfondo Personalizzato" (vedi WeatherBgFactory). */
    private int appAccent2Color() {
        if (Xprefs.getBoolean("DST_ACCENT2_on", false)) {
            return Xprefs.getInt("DST_ACCENT2", 0xFF3700B3) | 0xFF000000;
        }
        return 0xFF3700B3;
    }

    /** Sfondo per "Sfondo Personalizzato" (grandi e mini) — stesse 9 varianti di "Sfondo
     *  Meteo" (WeatherBgFactory), una nuova istanza per ogni widget cosi ognuno ha il proprio
     *  Drawable mutabile. Null se lo switch è spento o la variante è "0 = nessuno" — il
     *  chiamante ricade sul colore normale (Colori Personalizzati/accento). */
    private Drawable customWidgetBg() {
        if (!mCustomBg) return null;
        Drawable bg = switch (mBgSelection) {
            case 1 -> resolveDrawableById(R.drawable.weather_bg_box);
            case 2 -> resolveDrawableById(R.drawable.weather_bg_box_round);
            case 3 -> resolveDrawableById(R.drawable.weather_bg_pill);
            default -> WeatherBgFactory.buildAccentDrawable(
                    mBgSelection, appAccentColor(), appAccent2Color(),
                    mContext.getResources().getDisplayMetrics().density);
        };
        if (bg == null) return null;
        bg = bg.mutate();
        bg.setAlpha(WeatherBgFactory.alpha(mBgSelection));
        return bg;
    }

    /** Come customWidgetBg(), ma per l'ExtendedFAB dei widget grandi. Il primo tentativo
     *  (Drawable via setBackground()) mostrava solo un colore piatto: MaterialButton applica
     *  SEMPRE il suo backgroundTintList ereditato dal tema come color filter SOPRA qualunque
     *  Drawable gli si assegni — senza azzerarlo prima, "mangiava" il gradiente/bordo del
     *  Drawable custom. Un secondo tentativo con le proprietà native (tint/stroke/corner) è
     *  risultato comunque più chiaro del colore richiesto (~#33373F invece di #1B2029,
     *  verificato via color picker sullo screenshot) anche con elevazione ed SRC_IN forzati —
     *  causa non isolata. Si torna quindi al Drawable vero (fedeltà piena, gradiente incluso,
     *  identico ai mini) ma stavolta azzerando esplicitamente backgroundTintList PRIMA di
     *  assegnarlo, cosi non c'è più nulla che lo tinga sopra. */
    private void applyFabBackground(ExtendedFloatingActionButton fab, int fallbackColor) {
        fab.setStrokeWidth(0);
        fab.setElevation(0f);
        Drawable customBg = customWidgetBg();
        if (customBg == null) {
            // Ogni ExtendedFAB è un'istanza nuova (ricreata a ogni rebuild, mai riusata), ha
            // già la sua shape/drawable di default di MaterialButton — basta tingerla.
            fab.setBackgroundTintList(ColorStateList.valueOf(fallbackColor));
        } else {
            fab.setBackgroundTintList(null);
            fab.setBackground(customBg);
        }
    }

    private static class SlotResources {
        Drawable icon;
        String label = "";
        boolean tintable = true;
        boolean active = true;
        View.OnClickListener onClick = v -> {};
        View.OnLongClickListener onLongClick = null;
    }

    /** Mappa indice-tipo (vedi R.array.lockscreen_widget_entries) su icona + etichetta +
     *  azione, come OC's setUpWidgetResources(). Torna null per i tipi non ancora collegati
     *  (fase 2) — lo slot resta semplicemente nascosto. */
    private SlotResources resolveSlot(int type, String appPackage) {
        SlotResources r = new SlotResources();
        switch (type) {
            case TYPE_CUSTOM_APP -> {
                r.tintable = false;
                String appLabel = null;
                if (appPackage != null && !appPackage.isEmpty()) {
                    try {
                        PackageManager pm = mContext.getPackageManager();
                        r.icon = pm.getApplicationIcon(appPackage);
                        appLabel = pm.getApplicationLabel(pm.getApplicationInfo(appPackage, 0)).toString();
                    } catch (Throwable ignored) {}
                }
                if (r.icon == null) { r.icon = resolveDrawable("ic_widget_app_generic"); r.tintable = true; }
                r.label = appLabel != null ? appLabel : string("lockscreen_widgets_pick_app_title");
                r.onClick = v -> { if (mLauncher != null) mLauncher.launchApp(appPackage); };
            }
            case 2 -> { // Calcolatrice
                r.icon = resolveDrawable("ic_widget_calculator");
                r.label = "Calcolatrice";
                r.onClick = v -> { if (mLauncher != null) mLauncher.launchCalculator(); };
            }
            case 3 -> { // Media — apre il lettore predefinito (tocca lungo per play/pausa)
                r.icon = resolveDrawable("ic_widget_media");
                r.label = "Media";
                r.onClick = v -> { if (mLauncher != null) mLauncher.launchMusicPlayer(); };
                r.onLongClick = v -> { toggleMediaPlayback(); return true; };
            }
            case 4 -> { // Timer
                r.icon = resolveDrawable("ic_clock");
                r.label = "Timer";
                r.onClick = v -> { if (mLauncher != null) mLauncher.launchTimer(); };
            }
            case 5 -> { // Torcia
                r.icon = resolveDrawable("ic_widget_torch");
                r.active = mTorchOn;
                r.label = mTorchOn ? "Torcia attivata" : "Torcia disattivata";
                r.onClick = v -> toggleTorch();
            }
            case 6 -> { // Meteo — icona del pack scelto in "Meteo" (stesso pref condiviso di
                        // LockscreenWeather/AodWeather); i pack reali sono immagini a colori
                        // fissi e non vanno tintate, solo il fallback vettoriale lo è.
                WeatherInfo info = WeatherCache.get();
                int packIconRes = info != null
                        ? WeatherIconPacks.resolve(mContext, mWeatherIconPack, info.weatherCode, info.isDay)
                        : 0;
                Drawable weatherIcon = packIconRes != 0 ? resolveDrawableById(packIconRes) : null;
                r.tintable = weatherIcon == null;
                if (weatherIcon == null) {
                    weatherIcon = info != null
                            ? resolveDrawableById(WeatherInfo.iconRes(info.weatherCode, info.isDay))
                            : resolveDrawable("ic_weather_sunny");
                }
                r.icon = weatherIcon;
                r.label = weatherLabel();
                r.onClick = v -> { if (mLauncher != null) mLauncher.launchWeatherApp(); };
            }
            case 7 -> { // Wi-Fi — WifiManager.setWifiEnabled() è deprecata per le app normali,
                        // ma dentro SystemUI (contesto privilegiato) può ancora funzionare.
                if (mWifiManager == null) { return null; }
                boolean wifiOn = mWifiManager.isWifiEnabled();
                r.icon = resolveDrawable("ic_widget_wifi");
                r.active = wifiOn;
                r.label = wifiOn ? "Wi-Fi attivo" : "Wi-Fi disattivo";
                r.onClick = v -> toggleWifi();
            }
            case 10 -> { // Bluetooth — stessa cosa: API pubblica ma deprecata/ristretta.
                if (mBluetoothAdapter == null) { return null; }
                boolean btOn = mBluetoothAdapter.isEnabled();
                r.icon = resolveDrawable("ic_widget_bluetooth");
                r.active = btOn;
                r.label = btOn ? "Bluetooth attivo" : "Bluetooth disattivo";
                r.onClick = v -> toggleBluetooth();
            }
            case 9 -> { // Suoneria
                r.icon = resolveDrawable("ic_sysui_volume");
                r.label = ringerLabel();
                r.onClick = v -> toggleRingerMode();
            }
            case 12 -> { // Fotocamera
                r.icon = resolveDrawable("ic_widget_camera");
                r.label = "Fotocamera";
                r.onClick = v -> { if (mLauncher != null) mLauncher.launchCamera(); };
            }
            case 13 -> { // Wallet
                r.icon = resolveDrawable("ic_widget_wallet");
                r.label = "Wallet";
                r.onClick = v -> { if (mLauncher != null) mLauncher.launchWallet(); };
            }
            case 11 -> { // Controlli Casa — nessuno stato attivo/inattivo, come Calcolatrice/
                        // Timer/Fotocamera: tocco apre semplicemente l'Activity giusta.
                if (mDeviceControlsTile == null) return null;
                r.icon = resolveDrawable("ic_widget_home_controls");
                r.label = "Controlli Casa";
                r.onClick = v -> launchDeviceControls();
            }
            default -> { return null; }
        }
        return r;
    }

    /** "34°C · Soleggiato", come OC (mWeatherInfo.temp + unit + " • " + condizione). Usa la
     *  stessa cache del widget Meteo SdB/AOD — se non ancora popolata, l'etichetta resta
     *  "Meteo" finché non arriva il primo aggiornamento. */
    private String weatherLabel() {
        WeatherInfo info = WeatherCache.get();
        if (info == null) return "Meteo";
        // Temperatura su una riga propria invece che affiancata alla condizione con " · " —
        // quest'ultima (es. "Poco Nuvoloso") va spesso a capo da sola, e le due righe insieme
        // superavano il vecchio maxLines(2) del pulsante tagliando il testo (es. solo "24°C ·
        // Poco" visibile) — vedi anche il bump a maxLines(3) qui sotto.
        return info.tempC + "°C\n" + WeatherInfo.conditionText(info.weatherCode, mContext);
    }

    private String ringerLabel() {
        if (mAudioManager == null) return "Suoneria";
        return switch (mAudioManager.getRingerMode()) {
            case AudioManager.RINGER_MODE_VIBRATE -> "Suoneria: vibrazione";
            case AudioManager.RINGER_MODE_SILENT -> "Suoneria: silenziosa";
            default -> "Suoneria: normale";
        };
    }

    /** Legge una stringa dalle risorse dell'app (non essenziale se manca — solo etichette). */
    private String string(String name, String fallback) {
        if (appContext == null) return fallback;
        try {
            int id = appContext.getResources().getIdentifier(name, "string", appContext.getPackageName());
            return id != 0 ? appContext.getResources().getString(id) : fallback;
        } catch (Throwable t) { return fallback; }
    }

    private String string(String name) { return string(name, ""); }

    // ── Azioni dirette (API pubbliche, nessuna reflection su classi interne di SystemUI) ──

    private void toggleTorch() {
        if (mCameraManager == null || mTorchCameraId == null) return;
        try {
            mTorchOn = !mTorchOn;
            mCameraManager.setTorchMode(mTorchCameraId, mTorchOn);
            mRowDirty = true;
            refreshForCurrentState();
        } catch (Throwable t) {
            dbg("toggleTorch failed: " + t);
        }
    }

    private void toggleWifi() {
        if (mWifiManager == null) return;
        try {
            mWifiManager.setWifiEnabled(!mWifiManager.isWifiEnabled());
            mRowDirty = true;
            refreshForCurrentState();
        } catch (Throwable t) {
            dbg("toggleWifi failed: " + t);
        }
    }

    @SuppressWarnings("deprecation")
    private void toggleBluetooth() {
        if (mBluetoothAdapter == null) return;
        try {
            if (mBluetoothAdapter.isEnabled()) mBluetoothAdapter.disable();
            else mBluetoothAdapter.enable();
            mRowDirty = true;
            refreshForCurrentState();
        } catch (Throwable t) {
            dbg("toggleBluetooth failed: " + t);
        }
    }

    private void launchDeviceControls() {
        if (mDeviceControlsTile == null) return;
        try { callMethod(mDeviceControlsTile, "handleClick", (Object) null); }
        catch (Throwable t) { dbg("launchDeviceControls failed: " + t); }
    }

    private void toggleRingerMode() {
        if (mAudioManager == null) return;
        try {
            int mode = mAudioManager.getRingerMode();
            int next = switch (mode) {
                case AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE;
                case AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT;
                default -> AudioManager.RINGER_MODE_NORMAL;
            };
            mAudioManager.setRingerMode(next);
            mRowDirty = true;
            refreshForCurrentState();
        } catch (Throwable t) {
            dbg("toggleRingerMode failed: " + t);
        }
    }

    private void toggleMediaPlayback() {
        if (mAudioManager == null) return;
        try {
            long now = SystemClock.uptimeMillis();
            KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0);
            mAudioManager.dispatchMediaKeyEvent(down);
            mAudioManager.dispatchMediaKeyEvent(KeyEvent.changeAction(down, KeyEvent.ACTION_UP));
        } catch (Throwable t) {
            dbg("toggleMediaPlayback failed: " + t);
        }
    }

    private void applyMargin() {
        if (mWidgetRow == null) return;
        // 2026-09-18: con stili orologio più alti del previsto i widget finivano sopra al
        // calendario (bug reale, confermato live) — leggere l'altezza vera a runtime si è
        // rivelato instabile (vedi LockscreenClockMod.STYLE_WIDGET_MARGIN_DP). Fix a due livelli,
        // proposto dall'utente: 1) base di sicurezza = metà schermo, sotto QUALSIASI stile senza
        // bisogno di testarli tutti e 61; 2) tabella di valori calibrati per stile (se presente)
        // per chi vuole il posizionamento esatto invece del generico "metà schermo".
        Integer calibrated = LockscreenClockMod.getCalibratedWidgetMarginDp(LockscreenClockMod.getCurrentStyle());
        int safeBase = mContext.getResources().getDisplayMetrics().heightPixels / 2 - dp(100);
        int topMargin = calibrated != null ? dp(calibrated) : safeBase + dp(mTopMargin);

        ViewGroup.LayoutParams lpRaw = mWidgetRow.getLayoutParams();
        if (lpRaw instanceof ViewGroup.MarginLayoutParams mlp) {
            mlp.setMargins(0, topMargin, 0, 0);
            mWidgetRow.setLayoutParams(mlp);
        } else {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, topMargin, 0, 0);
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            mWidgetRow.setLayoutParams(lp);
        }
    }

    private Drawable resolveDrawable(String name) {
        if (appContext == null) return null;
        try {
            int resId = appContext.getResources().getIdentifier(name, "drawable", appContext.getPackageName());
            return resolveDrawableById(resId);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Come resolveDrawable(String), ma per un ID già noto (R.drawable.xxx di Obsidian, o
     *  quello tornato da WeatherIconPacks.resolve) invece che per nome. */
    private Drawable resolveDrawableById(int resId) {
        if (appContext == null || resId == 0) return null;
        try {
            return ResourcesCompat.getDrawable(appContext.getResources(), resId, appContext.getTheme());
        } catch (Throwable t) {
            return null;
        }
    }

    private int dp(int v) {
        return Math.round(v * mContext.getResources().getDisplayMetrics().density);
    }

    private Class<?> tryFindClass(XC_LoadPackage.LoadPackageParam lp, String... names) {
        for (String name : names) {
            try { return findClass(name, lp.classLoader); } catch (Throwable ignored) {}
        }
        return null;
    }

    private static void dbg(String msg) {
        XposedBridge.log("[ Obsidian ] LockscreenWidgetsMod: " + msg);
    }

    @Override public boolean listensTo(String packageName) { return SYSTEM_UI.equals(packageName); }
}
