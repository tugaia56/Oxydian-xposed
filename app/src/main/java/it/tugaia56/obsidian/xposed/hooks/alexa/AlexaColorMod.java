package it.tugaia56.obsidian.xposed.hooks.alexa;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;
import it.tugaia56.obsidian.xposed.utils.AccentUtil;

/**
 * Amazon Alexa (com.amazon.dee.app): le schermate sono React Native e i colori del design system "Mosaic" sono scritti nel
 * bundle JavaScript, quindi l'overlay del Theme non li raggiunge (cambia solo le risorse Android). Qui i colori fissi
 * vengono sostituiti nel momento in cui l'app li passa alle view: i blu (#3f88ff dei testi, #1db2f7 di barra volume e
 * link nel pannello del dispositivo, #126dff del tema chiaro) diventano l'accento, il grigio dei pannelli (#272d32) e
 * quello delle tessere (#121c23) diventano i colori di sfondo del tema, come nell'overlay. La trasparenza resta quella
 * originale. Solo con il tema scuro. Serve la spunta su Alexa nell'ambito di Oxydian in LSPosed.
 */
public class AlexaColorMod extends XposedMods {

    public static final String PKG = "com.amazon.dee.app";

    private static volatile int sAccent;
    private static volatile int sSheet, sTile;
    private static volatile boolean sReady;

    public AlexaColorMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {}

    @Override
    public boolean listensTo(String packageName) {
        return PKG.equals(packageName);
    }

    /** Colori di destinazione letti dal sistema (stesse risorse dell'overlay); se mancano restano quelli originali. */
    private static void load() {
        if (sReady) return;
        try {
            // con le risorse dell'app (non quelle di sistema): hanno gia' applicati gli overlay del Theme
            android.app.Application app = android.app.AndroidAppHelper.currentApplication();
            if (app == null) return;
            Resources res = app.getResources();
            sAccent = AccentUtil.systemAccent() & 0xFFFFFF;
            sSheet = color(res, "holo_light_primary_dark");
            sTile = color(res, "button_material_dark");
            sReady = true;
        } catch (Throwable ignored) {}
    }

    private static int color(Resources res, String name) {
        int id = Resources.getSystem().getIdentifier(name, "color", "android");
        return id == 0 ? -1 : (res.getColor(id, null) & 0xFFFFFF);
    }

    static int map(int c) {
        int rgb = c & 0xFFFFFF;
        switch (rgb) {
            case 0x3F88FF:
            case 0x1DB2F7:
            case 0x126DFF:
                if (!sReady) load();
                return sReady ? (c & 0xFF000000) | sAccent : c;
            case 0x272D32:
                if (!sReady) load();
                return !sReady || sSheet < 0 ? c : (c & 0xFF000000) | sSheet;
            case 0x121C23:
                if (!sReady) load();
                return !sReady || sTile < 0 ? c : (c & 0xFF000000) | sTile;
            default:
                return c;
        }
    }

    private static boolean night() {
        return (Resources.getSystem().getConfiguration().uiMode & 0x30) == 0x20;
    }

    /** Hook "prima": il primo argomento e' il colore. */
    private static final class ColorArg extends XC_MethodHook {
        private final String mName;
        private boolean mSeen;

        ColorArg(String name) { mName = name; }

        @Override protected void beforeHookedMethod(MethodHookParam p) {
            try {
                int c = (Integer) p.args[0];
                int n = map(c);
                if (n != c && night()) {
                    p.args[0] = n;
                    if (!mSeen) {
                        mSeen = true;
                        XposedBridge.log("[ Obsidian ] AlexaColorMod: " + mName + " " + Integer.toHexString(c) + " -> " + Integer.toHexString(n));
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    /** Hook "prima" su un array di colori (ColorStateList): si sostituiscono i valori che corrispondono. */
    private static final class ColorsArray extends XC_MethodHook {
        @Override protected void beforeHookedMethod(MethodHookParam p) {
            try {
                int[] a = (int[]) p.args[1];
                if (a == null || !night()) return;
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
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!PKG.equals(lp.packageName)) return;
        int ok = 0;
        try { XposedHelpers.findAndHookMethod(Paint.class, "setColor", int.class, new ColorArg("Paint.setColor")); ok++; } catch (Throwable t) { log("Paint.setColor", t); }
        try { XposedHelpers.findAndHookMethod(TextView.class, "setTextColor", int.class, new ColorArg("TextView.setTextColor")); ok++; } catch (Throwable t) { log("TextView.setTextColor", t); }
        try { XposedHelpers.findAndHookMethod(View.class, "setBackgroundColor", int.class, new ColorArg("View.setBackgroundColor")); ok++; } catch (Throwable t) { log("View.setBackgroundColor", t); }
        try { XposedHelpers.findAndHookMethod(ColorDrawable.class, "setColor", int.class, new ColorArg("ColorDrawable.setColor")); ok++; } catch (Throwable t) { log("ColorDrawable.setColor", t); }
        try { XposedHelpers.findAndHookConstructor(ColorDrawable.class, int.class, new ColorArg("ColorDrawable()")); ok++; } catch (Throwable t) { log("ColorDrawable()", t); }
        try { XposedHelpers.findAndHookMethod(GradientDrawable.class, "setColor", int.class, new ColorArg("GradientDrawable.setColor")); ok++; } catch (Throwable t) { log("GradientDrawable.setColor", t); }
        try { XposedHelpers.findAndHookMethod(Canvas.class, "drawColor", int.class, new ColorArg("Canvas.drawColor")); ok++; } catch (Throwable t) { log("Canvas.drawColor", t); }
        try { XposedHelpers.findAndHookConstructor(PorterDuffColorFilter.class, int.class, PorterDuff.Mode.class, new ColorArg("PorterDuffColorFilter()")); ok++; } catch (Throwable t) { log("PorterDuffColorFilter()", t); }
        try { XposedHelpers.findAndHookMethod(Drawable.class, "setColorFilter", int.class, PorterDuff.Mode.class, new ColorArg("Drawable.setColorFilter")); ok++; } catch (Throwable t) { log("Drawable.setColorFilter", t); }
        // testo: i colori dei tratti di testo (React Native usa ForegroundColorSpan e sottoclassi) e i ColorStateList
        try { XposedHelpers.findAndHookConstructor(android.text.style.ForegroundColorSpan.class, int.class, new ColorArg("ForegroundColorSpan()")); ok++; } catch (Throwable t) { log("ForegroundColorSpan()", t); }
        try { XposedHelpers.findAndHookMethod(android.content.res.ColorStateList.class, "valueOf", int.class, new ColorArg("ColorStateList.valueOf")); ok++; } catch (Throwable t) { log("ColorStateList.valueOf", t); }
        try { XposedHelpers.findAndHookConstructor(android.content.res.ColorStateList.class, int[][].class, int[].class, new ColorsArray()); ok++; } catch (Throwable t) { log("ColorStateList()", t); }
        try {
            XposedHelpers.findAndHookMethod(Paint.class, "setColor", long.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        long v = (Long) p.args[0];
                        if (!night()) return;
                        int c = android.graphics.Color.toArgb(v), n = map(c);
                        if (n != c) p.args[0] = android.graphics.Color.pack(n);
                    } catch (Throwable ignored) {}
                }
            });
            ok++;
        } catch (Throwable t) { log("Paint.setColor(long)", t); }
        // colori letti dalle risorse (alone della barra "Chiedi ad Alexa", testi nativi): stesso criterio dopo la lettura
        XC_MethodHook after = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    int c = (Integer) p.getResult();
                    int n = map(c);
                    if (n != c && night()) p.setResult(n);
                } catch (Throwable ignored) {}
            }
        };
        try { XposedHelpers.findAndHookMethod(Resources.class, "getColor", int.class, Resources.Theme.class, after); ok++; } catch (Throwable t) { log("Resources.getColor", t); }
        try { XposedHelpers.findAndHookMethod(android.content.res.TypedArray.class, "getColor", int.class, int.class, after); ok++; } catch (Throwable t) { log("TypedArray.getColor", t); }
        hookTileBorder(lp);
        XposedBridge.log("[ Obsidian ] AlexaColorMod: " + ok + " colour hooks installed in " + lp.packageName);
    }

    private final Paint mBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF mRect = new android.graphics.RectF();

    /**
     * Bordo accento sulle tessere dei dispositivi (sfondo #121c23): il bordo non e' una proprieta' delle tessere nel JS,
     * si disegna sopra lo sfondo di React Native (BackgroundDrawable) quando il suo colore e' quello delle tessere.
     */
    private void hookTileBorder(XC_LoadPackage.LoadPackageParam lp) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists("com.facebook.react.uimanager.drawable.BackgroundDrawable", lp.classLoader);
            if (cls == null) {
                XposedBridge.log("[ Obsidian ] AlexaColorMod: BackgroundDrawable not found");
                return;
            }
            XposedHelpers.findAndHookMethod(cls, "draw", Canvas.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        if (!night()) return;
                        if (!sReady) load();
                        if (!sReady) return;
                        Object col = XposedHelpers.getObjectField(p.thisObject, "backgroundColor");
                        if (!(col instanceof Integer) || ((Integer) col & 0xFFFFFF) != 0x121C23) return;
                        Drawable d = (Drawable) p.thisObject;
                        float dens = android.content.res.Resources.getSystem().getDisplayMetrics().density;
                        float w = 1.5f * dens;
                        mRect.set(d.getBounds());
                        mRect.inset(w / 2f, w / 2f);
                        mBorder.setStyle(Paint.Style.STROKE);
                        mBorder.setStrokeWidth(w);
                        mBorder.setColor(0xFF000000 | sAccent);
                        float r = 12f * dens;
                        ((Canvas) p.args[0]).drawRoundRect(mRect, r, r, mBorder);
                    } catch (Throwable t) {
                        if (!mBorderErr) {
                            mBorderErr = true;
                            XposedBridge.log("[ Obsidian ] AlexaColorMod: tile border: " + t);
                        }
                    }
                }
            });
        } catch (Throwable t) {
            log("BackgroundDrawable.draw", t);
        }
    }

    private boolean mBorderErr;

    private static void log(String what, Throwable t) {
        XposedBridge.log("[ Obsidian ] AlexaColorMod: hook " + what + " failed: " + t);
    }
}
