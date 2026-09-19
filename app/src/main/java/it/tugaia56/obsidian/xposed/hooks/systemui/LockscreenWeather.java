package it.tugaia56.obsidian.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.weather.MetNorwayClient;
import it.tugaia56.obsidian.weather.OpenMeteoClient;
import it.tugaia56.obsidian.weather.OpenWeatherMapClient;
import it.tugaia56.obsidian.weather.WeatherCache;
import it.tugaia56.obsidian.weather.WeatherIconPacks;
import it.tugaia56.obsidian.weather.WeatherInfo;
import it.tugaia56.obsidian.weather.YandexWeatherClient;
import it.tugaia56.obsidian.xposed.XposedMods;
import it.tugaia56.obsidian.xposed.utils.ViewHelper;
import it.tugaia56.obsidian.xposed.views.CurrentWeatherView;

/**
 * Meteo Schermata di Blocco (+ AOD "schermo intero") — reale, stesso punto di aggancio di
 * LockscreenClockMod (OplusKeyguardStyleBaseClock.getView / OplusKeyguardStyleClock.
 * onUiStateChanged), confermato funzionante su questo device. A differenza del vecchio
 * AodClockLayout.initForAodApk (che con "AOD a schermo intero" non viene mai chiamato), questo
 * container è condiviso da Schermata di Blocco E AOD — quindi lo stesso widget può comparire
 * in entrambi, con impostazioni indipendenti (chiavi "weather_*" per SdB, "aod_weather_*" per
 * AOD, condividendo provider/posizione/pacchetto icone che sono per definizione un'unica cosa).
 * Indipendente da "Orologio Personalizzato" — si aggiunge come figlio in più nello stesso
 * contenitore, non tocca l'orologio (stock o personalizzato che sia).
 */
public class LockscreenWeather extends XposedMods {

    private static final int UI_STATE_LS  = 2;
    private static final int UI_STATE_AOD = 3;

    // Chiavi condivise (stesse di AodWeatherFragment/LockscreenWeatherFragment)
    private static final String KEY_UPDATE_INTERVAL = "weather_update_interval";
    private static final String KEY_PROVIDER        = "weather_provider";
    private static final String KEY_OWM_KEY         = "owm_key";
    private static final String KEY_YANDEX_KEY      = "yandex_key";
    private static final String KEY_UNITS           = "weather_units";
    private static final String KEY_LOC_SWITCH      = "weather_custom_location_switch";
    private static final String KEY_LOC_VALUE       = "weather_custom_location_value";
    private static final String KEY_ICON_PACK       = "weather_icon_pack";
    /** Posizione GPS (switch OFF) — stesse chiavi di GpsLocationHelper, scritte dal processo
     *  Obsidian (unico che ha il permesso di posizione) e lette qui via Xprefs. */
    private static final String KEY_GPS_LAT  = "weather_gps_lat";
    private static final String KEY_GPS_LON  = "weather_gps_lon";
    private static final String KEY_GPS_NAME = "weather_gps_name";

    // Chiavi Schermata di Blocco
    private static final String LS_KEY_ENABLED        = "lockscreen_weather_enabled";
    private static final String KEY_LAST_UPDATE       = "weather_last_update"; // condivisa con AodWeather
    private static final String LS_KEY_SHOW_LOCATION  = "weather_show_location";
    private static final String LS_KEY_SHOW_CONDITION = "weather_show_condition";
    private static final String LS_KEY_SHOW_HUMIDITY  = "weather_show_humidity";
    private static final String LS_KEY_SHOW_WIND      = "weather_show_wind";
    private static final String LS_KEY_TEXT_SIZE      = "weather_text_size";
    private static final String LS_KEY_IMAGE_SIZE     = "weather_image_size";
    private static final String LS_KEY_COLOR          = "weather_custom_color";
    private static final String LS_KEY_CENTERED       = "weather_centered";
    private static final String LS_KEY_MARGINS_SWITCH = "weather_custom_margins";
    private static final String LS_KEY_MARGIN_TOP     = "weather_margin_top";
    private static final String LS_KEY_MARGIN_LEFT    = "weather_margin_left";
    private static final String LS_KEY_BACKGROUND     = "weather_background";

    // Chiavi AOD (stesse di AodWeatherFragment/AodWeather)
    private static final String AOD_KEY_ENABLED        = "aod_weather_enabled";
    private static final String AOD_KEY_SHOW_LOCATION  = "aod_weather_show_location";
    private static final String AOD_KEY_SHOW_CONDITION = "aod_weather_show_condition";
    private static final String AOD_KEY_SHOW_HUMIDITY  = "aod_weather_show_humidity";
    private static final String AOD_KEY_SHOW_WIND      = "aod_weather_show_wind";
    private static final String AOD_KEY_TEXT_SIZE      = "aod_weather_text_size";
    private static final String AOD_KEY_IMAGE_SIZE     = "aod_weather_image_size";
    private static final String AOD_KEY_COLOR          = "aod_weather_custom_color";
    private static final String AOD_KEY_CENTERED       = "aod_weather_centered";
    private static final String AOD_KEY_MARGINS_SWITCH = "aod_weather_custom_margins";
    private static final String AOD_KEY_MARGIN_TOP     = "aod_weather_margin_top";
    private static final String AOD_KEY_MARGIN_LEFT    = "aod_weather_margin_left";

    private static final String TAG_MARKER = "obsidian_lockscreen_weather";
    private static final int[] INTERVAL_MINUTES = {30, 60, 180, 360};

    // Condivisi
    private boolean mLocOn;
    private String mLocation = "";
    private float mGpsLat, mGpsLon;
    private String mGpsName = "";
    private String mProvider = "2";
    private String mOwmKey = "", mYandexKey = "";
    private String mIconPack = WeatherIconPacks.DEFAULT;
    private int mIntervalIdx = 1;
    private boolean mMetric = true;

    // Schermata di Blocco
    private boolean mLsEnabled, mLsShowLocation = true, mLsShowCondition = true, mLsShowHumidity, mLsShowWind;
    private boolean mLsCentered, mLsMarginsOn;
    private int mLsTextSize = 16, mLsImageSize = 18, mLsMarginTop, mLsMarginLeft;
    private boolean mLsColorOn;
    private int mLsColor = Color.WHITE;
    private boolean mLsColorUseAccent;
    private int mLsBackground;

    // AOD
    private boolean mAodEnabled, mAodShowLocation = true, mAodShowCondition = true, mAodShowHumidity, mAodShowWind;
    private boolean mAodCentered, mAodMarginsOn;
    private int mAodTextSize = 16, mAodImageSize = 18, mAodMarginTop, mAodMarginLeft;
    private boolean mAodColorOn;
    private int mAodColor = Color.WHITE;
    private boolean mAodColorUseAccent;

    private int mUiState = UI_STATE_LS;
    private ViewGroup mContainer;
    private LinearLayout mWidgetContainer;
    private CurrentWeatherView mWeatherView;


    public LockscreenWeather(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... key) {
        if (Xprefs == null) return;

        mIntervalIdx = parseInt(Xprefs.getString(KEY_UPDATE_INTERVAL, "1"), 1);
        mMetric      = "0".equals(Xprefs.getString(KEY_UNITS, "0"));
        mProvider    = Xprefs.getString(KEY_PROVIDER, "2");
        mOwmKey      = Xprefs.getString(KEY_OWM_KEY, "");
        mYandexKey   = Xprefs.getString(KEY_YANDEX_KEY, "");
        mIconPack    = Xprefs.getString(KEY_ICON_PACK, WeatherIconPacks.DEFAULT);
        mLocOn       = Xprefs.getBoolean(KEY_LOC_SWITCH, false);
        mLocation    = mLocOn ? Xprefs.getString(KEY_LOC_VALUE, "") : "";
        mGpsLat      = Xprefs.getFloat(KEY_GPS_LAT, 0f);
        mGpsLon      = Xprefs.getFloat(KEY_GPS_LON, 0f);
        mGpsName     = Xprefs.getString(KEY_GPS_NAME, "");

        mLsEnabled       = Xprefs.getBoolean(LS_KEY_ENABLED, false);
        mLsShowLocation  = Xprefs.getBoolean(LS_KEY_SHOW_LOCATION, true);
        mLsShowCondition = Xprefs.getBoolean(LS_KEY_SHOW_CONDITION, true);
        mLsShowHumidity  = Xprefs.getBoolean(LS_KEY_SHOW_HUMIDITY, false);
        mLsShowWind      = Xprefs.getBoolean(LS_KEY_SHOW_WIND, false);
        mLsTextSize      = Xprefs.getInt(LS_KEY_TEXT_SIZE, 16);
        mLsImageSize     = Xprefs.getInt(LS_KEY_IMAGE_SIZE, 18);
        mLsColorOn       = Xprefs.getBoolean(LS_KEY_COLOR + "_on", false);
        mLsColor         = Xprefs.getInt(LS_KEY_COLOR, Color.WHITE);
        mLsColorUseAccent = Xprefs.getBoolean(LS_KEY_COLOR + "_use_accent", false);
        mLsCentered      = Xprefs.getBoolean(LS_KEY_CENTERED, false);
        mLsMarginsOn     = Xprefs.getBoolean(LS_KEY_MARGINS_SWITCH, false);
        mLsMarginTop     = Xprefs.getInt(LS_KEY_MARGIN_TOP, 0);
        mLsMarginLeft    = Xprefs.getInt(LS_KEY_MARGIN_LEFT, 0);
        mLsBackground    = parseInt(Xprefs.getString(LS_KEY_BACKGROUND, "0"), 0);

        mAodEnabled       = Xprefs.getBoolean(AOD_KEY_ENABLED, false);
        mAodShowLocation  = Xprefs.getBoolean(AOD_KEY_SHOW_LOCATION, true);
        mAodShowCondition = Xprefs.getBoolean(AOD_KEY_SHOW_CONDITION, true);
        mAodShowHumidity  = Xprefs.getBoolean(AOD_KEY_SHOW_HUMIDITY, false);
        mAodShowWind      = Xprefs.getBoolean(AOD_KEY_SHOW_WIND, false);
        mAodTextSize      = Xprefs.getInt(AOD_KEY_TEXT_SIZE, 16);
        mAodImageSize     = Xprefs.getInt(AOD_KEY_IMAGE_SIZE, 18);
        mAodColorOn       = Xprefs.getBoolean(AOD_KEY_COLOR + "_on", false);
        mAodColor         = Xprefs.getInt(AOD_KEY_COLOR, Color.WHITE);
        mAodColorUseAccent = Xprefs.getBoolean(AOD_KEY_COLOR + "_use_accent", false);
        mAodCentered      = Xprefs.getBoolean(AOD_KEY_CENTERED, false);
        mAodMarginsOn     = Xprefs.getBoolean(AOD_KEY_MARGINS_SWITCH, false);
        mAodMarginTop     = Xprefs.getInt(AOD_KEY_MARGIN_TOP, 0);
        mAodMarginLeft    = Xprefs.getInt(AOD_KEY_MARGIN_LEFT, 0);

        refreshForCurrentState();
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!SYSTEM_UI.equals(lp.packageName)) return;
        if (Build.VERSION.SDK_INT < 35) {
            XposedBridge.log("[ Obsidian ] LockscreenWeather: SDK " + Build.VERSION.SDK_INT + " < 35, skipped");
            return;
        }

        Class<?> baseClockClass = tryFindClass(lp,
                "com.oplus.keyguard.OplusKeyguardStyleBaseClock",
                "com.oplus.keyguard.comm.OplusKeyguardStyleWrapper");
        if (baseClockClass == null) {
            XposedBridge.log("[ Obsidian ] LockscreenWeather: OplusKeyguardStyleBaseClock/Wrapper not found");
            return;
        }

        hookAllMethods(baseClockClass, "getView", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                try { onGetView(p); } catch (Throwable t) {
                    XposedBridge.log("[ Obsidian ] LockscreenWeather.getView ERROR: " + t);
                }
            }
        });

        Class<?> uiStateClass = tryFindClass(lp, "com.oplus.keyguard.OplusKeyguardStyleClock");
        if (uiStateClass != null) {
            hookAllMethods(uiStateClass, "onUiStateChanged", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    if (p.args.length == 0 || !(p.args[0] instanceof Integer)) return;
                    mUiState = (Integer) p.args[0];
                    refreshForCurrentState();
                }
            });
        }
        XposedBridge.log("[ Obsidian ] LockscreenWeather: hooked OplusKeyguardStyleBaseClock.getView");
    }

    private void onGetView(XC_MethodHook.MethodHookParam p) {
        if (p.args.length == 0 || !(p.args[0] instanceof Integer)) return;
        if ((Integer) p.args[0] != 1) return; // 1 == slot orologio/AOD, come LockscreenClockMod
        if (!(p.getResult() instanceof ViewGroup container)) return;
        mContainer = container;
        applyForState(mUiState);
    }

    private void refreshForCurrentState() {
        if (mContainer != null) applyForState(mUiState);
    }

    private boolean isEnabledForState(int state) {
        return state == UI_STATE_AOD ? mAodEnabled : mLsEnabled;
    }

    /** Cerca sempre il widget nel contenitore CORRENTE (come fa LockscreenClockMod) invece di
     *  fidarsi di un riferimento cache — con "AOD a schermo intero" il plugin può restituire
     *  un contenitore nuovo ad ogni getView(), e un riferimento vecchio resterebbe agganciato
     *  a una view ormai staccata dalla finestra (invisibile). */
    private void applyForState(int state) {
        if (mContainer == null) return;
        boolean enabled = isEnabledForState(state);

        Object existing = ViewHelper.findViewWithTag(mContainer, TAG_MARKER);
        if (!enabled) {
            if (existing instanceof ViewGroup existingView) {
                ViewGroup parent = (ViewGroup) existingView.getParent();
                if (parent != null) parent.removeView(existingView);
            }
            mWidgetContainer = null;
            mWeatherView = null;
            return;
        }

        if (existing instanceof LinearLayout existingContainer) {
            mWidgetContainer = existingContainer;
            mWeatherView = mWidgetContainer.getChildCount() > 0
                    && mWidgetContainer.getChildAt(0) instanceof CurrentWeatherView cwv ? cwv : null;
        } else {
            mWidgetContainer = new LinearLayout(mContext);
            mWidgetContainer.setTag(TAG_MARKER);
            mWeatherView = new CurrentWeatherView(mContext);
            mWidgetContainer.addView(mWeatherView);
            mContainer.addView(mWidgetContainer);
        }

        refreshView(state);
        requestWeatherIfStale();
    }

    private void refreshView(int state) {
        if (mWeatherView == null) return;
        boolean isAod = state == UI_STATE_AOD;
        boolean showLocation  = isAod ? mAodShowLocation  : mLsShowLocation;
        boolean showCondition = isAod ? mAodShowCondition : mLsShowCondition;
        boolean showHumidity  = isAod ? mAodShowHumidity  : mLsShowHumidity;
        boolean showWind      = isAod ? mAodShowWind      : mLsShowWind;
        int     textSize      = isAod ? mAodTextSize      : mLsTextSize;
        int     imageSize     = isAod ? mAodImageSize     : mLsImageSize;
        boolean colorOn       = isAod ? mAodColorOn       : mLsColorOn;
        boolean colorUseAccent = isAod ? mAodColorUseAccent : mLsColorUseAccent;
        int     color         = colorUseAccent ? appAccentColor() : (isAod ? mAodColor : mLsColor);
        boolean centered      = isAod ? mAodCentered      : mLsCentered;
        boolean marginsOn     = isAod ? mAodMarginsOn     : mLsMarginsOn;
        int     marginTop     = isAod ? mAodMarginTop     : mLsMarginTop;
        int     marginLeft    = isAod ? mAodMarginLeft    : mLsMarginLeft;

        WeatherInfo data = WeatherCache.get();
        mWeatherView.updateIconPack(mIconPack);
        mWeatherView.updateData(data, mMetric, showLocation, showCondition, showHumidity, showWind);
        mWeatherView.updateSizes(textSize, imageSize);
        mWeatherView.updateColor(colorOn ? color : Color.WHITE);
        mWeatherView.setGravityCentered(centered);
        // Solo SdB — l'AOD non ha questa impostazione, resta senza sfondo come finora.
        mWeatherView.updateWeatherBg(isAod ? 0 : mLsBackground, appAccentColor(), appAccent2Color());

        if (mWidgetContainer != null) {
            mWidgetContainer.setGravity(centered ? Gravity.CENTER_HORIZONTAL : Gravity.START);
            ViewGroup.LayoutParams lpRaw = mWidgetContainer.getLayoutParams();
            if (lpRaw instanceof ViewGroup.MarginLayoutParams mlp) {
                int density = (int) mContext.getResources().getDisplayMetrics().density;
                // Offset di base: "0 dp" nello slider deve partire sotto il blocco orologio+data,
                // non attaccato al bordo — lo slider aggiunge margine IN PIÙ da questo punto.
                // 2026-09-18: stessa tabella-fix di LockscreenWidgetsMod.applyMargin() — se lo
                // stile ha un valore calibrato SOSTITUISCE l'intero calcolo verticale (base +
                // slider "Margine superiore"), non si somma — il valore calibrato incorpora già
                // quello che oggi serve impostare a mano nello slider. Margine orizzontale
                // (marginLeft) invariato, resta sotto controllo dell'utente in ogni caso.
                // Base di sicurezza = metà schermo (proposta dall'utente) quando lo stile non ha
                // una calibrazione specifica — sotto QUALSIASI orologio senza doverli testare
                // tutti e 61, invece del vecchio 150dp fisso indovinato.
                Integer calibratedTop = LockscreenClockMod.getCalibratedWeatherMarginDp(LockscreenClockMod.getCurrentStyle());
                int safeBasePx = mContext.getResources().getDisplayMetrics().heightPixels / 2 - 200 * density;
                int topPx = calibratedTop != null
                        ? calibratedTop * density
                        : safeBasePx + (marginsOn ? marginTop * density : 0);
                mlp.setMargins(marginsOn ? marginLeft * density : 20 * density, topPx, 0, 0);
                mWidgetContainer.setLayoutParams(mlp);
            }
        }
    }

    /** Stessa logica di ObsidianTheme.accentColor() (app UI), ma letta via Xprefs dato che
     *  questo hook gira nel processo di SystemUI, non in quello dell'app. Default = viola
     *  Obsidian (0xFF908DFF), non l'accento Material You del dispositivo — quello segue il
     *  wallpaper e può risultare molto più scuro/diverso da quello visto nel resto dell'app. */
    private int appAccentColor() {
        if (Xprefs.getBoolean("DST_ACCENT1_on", false)) {
            return Xprefs.getInt("DST_ACCENT1", 0xFF908DFF) | 0xFF000000;
        }
        return 0xFF908DFF;
    }

    /** Come appAccentColor(), ma per l'Accento Secondario (DST_ACCENT2). */
    private int appAccent2Color() {
        if (Xprefs.getBoolean("DST_ACCENT2_on", false)) {
            return Xprefs.getInt("DST_ACCENT2", 0xFF3700B3) | 0xFF000000;
        }
        return 0xFF3700B3;
    }

    private void requestWeatherIfStale() {
        // Con lo switch spento (posizione automatica) mLocation è vuoto per costruzione — la
        // vera sorgente diventa mGpsLat/mGpsLon, presa dal processo Obsidian (SystemUI non ha
        // il permesso di posizione, vedi GpsLocationHelper) e passata via Xprefs. mGpsName
        // vuoto = nessun fix ancora salvato, niente da fare finché l'utente non lo richiede
        // dall'app (pulsante "Aggiorna posizione" in LockscreenWeatherFragment/AodWeatherFragment).
        boolean useGps = !mLocOn;
        if (useGps && mGpsName.isEmpty()) return;
        if (!useGps && (mLocation == null || mLocation.trim().isEmpty())) return;

        int maxAgeMinutes = (mIntervalIdx >= 0 && mIntervalIdx < INTERVAL_MINUTES.length)
                ? INTERVAL_MINUTES[mIntervalIdx] : 60;
        String cacheKey = useGps ? (mGpsLat + "," + mGpsLon + "|" + mProvider) : (mLocation + "|" + mProvider);
        if (!WeatherCache.isStale(cacheKey, maxAgeMinutes)) { refreshView(mUiState); return; }
        if (!WeatherCache.tryStartFetch()) return;

        final String city = mLocation;
        final double gpsLat = mGpsLat, gpsLon = mGpsLon;
        final String gpsName = mGpsName;
        final String provider = mProvider;
        final String owmKey = mOwmKey;
        final String yandexKey = mYandexKey;
        new Thread(() -> {
            WeatherInfo result = null;
            try {
                OpenMeteoClient.GeoResult geo;
                if (useGps) {
                    geo = new OpenMeteoClient.GeoResult();
                    geo.lat = gpsLat;
                    geo.lon = gpsLon;
                    geo.resolvedName = gpsName;
                } else {
                    geo = OpenMeteoClient.geocode(city);
                }
                if (geo != null) {
                    result = switch (provider) {
                        case "0" -> OpenWeatherMapClient.fetchCurrent(geo.lat, geo.lon, geo.resolvedName, owmKey);
                        case "1" -> MetNorwayClient.fetchCurrent(geo.lat, geo.lon, geo.resolvedName);
                        case "3" -> YandexWeatherClient.fetchCurrent(geo.lat, geo.lon, geo.resolvedName, yandexKey);
                        default -> OpenMeteoClient.fetchCurrent(geo.lat, geo.lon, geo.resolvedName);
                    };
                    if (result == null) {
                        result = OpenMeteoClient.fetchCurrent(geo.lat, geo.lon, geo.resolvedName);
                    }
                }
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] LockscreenWeather fetch ERROR: " + t);
            }
            WeatherCache.finishFetch(result, cacheKey);
            if (result != null && Xprefs != null) {
                String stamp = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        .format(new java.util.Date());
                try { Xprefs.edit().putString(KEY_LAST_UPDATE, stamp).apply(); } catch (Throwable ignored) {}
            }
            new Handler(Looper.getMainLooper()).post(() -> refreshView(mUiState));
        }).start();
    }

    private Class<?> tryFindClass(XC_LoadPackage.LoadPackageParam lp, String... names) {
        for (String name : names) {
            try { return findClass(name, lp.classLoader); } catch (Throwable ignored) {}
        }
        return null;
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Throwable t) { return def; }
    }

    @Override
    public boolean listensTo(String packageName) {
        return SYSTEM_UI.equals(packageName);
    }
}
