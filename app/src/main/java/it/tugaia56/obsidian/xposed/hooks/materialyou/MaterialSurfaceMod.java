package it.tugaia56.obsidian.xposed.hooks.materialyou;

import android.app.AndroidAppHelper;
import android.app.Application;
import android.content.Context;
import android.content.res.Resources;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * App Compose/Material 3 con i colori dinamici di Android (es. APKUpdater): sfondo, barre e card arrivano dai colori di
 * sistema system_background_dark / system_surface_dark / system_surface_container_*_dark, che un overlay del Theme
 * sull'app non puo' cambiare (sono risorse di android). Qui il valore letto dall'app viene sostituito con i colori di
 * sfondo del Theme (stesse risorse che usano l'overlay delle altre app): sfondo e superfici -> background_dark, contenitori
 * -> button_material_dark, contenitori alti -> holo_light_primary_dark. Solo con il tema scuro. Serve la spunta sull'app
 * nell'ambito di Oxydian in LSPosed.
 */
public class MaterialSurfaceMod extends XposedMods {

    public static final Set<String> PKGS = new HashSet<>(Arrays.asList("com.apkupdater.ci"));

    private static volatile boolean sReady;
    private static volatile int sPage, sTile, sCard;
    /** load() legge colori con getColor, che e' agganciato: senza guardia si richiamerebbe all'infinito. */
    private static final ThreadLocal<Boolean> sBusy = new ThreadLocal<>();

    public MaterialSurfaceMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {}

    @Override
    public boolean listensTo(String packageName) {
        return PKGS.contains(packageName);
    }

    private static int color(Resources res, String name) {
        int id = Resources.getSystem().getIdentifier(name, "color", "android");
        return id == 0 ? -1 : (res.getColor(id, null) & 0xFFFFFF);
    }

    /** Colori di destinazione con le risorse dell'app (hanno gia' gli overlay del Theme applicati). */
    private static void load() {
        if (sReady || sBusy.get() != null) return;
        sBusy.set(Boolean.TRUE);
        try {
            Application app = AndroidAppHelper.currentApplication();
            if (app == null) return;
            Resources res = app.getResources();
            sPage = color(res, "background_dark");
            sTile = color(res, "button_material_dark");
            sCard = color(res, "holo_light_primary_dark");
            sReady = true;
        } catch (Throwable ignored) {
        } finally {
            sBusy.remove();
        }
    }

    /** Valori di sistema dei colori Material (palette di questa ROM, tema scuro) -> colore del Theme; -1 se non cambia. */
    private static int target(int rgb) {
        switch (rgb) {
            case 0x13121C:   // system_background_dark, system_surface_dark, system_surface_dim_dark
            case 0x0E0D17:   // system_surface_container_lowest_dark
            case 0x1B1B25:   // system_surface_container_low_dark
                return sPage;
            case 0x1F1F29:   // system_surface_container_dark
                return sTile;
            case 0x2A2934:   // system_surface_container_high_dark
            case 0x34343F:   // system_surface_container_highest_dark
                return sCard;
            default:
                return -1;
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!PKGS.contains(lp.packageName)) return;
        try {
            XposedHelpers.findAndHookMethod(Resources.class, "getColor", int.class, Resources.Theme.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        if (sBusy.get() != null) return;
                        if ((Resources.getSystem().getConfiguration().uiMode & 0x30) != 0x20) return;
                        if (!sReady) load();
                        if (!sReady) return;
                        int c = (Integer) p.getResult();
                        int t = target(c & 0xFFFFFF);
                        if (t >= 0) p.setResult((c & 0xFF000000) | t);
                    } catch (Throwable ignored) {}
                }
            });
            XposedBridge.log("[ Obsidian ] MaterialSurfaceMod: surface colours hooked in " + lp.packageName);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] MaterialSurfaceMod: hook failed in " + lp.packageName + ": " + t);
        }
    }
}
