package it.tugaia56.obsidian.xposed.hooks.launcher;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static it.tugaia56.obsidian.utils.Constants.Packages.LAUNCHER;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * "Impostazioni schermata iniziale" (com.android.launcher, LauncherSettingsActivity) — stessa
 * card sbagliata gia' vista e risolta in [[project_settings_theme_header_gap]] per
 * com.android.settings, stesso identico meccanismo COUI condiviso: le righe preferenza qui sono
 * com.coui.appcompat.preference.COUICustomListSelectedLinearLayout, che ESTENDE
 * com.coui.appcompat.cardlist.COUICardListSelectedItemLayout (stessa classe di com.android.settings,
 * confermato leggendo il sorgente reale di entrambe le copie via jadx — mCardBackgroundColor,
 * init(), refreshCardBg() sono identici). L'overlay RRO "Stile Launcher" (LauncherThemeCompiler)
 * dava un colore sbagliato/scambiato sulle card — stesso trattamento del card-bg di Settings:
 * abbandonato l'overlay statico, hook runtime diretto su init() -> refreshCardBg(colore).
 *
 * Non serve hookare COUIContextUtil qui (il suo metodo colore e' offuscato in modo diverso in
 * questa build, "a" invece di "getAttrColor" — nomi non stabili tra app diverse): l'hook su
 * init()/refreshCardBg() da solo basta, stessa prova gia' fatta per Settings.
 *
 * Riapplica le card gia' costruite quando updatePrefs() arriva (corsa Xprefs) SEMPRE sul thread
 * UI (Handler+Looper.getMainLooper() — CalledFromWrongThreadException altrimenti, stesso bug
 * gia' trovato e risolto oggi in SettingsCardBackgroundMod).
 *
 * Sfondo pagina (nero, mai toccato dall'overlay) NON risolto qui: setBackgroundDrawable() sulla
 * finestra di LauncherSettingsActivity la rende trasparente invece che opaca — questa Activity
 * ha evidentemente windowShowWallpaper/simile nel tema, si vede il wallpaper della home dietro
 * invece del colore pieno. Serve un approccio diverso (probabilmente tingere una View reale
 * nell'albero, come fatto per l'header di Impostazioni, non la finestra). Tentativo scartato,
 * non peggiorare cambiando la finestra — solo le card sono corrette per ora.
 */
public class LauncherCardBackgroundMod extends XposedMods {

    private boolean mThemeApplied = false;
    // android:color/button_material_dark, il colore grigio scuro standard AOSP che l'overlay
    // "Stile Launcher" avrebbe dovuto assegnare alle card (finito scambiato con lo sfondo di
    // pagina invece — vedi project_launcher_mods_rollout memory).
    private final int mCardColor = 0xFF404040;

    private final List<WeakReference<Object>> mCardInstances = new CopyOnWriteArrayList<>();

    public LauncherCardBackgroundMod(Context context) { super(context); }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mThemeApplied = Xprefs.getBoolean("launcher_theme_applied", false);
        new Handler(Looper.getMainLooper()).post(this::reapplyCardColors);
    }

    private void reapplyCardColors() {
        if (!mThemeApplied || !isNight()) return;
        for (WeakReference<Object> ref : mCardInstances) {
            Object card = ref.get();
            if (card == null) {
                mCardInstances.remove(ref);
                continue;
            }
            try {
                callMethod(card, "refreshCardBg", mCardColor);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!LAUNCHER.equals(lp.packageName)) return;

        try {
            Class<?> cardLayoutCls = findClass(
                    "com.coui.appcompat.cardlist.COUICardListSelectedItemLayout", lp.classLoader);
            findAndHookMethod(cardLayoutCls, "init", Context.class, boolean.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    mCardInstances.add(new WeakReference<>(p.thisObject));
                    if (!mThemeApplied || !isNight()) return;
                    try {
                        callMethod(p.thisObject, "refreshCardBg", mCardColor);
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] LauncherCardBackgroundMod: COUICardListSelectedItemLayout hook failed: " + t);
        }
    }

    private boolean isNight() {
        try {
            return (mContext.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override public boolean listensTo(String packageName) { return LAUNCHER.equals(packageName); }
}
