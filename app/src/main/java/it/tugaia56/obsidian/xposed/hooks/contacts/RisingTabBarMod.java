package it.tugaia56.obsidian.xposed.hooks.contacts;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
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

    private void tint(Activity act, View decor) {
        refresh();
        int id = act.getResources().getIdentifier("navigation_bar", "id", act.getPackageName());
        if (id == 0) return;
        View bar = decor.findViewById(id);
        if (bar == null) return;
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
    }
}
