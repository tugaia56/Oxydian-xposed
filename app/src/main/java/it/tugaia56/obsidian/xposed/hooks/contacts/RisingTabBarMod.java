package it.tugaia56.obsidian.xposed.hooks.contacts;

import android.content.Context;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * RisingOS "LiquidTabBars" (impostazione secure penguin_glass_tab_bars): trasforma la barra in basso delle app in una pillola di
 * vetro che copre il fondo della pagina e il pulsante + e colora la selezione di blu. Qui la si spegne nelle app elencate: il suo
 * onLayout non fa niente, cosi' resta la barra normale dell'app. (I Contatti lo fanno gia' dentro ContactsMod.)
 */
public class RisingTabBarMod extends XposedMods {

    /** App in cui la pillola di vetro va spenta. */
    private static final String[] PACKAGES = {
            "com.google.android.deskclock",
    };

    public static boolean handles(String pkg) {
        for (String p : PACKAGES) if (p.equals(pkg)) return true;
        return false;
    }

    public RisingTabBarMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {}

    @Override
    public boolean listensTo(String packageName) {
        return handles(packageName);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        try {
            Class<?> liquid = Class.forName("com.android.internal.penguin.glass.LiquidTabBars");
            XposedBridge.hookAllMethods(liquid, "onLayout", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) { param.setResult(null); }
            });
            XposedBridge.log("[ Obsidian ] RisingTabBarMod: LiquidTabBars off in " + lpparam.packageName);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] RisingTabBarMod: LiquidTabBars hook failed: " + t);
        }
    }
}
