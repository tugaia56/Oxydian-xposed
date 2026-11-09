package it.tugaia56.obsidian.xposed.hooks.systemui;

import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.Context;

import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;
import it.tugaia56.obsidian.xposed.utils.AccentUtil;

/**
 * Android puro (RisingOS, ecc.), menu di spegnimento in Compose:
 *  - opzione "power_menu_border" (Oxydian > Varie): bordo nel colore accento attorno alla scheda del menu di spegnimento
 *    (PowerMenuContainer) e alla scheda della finestra Riavvia (RestartChoiceMenu). Il primo Surface senza bordo di ciascuna
 *    funzione riceve Modifier.border(...) dal suo stesso argomento Modifier.
 *  - icone delle opzioni della finestra Riavvia (RestartOptionTile) nel colore accento (sempre).
 * Su OxygenOS non viene caricata.
 */
public class PowerMenuAccentMod extends XposedMods {

    private static final float BORDER_DP = 1.5f;
    private volatile boolean mBorder = false;
    private volatile boolean mGlass = false;
    /** Opacita' del riempimento delle schede con "vetro" (0-255): la ROM sfoca gia' lo sfondo dietro il menu. */
    private static final int GLASS_ALPHA = 0xA0;

    /** Funzioni Compose attive (in pila): 1 = PowerMenuContainer, 2 = RestartChoiceMenu, 3 = RestartOptionTile. */
    private static final ThreadLocal<ArrayList<int[]>> sStack = ThreadLocal.withInitial(ArrayList::new);

    public PowerMenuAccentMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mBorder = Xprefs.getBoolean("power_menu_border", false);
        mGlass = Xprefs.getBoolean("power_menu_glass", false);
    }

    @Override
    public boolean listensTo(String packageName) {
        return SYSTEM_UI.equals(packageName);
    }

    /** Accento del Theme: colore di sistema system_primary_dark (Oxydian Theme su Android puro); se manca, l'accento di sistema. */
    private static int accent() {
        try {
            int id = android.content.res.Resources.getSystem().getIdentifier("system_primary_dark", "color", "android");
            if (id != 0) {
                android.app.Application app = android.app.AndroidAppHelper.currentApplication();
                android.content.res.Resources r = app != null ? app.getResources() : android.content.res.Resources.getSystem();
                return r.getColor(id, null) | 0xFF000000;
            }
        } catch (Throwable ignored) {}
        return AccentUtil.systemAccent();
    }

    /** Color di Compose (sRGB) impacchettato in un long: l'ARGB nei 32 bit alti. */
    private static long composeColor(int argb) {
        return (((long) argb) & 0xFFFFFFFFL) << 32;
    }

    private static XC_MethodHook scope(final int id) {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                sStack.get().add(new int[]{id, 0});   // [id, bordo gia' dato]
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                ArrayList<int[]> st = sStack.get();
                if (!st.isEmpty()) st.remove(st.size() - 1);
            }
        };
    }

    private static int[] top() {
        ArrayList<int[]> st = sStack.get();
        return st.isEmpty() ? null : st.get(st.size() - 1);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        final ClassLoader cl = lpparam.classLoader;
        final Class<?> dialogKt;
        try {
            dialogKt = XposedHelpers.findClass("com.android.systemui.globalactions.compose.GlobalActionsComposeDialogKt", cl);
        } catch (Throwable t) {
            return;
        }
        final Class<?> surfaceKt = XposedHelpers.findClassIfExists("androidx.compose.material3.SurfaceKt", cl);
        final Class<?> borderKt = XposedHelpers.findClassIfExists("androidx.compose.foundation.BorderKt", cl);
        final Class<?> iconKt = XposedHelpers.findClassIfExists("androidx.compose.material3.IconKt", cl);

        try { XposedBridge.hookAllMethods(dialogKt, "PowerMenuContainer", scope(1)); } catch (Throwable ignored) {}
        try { XposedBridge.hookAllMethods(dialogKt, "RestartChoiceMenu", scope(2)); } catch (Throwable ignored) {}
        try { XposedBridge.hookAllMethods(dialogKt, "RestartOptionTile", scope(3)); } catch (Throwable ignored) {}

        if (surfaceKt != null && borderKt != null) {
            // Surface senza bordo (modifier, shape, ...): il primo di ogni scheda riceve il bordo accento
            XposedBridge.hookAllMethods(surfaceKt, "Surface-T9BRK9s", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!mBorder && !mGlass) return;
                    int[] t = top();
                    if (t == null || (t[0] != 1 && t[0] != 2) || t[1] != 0 || p.args.length < 4) return;
                    t[1] = 1;
                    if (mBorder) {
                        try {
                            p.args[0] = XposedHelpers.callStaticMethod(borderKt, "border-xT4_qwU",
                                    p.args[0], BORDER_DP, composeColor(accent()), p.args[1]);
                        } catch (Throwable e) {
                            XposedBridge.log("[ Obsidian ] PowerMenuAccentMod border: " + e);
                        }
                    }
                    if (mGlass) {
                        try {
                            // colore del riempimento (indice 2) solo se passato davvero (bit "predefinito" spento nell'ultima maschera)
                            int last = p.args.length - 1;
                            boolean explicit = !(p.args[last] instanceof Integer) || (((Integer) p.args[last]) & 0x4) == 0;
                            if (explicit && p.args[2] instanceof Long) {
                                long c = (Long) p.args[2];
                                p.args[2] = (c & 0x00FFFFFFFFFFFFFFL) | (((long) GLASS_ALPHA) << 56);
                            }
                        } catch (Throwable e) {
                            XposedBridge.log("[ Obsidian ] PowerMenuAccentMod glass: " + e);
                        }
                    }
                }
            });
        }
        // "Annulla" (Cancel) nella finestra Riavvia: colore accento
        final Class<?> textKt = XposedHelpers.findClassIfExists("androidx.compose.material3.TextKt", cl);
        if (textKt != null) {
            XposedBridge.hookAllMethods(textKt, "Text-Nvy7gAk", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    int[] t = top();
                    if (t == null || t[0] != 2 || p.args.length < 8 || !(p.args[0] instanceof String)) return;
                    String x = (String) p.args[0];
                    if (!x.equals("Annulla") && !x.equals("Cancel")) return;
                    try {
                        p.args[2] = composeColor(accent());
                        int last = p.args.length - 1;
                        if (p.args[last] instanceof Integer) p.args[last] = ((Integer) p.args[last]) & ~0x4;
                    } catch (Throwable ignored) {}
                }
            });
        }
        if (iconKt != null) {
            // icone delle opzioni della finestra Riavvia: tinta accento (si toglie il bit "tint predefinito" dalla maschera)
            XposedBridge.hookAllMethods(iconKt, "Icon-ww6aTOc", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    int[] t = top();
                    if (t == null || t[0] != 3 || p.args.length < 7) return;
                    try {
                        p.args[3] = composeColor(accent());
                        int last = p.args.length - 1;
                        if (p.args[last] instanceof Integer) p.args[last] = ((Integer) p.args[last]) & ~0x8;
                    } catch (Throwable ignored) {}
                }
            });
        }
    }
}
