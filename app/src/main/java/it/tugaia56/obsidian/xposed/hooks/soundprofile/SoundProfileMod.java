package it.tugaia56.obsidian.xposed.hooks.soundprofile;

import android.content.Context;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Sound Profile (Orion.Soft). Le barre dei profili non selezionati hanno il testo e le icone di un nero scritto nel
 * codice (Color.BLACK passato a fragMostrarPerfiles.h0(CardView, int), che colora il nome e filtra le icone): un overlay
 * di Oxydian Theme non lo raggiunge. Con le barre scure (PerfilNoSeleccionado = ripple_material_dark nell'overlay) il
 * nero non si leggerebbe: qui, al momento di colorarle, il nero diventa bianco. Il bianco dei profili selezionati e
 * bloccati resta com'e'.
 *
 * La funzione si cerca per firma (CardView, int) dentro fragMostrarPerfiles, perche' il suo nome e' offuscato
 * (h0 nella versione 13.03). Mod nascosta, sempre attiva, nessuna impostazione. Serve la spunta su Sound Profile
 * nell'ambito di Oxydian in LSPosed.
 */
public class SoundProfileMod extends XposedMods {

    public static final String PKG = "Orion.Soft";
    private static final String FRAGMENT = "orion.soft.fragMostrarPerfiles";
    private static final String CARD_VIEW = "androidx.cardview.widget.CardView";

    public SoundProfileMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {}

    @Override
    public boolean listensTo(String packageName) {
        return PKG.equals(packageName);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!PKG.equals(lp.packageName)) return;
        Class<?> cls = XposedHelpers.findClassIfExists(FRAGMENT, lp.classLoader);
        if (cls == null) {
            XposedBridge.log("[ Obsidian ] SoundProfileMod: " + FRAGMENT + " not found (app version changed?)");
            return;
        }
        int hooked = 0;
        for (Method m : cls.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 2 && p[1] == int.class && CARD_VIEW.equals(p[0].getName())) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            if ((Integer) param.args[1] == 0xFF000000) param.args[1] = 0xFFFFFFFF;
                        } catch (Throwable ignored) {}
                    }
                });
                hooked++;
            }
        }
        XposedBridge.log("[ Obsidian ] SoundProfileMod: hooked " + hooked + " card colouring method(s) in " + FRAGMENT);
    }
}
