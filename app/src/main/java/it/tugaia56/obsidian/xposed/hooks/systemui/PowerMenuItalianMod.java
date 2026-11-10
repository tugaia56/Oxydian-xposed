package it.tugaia56.obsidian.xposed.hooks.systemui;

import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;

import android.content.Context;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Android puro (RisingOS, ecc.), menu di spegnimento in Compose: molte scritte sono nel codice di SystemUI (non nelle risorse),
 * quindi un overlay non le puo' tradurre ("Power Menu", "Restart", "Slide to power off", "Add Actions"...). Qui, con la lingua del
 * telefono in italiano, il testo passato a Compose Text() viene sostituito con la traduzione. Le altre lingue restano come sono.
 * Su OxygenOS non viene caricata.
 */
public class PowerMenuItalianMod extends XposedMods {

    private static final Map<String, String> IT = new HashMap<>();
    static {
        IT.put("Power Menu", "Menù di alimentazione");
        IT.put("Restart", "Riavvia");
        IT.put("Slide to power off", "Scorri per spegnere");
        IT.put("Slide to restart", "Scorri per riavviare");
        IT.put("Add Actions", "Aggiungi azioni");
        IT.put("Arrange Tiles", "Disponi i tasti");
        IT.put("Cancel", "Annulla");
        IT.put("Edit", "Modifica");
        IT.put("Remove", "Rimuovi");
        IT.put("Resize", "Ridimensiona");
        IT.put("Device Controls", "Controlli dispositivi");
        IT.put("Emergency", "Emergenza");
        // lettore multimediale nel pannello delle notifiche (RisingOS)
        IT.put("Open player", "Apri il lettore");
        IT.put("No active media", "Nessun contenuto in riproduzione");
    }

    public PowerMenuItalianMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {}

    @Override
    public boolean listensTo(String packageName) {
        return SYSTEM_UI.equals(packageName);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // scritte nel codice mostrate da normali TextView (lettore multimediale del pannello)
        XposedBridge.hookAllMethods(android.widget.TextView.class, "setText", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (p.args.length == 0 || !(p.args[0] instanceof CharSequence)) return;
                String t = IT.get(p.args[0].toString());
                if (t == null || !"it".equals(Locale.getDefault().getLanguage())) return;
                p.args[0] = t;
            }
        });
        Class<?> textKt = XposedHelpers.findClassIfExists("androidx.compose.material3.TextKt", lpparam.classLoader);
        if (textKt == null) return;
        XposedBridge.hookAllMethods(textKt, "Text-Nvy7gAk", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (p.args.length == 0 || !(p.args[0] instanceof String)) return;
                String t = IT.get((String) p.args[0]);
                if (t == null || !"it".equals(Locale.getDefault().getLanguage())) return;
                p.args[0] = t;
            }
        });
    }
}
