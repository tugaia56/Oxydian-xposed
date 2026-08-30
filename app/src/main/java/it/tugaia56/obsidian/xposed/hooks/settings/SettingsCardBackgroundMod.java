package it.tugaia56.obsidian.xposed.hooks.settings;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static it.tugaia56.obsidian.utils.Constants.Packages.SETTINGS;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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

    // Card costruite (COUICardListSelectedItemLayout.init()) PRIMA che Xprefs consegnasse
    // updatePrefs() la prima volta: mThemeApplied era ancora false in quel momento, quindi
    // sono rimaste col colore stock per sempre (nessun redraw automatico dopo). Tenerne un
    // riferimento debole e ripassarle in refreshCardBg() non appena le prefs sono pronte
    // risolve la corsa una volta per tutte, senza dover indovinare un ritardo fisso.
    private final List<WeakReference<Object>> mCardInstances = new CopyOnWriteArrayList<>();
    // Stessa corsa, stesso rimedio, per l'header/barra ricerca (vedi sotto).
    private final List<WeakReference<Activity>> mHomepageActivities = new CopyOnWriteArrayList<>();

    public SettingsCardBackgroundMod(Context context) { super(context); }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mThemeApplied = Xprefs.getBoolean("settings_theme_applied", false);
        reapplyCardColors();
        reapplyHeaderColors();
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

    private void reapplyHeaderColors() {
        if (!mThemeApplied || !isNight()) return;
        for (WeakReference<Activity> ref : mHomepageActivities) {
            Activity activity = ref.get();
            if (activity == null) {
                mHomepageActivities.remove(ref);
                continue;
            }
            applyHeaderTheme(activity);
        }
    }

    private void applyHeaderTheme(Activity activity) {
        if (!mThemeApplied || !isNight()) return;
        tintViewById(activity, "collapsingToolbarLayout");
        tintViewById(activity, "searchView");
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
                    mCardInstances.add(new WeakReference<>(p.thisObject));
                    if (!mThemeApplied || !isNight()) return;
                    try {
                        callMethod(p.thisObject, "refreshCardBg", mCardColor);
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] SettingsCardBackgroundMod: COUICardListSelectedItemLayout hook failed: " + t);
        }

        // Header ("Impostazioni" + barra ricerca) — 2026-08-30, stessa richiesta dell'utente.
        // Niente resource-id da intercettare qui: l'AppBarLayout dell'header ha
        // android:background=@android:color/transparent (il nero visibile è la finestra sotto),
        // e lo sfondo della barra ricerca arriva da un colore Material You dinamico
        // (settingslib_materialColorSurfaceContainerLowest -> token di sistema legato al
        // wallpaper), non da una risorsa statica di Settings — stessa famiglia "bypassa RRO"
        // della card, ma qui la via diretta è più semplice: setBackgroundColor sulla View vera,
        // bypassando la risoluzione risorse.
        //
        // Hook su android.app.Activity (classe di framework, un solo classloader possibile, mai
        // ambiguo) invece che sulle classi Activity di com.android.settings/com.oplus.settings —
        // findClass su queste ultime restituiva un hook installato senza errori ma che non
        // scattava MAI (probabile classloader diverso da quello realmente usato per istanziare
        // l'Activity, dato che questo pacchetto OEM carica moduli/dex extra); filtrare per nome
        // classe dopo un hook generico su Activity.onResume() aggira il problema alla radice.
        try {
            findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        Activity activity = (Activity) p.thisObject;
                        String cls = activity.getClass().getName();
                        if (!"com.oplus.settings.feature.homepage.OplusSettingsHomepageActivity".equals(cls)
                                && !"com.android.settings.homepage.SettingsHomepageActivity".equals(cls)) return;
                        // Tracciata anche qui: se a questo giro Xprefs non e' ancora pronta,
                        // updatePrefs() la ritrova via reapplyHeaderColors() non appena arriva —
                        // stessa corsa, stesso rimedio delle card qui sopra. I postDelayed sotto
                        // restano solo come rete di sicurezza (coprono il caso limite in cui
                        // l'Activity sia gia' sparita prima che Xprefs sia pronta).
                        mHomepageActivities.add(new WeakReference<>(activity));
                        // "app_bar"/"search_action_bar" (da search_bar.xml, libreria collapsing
                        // toolbar generica) erano un abbaglio: mai presenti nell'albero reale di
                        // questa Activity (findViewById sempre null). Gli id VERI, confermati con
                        // uiautomator dump sullo schermo dal vivo: collapsingToolbarLayout
                        // (l'header "Impostazioni") e searchView (la pillola di ricerca).
                        View decor = activity.getWindow().getDecorView();
                        Runnable tint = () -> applyHeaderTheme(activity);
                        decor.postDelayed(tint, 400);
                        decor.postDelayed(tint, 1500);
                        decor.postDelayed(tint, 4000);
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] SettingsCardBackgroundMod: Activity.onResume hook failed: " + t);
        }
    }

    private void tintViewById(Activity activity, String idName) {
        try {
            int id = activity.getResources().getIdentifier(idName, "id", SETTINGS);
            View v = (id != 0) ? activity.findViewById(id) : null;
            if (v == null) return;
            // Tentato setContentScrimColor/setStatusBarScrimColor per evitare un flash nero
            // visto durante lo scroll con setBackgroundColor: risultato peggiore (nero anche a
            // riposo, da espansa, perche' lo scrim dipinge solo da collassata in su — a riposo
            // espansa quella View non ha background proprio e resta trasparente). Tornato al
            // setBackgroundColor semplice, che a riposo (espansa E collassata) e' corretto,
            // confermato via screenshot; il flash durante il trascinamento resta un difetto
            // minore e transitorio, non un colore sbagliato fisso.
            v.setBackgroundColor(mCardColor);
        } catch (Throwable ignored) {}
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
