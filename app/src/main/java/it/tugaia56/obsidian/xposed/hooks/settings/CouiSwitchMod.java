package it.tugaia56.obsidian.xposed.hooks.settings;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;
import it.tugaia56.obsidian.xposed.utils.AccentUtil;

/**
 * Interruttori OnePlus (com.coui.appcompat.couiswitch.COUISwitch): disegnano da soli barra e tondo, quindi un overlay
 * non ne cambia la forma. Qui onDraw viene sostituito con lo stile "barra stretta": barra sottile del colore del testo
 * (bianca sul tema scuro, scura sul chiaro) e tondo con bordo e riempimento trasparente dell'accento.
 *
 * Si accende con la proprieta' persist.oxytheme.switchstyle (la imposta Oxydian Theme, scheda Interruttori):
 * barra_stretta (o narrow), barra_accento (barra nel colore dell'accento da acceso), pallino_pieno (tondo acceso pieno),
 * anello (pillola col tondo ad anello), on_off / on_off_verde_rosso (pillola con la scritta
 * ON/OFF), pulsante (cornice scura col pulsante colorato), sprite (stile Sprite: barra accento da accesa, tondo pieno col bordo).
 * Vuota = si disegna l'originale. Le posizioni vengono dall'interruttore stesso (setOuterCircleRectF), cosi' animazione e
 * pressione restano quelle di OnePlus. Nomi di campi legati alla versione di COUISwitch di OOS 16 (vedi log LSPosed).
 */
public class CouiSwitchMod extends XposedMods {

    private static final String CLS = "com.coui.appcompat.couiswitch.COUISwitch";
    private static final String PROP = "persist.oxytheme.switchstyle";

    private static volatile String sVariant = "";
    private static volatile long sAt = -10000;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    public CouiSwitchMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {}

    @Override
    public boolean listensTo(String packageName) {
        return true;   // ModPacks lo aggiunge solo ai pacchetti scelti
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        Class<?> cls = XposedHelpers.findClassIfExists(CLS, lp.classLoader);
        if (cls == null) return;
        try {
            XposedHelpers.findAndHookMethod(cls, "onDraw", Canvas.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (variant().isEmpty()) return;
                        View v = (View) p.thisObject;
                        if (XposedHelpers.getBooleanField(v, "mIsThemedEnabled")) return;
                        if (XposedHelpers.getBooleanField(v, "mIsLoading")) return;
                        draw(v, (Canvas) p.args[0]);
                        p.setResult(null);
                    } catch (Throwable t) {
                        XposedBridge.log("[ Obsidian ] CouiSwitchMod draw: " + t);
                    }
                }
            });
            XposedBridge.log("[ Obsidian ] CouiSwitchMod: hooked COUISwitch.onDraw in " + lp.packageName);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] CouiSwitchMod: hook failed in " + lp.packageName + ": " + t);
        }
    }

    private static String variant() {
        long now = SystemClock.elapsedRealtime();
        if (now - sAt > 3000) {
            sAt = now;
            try {
                Class<?> sp = Class.forName("android.os.SystemProperties");
                String v = String.valueOf(XposedHelpers.callStaticMethod(sp, "get", PROP, ""));
                sVariant = v == null ? "" : v.trim().toLowerCase();
            } catch (Throwable t) {
                sVariant = "";
            }
        }
        return sVariant;
    }

    private static int attrColor(Context c, int attr, int def) {
        TypedValue tv = new TypedValue();
        if (c.getTheme().resolveAttribute(attr, tv, true)) {
            if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) return tv.data;
            try { return c.getColor(tv.resourceId); } catch (Throwable ignored) {}
        }
        return def;
    }

    private static int alpha(int color, int a) {
        return (a << 24) | (color & 0xFFFFFF);
    }

    private void draw(View v, Canvas canvas) {
        Context c = v.getContext();
        boolean checked = (Boolean) XposedHelpers.callMethod(v, "isChecked");
        boolean enabled = v.isEnabled();
        XposedHelpers.callMethod(v, "setOuterCircleRectF");
        RectF o = (RectF) XposedHelpers.getObjectField(v, "mOuterCircleRectF");
        float scale = XposedHelpers.getFloatField(v, "mCircleScale");
        int pad = XposedHelpers.getIntField(v, "mPadding");
        int minW = (Integer) XposedHelpers.callMethod(v, "getSwitchMinWidth");
        float d = c.getResources().getDisplayMetrics().density;

        int fg = attrColor(c, android.R.attr.colorForeground, 0xFFFFFFFF);
        int accent = AccentUtil.systemAccent();
        float dis = enabled ? 1f : 0.38f;
        float cy = o.centerY();

        String var = variant();
        if (drawForm(canvas, var, checked, enabled, pad, minW, o, scale,
                XposedHelpers.getIntField(v, "mBarHeight"), fg, accent, d)) return;
        // barra stretta: dal bordo del pallino spento al bordo del pallino acceso
        boolean classic = "sprite".equals(var);
        float th = 2f * d;
        int cp = XposedHelpers.getIntField(v, "mCirclePadding");
        int ow = XposedHelpers.getIntField(v, "mOuterCircleWidth");
        mRect.set(pad + cp, cy - th / 2f, pad + minW - cp, cy + th / 2f);
        mPaint.setStyle(Paint.Style.FILL);
        int barColor = checked && ("barra_accento".equals(var) || classic) ? accent : fg;
        if (classic && !checked) barColor = alpha(fg, 0x40);
        mPaint.setColor(alpha(barColor, Math.round(0xFF * dis)));
        canvas.drawRoundRect(mRect, th / 2f, th / 2f, mPaint);

        // tondo con bordo e riempimento trasparente
        float sw = (classic ? 1f : 1.5f) * d;
        float r = o.height() / 2f - sw / 2f;
        float cx = o.centerX();
        canvas.save();
        canvas.scale(scale, scale, cx, cy);
        boolean full = "pallino_pieno".equals(var);
        int fill = checked ? alpha(accent, full ? 0xFF : 0x59) : full ? fg : alpha(fg, 0x22);
        int stroke = checked ? accent : full ? fg : alpha(fg, 0x99);
        if (classic) fill = checked ? alpha(accent, 0x59) : alpha(fg, 0x4D);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(alpha(fill, Math.round(Color_a(fill) * dis)));
        canvas.drawCircle(cx, cy, r, mPaint);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(sw);
        mPaint.setColor(alpha(stroke, Math.round(Color_a(stroke) * dis)));
        canvas.drawCircle(cx, cy, r, mPaint);
        canvas.restore();
    }

    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Stili "di carattere": levetta, led, tacche, neon, lucido_3d. cx = centro del pallino, rad = suo raggio, h = altezza della barra. */
    static void special(Canvas c, Paint p, RectF r, String var, boolean on, float left, float right, float cy, float h,
                        float cx, float rad, int fg, int accent, float d) {
        p.setShader(null);
        switch (var) {
            case "levetta": {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF2A2D33);
                float bl = left + 4 * d, br = right - 4 * d, bt = cy - h / 2f + 2 * d, bb = cy + h / 2f - 2 * d;
                r.set(bl, bt, br, bb);
                c.drawRoundRect(r, 5 * d, 5 * d, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.5f * d);
                p.setColor((0x66 << 24) | (fg & 0xFFFFFF));
                r.inset(0.75f * d, 0.75f * d);
                c.drawRoundRect(r, 5 * d, 5 * d, p);
                float kw = (br - bl) / 2f - 3 * d, kh = bb - bt - 4 * d;
                float kx = Math.max(bl + 2 * d + kw / 2f, Math.min(br - 2 * d - kw / 2f, cx));
                r.set(kx - kw / 2f, cy - kh / 2f, kx + kw / 2f, cy + kh / 2f);
                p.setStyle(Paint.Style.FILL);
                p.setColor(on ? accent : 0xFF8A8F99);
                c.drawRoundRect(r, 4 * d, 4 * d, p);
                p.setColor(0x55FFFFFF);
                r.set(kx - kw / 2f + 3 * d, cy - kh / 2f + 1.5f * d, kx + kw / 2f - 3 * d, cy - kh / 2f + 3f * d);
                c.drawRect(r, p);
                break;
            }
            case "led": {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.5f * d);
                p.setColor((0x44 << 24) | (fg & 0xFFFFFF));
                r.set(left + 0.75f * d, cy - 4 * d, right - 0.75f * d, cy + 4 * d);
                c.drawRoundRect(r, 4 * d, 4 * d, p);
                float rr = rad * 0.62f;
                p.setStyle(Paint.Style.FILL);
                if (on) {
                    float gr = Math.max(h / 2f, rr * 2f);
                    p.setColor(0xFF000000);
                    p.setShader(new RadialGradient(cx, cy, gr,
                            new int[]{(0xE6 << 24) | (accent & 0xFFFFFF), (0x80 << 24) | (accent & 0xFFFFFF),
                                    (0x30 << 24) | (accent & 0xFFFFFF), (accent & 0xFFFFFF)},
                            new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
                    c.drawCircle(cx, cy, gr, p);
                    p.setShader(null);
                }
                p.setColor(on ? accent : 0xFF4A4E56);
                c.drawCircle(cx, cy, rr, p);
                p.setColor(on ? 0xAAFFFFFF : 0x22FFFFFF);
                c.drawCircle(cx - rr * 0.3f, cy - rr * 0.3f, rr * 0.32f, p);
                break;
            }
            case "tacche": {
                int n = 9;
                float tw = 3 * d, th = 12 * d;
                float l0 = left + 3 * d + tw / 2f, r0 = right - 3 * d - tw / 2f;
                // l'indice percorre tutte le tacche: da quella di sinistra a quella di destra
                float c0 = left + 3 * d + rad, c1 = right - 3 * d - rad;
                float prog = c1 - c0 > 1f ? Math.max(0f, Math.min(1f, (cx - c0) / (c1 - c0))) : (on ? 1f : 0f);
                float mx = l0 + prog * (r0 - l0);
                p.setStyle(Paint.Style.FILL);
                for (int i = 0; i < n; i++) {
                    float x = l0 + (r0 - l0) * i / (n - 1);
                    boolean act = x <= mx + 0.5f * d;
                    p.setColor(act ? accent : ((0x44 << 24) | (fg & 0xFFFFFF)));
                    r.set(x - tw / 2f, cy - th / 2f, x + tw / 2f, cy + th / 2f);
                    c.drawRoundRect(r, tw / 2f, tw / 2f, p);
                }
                float bw = 4 * d, bh = Math.min(h - 2 * d, 16 * d);
                p.setColor(on ? 0xFFFFFFFF : ((0xCC << 24) | (fg & 0xFFFFFF)));
                r.set(mx - bw / 2f, cy - bh / 2f, mx + bw / 2f, cy + bh / 2f);
                c.drawRoundRect(r, bw / 2f, bw / 2f, p);
                break;
            }
            case "neon": {
                float ph = h - 10 * d;
                r.set(left + 5 * d, cy - ph / 2f, right - 5 * d, cy + ph / 2f);
                p.setStyle(Paint.Style.STROKE);
                if (on) {
                    // bordo senza alone: l'alone resta solo sul pallino
                    p.setStrokeWidth(2 * d);
                    p.setColor(accent);
                    c.drawRoundRect(r, ph / 2f, ph / 2f, p);
                } else {
                    p.setStrokeWidth(1.5f * d);
                    p.setColor((0x66 << 24) | (fg & 0xFFFFFF));
                    c.drawRoundRect(r, ph / 2f, ph / 2f, p);
                }
                float rr = rad - 5 * d;
                if (on) {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor((0x33 << 24) | (accent & 0xFFFFFF));
                    c.drawCircle(cx, cy, rr, p);
                    p.setStyle(Paint.Style.STROKE);
                    float[] sw = {9 * d, 6 * d, 4 * d, 2.5f * d};
                    int[] al = {0x33, 0x55, 0x99, 0xFF};
                    for (int i = 0; i < 4; i++) {
                        p.setStrokeWidth(sw[i]);
                        p.setColor((al[i] << 24) | (accent & 0xFFFFFF));
                        c.drawCircle(cx, cy, rr, p);
                    }
                } else {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(1.5f * d);
                    p.setColor((0x66 << 24) | (fg & 0xFFFFFF));
                    c.drawCircle(cx, cy, rr, p);
                }
                break;
            }
            case "lucido_3d": {
                float top = cy - h / 2f + d, bot = cy + h / 2f - d;
                int c1 = on ? mixc(accent, 0xFFFFFFFF, 0.30f) : 0xFF6A6F79;
                int c2 = on ? mixc(accent, 0xFF000000, 0.40f) : 0xFF2B2E34;
                r.set(left + d, top, right - d, bot);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF000000);
                p.setShader(new LinearGradient(0, top, 0, bot, c1, c2, Shader.TileMode.CLAMP));
                c.drawRoundRect(r, (bot - top) / 2f, (bot - top) / 2f, p);
                p.setShader(null);
                // bordo e riflesso sul bordo alto
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1f * d);
                p.setColor(0x55000000);
                c.drawRoundRect(r, (bot - top) / 2f, (bot - top) / 2f, p);
                p.setColor(0x44FFFFFF);
                p.setStrokeWidth(1f * d);
                r.set(left + d + 5 * d, top + 1.5f * d, right - d - 5 * d, top + 1.5f * d);
                c.drawLine(r.left, r.top, r.right, r.top, p);
                // pallino come una sfera lucida con ombra
                float rr = rad - 2 * d;
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x55000000);
                c.drawCircle(cx, cy + 1.5f * d, rr, p);
                p.setColor(0xFF000000);
                p.setShader(new RadialGradient(cx - rr * 0.3f, cy - rr * 0.4f, rr * 1.4f,
                        new int[]{0xFFFFFFFF, 0xFFE3E6EB, 0xFF9CA2AC}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, rr, p);
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1f * d);
                p.setColor(0x44000000);
                c.drawCircle(cx, cy, rr, p);
                break;
            }
            default:
                break;
        }
    }

    private static int mixc(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    /** Stili "a forma": pillola con anello, ON/OFF, pulsante. Stessa geometria dell'interruttore OnePlus. */
    private boolean drawForm(Canvas canvas, String var, boolean checked, boolean enabled, int pad, int minW,
                             RectF o, float scale, int barH, int fg, int accent, float d) {
        boolean ring = "anello".equals(var), onoff = var.startsWith("on_off"), btn = "pulsante".equals(var);
        boolean sp = "levetta".equals(var) || "led".equals(var) || "tacche".equals(var) || "neon".equals(var) || "lucido_3d".equals(var);
        if (!(ring || onoff || btn || sp)) return false;
        float cy = o.centerY(), h = barH > 0 ? barH : o.height() + 6 * d;
        float left = pad, right = pad + minW;
        int on = accent, off = alpha(fg, 0x55);
        if ("on_off_verde_rosso".equals(var)) { on = 0xFF34A853; off = 0xFFD93025; }
        int save = enabled ? -1 : canvas.saveLayerAlpha(null, 97);
        if (sp) {
            special(canvas, mPaint, mRect, var, checked, left, right, cy, h, o.centerX(), o.height() / 2f, fg, accent, d);
            if (save >= 0) canvas.restoreToCount(save);
            return true;
        }
        mPaint.setStyle(Paint.Style.FILL);
        mRect.set(left, cy - h / 2f, right, cy + h / 2f);
        if (btn) {
            mPaint.setColor(0xFF2A2D33);
            canvas.drawRoundRect(mRect, h / 2f, h / 2f, mPaint);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(1.5f * d);
            mPaint.setColor(alpha(fg, 0x66));
            mRect.inset(0.75f * d, 0.75f * d);
            canvas.drawRoundRect(mRect, h / 2f, h / 2f, mPaint);
        } else {
            mPaint.setColor(checked ? on : off);
            canvas.drawRoundRect(mRect, h / 2f, h / 2f, mPaint);
        }
        float cx = o.centerX(), rad = o.height() / 2f;
        canvas.save();
        canvas.scale(scale, scale, cx, cy);
        if (ring) {
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(3f * d);
            mPaint.setColor(0xFFFFFFFF);
            canvas.drawCircle(cx, cy, rad - 1.5f * d, mPaint);
        } else if (onoff) {
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(0xFFFFFFFF);
            canvas.drawCircle(cx, cy, rad - 1f * d, mPaint);
        } else {
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(checked ? accent : 0xFF8A8F99);
            canvas.drawCircle(cx, cy, rad - 3f * d, mPaint);
        }
        canvas.restore();
        if (onoff) {
            mText.setColor(0xFFFFFFFF);
            mText.setTextAlign(Paint.Align.CENTER);
            mText.setFakeBoldText(true);
            mText.setTextSize(h * 0.36f);
            float mid = checked ? (left + (cx - rad)) / 2f : ((cx + rad) + right) / 2f;
            float base = cy - (mText.ascent() + mText.descent()) / 2f;
            canvas.drawText(checked ? "ON" : "OFF", mid, base, mText);
        }
        if (save >= 0) canvas.restoreToCount(save);
        return true;
    }

    private static int Color_a(int color) {
        return (color >>> 24) & 0xFF;
    }
}
