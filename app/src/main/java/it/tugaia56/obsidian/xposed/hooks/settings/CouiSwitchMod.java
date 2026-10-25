package it.tugaia56.obsidian.xposed.hooks.settings;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
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
        int fill = checked ? alpha(accent, full ? 0xFF : 0x59) : alpha(fg, full ? 0x99 : 0x22);
        int stroke = checked ? accent : alpha(fg, 0x99);
        if (classic) fill = alpha(accent, 0x59);
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

    /** Stili "a forma": pillola con anello, ON/OFF, pulsante. Stessa geometria dell'interruttore OnePlus. */
    private boolean drawForm(Canvas canvas, String var, boolean checked, boolean enabled, int pad, int minW,
                             RectF o, float scale, int barH, int fg, int accent, float d) {
        boolean ring = "anello".equals(var), onoff = var.startsWith("on_off"), btn = "pulsante".equals(var);
        if (!(ring || onoff || btn)) return false;
        float cy = o.centerY(), h = barH > 0 ? barH : o.height() + 6 * d;
        float left = pad, right = pad + minW;
        int on = accent, off = alpha(fg, 0x55);
        if ("on_off_verde_rosso".equals(var)) { on = 0xFF34A853; off = 0xFFD93025; }
        int save = enabled ? -1 : canvas.saveLayerAlpha(null, 97);
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
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(1f * d);
            mPaint.setColor(0xFFB0B0B0);
            canvas.drawCircle(cx, cy, rad - 1f * d, mPaint);
        } else {
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(checked ? accent : 0xFF8A8F99);
            canvas.drawCircle(cx, cy, rad - 3f * d, mPaint);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(1.5f * d);
            mPaint.setColor(alpha(fg, 0x66));
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
