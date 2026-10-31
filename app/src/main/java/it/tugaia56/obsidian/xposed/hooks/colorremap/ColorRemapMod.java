package it.tugaia56.obsidian.xposed.hooks.colorremap;

import android.app.AndroidAppHelper;
import android.app.Application;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;
import it.tugaia56.obsidian.xposed.utils.AccentUtil;

/**
 * App con i colori scritti nel codice (Compose, React Native, tavolozze proprie) che un overlay del Theme non raggiunge:
 * i colori fissi vengono sostituiti nel momento in cui l'app li passa alle view (Paint, testo, sfondi, filtri, liste di
 * colori, risorse). Ogni app ha una tabella "colore originale (rgb) -> ruolo": ACCENT = accento del Theme, PAGE = sfondo
 * (background_dark), TILE = tessere e barre (button_material_dark), CARD = card (holo_light_primary_dark), PILL = pillola
 * della barra di navigazione (system_secondary_container_dark), WHITE = bianco. La trasparenza originale resta. Solo con il tema scuro.
 * Serve la spunta sull'app nell'ambito di Oxydian in LSPosed. Prima di aggiungere un'app controllare che non abbia PairIP.
 */
public class ColorRemapMod extends XposedMods {

    private static final int ACCENT = 0, PAGE = 1, TILE = 2, CARD = 3, PILL = 4, WHITE = 5;

    private static final Map<String, Map<Integer, Integer>> RULES = new HashMap<>();

    static {
        // Box (com.box.android)
        Map<Integer, Integer> box = new HashMap<>();
        for (int c : new int[]{0x0061D5, 0x7FB0EA, 0x3381DD, 0x0065E3, 0x2972FF, 0x76ACFF, 0x3271EA, 0x4E8FF8, 0x2486FC}) box.put(c, ACCENT);
        box.put(0x222222, PAGE);
        box.put(0x212121, PAGE);
        box.put(0x383838, TILE);
        box.put(0x314358, PILL);
        RULES.put("com.box.android", box);
        // Call Recorder (org.skvalex.cr): riga verde "holo" (#99cc00) sotto le date, colorata nel codice
        Map<Integer, Integer> cr = new HashMap<>();
        cr.put(0x99CC00, ACCENT);
        RULES.put("org.skvalex.cr", cr);
        // Claude (com.anthropic.claude): grigi caldi scritti nel codice -> sfondo e superfici del Theme (l'arancione del marchio resta)
        Map<Integer, Integer> claude = new HashMap<>();
        claude.put(0x151515, PAGE);
        claude.put(0x20201F, TILE);
        claude.put(0x313131, CARD);
        claude.put(0x111111, PAGE);
        RULES.put("com.anthropic.claude", claude);
        // Dreo (com.hesung.dreo): il testo blu #3370ff e' scritto nel bundle JS (React Native); le card del profilo (#303236) cambiano sotto
        Map<Integer, Integer> dreo = new HashMap<>();
        dreo.put(0x3370FF, ACCENT);
        RULES.put("com.hesung.dreo", dreo);
        // Google Drive (com.google.android.apps.docs): sfondo e card arrivano dai colori Material You di sistema (superfici scure)
        Map<Integer, Integer> drive = new HashMap<>();
        drive.put(0x13121C, PAGE);   // system_background_dark / system_surface_dark: pagine e card
        drive.put(0x0E0D17, PAGE);
        drive.put(0x1B1B25, PAGE);   // system_surface_container_low_dark
        drive.put(0x1F1F29, TILE);   // system_surface_container_dark
        drive.put(0x2A2934, CARD);
        drive.put(0x34343F, CARD);
        drive.put(0xCFC0E8, ACCENT); // riquadro attivo di scelta vista (elenco/griglia): sfondo
        drive.put(0x362B4B, WHITE);  // e icona
        RULES.put("com.google.android.apps.docs", drive);
        // F-Droid (org.fdroid.fdroid): interfaccia Compose con colori Material fissi nel codice
        Map<Integer, Integer> fdroid = new HashMap<>();
        fdroid.put(0x111318, PAGE);   // sfondo usato ovunque
        fdroid.put(0x1D2024, PAGE);   // barra di navigazione
        fdroid.put(0xA6C8FF, ACCENT); // icona selezionata della barra di navigazione (e altri blu primari)
        fdroid.put(0x01315E, WHITE);  // icona sopra il colore primario
        fdroid.put(0xBACF82, ACCENT); // testo verde nelle Impostazioni
        RULES.put("org.fdroid.fdroid", fdroid);
    }

    /** Colori che cambiano solo quando l'app li disegna come rettangolo arrotondato (pulsanti, chip): il testo e le icone con lo stesso colore restano come sono. */
    private static final Map<String, Map<Integer, Integer>> FILL_RULES = new HashMap<>();

    static {
        Map<Integer, Integer> claudeFill = new HashMap<>();
        claudeFill.put(0xF9F9F7, ACCENT);   // pulsante "Nuova sessione" e pulsante di invio
        FILL_RULES.put("com.anthropic.claude", claudeFill);
    }

    /** Riempimenti che prendono il colore del tema e un bordo accento (card): colore originale -> ruolo del riempimento. Si agisce al momento del disegno. */
    private static final Map<String, Map<Integer, Integer>> BORDER_FILL = new HashMap<>();

    static {
        Map<Integer, Integer> dreoCards = new HashMap<>();
        dreoCards.put(0x303236, TILE);
        BORDER_FILL.put("com.hesung.dreo", dreoCards);
    }

    /** Barre di navigazione proprie dell'app: classe della vista -> sfondo pieno nel colore delle tessere, a pillola, con bordo accento. */
    private static final Map<String, String> SOLID_BAR = new HashMap<>();

    static {
        SOLID_BAR.put("com.hesung.dreo", "com.hesung.dreo.view.CustPageNavigationView");
    }

    public static boolean handles(String pkg) {
        return RULES.containsKey(pkg);
    }

    private static volatile boolean sReady;
    private static final int[] sColors = new int[6];
    /** load() legge colori con getColor, che e' agganciato: senza guardia si richiamerebbe all'infinito. */
    private static final ThreadLocal<Boolean> sBusy = new ThreadLocal<>();

    private Map<Integer, Integer> mRules;

    public ColorRemapMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {}

    @Override
    public boolean listensTo(String packageName) {
        return RULES.containsKey(packageName);
    }

    private static int resColor(Resources res, String name) {
        int id = Resources.getSystem().getIdentifier(name, "color", "android");
        return id == 0 ? -1 : (res.getColor(id, null) & 0xFFFFFF);
    }

    private static void load() {
        if (sReady || sBusy.get() != null) return;
        sBusy.set(Boolean.TRUE);
        try {
            Application app = AndroidAppHelper.currentApplication();
            if (app == null) return;
            Resources res = app.getResources();
            sColors[ACCENT] = AccentUtil.systemAccent() & 0xFFFFFF;
            sColors[PAGE] = resColor(res, "background_dark");
            sColors[TILE] = resColor(res, "button_material_dark");
            sColors[CARD] = resColor(res, "holo_light_primary_dark");
            sColors[PILL] = resColor(res, "system_secondary_container_dark");
            sColors[WHITE] = 0xFFFFFF;
            sReady = true;
        } catch (Throwable ignored) {
        } finally {
            sBusy.remove();
        }
    }

    private static boolean night() {
        return (Resources.getSystem().getConfiguration().uiMode & 0x30) == 0x20;
    }

    private int map(int c) {
        Integer role = mRules.get(c & 0xFFFFFF);
        if (role == null) return c;
        if (!sReady) load();
        if (!sReady || sColors[role] < 0) return c;
        return (c & 0xFF000000) | sColors[role];
    }

    private final class First extends XC_MethodHook {
        private final String mName;
        private boolean mSeen;

        First(String name) { mName = name; }

        @Override protected void beforeHookedMethod(MethodHookParam p) {
            try {
                if (sBusy.get() != null || !night()) return;
                int c = (Integer) p.args[0], n = map(c);
                if (n != c) {
                    p.args[0] = n;
                    if (!mSeen) {
                        mSeen = true;
                        XposedBridge.log("[ Obsidian ] ColorRemapMod: " + mName + " " + Integer.toHexString(c) + " -> " + Integer.toHexString(n));
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private final class ResultColor extends XC_MethodHook {
        @Override protected void afterHookedMethod(MethodHookParam p) {
            try {
                if (sBusy.get() != null || !night()) return;
                int c = (Integer) p.getResult(), n = map(c);
                if (n != c) p.setResult(n);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        mRules = RULES.get(lp.packageName);
        if (mRules == null) return;
        int ok = 0;
        try { XposedHelpers.findAndHookMethod(Paint.class, "setColor", int.class, new First("Paint.setColor")); ok++; } catch (Throwable t) { log("Paint.setColor", t); }
        try { XposedHelpers.findAndHookMethod(TextView.class, "setTextColor", int.class, new First("TextView.setTextColor")); ok++; } catch (Throwable t) { log("TextView.setTextColor", t); }
        try { XposedHelpers.findAndHookMethod(View.class, "setBackgroundColor", int.class, new First("View.setBackgroundColor")); ok++; } catch (Throwable t) { log("View.setBackgroundColor", t); }
        try { XposedHelpers.findAndHookMethod(ColorDrawable.class, "setColor", int.class, new First("ColorDrawable.setColor")); ok++; } catch (Throwable t) { log("ColorDrawable.setColor", t); }
        try { XposedHelpers.findAndHookConstructor(ColorDrawable.class, int.class, new First("ColorDrawable()")); ok++; } catch (Throwable t) { log("ColorDrawable()", t); }
        try { XposedHelpers.findAndHookMethod(GradientDrawable.class, "setColor", int.class, new First("GradientDrawable.setColor")); ok++; } catch (Throwable t) { log("GradientDrawable.setColor", t); }
        try { XposedHelpers.findAndHookMethod(Canvas.class, "drawColor", int.class, new First("Canvas.drawColor")); ok++; } catch (Throwable t) { log("Canvas.drawColor", t); }
        try { XposedHelpers.findAndHookConstructor(PorterDuffColorFilter.class, int.class, PorterDuff.Mode.class, new First("PorterDuffColorFilter()")); ok++; } catch (Throwable t) { log("PorterDuffColorFilter()", t); }
        try { XposedHelpers.findAndHookConstructor(android.graphics.BlendModeColorFilter.class, int.class, android.graphics.BlendMode.class, new First("BlendModeColorFilter()")); ok++; } catch (Throwable t) { log("BlendModeColorFilter()", t); }
        try { XposedHelpers.findAndHookMethod(Drawable.class, "setColorFilter", int.class, PorterDuff.Mode.class, new First("Drawable.setColorFilter")); ok++; } catch (Throwable t) { log("Drawable.setColorFilter", t); }
        try { XposedHelpers.findAndHookMethod(Drawable.class, "setTint", int.class, new First("Drawable.setTint")); ok++; } catch (Throwable t) { log("Drawable.setTint", t); }
        try { XposedHelpers.findAndHookMethod(android.widget.ImageView.class, "setColorFilter", int.class, new First("ImageView.setColorFilter")); ok++; } catch (Throwable t) { log("ImageView.setColorFilter", t); }
        try { XposedHelpers.findAndHookConstructor(ForegroundColorSpan.class, int.class, new First("ForegroundColorSpan()")); ok++; } catch (Throwable t) { log("ForegroundColorSpan()", t); }
        try { XposedHelpers.findAndHookMethod(ColorStateList.class, "valueOf", int.class, new First("ColorStateList.valueOf")); ok++; } catch (Throwable t) { log("ColorStateList.valueOf", t); }
        try {
            XposedHelpers.findAndHookConstructor(ColorStateList.class, int[][].class, int[].class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        int[] a = (int[]) p.args[1];
                        if (a == null || sBusy.get() != null || !night()) return;
                        int[] b = null;
                        for (int i = 0; i < a.length; i++) {
                            int n = map(a[i]);
                            if (n != a[i]) {
                                if (b == null) b = a.clone();
                                b[i] = n;
                            }
                        }
                        if (b != null) p.args[1] = b;
                    } catch (Throwable ignored) {}
                }
            });
            ok++;
        } catch (Throwable t) { log("ColorStateList()", t); }
        try {
            XposedHelpers.findAndHookMethod(Paint.class, "setColor", long.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (sBusy.get() != null || !night()) return;
                        int c = Color.toArgb((Long) p.args[0]), n = map(c);
                        if (n != c) p.args[0] = Color.pack(n);
                    } catch (Throwable ignored) {}
                }
            });
            ok++;
        } catch (Throwable t) { log("Paint.setColor(long)", t); }
        // indicatore attivo ("pillola") della barra di navigazione Material: colore dell'accento
        for (String cn : new String[]{"com.google.android.material.navigation.NavigationBarView",
                "com.google.android.material.navigation.NavigationBarMenuView"}) {
            try {
                Class<?> nc = XposedHelpers.findClassIfExists(cn, lp.classLoader);
                if (nc == null) continue;
                XposedHelpers.findAndHookMethod(nc, "setItemActiveIndicatorColor", ColorStateList.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            if (p.args[0] == null || sBusy.get() != null || !night()) return;
                            if (!sReady) load();
                            if (sReady) p.args[0] = ColorStateList.valueOf(0xFF000000 | sColors[ACCENT]);
                        } catch (Throwable ignored) {}
                    }
                });
                ok++;
            } catch (Throwable t) { log(cn + ".setItemActiveIndicatorColor", t); }
        }
        // sfondi a tinta unita presi da attributi/risorse (ColorDrawable): il colore si sostituisce sul drawable prima che la view lo usi
        try {
            XposedHelpers.findAndHookMethod(View.class, "setBackground", Drawable.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (!(p.args[0] instanceof ColorDrawable) || sBusy.get() != null || !night()) return;
                        ColorDrawable d = (ColorDrawable) p.args[0];
                        int c = d.getColor(), n = map(c);
                        if (n != c) {
                            ColorDrawable m = (ColorDrawable) d.mutate();
                            m.setColor(n);
                        }
                    } catch (Throwable ignored) {}
                }
            });
            ok++;
        } catch (Throwable t) { log("View.setBackground", t); }
        // gradienti (Compose usa LinearGradient per le sfumature, ad esempio sotto l'header): si mappano i colori delle tappe
        try {
            XposedHelpers.findAndHookConstructor(android.graphics.LinearGradient.class, float.class, float.class, float.class, float.class,
                    int[].class, float[].class, android.graphics.Shader.TileMode.class, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                int[] a = (int[]) p.args[4];
                                if (a == null || sBusy.get() != null || !night()) return;
                                int[] b = null;
                                for (int i = 0; i < a.length; i++) {
                                    int n = map(a[i]);
                                    if (n != a[i]) {
                                        if (b == null) b = a.clone();
                                        b[i] = n;
                                    }
                                }
                                if (b != null) p.args[4] = b;
                            } catch (Throwable ignored) {}
                        }
                    });
            ok++;
        } catch (Throwable t) { log("LinearGradient(int[])", t); }
        try {
            XposedHelpers.findAndHookConstructor(android.graphics.LinearGradient.class, float.class, float.class, float.class, float.class,
                    int.class, int.class, android.graphics.Shader.TileMode.class, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                if (sBusy.get() != null || !night()) return;
                                for (int i = 4; i <= 5; i++) {
                                    int c = (Integer) p.args[i], n = map(c);
                                    if (n != c) p.args[i] = n;
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
            ok++;
        } catch (Throwable t) { log("LinearGradient(int,int)", t); }
        final Map<Integer, Integer> fill = FILL_RULES.get(lp.packageName);
        if (fill != null) {
            try {
                Class<?> rc = XposedHelpers.findClassIfExists("android.graphics.BaseRecordingCanvas", lp.classLoader);
                if (rc != null) {
                    XC_MethodHook rr = new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                if (sBusy.get() != null || !night()) return;
                                Paint paint = (Paint) p.args[p.args.length - 1];
                                int c = paint.getColor();
                                Integer role = fill.get(c & 0xFFFFFF);
                                if (role == null) return;
                                if (!sReady) load();
                                if (!sReady || sColors[role] < 0) return;
                                p.setObjectExtra("old", c);
                                paint.setColor((c & 0xFF000000) | sColors[role]);
                            } catch (Throwable ignored) {}
                        }

                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                Object old = p.getObjectExtra("old");
                                if (old != null) ((Paint) p.args[p.args.length - 1]).setColor((Integer) old);
                            } catch (Throwable ignored) {}
                        }
                    };
                    XposedHelpers.findAndHookMethod(rc, "drawRoundRect", float.class, float.class, float.class, float.class, float.class, float.class, Paint.class, rr);
                    XposedHelpers.findAndHookMethod(rc, "drawRoundRect", android.graphics.RectF.class, float.class, float.class, Paint.class, rr);
                    ok += 2;
                    // pulsanti tondi o a pillola disegnati come cerchio/ovale/tracciato: solo se grandi (le icone, piccole, restano come sono)
                    final float dens = Resources.getSystem().getDisplayMetrics().density;
                    XC_MethodHook big = new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                if (sBusy.get() != null || !night()) return;
                                Paint paint = (Paint) p.args[p.args.length - 1];
                                int c = paint.getColor();
                                Integer role = fill.get(c & 0xFFFFFF);
                                if (role == null) return;
                                float minSide;
                                Object a0 = p.args[0];
                                if (a0 instanceof android.graphics.Path) {
                                    android.graphics.RectF b = new android.graphics.RectF();
                                    ((android.graphics.Path) a0).computeBounds(b, true);
                                    minSide = Math.min(b.width(), b.height());
                                } else if (a0 instanceof Float && p.args.length == 4) {   // drawCircle(cx, cy, radius, paint)
                                    minSide = 2f * (Float) p.args[2];
                                } else {
                                    return;
                                }
                                if (minSide < 36f * dens) return;
                                if (!sReady) load();
                                if (!sReady || sColors[role] < 0) return;
                                p.setObjectExtra("old", c);
                                paint.setColor((c & 0xFF000000) | sColors[role]);
                            } catch (Throwable ignored) {}
                        }

                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                Object old = p.getObjectExtra("old");
                                if (old != null) ((Paint) p.args[p.args.length - 1]).setColor((Integer) old);
                            } catch (Throwable ignored) {}
                        }
                    };
                    XposedHelpers.findAndHookMethod(rc, "drawPath", android.graphics.Path.class, Paint.class, big);
                    XposedHelpers.findAndHookMethod(rc, "drawCircle", float.class, float.class, float.class, Paint.class, big);
                    ok += 2;
                }
            } catch (Throwable t) { log("BaseRecordingCanvas.drawRoundRect", t); }
        }
        final Map<Integer, Integer> bf = BORDER_FILL.get(lp.packageName);
        if (bf != null) {
            try {
                Class<?> rc = XposedHelpers.findClassIfExists("android.graphics.BaseRecordingCanvas", lp.classLoader);
                if (rc != null) {
                    final float dens = Resources.getSystem().getDisplayMetrics().density;
                    final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
                    stroke.setStyle(Paint.Style.STROKE);
                    final android.graphics.RectF tmp = new android.graphics.RectF();
                    XC_MethodHook card = new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                if (sBusy.get() != null || !night()) return;
                                Paint paint = (Paint) p.args[p.args.length - 1];
                                int c = paint.getColor();
                                Integer role = bf.get(c & 0xFFFFFF);
                                if (role == null || paint.getStyle() == Paint.Style.STROKE) return;
                                if (!sReady) load();
                                if (!sReady || sColors[role] < 0) return;
                                p.setObjectExtra("old", c);
                                paint.setColor((c & 0xFF000000) | sColors[role]);
                            } catch (Throwable ignored) {}
                        }

                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                Object old = p.getObjectExtra("old");
                                if (old == null) return;
                                Paint paint = (Paint) p.args[p.args.length - 1];
                                paint.setColor((Integer) old);
                                Canvas cv = (Canvas) p.thisObject;
                                float w = 1.5f * dens;
                                stroke.setStrokeWidth(w);
                                stroke.setColor(0xFF000000 | sColors[ACCENT]);
                                String n = p.method.getName();
                                Object a0 = p.args[0];
                                if (a0 instanceof Float) {                       // coordinate float (rect o rect arrotondato)
                                    tmp.set((Float) a0, (Float) p.args[1], (Float) p.args[2], (Float) p.args[3]);
                                    tmp.inset(w / 2f, w / 2f);
                                    if (n.equals("drawRoundRect")) cv.drawRoundRect(tmp, (Float) p.args[4], (Float) p.args[5], stroke);
                                    else cv.drawRect(tmp, stroke);
                                } else if (a0 instanceof android.graphics.RectF) {
                                    tmp.set((android.graphics.RectF) a0);
                                    tmp.inset(w / 2f, w / 2f);
                                    if (n.equals("drawRoundRect")) cv.drawRoundRect(tmp, (Float) p.args[1], (Float) p.args[2], stroke);
                                    else cv.drawRect(tmp, stroke);
                                } else if (a0 instanceof android.graphics.Path) {
                                    cv.drawPath((android.graphics.Path) a0, stroke);
                                }
                            } catch (Throwable ignored) {}
                        }
                    };
                    XposedHelpers.findAndHookMethod(rc, "drawRoundRect", float.class, float.class, float.class, float.class, float.class, float.class, Paint.class, card);
                    XposedHelpers.findAndHookMethod(rc, "drawRoundRect", android.graphics.RectF.class, float.class, float.class, Paint.class, card);
                    XposedHelpers.findAndHookMethod(rc, "drawRect", float.class, float.class, float.class, float.class, Paint.class, card);
                    XposedHelpers.findAndHookMethod(rc, "drawRect", android.graphics.RectF.class, Paint.class, card);
                    XposedHelpers.findAndHookMethod(rc, "drawPath", android.graphics.Path.class, Paint.class, card);
                    ok += 5;
                }
            } catch (Throwable t) { log("card border hooks", t); }
        }
        final String barClass = SOLID_BAR.get(lp.packageName);
        if (barClass != null) {
            try {
                Class<?> bc = XposedHelpers.findClassIfExists(barClass, lp.classLoader);
                if (bc != null) {
                    final float dens = Resources.getSystem().getDisplayMetrics().density;
                    final Paint fillP = new Paint(Paint.ANTI_ALIAS_FLAG);
                    final Paint strokeP = new Paint(Paint.ANTI_ALIAS_FLAG);
                    strokeP.setStyle(Paint.Style.STROKE);
                    final android.graphics.RectF box = new android.graphics.RectF();
                    final Class<?> barCls = bc;
                    // la vista disegna da sola la barra (fondo semitrasparente e bordo sfumato): prima delle icone ci si disegna sopra una pillola piena
                    XposedHelpers.findAndHookMethod(android.view.ViewGroup.class, "dispatchDraw", Canvas.class, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                if (p.thisObject.getClass() != barCls || !night()) return;
                                if (!sReady) load();
                                if (!sReady) return;
                                View v = (View) p.thisObject;
                                float w = 1.5f * dens;
                                box.set(w / 2f, w / 2f, v.getWidth() - w / 2f, v.getHeight() - w / 2f);
                                float r = box.height() / 2f;
                                fillP.setColor(0xFF000000 | sColors[TILE]);
                                strokeP.setStrokeWidth(w);
                                strokeP.setColor(0xFF000000 | sColors[ACCENT]);
                                Canvas c = (Canvas) p.args[0];
                                c.drawRoundRect(box, r, r, fillP);
                                c.drawRoundRect(box, r, r, strokeP);
                            } catch (Throwable ignored) {}
                        }
                    });
                    ok++;
                    ok++;
                } else {
                    XposedBridge.log("[ Obsidian ] ColorRemapMod: " + barClass + " not found");
                }
            } catch (Throwable t) { log("solid bar", t); }
        }
        ResultColor after = new ResultColor();
        try { XposedHelpers.findAndHookMethod(Resources.class, "getColor", int.class, Resources.Theme.class, after); ok++; } catch (Throwable t) { log("Resources.getColor", t); }
        try { XposedHelpers.findAndHookMethod(TypedArray.class, "getColor", int.class, int.class, after); ok++; } catch (Throwable t) { log("TypedArray.getColor", t); }
        XposedBridge.log("[ Obsidian ] ColorRemapMod: " + ok + " colour hooks installed in " + lp.packageName);
    }

    private static void log(String what, Throwable t) {
        XposedBridge.log("[ Obsidian ] ColorRemapMod: hook " + what + " failed: " + t);
    }
}
