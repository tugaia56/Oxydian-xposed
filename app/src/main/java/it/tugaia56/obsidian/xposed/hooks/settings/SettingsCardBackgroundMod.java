package it.tugaia56.obsidian.xposed.hooks.settings;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static it.tugaia56.obsidian.utils.Constants.Packages.SETTINGS;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.content.res.Configuration;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Preference-group card background — vero meccanismo confermato il 2026-08-30 con un decompile
 * jadx fresco del vero Settings.apk installato. I due tentativi precedenti puntavano a classi
 * inesistenti (com.coui.appcompat.util.COUIContextUtil / com.coui.appcompat.list.
 * COUICardListSelectedItemLayout — entrambe ClassNotFoundException, hook mai installati),
 * per questo cambiare il colore forzato non aveva mai avuto alcun effetto in nessuno dei due
 * tentativi: non era un problema di blending/tint, l'hook non partiva proprio.
 *
 * Classi vere: com.coui.appcompat.contextutil.COUIContextUtil,
 * com.coui.appcompat.cardlist.COUICardListSelectedItemLayout. mCardBackgroundColor viene
 * impostato una sola volta dentro init(), da COUIContextUtil.getAttrColor(context,
 * couiColorCardBackground), poi letto live ad ogni draw() da una Drawable interna
 * (canvas.drawColor(mCardBackgroundColor), nessun blend/tint aggiuntivo in questa classe) —
 * quindi un override del campo funziona a patto di far seguire un invalidate(), cosa che il
 * metodo pubblico della classe stessa, refreshCardBg(int), fa già da solo.
 *
 * Due hook ridondanti:
 *   1) COUIContextUtil.getAttrColor(...) — forza il valore la prima volta che init() lo calcola.
 *   2) COUICardListSelectedItemLayout.init(Context, boolean) — dopo che ritorna, richiama
 *      refreshCardBg(mCardColor) sull'istanza stessa (campo + invalidate in un colpo solo).
 */
public class SettingsCardBackgroundMod extends XposedMods {

    private boolean mThemeApplied = false;
    // Stesso hex opaco del background di pagina già confermato corretto (DST_BACKGROUND).
    // In questa classe drawColor() non ha alcun blend/tint sopra, quindi un valore opaco
    // identico al background di pagina deve corrispondere esattamente, senza schiarimenti.
    private int mCardColor = 0xFF1B2029;

    public SettingsCardBackgroundMod(Context context) { super(context); }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mThemeApplied = Xprefs.getBoolean("settings_theme_applied", false);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!SETTINGS.equals(lp.packageName)) return;

        final int attrId = mContext.getResources().getIdentifier("couiColorCardBackground", "attr", SETTINGS);

        try {
            Class<?> couiContextUtilCls = findClass("com.coui.appcompat.contextutil.COUIContextUtil", lp.classLoader);
            if (attrId != 0) {
                hookAllMethods(couiContextUtilCls, "getAttrColor", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (!mThemeApplied || !isNight()) return;
                        try {
                            if (p.args.length < 2 || !(p.args[1] instanceof Integer)) return;
                            if ((Integer) p.args[1] == attrId) p.setResult(mCardColor);
                        } catch (Throwable ignored) {}
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] SettingsCardBackgroundMod: COUIContextUtil hook failed: " + t);
        }

        try {
            Class<?> cardLayoutCls = findClass(
                    "com.coui.appcompat.cardlist.COUICardListSelectedItemLayout", lp.classLoader);
            findAndHookMethod(cardLayoutCls, "init", Context.class, boolean.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!mThemeApplied || !isNight()) return;
                    try {
                        callMethod(p.thisObject, "refreshCardBg", mCardColor);
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] SettingsCardBackgroundMod: COUICardListSelectedItemLayout hook failed: " + t);
        }
    }

    /** Stessa cautela già usata da DstDialogStyle/DstNotifStyle: in chiaro lo stock è già
     *  corretto, un colore pensato solo per lo scuro andrebbe forzato a sproposito. */
    private boolean isNight() {
        try {
            return (mContext.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override public boolean listensTo(String packageName) { return SETTINGS.equals(packageName); }
}
