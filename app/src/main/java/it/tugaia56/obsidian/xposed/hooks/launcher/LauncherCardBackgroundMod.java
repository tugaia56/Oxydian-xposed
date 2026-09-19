package it.tugaia56.obsidian.xposed.hooks.launcher;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static it.tugaia56.obsidian.utils.Constants.Packages.LAUNCHER;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

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
 * Sfondo pagina (nero, mai toccato dall'overlay) — 2026-08-30 pomeriggio, PARZIALMENTE risolto.
 * Primo tentativo con getWindow().setBackgroundDrawable() SCARTATO: rendeva la finestra
 * trasparente invece che opaca (windowShowWallpaper/simile nel tema di questa Activity, si
 * vedeva il wallpaper della home dietro). Corretto invece: tingere android:id/content (il
 * FrameLayout radice standard che ogni Activity ha sempre) — funziona per tutto il corpo della
 * pagina. La striscia dell'header ("Impostazioni schermata iniziale" + freccia indietro,
 * dentro appBarLayout/toolbar) resta nera: setBackgroundColor su appBarLayout (un
 * com.google.android.material.appbar.AppBarLayout reale, id e view trovati correttamente, log
 * di conferma visto) non ha alcun effetto visibile — quasi certamente perche' il suo figlio
 * "toolbar" (un ViewGroup che copre quasi tutta l'area, 368px su 369) ha un proprio sfondo
 * opaco che lo copre, stessa famiglia del caso collapsingToolbarLayout/searchView gia' risolto
 * in Impostazioni (dove pero' il fix era tingere il figlio, non il genitore — qui non ancora
 * tentato). Lasciato cosi' su richiesta dell'utente ("lascia stare"): se mai ripreso, il
 * prossimo passo ovvio e' tingere "toolbar" invece di (o oltre a) "appBarLayout".
 */
public class LauncherCardBackgroundMod extends XposedMods {

    private boolean mThemeApplied = false;
    private static final int DEFAULT_CARD_COLOR = 0xFF1B2029;
    // 2026-09-04: era un valore fisso (stesso bug segnalato dall'utente per Settings) — ora legge
    // il "Colore Sfondo" (DST_BACKGROUND) dal vivo, stesse chiavi/logica di MonetFreeze/
    // SettingsCardBackgroundMod, così card e pagina seguono il colore scelto invece di restare
    // bloccate sul default.
    private static final String PREF_BG_ON = "DST_BACKGROUND_on";
    private static final String PREF_BG    = "DST_BACKGROUND";
    private int mCardColor = DEFAULT_CARD_COLOR;
    private int mPageColor = DEFAULT_CARD_COLOR;

    private final List<WeakReference<Object>> mCardInstances = new CopyOnWriteArrayList<>();
    private final List<WeakReference<Activity>> mActivities = new CopyOnWriteArrayList<>();

    public LauncherCardBackgroundMod(Context context) { super(context); }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mThemeApplied = Xprefs.getBoolean("launcher_theme_applied", false);
        mCardColor = Xprefs.getBoolean(PREF_BG_ON, false)
                ? (Xprefs.getInt(PREF_BG, DEFAULT_CARD_COLOR) | 0xFF000000)
                : DEFAULT_CARD_COLOR;
        mPageColor = mCardColor;
        new Handler(Looper.getMainLooper()).post(() -> {
            reapplyCardColors();
            reapplyPageColor();
        });
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

    private void reapplyPageColor() {
        if (!mThemeApplied || !isNight()) return;
        for (WeakReference<Activity> ref : mActivities) {
            Activity activity = ref.get();
            if (activity == null) {
                mActivities.remove(ref);
                continue;
            }
            tintContent(activity);
        }
    }

    private void tintContent(Activity activity) {
        try {
            View content = activity.findViewById(android.R.id.content);
            if (content != null) content.setBackgroundColor(mPageColor);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] LauncherCardBackgroundMod.tintContent content failed: " + t);
        }
        // android:id/content da solo non copre l'header: appBarLayout (contiene la toolbar con
        // freccia indietro + titolo) e' un fratello impilato sopra con sfondo proprio opaco
        // nero (bounds [0,160][1440,369], trovato via uiautomator dump — stessa famiglia del
        // caso collapsingToolbarLayout/searchView gia' risolto per Impostazioni).
        try {
            int id = activity.getResources().getIdentifier("appBarLayout", "id", LAUNCHER);
            View appBar = (id != 0) ? activity.findViewById(id) : null;
            if (appBar != null) appBar.setBackgroundColor(mPageColor);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] LauncherCardBackgroundMod.tintContent appBar failed: " + t);
        }
        // 2026-09-11: content (ContentFrameLayout) prende il colore ma resta invisibile — il suo
        // figlio diretto "fragment_container" (altro FrameLayout, stessi bounds pieni) ha un
        // proprio sfondo opaco nero disegnato sopra, stessa famiglia toolbar/appBarLayout.
        try {
            int id = activity.getResources().getIdentifier("fragment_container", "id", LAUNCHER);
            View fragContainer = (id != 0) ? activity.findViewById(id) : null;
            if (fragContainer != null) fragContainer.setBackgroundColor(mPageColor);
            // 2026-09-11: content/appBarLayout/fragment_container prendono tutti il colore senza
            // errori ma restano invisibili — nel dump uiautomator c'e' un ViewGroup SENZA id
            // subito dentro fragment_container (bounds pieni, stessa famiglia) che con ogni
            // probabilita' e' il vero strato che disegna nero sopra tutto. Non raggiungibile per
            // nome (nessun resource-id): tinto per posizione, scendendo di 2 livelli e saltando
            // la RecyclerView stessa (altrimenti rischio di rompere le righe della lista).
            if (fragContainer instanceof ViewGroup) tintUnnamedDescendants((ViewGroup) fragContainer, 2);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] LauncherCardBackgroundMod.tintContent fragment_container failed: " + t);
        }
    }

    // 2026-09-11: esteso da "solo LauncherSettingsActivity" a QUALSIASI activity dentro il
    // processo com.android.launcher, per coprire anche le sotto-schermate con classe diversa
    // (es. com.oplus.quickstep.locksetting.ui.LockSettingActivity) — segnalata dall'utente come
    // ancora nera. Serve pero' un'esclusione esplicita per le 2 activity che NON sono schermate
    // di impostazioni e che andrebbero rotte visibilmente se tinte di navy pieno: la Home vera
    // (sfondo/icone, sparirebbe sotto il colore) e Recenti/Overview (anteprime app, idem).
    private static final String[] PAGE_TINT_EXCLUDED = {
            "com.android.launcher.Launcher",
            "com.android.quickstep.RecentsActivity",
    };

    private boolean isExcludedFromPageTint(String activityClassName) {
        for (String excluded : PAGE_TINT_EXCLUDED) {
            if (excluded.equals(activityClassName)) return true;
        }
        return false;
    }

    private void tintUnnamedDescendants(ViewGroup parent, int depthLeft) {
        if (depthLeft <= 0) return;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            String cls = child.getClass().getName();
            if (cls.contains("RecyclerView")) continue;
            if (child.getId() == View.NO_ID) {
                try { child.setBackgroundColor(mPageColor); } catch (Throwable ignored) {}
            }
            if (child instanceof ViewGroup) tintUnnamedDescendants((ViewGroup) child, depthLeft - 1);
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

        try {
            findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        Activity activity = (Activity) p.thisObject;
                        if (isExcludedFromPageTint(activity.getClass().getName())) return;
                        mActivities.add(new WeakReference<>(activity));
                        if (!mThemeApplied || !isNight()) return;
                        tintContent(activity);
                        // 2026-09-11: il primo giro viene sovrascritto — qualcosa (il Fragment
                        // che popola la lista, presumibilmente) reimposta lo sfondo subito DOPO
                        // Activity.onResume. Riapplica con un breve ritardo, dopo che si e'
                        // sistemato tutto (stesso "debounced retry" gia' usato in
                        // project_qs_icon_refresh_bug per un problema analogo).
                        new Handler(Looper.getMainLooper()).postDelayed(
                                () -> tintContent(activity), 300);
                        new Handler(Looper.getMainLooper()).postDelayed(
                                () -> tintContent(activity), 900);
                    } catch (Throwable t) {
                        XposedBridge.log("[ Obsidian ] LauncherCardBackgroundMod.onResume failed: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] LauncherCardBackgroundMod: Activity.onResume hook failed: " + t);
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
