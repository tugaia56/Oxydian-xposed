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
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
import it.tugaia56.obsidian.weather.WeatherCache;
import it.tugaia56.obsidian.weather.WeatherIconPacks;
import it.tugaia56.obsidian.weather.WeatherInfo;
import it.tugaia56.obsidian.xposed.XposedMods;
import it.tugaia56.obsidian.xposed.utils.ActivityLauncherUtils;

/**
 * Widget Impostazioni Rapide — porting semplificato di OC's QsWidgets/QsControlsView:
 * svuota il pannello media QS reale e ci inietta una singola riga scorrevole di "chip"
 * (icona + etichetta), una per ogni voce dell'elenco configurato in QsWidgetsFragment
 * (pref CSV "qs_widgets_list"). Niente ViewPager a pagine come OC (versione-fragile per via
 * dei suoi hack di touch-forwarding OOS16) — una riga sola, più semplice e robusta, stesso
 * spirito "meglio nascosto che rotto" di LockscreenWidgetsMod.
 *
 * Azioni dirette (torcia/Wi-Fi/Bluetooth/suoneria/media) copiate 1:1 da LockscreenWidgetsMod
 * — stato indipendente apposta (istanza separata), per non rischiare di toccare quell'hook
 * già collaudato. "Foto" e i tipi Dati Mobili/Controlli Casa restano nascosti come nelle altre
 * mod (nessuna UI di selezione foto/reflection sui controller OEM ancora collegata) invece di
 * mostrare un'icona rotta.
 */
public class QsWidgetsMod extends XposedMods {

    private static final String KEY_ENABLED = "qs_widgets_switch";
    private static final String KEY_LIST    = "qs_widgets_list";
    /** Stesso pref condiviso di Meteo SdB/AOD/Widget SdB. */
    private static final String KEY_WEATHER_ICON_PACK = "weather_icon_pack";
    private static final String TAG_MARKER = "obsidian_qs_widgets";

    private boolean mEnabled;
    private List<String> mWidgets = new ArrayList<>();
    private String mWeatherIconPack = WeatherIconPacks.DEFAULT;

    private Context appContext;
    private Context mMaterialContext;
    /** Il pannello media reale di OOS, svuotato e riusato come contenitore. */
    private ViewGroup mContainer;
    /** Colonna verticale di righe — niente scroll orizzontale: OOS intercetta lo swipe
     *  orizzontale sul pannello QS (chiude il pannello o cambia pagina) prima che arrivi a un
     *  HorizontalScrollView interno, quindi qualunque voce oltre la prima resterebbe
     *  irraggiungibile. Tile compatte (icona+etichetta corta, come le tile QS native) che
     *  vanno a capo su più righe invece — stesso approccio "niente pager" già usato per
     *  Widget Schermata di Blocco. */
    private LinearLayout mGrid;
    private boolean mRowDirty = true;
    /** Quante tile per riga prima di andare a capo — calibrato su schermo reale (screenshot):
     *  anche a 62dp di larghezza, la terza tile su una riga da 3 spariva del tutto (il
     *  pannello media è molto più stretto di quanto sembri accanto a "Torcia"). Con 2 per riga
     *  entrambe restano piene e leggibili. */
    private static final int TILES_PER_ROW = 2;

    private Object mActivityStarter;
    private ActivityLauncherUtils mLauncher;

    private CameraManager mCameraManager;
    private String mTorchCameraId;
    private boolean mTorchOn;
    private AudioManager mAudioManager;
    private WifiManager mWifiManager;
    private BluetoothAdapter mBluetoothAdapter;
    /** Il riquadro QS "Controlli Casa" stesso — si richiama direttamente il suo
     *  handleClick(Expandable), che apre l'Activity giusta da solo. Resta null (widget nascosto)
     *  se il riquadro non esiste su questa build — es. gate regionale lato OOS. */
    private Object mDeviceControlsTile;

    public QsWidgetsMod(Context context) {
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
        List<String> list = new ArrayList<>();
        for (String s : Xprefs.getString(KEY_LIST, "media").split(",")) {
            if (!s.isEmpty()) list.add(s);
        }
        mWidgets = list;
        mWeatherIconPack = Xprefs.getString(KEY_WEATHER_ICON_PACK, WeatherIconPacks.DEFAULT);
        mRowDirty = true;
        refresh();
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!SYSTEM_UI.equals(lp.packageName)) return;

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

        // Controlli Casa — niente servizio di sistema pubblico equivalente a Wi-Fi/Bluetooth,
        // l'unico modo è agganciare l'istanza già viva del vero riquadro QS nativo. Se l'utente
        // non ha quel riquadro tra le Impostazioni Rapide (o su questa build/regione non esiste
        // affatto), l'oggetto non viene mai costruito e il widget resta nascosto — stessa logica
        // di LockscreenWidgetsMod. Dati Mobili (stessa tecnica, via CellularTile) tolto: senza un
        // aggancio live sicuro (DataUsageController ha un solo slot per l'ascoltatore, già
        // occupato dal riquadro Cellulare nativo) il widget non si aggiornava quando i dati
        // venivano attivati/disattivati da altrove, giudicato inutile.
        Class<?> deviceControlsTile = tryFindClass(lp,
                "com.oplus.systemui.qs.tiles.OplusDeviceControlsTile", "com.android.systemui.qs.tiles.DeviceControlsTile");
        if (deviceControlsTile != null) {
            hookAllConstructors(deviceControlsTile, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    mDeviceControlsTile = p.thisObject;
                    mRowDirty = true;
                    refresh();
                }
            });
        } else {
            dbg("DeviceControlsTile non trovata — widget Controlli Casa non disponibile");
        }

        // Stesso aggancio di LockscreenWidgetsMod — istanza propria, non condivisa.
        try {
            Class<?> interactor = findClass(
                    "com.android.systemui.keyguard.domain.interactor.KeyguardQuickAffordanceInteractor",
                    lp.classLoader);
            hookAllConstructors(interactor, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        mActivityStarter = getObjectField(p.thisObject, "activityStarter");
                        mLauncher = new ActivityLauncherUtils(mContext, mActivityStarter);
                    } catch (Throwable t) { dbg("activityStarter capture failed: " + t); }
                }
            });
        } catch (Throwable t) {
            dbg("KeyguardQuickAffordanceInteractor not found: " + t);
        }

        Class<?> mediaPanelClass = tryFindClass(lp,
                "com.oplus.systemui.qs.media.OplusQsBaseMediaPanelView", // OOS16
                "com.oplus.systemui.qs.media.OplusQsMediaPanelView");    // OOS15 e precedenti
        if (mediaPanelClass == null) {
            dbg("OplusQsBaseMediaPanelView/OplusQsMediaPanelView non trovata — Widget QS non disponibili");
            return;
        }
        hookAllMethods(mediaPanelClass, "onFinishInflate", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try { onPanelInflated(p); } catch (Throwable t) { dbg("onFinishInflate hook failed: " + t); }
            }
        });
    }

    // BUG ("colonna widget sovrapposta alla card media reale"): quando parte una riproduzione
    // musicale vera, OOS ripopola questo stesso contenitore con la sua card nativa (copertina/
    // controlli) SENZA richiamare onFinishInflate — la nostra griglia iniettata resta attaccata
    // sopra e le due cose finiscono sovrapposte/sporche. Tentato prima l'approccio di OC
    // (QsWidgets.java: fingere che "modalità media sia sempre attiva" così OOS non prova mai a
    // iniettare la sua card) — non porta su questa build: confermato via log diagnostici che
    // OOS16 usa un sistema completamente diverso ("Media Carousel Controller" con MediaHost
    // separati, trovato decompilando SystemUI.apk vera) che non passa più dai vecchi
    // setMediaMode/setQsMediaPanelShown che OC agganciava — mai nemmeno chiamati.
    // Soluzione più semplice (proposta dall'utente): invece di litigare col nuovo sistema,
    // cedergli il posto quando serve davvero. Primo tentativo con AudioManager.isMusicActive()
    // — SBAGLIATO: torna false in pausa (OOS tiene comunque la card visibile) e resta false pure
    // a riproduzione forza-chiusa (OOS mette in cache la copertina e non la ritira da sola,
    // confermato dall'utente in test live) — "aspettare che se ne vada" non è un segnale
    // affidabile. Fix reale: guardiamo direttamente cosa c'è DENTRO il contenitore ad ogni tick
    // — se contiene una view che non è la nostra griglia, vuol dire che OOS ci ha messo la sua
    // card e ci togliamo; se contiene solo la nostra griglia (o è vuoto), il posto è libero e la
    // rimettiamo. Verità dal vivo invece di un segnale indiretto (audio) che può sbagliare.
    // Controllo leggero, attivo SOLO mentre il pannello QS è davvero visibile
    // (OnAttachStateChangeListener sul contenitore), non un timer permanente in background.
    // Tentato anche un "margine di sicurezza + sfratto forzato" (mContainer.removeAllViews())
    // per i casi di copertina "incollata" a tempo indeterminato — REVERTED 2026-08-29: sfrattare
    // con removeAllViews() rompe qualcosa nello stato interno di OOS, che poi non riesce più a
    // reiniettare la sua card nemmeno con musica vera in corso, per il resto della sessione
    // SystemUI (confermato dall'utente in test live — molto peggio del difetto originale).
    // Tentato anche un riavvio automatico di SystemUI (Process.killProcess(getpid()) dall'interno
    // del suo stesso processo, stesso effetto del pulsante "Riavvia SystemUI") dopo il margine —
    // REVERTED anche questo, stesso giorno: il timer partiva da quando appariva la card (non da
    // quando l'audio si fermava davvero), quindi scattava appena l'utente metteva in PAUSA, non
    // solo a app chiusa; e soprattutto la copertina restava incollata ANCHE dopo il riavvio,
    // quindi il ciclo si ripeteva 3-4 volte di fila (schermo che sfarfalla ripetutamente) SENZA
    // risolvere nulla — peggio del problema originale, confermato dall'utente in test live.
    // Nessuna via automatica trovata per il caso "copertina incollata a tempo indeterminato" che
    // non introduca un effetto collaterale peggiore — resta un limite noto e accettato: il
    // riavvio SystemUI risolve sempre, ma va fatto manualmente (pulsante in app) quando serve.
    // Restano solo le due reazioni sicure e testate (mollare/riprendersi il contenitore in base
    // a cosa contiene davvero) — nessuna manipolazione di view non nostre, nessun riavvio.
    private boolean mMediaCardPresent = false;
    private final android.os.Handler mMediaPollHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable mMediaPoll = new Runnable() {
        @Override public void run() {
            checkMediaCardState();
            mMediaPollHandler.postDelayed(this, 1000);
        }
    };

    private void checkMediaCardState() {
        if (mContainer == null || !mEnabled) return;
        boolean oosOwnsContainer = false;
        for (int i = 0; i < mContainer.getChildCount(); i++) {
            if (mContainer.getChildAt(i) != mGrid) { oosOwnsContainer = true; break; }
        }
        if (oosOwnsContainer == mMediaCardPresent) return;
        mMediaCardPresent = oosOwnsContainer;
        if (oosOwnsContainer) {
            // OOS ha messo qualcosa di suo nel contenitore: molliamo il posto.
            if (mGrid != null && mGrid.getParent() == mContainer) mContainer.removeView(mGrid);
        } else {
            // Il contenitore è di nuovo libero (vuoto o solo la nostra griglia): riprendiamocelo.
            attachGrid();
            mRowDirty = true;
            refresh();
        }
    }

    private void onPanelInflated(XC_MethodHook.MethodHookParam p) {
        if (!(p.thisObject instanceof ViewGroup panel)) return;
        mContainer = panel;
        mContainer.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                mMediaPollHandler.removeCallbacks(mMediaPoll);
                mMediaPollHandler.post(mMediaPoll);
            }
            @Override public void onViewDetachedFromWindow(View v) {
                mMediaPollHandler.removeCallbacks(mMediaPoll);
            }
        });
        if (!mEnabled) return;
        mContainer.removeAllViews();
        attachGrid();
        mRowDirty = true;
        refresh();
    }

    private void attachGrid() {
        buildGridIfNeeded();
        if (mGrid.getParent() instanceof ViewGroup oldParent && oldParent != mContainer) {
            oldParent.removeView(mGrid);
        }
        if (mGrid.getParent() != mContainer) {
            mContainer.addView(mGrid);
        }
    }

    private void buildGridIfNeeded() {
        if (mGrid != null) return;
        mGrid = new LinearLayout(mContext);
        mGrid.setOrientation(LinearLayout.VERTICAL);
        mGrid.setTag(TAG_MARKER);
        int pad = dp(8);
        mGrid.setPadding(pad, pad, pad, pad);
        mGrid.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // NB: oltre ~4 widget (2 righe) il pannello media li taglia via invece di crescere, e
        // uno scroll verticale non li recupera — il gesto viene intercettato dal pannello QS
        // di OOS a un livello più alto della normale catena onInterceptTouchEvent (già
        // provato requestDisallowInterceptTouchEvent, non basta). Servirebbe agganciare le
        // classi di gestione gesture interne di OOS, stesso lavoro che ha dovuto fare OC —
        // per ora meglio tenere l'elenco a ~4 voci.
    }

    private void refresh() {
        if (mContainer == null) return;
        if (!mEnabled || mMediaCardPresent) {
            // mMediaCardPresent: OOS ha in questo momento la sua card nel contenitore, non
            // aggiungere/togliere niente qui — ci pensa checkMediaCardState() a riprenderselo
            // quando torna libero (evita di litigare con la card nativa).
            if (!mEnabled && mGrid != null && mGrid.getParent() == mContainer) {
                mContainer.removeView(mGrid);
            }
            return;
        }
        attachGrid();
        if (mRowDirty) {
            rebuildGrid();
            mRowDirty = false;
        }
    }

    /** 2 righe × TILES_PER_ROW — oltre, il pannello media taglia via il contenuto e non c'è
     *  modo di scorrere per raggiungerlo (vedi nota in buildGridIfNeeded), quindi meglio non
     *  costruire righe che tanto resterebbero invisibili. La schermata di configurazione
     *  avvisa di non superare questo numero. */
    private static final int MAX_ROWS = 2;

    private void rebuildGrid() {
        if (mGrid == null) return;
        mGrid.removeAllViews();
        LinearLayout line = null;
        int inLine = 0;
        int rows = 0;
        for (String token : mWidgets) {
            WidgetResources res = resolveWidget(token);
            if (res == null) continue; // tipo non ancora collegato — nascosto, non rotto
            if (line == null || inLine >= TILES_PER_ROW) {
                if (rows >= MAX_ROWS) break;
                line = new LinearLayout(mContext);
                line.setOrientation(LinearLayout.HORIZONTAL);
                // START, non CENTER: se una riga risultasse comunque troppo larga per il
                // contenitore, meglio tagliare solo l'ultima tile che tutte e due ai lati.
                line.setGravity(Gravity.START);
                mGrid.addView(line, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                inLine = 0;
                rows++;
            }
            line.addView(buildWidgetTile(res));
            inLine++;
        }
    }

    // ── Tile: icona a cerchio + etichetta corta sotto, come le tile QS native ──────────────

    private View buildWidgetTile(WidgetResources res) {
        Context ctx = mMaterialContext != null ? mMaterialContext : mContext;
        LinearLayout tile = new LinearLayout(ctx);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(2);
        tile.setPadding(pad, pad, pad, pad);

        int iconColor = 0xFFFFFFFF;
        int circleSize = dp(38);
        FrameLayoutCircle circle = new FrameLayoutCircle(ctx, circleSize,
                res.active ? appAccentColor() : 0x40FFFFFF);
        if (res.icon != null) {
            ImageView iv = new ImageView(ctx);
            int iconSize = dp(19);
            FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER);
            iv.setLayoutParams(ivLp);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setImageDrawable(res.icon);
            if (res.tintable) iv.setColorFilter(iconColor);
            circle.addView(iv);
        }
        LinearLayout.LayoutParams circleLp = new LinearLayout.LayoutParams(circleSize, circleSize);
        tile.addView(circle, circleLp);

        TextView tv = new TextView(ctx);
        tv.setText(res.label);
        tv.setTextColor(iconColor);
        tv.setTextSize(10);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setGravity(Gravity.CENTER_HORIZONTAL);
        int tileWidth = dp(62);
        tv.setMaxWidth(tileWidth);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tvLp.topMargin = dp(4);
        tvLp.gravity = Gravity.CENTER_HORIZONTAL;
        tile.addView(tv, tvLp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                tileWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
        tile.setLayoutParams(lp);

        tile.setOnClickListener(res.onClick);
        if (res.onLongClick != null) tile.setOnLongClickListener(res.onLongClick);
        return tile;
    }

    /** FrameLayout circolare riusabile per lo sfondo dell'icona — evita di ricreare un
     *  GradientDrawable OVAL a mano ogni volta in buildWidgetTile. */
    private static class FrameLayoutCircle extends FrameLayout {
        FrameLayoutCircle(Context ctx, int size, int color) {
            super(ctx);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(color);
            setBackground(bg);
        }
    }

    private static class WidgetResources {
        Drawable icon;
        String label = "";
        boolean tintable = true;
        boolean active = false;
        View.OnClickListener onClick = v -> {};
        View.OnLongClickListener onLongClick = null;
    }

    /** Mappa token CSV (vedi QsWidgetsFragment.AVAILABLE) su icona+etichetta+azione. Torna
     *  null per i tipi non ancora collegati (foto, dati mobili, controlli casa) — nascosti
     *  invece di mostrare un'icona rotta, stesso approccio di LockscreenWidgetsMod. */
    private WidgetResources resolveWidget(String token) {
        if (token == null || token.isEmpty()) return null;
        WidgetResources r = new WidgetResources();

        if (token.startsWith("ca")) {
            String[] parts = token.split(":", 2);
            String pkg = parts.length > 1 ? parts[1] : "";
            r.tintable = false;
            String appLabel = null;
            if (!pkg.isEmpty()) {
                try {
                    PackageManager pm = mContext.getPackageManager();
                    r.icon = pm.getApplicationIcon(pkg);
                    appLabel = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
                } catch (Throwable ignored) {}
            }
            if (r.icon == null) { r.icon = resolveDrawable("ic_widget_app_generic"); r.tintable = true; }
            r.label = appLabel != null ? appLabel : "App";
            String pkgFinal = pkg;
            r.onClick = v -> { if (mLauncher != null) mLauncher.launchApp(pkgFinal); };
            return r;
        }

        switch (token) {
            case "weather" -> {
                WeatherInfo info = WeatherCache.get();
                int packIconRes = info != null
                        ? WeatherIconPacks.resolve(mContext, mWeatherIconPack, info.weatherCode, info.isDay) : 0;
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
            case "media" -> {
                r.icon = resolveDrawable("ic_widget_media");
                r.label = "Media";
                r.onClick = v -> { if (mLauncher != null) mLauncher.launchMusicPlayer(); };
                r.onLongClick = v -> { toggleMediaPlayback(); return true; };
            }
            case "w:wifi" -> {
                if (mWifiManager == null) return null;
                boolean on = mWifiManager.isWifiEnabled();
                r.icon = resolveDrawable("ic_widget_wifi");
                r.active = on;
                r.label = on ? "Wi-Fi attivo" : "Wi-Fi disattivo";
                r.onClick = v -> toggleWifi();
            }
            case "w:calculator" -> {
                r.icon = resolveDrawable("ic_widget_calculator");
                r.label = "Calcolatrice";
                r.onClick = v -> { if (mLauncher != null) mLauncher.launchCalculator(); };
            }
            case "w:torch" -> {
                r.icon = resolveDrawable("ic_widget_torch");
                r.active = mTorchOn;
                r.label = mTorchOn ? "Torcia attivata" : "Torcia disattivata";
                r.onClick = v -> toggleTorch();
            }
            case "w:ringer" -> {
                r.icon = resolveDrawable("ic_sysui_volume");
                r.label = ringerLabel();
                r.onClick = v -> toggleRingerMode();
            }
            case "w:bt" -> {
                if (mBluetoothAdapter == null) return null;
                boolean on = mBluetoothAdapter.isEnabled();
                r.icon = resolveDrawable("ic_widget_bluetooth");
                r.active = on;
                r.label = on ? "Bluetooth attivo" : "Bluetooth disattivo";
                r.onClick = v -> toggleBluetooth();
            }
            case "w:wallet" -> {
                r.icon = resolveDrawable("ic_widget_wallet");
                r.label = "Wallet";
                r.onClick = v -> { if (mLauncher != null) mLauncher.launchWallet(); };
            }
            case "w:homecontrols" -> { // nessuno stato attivo/inattivo, come Calcolatrice/Media:
                                       // tocco apre semplicemente l'Activity giusta.
                if (mDeviceControlsTile == null) return null;
                r.icon = resolveDrawable("ic_widget_home_controls");
                r.label = "Controlli Casa";
                r.onClick = v -> launchDeviceControls();
            }
            default -> { return null; } // photo (fase 2, serve un picker immagine), w:data (tolto: non live-updatabile)
        }
        return r;
    }

    private String weatherLabel() {
        WeatherInfo info = WeatherCache.get();
        if (info == null) return "Meteo";
        return info.tempC + "°C · " + WeatherInfo.conditionText(info.weatherCode, mContext);
    }

    private String ringerLabel() {
        if (mAudioManager == null) return "Suoneria";
        return switch (mAudioManager.getRingerMode()) {
            case AudioManager.RINGER_MODE_VIBRATE -> "Suoneria: vibrazione";
            case AudioManager.RINGER_MODE_SILENT -> "Suoneria: silenziosa";
            default -> "Suoneria: normale";
        };
    }

    // ── Azioni dirette (API pubbliche, copiate da LockscreenWidgetsMod) ─────────────────────

    private void toggleTorch() {
        if (mCameraManager == null || mTorchCameraId == null) return;
        try {
            mTorchOn = !mTorchOn;
            mCameraManager.setTorchMode(mTorchCameraId, mTorchOn);
            mRowDirty = true;
            refresh();
        } catch (Throwable t) { dbg("toggleTorch failed: " + t); }
    }

    private void toggleWifi() {
        if (mWifiManager == null) return;
        try {
            mWifiManager.setWifiEnabled(!mWifiManager.isWifiEnabled());
            mRowDirty = true;
            refresh();
        } catch (Throwable t) { dbg("toggleWifi failed: " + t); }
    }

    @SuppressWarnings("deprecation")
    private void toggleBluetooth() {
        if (mBluetoothAdapter == null) return;
        try {
            if (mBluetoothAdapter.isEnabled()) mBluetoothAdapter.disable();
            else mBluetoothAdapter.enable();
            mRowDirty = true;
            refresh();
        } catch (Throwable t) { dbg("toggleBluetooth failed: " + t); }
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
            refresh();
        } catch (Throwable t) { dbg("toggleRingerMode failed: " + t); }
    }

    private void toggleMediaPlayback() {
        if (mAudioManager == null) return;
        try {
            long now = SystemClock.uptimeMillis();
            KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0);
            mAudioManager.dispatchMediaKeyEvent(down);
            mAudioManager.dispatchMediaKeyEvent(KeyEvent.changeAction(down, KeyEvent.ACTION_UP));
        } catch (Throwable t) { dbg("toggleMediaPlayback failed: " + t); }
    }

    private int appAccentColor() {
        if (Xprefs.getBoolean("DST_ACCENT1_on", false)) {
            return Xprefs.getInt("DST_ACCENT1", 0xFF908DFF) | 0xFF000000;
        }
        return it.tugaia56.obsidian.xposed.utils.AccentUtil.systemAccent();
    }

    private Drawable resolveDrawable(String name) {
        if (appContext == null) return null;
        try {
            int resId = appContext.getResources().getIdentifier(name, "drawable", appContext.getPackageName());
            return resolveDrawableById(resId);
        } catch (Throwable t) { return null; }
    }

    private Drawable resolveDrawableById(int resId) {
        if (appContext == null || resId == 0) return null;
        try {
            return ResourcesCompat.getDrawable(appContext.getResources(), resId, appContext.getTheme());
        } catch (Throwable t) { return null; }
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
        XposedBridge.log("[ Obsidian ] QsWidgetsMod: " + msg);
    }

    @Override public boolean listensTo(String packageName) { return SYSTEM_UI.equals(packageName); }
}
