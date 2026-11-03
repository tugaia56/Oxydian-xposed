package it.tugaia56.obsidian.xposed.hooks.settings;

import static it.tugaia56.obsidian.utils.Constants.Packages.SETTINGS;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Impostazioni su Android puro (crDroid, ecc.): la finestra e' opaca, quindi i colori di sfondo trasparenti scelti in
 * Oxydian Theme scuriscono soltanto la pagina. Qui la finestra di ogni attivita' di Impostazioni viene resa trasparente
 * e mostra lo sfondo del telefono. Su OxygenOS non serve (lo fa gia' il tema) e non viene caricata.
 */
public class SettingsWindowTransparencyMod extends XposedMods {

    public SettingsWindowTransparencyMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {}

    @Override
    public boolean listensTo(String packageName) {
        return SETTINGS.equals(packageName);
    }

    /** Sistema OxygenOS/ColorOS? (proprieta' ro.build.version.oplusrom presente) */
    public static boolean isOplus() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            String v = (String) XposedHelpers.callStaticMethod(sp, "get", "ro.build.version.oplusrom", "");
            return v != null && !v.isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Oxydian Theme pubblica 1 quando e' scelto uno sfondo "Trasparente" (persist.oxytheme.settings_window). */
    private static boolean wanted() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            return "1".equals(XposedHelpers.callStaticMethod(sp, "get", "persist.oxytheme.settings_window", "0"));
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedBridge.hookAllMethods(Activity.class, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (!(param.args.length > 0 && (param.args[0] == null || param.args[0] instanceof Bundle))) return;
                    if (!wanted()) return;
                    Activity a = (Activity) param.thisObject;
                    Window w = a.getWindow();
                    w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
                    w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                } catch (Throwable t) {
                    XposedBridge.log("[ Obsidian - SettingsWindowTransparencyMod ] onCreate: " + t);
                }
            }
        });
    }
}
