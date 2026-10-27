package it.tugaia56.obsidian.xposed.hooks.contacts;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Google Contatti. L'app dichiara una sola risorsa "overlayable" (e non e' un colore), quindi gli
 * overlay di Oxydian Theme non possono cambiarla (STATE_NO_IDMAP: "no resources were overlaid").
 * Si lavora quindi da dentro l'app:
 *  - l'elenco dei contatti e' scritto in Compose: i colori non passano dalle risorse, quindi (come per
 *    le bolle di Messaggi) si interviene sul disegno, Canvas.drawRoundRect/drawPath: le card larghe e
 *    scure prendono lo sfondo scelto, e con "con bordo" un bordo nell'accento;
 *  - pulsante +: View normale, si tinge con l'accento da un listener di layout; la pillola della scheda
 *    selezionata si riconosce dal disegno (piena, 56 x 32 dp) e prende l'accento;
 *  - sostituzioni risorse di Xposed per i colori dinamici dell'app (applyResources).
 *
 * La scelta delle card (con/senza bordo) arriva da una proprieta' di sistema, perche' Contatti non
 * vede le preferenze di Oxydian. Serve la spunta su Contatti nell'ambito di Oxydian in LSPosed.
 */
public class ContactsMod extends XposedMods {

    public static final String PKG = "com.google.android.contacts";
    public static final String PROP_BORDER = "persist.obsidian.ct_border";   // "1" con bordo, "0" senza bordo (predefinito)
    private static final int BLUE_GRAY_DARK = 0xFF1B2029;

    private volatile boolean mBorder = false;
    private volatile int mAccent = 0xFF908DFF;
    private volatile int mFillNoBorder = 0xFF2B2F3A;
    private long mPropsAt = 0;
    private float mDensity = 3f;
    private float mScreenW = 1080f;
    private Paint mStroke;
    private final ThreadLocal<Boolean> mBusy = new ThreadLocal<>();

    public ContactsMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {}

    @Override
    public boolean listensTo(String packageName) {
        return PKG.equals(packageName);
    }

    private static String prop(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            return (String) XposedHelpers.callStaticMethod(sp, "get", key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    /** Accento scelto dall'utente (Oxydian lo scrive in una proprieta' leggibile da ogni processo). */
    static int accent() {
        try {
            String a1 = prop("persist.obsidian.dst.a1", "");
            if (!a1.isEmpty()) return 0xFF000000 | Integer.parseInt(a1);
        } catch (NumberFormatException ignored) {}
        try {
            Resources r = appResources();
            int id = r.getIdentifier("accent_material_dark", "color", "android");
            if (id != 0) return r.getColor(id, null);
        } catch (Throwable ignored) {}
        return 0xFF908DFF;
    }

    /** Risorse dell'app (con gli overlay di Oxydian Theme applicati): Resources.getSystem() ha i colori originali di
     *  Android, non quelli del tema, e dava il colore "strano" alle card senza bordo. */
    private static Resources appResources() {
        try {
            android.app.Application app = android.app.AndroidAppHelper.currentApplication();
            if (app != null) return app.getResources();
        } catch (Throwable ignored) {}
        return Resources.getSystem();
    }

    private void refreshProps() {
        long now = SystemClock.elapsedRealtime();
        if (now - mPropsAt < 2000) return;
        mPropsAt = now;
        mBorder = "1".equals(prop(PROP_BORDER, "0"));
        mAccent = accent();
        try {
            Resources r = appResources();
            mDensity = r.getDisplayMetrics().density;
            mScreenW = r.getDisplayMetrics().widthPixels;
            int id = r.getIdentifier("button_material_dark", "color", "android");
            if (id != 0) mFillNoBorder = r.getColor(id, null);
        } catch (Throwable ignored) {}
    }

    private static float luma(int c) {
        return (0.299f * ((c >> 16) & 0xFF) + 0.587f * ((c >> 8) & 0xFF) + 0.114f * (c & 0xFF)) / 255f;
    }

    /** Card della lista: forma piena, opaca, larga (quasi tutto lo schermo), bassa, di colore scuro. */
    private boolean isCard(Paint p, float w, float h) {
        if (p.getStyle() != Paint.Style.FILL || p.getShader() != null) return false;
        int c = p.getColor();
        if ((c >>> 24) != 255) return false;
        // niente barra di navigazione a tutta larghezza: le card dei contatti hanno un margine ai lati
        if (w < mScreenW * 0.7f || w > mScreenW * 0.985f || h < 40 * mDensity || h > 160 * mDensity) return false;
        float l = luma(c);
        return l > 0.08f && l < 0.45f;
    }

    /** Riquadro della scheda selezionata nella barra in basso: pillola di circa 56 x 32 dp, piena e opaca. */
    private boolean isPill(Paint p, float w, float h) {
        if (p.getStyle() != Paint.Style.FILL || p.getShader() != null) return false;
        if ((p.getColor() >>> 24) < 200) return false;
        return w >= 48 * mDensity && w <= 72 * mDensity && h >= 28 * mDensity && h <= 38 * mDensity;
    }

    private int fill() {
        return mBorder ? BLUE_GRAY_DARK : mFillNoBorder;
    }

    private Paint stroke() {
        if (mStroke == null) {
            mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            mStroke.setStyle(Paint.Style.STROKE);
        }
        mStroke.setStrokeWidth(Math.max(1f, mDensity));
        mStroke.setColor(mAccent);
        return mStroke;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XC_MethodHook roundRect = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (mBusy.get() != null) return;
                    refreshProps();
                    Object[] a = param.args;
                    Paint p; float l, t, r, b;
                    if (a.length == 7 && a[6] instanceof Paint) {
                        p = (Paint) a[6];
                        l = (Float) a[0]; t = (Float) a[1]; r = (Float) a[2]; b = (Float) a[3];
                    } else if (a.length == 4 && a[0] instanceof RectF && a[3] instanceof Paint) {
                        p = (Paint) a[3];
                        RectF rc = (RectF) a[0];
                        l = rc.left; t = rc.top; r = rc.right; b = rc.bottom;
                    } else return;
                    float w = r - l, h = b - t;
                    if (isPill(p, w, h)) {
                        param.setObjectExtra("oc", p.getColor());
                        p.setColor(mAccent);
                        return;
                    }
                    if (!isCard(p, w, h)) return;
                    param.setObjectExtra("oc", p.getColor());
                    param.setObjectExtra("card", Boolean.TRUE);
                    p.setColor(fill());
                } catch (Throwable ignored) {}
            }

            @Override protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Object oc = param.getObjectExtra("oc");
                    if (oc == null) return;
                    Object[] a = param.args;
                    Paint p = a.length == 7 ? (Paint) a[6] : (Paint) a[3];
                    p.setColor((Integer) oc);
                    if (!mBorder || param.getObjectExtra("card") == null) return;
                    mBusy.set(Boolean.TRUE);
                    try {
                        Canvas cv = (Canvas) param.thisObject;
                        if (a.length == 7) {
                            float inset = Math.max(1f, mDensity) / 2f;
                            cv.drawRoundRect((Float) a[0] + inset, (Float) a[1] + inset, (Float) a[2] - inset, (Float) a[3] - inset,
                                    (Float) a[4], (Float) a[5], stroke());
                        } else {
                            cv.drawRoundRect((RectF) a[0], (Float) a[1], (Float) a[2], stroke());
                        }
                    } finally {
                        mBusy.remove();
                    }
                } catch (Throwable ignored) {}
            }
        };
        XC_MethodHook drawPath = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (mBusy.get() != null) return;
                    refreshProps();
                    if (param.args.length != 2 || !(param.args[0] instanceof Path) || !(param.args[1] instanceof Paint)) return;
                    Path path = (Path) param.args[0];
                    Paint p = (Paint) param.args[1];
                    if (p.getStyle() != Paint.Style.FILL || p.getShader() != null) return;
                    RectF b = new RectF();
                    path.computeBounds(b, true);
                    if (isPill(p, b.width(), b.height())) {
                        param.setObjectExtra("oc", p.getColor());
                        p.setColor(mAccent);
                        return;
                    }
                    if (!isCard(p, b.width(), b.height())) return;
                    param.setObjectExtra("oc", p.getColor());
                    param.setObjectExtra("card", Boolean.TRUE);
                    p.setColor(fill());
                } catch (Throwable ignored) {}
            }

            @Override protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Object oc = param.getObjectExtra("oc");
                    if (oc == null) return;
                    Paint p = (Paint) param.args[1];
                    p.setColor((Integer) oc);
                    if (!mBorder || param.getObjectExtra("card") == null) return;
                    mBusy.set(Boolean.TRUE);
                    try {
                        ((Canvas) param.thisObject).drawPath((Path) param.args[0], stroke());
                    } finally {
                        mBusy.remove();
                    }
                } catch (Throwable ignored) {}
            }
        };
        for (String cls : new String[]{"android.graphics.Canvas", "android.graphics.BaseRecordingCanvas",
                "android.graphics.RecordingCanvas"}) {
            try {
                Class<?> c = Class.forName(cls);
                XposedBridge.hookAllMethods(c, "drawRoundRect", roundRect);
                XposedBridge.hookAllMethods(c, "drawPath", drawPath);
            } catch (Throwable ignored) {}
        }

        // Pulsante + e scheda selezionata: View normali, si tingono con l'accento a ogni layout.
        // Quando l'activity compare: piu' punti di aggancio, perche' alcuni onResume sono "inlined" dal runtime.
        XC_MethodHook onShow = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (param.thisObject instanceof Activity) watch((Activity) param.thisObject);
                } catch (Throwable t) { XposedBridge.log("[ Obsidian ] ContactsMod watch: " + t); }
            }
        };
        for (String m : new String[]{"onStart", "onPostResume", "onWindowFocusChanged", "onResume"}) {
            try { XposedBridge.hookAllMethods(Activity.class, m, onShow); } catch (Throwable ignored) {}
        }
        try {
            Class<?> people = XposedHelpers.findClassIfExists("com.android.contacts.activities.PeopleActivity", lpparam.classLoader);
            if (people != null) for (String m : new String[]{"onResume", "onStart", "onPostResume"}) {
                try { XposedBridge.hookAllMethods(people, m, onShow); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        XposedBridge.log("[ Obsidian ] ContactsMod: canvas hooks installed");
    }

    private static final int TAG_WATCH = 0x4f787943;

    private void watch(final Activity act) {
        final View decor = act.getWindow().getDecorView();
        if (decor.getTag(TAG_WATCH) != null) return;
        decor.setTag(TAG_WATCH, Boolean.TRUE);
        final long[] last = {0};
        decor.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            long now = SystemClock.elapsedRealtime();
            if (now - last[0] < 400) return;
            last[0] = now;
            try { tint(act, decor); } catch (Throwable t) { XposedBridge.log("[ Obsidian ] ContactsMod tint: " + t); }
        });
    }

    private void tint(Activity act, View decor) {
        refreshProps();
        Resources r = act.getResources();
        int accent = mAccent;
        int fabId = r.getIdentifier("floating_action_button", "id", PKG);
        if (fabId != 0) {
            View fab = decor.findViewById(fabId);
            if (fab != null) {
                fab.setBackgroundTintList(ColorStateList.valueOf(accent));
                if (fab instanceof ImageView) ((ImageView) fab).setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
            }
        }
    }

    /** Sostituzioni delle risorse colore dell'app (sfondo, pulsante +, scheda selezionata). */
    public static void applyResources(XC_InitPackageResources.InitPackageResourcesParam resparam) {
        if (!PKG.equals(resparam.packageName)) return;
        final int bg = BLUE_GRAY_DARK, acc = accent(), white = 0xFFFFFFFF;
        String[][] map = {
                {"background", "b"}, {"surface", "b"}, {"surface_bright", "b"}, {"surface_dim", "b"},
                {"surface_container", "b"}, {"surface_container_low", "b"}, {"surface_container_lowest", "b"},
                {"surface_container_high", "b"}, {"surface_container_highest", "b"}, {"surface_variant", "b"},
                {"primary_container", "a"}, {"secondary_container", "a"},
                {"on_primary_container", "w"}, {"on_secondary_container", "w"},
        };
        for (String[] m : map) {
            int value = "b".equals(m[1]) ? bg : "a".equals(m[1]) ? acc : white;
            try {
                resparam.res.setReplacement(PKG, "color", "gm3_sys_color_dynamic_dark_" + m[0], value);
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] ContactsMod res " + m[0] + ": " + t);
            }
        }
        XposedBridge.log("[ Obsidian ] ContactsMod: colour replacements registered");
    }
}
