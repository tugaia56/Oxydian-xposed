package it.tugaia56.obsidian.xposed;
import android.content.Context;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
public abstract class XposedMods {
    protected Context mContext;
    protected boolean mDebug = false;
    public XposedMods(Context context) { mContext = context; }
    public abstract void updatePrefs(String... Key);
    public abstract void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable;
    protected void initResources() {}
    /** Chiamato SOLO quando Xprefs (il ContentProvider) risulta irraggiungibile per davvero,
     *  non per timing — es. filtro di visibilità pacchetti di Android che blocca alcune app
     *  dal vedere Obsidian a prescindere da quanto si aspetti (confermato 2026-09-05 su
     *  com.oneplus.account/com.oplus.games: "Failed to find provider info" ripetuto ogni
     *  secondo per sempre in logcat, mai una volta risolto). Default no-op: solo i mod che
     *  hanno davvero bisogno di funzionare in questi processi (SettingsCardBackgroundMod)
     *  lo sovrascrivono, rileggendo le prefs direttamente dal file invece che dal provider —
     *  stesso pattern già usato dagli hook di sistema (DstDialogStyle/MonetFreeze). */
    public void preloadFallback() {}
    public abstract boolean listensTo(String packageName);
    public void log(String message) {
        if (!mDebug) return;
        XposedBridge.log("[ Obsidian - " + getClass().getSimpleName() + " ] " + message);
    }
}
