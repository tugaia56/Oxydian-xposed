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
 * Si accende con la proprieta' persist.oxytheme.switchstyle = narrow (la imposta Oxydian Theme, scheda Interruttori);
 * spenta si disegna l'originale. Le posizioni vengono dall'interruttore stesso (setOuterCircleRectF), cosi' animazione e
 * pressione restano quelle di OnePlus. Nomi di campi legati alla versione di COUISwitch di OOS 16 (vedi log LSPosed).
 */
public class CouiSwitchMod extends XposedMods {

    private static final String CLS = "com.coui.appcompat.couiswitch.COUISwitch";
    private static final String PROP = "persist.oxytheme.switchstyle";

    private static volatile boolean sOn;
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
                        if (!enabled()) return;
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

    private static boolean enabled() {
        long now = SystemClock.elapsedRealtime();
        if (now - sAt > 3000) {
            sAt = now;
            try {
                Class<?> sp = Class.forName("android.os.SystemProperties");
                sOn = "narrow".equals(XposedHelpers.callStaticMethod(sp, "get", PROP, ""));
            } catch (Throwable t) {
                sOn = false;
            }
        }
        return sOn;
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

        // barra stretta: dal bordo del pallino spento al bordo del pallino acceso
        float th = 2f * d;
        int cp = XposedHelpers.getIntField(v, "mCirclePadding");
        int ow = XposedHelpers.getIntField(v, "mOuterCircleWidth");
        mRect.set(pad + cp, cy - th / 2f, pad + minW - cp, cy + th / 2f);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(alpha(fg, Math.round(0xFF * dis)));
        canvas.drawRoundRect(mRect, th / 2f, th / 2f, mPaint);

        // tondo con bordo e riempimento trasparente
        float sw = 1.5f * d;
        float r = o.height() / 2f - sw / 2f;
        float cx = o.centerX();
        canvas.save();
        canvas.scale(scale, scale, cx, cy);
        int fill = checked ? alpha(accent, 0x59) : alpha(fg, 0x22);
        int stroke = checked ? accent : alpha(fg, 0x99);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(alpha(fill, Math.round(Color_a(fill) * dis)));
        canvas.drawCircle(cx, cy, r, mPaint);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(sw);
        mPaint.setColor(alpha(stroke, Math.round(Color_a(stroke) * dis)));
        canvas.drawCircle(cx, cy, r, mPaint);
        canvas.restore();
    }

    private static int Color_a(int color) {
        return (color >>> 24) & 0xFF;
    }
}
