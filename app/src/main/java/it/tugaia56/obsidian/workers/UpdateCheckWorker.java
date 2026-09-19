package it.tugaia56.obsidian.workers;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.utils.UpdateChecker;
import it.tugaia56.obsidian.utils.UpdateScheduler;

/**
 * Controllo periodico in background (enqueue/cancel in UpdateScheduler, in base agli switch
 * "Aggiornamento automatico"/"Solo Wi-Fi" di SettingsUpdateFragment). Stessa logica del
 * pulsante manuale (UpdateChecker), ma qui il risultato va in una notifica invece che in un
 * dialog/toast — nessuna Activity/Fragment garantita in vita quando questo Worker gira.
 */
public class UpdateCheckWorker extends Worker {

    private static final int NOTIF_ID = 501;

    public UpdateCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull @Override
    public Result doWork() {
        try {
            UpdateChecker.Result result = UpdateChecker.check();
            if (result.newer && result.downloadUrl != null) {
                notifyNewVersion(result.version);
            }
            return Result.success();
        } catch (Throwable t) {
            // Errore di rete/parsing — non è un fallimento del Worker in sé (WorkManager
            // farebbe retry con backoff), semplicemente niente da notificare questo giro:
            // il prossimo controllo periodico riprova da solo.
            return Result.success();
        }
    }

    private void notifyNewVersion(String version) {
        Context ctx = getApplicationContext();
        UpdateScheduler.ensureNotificationChannel(ctx);

        Intent openApp = new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(ctx, 0, openApp,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, UpdateScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(ctx.getString(R.string.update_available_title))
                .setContentText(ctx.getString(R.string.update_available_body, version))
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        try {
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID, builder.build());
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS non concesso (Android 13+, utente l'ha rifiutato) — niente
            // notifica, il controllo comunque avviene, l'utente vedrà l'update aprendo l'app.
        }
    }
}
