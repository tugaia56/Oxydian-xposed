package it.tugaia56.obsidian.xposed.hooks.systemui;

import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.view.WindowManager;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Android puro (RisingOS, crDroid): le opzioni della Barra di stato che usano solo classi Android normali.
 * (doppio tocco e luminosita' della barra di stato li ha gia' RisingOS, qui non servono)
 *  - block_clipboard_overlay: niente finestrella dell'appunto copiato (WindowManagerGlobal.addView)
 */
public class AospStatusbarMod extends XposedMods {

    private static volatile boolean sBlockClip;


    public AospStatusbarMod(Context context) { super(context); }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        sBlockClip  = Xprefs.getBoolean("block_clipboard_overlay", false);
    }

    @Override
    public boolean listensTo(String packageName) { return SYSTEM_UI.equals(packageName); }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!SYSTEM_UI.equals(lp.packageName)) return;
        try { updatePrefs(); } catch (Throwable ignored) {}
        try { hookClipboard(); } catch (Throwable t) { XposedBridge.log("[ Obsidian ] AospStatusbarMod clipboard: " + t); }
    }

    // ── Clipboard ───────────────────────────────────────────────────────────

    private void hookClipboard() {
        Class<?> wmg = XposedHelpers.findClassIfExists("android.view.WindowManagerGlobal", null);
        if (wmg == null) return;
        XposedBridge.hookAllMethods(wmg, "addView", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!sBlockClip) return;
                try {
                    for (Object arg : p.args) {
                        if (arg instanceof WindowManager.LayoutParams) {
                            CharSequence title = ((WindowManager.LayoutParams) arg).getTitle();
                            if (title != null && title.toString().contains("ClipboardOverlay")) { p.setResult(null); return; }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        });
    }
}
