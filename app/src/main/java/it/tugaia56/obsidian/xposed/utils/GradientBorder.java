package it.tugaia56.obsidian.xposed.utils;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Bordo sfumato (2 o 3 colori) per dialog, popup, notifiche, toast e finestre: al posto di
 * GradientDrawable.setStroke(spessore, accento) i vari mod chiamano {@link #setStroke}; con l'opzione accesa
 * il bordo normale non si imposta e, dopo il disegno dello sfondo, GradientDrawable.draw() disegna un anello
 * con una sfumatura lineare accento -> colore 2 -> (colore 3). Ogni processo legge la scelta da proprieta' di
 * sistema (Oxydian le scrive), perche' le app non vedono le preferenze di Oxydian:
 *   persist.obsidian.grad_on  "1" acceso
 *   persist.obsidian.grad_c2  colore 2 (intero ARGB, vuoto = nessuno)
 *   persist.obsidian.grad_c3  colore 3 (intero ARGB, vuoto = solo 2 colori)
 * Il primo colore e' quello che il mod chiamante avrebbe usato per il bordo (l'accento).
 */
public final class GradientBorder {

    public static final String PROP_ON = "persist.obsidian.grad_on";
    public static final String PROP_C2 = "persist.obsidian.grad_c2";
    public static final String PROP_C3 = "persist.obsidian.grad_c3";

    private static final Map<GradientDrawable, int[]> sMap = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile boolean sHooked = false;
    private static long sPropsAt = 0;
    private static boolean sOn = false;
    private static int sC2 = 0, sC3 = 0;
    private static boolean sHasC2 = false, sHasC3 = false;

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
        String c2 = prop(PROP_C2), c3 = prop(PROP_C3);
        sHasC2 = parse(c2) != null;
        sHasC3 = parse(c3) != null;
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

    public static boolean enabled() {
        refresh();
        return sOn;
    }

    /** Come {@code d.setStroke(width, color)}, ma con il bordo sfumato se l'opzione e' accesa. */
    public static void setStroke(GradientDrawable d, int width, int color) {
        if (d == null || width <= 0) return;
        if (!enabled() || (!sHasC2 && !sHasC3)) {
            d.setStroke(width, color);
            return;
        }
        ensureHooked();
        sMap.put(d, new int[]{width, color});
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
        if (!enabled() || (!sHasC2 && !sHasC3)) {
            if (width > 0) d.setStroke(width, color);
            return;
        }
        int w = width > 0 ? width : Math.max(2, Math.round(2 * android.content.res.Resources.getSystem().getDisplayMetrics().density));
        int c = width > 0 ? color : accent();
        ensureHooked();
        sMap.put(d, new int[]{w, c});
    }

    private static void ensureHooked() {
        if (sHooked) return;
        synchronized (GradientBorder.class) {
            if (sHooked) return;
            try {
                XposedBridge.hookAllMethods(GradientDrawable.class, "draw", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        try {
                            int[] s = sMap.get(p.thisObject);
                            if (s == null || !(p.args[0] instanceof Canvas)) return;
                            draw((GradientDrawable) p.thisObject, (Canvas) p.args[0], s[0], s[1]);
                        } catch (Throwable ignored) {}
                    }
                });
                sHooked = true;
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] GradientBorder hook failed: " + t);
            }
        }
    }

    private static final ThreadLocal<Paint> sPaint = new ThreadLocal<>();

    private static void draw(GradientDrawable d, Canvas c, int width, int first) {
        if (!enabled()) return;
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
        else return;   // senza secondo colore non c'e' sfumatura: resta il bordo a un colore
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
