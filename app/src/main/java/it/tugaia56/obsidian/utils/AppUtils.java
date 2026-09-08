package it.tugaia56.obsidian.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import com.topjohnwu.superuser.Shell;

import it.tugaia56.obsidian.R;

public class AppUtils {

    /** True when the app holds the MANAGE_EXTERNAL_STORAGE ("all files access") permission. */
    public static boolean hasStoragePermission() {
        return Environment.isExternalStorageManager();
    }

    /** Opens the system "all files access" settings page for this app. */
    public static void requestStoragePermission(Context context) {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + context.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void restartScope(String packageName) {
        Shell.cmd("killall " + packageName).submit();
    }

    /** Apre l'editor sfondi nativo di OxygenOS (Sfondi → Scegli da album → scheda
     *  "Profondità") — l'activity richiede il permesso di sistema
     *  com.oplus.permission.safe.SETTINGS, che un'app terza non può avere: lanciata come
     *  root (già usato per restartScope) bypassa il controllo senza bisogno di alcun
     *  permesso speciale da parte di Obsidian. */
    public static void openDepthWallpaperEditor() {
        Shell.cmd("am start -a android.intent.action.SET_WALLPAPER -n com.oplus.wallpapers/.home.HomeActivity").submit();
    }

    /** Restart SystemUI silently (no toast). */
    public static void restartSystemUI() {
        restartScope(Constants.SYSTEM_UI);
    }

    /** Restart SystemUI and show a toast feedback on the main thread. */
    public static void restartSystemUI(Context context) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context.getApplicationContext(),
                        R.string.restart_systemui_toast, Toast.LENGTH_SHORT).show());
        restartScope(Constants.SYSTEM_UI);
    }

    /**
     * Shows a "restart SystemUI to apply changes" reminder without actually restarting —
     * lets the user toggle several settings first, then restart once from the toolbar button.
     */
    public static void showRestartReminder(Context context) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context.getApplicationContext(),
                        R.string.restart_systemui_reminder, Toast.LENGTH_SHORT).show());
    }

    /**
     * Reminder for settings applied via a hook installed once in ZYGOTE at boot
     * (es. DstDialogStyle.hookDialogGlobal, "Raggio Finestre Dialogo") — a SystemUI
     * restart does NOT reload zygote-cached hooks, only a full device reboot does.
     */
    public static void showRebootReminder(Context context) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context.getApplicationContext(),
                        R.string.reboot_device_reminder, Toast.LENGTH_LONG).show());
    }

    /** Percorsi APK/split del pacchetto — servono ad aapt2 come riferimento risorse
     *  aggiuntivo (-I) quando compila un overlay contro un target reale (es. com.android.settings). */
    public static String[] getSplitLocations(String packageName) {
        try {
            android.content.pm.ApplicationInfo info = it.tugaia56.obsidian.Obsidian.getAppContext()
                    .getPackageManager().getApplicationInfo(packageName, 0);
            String[] splitLocations = info.splitSourceDirs;
            if (splitLocations == null) {
                splitLocations = new String[]{info.sourceDir};
            }
            return splitLocations;
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
        }
        return new String[0];
    }
}
