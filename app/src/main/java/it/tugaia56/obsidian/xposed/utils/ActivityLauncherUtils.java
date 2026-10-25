package it.tugaia56.obsidian.xposed.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.MediaStore;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import it.tugaia56.obsidian.BuildConfig;
import it.tugaia56.obsidian.ui.activity.MainActivity;

/**
 * Porting semplificato di OC's ActivityLauncherUtils — lancia un'Intent "dismissando" la
 * keyguard tramite l'ActivityStarter reale di SystemUI (un oggetto interno non pubblico,
 * per questo qui è tipizzato Object e chiamato via reflection). Senza questo, avviare
 * un'app da sopra la Schermata di Blocco o non parte o lascia la keyguard a metà.
 */
public class ActivityLauncherUtils {

    private static final String[] CALCULATOR_PACKAGES = {
            "com.oneplus.calculator", "com.coloros.calculator",
            "com.android.calculator2", "com.google.android.calculator",
    };
    private static final String[] CLOCK_PACKAGES = {
            "com.oneplus.deskclock", "com.coloros.alarmclock", "com.oplus.alarmclock",
            "com.google.android.deskclock", "com.android.deskclock",
    };
    private static final String WALLET_PACKAGE = "com.google.android.apps.walletnfcrel";
    private static final String[] WEATHER_PACKAGES = {
            "net.oneplus.weather", "com.coloros.weather2", "com.oplus.weather",
            "com.wunderground.android.weather", "com.google.android.apps.weather", "com.weather.Weather",
    };

    private final Context mContext;
    private final Object mActivityStarter;
    private final PackageManager mPackageManager;

    public ActivityLauncherUtils(Context context, Object activityStarter) {
        mContext = context;
        mActivityStarter = activityStarter;
        mPackageManager = context.getPackageManager();
    }

    public void launchApp(String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        Intent intent = mPackageManager.getLaunchIntentForPackage(packageName);
        if (intent == null) return;
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        launch(intent);
    }

    public void launchCamera() {
        launch(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE));
    }

    /** Apre l'app Orologio OnePlus direttamente su una scheda (stesso trucco del tocco sull'orologio della barra di stato):
     *  azione "enter_and_open_timer" + extra "clock_tab_index" (0=Sveglia, 1=Orologio mondiale, 2=Cronometro, 3=Timer).
     *  Se l'app non c'e', ripiega sulle richieste standard o sull'app Orologio del sistema. */
    private void launchClockTab(int tab) {
        if (mPackageManager.getLaunchIntentForPackage(CLOCK_PACKAGES[0]) != null) {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(CLOCK_PACKAGES[0], "com.oplus.alarmclock.AlarmClock"));
            intent.setAction("com.oplus.alarmclock.alarmclock.enter_and_open_timer");
            intent.putExtra("clock_tab_index", tab);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            launch(intent);
            return;
        }
        if (tab == 3) {
            Intent intent = new Intent("android.intent.action.SHOW_TIMERS");
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            launch(intent);
            return;
        }
        for (String pkg : CLOCK_PACKAGES) {
            Intent found = mPackageManager.getLaunchIntentForPackage(pkg);
            if (found != null) {
                found.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                launch(found);
                return;
            }
        }
    }

    /** Apre il Timer dell'app Orologio. */
    public void launchTimer() {
        launchClockTab(3);
    }

    /** Apre il Cronometro dell'app Orologio. */
    public void launchStopwatch() {
        launchClockTab(2);
    }

    /** Sveglia: apre l'app scelta dall'utente, oppure la lista delle sveglie dell'app Orologio. */
    public void launchAlarm(String packageName) {
        if (packageName != null && !packageName.isEmpty()) {
            launchApp(packageName);
            return;
        }
        Intent intent = new Intent("android.intent.action.SHOW_ALARMS");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        launch(intent);
    }

    public void launchCalculator() {
        Intent intent = null;
        for (String pkg : CALCULATOR_PACKAGES) {
            Intent found = mPackageManager.getLaunchIntentForPackage(pkg);
            if (found != null) { intent = found; break; }
        }
        if (intent == null) {
            intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_APP_CALCULATOR);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        launch(intent);
    }

    /** Apre il lettore musicale predefinito del sistema (CATEGORY_APP_MUSIC — risolta da
     *  Android/launcher, non richiede sapere quale app è installata). */
    public void launchMusicPlayer() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_APP_MUSIC);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        launch(intent);
    }

    public void launchWallet() {
        Intent intent = mPackageManager.getLaunchIntentForPackage(WALLET_PACKAGE);
        if (intent == null) return;
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        launch(intent);
    }

    /** Apre l'app Meteo del sistema (non esiste una categoria Intent standard tipo
     *  CATEGORY_APP_MUSIC/CALCULATOR — provo i pacchetti più comuni su OOS/ColorOS e
     *  Android in generale); se nessuno è installato apre Obsidian stessa come ripiego,
     *  dato che i dati meteo del widget vengono comunque da lì, non da un'app esterna. */
    public void launchWeatherApp() {
        for (String pkg : WEATHER_PACKAGES) {
            Intent found = mPackageManager.getLaunchIntentForPackage(pkg);
            if (found != null) {
                found.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                launch(found);
                return;
            }
        }
        launchWeatherSettings();
    }

    /** Apre Obsidian stessa (schermata Meteo SdB) — usato come ripiego se non c'è un'app
     *  Meteo installata riconosciuta. */
    public void launchWeatherSettings() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(BuildConfig.APPLICATION_ID, MainActivity.class.getName()));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        launch(intent);
    }

    private void launch(Intent intent) {
        if (mActivityStarter == null) {
            XposedBridge.log("[ Obsidian ] ActivityLauncherUtils: activityStarter is null, cannot launch " + intent);
            return;
        }
        try {
            XposedHelpers.callMethod(mActivityStarter, "postStartActivityDismissingKeyguard", intent, 0);
        } catch (Throwable t) {
            try {
                XposedHelpers.callMethod(mActivityStarter, "startActivity", intent, false);
            } catch (Throwable t2) {
                XposedBridge.log("[ Obsidian ] ActivityLauncherUtils: launch failed for " + intent + ": " + t2);
            }
        }
    }
}
