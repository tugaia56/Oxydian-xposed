package it.tugaia56.obsidian.xposed.hooks.systemui;

import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.view.View;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Android puro (RisingOS): "Nascondi barra di stato" della schermata di blocco (lockscreen_hide_statusbar).
 * Su OxygenOS lo fa LockScreenMiscMods con un hook sul layout; qui si tiene sempre GONE la view della barra di stato del blocco
 * (KeyguardStatusBarView: operatore + icone + batteria), anche quando SystemUI prova a mostrarla.
 */
public class LockScreenStatusBarHideMod extends XposedMods {

    private static volatile boolean sHide = false;
    private static volatile boolean sHideCarrier = false;
    private static volatile long sRead = 0;

    public LockScreenStatusBarHideMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        sHide = Xprefs.getBoolean("lockscreen_hide_statusbar", false);
        sHideCarrier = Xprefs.getBoolean("lockscreen_hide_carrier", false);
        applyNow();
    }

    @Override
    public boolean listensTo(String packageName) {
        return SYSTEM_UI.equals(packageName);
    }

    private static java.lang.ref.WeakReference<View> sView;

    /** Cambio dell'interruttore con SystemUI gia' acceso: si applica alla view viva. */
    private static void applyNow() {
        View v = sView != null ? sView.get() : null;
        if (v == null) return;
        v.post(() -> v.setVisibility(sHide ? View.GONE : View.VISIBLE));
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!SYSTEM_UI.equals(lpparam.packageName)) return;
        Class<?> cls = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.phone.KeyguardStatusBarView", lpparam.classLoader);
        if (cls == null) {
            XposedBridge.log("[ Obsidian ] LockScreenStatusBarHideMod: KeyguardStatusBarView not found");
            return;
        }
        try {
            XposedBridge.hookAllMethods(cls, "setVisibility", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        sView = new java.lang.ref.WeakReference<>((View) p.thisObject);
                        if (sHide && p.args.length == 1 && p.args[0] instanceof Integer) p.args[0] = View.GONE;
                    } catch (Throwable ignored) {}
                }
            });
            XposedBridge.hookAllMethods(cls, "onFinishInflate", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        sView = new java.lang.ref.WeakReference<>((View) p.thisObject);
                        if (sHide) ((View) p.thisObject).setVisibility(View.GONE);
                    } catch (Throwable ignored) {}
                }
            });
            // "Nascondi operatore": solo la scritta dell'operatore (mCarrierLabel) della barra del blocco
            XC_MethodHook carrier = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        if (!sHideCarrier) return;
                        Object label = XposedHelpers.getObjectField(p.thisObject, "mCarrierLabel");
                        if (label instanceof View) ((View) label).setVisibility(View.GONE);
                    } catch (Throwable ignored) {}
                }
            };
            for (String m : new String[]{"onFinishInflate", "updateVisibilities", "updateLayoutParamsNoCutout", "updateCarrierLabelMargin", "onConfigurationChanged"}) {
                try { XposedBridge.hookAllMethods(cls, m, carrier); } catch (Throwable ignored) {}
            }
            XposedBridge.log("[ Obsidian ] LockScreenStatusBarHideMod: hooked KeyguardStatusBarView");
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] LockScreenStatusBarHideMod: hook failed: " + t);
        }
    }
}
