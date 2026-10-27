package it.tugaia56.obsidian.xposed.utils;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;

import java.lang.reflect.Field;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Bordo sfumato (2 o 3 colori) per dialog, popup, notifiche, toast e finestre: al posto di
 * GradientDrawable.setStroke(spessore, accento) i vari mod chiamano {@link #setStroke}. Con l'opzione accesa il
 * bordo viene "marcato" dentro il drawable stesso: spessore normale e colore quasi trasparente (alfa 1) che porta
 * nei 24 bit bassi il primo colore. La marcatura sta nello stato del GradientDrawable, quindi resiste a copie e
 * mutate() (il sistema disegna spesso una copia, non l'oggetto che abbiamo creato). Dopo il disegno normale,
 * l'hook su GradientDrawable.draw() riconosce la marcatura e disegna sopra un anello con una sfumatura lineare
 * primo colore -> colore 2 -> (colore 3).
 * Ogni processo legge la scelta da proprieta' di sistema (Oxydian le scrive), perche' le app non vedono le
 * preferenze di Oxydian:
 *   persist.obsidian.grad_on  "1" acceso
 *   persist.obsidian.grad_c1  colore 1 (intero ARGB; vuoto = quello che il mod chiamante usa, l'accento)
 *   persist.obsidian.grad_c2  colore 2 (intero ARGB)
 *   persist.obsidian.grad_c3  colore 3 (intero ARGB, vuoto = solo 2 colori)
 */
public final class GradientBorder {

    public static final String PROP_ON = "persist.obsidian.grad_on";
    public static final String PROP_W  = "persist.obsidian.grad_w";    // spessore in mezzi dp (3 = 1,5 dp, di serie); 0 = come lo stile
    public static final String PROP_C1 = "persist.obsidian.grad_c1";
    public static final String PROP_C2 = "persist.obsidian.grad_c2";
    public static final String PROP_C3 = "persist.obsidian.grad_c3";

    /** Alfa riservato alla marcatura: il bordo "vero" ha alfa 1/255 (invisibile) e lo disegna l'hook. */
    private static final int MARK_ALPHA = 0x01;

    private static volatile boolean sHooked = false;
    private static long sPropsAt = 0;
    private static boolean sOn = false;
    private static int sC1 = 0, sC2 = 0, sC3 = 0;
    private static boolean sHasC1 = false, sHasC2 = false, sHasC3 = false;
    private static int sHalfDp = 0;
    private static Field sStateField, sWidthField, sColorsField;
    private static int sLogged = 0, sDrawLogged = 0, sErrLogged = 0;

    private GradientBorder() {}

    private static String prop(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            return (String) XposedHelpers.callStaticMethod(sp, "get", key, "");
        } catch (Throwable t) {
            return "";
        }
    }

    private static synchronized void refresh() {
        long now = SystemClock.elapsedRealtime();
        if (now - sPropsAt < 2000 && sPropsAt != 0) return;
        sPropsAt = now;
        sOn = "1".equals(prop(PROP_ON));
        String c1 = prop(PROP_C1), c2 = prop(PROP_C2), c3 = prop(PROP_C3);
        Integer hw = parse(prop(PROP_W));
        sHalfDp = hw != null ? hw : 3;   // di serie 1,5 dp
        sHasC1 = parse(c1) != null;
        sHasC2 = parse(c2) != null;
        sHasC3 = parse(c3) != null;
        sC1 = sHasC1 ? parse(c1) : 0;
        sC2 = sHasC2 ? parse(c2) : 0;
        sC3 = sHasC3 ? parse(c3) : 0;
    }

    private static Integer parse(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return (int) Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Opzione accesa e almeno un secondo colore scelto (senza, non c'e' sfumatura: resta il bordo normale). */
    public static boolean enabled() {
        refresh();
        return sOn && (sHasC2 || sHasC3);
    }

    /** Come {@code d.setStroke(width, color)}, ma con il bordo sfumato se l'opzione e' accesa. */
    public static void setStroke(GradientDrawable d, int width, int color) {
        if (d == null || width <= 0) return;
        if (!enabled()) {
            d.setStroke(width, color);
            return;
        }
        mark(d, width, color);
    }

    /** Accento: lo stesso scelto in Oxydian Theme (proprieta' di Oxydian, altrimenti accento di sistema). */
    private static int accent() {
        try {
            String a1 = prop("persist.obsidian.dst.a1");
            if (!a1.isEmpty()) return 0xFF000000 | Integer.parseInt(a1);
        } catch (NumberFormatException ignored) {}
        try {
            android.content.res.Resources r = android.content.res.Resources.getSystem();
            int id = r.getIdentifier("accent_material_dark", "color", "android");
            if (id != 0) return r.getColor(id, null);
        } catch (Throwable ignored) {}
        return 0xFF908DFF;
    }

    /** Come {@link #setStroke}, ma con il bordo sfumato acceso il bordo c'e' sempre: se lo stile scelto non ne ha
     *  (spessore 0) ne mette uno da 2 dp con l'accento. Spento: comportamento di prima (nessun bordo se width = 0). */
    public static void setStrokeOrForce(GradientDrawable d, int width, int color) {
        if (d == null) return;
        if (!enabled()) {
            if (width > 0) d.setStroke(width, color);
            return;
        }
        int w = width > 0 ? width
                : Math.max(2, Math.round(2 * android.content.res.Resources.getSystem().getDisplayMetrics().density));
        mark(d, w, width > 0 ? color : accent());
    }

    private static void mark(GradientDrawable d, int width, int color) {
        ensureHooked();
        d.setStroke(width, (MARK_ALPHA << 24) | (color & 0xFFFFFF));
        if (sLogged < 3) {
            sLogged++;
            XposedBridge.log("[ Obsidian ] GradientBorder: marked " + d.getClass().getName() + " w=" + width);
        }
    }

    private static void ensureHooked() {
        if (sHooked) return;
        synchronized (GradientBorder.class) {
            if (sHooked) return;
            try {
                sStateField = XposedHelpers.findField(GradientDrawable.class, "mGradientState");
                Class<?> st = Class.forName("android.graphics.drawable.GradientDrawable$GradientState");
                sWidthField = XposedHelpers.findField(st, "mStrokeWidth");
                sColorsField = XposedHelpers.findField(st, "mStrokeColors");
                XposedBridge.hookAllMethods(GradientDrawable.class, "draw", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        try {
                            if (!(p.args[0] instanceof Canvas) || !sOn) return;
                            GradientDrawable d = (GradientDrawable) p.thisObject;
                            Object state = sStateField.get(d);
                            if (state == null) return;
                            int width = sWidthField.getInt(state);
                            if (width <= 0) return;
                            ColorStateList csl = (ColorStateList) sColorsField.get(state);
                            if (csl == null) return;
                            int c = csl.getDefaultColor();
                            if ((c >>> 24) != MARK_ALPHA) return;
                            if (sDrawLogged < 3) {
                                sDrawLogged++;
                                XposedBridge.log("[ Obsidian ] GradientBorder: draw " + d.getClass().getName());
                            }
                            draw(d, (Canvas) p.args[0], width, 0xFF000000 | (c & 0xFFFFFF));
                        } catch (Throwable t) {
                            if (sErrLogged < 3) {
                                sErrLogged++;
                                XposedBridge.log("[ Obsidian ] GradientBorder draw error: " + t);
                            }
                        }
                    }
                });
                sHooked = true;
                XposedBridge.log("[ Obsidian ] GradientBorder: draw hook installed");
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] GradientBorder hook failed: " + t);
            }
        }
    }

    private static final ThreadLocal<Paint> sPaint = new ThreadLocal<>();

    private static void draw(GradientDrawable d, Canvas c, int width, int first) {
        refresh();
        if (sHalfDp > 0) {
            width = Math.max(1, Math.round(sHalfDp * 0.5f * android.content.res.Resources.getSystem().getDisplayMetrics().density));
        }
        if (sHasC1) first = sC1;   // colore 1 scelto dall'utente al posto dell'accento
        Rect b = d.getBounds();
        if (b.isEmpty()) return;
        float radius = d.getCornerRadius();
        float[] radii = d.getCornerRadii();
        if (radii != null && radii.length > 0) radius = radii[0];
        float half = width / 2f;
        RectF r = new RectF(b.left + half, b.top + half, b.right - half, b.bottom - half);
        int[] colors;
        if (sHasC2 && sHasC3) colors = new int[]{first, sC2, sC3};
        else if (sHasC2) colors = new int[]{first, sC2};
        else if (sHasC3) colors = new int[]{first, sC3};
        else return;
        Paint p = sPaint.get();
        if (p == null) {
            p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStyle(Paint.Style.STROKE);
            sPaint.set(p);
        }
        p.setStrokeWidth(width);
        p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, colors, null, Shader.TileMode.CLAMP));
        float rr = Math.max(0f, radius - half);
        c.drawRoundRect(r, rr, rr, p);
        p.setShader(null);
    }
}
