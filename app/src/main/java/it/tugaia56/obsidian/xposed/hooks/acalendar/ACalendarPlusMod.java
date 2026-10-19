package it.tugaia56.obsidian.xposed.hooks.acalendar;

import android.app.AndroidAppHelper;
import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.TypedValue;

import java.util.HashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * aCalendar+ (org.withouthat.acalendarplus): mod nascosta, sempre attiva (nessuna impostazione).
 *
 * I temi scuro e nero dell'app hanno gli sfondi scritti come colori fissi dentro gli stili
 * (AppThemeDark / AppThemeBlack). Un overlay di Oxydian Theme su questi stili fa crashare l'app
 * (lo stile attivo viene scartato e "colorTitle" non si risolve), quindi i colori si cambiano qui,
 * al momento in cui l'app li legge dal tema: Theme.resolveAttribute e Theme.obtainStyledAttributes.
 * Un colore si sostituisce solo se (attributo, valore originale) e' nell'elenco: i temi chiari e le
 * altre schermate non vengono toccati. Gli sfondi diventano la famiglia Blue Gray Dark di sistema
 * (background_dark / background_floating_material_dark / button_material_dark).
 *
 * Serve la spunta su aCalendar+ nell'ambito di Oxydian in LSPosed.
 */
public class ACalendarPlusMod extends XposedMods {

    public static final String PKG = "org.withouthat.acalendarplus";

    private static final int STYLE_NUM_ENTRIES = 7;
    private static final int STYLE_TYPE = 0;
    private static final int STYLE_DATA = 1;
    private static final int TYPE_COLOR = TypedValue.TYPE_INT_COLOR_ARGB8;

    // (nome attributo, colore originale) -> colore di sistema: 0 = background_dark, 1 = floating, 2 = button, 3 = ripple, 4 = rosso #ffff0000, 5 = arancione #ffff9800
    private static final Object[][] RULES = {
            // AppThemeDark
            {"colorBgAgenda", 0xFF3A3A3A, 0}, {"colorBgAgendaPast", 0xFF303030, 2},
            {"colorMonth", 0xFF212121, 0}, {"colorOffMonth", 0xFF353535, 1},
            {"colorPrimary", 0xFF212121, 0}, {"colorPrimaryDark", 0xFF000000, 0},
            {"colorSidebar", 0xFF303030, 1}, {"shadedBg", 0xFF212121, 1}, {"colorGrid", 0xFF555555, 2},
            {"colorAccountHeader1", 0xFF212121, 0}, {"colorAccountHeader2", 0xFF212121, 0},
            {"@android:navigationBarColor", 0xFF212121, 0},
            // AppThemeBlack
            {"colorBgAgenda", 0xFF202020, 1}, {"colorBgAgendaPast", 0xFF161616, 0},
            {"colorMonth", 0xFF000000, 0}, {"colorOffMonth", 0xFF202020, 1},
            {"colorPrimary", 0xFF161616, 0}, {"colorSidebar", 0xFF161616, 0},
            {"material_drawer_background", 0xFF202020, 1}, {"shadedBg", 0xFF161616, 0},
            {"@android:navigationBarColor", 0xFF000000, 0},
            {"@android:statusBarColor", 0xFF000000, 1}, {"@android:statusBarColor", 0xFF212121, 1},
            // sabato (blu): arancione #ff9800
            {"colorSaturday", 0xFF2020F0, 5}, {"colorSaturday", 0xFF3030E0, 5},
            {"colorSaturdayOff", 0xFF6060E0, 5}, {"colorSaturdayOff", 0xFF4040E0, 5},
            // domenica: rosso puro al posto dei rosa
            {"colorSunday", 0xFFFF5252, 4}, {"colorSundayOff", 0xFFD95F5F, 4}, {"colorSundayOff", 0xFFFF5564, 4},
    };

    private final HashMap<Long, Integer> mMap = new HashMap<>();
    private volatile boolean mReady = false;
    private volatile int mAccent = 0xFF908DFF;
    private volatile int mRipple = 0x80908DFF;
    private volatile int mButton = 0xFF242832;
    private volatile int mFloating = 0xFF22262F;
    private volatile int mWeekendDerived = 0;

    public ACalendarPlusMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {}

    @Override
    public boolean listensTo(String packageName) {
        return PKG.equals(packageName);
    }

    /** Costruisce la tabella (id attributo, colore originale) -> nuovo colore, appena l'app e' disponibile. */
    private boolean ensureReady() {
        if (mReady) return true;
        try {
            Application app = AndroidAppHelper.currentApplication();
            if (app == null) return false;
            Resources appRes = app.getResources();
            Resources sys = Resources.getSystem();
            int[] bg = new int[6];
            bg[4] = 0xFFFF0000;
            bg[5] = 0xFFFF9800;
            int aid = sys.getIdentifier("accent_material_dark", "color", "android");
            if (aid != 0) mAccent = appRes.getColor(aid, null);
            int rid = sys.getIdentifier("ripple_material_dark", "color", "android");
            if (rid != 0) mRipple = appRes.getColor(rid, null);
            String[] names = {"background_dark", "background_floating_material_dark", "button_material_dark", "ripple_material_dark"};
            for (int i = 0; i < 4; i++) {
                int id = sys.getIdentifier(names[i], "color", "android");
                bg[i] = id != 0 ? appRes.getColor(id, null) : 0xFF1B2029;
            }
            HashMap<Long, Integer> map = new HashMap<>();
            mButton = bg[2];
            mFloating = bg[1];
            int b0 = bg[0];
            mWeekendDerived = 0xFF000000 | ((((b0 >> 16) & 0xFF) + ((255 - ((b0 >> 16) & 0xFF)) * 15) / 255) << 16) | ((((b0 >> 8) & 0xFF) + ((255 - ((b0 >> 8) & 0xFF)) * 15) / 255) << 8) | ((b0 & 0xFF) + ((255 - (b0 & 0xFF)) * 15) / 255);
            for (Object[] r : RULES) {
                String n = (String) r[0];
                int attr = n.startsWith("@android:")
                        ? sys.getIdentifier(n.substring(9), "attr", "android")
                        : appRes.getIdentifier(n, "attr", PKG);
                if (attr == 0) continue;
                map.put(key(attr, (Integer) r[1]), bg[(Integer) r[2]]);
            }
            synchronized (mMap) {
                mMap.clear();
                mMap.putAll(map);
            }
            mReady = true;
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] ACalendarPlusMod init ERROR: " + t);
        }
        return mReady;
    }

    private static long key(int attr, int color) {
        return ((long) attr << 32) | (color & 0xFFFFFFFFL);
    }

    private Integer lookup(int attr, int color) {
        synchronized (mMap) {
            Integer r = mMap.get(key(attr, color));
            return r;
        }
    }

    private void fixTypedArray(TypedArray ta, int[] attrs) {
        if (ta == null || attrs == null || !ensureReady()) return;
        try {
            int[] data = (int[]) XposedHelpers.getObjectField(ta, "mData");
            for (int i = 0; i < attrs.length; i++) {
                int base = i * STYLE_NUM_ENTRIES;
                if (base + STYLE_DATA >= data.length) break;
                int type = data[base + STYLE_TYPE];
                if (type < TypedValue.TYPE_FIRST_COLOR_INT || type > TypedValue.TYPE_LAST_COLOR_INT) continue;
                Integer nc = lookup(attrs[i], data[base + STYLE_DATA]);
                if (nc != null) {
                    data[base + STYLE_TYPE] = TYPE_COLOR;
                    data[base + STYLE_DATA] = nc;
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] ACalendarPlusMod TypedArray ERROR: " + t);
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!PKG.equals(lpparam.packageName)) return;
        Class<?> theme = Resources.Theme.class;

        XposedBridge.hookAllMethods(theme, "resolveAttribute", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (!Boolean.TRUE.equals(param.getResult()) || param.args.length < 2) return;
                    if (!(param.args[0] instanceof Integer) || !(param.args[1] instanceof TypedValue)) return;
                    TypedValue v = (TypedValue) param.args[1];
                    if (v.type < TypedValue.TYPE_FIRST_COLOR_INT || v.type > TypedValue.TYPE_LAST_COLOR_INT) return;
                    if (!ensureReady()) return;
                    Integer nc = lookup((Integer) param.args[0], v.data);
                    if (nc != null) {
                        v.type = TYPE_COLOR;
                        v.data = nc;
                    }
                } catch (Throwable ignored) {}
            }
        });

        XposedBridge.hookAllMethods(theme, "obtainStyledAttributes", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.getResult() instanceof TypedArray)) return;
                int[] attrs = null;
                for (Object a : param.args) {
                    if (a instanceof int[]) { attrs = (int[]) a; break; }
                }
                fixTypedArray((TypedArray) param.getResult(), attrs);
            }
        });
        // Colori scritti nel codice dell'app (non passano dal tema): azzurro degli eventi (#4285f4) -> accento,
        // blu del sabato (blu) -> arancione. Si cambia solo il colore passato ai metodi di disegno.
        XC_MethodHook recolor = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length == 0 || !(param.args[0] instanceof Integer)) return;
                int c = (Integer) param.args[0];
                int rgb = c & 0xFFFFFF;
                boolean sunday = rgb == 0xFF5252 || rgb == 0xD95F5F || rgb == 0xFF5564;
                boolean saturday = rgb == 0x2020F0 || rgb == 0x3030E0 || rgb == 0x6060E0 || rgb == 0x4040E0;
                // cerchi "accento" nei selettori colore di Aspetto: viola chiaro della tavolozza e blu del Tema
                boolean accentChoice = rgb == 0xBA68C8 || rgb == 0x0277BD || rgb == 0x0288D1;
                if (rgb != 0x4285F4 && !saturday && !sunday && !accentChoice) return;
                if (!ensureReady()) return;
                if (sunday) { param.args[0] = (c & 0xFF000000) | 0xFF0000; return; }
                param.args[0] = saturday ? (int) 0xFFFF9800 : ((c & 0xFF000000) | (mAccent & 0xFFFFFF));
            }
        };
        // sfondo di sabato e domenica nell'agenda: l'app lo calcola come sfondo + bianco al 6% e lo applica con
        // setBackgroundColor: si sostituisce con button_material_dark
        XC_MethodHook weekend = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length == 0 || !(param.args[0] instanceof Integer)) return;
                if (!ensureReady()) return;
                if ((Integer) param.args[0] != mWeekendDerived) return;
                param.args[0] = mButton;
            }
        };
        XposedBridge.hookAllMethods(android.view.View.class, "setBackgroundColor", weekend);
        XposedBridge.hookAllMethods(android.graphics.Paint.class, "setColor", recolor);
        XposedBridge.hookAllMethods(android.widget.TextView.class, "setTextColor", recolor);
        XposedBridge.hookAllMethods(android.view.View.class, "setBackgroundColor", recolor);
        XposedBridge.hookAllMethods(android.graphics.drawable.ColorDrawable.class, "setColor", recolor);
        XposedBridge.hookAllConstructors(android.graphics.drawable.ColorDrawable.class, recolor);
        // intestazione (barra del titolo): grigio #212121 dello stile scuro -> background_floating_material_dark
        XC_MethodHook header = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length == 0 || !(param.args[0] instanceof Integer)) return;
                if (((Integer) param.args[0] & 0xFFFFFF) != 0x212121) return;
                if (!ensureReady()) return;
                param.args[0] = (((Integer) param.args[0]) & 0xFF000000) | (mFloating & 0xFFFFFF);
            }
        };
        XposedBridge.hookAllMethods(android.graphics.drawable.ColorDrawable.class, "setColor", header);
        XposedBridge.hookAllConstructors(android.graphics.drawable.ColorDrawable.class, header);
        XposedBridge.hookAllMethods(android.content.res.ColorStateList.class, "valueOf", header);
        XposedBridge.hookAllMethods(android.graphics.Paint.class, "setColor", header);
        XposedBridge.hookAllMethods(android.view.View.class, "setBackgroundColor", header);
        XposedBridge.hookAllMethods(android.graphics.drawable.GradientDrawable.class, "setColor", recolor);
        XposedBridge.hookAllConstructors(android.text.style.ForegroundColorSpan.class, recolor);
        XposedBridge.hookAllMethods(android.widget.ImageView.class, "setColorFilter", recolor);
        try { XposedBridge.hookAllMethods(android.graphics.drawable.Drawable.class, "setTint", recolor); } catch (Throwable ignored) {}
        // intestazione con sfondo a Drawable (ColorDrawable / forma piena #212121)
        XC_MethodHook headerBg = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (param.args.length == 0 || !ensureReady()) return;
                    Object d = param.args[0];
                    if (d instanceof android.graphics.drawable.ColorDrawable) {
                        android.graphics.drawable.ColorDrawable cd = (android.graphics.drawable.ColorDrawable) d;
                        if ((cd.getColor() & 0xFFFFFF) == 0x212121) param.args[0] = new android.graphics.drawable.ColorDrawable(mFloating);
                    } else if (d instanceof android.graphics.drawable.GradientDrawable) {
                        android.graphics.drawable.GradientDrawable gd = (android.graphics.drawable.GradientDrawable) d;
                        android.content.res.ColorStateList c = gd.getColor();
                        if (c != null && (c.getDefaultColor() & 0xFFFFFF) == 0x212121) gd.setColor(mFloating);
                    }
                } catch (Throwable ignored) {}
            }
        };
        XposedBridge.hookAllMethods(android.view.View.class, "setBackground", headerBg);
        XposedBridge.hookAllMethods(android.view.View.class, "setBackgroundDrawable", headerBg);
        log("hook aCalendar+ installati");
    }
}
