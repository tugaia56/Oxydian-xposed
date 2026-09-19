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

                    // Layout uguale alle voci OEM vicine: senza, la ImageView dell'icona
                    // è 72px invece di 108px. NB: prende una voce a RIGA SINGOLA come
                    // modello — getPreference(0) è "Schermata iniziale…" (2 righe) e usa un
                    // frame icona più alto (120px), che rendeva la gemma più grande delle
                    // altre. Scandisce quindi i figli e salta i titoli multi-riga.
                    try {
                        int cnt = (int) callMethod(category, "getPreferenceCount");
                        for (int k = 0; k < cnt; k++) {
                            Object tmpl = callMethod(category, "getPreference", k);
                            CharSequence tt = (CharSequence) callMethod(tmpl, "getTitle");
                            if (tt != null && tt.toString().contains("\n")) continue;
                            if (tt != null && tt.length() > 24) continue; // probabile a capo
                            callMethod(pref, "setLayoutResource",
                                    callMethod(tmpl, "getLayoutResource"));
                            break;
                        }
                    } catch (Throwable ignored) {}
                    callMethod(pref, "setIconSpaceReserved", true);

                    // Icona: logo Obsidian ufficiale a 4 schegge, bianco. Risorsa DEDICATA
                    // (ic_obsidian_shard, non ic_obsidian_gem) perché modRes viene da
                    // createPackageContext e include gli RRO attivi: il pack icone (SIP2)
                    // sovrascrive ic_obsidian_gem con la sua versione vecchia, questa no.
                    // L'anello circolare NON è disegnato da OOS per una preferenza iniettata
                    // (quello delle altre righe è nel drawable del pack SIP1) → lo compongo
                    // qui: cerchio vuoto bordo accento + scheggia bianca al centro.
                    float density = mContext.getResources().getDisplayMetrics().density;
                    // L'anello e la scheggia hanno DIMENSIONI ESPLICITE e sono centrati:
                    // così il cerchio resta identico alle altre righe (~36dp) anche se il
                    // frame icona della preferenza iniettata è più largo (misurato 120px
                    // vs 108px delle voci OEM — evita la gemma "un po' più grande").
                    int ringPx  = Math.round(density * 36f);
                    int shardPx = Math.round(density * 21f);
                    android.graphics.drawable.GradientDrawable ring =
                            new android.graphics.drawable.GradientDrawable();
                    ring.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                    ring.setColor(android.graphics.Color.TRANSPARENT);
                    ring.setStroke(Math.round(density * 2f), mAccentColor);
                    ring.setSize(ringPx, ringPx);

                    Drawable shard = ResourcesCompat.getDrawable(ResourceManager.modRes,
                            R.drawable.ic_obsidian_shard, mContext.getTheme());
                    Drawable icon;
                    if (shard != null) {
                        android.graphics.drawable.LayerDrawable ld =
                                new android.graphics.drawable.LayerDrawable(
                                        new Drawable[]{ring, shard});
                        ld.setLayerGravity(0, android.view.Gravity.CENTER);
                        ld.setLayerGravity(1, android.view.Gravity.CENTER);
                        ld.setLayerSize(0, ringPx, ringPx);
                        ld.setLayerSize(1, shardPx, shardPx);
                        icon = ld;
                    } else {
                        icon = ring;
                    }

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
