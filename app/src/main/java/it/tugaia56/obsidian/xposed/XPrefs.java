package it.tugaia56.obsidian.xposed;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import de.robv.android.xposed.XposedBridge;
import it.tugaia56.obsidian.BuildConfig;
import it.tugaia56.obsidian.xposed.utils.ExtendedRemotePreferences;
public class XPrefs {
    @SuppressLint("StaticFieldLeak") public static ExtendedRemotePreferences Xprefs;
    private static final SharedPreferences.OnSharedPreferenceChangeListener listener = (sp, key) -> loadEverything(key);
    private static volatile boolean sListenerRegistered = false;
    public static void init(Context context) {
        Xprefs = new ExtendedRemotePreferences(context, BuildConfig.APPLICATION_ID, BuildConfig.APPLICATION_ID + "_preferences", true);
        // 2026-09-04: alcuni pacchetti (es. com.oplus.sos) partono così presto che il
        // ContentProvider di Obsidian non è ancora vivo — registerOnSharedPreferenceChangeListener
        // lanciava una SecurityException NON catturata qui, che risaliva fino a initContext() e
        // interrompeva TUTTO il resto (installHooks() non veniva mai chiamato per quel processo:
        // nessun mod si installava, non solo le prefs). ensureListenerRegistered() (chiamato da
        // XPLauncher.waitAndRefreshPrefs() dopo che il provider è confermato vivo) ritenta più
        // avanti, quando il retry loop già esistente ha successo.
        try {
            Xprefs.registerOnSharedPreferenceChangeListener(listener);
            sListenerRegistered = true;
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] XPrefs.init: listener registration deferred: " + t);
        }
    }
    /** Ritenta la registrazione del listener se il primo tentativo in init() è fallito
     *  (provider non ancora vivo) — chiamato da XPLauncher una volta che il retry loop
     *  su Xprefs.getBoolean() conferma che il provider risponde davvero. */
    public static void ensureListenerRegistered() {
        if (sListenerRegistered || Xprefs == null) return;
        try {
            Xprefs.registerOnSharedPreferenceChangeListener(listener);
            sListenerRegistered = true;
        } catch (Throwable ignored) {}
    }
    public static void loadEverything(String... key) {
        // Stessa chiave/semantica di "moreLogging" in OC — un solo switch in Impostazioni >
        // Generale, applicato subito (nessun restart SystemUI necessario) perché questo
        // listener gira già ad ogni cambio pref.
        boolean moreLogging = Xprefs.getBoolean("more_logging", false);
        // Un mod che lancia un'eccezione (es. un pref con tipo cambiato tra versioni) non deve
        // impedire agli altri mod di aggiornarsi, né tantomeno destabilizzare SystemUI.
        for (XposedMods mod : XPLauncher.runningMods) {
            try {
                mod.mDebug = moreLogging;
                mod.updatePrefs(key);
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] " + mod.getClass().getSimpleName() + ".updatePrefs failed: " + t);
            }
        }
    }
}
