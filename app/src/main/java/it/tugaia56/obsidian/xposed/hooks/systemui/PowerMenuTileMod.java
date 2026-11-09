package it.tugaia56.obsidian.xposed.hooks.systemui;

import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;

import android.content.Context;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Android puro (RisingOS, ecc.), menu di spegnimento in Compose (GlobalActionsComposeDialogKt): nei tasti larghi (2 colonne,
 * come "Riavvia") l'icona e' 32 dp e il testo parte 40 dp dopo il bordo, cosi' scritte come "Riavvia" scorrono (marquee) e
 * l'ultima lettera si taglia. Qui, solo dentro i tasti larghi, l'icona passa a 26 dp e il testo parte a 34 dp: piu' spazio per
 * la scritta. I valori sono costanti del codice compilato: si riconoscono per valore (32 e 40) e si cambiano al volo nelle
 * chiamate Compose Modifier.size / Modifier.padding fatte mentre il tasto e' in composizione. Su OxygenOS non viene caricata.
 */
public class PowerMenuTileMod extends XposedMods {

    private static final float ICON_FROM = 32f, ICON_TO = 26f;
    private static final float TEXT_FROM = 40f, TEXT_TO = 34f;
    /** > 0 mentre si compone il contenuto di un tasto largo. */
    private static final ThreadLocal<int[]> sWide = ThreadLocal.withInitial(() -> new int[1]);

    public PowerMenuTileMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {}

    @Override
    public boolean listensTo(String packageName) {
        return SYSTEM_UI.equals(packageName);
    }

    private static boolean isWide(Object tileConfig) {
        try { return XposedHelpers.getIntField(tileConfig, "spanCols") > 1; } catch (Throwable t) { return false; }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        final ClassLoader cl = lpparam.classLoader;
        final Class<?> dialogKt;
        try {
            dialogKt = XposedHelpers.findClass("com.android.systemui.globalactions.compose.GlobalActionsComposeDialogKt", cl);
        } catch (Throwable t) {
            return;   // ROM senza il menu Compose
        }
        final Class<?> sizeKt = XposedHelpers.findClassIfExists("androidx.compose.foundation.layout.SizeKt", cl);
        final Class<?> paddingKt = XposedHelpers.findClassIfExists("androidx.compose.foundation.layout.PaddingKt", cl);
        if (sizeKt == null || paddingKt == null) return;

        // dentro TileItem (o il suo contenuto) per un tasto largo
        XC_MethodHook scope = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Object cfg = null;
                try { cfg = p.thisObject != null ? XposedHelpers.getObjectField(p.thisObject, "f$0") : (p.args.length > 0 ? p.args[0] : null); } catch (Throwable ignored) {}
                if (cfg != null && isWide(cfg)) sWide.get()[0]++;
                else p.setObjectExtra("oxy_no", Boolean.TRUE);
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.getObjectExtra("oxy_no") == null) sWide.get()[0]--;
            }
        };
        try { XposedBridge.hookAllMethods(dialogKt, "TileItem-JrCoQdk", scope); } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] PowerMenuTileMod TileItem: " + t);
        }
        // il contenuto del tasto e' una lambda separata (ricomposizione senza passare da TileItem): ExternalSyntheticLambda15, con
        // la TileConfig nel campo f$0; il numero puo' cambiare con la ROM, si prova e basta
        try {
            Class<?> lam = XposedHelpers.findClassIfExists(
                    "com.android.systemui.globalactions.compose.GlobalActionsComposeDialogKt$$ExternalSyntheticLambda15", cl);
            if (lam != null) XposedBridge.hookAllMethods(lam, "invoke", scope);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] PowerMenuTileMod lambda: " + t);
        }

        XposedBridge.hookAllMethods(sizeKt, "size-3ABfNKs", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (sWide.get()[0] <= 0 || p.args.length < 2 || !(p.args[1] instanceof Float)) return;
                if ((Float) p.args[1] == ICON_FROM) p.args[1] = ICON_TO;
            }
        });
        XposedBridge.hookAllMethods(paddingKt, "padding-qDBjuR0$default", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (sWide.get()[0] <= 0 || p.args.length < 2 || !(p.args[1] instanceof Float)) return;
                if ((Float) p.args[1] == TEXT_FROM) p.args[1] = TEXT_TO;
            }
        });
    }
}
