package it.tugaia56.obsidian.utils.overlay;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.overlay.compiler.GenericAppThemeCompiler;
import it.tugaia56.obsidian.xposed.hooks.systemui.DstSignalIconStyle;
import it.tugaia56.obsidian.xposed.hooks.systemui.DstWifiIconStyle;

/**
 * Stili delle icone Wi-Fi e segnale mobile come overlay di SystemUI (WIFI1 / SIG1): al posto
 * della sostituzione con Xposed, cosi le icone si comportano come quelle di sistema (colore della
 * barra incluso). La prima volta serve un riavvio perche il sistema conosca il nuovo overlay.
 */
public final class SystemUiIconOverlays {
    public static final String SYSTEMUI = "com.android.systemui";
    public static final String WIFI = "WIFI1";
    public static final String SIGNAL = "SIG1";

    private SystemUiIconOverlays() {}

    private static String nameFor(String[] keys, String[] names, String key) {
        if (key == null) return null;
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(key)) return names[i];
        return null;
    }

    /** Compila e attiva gli stili indicati; una chiave null lascia quell'icona com'e. */
    public static void apply(final Context ctx, final String wifiKey, final String signalKey) {
        final String wifi = nameFor(DstWifiIconStyle.getPresetKeys(), DstWifiIconStyle.getPresetNames(), wifiKey);
        final String sig = nameFor(DstSignalIconStyle.PRESET_KEYS, DstSignalIconStyle.PRESET_NAMES, signalKey);
        if (wifi == null && sig == null) return;
        Toast.makeText(ctx, R.string.app_themes_working, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean okWifi = true, okSig = true;
            try {
                GenericAppThemeCompiler.beginBatch();
                if (wifi != null) okWifi = !GenericAppThemeCompiler.buildNamedInBatch(SYSTEMUI, "WIFI_" + wifi, WIFI);
                if (sig != null) okSig = !GenericAppThemeCompiler.buildNamedInBatch(SYSTEMUI, "SIG_" + sig, SIGNAL);
            } catch (Throwable t) {
                okWifi = false;
                okSig = false;
            } finally {
                if (wifi != null) GenericAppThemeCompiler.finishNamed(WIFI, okWifi);
                if (sig != null) GenericAppThemeCompiler.finishNamed(SIGNAL, okSig);
            }
            if (wifi != null && okWifi) ObsidianPrefs.putBoolean("WIFI_STYLE_OVERLAY", true);
            if (sig != null && okSig) ObsidianPrefs.putBoolean("SIGNAL_STYLE_OVERLAY", true);
            final boolean ok = okWifi && okSig;
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(ctx,
                    ok ? ctx.getString(R.string.toast_applied) : ctx.getString(R.string.toast_error),
                    Toast.LENGTH_LONG).show());
        }).start();
    }

    public static void removeWifi() {
        ObsidianPrefs.putBoolean("WIFI_STYLE_OVERLAY", false);
        new Thread(() -> GenericAppThemeCompiler.removeNamed(WIFI)).start();
    }

    public static void removeSignal() {
        ObsidianPrefs.putBoolean("SIGNAL_STYLE_OVERLAY", false);
        new Thread(() -> GenericAppThemeCompiler.removeNamed(SIGNAL)).start();
    }
}
