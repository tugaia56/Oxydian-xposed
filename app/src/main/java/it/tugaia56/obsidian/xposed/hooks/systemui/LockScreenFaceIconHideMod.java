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
 * Android puro (RisingOS): "Nascondi icona lucchetto" (lockscreen_hide_lock_icon). Su RisingOS l'icona del blocco e' la faccina del
 * riconoscimento del volto (FaceUnlockImageView: cambia tra scansione, sorridente e arrabbiata); l'impronta e' un'altra vista e resta.
 * Su OxygenOS lo fa LockScreenButtonsMods col LockIconView.
 */
public class LockScreenFaceIconHideMod extends XposedMods {

    private static volatile boolean sHide = false;

    public LockScreenFaceIconHideMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        sHide = Xprefs.getBoolean("lockscreen_hide_lock_icon", false);
    }

    @Override
    public boolean listensTo(String packageName) {
        return SYSTEM_UI.equals(packageName);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!SYSTEM_UI.equals(lpparam.packageName)) return;
        Class<?> cls = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.phone.FaceUnlockImageView", lpparam.classLoader);
        if (cls == null) {
            XposedBridge.log("[ Obsidian ] LockScreenFaceIconHideMod: FaceUnlockImageView not found (non e' RisingOS?)");
            return;
        }
        XC_MethodHook h = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    if (sHide && p.thisObject instanceof View) ((View) p.thisObject).setVisibility(View.GONE);
                } catch (Throwable ignored) {}
            }
        };
        int n = 0;
        for (String m : new String[]{"onAttachedToWindow", "setState", "updateFaceIconState", "startAnimation", "startAnimationChain",
                "scanningAnimation", "successAnimation", "failureShakeAnimation"}) {
            try { n += XposedBridge.hookAllMethods(cls, m, h).size(); } catch (Throwable ignored) {}
        }
        XposedBridge.log("[ Obsidian ] LockScreenFaceIconHideMod: hooked " + n + " methods of FaceUnlockImageView");
    }
}
