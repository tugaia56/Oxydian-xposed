package it.tugaia56.obsidian.xposed.hooks.dialer;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Telefono Google: sfondo, bordo e colore dei numeri del tastierino. Il Theme (overlay) non puo' dare un bordo ai tasti,
 * e il colore dei numeri e' lo stesso di quasi tutto il testo dell'app.
 * Le scelte arrivano da proprieta' di sistema (persist.obsidian.dial_*), perche' l'app Telefono non vede le
 * preferenze di Oxydian; si applicano quando il tastierino viene ricreato (chiudere e riaprire l'app).
 */
public class DialerKeypadMod extends XposedMods {

    public static final String PKG = "com.google.android.dialer";
    public static final String PROP_FILL = "persist.obsidian.dial_fill";                 // "0" originale, "1" accento, "2" colore scelto
    public static final String PROP_FILL_COLOR = "persist.obsidian.dial_fill_color";     // ARGB (intero)
    public static final String PROP_BORDER = "persist.obsidian.dial_border";             // "0" spento, "1".."8" spessore (mezzi dp)
    public static final String PROP_BORDER_COLOR = "persist.obsidian.dial_border_color"; // "accent" o colore ARGB (intero)

    public static final String PROP_NUMBER = "persist.obsidian.dial_number";             // "0" originale, "accent" o colore ARGB (intero)

    private static final String ORIGINAL = "oxy_dial_original_bg";
    private static final String ORIGINAL_TEXT = "oxy_dial_original_text";
    private static final String WATCHED = "oxy_dial_watched";

    public DialerKeypadMod(Context context) { super(context); }

    @Override public void updatePrefs(String... Key) {}

    @Override public boolean listensTo(String packageName) { return PKG.equals(packageName); }

    private static String prop(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            String v = (String) XposedHelpers.callStaticMethod(sp, "get", key, def);
            return v == null || v.isEmpty() ? def : v.trim();
        } catch (Throwable t) {
            return def;
        }
    }

    private static int intProp(String key, int def) {
        try {
            return Integer.parseInt(prop(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int accent(Context ctx) {
        try {
            String v = prop("persist.oxytheme.accent", "");
            if (v.length() >= 6) return 0xFF000000 | (int) Long.parseLong(v.substring(v.length() - 6), 16);
        } catch (NumberFormatException ignored) {}
        Resources r = ctx.getResources();
        int id = r.getIdentifier("accent_material_dark", "color", "android");
        return id != 0 ? r.getColor(id, null) : it.tugaia56.obsidian.xposed.utils.AccentUtil.themeAccent(0xFF908DFF);
    }

    /** Colore del numero grande (le lettere sotto restano come sono). */
    private void styleNumber(View key) {
        TextView number = number(key);
        if (number == null) return;
        String c = prop(PROP_NUMBER, "0");
        Object saved = XposedHelpers.getAdditionalInstanceField(number, ORIGINAL_TEXT);
        if ("0".equals(c)) {
            if (saved instanceof ColorStateList) number.setTextColor((ColorStateList) saved);
            return;
        }
        if (saved == null) XposedHelpers.setAdditionalInstanceField(number, ORIGINAL_TEXT, number.getTextColors());
        int color;
        try {
            color = "accent".equals(c) ? accent(key.getContext()) : 0xFF000000 | Integer.parseInt(c);
        } catch (NumberFormatException e) {
            color = accent(key.getContext());
        }
        number.setTextColor(color);
        keepNumberColor(key, color);
    }

    private static TextView number(View key) {
        int id = key.getResources().getIdentifier("dialpad_key_number", "id", PKG);
        View v = id == 0 ? null : key.findViewById(id);
        return v instanceof TextView ? (TextView) v : null;
    }

    /** L'app ricolora il tasto 1 (segreteria) dopo che e' comparso: il colore si ricontrolla prima di disegnare. */
    private static void keepNumberColor(View key, int color) {
        if (XposedHelpers.getAdditionalInstanceField(key, WATCHED) != null) return;
        XposedHelpers.setAdditionalInstanceField(key, WATCHED, Boolean.TRUE);
        key.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (!key.isAttachedToWindow()) {
                    ViewTreeObserver o = key.getViewTreeObserver();
                    if (o.isAlive()) o.removeOnPreDrawListener(this);
                    XposedHelpers.removeAdditionalInstanceField(key, WATCHED);
                    return true;
                }
                TextView n = number(key);
                if (n != null && n.getCurrentTextColor() != color) n.setTextColor(color);
                return true;
            }
        });
    }

    private void style(View key) {
        styleNumber(key);
        int mode = intProp(PROP_FILL, 0);
        int width = Math.max(0, Math.min(8, intProp(PROP_BORDER, 0)));
        Object saved = XposedHelpers.getAdditionalInstanceField(key, ORIGINAL);
        if (mode == 0 && width == 0) {
            if (saved instanceof Drawable) key.setBackground((Drawable) saved);
            return;
        }
        Drawable original = saved instanceof Drawable ? (Drawable) saved : key.getBackground();
        if (saved == null && original != null) XposedHelpers.setAdditionalInstanceField(key, ORIGINAL, original);

        Context ctx = key.getContext();
        int stroke = Math.round(width * 0.5f * ctx.getResources().getDisplayMetrics().density);
        String bc = prop(PROP_BORDER_COLOR, "accent");
        int borderColor;
        try {
            borderColor = "accent".equals(bc) ? accent(ctx) : 0xFF000000 | Integer.parseInt(bc);
        } catch (NumberFormatException e) {
            borderColor = accent(ctx);
        }

        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(10000f);   // stessa forma a pillola dei tasti di serie
        if (width > 0) shape.setStroke(Math.max(1, stroke), borderColor);

        if (mode == 0 && original != null) {
            // sfondo di serie (con il suo effetto al tocco) piu' il solo bordo sopra
            key.setBackground(new LayerDrawable(new Drawable[]{original, shape}));
            return;
        }
        if (mode == 1) shape.setColor(accent(ctx));
        else if (mode == 2) shape.setColor(intProp(PROP_FILL_COLOR, accent(ctx)));
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(10000f);
        mask.setColor(0xFFFFFFFF);
        key.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), shape, mask));
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    View v = (View) param.thisObject;
                    if (v.getClass().getName().endsWith(".DialpadKey")) style(v);
                } catch (Throwable t) {
                    XposedBridge.log("[ Obsidian ] DialerKeypadMod: " + t);
                }
            }
        });
        XposedBridge.log("[ Obsidian ] DialerKeypadMod: hooks installed in " + PKG);
    }
}
