package it.tugaia56.obsidian.xposed.hooks.systemui;

import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;

import android.content.Context;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Android puro: il tasto del tastierino PIN premuto prende il suo colore da un colore di sistema (di serie un grigio con un po' di
 * viola). Con gli overlay del Theme l'accento arriva solo dopo l'avvio, quindi al PIN iniziale resta il colore di sistema.
 * Qui, mentre NumPadAnimator.reloadColors() legge quel colore, si da' direttamente l'accento scelto in Oxydian Theme
 * (persist.oxytheme.accent): vale subito, senza aspettare gli overlay. Con l'accento di serie (proprieta' vuota) non si tocca niente.
 */
public class PinKeyPressedMod extends XposedMods {

    private static final String PROP = "persist.oxytheme.accent";
    private static final ThreadLocal<Boolean> IN_RELOAD = new ThreadLocal<>();
    private static volatile int sPressedId = 0;
    private static volatile long sRead = 0;
    private static volatile int sAccent = 0;

    public PinKeyPressedMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {}

    @Override
    public boolean listensTo(String packageName) {
        return SYSTEM_UI.equals(packageName);
    }

    /** Accento scelto nel Theme (0 = nessuno); letto al piu' una volta al secondo. */
    private static int accent() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - sRead < 1000) return sAccent;
        sRead = now;
        int a = 0;
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            String v = String.valueOf(XposedHelpers.callStaticMethod(sp, "get", PROP, "")).trim();
            if (v.length() >= 6) a = 0xFF000000 | (int) Long.parseLong(v.substring(v.length() - 6), 16);
        } catch (Throwable ignored) {}
        sAccent = a;
        return a;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!SYSTEM_UI.equals(lpparam.packageName)) return;
        try {
            Class<?> pad = XposedHelpers.findClassIfExists("com.android.keyguard.NumPadAnimator", lpparam.classLoader);
            Class<?> col = XposedHelpers.findClassIfExists("com.android.systemui.bouncer.shared.constants.PinBouncerConstants$Color", lpparam.classLoader);
            if (pad == null || col == null) {
                XposedBridge.log("[ Obsidian ] PinKeyPressedMod: NumPadAnimator/PinBouncerConstants$Color not found");
                return;
            }
            sPressedId = XposedHelpers.getStaticIntField(col, "bgPressed");
            XposedBridge.hookAllMethods(pad, "reloadColors", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) { IN_RELOAD.set(Boolean.TRUE); }
                @Override protected void afterHookedMethod(MethodHookParam p) { IN_RELOAD.remove(); }
            });
            XposedHelpers.findAndHookMethod(Context.class, "getColor", int.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (IN_RELOAD.get() == null || sPressedId == 0 || (int) p.args[0] != sPressedId) return;
                    int a = accent();
                    if (a != 0) p.setResult(a);
                }
            });
            XposedBridge.log("[ Obsidian ] PinKeyPressedMod: hooked, bgPressed id=0x" + Integer.toHexString(sPressedId));
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] PinKeyPressedMod: hook failed: " + t);
        }
    }
}
