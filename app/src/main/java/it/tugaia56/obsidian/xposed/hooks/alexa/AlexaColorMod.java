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
            Resources sys = Resources.getSystem();
            sAccent = AccentUtil.systemAccent() & 0xFFFFFF;
            sSheet = color(sys, "holo_light_primary_dark");
            sTile = color(sys, "button_material_dark");
            sReady = true;
        } catch (Throwable ignored) {}
    }

    private static int color(Resources sys, String name) {
        int id = sys.getIdentifier(name, "color", "android");
        return id == 0 ? -1 : (sys.getColor(id, null) & 0xFFFFFF);
    }

    static int map(int c) {
        int rgb = c & 0xFFFFFF;
        switch (rgb) {
            case 0x3F88FF:
            case 0x1DB2F7:
            case 0x126DFF:
                if (!sReady) load();
                return (c & 0xFF000000) | sAccent;
            case 0x272D32:
                if (!sReady) load();
                return sSheet < 0 ? c : (c & 0xFF000000) | sSheet;
            case 0x121C23:
                if (!sReady) load();
                return sTile < 0 ? c : (c & 0xFF000000) | sTile;
            default:
                return c;
        }
    }

    private static boolean night() {
        return (Resources.getSystem().getConfiguration().uiMode & 0x30) == 0x20;
    }

    /** Hook "prima": il primo argomento e' il colore. */
    private static final class ColorArg extends XC_MethodHook {
        @Override protected void beforeHookedMethod(MethodHookParam p) {
            try {
                int c = (Integer) p.args[0];
                int n = map(c);
                if (n != c && night()) p.args[0] = n;
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!PKG.equals(lp.packageName)) return;
        int ok = 0;
        ColorArg first = new ColorArg();
        try { XposedHelpers.findAndHookMethod(Paint.class, "setColor", int.class, first); ok++; } catch (Throwable t) { log("Paint.setColor", t); }
        try { XposedHelpers.findAndHookMethod(TextView.class, "setTextColor", int.class, first); ok++; } catch (Throwable t) { log("TextView.setTextColor", t); }
        try { XposedHelpers.findAndHookMethod(View.class, "setBackgroundColor", int.class, first); ok++; } catch (Throwable t) { log("View.setBackgroundColor", t); }
        try { XposedHelpers.findAndHookMethod(ColorDrawable.class, "setColor", int.class, first); ok++; } catch (Throwable t) { log("ColorDrawable.setColor", t); }
        try { XposedHelpers.findAndHookConstructor(ColorDrawable.class, int.class, first); ok++; } catch (Throwable t) { log("ColorDrawable()", t); }
        try { XposedHelpers.findAndHookMethod(GradientDrawable.class, "setColor", int.class, first); ok++; } catch (Throwable t) { log("GradientDrawable.setColor", t); }
        try { XposedHelpers.findAndHookMethod(Canvas.class, "drawColor", int.class, first); ok++; } catch (Throwable t) { log("Canvas.drawColor", t); }
        try { XposedHelpers.findAndHookConstructor(PorterDuffColorFilter.class, int.class, PorterDuff.Mode.class, first); ok++; } catch (Throwable t) { log("PorterDuffColorFilter()", t); }
        try { XposedHelpers.findAndHookMethod(Drawable.class, "setColorFilter", int.class, PorterDuff.Mode.class, first); ok++; } catch (Throwable t) { log("Drawable.setColorFilter", t); }
        XposedBridge.log("[ Obsidian ] AlexaColorMod: " + ok + " colour hooks installed in " + lp.packageName);
    }

    private static void log(String what, Throwable t) {
        XposedBridge.log("[ Obsidian ] AlexaColorMod: hook " + what + " failed: " + t);
    }
}
