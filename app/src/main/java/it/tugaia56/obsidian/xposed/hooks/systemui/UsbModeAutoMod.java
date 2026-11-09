package it.tugaia56.obsidian.xposed.hooks.systemui;

import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.provider.Settings;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Android puro (RisingOS, ecc.): quando si collega il cavo SystemUI mostra "Seleziona la modalità USB"
 * (UsbModePickerDialogDelegate). Con l'opzione "Rimuovi finestra dialogo USB" accesa la finestra non compare più e si
 * riapplica l'ultima modalità scelta dall'utente (ricordata in Settings.Global "oxy_usb_mode"). Finché non c'è una scelta
 * ricordata la finestra compare come al solito; la scelta si registra ogni volta che l'utente ne fa una.
 * Per cambiare modalità: spegnere l'opzione, scegliere dalla finestra, riaccendere l'opzione. Su OxygenOS non viene caricata.
 */
public class UsbModeAutoMod extends XposedMods {

    private static final String KEY = "oxy_usb_mode";
    private static final String CLS = "com.android.systemui.usb.UsbModePickerDialogDelegate";
    private volatile boolean mOn = false;

    public UsbModeAutoMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mOn = Xprefs.getBoolean("remove_usb_dialog", false);
    }

    @Override
    public boolean listensTo(String packageName) {
        return SYSTEM_UI.equals(packageName);
    }

    private static Context ctxOf(Object delegate) {
        try { return (Context) XposedHelpers.getObjectField(delegate, "context"); } catch (Throwable t) { return null; }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        Class<?> c;
        try {
            c = XposedHelpers.findClass(CLS, lpparam.classLoader);
        } catch (Throwable t) {
            return;   // ROM senza il selettore USB di SystemUI: niente da fare
        }
        // ricorda la modalità scelta (funzione USB, maschera di bit)
        XposedBridge.hookAllMethods(c, "applyUsbFunction", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                try {
                    Context ctx = ctxOf(p.thisObject);
                    if (ctx != null && p.args.length > 0 && p.args[0] instanceof Long)
                        Settings.Global.putLong(ctx.getContentResolver(), KEY, (Long) p.args[0]);
                } catch (Throwable ignored) {}
            }
        });
        // con l'opzione accesa e una scelta ricordata: niente finestra, si riapplica la scelta
        XposedBridge.hookAllMethods(c, "showDialog", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!mOn) return;
                try {
                    Context ctx = ctxOf(p.thisObject);
                    if (ctx == null || p.args.length < 1 || !(p.args[0] instanceof Long)) return;
                    long saved = Settings.Global.getLong(ctx.getContentResolver(), KEY, -1L);
                    if (saved < 0) return;   // nessuna scelta ancora: la finestra compare
                    p.setResult(null);
                    XposedHelpers.callMethod(p.thisObject, "applyUsbFunction", saved, (Long) p.args[0]);
                } catch (Throwable t) {
                    XposedBridge.log("[ Obsidian ] UsbModeAutoMod: " + t);
                }
            }
        });
    }
}
