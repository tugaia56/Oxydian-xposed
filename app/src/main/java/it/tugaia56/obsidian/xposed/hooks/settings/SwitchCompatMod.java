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
    private static java.lang.reflect.Field sPosField;

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
        Class<?> found = XposedHelpers.findClassIfExists(CLS, lp.classLoader);
        if (found == null) {
            // App con R8: SwitchCompat e' rinominata, ma MaterialSwitch/SwitchMaterial restano col nome (sono nei layout):
            // si risale la catena delle superclassi fino a quella che dichiara onDraw(Canvas)
            for (String n : new String[]{"com.google.android.material.materialswitch.MaterialSwitch",
                    "com.google.android.material.switchmaterial.SwitchMaterial"}) {
                Class<?> m = XposedHelpers.findClassIfExists(n, lp.classLoader);
                for (Class<?> c = m; c != null && c != View.class; c = c.getSuperclass()) {
                    try { c.getDeclaredMethod("onDraw", Canvas.class); found = c; break; } catch (NoSuchMethodException ignored) {}
                }
                if (found != null) break;
            }
            if (found != null) XposedBridge.log("[ Obsidian ] SwitchCompatMod: obfuscated base " + found.getName() + " in " + lp.packageName);
        }
        if (found == null) return;
        final Class<?> cls = found;
        final java.lang.reflect.Field[] fld = drawableFields(cls);
        try {
            XposedHelpers.findAndHookMethod(cls, "onDraw", Canvas.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (CouiSwitchMod.variant().isEmpty()) return;
                        if (p.thisObject.getClass() == cls) {
                            setAlpha(p.thisObject, 0);
                        } else {
                            // MaterialSwitch e simili: barra e tondo si rifanno a ogni invalidate() e l'alfa non basta; per la
                            // durata del disegno originale si mettono disegni trasparenti (senza setter, quindi senza invalidate)
                            if (fld == null) return;
                            Object trk = fld[0].get(p.thisObject);
                            Object thb = fld[1].get(p.thisObject);
                            android.graphics.drawable.ColorDrawable st = new android.graphics.drawable.ColorDrawable(0);
                            android.graphics.drawable.ColorDrawable sb = new android.graphics.drawable.ColorDrawable(0);
                            p.setObjectExtra("oxy_trk", trk);
                            p.setObjectExtra("oxy_thb", thb);
                            p.setObjectExtra("oxy_rb0", trk instanceof Drawable ? new android.graphics.drawable.ColorDrawable(0) {{ setBounds(((Drawable) trk).getBounds()); }} : null);
                            p.setObjectExtra("oxy_rb1", thb instanceof Drawable ? new android.graphics.drawable.ColorDrawable(0) {{ setBounds(((Drawable) thb).getBounds()); }} : null);
                            fld[0].set(p.thisObject, st);
                            fld[1].set(p.thisObject, sb);
                        }
                    } catch (Throwable ignored) {}
                }

                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        String var = CouiSwitchMod.variant();
                        if (var.isEmpty()) return;
                        Drawable[] geo = null;
                        if (p.thisObject.getClass() == cls) setAlpha(p.thisObject, 255);
                        else {
                            Object trk = p.getObjectExtra("oxy_trk"), thb = p.getObjectExtra("oxy_thb");
                            if (fld != null) {
                                if (trk != null) fld[0].set(p.thisObject, trk);
                                if (thb != null) fld[1].set(p.thisObject, thb);
                            }
                            geo = new Drawable[]{null, null, (Drawable) p.getObjectExtra("oxy_rb0"), (Drawable) p.getObjectExtra("oxy_rb1")};
                        }
                        drawStyled((View) p.thisObject, (Canvas) p.args[0], var, geo);
                    } catch (Throwable t) {
                        XposedBridge.log("[ Obsidian ] SwitchCompatMod draw: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] SwitchCompatMod: hook failed in " + lp.packageName + ": " + t);
        }
    }

    private static void setAlpha(Object sw, int a) {
        for (String f : new String[]{"mTrackDrawable", "mThumbDrawable"}) {
            Object dr = XposedHelpers.getObjectField(sw, f);
            if (!(dr instanceof Drawable)) continue;
            Drawable d = (Drawable) dr;
            d.setAlpha(a);
        }
    }

    /** Campi barra/tondo di SwitchCompat: per nome, altrimenti (R8) i due campi Drawable dichiarati nella classe. */
    private static java.lang.reflect.Field[] drawableFields(Class<?> c) {
        try {
            java.lang.reflect.Field t = c.getDeclaredField("mTrackDrawable"), h = c.getDeclaredField("mThumbDrawable");
            t.setAccessible(true); h.setAccessible(true);
            return new java.lang.reflect.Field[]{t, h};
        } catch (NoSuchFieldException ignored) {}
        java.util.ArrayList<java.lang.reflect.Field> l = new java.util.ArrayList<>();
        for (java.lang.reflect.Field f : c.getDeclaredFields()) {
            if (Drawable.class.equals(f.getType()) && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) { f.setAccessible(true); l.add(f); }
        }
        XposedBridge.log("[ Obsidian ] SwitchCompatMod: drawable fields in " + c.getName() + ": " + l.size());
        return l.size() == 2 ? new java.lang.reflect.Field[]{l.get(0), l.get(1)} : null;
    }

    /** mThumbPosition rinominato: il float di SwitchCompat che, a interruttore acceso, vale tra 0 (escluso) e 1. */
    private static float thumbPosition(View v) {
        boolean on = ((android.widget.Checkable) v).isChecked();
        try {
            if (sPosField != null) return sPosField.getFloat(v);
            if (!on) return 0f;
            for (Class<?> k = v.getClass(); k != null && k != View.class; k = k.getSuperclass()) {
                for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                    if (f.getType() != float.class || java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    float x = f.getFloat(v);
                    if (x > 0f && x <= 1f) { sPosField = f; return x; }
                }
            }
        } catch (Throwable ignored) {}
        return on ? 1f : 0f;
    }

    private void drawStyled(View v, Canvas canvas, String var, Drawable[] geo) {
        if (mRenderer == null) mRenderer = new CouiSwitchMod(mContext);
        Context c = v.getContext();
        float d = c.getResources().getDisplayMetrics().density;
        int left, right, top, bottom;
        float pos;
        try {
            left = XposedHelpers.getIntField(v, "mSwitchLeft");
            right = XposedHelpers.getIntField(v, "mSwitchRight");
            top = XposedHelpers.getIntField(v, "mSwitchTop");
            bottom = XposedHelpers.getIntField(v, "mSwitchBottom");
            pos = XposedHelpers.getFloatField(v, "mThumbPosition");
        } catch (Throwable named) {
            // campi rinominati (R8): la barra e' il piu' largo dei due disegni veri (bounds fissati dal layout, letti prima
            // della sostituzione); la posizione del tondo e' il campo float di SwitchCompat che segue lo stato (0..1)
            if (geo == null || geo.length < 4 || geo[2] == null || geo[3] == null) return;
            android.graphics.Rect a = geo[2].getBounds(), b = geo[3].getBounds();
            if (a.width() < b.width()) a = b;
            if (a.isEmpty()) return;
            left = a.left; right = a.right; top = a.top; bottom = a.bottom;
            pos = thumbPosition(v);
        }
        boolean checked = (Boolean) XposedHelpers.callMethod(v, "isChecked");
        float w = right - left, h = bottom - top;
        if (w <= 0 || h <= 0) return;
        float cy = (top + bottom) / 2f;
        float thumb = Math.max(10f * d, Math.min(h - 6f * d, w / 2f));
        if (h > 28f * d) thumb = Math.min(thumb, 20f * d); // barre alte (Material 3, 32dp): tondo come negli altri interruttori
        float cx = left + h / 2f + Math.max(0f, Math.min(1f, pos)) * (w - h);
        int fg = CouiSwitchMod.attrColor(c, android.R.attr.colorForeground, 0xFFFFFFFF);
        int accent = AccentUtil.systemAccent();
        mRenderer.drawGeneric(canvas, var, checked, v.isEnabled(), left, w, cy, h, cx, thumb, d, fg, accent);
    }
}
