package it.tugaia56.obsidian.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.Context;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Trasparenza e Sfocatura QS.
 *
 * Trasparenza — porting reale di OC's QSTransparency: intercetta l'alpha che SystemUI stesso
 * assegna agli scrim di sfondo/notifiche ogni volta che lo calcola, moltiplicandolo per il
 * valore scelto.
 *
 * Sfocatura — NON è il trucco di OC (quello è un no-op su SDK&gt;=35, OC stesso l'ha
 * disattivato). Qui si usa invece il blur NATIVO di OOS: com.oplus.systemui.scrim.
 * ScrimViewExImp.setBlurAmount(float) è un metodo reale già confermato presente su questo
 * ROM (QsBackground.java lo aggancia per azzerarlo quando "Sfondo Solido" è attivo) — gli
 * passiamo il nostro valore invece di lasciarlo passare o azzerarlo. Se "Sfondo Solido" è
 * attivo, non tocchiamo nulla: quella funzione vuole colore piatto senza sfocatura, e il suo
 * hook (registrato prima nella lista ModPacks) girerebbe comunque dopo il nostro sovrascrivendo
 * il valore — leggiamo lo stesso pref per restare coerenti invece di affidarci all'ordine.
 */
public class QsTransparencyMod extends XposedMods {

    private static final String PREF_TRANSP_ON    = "OBS_QS_TRANSPARENCY_ON";
    private static final String PREF_TRANSP_VALUE = "OBS_QS_TRANSPARENCY_VALUE";
    private static final String PREF_BLUR_ON       = "OBS_QS_BLUR_ON";
    private static final String PREF_BLUR_RADIUS   = "OBS_QS_BLUR_RADIUS";
    private static final String PREF_BLUR_MAX      = "OBS_QS_BLUR_MAX";
    private static final String PREF_SOLID_BG_ON   = "DST_QS_BG_ENABLED";

    private static final float KEYGUARD_ALPHA = 0.85f;

    private boolean mTransparencyOn = false;
    private float mAlpha = 0.4f;
    private boolean mBlurOn = false;
    private float mBlurAmount = 0.4f;
    private boolean mSolidBgOn = false;

    /** ScrimController reale, catturato al primo updateScrimColor: serve a leggere lo stato
     *  corrente anche dall'hook della sfocatura (che non ha accesso al controller). */
    private static volatile Object sScrimController;

    /** In AOD/pulsing lo sfondo deve restare nero pieno e senza sfocatura: ridurre l'opacità
     *  degli scrim lì lascia vedere sfondo e contenuti della Schermata di Blocco dietro l'AOD
     *  (segnalato da due utenti: sfondo visibile in AOD con trasparenza/sfocatura attive). */
    private static boolean isAodLikeState(Object state) {
        String name = String.valueOf(state);
        return name.contains("AOD") || name.contains("PULSING") || name.contains("DOZ");
    }

    private static boolean isAodNow() {
        try {
            Object c = sScrimController;
            return c != null && isAodLikeState(getObjectField(c, "mState"));
        } catch (Throwable t) {
            return false;
        }
    }

    public QsTransparencyMod(Context context) { super(context); }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mTransparencyOn = Xprefs.getBoolean(PREF_TRANSP_ON, false);
        mAlpha = Xprefs.getInt(PREF_TRANSP_VALUE, 40) / 100f;
        mBlurOn = Xprefs.getBoolean(PREF_BLUR_ON, false);
        float radius = Xprefs.getInt(PREF_BLUR_RADIUS, 40) / 100f;
        float max = Xprefs.getInt(PREF_BLUR_MAX, 100) / 100f;
        mBlurAmount = Math.min(radius, max);
        mSolidBgOn = Xprefs.getBoolean(PREF_SOLID_BG_ON, false);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!SYSTEM_UI.equals(lp.packageName)) return;

        Class<?> scrimControllerClass = findClass(
                "com.android.systemui.statusbar.phone.ScrimController", lp.classLoader);

        hookAllMethods(scrimControllerClass, "updateScrimColor", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                sScrimController = param.thisObject;
                if (!mTransparencyOn) return;
                try {
                    int alphaIndex = param.args[2] instanceof Float ? 2 : 1;
                    Object stateObj = getObjectField(param.thisObject, "mState");
                    if (isAodLikeState(stateObj)) return; // AOD: scrim originale, nero pieno
                    String scrimState = String.valueOf(stateObj);

                    if (scrimState.contains("BOUNCER")) {
                        param.args[alphaIndex] = (Float) param.args[alphaIndex] * KEYGUARD_ALPHA;
                        return;
                    }

                    Object scrimBehind = findField(scrimControllerClass, "mScrimBehind").get(param.thisObject);
                    Object scrimNotifications = findField(scrimControllerClass, "mNotificationsScrim").get(param.thisObject);
                    boolean isBackgroundScrim = param.args[0].equals(scrimBehind) || param.args[0].equals(scrimNotifications);
                    if (isBackgroundScrim) {
                        param.args[alphaIndex] = (Float) param.args[alphaIndex] * mAlpha;
                    }
                } catch (Throwable ignored) {}
            }
        });

        try {
            Class<?> scrimViewExImp = findClass("com.oplus.systemui.scrim.ScrimViewExImp", lp.classLoader);
            hookAllMethods(scrimViewExImp, "setBlurAmount", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!mBlurOn || mSolidBgOn || isAodNow()) return;
                    if (param.args.length > 0 && param.args[0] instanceof Float) {
                        param.args[0] = mBlurAmount;
                    }
                }
            });
        } catch (Throwable ignored) {}
    }

    @Override public boolean listensTo(String packageName) { return SYSTEM_UI.equals(packageName); }
}
