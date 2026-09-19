package it.tugaia56.obsidian.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import it.tugaia56.obsidian.workers.UpdateCheckWorker;

/**
 * Avvia/ferma il controllo aggiornamenti periodico (WorkManager) in base agli switch
 * "Aggiornamento automatico"/"Solo Wi-Fi" in SettingsUpdateFragment. Richiamato da lì ad ogni
 * cambio switch, e da Obsidian.onCreate() ad ogni avvio del processo per restare coerente con
 * i pref correnti (WorkManager persiste già da solo il lavoro periodico tra i riavvii, questo
 * è solo un allineamento difensivo, es. se i pref sono stati cambiati da adb a processo spento).
 */
public class UpdateScheduler {

    public static final String CHANNEL_ID = "obsidian_updates";
    private static final String WORK_NAME = "obsidian_update_check";
    private static final String KEY_AUTO_CHECK = "update_auto_check";
    private static final String KEY_WIFI_ONLY  = "update_wifi_only";

    /** Legge i pref correnti e allinea il lavoro periodico di conseguenza — enqueue se
     *  l'auto-check è attivo (aggiornando i vincoli se "Solo Wi-Fi" è cambiato), cancel se è
     *  spento. Sicuro da richiamare quante volte si vuole (KEEP/REPLACE via policy corretta). */
    public static void reschedule(Context context) {
        boolean autoCheck = ObsidianPrefs.getBoolean(KEY_AUTO_CHECK, false);
        if (!autoCheck) {
            cancel(context);
            return;
        }
        boolean wifiOnly = ObsidianPrefs.getBoolean(KEY_WIFI_ONLY, false);
        enqueue(context, wifiOnly);
    }

    private static void enqueue(Context context, boolean wifiOnly) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(wifiOnly ? NetworkType.UNMETERED : NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                UpdateCheckWorker.class, 12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    private static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }

    /** Idempotente — createNotificationChannel() non fa nulla se il canale esiste già con gli
     *  stessi parametri, sicuro da richiamare ad ogni avvio o prima di ogni notifica. */
    public static void ensureNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Aggiornamenti Oxydian", NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(channel);
    }
}
