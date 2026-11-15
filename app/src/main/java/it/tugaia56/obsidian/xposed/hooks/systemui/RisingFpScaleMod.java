package it.tugaia56.obsidian.xposed.hooks.systemui;

import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewParent;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Android puro (RisingOS): le icone dell'impronta di Impostazioni > Impostazioni risingOS > Schermata di blocco sono tante ma
 * hanno una misura fissa. UdfpsFpIconDrawable disegna l'icona scelta dentro i propri limiti: qui, mentre disegna, la tela viene
 * ingrandita/rimpicciolita attorno al centro con il cursore "Ridimensionamento icona impronta digitale" (lockscreen_fp_icon_scaling,
 * 50-200%). Sopra il 100% i contenitori vicini non devono tagliare l'icona: si spegne il ritaglio dei genitori (una volta).
 */
public class RisingFpScaleMod extends XposedMods {

    private static volatile float sScale = 1.0f;
    private static volatile long sRead = 0;
    private boolean mUnclipped = false;

    public RisingFpScaleMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        sScale = clamp(Xprefs.getFloat("lockscreen_fp_icon_scaling", 1.0f));
    }

    @Override
    public boolean listensTo(String packageName) {
        return SYSTEM_UI.equals(packageName);
    }

    private static float clamp(float v) {
        return Math.max(0.5f, Math.min(2.0f, v));
    }

    /** Scala corrente: riletta dalle preferenze al massimo una volta al secondo (cosi' il cursore vale subito). */
    private static float scale() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - sRead > 1000 && Xprefs != null) {
            sRead = now;
            try { sScale = clamp(Xprefs.getFloat("lockscreen_fp_icon_scaling", 1.0f)); } catch (Throwable ignored) {}
        }
        return sScale;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!SYSTEM_UI.equals(lpparam.packageName)) return;
        Class<?> cls = XposedHelpers.findClassIfExists("com.android.systemui.biometrics.UdfpsFpIconDrawable", lpparam.classLoader);
        if (cls == null) {
            XposedBridge.log("[ Obsidian ] RisingFpScaleMod: UdfpsFpIconDrawable not found (non e' RisingOS?)");
            return;
        }
        try {
            XposedBridge.hookAllMethods(cls, "draw", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        float s = scale();
                        if (s == 1.0f || !(p.args[0] instanceof Canvas)) return;
                        Canvas c = (Canvas) p.args[0];
                        Rect b = ((Drawable) p.thisObject).getBounds();
                        c.save();
                        c.scale(s, s, b.exactCenterX(), b.exactCenterY());
                        p.setObjectExtra("fpScaled", Boolean.TRUE);
                        if (s > 1.0f && !mUnclipped) unclip((Drawable) p.thisObject);
                    } catch (Throwable ignored) {}
                }
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        if (p.getObjectExtra("fpScaled") != null) ((Canvas) p.args[0]).restore();
                    } catch (Throwable ignored) {}
                }
            });
            XposedBridge.log("[ Obsidian ] RisingFpScaleMod: hooked UdfpsFpIconDrawable.draw");
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] RisingFpScaleMod: hook failed: " + t);
        }
    }

    /** L'icona ingrandita esce dai limiti della view: si spegne il ritaglio della view e dei suoi 3 genitori. */
    private void unclip(Drawable d) {
        mUnclipped = true;
        Drawable.Callback cb = d.getCallback();
        if (!(cb instanceof View)) return;
        View v = (View) cb;
        for (int i = 0; i < 4 && v != null; i++) {
            if (v instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) v).setClipChildren(false);
                ((android.view.ViewGroup) v).setClipToPadding(false);
            }
            ViewParent pa = v.getParent();
            v = pa instanceof View ? (View) pa : null;
        }
    }
}
