package it.tugaia56.obsidian.xposed.hooks.settings;

import static it.tugaia56.obsidian.utils.Constants.Packages.SETTINGS;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Schermata "Avvio del telefono..." mostrata subito dopo il PIN (com.android.settings.FallbackHome,
 * layout fallback_home_finishing_boot): la barra di avanzamento prende il colore del testo del tema
 * (colorControlActivated = ?textColorPrimary, quindi bianco). Qui la si colora con l'accento scelto
 * dall'utente (proprieta' persist.obsidian.dst.a1, scritta da Oxydian Theme/Oxydian).
 */
public class FallbackHomeBarMod extends XposedMods {

    private static final String FALLBACK_HOME = "com.android.settings.FallbackHome";

    public FallbackHomeBarMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {}

    @Override
    public boolean listensTo(String packageName) {
        return SETTINGS.equals(packageName);
    }

    private static int accent() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            String a1 = (String) XposedHelpers.callStaticMethod(sp, "get", "persist.obsidian.dst.a1", "");
            if (a1 != null && !a1.isEmpty()) return 0xFF000000 | Integer.parseInt(a1);
        } catch (Throwable ignored) {}
        return 0;
    }

    private static ProgressBar findBar(View v) {
        if (v instanceof ProgressBar) return (ProgressBar) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                ProgressBar p = findBar(g.getChildAt(i));
                if (p != null) return p;
            }
        }
        return null;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedBridge.hookAllMethods(Activity.class, "setContentView", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (!FALLBACK_HOME.equals(param.thisObject.getClass().getName())) return;
                    int c = accent();
                    if (c == 0) return;
                    ProgressBar bar = findBar(((Activity) param.thisObject).getWindow().getDecorView());
                    if (bar == null) return;
                    ColorStateList tint = ColorStateList.valueOf(c);
                    bar.setIndeterminateTintList(tint);
                    bar.setProgressTintList(tint);
                } catch (Throwable ignored) {}
            }
        });
    }
}
