package it.tugaia56.obsidian.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.weather.OpenMeteoClient;
import it.tugaia56.obsidian.weather.OpenWeatherMapClient;
import it.tugaia56.obsidian.weather.MetNorwayClient;
import it.tugaia56.obsidian.weather.YandexWeatherClient;
import it.tugaia56.obsidian.weather.WeatherCache;
import it.tugaia56.obsidian.weather.WeatherIconPacks;
import it.tugaia56.obsidian.weather.WeatherInfo;
import it.tugaia56.obsidian.xposed.XposedMods;
import it.tugaia56.obsidian.xposed.views.CurrentWeatherView;

/**
 * Meteo AOD — real hook mirroring OC's AodWeather (com.oplus.systemui.aod.aodclock.off.
 * AodClockLayout.initForAodApk). Il meteo viene scaricato da open-meteo.com (gratis, senza
 * chiave API) usando la posizione inserita manualmente dall'utente (nessuna gestione
 * permessi GPS per ora).
 */
public class AodWeather extends XposedMods {

    private static final String KEY_ENABLED         = "aod_weather_enabled";
    private static final String KEY_LAST_UPDATE     = "weather_last_update"; // condivisa con LockscreenWeather
    private static final String KEY_UPDATE_INTERVAL = "weather_update_interval";
    private static final String KEY_PROVIDER        = "weather_provider"; // 0=OWM, 1=MET Norway, 2=OpenMeteo, 3=Yandex
    private static final String KEY_OWM_KEY         = "owm_key";
    private static final String KEY_YANDEX_KEY      = "yandex_key";
    private static final String KEY_ICON_PACK       = "weather_icon_pack";
    private static final String KEY_UNITS           = "weather_units";
    private static final String KEY_SHOW_LOCATION   = "aod_weather_show_location";
    private static final String KEY_SHOW_CONDITION  = "aod_weather_show_condition";
    private static final String KEY_SHOW_HUMIDITY   = "aod_weather_show_humidity";
    private static final String KEY_SHOW_WIND       = "aod_weather_show_wind";
    private static final String KEY_TEXT_SIZE       = "aod_weather_text_size";
    private static final String KEY_IMAGE_SIZE      = "aod_weather_image_size";
    private static final String KEY_COLOR           = "aod_weather_custom_color";
    private static final String KEY_LOC_SWITCH      = "weather_custom_location_switch";
    private static final String KEY_LOC_VALUE       = "weather_custom_location_value";
    /** Posizione GPS (switch OFF) — stesse chiavi di GpsLocationHelper/LockscreenWeather. */
    private static final String KEY_GPS_LAT  = "weather_gps_lat";
    private static final String KEY_GPS_LON  = "weather_gps_lon";
    private static final String KEY_GPS_NAME = "weather_gps_name";
    private static final String KEY_CENTERED        = "aod_weather_centered";
    private static final String KEY_MARGINS_SWITCH  = "aod_weather_custom_margins";
    private static final String KEY_MARGIN_TOP      = "aod_weather_margin_top";
    private static final String KEY_MARGIN_LEFT     = "aod_weather_margin_left";

    private static final int[] INTERVAL_MINUTES = {30, 60, 180, 360};

    private boolean mEnabled, mShowLocation = true, mShowCondition = true, mShowHumidity, mShowWind;
    private boolean mMetric = true, mCentered, mMarginsOn;
    private int mTextSize = 16, mImageSize = 18, mMarginTop, mMarginLeft, mIntervalIdx = 1;
    private boolean mColorOn;
    private int mColor = Color.WHITE;
    private boolean mColorUseAccent;
    private boolean mLocOn;
    private String mLocation = "";
    private float mGpsLat, mGpsLon;
    private String mGpsName = "";
    private String mProvider = "2";
    private String mOwmKey = "";
    private String mYandexKey = "";
    private String mIconPack = WeatherIconPacks.DEFAULT;

    private ViewGroup mRootLayout;
    private LinearLayout mContainer;
    private CurrentWeatherView mWeatherView;

    public AodWeather(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mEnabled = Xprefs.getBoolean(KEY_ENABLED, false);
        mIntervalIdx = parseInt(Xprefs.getString(KEY_UPDATE_INTERVAL, "1"), 1);
        mMetric = "0".equals(Xprefs.getString(KEY_UNITS, "0"));
        mShowLocation = Xprefs.getBoolean(KEY_SHOW_LOCATION, true);
        mShowCondition = Xprefs.getBoolean(KEY_SHOW_CONDITION, true);
        mShowHumidity = Xprefs.getBoolean(KEY_SHOW_HUMIDITY, false);
        mShowWind = Xprefs.getBoolean(KEY_SHOW_WIND, false);
        mColorOn = Xprefs.getBoolean(KEY_COLOR + "_on", false);
        mColor = Xprefs.getInt(KEY_COLOR, Color.WHITE);
        mColorUseAccent = Xprefs.getBoolean(KEY_COLOR + "_use_accent", false);
        mLocOn = Xprefs.getBoolean(KEY_LOC_SWITCH, false);
        mLocation = mLocOn ? Xprefs.getString(KEY_LOC_VALUE, "") : "";
        mGpsLat = Xprefs.getFloat(KEY_GPS_LAT, 0f);
        mGpsLon = Xprefs.getFloat(KEY_GPS_LON, 0f);
        mGpsName = Xprefs.getString(KEY_GPS_NAME, "");
        mProvider = Xprefs.getString(KEY_PROVIDER, "2");
        mOwmKey = Xprefs.getString(KEY_OWM_KEY, "");
        mYandexKey = Xprefs.getString(KEY_YANDEX_KEY, "");
        mIconPack = Xprefs.getString(KEY_ICON_PACK, WeatherIconPacks.DEFAULT);
        mCentered = Xprefs.getBoolean(KEY_CENTERED, false);
        mMarginsOn = Xprefs.getBoolean(KEY_MARGINS_SWITCH, false);
        mMarginTop = Xprefs.getInt(KEY_MARGIN_TOP, 0);
        mMarginLeft = Xprefs.getInt(KEY_MARGIN_LEFT, 0);
        mTextSize = Xprefs.getInt(KEY_TEXT_SIZE, 16);
        mImageSize = Xprefs.getInt(KEY_IMAGE_SIZE, 18);
        refreshView();
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
                XposedBridge.log("[ Obsidian ] AodWeather: AodClockLayout not found: " + t2);
                return;
            }
        }

        XposedBridge.hookAllMethods(aodClockLayout, "initForAodApk", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (!mEnabled) return;
                    FrameLayout aodViewFromApk = (FrameLayout) getObjectField(param.thisObject, "mAodViewFromApk");
                    mRootLayout = null;
                    for (int i = 0; i < aodViewFromApk.getChildCount(); i++) {
                        if (aodViewFromApk.getChildAt(i) instanceof ViewGroup vg) mRootLayout = vg;
                    }
                    placeWeatherView();
                } catch (Throwable t) {
                    XposedBridge.log("[ Obsidian ] AodWeather.initForAodApk hook ERROR: " + t);
                }
            }
        });
        XposedBridge.log("[ Obsidian ] AodWeather: hooked AodClockLayout.initForAodApk");
    }

    private void placeWeatherView() {
        if (mRootLayout == null) return;
        try {
            if (mContainer == null) {
                mContainer = new LinearLayout(mContext);
                mContainer.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                mWeatherView = new CurrentWeatherView(mContext);
                mContainer.addView(mWeatherView);
            }
            try { ((ViewGroup) mContainer.getParent()).removeView(mContainer); } catch (Throwable ignored) {}
            mRootLayout.addView(mContainer);
            refreshView();
            requestWeatherIfStale();
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] AodWeather.placeWeatherView ERROR: " + t);
        }
    }

    private void refreshView() {
        if (mWeatherView == null) return;
        WeatherInfo data = WeatherCache.get();
        mWeatherView.updateIconPack(mIconPack);
        mWeatherView.updateData(data, mMetric, mShowLocation, mShowCondition, mShowHumidity, mShowWind);
        mWeatherView.updateSizes(mTextSize, mImageSize);
        mWeatherView.updateColor(mColorOn ? (mColorUseAccent ? appAccentColor() : mColor) : Color.WHITE);
        mWeatherView.setGravityCentered(mCentered);
        mWeatherView.setVisibility(mEnabled ? android.view.View.VISIBLE : android.view.View.GONE);
        if (mContainer != null) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mContainer.getLayoutParams();
            if (lp != null) {
                int density = (int) mContext.getResources().getDisplayMetrics().density;
                lp.setMargins(mMarginsOn ? mMarginLeft * density : 20 * density,
                        mMarginsOn ? mMarginTop * density : 0, 0, 0);
                mContainer.setGravity(mCentered ? Gravity.CENTER_HORIZONTAL : Gravity.START);
                mContainer.setLayoutParams(lp);
            }
        }
    }

    private void requestWeatherIfStale() {
        // Con lo switch spento (posizione automatica) mLocation è vuoto per costruzione — la
        // vera sorgente diventa mGpsLat/mGpsLon, presa dal processo Obsidian (SystemUI non ha
        // il permesso di posizione, vedi GpsLocationHelper) e passata via Xprefs. mGpsName
        // vuoto = nessun fix ancora salvato, niente da fare finché l'utente non lo richiede
        // dall'app (pulsante "Aggiorna posizione").
        boolean useGps = !mLocOn;
        if (useGps && mGpsName.isEmpty()) return;
        if (!useGps && (mLocation == null || mLocation.trim().isEmpty())) return;

        int maxAgeMinutes = (mIntervalIdx >= 0 && mIntervalIdx < INTERVAL_MINUTES.length)
                ? INTERVAL_MINUTES[mIntervalIdx] : 60;
        String cacheKey = useGps ? (mGpsLat + "," + mGpsLon + "|" + mProvider) : (mLocation + "|" + mProvider);
        if (!WeatherCache.isStale(cacheKey, maxAgeMinutes)) { refreshView(); return; }
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
                // La geocodifica (nome città -> coordinate) usa sempre OpenMeteo: è
                // gratuita e senza chiave, indipendentemente dal provider meteo scelto.
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
                    // 0=OpenWeatherMap, 1=MET Norway, 2=OpenMeteo, 3=Yandex (ordine di OC).
                    result = switch (provider) {
                        case "0" -> OpenWeatherMapClient.fetchCurrent(geo.lat, geo.lon, geo.resolvedName, owmKey);
                        case "1" -> MetNorwayClient.fetchCurrent(geo.lat, geo.lon, geo.resolvedName);
                        case "3" -> YandexWeatherClient.fetchCurrent(geo.lat, geo.lon, geo.resolvedName, yandexKey);
                        default -> OpenMeteoClient.fetchCurrent(geo.lat, geo.lon, geo.resolvedName);
                    };
                    // Se il provider scelto fallisce (o non ha chiave), torna a OpenMeteo.
                    if (result == null) {
                        result = OpenMeteoClient.fetchCurrent(geo.lat, geo.lon, geo.resolvedName);
                    }
                }
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] AodWeather fetch ERROR: " + t);
            }
            WeatherCache.finishFetch(result, cacheKey);
            if (result != null && Xprefs != null) {
                String stamp = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        .format(new java.util.Date());
                try { Xprefs.edit().putString(KEY_LAST_UPDATE, stamp).apply(); } catch (Throwable ignored) {}
            }
            new Handler(Looper.getMainLooper()).post(this::refreshViewSafe);
        }).start();
    }

    private void refreshViewSafe() {
        try { refreshView(); } catch (Throwable ignored) {}
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Throwable t) { return def; }
    }

    private int appAccentColor() {
        if (Xprefs.getBoolean("DST_ACCENT1_on", false)) {
            return Xprefs.getInt("DST_ACCENT1", 0xFF908DFF) | 0xFF000000;
        }
        return it.tugaia56.obsidian.xposed.utils.AccentUtil.systemAccent();
    }

    @Override
    public boolean listensTo(String packageName) {
        return SYSTEM_UI.equals(packageName);
    }
}
