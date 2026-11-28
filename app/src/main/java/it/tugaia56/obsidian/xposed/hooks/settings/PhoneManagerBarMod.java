package it.tugaia56.obsidian.xposed.hooks.settings;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Gestione telefono: bordo attorno alla barretta in basso (Home, Utilita'), che ha lo stesso colore delle card e si vede poco.
 * Il colore della barretta non passa dalle risorse (provato con un overlay), quindi si disegna un bordo sopra la sua vista.
 * Le scelte arrivano da proprieta' di sistema (persist.obsidian.pm_bar*), perche' l'app non vede le preferenze di Oxydian.
 */
public class PhoneManagerBarMod extends XposedMods {

    public static final String PKG = "com.oplus.phonemanager";
    public static final String PROP_BORDER = "persist.obsidian.pm_bar_border";       // "0" spento, "1".."8" spessore (mezzi dp)
    public static final String PROP_COLOR = "persist.obsidian.pm_bar_color";         // "accent" o colore ARGB (intero)
    public static final String PROP_FILL = "persist.obsidian.pm_bar_fill";           // "0" originale, "accent" o colore ARGB

    private static final String BAR_VIEW = "COUIBottomFloatingNavigationBarMenuView";

    /** Le barrette trovate: il disegno controlla solo questo (chiamato per ogni vista dell'app, deve costare poco). */
    private final java.util.Set<View> mBars = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    public PhoneManagerBarMod(Context context) { super(context); }

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

    private static int accent(Context ctx) {
        try {
            String v = prop("persist.oxytheme.accent", "");
            if (v.length() >= 6) return 0xFF000000 | (int) Long.parseLong(v.substring(v.length() - 6), 16);
        } catch (NumberFormatException ignored) {}
        Resources r = ctx.getResources();
        int id = r.getIdentifier("accent_material_dark", "color", "android");
        return id != 0 ? r.getColor(id, null) : it.tugaia56.obsidian.xposed.utils.AccentUtil.themeAccent(0xFF908DFF);
    }

    /** Colore scelto per lo sfondo della barretta; 0 = lascia quello dell'app. "accent" = accento pieno. */
    private static int fillColor(Context ctx) {
        String fill = prop(PROP_FILL, "0");
        if ("0".equals(fill)) return 0;
        try {
            return "accent".equals(fill) ? accent(ctx) : Integer.parseInt(fill);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void style(View bar) {
        int width;
        try {
            width = Math.max(0, Math.min(8, Integer.parseInt(prop(PROP_BORDER, "0"))));
        } catch (NumberFormatException e) {
            width = 0;
        }
        Context ctx = bar.getContext();
        if (width == 0) return;
        String c = prop(PROP_COLOR, "accent");
        int color;
        try {
            color = "accent".equals(c) ? accent(ctx) : 0xFF000000 | Integer.parseInt(c);
        } catch (NumberFormatException e) {
            color = accent(ctx);
        }
        GradientDrawable border = new GradientDrawable();
        border.setCornerRadius(10000f);   // a pillola, come la barretta
        border.setColor(0);
        border.setStroke(Math.max(1, Math.round(width * 0.5f * ctx.getResources().getDisplayMetrics().density)), color);
        bar.setForeground(border);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    View v = (View) param.thisObject;
                    if (v.getClass().getName().endsWith(BAR_VIEW)) {
                        mBars.add(v);
                        style(v);
                    }
                } catch (Throwable t) {
                    XposedBridge.log("[ Obsidian ] PhoneManagerBarMod: " + t);
                }
            }
        });
        // Lo sfondo: l'app disegna la barretta sopra il background della vista, quindi il colore si disegna subito prima della
        // prima icona (sopra la barretta, sotto icone e testo).
        XposedHelpers.findAndHookMethod(ViewGroup.class, "drawChild", Canvas.class, View.class, long.class, new XC_MethodHook() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (!mBars.contains(param.thisObject)) return;
                    ViewGroup g = (ViewGroup) param.thisObject;
                    if (g.getChildCount() == 0 || param.args[1] != g.getChildAt(0)) return;
                    int color = fillColor(g.getContext());
                    if (color == 0) return;
                    paint.setColor(color);
                    float r = g.getHeight() / 2f;
                    ((Canvas) param.args[0]).drawRoundRect(0, 0, g.getWidth(), g.getHeight(), r, r, paint);
                } catch (Throwable t) {
                    XposedBridge.log("[ Obsidian ] PhoneManagerBarMod draw: " + t);
                }
            }
        });
        XposedBridge.log("[ Obsidian ] PhoneManagerBarMod: hooks installed in " + PKG);
    }
}
