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
            {"@android:statusBarColor", 0xFF000000, 0}, {"@android:statusBarColor", 0xFF212121, 0},
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
            mFloating = bg[0];
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

    private static int fillColor(android.graphics.drawable.Drawable d) {
        try {
            if (d instanceof android.graphics.drawable.ColorDrawable) return ((android.graphics.drawable.ColorDrawable) d).getColor();
            if (d instanceof android.graphics.drawable.ShapeDrawable) return ((android.graphics.drawable.ShapeDrawable) d).getPaint().getColor();
            if (d instanceof android.graphics.drawable.GradientDrawable) {
                android.content.res.ColorStateList cs = ((android.graphics.drawable.GradientDrawable) d).getColor();
                if (cs != null) return cs.getDefaultColor();
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    /**
     * Striscia di oggi (stile "Solo data"): il testo e' dentro due contenitori e il secondo ha come sfondo un PaintDrawable nero
     * colorato da un filtro (il colore dichiarato del disegno e' nero, non l'accento). L'app mette il testo nero sui colori chiari:
     * si usa il colore del giorno come negli altri giorni (sabato arancione, domenica rosso), bianco per gli altri.
     */
    private void whitenTextOnAccent(android.widget.TextView tv) {
        try {
            if (!ensureReady() || tv.getBackground() != null) return;
            android.view.ViewParent p = tv.getParent();
            for (int i = 0; i < 3 && p instanceof android.view.View; i++) {
                android.view.View pv = (android.view.View) p;
                android.graphics.drawable.Drawable bg = pv.getBackground();
                if (bg instanceof android.graphics.drawable.PaintDrawable) {
                    String t = String.valueOf(tv.getText()).toLowerCase();
                    int col = 0xFFFFFFFF;
                    if (t.contains("sab") || t.contains("sat")) col = 0xFFFF9800;
                    else if (t.contains("dom") || t.contains("sun")) col = 0xFFFF0000;
                    tv.setTextColor(col);
                    // sfondo della striscia: accento con l'opacita' di "Opacita' tocco" (l'app disegna il colore sempre pieno)
                    bg.setAlpha((mRipple >>> 24) & 0xFF);
                    // parti del testo con uno stile proprio (span dell'app che rimettono il nero): si rifa' il testo tenendo solo
                    // grandezza, grassetto e carattere, con il nostro colore su tutto
                    CharSequence cs = tv.getText();
                    if (cs instanceof android.text.Spanned) {
                        android.text.Spanned sd = (android.text.Spanned) cs;
                        StripColorSpan[] mine = sd.getSpans(0, sd.length(), StripColorSpan.class);
                        if (mine.length > 0 && mine[0].getForegroundColor() == col) return;   // gia' fatto
                        android.text.SpannableString ns = new android.text.SpannableString(cs.toString());
                        for (Object o : sd.getSpans(0, sd.length(), Object.class)) {
                            if (o instanceof android.text.style.RelativeSizeSpan || o instanceof android.text.style.AbsoluteSizeSpan
                                    || o instanceof android.text.style.StyleSpan || o instanceof android.text.style.TypefaceSpan
                                    || o instanceof android.text.style.ScaleXSpan)
                                ns.setSpan(o, sd.getSpanStart(o), sd.getSpanEnd(o), sd.getSpanFlags(o));
                        }
                        ns.setSpan(new StripColorSpan(col), 0, ns.length(), android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                        tv.setText(ns);
                    }
                    return;
                }
                if (bg != null) return;   // un altro sfondo prima: non e' la striscia
                p = pv.getParent();
            }
        } catch (Throwable ignored) {}
    }

    /** Colore del testo della striscia di oggi (sostituito e riaggiunto a ogni aggiornamento). */
    private static final class StripColorSpan extends android.text.style.ForegroundColorSpan {
        StripColorSpan(int c) { super(c); }
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
                boolean pale = rgb == 0x9FC6E7 || rgb == 0x4CAF50;
                if (rgb != 0x4285F4 && !saturday && !sunday && !accentChoice && !pale) return;
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
        // intestazione (barra del titolo): grigio #212121 dello stile scuro -> background_dark
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
        // Colore "Oggi" (Aspetto > Oggi > Colore): se e' il viola della tavolozza (#ba68c8) l'app lo legge dalle sue impostazioni e lo
        // disegna da se': si sostituisce qui con l'accento del Theme (l'opacita' di "Opacita' tocco" si applica al disegno della striscia)
        try {
            Class<?> spi = XposedHelpers.findClass("android.app.SharedPreferencesImpl", lpparam.classLoader);
            XposedBridge.hookAllMethods(spi, "getInt", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args.length < 1 || !(param.args[0] instanceof String)) return;
                        String k = (String) param.args[0];
                        if (!k.toLowerCase().contains("today")) return;
                        Object r = param.getResult();
                        if ("todayColor".equals(k) && r instanceof Integer && (((Integer) r) & 0xFFFFFF) == 0xBA68C8 && ensureReady()) {
                            param.setResult((((Integer) r) & 0xFF000000) | (mAccent & 0xFFFFFF));
                        }
                    } catch (Throwable ignored) {}
                }
            });
            XposedBridge.hookAllMethods(spi, "getString", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args.length < 1 || !(param.args[0] instanceof String)) return;
                        String k = (String) param.args[0];
                        if (!"todayColor".equals(k)) return;
                        Object r = param.getResult();
                        if (!(r instanceof String)) return;
                        int c = (int) Long.parseLong(((String) r).trim());
                        if ((c & 0xFFFFFF) == 0xBA68C8 && ensureReady()) param.setResult(String.valueOf(0xFF000000 | (mAccent & 0xFFFFFF)));
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] ACalendarPlusMod: preference hook failed: " + t);
        }
        // Striscia di oggi con lo sfondo accento: l'app sceglie da sola il testo nero sui colori chiari; si preferisce il bianco.
        // Solo per un testo senza sfondo proprio dentro un contenitore con sfondo pieno uguale all'accento (le barre degli
        // eventi hanno lo sfondo sul testo stesso e restano come sono).
        XposedBridge.hookAllMethods(android.widget.TextView.class, "setTextColor", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (param.args.length == 0 || !(param.args[0] instanceof Integer)) return;
                    int c = (Integer) param.args[0];
                    if (((c >>> 24) & 0xFF) < 0xC0 || android.graphics.Color.luminance(c) > 0.3f) return;   // solo nero / colori scuri quasi opachi
                    final android.widget.TextView tv = (android.widget.TextView) param.thisObject;
                    tv.post(() -> whitenTextOnAccent(tv));
                } catch (Throwable ignored) {}
            }
        });
        // il testo della striscia viene impostato anche dopo il colore: si rifa' il controllo a ogni setText (solo se e' la striscia)
        XposedBridge.hookAllMethods(android.widget.TextView.class, "setText", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    final android.widget.TextView tv = (android.widget.TextView) param.thisObject;
                    if (tv.getBackground() != null) return;
                    android.view.ViewParent p = tv.getParent();
                    if (!(p instanceof android.view.View)) return;
                    android.view.ViewParent pp = ((android.view.View) p).getParent();
                    if (!(pp instanceof android.view.View) || !(((android.view.View) pp).getBackground() instanceof android.graphics.drawable.PaintDrawable)) return;
                    tv.post(() -> whitenTextOnAccent(tv));
                } catch (Throwable ignored) {}
            }
        });
        log("hook aCalendar+ installati");
    }
}
