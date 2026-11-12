package it.tugaia56.obsidian.xposed.hooks.contacts;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.os.SystemClock;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * RisingOS "LiquidTabBars" (impostazione secure penguin_glass_tab_bars): trasforma la barra in basso delle app in una pillola di
 * vetro che copre il fondo della pagina e il pulsante + e colora la selezione di blu. Qui la si spegne nelle app elencate: il suo
 * onLayout non fa niente, cosi' resta la barra normale dell'app. La barra normale poi si ridisegna nel colore di sfondo del Theme
 * con una linea accento sopra (come nei Contatti, che lo fanno dentro ContactsMod).
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

    private volatile int mBg = 0xFF1B2029;
    private volatile int mAccent = 0xFFE05D00;
    private float mDensity = 3f;
    private float mScreenW = 1080f;
    private long mAt = 0;
    private static final Object NO_FILTER = new Object();
    private Paint mStroke;
    private final ThreadLocal<Boolean> mBusy = new ThreadLocal<>();

    public RisingTabBarMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {}

    @Override
    public boolean listensTo(String packageName) {
        return handles(packageName);
    }

    private static String prop(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            return String.valueOf(XposedHelpers.callStaticMethod(sp, "get", key, ""));
        } catch (Throwable t) {
            return "";
        }
    }

    private void refresh() {
        long now = SystemClock.elapsedRealtime();
        if (now - mAt < 2000) return;
        mAt = now;
        try {
            android.app.Application app = android.app.AndroidAppHelper.currentApplication();
            Resources r = app != null ? app.getResources() : Resources.getSystem();
            mDensity = r.getDisplayMetrics().density;
            mScreenW = r.getDisplayMetrics().widthPixels;
            int id = r.getIdentifier("system_background_dark", "color", "android");
            if (id != 0) mBg = 0xFF000000 | (r.getColor(id, null) & 0xFFFFFF);
            int acc = 0;
            String v = prop("persist.oxytheme.accent");
            if (v.length() >= 6) {
                try { acc = 0xFF000000 | (int) Long.parseLong(v.substring(v.length() - 6), 16); } catch (NumberFormatException ignored) {}
            }
            if (acc == 0) {
                id = r.getIdentifier("accent_material_dark", "color", "android");
                if (id != 0) acc = 0xFF000000 | (r.getColor(id, null) & 0xFFFFFF);
            }
            if (acc != 0) mAccent = acc;
        } catch (Throwable ignored) {}
    }

    private static float luma(int c) {
        return (0.299f * ((c >> 16) & 0xFF) + 0.587f * ((c >> 8) & 0xFF) + 0.114f * (c & 0xFF)) / 255f;
    }

    /** Card: forma piena e opaca, larga (70-98% dello schermo), alta 40-650 dp, di colore scuro. */
    private boolean isCard(Paint p, float w, float h) {
        if (p.getStyle() != Paint.Style.FILL || p.getShader() != null || (p.getColor() >>> 24) != 255) return false;
        if (w < mScreenW * 0.7f || w > mScreenW * 0.985f || h < 40 * mDensity || h > 650 * mDensity) return false;
        if ((p.getColor() & 0xFFFFFF) == (mBg & 0xFFFFFF)) return false;
        float l = luma(p.getColor());
        return l > 0.05f && l < 0.45f;
    }

    private Paint stroke() {
        if (mStroke == null) { mStroke = new Paint(Paint.ANTI_ALIAS_FLAG); mStroke.setStyle(Paint.Style.STROKE); }
        mStroke.setStrokeWidth(Math.max(1f, mDensity));
        mStroke.setColor(mAccent);
        return mStroke;
    }

    private static final int TAG_WATCH = 0x4f787954;
    private static final int TAG_BAR = 0x4f787955;

    private void watch(final Activity act) {
        final View decor = act.getWindow().getDecorView();
        if (decor.getTag(TAG_WATCH) != null) return;
        decor.setTag(TAG_WATCH, Boolean.TRUE);
        final long[] last = {0};
        decor.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            long now = SystemClock.elapsedRealtime();
            if (now - last[0] < 400) return;
            last[0] = now;
            try { tint(act, decor); } catch (Throwable t) { XposedBridge.log("[ Obsidian ] RisingTabBarMod tint: " + t); }
        });
    }

    private void recolorLabels(View v, int id) {
        if (v instanceof android.widget.TextView && v.getId() == id) {
            android.widget.TextView tv = (android.widget.TextView) v;
            if (tv.getCurrentTextColor() != mAccent) tv.setTextColor(mAccent);
            return;
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) recolorLabels(g.getChildAt(i), id);
        }
    }

    private void tint(Activity act, View decor) {
        refresh();
        int id = act.getResources().getIdentifier("navigation_bar", "id", act.getPackageName());
        if (id == 0) return;
        View bar = decor.findViewById(id);
        if (bar == null) return;
        // scritta della scheda attiva: accento pieno (di serie e' una trasparenza dell'accento). La vista con la scritta grande e' quella della
        // scheda selezionata; si rifa a ogni layout perche' l'app la rimette al cambio di scheda
        try {
            int largeId = act.getResources().getIdentifier("navigation_bar_item_large_label_view", "id", act.getPackageName());
            if (largeId != 0) recolorLabels(bar, largeId);
        } catch (Throwable t) {
            if (bar.getTag(TAG_BAR + 3) == null) { bar.setTag(TAG_BAR + 3, Boolean.TRUE); XposedBridge.log("[ Obsidian ] RisingTabBarMod label colour: " + t); }
        }
        Object mine = bar.getTag(TAG_BAR);
        if (mine != null && bar.getBackground() == mine && mine instanceof LayerDrawable
                && ((Integer) bar.getTag(TAG_BAR + 1)) == (mBg ^ mAccent)) return;
        float line = Math.max(1f, mDensity);
        LayerDrawable ld = new LayerDrawable(new Drawable[]{new ColorDrawable(mBg), new ColorDrawable(mAccent)});
        ld.setLayerHeight(1, (int) Math.ceil(line));
        ld.setLayerGravity(1, Gravity.TOP);
        bar.setBackground(ld);
        bar.setTag(TAG_BAR, ld);
        bar.setTag(TAG_BAR + 1, mBg ^ mAccent);
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
        // barra piatta in basso (vista "navigation_bar"): sfondo del Theme + linea accento sopra, a ogni layout
        XC_MethodHook onShow = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (param.thisObject instanceof Activity) watch((Activity) param.thisObject);
                } catch (Throwable t) { XposedBridge.log("[ Obsidian ] RisingTabBarMod watch: " + t); }
            }
        };
        for (String m : new String[]{"onStart", "onPostResume", "onWindowFocusChanged", "onResume"}) {
            try { XposedBridge.hookAllMethods(Activity.class, m, onShow); } catch (Throwable ignored) {}
        }
        // bordo accento attorno alle card (forme larghe, piene e scure disegnate dall'app)
        XC_MethodHook cardRR = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (mBusy.get() != null) return;
                    Object[] a = param.args;
                    Paint p; float w, h;
                    if (a.length == 7 && a[6] instanceof Paint) { p = (Paint) a[6]; w = (Float) a[2] - (Float) a[0]; h = (Float) a[3] - (Float) a[1]; }
                    else if (a.length == 4 && a[0] instanceof RectF && a[3] instanceof Paint) { p = (Paint) a[3]; w = ((RectF) a[0]).width(); h = ((RectF) a[0]).height(); }
                    else return;
                    refresh();
                    if (isCard(p, w, h)) {
                        param.setObjectExtra("card", Boolean.TRUE);
                        param.setObjectExtra("oc", p.getColor());
                        param.setObjectExtra("cf", p.getColorFilter() == null ? NO_FILTER : p.getColorFilter());
                        p.setColorFilter(null);   // le card hanno una tinta (filtro colore) che copre il colore: si toglie solo per il disegno
                        p.setColor(mBg);
                    }
                } catch (Throwable ignored) {}
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (param.getObjectExtra("card") == null) return;
                    Object[] a = param.args;
                    Paint pp = a.length == 7 ? (Paint) a[6] : (Paint) a[3];
                    pp.setColor((Integer) param.getObjectExtra("oc"));
                    Object cf = param.getObjectExtra("cf");
                    pp.setColorFilter(cf == NO_FILTER ? null : (android.graphics.ColorFilter) cf);
                    Canvas cv = (Canvas) param.thisObject;
                    mBusy.set(Boolean.TRUE);
                    try {
                        if (a.length == 7) {
                            float inset = Math.max(1f, mDensity) / 2f;
                            cv.drawRoundRect((Float) a[0] + inset, (Float) a[1] + inset, (Float) a[2] - inset, (Float) a[3] - inset, (Float) a[4], (Float) a[5], stroke());
                        } else cv.drawRoundRect((RectF) a[0], (Float) a[1], (Float) a[2], stroke());
                    } finally { mBusy.remove(); }
                } catch (Throwable ignored) {}
            }
        };
        XC_MethodHook cardPath = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (mBusy.get() != null || param.args.length != 2 || !(param.args[0] instanceof Path) || !(param.args[1] instanceof Paint)) return;
                    Paint p = (Paint) param.args[1];
                    if (p.getStyle() != Paint.Style.FILL) return;
                    RectF b = new RectF();
                    ((Path) param.args[0]).computeBounds(b, true);
                    refresh();
                    if (isCard(p, b.width(), b.height())) {
                        param.setObjectExtra("card", Boolean.TRUE);
                        param.setObjectExtra("oc", p.getColor());
                        param.setObjectExtra("cf", p.getColorFilter() == null ? NO_FILTER : p.getColorFilter());
                        p.setColorFilter(null);
                        p.setColor(mBg);
                    }
                } catch (Throwable ignored) {}
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (param.getObjectExtra("card") == null) return;
                    Paint pp = (Paint) param.args[1];
                    pp.setColor((Integer) param.getObjectExtra("oc"));
                    Object cf = param.getObjectExtra("cf");
                    pp.setColorFilter(cf == NO_FILTER ? null : (android.graphics.ColorFilter) cf);
                    mBusy.set(Boolean.TRUE);
                    try { ((Canvas) param.thisObject).drawPath((Path) param.args[0], stroke()); } finally { mBusy.remove(); }
                } catch (Throwable ignored) {}
            }
        };
        for (String cls : new String[]{"android.graphics.Canvas", "android.graphics.BaseRecordingCanvas", "android.graphics.RecordingCanvas"}) {
            try {
                Class<?> c = Class.forName(cls);
                XposedBridge.hookAllMethods(c, "drawRoundRect", cardRR);
                XposedBridge.hookAllMethods(c, "drawPath", cardPath);
            } catch (Throwable ignored) {}
        }
    }
}
