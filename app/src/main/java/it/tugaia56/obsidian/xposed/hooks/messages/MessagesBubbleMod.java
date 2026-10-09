package it.tugaia56.obsidian.xposed.hooks.messages;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Google Messaggi: bolle della chat. La chat e' scritta in Compose, quindi i colori delle bolle non
 * passano dalle risorse (verificato provando oltre 140 colori con overlay: nessun effetto). Si
 * lavora a livello di disegno: Compose, alla fine, chiama Canvas.drawRoundRect/drawPath.
 *
 * Misurato sul telefono: le bolle sono forme riempite con angoli di 20 dp; quelle ricevute (IN)
 * hanno il colore scuro del tema, quelle inviate (OUT) un grigio chiaro.
 *
 *  - Colore delle bolle inviate: stock / scuro come le IN / accento al 50%.
 *  - Bordo sottile attorno a tutte le bolle (bianco sulle IN, accento sulle OUT).
 *  - Angolo a punta (0 dp): in basso a destra sulle OUT, in basso a sinistra sulle IN.
 *
 * Le scelte arrivano da proprieta' di sistema (persist.obsidian.msg_*), perche' l'app Messaggi non
 * vede le preferenze di Oxydian (stesso metodo usato dagli hook SystemUI per gli stili).
 */
public class MessagesBubbleMod extends XposedMods {

    public static final String PKG = "com.google.android.apps.messaging";
    public static final String PROP_OUT = "persist.obsidian.msg_out_dark";   // "0" stock, "1" scure come le IN, "2" accento al 50%
    public static final String PROP_BORDER = "persist.obsidian.msg_border";  // "0" spento, "1".."8" spessore (mezzi dp)
    public static final String PROP_TAIL = "persist.obsidian.msg_tail";      // "1" angolo a punta
    public static final String PROP_BORDER_IN = "persist.obsidian.msg_border_in";   // "accent" o colore ARGB (intero)
    public static final String PROP_BORDER_OUT = "persist.obsidian.msg_border_out"; // "accent" o colore ARGB (intero)

    private static final int DEFAULT_IN_COLOR = 0xFF1C2029;
    private static final float BUBBLE_RADIUS_DP = 20f;

    private volatile int mInColor = DEFAULT_IN_COLOR;
    private volatile int mOutMode = 2;
    private volatile int mBorderHalfDp = 2;
    private volatile boolean mTail = true;
    private long mPropsAt = 0;
    private Paint mStroke;
    private Paint mStrokeIn;
    private int mStrokeColor = 0xFF908DFF;      // accento dell'utente
    private volatile int mBorderInColor = 0xFFFFFFFF;
    private volatile int mBorderOutColor = 0xFF908DFF;
    private float mDensity = 3f;
    private final ThreadLocal<Boolean> mBusy = new ThreadLocal<>();
    // Testo delle bolle inviate: quando la bolla cambia colore, il testo (scuro) va reso chiaro.
    // mInOut = l'ultima forma grande disegnata e' una bolla OUT; mLastIn = e' una bolla IN.
    private volatile boolean mInOut = false;
    private volatile boolean mLastIn = false;
    private volatile int mInText = 0xFFEAEAFC;
    // Colori scuri del testo visti dentro le bolle OUT: se l'app ridisegna solo il testo (senza la bolla),
    // il testo con uno di questi colori va comunque schiarito.
    private final java.util.Set<Integer> mOutText = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public MessagesBubbleMod(Context context) {
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

    private static int intProp(String key, int def, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(prop(key, String.valueOf(def)))));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void refreshProps() {
        long now = SystemClock.elapsedRealtime();
        if (now - mPropsAt < 2000) return;
        mPropsAt = now;
        mOutMode = intProp(PROP_OUT, 2, 0, 2);
        mBorderHalfDp = intProp(PROP_BORDER, 2, 0, 8);
        mTail = intProp(PROP_TAIL, 1, 0, 1) == 1;
        try {
            mDensity = Resources.getSystem().getDisplayMetrics().density;
            // Accento scelto dall'utente: Oxydian lo salva anche in questa proprieta' (leggibile da ogni processo)
            int c = 0;
            try {
                String a1 = prop("persist.obsidian.dst.a1", "");
                if (!a1.isEmpty()) c = 0xFF000000 | Integer.parseInt(a1);
            } catch (NumberFormatException ignored) {}
            if (c == 0) {
                Resources r = Resources.getSystem();
                int id = r.getIdentifier("accent_material_dark", "color", "android");
                c = id != 0 ? r.getColor(id, null) : 0xFF908DFF;
            }
            if (c != mStrokeColor) {
                mStrokeColor = c;
            }
        } catch (Throwable ignored) {}
        mBorderInColor = borderColor(prop(PROP_BORDER_IN, "-1"), 0xFFFFFFFF);
        mBorderOutColor = borderColor(prop(PROP_BORDER_OUT, "accent"), mStrokeColor);
    }

    /** "accent" = colore di accento; altrimenti un colore ARGB scritto come intero. */
    private int borderColor(String v, int def) {
        if (v == null || v.isEmpty()) return def;
        if ("accent".equals(v)) return mStrokeColor;
        try {
            return 0xFF000000 | Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Colore con cui si disegna la bolla OUT: scuro come le IN, oppure accento al 50% di trasparenza. */
    private int outFill() {
        return mOutMode == 2 ? ((0x80 << 24) | (mStrokeColor & 0xFFFFFF)) : mInColor;
    }

    private static float luma(int c) {
        return (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f;
    }

    /** Forma di una bolla: riempita, opaca, angoli ~20 dp, abbastanza grande. */
    private boolean isBubbleRR(Paint p, float w, float h, float rx, float ry) {
        if (p.getStyle() != Paint.Style.FILL || p.getShader() != null) return false;
        float r = BUBBLE_RADIUS_DP * mDensity;
        if (Math.abs(rx - r) > 3f || Math.abs(ry - r) > 3f) return false;
        return w >= 100 && h >= 50 && Color.alpha(p.getColor()) == 255;
    }

    private boolean isOutColor(int c) {
        float l = luma(c);
        return l > 0.7f && l < 0.97f;
    }

    private boolean isInColor(int c) {
        int rgb = c & 0xFFFFFF;
        return rgb != 0 && luma(c) < 0.25f;
    }

    /** Bordo: bianco sulle bolle ricevute (IN), colore di accento su quelle inviate (OUT). */
    private Paint stroke(boolean inBubble) {
        if (mStroke == null) {
            Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
            s.setStyle(Paint.Style.STROKE);
            mStroke = s;
        }
        if (mStrokeIn == null) {
            Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
            s.setStyle(Paint.Style.STROKE);
            mStrokeIn = s;
        }
        Paint out = inBubble ? mStrokeIn : mStroke;
        out.setColor(inBubble ? mBorderInColor : mBorderOutColor);
        out.setStrokeWidth(Math.max(1f, mBorderHalfDp * 0.5f * mDensity));
        return out;
    }

    /** Rettangolo con raggi diversi per angolo (alto-sx, alto-dx, basso-dx, basso-sx). */
    private static Path roundPath(float l, float t, float r, float b, float tl, float tr, float br, float bl) {
        Path p = new Path();
        p.addRoundRect(new RectF(l, t, r, b), new float[]{tl, tl, tr, tr, br, br, bl, bl}, Path.Direction.CW);
        return p;
    }

    /** Forma e bordo di una bolla con angoli uguali da rx; l'angolo a punta (se attivo) vale 0. */
    private Path[] bubbleShapes(float l, float t, float r, float b, float rx, boolean in, boolean[] join) {
        // angoli: alto-sx, alto-dx, basso-dx, basso-sx. "join" = angoli di unione tra bolle consecutive (0 dp)
        float tl = join != null && join[0] ? 0f : rx;
        float tr = join != null && join[1] ? 0f : rx;
        float br = join != null && join[2] ? 0f : rx;
        float bl = join != null && join[3] ? 0f : rx;
        if (mTail) { if (in) bl = 0f; else br = 0f; }
        float inset = stroke(in).getStrokeWidth() / 2f;
        Path shape = roundPath(l, t, r, b, tl, tr, br, bl);
        Path border = roundPath(l + inset, t + inset, r - inset, b - inset,
                tl == 0f ? 0f : Math.max(0f, tl - inset), tr == 0f ? 0f : Math.max(0f, tr - inset),
                br == 0f ? 0f : Math.max(0f, br - inset), bl == 0f ? 0f : Math.max(0f, bl - inset));
        return new Path[]{shape, border};
    }

    /** Angoli "piccoli" (di serie, per le bolle consecutive) del percorso originale: alto-sx, alto-dx, basso-dx, basso-sx. */
    private boolean[] smallCorners(Path path, RectF b) {
        boolean[] out = new boolean[4];
        float s = 3f * mDensity;
        RectF[] sq = {
                new RectF(b.left, b.top, b.left + s, b.top + s),
                new RectF(b.right - s, b.top, b.right, b.top + s),
                new RectF(b.right - s, b.bottom - s, b.right, b.bottom),
                new RectF(b.left, b.bottom - s, b.left + s, b.bottom)};
        for (int i = 0; i < 4; i++) {
            try {
                Path q = new Path();
                q.addRect(sq[i], Path.Direction.CW);
                Path inter = new Path();
                out[i] = inter.op(path, q, Path.Op.INTERSECT) && !inter.isEmpty();
            } catch (Throwable ignored) {}
        }
        return out;
    }

    private void markBubble(boolean in) {
        mInOut = !in;
        mLastIn = in;
    }

    /** Disegna riempimento (eventualmente ricolorato) e bordo di una bolla gia' trasformata. */
    private void drawBubble(Canvas canvas, Path shape, Path borderShape, Paint p, int origColor, boolean in) {
        mBusy.set(Boolean.TRUE);
        try {
            int fillColor = (!in && mOutMode != 0) ? outFill() : origColor;
            p.setColor(fillColor);
            canvas.drawPath(shape, p);
            p.setColor(origColor);
            if (mBorderHalfDp > 0) canvas.drawPath(borderShape, stroke(in));
        } finally {
            mBusy.remove();
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XC_MethodHook roundRect = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (mBusy.get() != null) return;
                    refreshProps();
                    Object[] a = param.args;
                    Paint p;
                    float l, t, r, b, rx, ry;
                    if (a.length == 7 && a[6] instanceof Paint) {
                        p = (Paint) a[6];
                        l = (Float) a[0]; t = (Float) a[1]; r = (Float) a[2]; b = (Float) a[3];
                        rx = (Float) a[4]; ry = (Float) a[5];
                    } else if (a.length == 4 && a[0] instanceof RectF && a[3] instanceof Paint) {
                        p = (Paint) a[3];
                        RectF rc = (RectF) a[0];
                        l = rc.left; t = rc.top; r = rc.right; b = rc.bottom;
                        rx = (Float) a[1]; ry = (Float) a[2];
                    } else return;
                    float w = r - l, h = b - t;
                    boolean large = p.getStyle() == Paint.Style.FILL && p.getShader() == null && w >= 100 && h >= 50;
                    int c = p.getColor();
                    if (!isBubbleRR(p, w, h, rx, ry) || (!isInColor(c) && !isOutColor(c))) {
                        if (large) { mInOut = false; mLastIn = false; }
                        return;
                    }
                    boolean in = isInColor(c);
                    if (in) mInColor = c;
                    markBubble(in);
                    boolean recolor = !in && mOutMode != 0;
                    if (!mTail && mBorderHalfDp <= 0) {
                        if (recolor) { param.setObjectExtra("oc", c); p.setColor(outFill()); }
                        return;
                    }
                    // Forma propria: angoli uguali + angolo a punta + bordo. Si disegna qui e si salta l'originale.
                    Path[] sh = bubbleShapes(l, t, r, b, rx, in, null);
                    drawBubble((Canvas) param.thisObject, sh[0], sh[1], p, c, in);
                    param.setResult(null);
                } catch (Throwable ignored) {}
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Object oc = param.getObjectExtra("oc");
                    if (oc == null) return;
                    Object[] a = param.args;
                    Paint p = a.length == 7 ? (Paint) a[6] : (Paint) a[3];
                    p.setColor((Integer) oc);
                } catch (Throwable ignored) {}
            }
        };
        XC_MethodHook drawPath = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (mBusy.get() != null) return;
                    refreshProps();
                    if (param.args.length != 2 || !(param.args[0] instanceof Path) || !(param.args[1] instanceof Paint))
                        return;
                    Path path = (Path) param.args[0];
                    Paint p = (Paint) param.args[1];
                    int c = p.getColor();
                    RectF b = new RectF();
                    boolean bubble = isBubblePath(p, path, b);
                    if (!bubble) {
                        if (p.getStyle() == Paint.Style.FILL && p.getShader() == null) {
                            path.computeBounds(b, true);
                            if (b.width() >= 100 && b.height() >= 50) { mInOut = false; mLastIn = false; }
                        }
                        return;
                    }
                    boolean in = isInColor(c);
                    if (in) mInColor = c;
                    markBubble(in);
                    boolean recolor = !in && mOutMode != 0;
                    if (!mTail && mBorderHalfDp <= 0) {
                        if (recolor) { param.setObjectExtra("oc", c); p.setColor(outFill()); }
                        return;
                    }
                    // Bolle consecutive (forma "a percorso"): gli angoli piccoli di serie (unione) diventano 0 dp,
                    // gli altri 20 dp, piu' l'angolo a punta.
                    float rr = Math.min(BUBBLE_RADIUS_DP * mDensity, Math.min(b.width(), b.height()) / 2f);
                    Path[] sh = bubbleShapes(b.left, b.top, b.right, b.bottom, rr, in, smallCorners(path, b));
                    drawBubble((Canvas) param.thisObject, sh[0], sh[1], p, c, in);
                    param.setResult(null);
                } catch (Throwable ignored) {}
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Object oc = param.getObjectExtra("oc");
                    if (oc == null) return;
                    ((Paint) param.args[1]).setColor((Integer) oc);
                } catch (Throwable ignored) {}
            }
        };
        XC_MethodHook text = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (mBusy.get() != null || param.args.length == 0) return;
                    Object last = param.args[param.args.length - 1];
                    if (!(last instanceof Paint)) return;
                    Paint p = (Paint) last;
                    int c = p.getColor();
                    if (Color.alpha(c) != 255) return;
                    if (mLastIn && !mInOut && luma(c) > 0.85f) {
                        mInText = c;                       // colore del testo delle bolle ricevute
                    } else if (mOutMode != 0 && luma(c) < 0.45f) {
                        if (mInOut) mOutText.add(c);       // testo scuro dentro una bolla OUT: lo impariamo
                        if (mInOut || mOutText.contains(c)) {
                            param.setObjectExtra("tc", c);
                            p.setColor(mInText);
                        }
                    }
                } catch (Throwable ignored) {}
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Object tc = param.getObjectExtra("tc");
                    if (tc == null) return;
                    ((Paint) param.args[param.args.length - 1]).setColor((Integer) tc);
                } catch (Throwable ignored) {}
            }
        };
        for (String cls : new String[]{"android.graphics.Canvas", "android.graphics.BaseRecordingCanvas",
                "android.graphics.RecordingCanvas"}) {
            try {
                Class<?> c = Class.forName(cls, false, lpparam.classLoader);
                XposedBridge.hookAllMethods(c, "drawRoundRect", roundRect);
                XposedBridge.hookAllMethods(c, "drawPath", drawPath);
                XposedBridge.hookAllMethods(c, "drawText", text);
                XposedBridge.hookAllMethods(c, "drawTextRun", text);
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] MessagesBubbleMod: " + cls + " not hooked: " + t);
            }
        }
        XposedBridge.log("[ Obsidian ] MessagesBubbleMod: hooks installed in " + PKG);
    }

    /** Percorso riempito, opaco, di dimensioni da bolla (le icone sono bianche pure e quadrate). */
    private boolean isBubblePath(Paint p, Path path, RectF out) {
        if (p.getStyle() != Paint.Style.FILL || p.getShader() != null || Color.alpha(p.getColor()) != 255)
            return false;
        int c = p.getColor();
        if (!isInColor(c) && !isOutColor(c)) return false;
        path.computeBounds(out, true);
        return out.width() >= 100 && out.height() >= 50 && out.height() < 1200;
    }
}
