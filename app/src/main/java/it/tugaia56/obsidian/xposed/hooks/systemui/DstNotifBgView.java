package it.tugaia56.obsidian.xposed.hooks.systemui;

import android.graphics.drawable.Drawable;
import android.view.View;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hook diretto su com.android.systemui.statusbar.notification.row.NotificationBackgroundView
 * — la classe reale (OOS/AOSP) che disegna lo sfondo di ogni notifica. Su questa build lo
 * sfondo non passa mai per una risorsa drawable sostituibile via XResources (verificato con
 * una diagnostica: notification_material_bg[/_monet] non viene mai richiesta), quindi
 * DstNotifStyle.applyPreloaded() (basato su XResources.setReplacement) non ha alcun effetto.
 *
 * Stessa tecnica già usata con successo per il Dialogo (vedi DstDialogStyle): si intercetta
 * il metodo che imposta lo sfondo e si sostituisce l'argomento con il nostro drawable, invece
 * di sostituire una risorsa che il sistema non carica mai.
 */
public class DstNotifBgView {

    private static final String CLASS_NAME =
        "com.android.systemui.statusbar.notification.row.NotificationBackgroundView";

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = lpparam.classLoader.loadClass(CLASS_NAME);

            XC_MethodHook bgHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    if (p.args.length == 0) return;
                    if (!(p.args[0] instanceof Drawable)) return; // skip int-resId overload
                    try {
                        View v = (View) p.thisObject;
                        float density = v.getResources().getDisplayMetrics().density;
                        boolean isNight = (v.getResources().getConfiguration().uiMode
                                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                        Drawable d = DstNotifStyle.currentDrawable(density, isNight);
                        if (d != null) p.args[0] = d;
                    } catch (Throwable t) {
                        XposedBridge.log("[ Obsidian ] DstNotifBgView build ERROR: " + t);
                    }
                }
            };
            // Android 17: setCustomBackground$1() senza argomenti carica da solo il disegno di serie: si sostituisce dopo
            XC_MethodHook afterNoArg = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        View v = (View) p.thisObject;
                        float density = v.getResources().getDisplayMetrics().density;
                        boolean isNight = (v.getResources().getConfiguration().uiMode
                                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                        Drawable d = DstNotifStyle.currentDrawable(density, isNight);
                        if (d == null) return;
                        Object old = XposedHelpers.getObjectField(v, "mBackground");
                        if (old instanceof Drawable) ((Drawable) old).setCallback(null);
                        d = d.mutate();
                        d.setCallback(v);
                        XposedHelpers.setObjectField(v, "mBackground", d);
                        XposedHelpers.setAdditionalInstanceField(v, "oxyStyled", Boolean.TRUE);
                        // gli angoli li decide lo stile: si evita che il sistema li riscriva (il disegno non e' quello di serie)
                        try { XposedHelpers.setBooleanField(v, "mDontModifyCorners", true); } catch (Throwable ignored) {}
                        v.invalidate();
                        XposedBridge.log("[ Obsidian ] DstNotifBgView(after): replaced with " + d.getClass().getSimpleName()
                                + " alpha=" + d.getAlpha() + " size=" + v.getWidth() + "x" + v.getHeight());
                    } catch (Throwable t) {
                        XposedBridge.log("[ Obsidian ] DstNotifBgView(after) ERROR: " + t);
                    }
                }
            };
            // crDroid "Notifiche semitrasparenti": sotto lo sfondo mette un disegno sfocato (BackgroundBlurDrawable) quadrato e opaco
            // che copre lo stile (gli angoli non glieli aggiorna nessuno, mDontModifyCorners): con uno stile attivo lo si spegne
            try {
                XposedBridge.hookAllMethods(cls, "setBlurBackgroundEnabled", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            if (p.args.length == 1 && p.args[0] instanceof Boolean && (Boolean) p.args[0] && DstNotifStyle.isEnabled()) {
                                p.args[0] = Boolean.FALSE;
                            }
                        } catch (Throwable ignored) {}
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] DstNotifBgView: setBlurBackgroundEnabled hook FAILED: " + t);
            }
            // Android 17: setTint(int) dipinge tutto lo sfondo di un solo colore (e presume che il primo strato sia un GradientDrawable):
            // cancellerebbe contorno e accento dello stile. Con lo stile applicato si salta e si tiene solo il colore.
            try {
                XposedBridge.hookAllMethods(cls, "setTint", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            if (p.args.length != 1 || !(p.args[0] instanceof Integer)) return;
                            if (!Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(p.thisObject, "oxyStyled"))) return;
                            XposedHelpers.setIntField(p.thisObject, "mTintColor", (Integer) p.args[0]);
                            ((View) p.thisObject).invalidate();
                            p.setResult(null);
                        } catch (Throwable ignored) {}
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] DstNotifBgView: setTint hook FAILED: " + t);
            }
            // su OxygenOS il metodo si chiama setCustomBackground(Drawable), su Android 17 setCustomBackground$1()
            int n = 0;
            for (java.lang.reflect.Method m : cls.getDeclaredMethods()) {
                if (!m.getName().startsWith("setCustomBackground")) continue;
                if (m.getParameterTypes().length == 0) XposedBridge.hookMethod(m, afterNoArg);
                else XposedBridge.hookMethod(m, bgHook);
                n++;
            }
            XposedBridge.log("[ Obsidian ] DstNotifBgView: hooked " + CLASS_NAME
                    + ".setCustomBackground* (" + n + ")");
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] DstNotifBgView: hook FAILED: " + t);
        }
    }
}
