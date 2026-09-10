package it.tugaia56.obsidian.xposed.hooks.settings;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookConstructor;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static it.tugaia56.obsidian.utils.Constants.Packages.SETTINGS;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.core.content.res.ResourcesCompat;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.BuildConfig;
import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.xposed.ResourceManager;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Adds an "Obsidian" entry to the system Settings homepage that launches the app
 * (mirrors OC's CustomShortcut). Runs in com.android.settings, not SystemUI.
 */
public class CustomShortcut extends XposedMods {

    private static final String ENTRY_TITLE = "Obsidian";

    private boolean mShowInSettings = true;
    private int     mAccentColor    = 0xFF6200EE;
    private Context mSettingsContext;

    public CustomShortcut(Context context) { super(context); }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mShowInSettings = Xprefs.getBoolean("show_entry_settings", true);
        mAccentColor    = Xprefs.getInt("DST_ACCENT1", 0xFF6200EE);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!SETTINGS.equals(lp.packageName)) return;

        Class<?> jumpPrefClass;
        try {
            jumpPrefClass = findClass("com.oplus.settings.widget.preference.SettingsSimpleJumpPreference", lp.classLoader);
        } catch (Throwable t) {
            log("[ Obsidian ] CustomShortcut: SettingsSimpleJumpPreference not found: " + t);
            return;
        }

        findAndHookConstructor(jumpPrefClass, Context.class, AttributeSet.class, int.class, int.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (mSettingsContext == null) mSettingsContext = (Context) p.args[0];
                    }
                });

        Class<?> topLevelSettings;
        try {
            topLevelSettings = findClass("com.android.settings.homepage.TopLevelSettings", lp.classLoader);
        } catch (Throwable t) {
            log("[ Obsidian ] CustomShortcut: TopLevelSettings not found: " + t);
            return;
        }

        hookAllMethods(topLevelSettings, "onPreferenceTreeClick", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                try {
                    if (!ENTRY_TITLE.equals(String.valueOf(getObjectField(p.args[0], "mTitle")))) return;
                    p.setResult(true);
                    Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(BuildConfig.APPLICATION_ID);
                    if (intent != null) mContext.startActivity(intent);
                } catch (Throwable ignored) {}
            }
        });

        hookAllMethods(topLevelSettings, "onCreateAdapter", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!mShowInSettings || mSettingsContext == null) return;
                try {
                    Object pref = jumpPrefClass.getConstructor(Context.class).newInstance(mSettingsContext);

                    Object category = null;
                    for (String key : new String[]{
                            "system_settings_category",       // OOS16+
                            "personality_settings_category",  // OOS14-15
                            "notification_settings_category"}) {
                        try {
                            category = callMethod(p.args[0], "findPreference", key);
                            if (category != null) break;
                        } catch (Throwable ignored) {}
                    }
                    if (category == null) return;

                    // 2026-09-09: era senza tint forzato (per non coprire l'icon_color scelto
                    // dall'utente quando un pack HOS/OOS è attivo) — ma se il pack/overlay non
                    // è applicato (es. bug OverlayManagerService su alcuni device, vedi
                    // project_ksu_next_migration), l'icona restava bianca/neutra invece di
                    // seguire l'accento come le altre righe — segnalato dall'utente. setTint()
                    // qui applica l'accento SOLO come fallback visivo: se un pack con la sua
                    // versione dell'icona (già colorata come vuole l'utente) è davvero attivo,
                    // l'overlay sostituisce l'intera risorsa ic_obsidian_gem PRIMA che questo
                    // codice la legga, quindi il tint si applicherebbe comunque su un colore
                    // già corretto (nessuna regressione pratica).
                    Drawable icon = ResourcesCompat.getDrawable(ResourceManager.modRes,
                            R.drawable.ic_obsidian_gem, mContext.getTheme());
                    if (icon != null) icon.setTint(mAccentColor);

                    callMethod(pref, "setIcon", icon);
                    callMethod(pref, "setTitle", ENTRY_TITLE);
                    callMethod(pref, "setOrder", Integer.MIN_VALUE);
                    callMethod(pref, "setKey", "obsidian_settings_entry");
                    callMethod(category, "addPreference", pref);
                } catch (Throwable t) {
                    log("[ Obsidian ] CustomShortcut: inject entry failed: " + t);
                }
            }
        });
    }

    @Override public boolean listensTo(String packageName) { return SETTINGS.equals(packageName); }
}
