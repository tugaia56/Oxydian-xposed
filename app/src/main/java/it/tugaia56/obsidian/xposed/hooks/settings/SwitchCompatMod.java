package it.tugaia56.obsidian.xposed.hooks.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;
import it.tugaia56.obsidian.xposed.utils.AccentUtil;

/**
 * Interruttori AndroidX/Material (androidx.appcompat.widget.SwitchCompat e le sottoclassi MaterialSwitch /
 * SwitchMaterial) nello stile scelto in Oxydian Theme (persist.oxytheme.switchstyle): come per COUISwitch, ma qui la
 * geometria si legge dai campi di SwitchCompat (mSwitchLeft/Top/Right/Bottom, mThumbPosition). Il disegno originale
 * si rende invisibile (alfa 0 su barra e tondo) cosi' il testo dell'interruttore, se c'e', resta, e sopra si disegna lo
 * stile scelto. Entra solo nelle app dove il modulo e' attivo in LSPosed.
 */
public class SwitchCompatMod extends XposedMods {

    private static final String CLS = "androidx.appcompat.widget.SwitchCompat";
    private CouiSwitchMod mRenderer;

    public SwitchCompatMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {}

    @Override
    public boolean listensTo(String packageName) {
        return true;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        Class<?> cls = XposedHelpers.findClassIfExists(CLS, lp.classLoader);
        if (cls == null) return;
        try {
            XposedHelpers.findAndHookMethod(cls, "onDraw", Canvas.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (CouiSwitchMod.variant().isEmpty()) return;
                        setAlpha(p.thisObject, 0);
                    } catch (Throwable ignored) {}
                }

                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        String var = CouiSwitchMod.variant();
                        if (var.isEmpty()) return;
                        setAlpha(p.thisObject, 255);
                        drawStyled((View) p.thisObject, (Canvas) p.args[0], var);
                    } catch (Throwable t) {
                        XposedBridge.log("[ Obsidian ] SwitchCompatMod draw: " + t);
                    }
                }
            });
            XposedBridge.log("[ Obsidian ] SwitchCompatMod: hooked SwitchCompat.onDraw in " + lp.packageName);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] SwitchCompatMod: hook failed in " + lp.packageName + ": " + t);
        }
    }

    private static void setAlpha(Object sw, int a) {
        for (String f : new String[]{"mTrackDrawable", "mThumbDrawable"}) {
            Object dr = XposedHelpers.getObjectField(sw, f);
            if (dr instanceof Drawable) ((Drawable) dr).setAlpha(a);
        }
    }

    private void drawStyled(View v, Canvas canvas, String var) {
        if (mRenderer == null) mRenderer = new CouiSwitchMod(mContext);
        Context c = v.getContext();
        float d = c.getResources().getDisplayMetrics().density;
        int left = XposedHelpers.getIntField(v, "mSwitchLeft");
        int right = XposedHelpers.getIntField(v, "mSwitchRight");
        int top = XposedHelpers.getIntField(v, "mSwitchTop");
        int bottom = XposedHelpers.getIntField(v, "mSwitchBottom");
        float pos = XposedHelpers.getFloatField(v, "mThumbPosition");
        boolean checked = (Boolean) XposedHelpers.callMethod(v, "isChecked");
        float w = right - left, h = bottom - top;
        if (w <= 0 || h <= 0) return;
        float cy = (top + bottom) / 2f;
        float thumb = Math.max(10f * d, Math.min(h - 6f * d, w / 2f));
        float cx = left + h / 2f + Math.max(0f, Math.min(1f, pos)) * (w - h);
        int fg = CouiSwitchMod.attrColor(c, android.R.attr.colorForeground, 0xFFFFFFFF);
        int accent = AccentUtil.systemAccent();
        mRenderer.drawGeneric(canvas, var, checked, v.isEnabled(), left, w, cy, h, cx, thumb, d, fg, accent);
    }
}
