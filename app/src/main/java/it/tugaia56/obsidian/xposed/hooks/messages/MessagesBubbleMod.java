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
 * Misurato sul telefono (diagnostica del 2026-10-09): le bolle sono forme riempite con angoli di
 * 20 dp; quelle ricevute (IN) hanno il colore scuro del tema, quelle inviate (OUT) un grigio chiaro.
 *
 *  - "Bolle inviate scure": le bolle OUT prendono il colore delle IN.
 *  - "Bordo bolle": un contorno sottile del colore di accento attorno a tutte le bolle.
 *
 * Le scelte arrivano da proprieta' di sistema (persist.obsidian.msg_*), perche' l'app Messaggi non
 * vede le preferenze di Oxydian (stesso metodo usato dagli hook SystemUI per gli stili).
 */
public class MessagesBubbleMod extends XposedMods {

    public static final String PKG = "com.google.android.apps.messaging";
    public static final String PROP_OUT_DARK = "persist.obsidian.msg_out_dark";   // "0" stock, "1" scure come le IN, "2" accento al 50%
    public static final String PROP_BORDER = "persist.obsidian.msg_border";       // "0" spento, "1".."4" spessore (dp/2)

    private static final int DEFAULT_IN_COLOR = 0xFF1C2029;
    private static final float BUBBLE_RADIUS_DP = 20f;

    private volatile int mInColor = DEFAULT_IN_COLOR;
    private volatile int mOutMode = 2;
    private volatile int mBorderHalfDp = 0;
    private long mPropsAt = 0;
    private Paint mStroke;
    private Paint mStrokeIn;
    private int mStrokeColor = 0;
    private float mDensity = 3f;
    private final ThreadLocal<Boolean> mBusy = new ThreadLocal<>();
    // Testo delle bolle inviate: quando la bolla diventa scura, il testo (scuro) va reso chiaro.
    // mInOut = l'ultima forma grande disegnata e' una bolla OUT; mLastIn = e' una bolla IN.
    private volatile boolean mInOut = false;
    private volatile boolean mLastIn = false;
    private volatile int mInText = 0xFFEAEAFC;

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

    private void refreshProps() {
        long now = SystemClock.elapsedRealtime();
        if (now - mPropsAt < 2000) return;
        mPropsAt = now;
        try {
            mOutMode = Math.max(0, Math.min(2, Integer.parseInt(prop(PROP_OUT_DARK, "2"))));
        } catch (NumberFormatException e) {
            mOutMode = 2;
        }
        try {
            mBorderHalfDp = Math.max(0, Math.min(8, Integer.parseInt(prop(PROP_BORDER, "2"))));
        } catch (NumberFormatException e) {
            mBorderHalfDp = 2;
        }
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
                mStroke = null;
            }
        } catch (Throwable ignored) {}
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
            s.setColor(mStrokeColor);
            mStroke = s;
        }
        if (mStrokeIn == null) {
            Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
            s.setStyle(Paint.Style.STROKE);
            s.setColor(0xFFFFFFFF);
            mStrokeIn = s;
        }
        Paint out = inBubble ? mStrokeIn : mStroke;
        out.setStrokeWidth(Math.max(1f, mBorderHalfDp * 0.5f * mDensity));
        return out;
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
                    float w, h, rx, ry;
                    if (a.length == 7 && a[6] instanceof Paint) {
                        p = (Paint) a[6];
                        w = (Float) a[2] - (Float) a[0];
                        h = (Float) a[3] - (Float) a[1];
                        rx = (Float) a[4];
                        ry = (Float) a[5];
                    } else if (a.length == 4 && a[0] instanceof RectF && a[3] instanceof Paint) {
                        p = (Paint) a[3];
                        RectF rc = (RectF) a[0];
                        w = rc.width();
                        h = rc.height();
                        rx = (Float) a[1];
                        ry = (Float) a[2];
                    } else return;
                    boolean large = p.getStyle() == Paint.Style.FILL && p.getShader() == null && w >= 100 && h >= 50;
                    if (!isBubbleRR(p, w, h, rx, ry)) {
                        if (large) { mInOut = false; mLastIn = false; }
                        return;
                    }
                    int c = p.getColor();
                    if (isInColor(c)) {
                        mInColor = c;
                        mInOut = false;
                        mLastIn = true;
                    } else if (isOutColor(c)) {
                        mInOut = true;
                        mLastIn = false;
                        if (mOutMode != 0) {
                            param.setObjectExtra("oc", c);
                            p.setColor(outFill());
                        }
                    } else if (large) { mInOut = false; mLastIn = false; }
                } catch (Throwable ignored) {}
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (mBusy.get() != null) return;
                    Object[] a = param.args;
                    Paint p;
                    if (a.length == 7 && a[6] instanceof Paint) p = (Paint) a[6];
                    else if (a.length == 4 && a[3] instanceof Paint) p = (Paint) a[3];
                    else return;
                    Object oc = param.getObjectExtra("oc");
                    if (oc != null) p.setColor((Integer) oc);
                    if (mBorderHalfDp <= 0) return;
                    float l, t, r, b, rx, ry;
                    if (a.length == 7) {
                        l = (Float) a[0]; t = (Float) a[1]; r = (Float) a[2]; b = (Float) a[3];
                        rx = (Float) a[4]; ry = (Float) a[5];
                    } else {
                        RectF rc = (RectF) a[0];
                        l = rc.left; t = rc.top; r = rc.right; b = rc.bottom;
                        rx = (Float) a[1]; ry = (Float) a[2];
                    }
                    if (!isBubbleRR(p, r - l, b - t, rx, ry)) return;
                    int c = oc != null ? (Integer) oc : p.getColor();
                    if (!isInColor(c) && !isOutColor(c)) return;
                    Paint s = stroke(isInColor(c));
                    float inset = s.getStrokeWidth() / 2f;
                    mBusy.set(Boolean.TRUE);
                    try {
                        ((Canvas) param.thisObject).drawRoundRect(l + inset, t + inset, r - inset, b - inset,
                                Math.max(0f, rx - inset), Math.max(0f, ry - inset), s);
                    } finally {
                        mBusy.remove();
                    }
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
                    Paint p = (Paint) param.args[1];
                    if (!isBubblePath(p, (Path) param.args[0])) return;
                    int c = p.getColor();
                    if (isInColor(c)) {
                        mInColor = c;
                        mInOut = false;
                        mLastIn = true;
                    } else if (isOutColor(c)) {
                        mInOut = true;
                        mLastIn = false;
                        if (mOutMode != 0) {
                            param.setObjectExtra("oc", c);
                            p.setColor(outFill());
                        }
                    }
                } catch (Throwable ignored) {}
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (mBusy.get() != null) return;
                    if (param.args.length != 2 || !(param.args[0] instanceof Path) || !(param.args[1] instanceof Paint))
                        return;
                    Paint p = (Paint) param.args[1];
                    Object oc = param.getObjectExtra("oc");
                    if (oc != null) p.setColor((Integer) oc);
                    if (mBorderHalfDp <= 0) return;
                    if (!isBubblePath(p, (Path) param.args[0])) return;
                    int c = oc != null ? (Integer) oc : p.getColor();
                    if (!isInColor(c) && !isOutColor(c)) return;
                    mBusy.set(Boolean.TRUE);
                    try {
                        ((Canvas) param.thisObject).drawPath((Path) param.args[0], stroke(isInColor(c)));
                    } finally {
                        mBusy.remove();
                    }
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
                    } else if (mOutMode != 0 && mInOut && luma(c) < 0.45f) {
                        param.setObjectExtra("tc", c);     // testo scuro dentro una bolla OUT ormai scura
                        p.setColor(mInText);
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
    private boolean isBubblePath(Paint p, Path path) {
        if (p.getStyle() != Paint.Style.FILL || p.getShader() != null || Color.alpha(p.getColor()) != 255)
            return false;
        int c = p.getColor();
        if (!isInColor(c) && !isOutColor(c)) return false;
        RectF b = new RectF();
        path.computeBounds(b, true);
        return b.width() >= 100 && b.height() >= 50 && b.height() < 1200;
    }
}
