package it.tugaia56.obsidian.xposed.hooks.permissions;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewTreeObserver;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Finestre dei permessi di Android ("Consentire a ... di inviarti notifiche?"), mod nascosta e sempre attiva.
 * Il controllo permessi di Google dichiara gli stili "overlayable" in gruppi separati e non offre un disegno per
 * lo sfondo della finestra, quindi un overlay di Oxydian Theme non puo' dare il bordo: si disegna da qui.
 * Sfondo Blue Gray Dark (background_dark) e bordo nell'accento, come le altre card.
 * Serve la spunta sul Controllo permessi di Google nell'ambito di Oxydian in LSPosed.
 */
public class PermissionDialogMod extends XposedMods {

    public static final String PKG = "com.google.android.permissioncontroller";
    private static final String TAG_DONE = "oxydian_perm_bg";

    public PermissionDialogMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {}

    @Override
    public boolean listensTo(String packageName) {
        return PKG.equals(packageName);
    }

    private static int sysColor(Resources appRes, String name, int def) {
        try {
            int id = Resources.getSystem().getIdentifier(name, "color", "android");
            if (id != 0) return appRes.getColor(id, null);
        } catch (Throwable ignored) {}
        return def;
    }

    private static void tintButtons(View v, int color) {
        if (v instanceof android.widget.Button) {
            android.widget.Button b = (android.widget.Button) v;
            if (b.getCurrentTextColor() != color) b.setTextColor(color);
        } else if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) tintButtons(g.getChildAt(i), color);
        }
    }

    private static void style(Activity a) {
        try {
            Resources r = a.getResources();
            int id = r.getIdentifier("grant_dialog", "id", "com.android.permissioncontroller");
            if (id == 0) id = r.getIdentifier("grant_dialog", "id", PKG);
            if (id == 0) return;
            View v = a.findViewById(id);
            if (v == null) return;
            // testo dei pulsanti ("Consenti", "Non consentire"...) nell'accento
            tintButtons(v, sysColor(r, "accent_material_dark", 0xFF908DFF));
            if (TAG_DONE.equals(v.getTag())) return;
            float d = r.getDisplayMetrics().density;
            GradientDrawable g = new GradientDrawable();
            g.setColor(sysColor(r, "background_dark", 0xFF1B2029));
            it.tugaia56.obsidian.xposed.utils.GradientBorder.setStroke(g, Math.max(2, Math.round(2 * d)), sysColor(r, "accent_material_dark", 0xFF908DFF));
            g.setCornerRadius(28 * d);
            // ai lati la finestra taglia qualche pixel: il disegno si restringe un po' per far vedere il bordo
            int side = Math.round(4 * d);
            v.setBackground(new android.graphics.drawable.InsetDrawable(g, side, 0, side, 0));
            v.setTag(TAG_DONE);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] PermissionDialogMod ERROR: " + t);
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!PKG.equals(lpparam.packageName)) return;
        XposedBridge.hookAllMethods(Activity.class, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                final Activity a = (Activity) param.thisObject;
                if (!a.getClass().getName().endsWith("GrantPermissionsActivity")) return;
                style(a);
                // il contenuto della finestra cambia da un permesso all'altro: si ricontrolla a ogni disegno
                try {
                    final View root = a.getWindow().getDecorView();
                    root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            style(a);
                        }
                    });
                } catch (Throwable ignored) {}
            }
        });
        log("hook finestra permessi installato");
    }
}
