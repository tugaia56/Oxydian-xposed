package it.tugaia56.obsidian.xposed.hooks.settings;

import static de.robv.android.xposed.XposedBridge.hookAllConstructors;
import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookConstructor;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static it.tugaia56.obsidian.utils.Constants.Packages.SETTINGS;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
 *
 * 2026-09-04: estesa alle app OEM Oplus raggiungibili da dentro Impostazioni (WirelessSettings —
 * Wi-Fi/Bluetooth/Device Connect, HeyCast — Cast) su richiesta dell'utente. Verificato PRIMA di
 * scrivere codice, non per analogia: entrambe le APK reali (estratte dal device) contengono le
 * stesse classi COUICardListSelectedItemLayout/COUIContextUtil già note, quindi le stesse card
 * "invisibili" della Home di Impostazioni si ottengono con lo stesso identico meccanismo — solo
 * il tinting dell'header (barra "Impostazioni" con ricerca) resta specifico a Settings, quelle
 * app non hanno un header analogo con lo stesso nome Activity da filtrare.
 */
public class SettingsCardBackgroundMod extends XposedMods {

    // Card-tinting: SETTINGS + le app OEM raggiunte dall'interno di Impostazioni (elenco che
    // l'utente sta ancora ampliando esplorando a mano, 2026-09-04 — verificate PRIMA di
    // aggiungerle qui: tutte contengono davvero COUICardListSelectedItemLayout/COUIContextUtil
    // nel proprio dex reale, non per analogia). Page-bg-tinting (android:id/content) usa lo
    // STESSO elenco: a differenza di Settings (che ha il proprio overlay OMS compilato a parte,
    // SettingsThemeCompiler) queste app non hanno mai avuto uno sfondo pagina navy, quindi lo
    // tingiamo qui via hook diretto. Header-tinting resta SOLO Settings (parcheggiato per le
    // altre — vedi tintPageContent, appBarLayout/toolbar trovati ma non ancora risolti stabile).
    private static final String WIRELESS_SETTINGS = "com.oplus.wirelesssettings";
    private static final String CAST = "com.oplus.cast";
    private static final String OP_SYNERGY = "com.oplus.linker";
    private static final String WALLPAPERS = "com.oplus.wallpapers";
    private static final String NOTIFICATION_CENTER = "com.oplus.notificationmanager";
    private static final String UX_DESIGN = "com.oplus.uxdesign";
    private static final String SOS = "com.oplus.sos";
    private static final String BATTERY = "com.oplus.battery";
    private static final String PANTANAL_UMS = "com.oplus.pantanal.ums";
    private static final String OPERATION_MANUAL = "com.coloros.operationManual";
    private static final String MY_DEVICES = "com.heytap.mydevices";
    private static final String PHONE = "com.android.phone";
    private static final String SCREENSHOT = "com.oplus.screenshot";
    // Esclusi (nessuna classe COUI card nel dex reale, verificato 2026-09-04): GMS, Google
    // Permission Controller, Google Wellbeing — Material Design puro di Google, nessun theming
    // COUI possibile lì; GMS anche troppo grande/delicato per toccarlo comunque.
    private static final String[] CORE_PACKAGES = {
            SETTINGS, WIRELESS_SETTINGS, CAST, OP_SYNERGY, WALLPAPERS, NOTIFICATION_CENTER,
            UX_DESIGN, SOS, BATTERY, PANTANAL_UMS, OPERATION_MANUAL, MY_DEVICES, PHONE
    };
    // 2026-09-05: lista lunga fornita dall'utente (app di sistema che aveva già temato via
    // Substratum) — verificate TUTTE una per una via script su device (unzip -p + grep sul dex
    // reale di ciascuna, non per analogia) prima di aggiungerle: 58 su 61 hanno le classi COUI
    // giuste. Esclusi (nessuna classe COUI nel dex, confermato): com.android.cellbroadcastreceiver,
    // com.android.providers.media, com.oplus.engineermode — puro AOSP/diagnostica, niente da temare.
    // Molte di queste sono servizi senza UI mai mostrata all'utente: innocuo includerle comunque
    // (l'hook semplicemente non trova mai nessuna vista da tingere), ma tenerne traccia qui evita
    // di doverle re-includere/verificare una per una in futuro.
    // public: riusata da XPLauncher per ritardare l'avvio dei mod in questi processi al boot
    // (vedi XPLauncher.DEFERRED_STARTUP_DELAY_MS) — richiesta utente 2026-09-06.
    public static final String[] EXTRA_OEM_PACKAGES = {
            "com.oplus.cota", "com.oplus.ota", "com.oplus.multiapp", "com.oplus.games",
            "com.coloros.smartsidebar", "com.oplus.beaconlink", "com.oplus.appbooster",
            "com.oneplus.calculator", "com.oplus.safecenter", "com.oplus.keyguard.clock.base",
            "com.coloros.systemclone", "com.oplus.eyeprotect", "com.heytap.accessory",
            "com.oplus.remotecontrol", "com.oplus.aiwriter", "com.oplus.melody",
            "com.oneplus.gallery", "com.oplus.camera", "com.oplus.gesture",
            "com.oplus.securitypermission", "com.oplus.phonemanager", "com.android.server.telecom",
            "com.heytap.browser", "net.oneplus.weather", "com.oplus.aimemory",
            "com.coloros.scenemode", "com.oplus.aiunit", "com.oplus.uiengine",
            "com.oplus.pscanvas", "com.oneplus.account", "com.oneplus.oshare",
            "com.oneplus.deskclock", "com.oplus.contentportal", "com.oplus.securepay",
            "com.oplus.apprecover", "com.coloros.bootreg", "com.oplus.screenrecorder",
            "com.oppo.quicksearchbox", "com.coloros.colordirectservice", "com.oplus.screenshot",
            "com.heytap.pictorial", "com.oplus.aod", "com.android.wallpaper.livepicker",
            "com.coloros.floatassistant", "com.coloros.assistantscreen",
            "com.coloros.accessibilityassistant", "com.oplus.trafficmonitor", "com.oplus.vdc",
            "com.coloros.video"
    };
    private static final String[] CARD_TARGET_PACKAGES = concat(CORE_PACKAGES, EXTRA_OEM_PACKAGES);
    // Sottoinsieme di CARD_TARGET_PACKAGES che riceve anche il tint dello sfondo pagina (tutte
    // tranne SETTINGS, che ha già il proprio overlay OMS separato).
    private static final String[] PAGE_BG_TARGET_PACKAGES = concat(new String[] {
            WIRELESS_SETTINGS, CAST, OP_SYNERGY, WALLPAPERS, NOTIFICATION_CENTER,
            UX_DESIGN, SOS, BATTERY, PANTANAL_UMS, OPERATION_MANUAL, MY_DEVICES, PHONE
    }, EXTRA_OEM_PACKAGES);

    private static String[] concat(String[] a, String[] b) {
        String[] r = new String[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private boolean mThemeApplied = false;
    // Colore di default — usato quando l'utente non ha un "Colore Sfondo" (DST_BACKGROUND)
    // personalizzato attivo, stesso hex della card "invisibile" storica.
    private static final int DEFAULT_CARD_COLOR = 0xFF1B2029;
    // 2026-09-04: prima era un valore fisso — segnalato dall'utente ("ho cambiato lo sfondo
    // tre volte con OBS, ma le card hanno sempre #1b2029"). MonetFreeze già legge DST_BACKGROUND
    // dal vivo per SystemUI (barra di stato, PIN, ecc.) — stesse chiavi/logica qui, così le card
    // seguono lo stesso colore scelto invece di restare bloccate sul default.
    private static final String PREF_BG_ON = "DST_BACKGROUND_on";
    private static final String PREF_BG    = "DST_BACKGROUND";
    private int mCardColor = DEFAULT_CARD_COLOR;
    // Bordo accento sul menu overflow (COUIPopupListWindow) — richiesto dall'utente 2026-09-05
    // per "I miei dispositivi", stesso stile accento+sfondo OBS già usato per i dialoghi in-app
    // (vedi ObsidianTheme.dialogBackground/themeDialog). Stesse chiavi/logica di MonetFreeze.
    private static final int DEFAULT_ACCENT_COLOR = 0xFF908DFF;
    private static final String PREF_ACCENT_ON = "DST_ACCENT1_on";
    private static final String PREF_ACCENT = "DST_ACCENT1";
    private int mAccentColor = DEFAULT_ACCENT_COLOR;

    // Card costruite (COUICardListSelectedItemLayout.init()) PRIMA che Xprefs consegnasse
    // updatePrefs() la prima volta: mThemeApplied era ancora false in quel momento, quindi
    // sono rimaste col colore stock per sempre (nessun redraw automatico dopo). Tenerne un
    // riferimento debole e ripassarle in refreshCardBg() non appena le prefs sono pronte
    // risolve la corsa una volta per tutte, senza dover indovinare un ritardo fisso.
    private final List<WeakReference<Object>> mCardInstances = new CopyOnWriteArrayList<>();
    // Stessa corsa, stesso rimedio, per l'header/barra ricerca (vedi sotto).
    private final List<WeakReference<Activity>> mHomepageActivities = new CopyOnWriteArrayList<>();
    // Stessa corsa, stesso rimedio, per lo sfondo pagina di WirelessSettings/Cast (vedi sotto).
    private final List<WeakReference<Activity>> mPageActivities = new CopyOnWriteArrayList<>();
    // 2026-09-11: stessa corsa, stesso rimedio, per i pulsanti/bordo dello screenshot flottante
    // (com.oplus.screenshot) — segnalata dall'utente ("succede spesso che perde l'accento del
    // bordo ed i pulsanti rimangono stock" dopo essere uscito e rientrato). Costruttori, non
    // un'Activity: ogni nuovo screenshot crea ISTANZE FRESCHE di questi widget, e se una nasce
    // prima che updatePrefs() sia arrivato la prima volta restava stock per sempre (nessun
    // redraw automatico dopo, a differenza delle card/header sopra che avevano già questo
    // rimedio).
    private final List<WeakReference<View>> mFloatButtons = new CopyOnWriteArrayList<>();
    private final List<WeakReference<Object>> mFloatPreviewWidgets = new CopyOnWriteArrayList<>();
    // TypedArray "marchiati" dall'hook Theme.obtainStyledAttributes(int[]) qui sotto — build
    // offuscate (Wallpapers/Battery/Pantanal UMS) risolvono il colore carta passando da qui,
    // non da COUIContextUtil. WeakHashMap: nessuna leak, l'oggetto sparisce da solo con recycle().
    private final java.util.Map<Object, Boolean> mFlaggedArrays =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    // Classe reale di COUICardListSelectedItemLayout per com.oplus.pantanal.ums (vedi
    // handleLoadPackage) — usata per il bordo per-TIPO in tintPantanalAiCardBorders, indipendente
    // da quale overload di init()/refreshCardBg esiste davvero in questa copia della libreria.
    private Class<?> mCardListLayoutCls;

    public SettingsCardBackgroundMod(Context context) { super(context); }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mThemeApplied = Xprefs.getBoolean("settings_theme_applied", false);
        mCardColor = Xprefs.getBoolean(PREF_BG_ON, false)
                ? (Xprefs.getInt(PREF_BG, DEFAULT_CARD_COLOR) | 0xFF000000)
                : DEFAULT_CARD_COLOR;
        mAccentColor = Xprefs.getBoolean(PREF_ACCENT_ON, false)
                ? (Xprefs.getInt(PREF_ACCENT, DEFAULT_ACCENT_COLOR) | 0xFF000000)
                : DEFAULT_ACCENT_COLOR;
        applyLive();
    }

    /** 2026-09-05: fallback per processi dove Xprefs (il ContentProvider) è irraggiungibile per
     *  davvero — filtro di visibilità pacchetti di Android, confermato via logcat dal vivo su
     *  com.oneplus.account/com.oplus.games ("Failed to find provider info" ogni secondo, mai
     *  risolto). Il PRIMO tentativo di questo fallback rileggeva le prefs dal file, MA
     *  fallisce ALLO STESSO MODO: quel file sta nella cartella privata di Obsidian, leggibile
     *  solo dal SUO uid — un processo di un'ALTRA app (l'uid di com.oneplus.account, non root)
     *  non ha permesso di leggerlo, stessa causa di fondo (l'app non può "vedere" nulla di
     *  Obsidian). Le SYSTEM PROPERTIES sono l'unico canale leggibile da QUALSIASI app
     *  indipendentemente da entrambi questi limiti — stesso motivo per cui MonetFreeze le usa
     *  già per il proprio fallback di boot. Richiede che DstFabricatedUtil.saveBootProps() sia
     *  stato chiamato almeno una volta (lo è ad ogni cambio di accento/sfondo/tema Settings). */
    @Override
    public void preloadFallback() {
        try {
            mThemeApplied = "1".equals(readSysProp("persist.obsidian.dst.settings_theme_on", "0"));
            boolean bgOn = "1".equals(readSysProp("persist.obsidian.dst.bg_on", "0"));
            mCardColor = bgOn
                    ? (parseIntProp(readSysProp("persist.obsidian.dst.bg", ""), DEFAULT_CARD_COLOR) | 0xFF000000)
                    : DEFAULT_CARD_COLOR;
            boolean a1On = "1".equals(readSysProp("persist.obsidian.dst.a1_on", "0"));
            mAccentColor = a1On
                    ? (parseIntProp(readSysProp("persist.obsidian.dst.a1", ""), DEFAULT_ACCENT_COLOR) | 0xFF000000)
                    : DEFAULT_ACCENT_COLOR;
            applyLive();
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] SettingsCardBackgroundMod.preloadFallback failed: " + t);
        }
    }

    /** Stesso trucco via riflessione già usato in DstDialogStyle/MonetFreeze per bypassare le
     *  restrizioni hidden-API su SystemProperties nei processi app normali. */
    private static String readSysProp(String key, String def) {
        try {
            Class<?> sp = de.robv.android.xposed.XposedHelpers.findClass("android.os.SystemProperties", null);
            Object val = de.robv.android.xposed.XposedHelpers.callStaticMethod(sp, "get", key, def);
            return val != null ? (String) val : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private static int parseIntProp(String s, int def) {
        if (s == null || s.isEmpty()) return def;
        try { return Integer.parseInt(s); } catch (NumberFormatException ex) { return def; }
    }

    /** Corpo condiviso tra updatePrefs() (via ContentProvider) e preloadFallback() (via file) —
     *  entrambi girano su un thread che NON è quello UI (callback di Xprefs, o il thread di
     *  XPLauncher.waitAndRefreshPrefs()) — confermato da CalledFromWrongThreadException nei log
     *  (refreshCardBg()/setBackgroundColor() toccavano le View da un thread sbagliato, fallendo
     *  in modo silenzioso: nessun crash, ma l'aggiornamento non arrivava mai a schermo). Stesso
     *  pattern già usato in StatusbarClock per lo stesso identico problema. */
    private void applyLive() {
        new Handler(Looper.getMainLooper()).post(() -> {
            reapplyCardColors();
            reapplyHeaderColors();
            reapplyPageColors();
            reapplyFloatButtons();
            reapplyFloatPreviewWidgets();
        });
    }

    private void reapplyFloatButtons() {
        if (!mThemeApplied || !isNight()) return;
        for (WeakReference<View> ref : mFloatButtons) {
            View v = ref.get();
            if (v == null) { mFloatButtons.remove(ref); continue; }
            tintFloatButton(v);
        }
    }

    private void tintFloatButton(View v) {
        try {
            float radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 999,
                    v.getResources().getDisplayMetrics());
            float stroke = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.5f,
                    v.getResources().getDisplayMetrics());
            android.graphics.drawable.GradientDrawable bg =
                    new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            bg.setColor(mCardColor);
            bg.setCornerRadius(radius);
            bg.setStroke((int) stroke, mAccentColor);
            v.setBackground(bg);
        } catch (Throwable ignored) {}
    }

    private void reapplyFloatPreviewWidgets() {
        if (!mThemeApplied || !isNight()) return;
        for (WeakReference<Object> ref : mFloatPreviewWidgets) {
            Object w = ref.get();
            if (w == null) { mFloatPreviewWidgets.remove(ref); continue; }
            tintFloatPreviewWidget(w);
        }
    }

    private void tintFloatPreviewWidget(Object widget) {
        try {
            callMethod(widget, "setBorderColor", mAccentColor);
            if (widget instanceof View v) v.postInvalidate();
        } catch (Throwable ignored) {}
    }

    private void reapplyPageColors() {
        if (!mThemeApplied || !isNight()) return;
        for (WeakReference<Activity> ref : mPageActivities) {
            Activity activity = ref.get();
            if (activity == null) {
                mPageActivities.remove(ref);
                continue;
            }
            tintPageContent(activity);
        }
    }

    private void tintPageContent(Activity activity) {
        if (!mThemeApplied || !isNight()) return;
        try {
            View content = activity.findViewById(android.R.id.content);
            if (content != null) content.setBackgroundColor(mCardColor);
        } catch (Throwable ignored) {}
        // Fallback strutturale (com.oplus.uxdesign "Colori"/UxColorSettingActivity, 2026-09-07):
        // qui il figlio diretto di content che copre tutto lo schermo NON ha nome (un LinearLayout
        // anonimo, confermato via dumpsys activity --views dal vivo) — nessun id da provare, a
        // differenza di "list_layout"/"personalRoot"/eccetera più sotto. Se content ha ESATTAMENTE
        // un figlio e quello copre l'intera area di content, tingiamo anche lui a prescindere dal
        // nome: pattern probabilmente comune ad altre app future con lo stesso problema.
        try {
            View content = activity.findViewById(android.R.id.content);
            if (content instanceof android.view.ViewGroup contentGroup && contentGroup.getChildCount() == 1) {
                View onlyChild = contentGroup.getChildAt(0);
                if (onlyChild.getWidth() >= contentGroup.getWidth()
                        && onlyChild.getHeight() >= contentGroup.getHeight()) {
                    onlyChild.setBackgroundColor(mCardColor);
                }
            }
        } catch (Throwable ignored) {}
        // Header nero segnalato dall'utente 2026-09-04 su Wireless Settings/Bluetooth — id VERI
        // confermati con uiautomator dump dal vivo sulla schermata Bluetooth: "appBarLayout" e
        // "toolbar" (nomi diversi da Settings, niente collapsingToolbarLayout/searchView qui —
        // è un toolbar semplice, non un header con ricerca). Namespace del pacchetto CORRENTE
        // (non SETTINGS), stesso motivo dell'attr COUI più sopra.
        tintViewByIdInPackage(activity, "appBarLayout");
        tintViewByIdInPackage(activity, "toolbar");
        // com.oplus.battery ("Batteria", 2026-09-05): stesso header nero, ma con id DIVERSI —
        // "consumption_appBarLayout"/"consumption_toolbar" invece di quelli generici sopra,
        // confermato via uiautomator dump dal vivo. Provarli non fa danno altrove (no-op se
        // assenti).
        tintViewByIdInPackage(activity, "consumption_appBarLayout");
        tintViewByIdInPackage(activity, "consumption_toolbar");
        // com.oneplus.account ("Account", 2026-09-05): stesso header nero, id "appbar" (minuscolo,
        // non "appBarLayout") — confermato via uiautomator dump dal vivo.
        tintViewByIdInPackage(activity, "appbar");
        // com.oplus.notificationmanager ("Illuminazione bordi" — AodSurfaceHomeActivity,
        // 2026-09-07): header nero, id DIVERSI ancora — "aod_toolbar_container"/"aod_toolbar",
        // confermato via dumpsys activity --views dal vivo. Il resto della pagina (anteprima
        // telefono + swatch colori) era già navy giusto, solo l'header mancava.
        tintViewByIdInPackage(activity, "aod_toolbar_container");
        tintViewByIdInPackage(activity, "aod_toolbar");
        // Stessa pagina, 2026-09-07: l'utente segnala anche un "bottom_sheet" marrone/#1e1e1e
        // ancora presente dopo il fix header. Confermato via dumpsys activity --views dal vivo:
        // non esiste alcuna view con "bottom"/"sheet" nel nome — il vero elemento è
        // "panel_layout" (ConstraintLayout, bounds 0,2512-1440,3168, l'ultimo ~1/5 dello schermo),
        // che ospita la gridview con gli swatch colore bordo (nessuno/blu/rosso/arancio).
        // Come per "Colori", l'utente vuole bordo+navy qui.
        tintNavyWithAccentBorder(activity, "panel_layout", 40);
        // Sfondo pagina nero segnalato dall'utente 2026-09-04 su Wallpapers/uxdesign nonostante
        // android:id/content sopra sia già tinto giusto — confermato via uiautomator dump dal
        // vivo (Wallpapers, home_list): android:id/content ha un unico figlio che ospita sia
        // l'header sia "list_layout" (contenitore attorno al RecyclerView per il pull-to-refresh,
        // pattern OOS comune), che copre l'intera area sotto l'header con un proprio sfondo opaco
        // — nostro tint su content invisibile perché sotto. "list_layout" è un id generico, non
        // specifico di Wallpapers: innocuo (no-op) sulle app dove non esiste.
        tintViewByIdInPackage(activity, "list_layout");
        // Stessa causa, variante diversa — com.heytap.mydevices ("I miei dispositivi", 2026-09-05):
        // qui non è un contenitore ma un ImageView a schermo intero dedicato allo sfondo pagina,
        // "bg_view" (confermato via uiautomator dump dal vivo, primo figlio dello ScrollView radice,
        // sotto appBarLayout in z-order). setBackgroundColor da solo non basta su un ImageView: il
        // suo drawable "src" (probabilmente nero fisso) resta sopra; va anche svuotato.
        tintImageViewByIdInPackage(activity, "bg_view");
        // 2026-09-13/14: provata la card del dispositivo in "I miei dispositivi" (grigio topo
        // dietro l'immagine prodotto/nome, "topContainer"/"bottomContainer") — navy+bordo accento
        // funzionava per il riempimento ma il bordo rendeva sempre più scuro dell'accento vero
        // (#474DB5 invece di #908DFF, causa non trovata nonostante 2 tentativi: pulizia del
        // foregroundTintList/Mode + riapplicazione ritardata, nessuno ha avuto effetto) — l'utente
        // ha chiesto di lasciar perdere. Non re-indagare senza che l'utente lo richieda di nuovo.
        // com.oplus.wallpapers ("Icone"/"Altro" dentro "Sfondi e stile", 2026-09-07): stessa causa,
        // quarta variante — qui il figlio pieno-schermo si chiama "personalRoot" (PersonalActivity),
        // confermato via uiautomator dump dal vivo, diretto figlio di android:id/content.
        tintViewByIdInPackage(activity, "personalRoot");
        // Stessa schermata, causa REALE del nero però non è personalRoot (quello si tinge già
        // giusto, confermato via dumpsys activity --views dal vivo): sopra c'è "wallpaperBackground",
        // una VIEW CUSTOM (com.oplus.wallpapers.personal.views.MyThemeWallpaperBackground) che
        // disegna il proprio contenuto a schermo intero via Canvas — un setBackgroundColor() lì
        // non serve a niente (una view custom con onDraw() proprio ignora il Drawable di sfondo).
        // Nasconderla lascia vedere il tint corretto di personalRoot sotto.
        hideViewByIdInPackage(activity, "wallpaperBackground");
        // Stessa schermata, sezione più in basso (le 4 card Sfondi/Icone/Carattere/Altro) —
        // segnalato dall'utente 2026-09-07 ("quello sotto ai pulsanti è rimasto nero") dopo il
        // fix di wallpaperBackground sopra. Contenitore vero (non decorativo, ospita i pulsanti
        // reali): "settingsListLayout", confermato via dumpsys activity --views dal vivo.
        tintViewByIdInPackage(activity, "settingsListLayout");
        // Vero motivo del nero rimasto sotto ai pulsanti (segnalato dall'utente 2026-09-07,
        // "il nero sembra a parte, scorrendo si vede uno sfondo tipo sheet"): "settingsListLayout"
        // sopra è solo UNA sezione più in basso — le 4 card Sfondi/Icone/Carattere/Altro vivono
        // in un ramo FRATELLO completamente separato, "drawerLayout"
        // (InspirationThemeDrawerLayout), un vero pannello/drawer sovrapposto a tutto schermo
        // con un proprio sfondo — confermato via dumpsys activity --views dal vivo (due rami,
        // stesso genitore, bounds identici 0,0-1440,3168, drawerLayout è quello con le card
        // dentro). "scroll_view" (il suo scroll interno) provato anche lui, non fa danno se
        // drawerLayout stesso non ha un suo sfondo proprio.
        tintViewByIdInPackage(activity, "drawerLayout");
        tintViewByIdInPackage(activity, "scroll_view");
        // Le 5 card dentro "drawerLayout" sopra (Sfondi/Schermo sempre acceso/Icone/Carattere/
        // Altro) sembravano navy a schermo ma il colore reale letto col color picker
        // dell'utente è #262626 (grigio scuro stock, non il nostro navy) — segnalato 2026-09-07
        // ("mail colore non è navy, è #262626"). Id reali confermati via dumpsys activity
        // --views dal vivo: wallpaperCardView/aodCardView/iconCardView/fontCardView/
        // moreCardView (ognuna una classe custom diversa — WallpaperCardView/AODCardView/
        // LauncherIconCardView/AutoSizeCardView×2 — accomunate solo dall'essere tutte View,
        // niente init/attr condiviso da hookare). Bordo accento richiesto in coerenza con
        // Mind Space/Colori/Illuminazione bordi (stesso stile). "iconCardView" secondo
        // l'utente potrebbe avere un blur dietro (letture pixolor incoerenti) — setBackground
        // qui rimpiazza comunque il Drawable originale, ma se è un vero blur via RenderEffect
        // (non un semplice Drawable) potrebbe non bastare: verificare dal vivo dopo il build.
        for (String cardId : new String[]{"wallpaperCardView", "aodCardView", "iconCardView",
                "fontCardView", "moreCardView"}) {
            tintViewByIdInPackage(activity, cardId);
            try {
                int id = activity.getResources().getIdentifier(cardId, "id", activity.getPackageName());
                View card = (id != 0) ? activity.findViewById(id) : null;
                if (card == null) continue;
                applyAccentBorder(card);
                // aod/icon/more restavano #262626 anche dopo il tint sopra — trovato il vero
                // motivo via dumpsys activity --views dal vivo: ogni card esterna ha UN SOLO
                // figlio, un RoundedConstraintLayout senza id stabile (id generati a runtime,
                // "#7"/"#d" ecc — non risolvibili per nome), che È la vera superficie visibile
                // e ha il proprio sfondo separato. La card esterna non disegna nulla di suo,
                // quindi tingerla non aveva alcun effetto. wallpaperCardView (anteprima
                // immagine, richiesta dall'utente di lasciarla) e fontCardView (già corretto
                // sull'esterno) restano esclusi.
                boolean needsInnerFix = "aodCardView".equals(cardId) || "iconCardView".equals(cardId)
                        || "moreCardView".equals(cardId);
                if (needsInnerFix && card instanceof android.view.ViewGroup cardGroup
                        && cardGroup.getChildCount() > 0) {
                    cardGroup.getChildAt(0).setBackgroundColor(mCardColor);
                }
            } catch (Throwable ignored) {}
        }
        // com.oplus.uxdesign ("Icone" dentro "Sfondi e stile" — UxIconStyleActivity, un pacchetto
        // ANCORA diverso da wallpaperе sopra, 2026-09-07): stesso schema, contenitore radice
        // "root_view" a schermo intero, confermato via dumpsys activity --views dal vivo. L'id si
        // ripete più volte più in basso nell'albero (righe icona più piccole dentro una
        // RecyclerView) ma findViewById() trova sempre quello radice per primo (attraversamento
        // in profondità dalla cima), quindi resta sicuro.
        tintViewByIdInPackage(activity, "root_view");
        // com.oplus.uxdesign ("Colori"/"Illuminazione bordi", 2026-09-07): il pannello inferiore
        // "Colori in primo piano"/"Colori sfondo" ha un id proprio, non coperto dal fallback
        // strutturale sopra (content ha PIÙ figli qui, non uno solo) — confermato via dumpsys
        // dal vivo, colore reale letto col color picker dell'utente (#1e1e1e, non il nostro
        // navy). Bordo + navy esplicitamente richiesti dall'utente, non solo il colore piatto.
        tintNavyWithAccentBorder(activity, "uxcolor_setting_bottom_layout", 40);
        // com.oplus.wallpapers ("Altro" dentro "Sfondi e stile" — PersonalMoreActivity, 2026-09-07):
        // stessa causa, contenitore radice diverso da "personalRoot" (quello è solo
        // PersonalActivity) — confermato via dumpsys activity --views dal vivo.
        tintViewByIdInPackage(activity, "personal_more_root");
        // com.oneplus.account ("Account", 2026-09-05): "list_container" esiste anche qui, ma nel
        // namespace del PACCHETTO (non "android" come per com.android.phone sotto) — id generico,
        // innocuo altrove.
        tintViewByIdInPackage(activity, "list_container");
        // Stessa causa, terza variante — com.android.phone ("Rete mobile", 2026-09-05): qui il
        // contenitore si chiama "list_container" MA nel namespace "android" (framework), non
        // in quello del pacchetto — confermato via uiautomator dump dal vivo
        // (resource-id="android:id/list_container"). tintViewByIdInPackage cerca sempre nel
        // namespace del pacchetto corrente: serve una variante che cerchi in "android".
        try {
            int id = activity.getResources().getIdentifier("list_container", "id", "android");
            View v = (id != 0) ? activity.findViewById(id) : null;
            if (v != null) v.setBackgroundColor(mCardColor);
        } catch (Throwable ignored) {}
        // Card SIM trasparenti con bordo accento — richiesta esplicita dell'utente 2026-09-05,
        // solo per com.android.phone (id specifici di questa schermata, non generici come sopra).
        if (PHONE.equals(activity.getPackageName())) {
            tintTransparentWithAccentBorder(activity, "sim_info_slot_1");
            tintTransparentWithAccentBorder(activity, "sim_info_slot_2");
        }
        // Card griglia "OnePlus AI" (hub raggiunto da Impostazioni, com.ums.aisetting.
        // GridPreference dentro UMS.apk) — bordo accento richiesto dall'utente 2026-09-05,
        // sfondo gia' risolto separatamente in installPantanalAiCardHook (vedi sotto per il perche').
        // 2026-09-06: la assunzione iniziale (rendering DENTRO l'Activity di Settings) era
        // sbagliata — confermato dal vivo con piu' giri di "dumpsys window | mCurrentFocus":
        // l'hub "OnePlus AI" e' una vera Activity PROPRIA di com.oplus.pantanal.ums
        // (com.ums.aisetting.AISettingActivity), processo :uiSetting separato, non un fragment
        // ospitato da Settings — per questo il bordo non compariva mai (il filtro sotto non
        // scattava mai). Entrambi i controlli per sicurezza, innocuo altrove.
        if (SETTINGS.equals(activity.getPackageName()) || PANTANAL_UMS.equals(activity.getPackageName())) {
            tintPantanalAiCardBorders(activity);
        }
    }

    /** Bordo accento sulle card "OnePlus AI" — COUICardView (copia compilata dentro UMS.apk) non
     *  espone alcuna API di stroke (verificato via decompile reale, nessun setStrokeColor/
     *  setStrokeWidth in questa build), quindi niente equivalente diretto di
     *  tintTransparentWithAccentBorder. View.setForeground() e' API di framework (View stessa,
     *  non COUICardView) e funziona su QUALSIASI View a prescindere dal classloader che ha
     *  creato la sua sottoclasse concreta — nessuna reflection necessaria per chiamarla. Fino a
     *  4 card nella griglia, tutte con lo STESSO id "super_item_layout": serve una ricerca
     *  ricorsiva su tutto l'albero del decor (findViewById trova solo la prima), stesso principio
     *  gia' usato in DstDialogStyle.clearBackgroundsByIdNameRecursive per lo stesso motivo. */
    private void tintPantanalAiCardBorders(Activity activity) {
        try {
            int id = activity.getResources().getIdentifier("super_item_layout", "id", PANTANAL_UMS);
            if (id == 0) return;
            View decor = activity.getWindow().getDecorView();
            List<View> cards = new java.util.ArrayList<>();
            findAllViewsById(decor, id, cards);
            for (View v : cards) applyAccentBorder(v);
        } catch (Throwable ignored) {}
        // 2026-09-06: bordo esteso anche a COUICardListSelectedItemLayout (righe "Copywriting AI"/
        // "Trascrizione vocale AI" e i box sotto) provato su richiesta esplicita dell'utente
        // ("prova, vediamo che succede") — risultato bocciato ("mica tanto buono, ci sono solo
        // tante righe": un bordo per OGNI singola card individuale, non una distinzione per
        // gruppo, troppo affollato). Rimosso di nuovo, solo Mind Space (super_item_layout) resta
        // bordato. Il meccanismo per farlo (mCardListLayoutCls + type-check sull'albero, dato che
        // l'unico overload noto di init() lancia NoSuchMethodError per questa copia della
        // libreria COUI) resta comunque valido se in futuro si vuole riprovare diversamente.
    }

    /** Sfondo trasparente + bordo accento applicato come foreground (disegnato sopra, non tocca
     *  la gestione interna sfondo/elevazione/clip della card) — stesso stile ovunque nel file,
     *  centralizzato qui perche' ora usato da due punti diversi (COUICardView "super_item_layout"
     *  E COUICardListSelectedItemLayout). Il raggio prova a leggere getCardRoundCornerRadius() per
     *  riflessione (presente su COUICardView, non su COUICardListSelectedItemLayout — fallback
     *  silenzioso a 16dp in quel caso). */
    private void applyAccentBorder(View v) {
        try {
            float radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16,
                    v.getResources().getDisplayMetrics());
            try {
                Object r = callMethod(v, "getCardRoundCornerRadius");
                if (r instanceof Float && (Float) r > 0f) radius = (Float) r;
            } catch (Throwable ignoredRadius) {}
            float stroke = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.5f,
                    v.getResources().getDisplayMetrics());
            android.graphics.drawable.GradientDrawable border = new android.graphics.drawable.GradientDrawable();
            border.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            border.setColor(0x00000000);
            border.setCornerRadius(radius);
            border.setStroke((int) stroke, mAccentColor);
            v.setForeground(border);
        } catch (Throwable ignored) {}
    }

    private void findAllViewsById(View root, int targetId, List<View> out) {
        if (root == null) return;
        if (root.getId() == targetId) out.add(root);
        if (root instanceof android.view.ViewGroup vg) {
            for (int i = 0; i < vg.getChildCount(); i++) findAllViewsById(vg.getChildAt(i), targetId, out);
        }
    }

    /** Sfondo trasparente + bordo accento (stesso stile del menu overflow, vedi
     *  tintPopupWrapper) su una view trovata per id nel namespace del pacchetto corrente. */
    private void tintTransparentWithAccentBorder(Activity activity, String idName) {
        try {
            int id = activity.getResources().getIdentifier(idName, "id", activity.getPackageName());
            View v = (id != 0) ? activity.findViewById(id) : null;
            if (v == null) return;
            float radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16,
                    activity.getResources().getDisplayMetrics());
            float stroke = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.5f,
                    activity.getResources().getDisplayMetrics());
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            bg.setColor(0x00000000);
            bg.setCornerRadius(radius);
            bg.setStroke((int) stroke, mAccentColor);
            v.setBackground(bg);
        } catch (Throwable ignored) {}
    }

    /** Come tintTransparentWithAccentBorder ma con riempimento navy (mCardColor) invece di
     *  trasparente — richiesto dall'utente 2026-09-07 per il pannello inferiore di com.oplus.
     *  uxdesign ("Colori"/"Illuminazione bordi": "bordo e navy", non solo bordo). Stesso stile
     *  di tintPopupWrapper (menu overflow), applicato per id invece che su un campo riflesso. */
    private void tintNavyWithAccentBorder(Activity activity, String idName) {
        tintNavyWithAccentBorder(activity, idName, 16);
    }

    /** Variante con raggio angoli personalizzabile — questi pannelli toccano il bordo fisico
     *  inferiore dello schermo (raggio reale ~40dp, letto da mRoundedCorners dal vivo), un raggio
     *  più stretto (16dp) fa "mangiare" il bordo dall'angolo fisico più arrotondato. Segnalato
     *  dall'utente 2026-09-07.
     *  Bordo nel FOREGROUND, non nel background: diagnosticato dal vivo (log ripetuti a ogni
     *  passata di layout) che il primo figlio di questi pannelli (GridView colori bordo su
     *  "Illuminazione bordi", striscia tab su "Colori") si riassegna da solo un proprio
     *  ColorDrawable scuro ad OGNI layout, vincendo sempre la corsa contro un setBackground() sul
     *  genitore — bordo invisibile, "mangiato" dal figlio. Il foreground invece disegna SOPRA i
     *  figli sempre, indipendentemente da questa corsa. Lo sfondo navy resta best-effort (si vede
     *  dove il figlio non copre, es. angoli). */
    private void tintNavyWithAccentBorder(Activity activity, String idName, float radiusDp) {
        try {
            int id = activity.getResources().getIdentifier(idName, "id", activity.getPackageName());
            View v = (id != 0) ? activity.findViewById(id) : null;
            if (v == null) return;
            float radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, radiusDp,
                    activity.getResources().getDisplayMetrics());
            float stroke = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.5f,
                    activity.getResources().getDisplayMetrics());
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            bg.setColor(mCardColor);
            bg.setCornerRadius(radius);
            v.setBackground(bg);
            android.graphics.drawable.GradientDrawable border = new android.graphics.drawable.GradientDrawable();
            border.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            border.setColor(0x00000000);
            border.setCornerRadius(radius);
            border.setStroke((int) stroke, mAccentColor);
            v.setForeground(border);
            if (v instanceof android.view.ViewGroup vg && vg.getChildCount() > 0) {
                vg.getChildAt(0).setBackgroundColor(0x00000000);
            }
        } catch (Throwable ignored) {}
    }

    private void tintImageViewByIdInPackage(Activity activity, String idName) {
        try {
            int id = activity.getResources().getIdentifier(idName, "id", activity.getPackageName());
            View v = (id != 0) ? activity.findViewById(id) : null;
            if (v instanceof android.widget.ImageView iv) {
                iv.setImageDrawable(null);
                iv.setBackgroundColor(mCardColor);
            }
        } catch (Throwable ignored) {}
    }

    /** Per view CUSTOM che disegnano il proprio contenuto via onDraw() (non un semplice
     *  Drawable di sfondo) — setBackgroundColor() non ha alcun effetto su queste, l'unico modo
     *  di far vedere il nostro tint sotto è nasconderle del tutto. */
    private void hideViewByIdInPackage(Activity activity, String idName) {
        try {
            int id = activity.getResources().getIdentifier(idName, "id", activity.getPackageName());
            View v = (id != 0) ? activity.findViewById(id) : null;
            if (v != null) v.setVisibility(View.GONE);
        } catch (Throwable ignored) {}
    }

    /** Menu overflow "⋮" (COUIPopupListWindow, classe COUI condivisa dalla maggior parte delle
     *  app di sistema — trovata via decompile reale di WirelessSettings.apk) — richiesto
     *  dall'utente 2026-09-05 per "I miei dispositivi". Il contenuto (mMainMenuWrapper/
     *  mSubMenuWrapper, entrambi RoundFrameLayout) prende il proprio sfondo dentro il metodo
     *  privato createContentView(), letto una sola volta da un attr tema — hook afterHooked per
     *  sostituirlo con uno sfondo OBS (mCardColor) + bordo accento, stesso stile dei dialoghi
     *  in-app (ObsidianTheme.dialogBackground). Nomi campo non offuscati nella build verificata;
     *  se un'altra build li offusca il try/catch fallisce silenziosamente, nessun crash. */
    private void installPopupMenuBorder(XC_LoadPackage.LoadPackageParam lp) {
        try {
            Class<?> popupCls = findClass("com.coui.appcompat.poplist.COUIPopupListWindow", lp.classLoader);
            findAndHookMethod(popupCls, "createContentView", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!mThemeApplied || !isNight()) return;
                    try {
                        tintPopupWrapper(p.thisObject, "mMainMenuWrapper");
                        tintPopupWrapper(p.thisObject, "mSubMenuWrapper");
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            // 2026-09-13: MyDevices confermato via jadx reale (MyDevices.apk) — la classe ESISTE,
            // solo rinominata "com.coui.appcompat.poplist.a" (R8, stessa famiglia offuscata di
            // Wallpapers/Battery/Pantanal UMS) invece di essere assente come si pensava il 2026-09-05.
            // Il nome del metodo/dei campi (mMainMenuWrapper/mSubMenuWrapper) è offuscato anche lui
            // nella classe base "a" — invece di inseguirlo, agganciato PopupWindow.setContentView()
            // (API di framework, mai offuscata) e tinta ogni RoundFrameLayout trovato nell'albero
            // della content view per TIPO, non per nome campo — com.coui.appcompat.poplist.
            // RoundFrameLayout resta un nome di classe reale/non offuscato anche in questa build
            // (confermato), quindi è un aggancio robusto indipendentemente da come si chiamano i
            // campi che lo referenziano.
            installPopupMenuBorderObfuscatedFallback(lp);
        }
    }

    /** 2026-09-13: il primo tentativo agganciava PopupWindow.setContentView() e cercava un
     *  RoundFrameLayout nell'albero — log dal vivo (aa.Q7, la vera content view di questa build,
     *  confermata via jadx come FrameLayout con un campo "g" controllato "instanceof
     *  RoundFrameLayout" più sotto nello stesso file) ha mostrato SEMPRE "tinted=0": il wrapper
     *  viene aggiunto come figlio DOPO che setContentView() ritorna, non dentro — niente da
     *  trovare in quel momento. Fix più diretto: agganciare il COSTRUTTORE di RoundFrameLayout
     *  stesso (classe reale, non offuscata anche in questa build) — non serve più sapere QUANDO
     *  o DOVE viene inserito nell'albero, il colore/bordo si applica nell'istante stesso in cui
     *  l'oggetto nasce. */
    private void installPopupMenuBorderObfuscatedFallback(XC_LoadPackage.LoadPackageParam lp) {
        try {
            final Class<?> roundFrameLayoutCls = findClass(
                    "com.coui.appcompat.poplist.RoundFrameLayout", lp.classLoader);
            XC_MethodHook ctorHook = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!mThemeApplied || !isNight()) return;
                    try {
                        if (p.thisObject instanceof View v) applyPopupWrapperBg(v);
                    } catch (Throwable ignored) {}
                }
            };
            hookAllConstructors(roundFrameLayoutCls, ctorHook);
            // 2026-09-14: confermato via log che il costruttore SCATTA correttamente (mThemeApplied/
            // isNight entrambi true) — eppure l'utente vedeva ancora stock. Stesso identico pattern
            // già risolto altrove in questo file (gridview/uxcolor_setting_tab_layout, 2026-09-07):
            // qualcosa dopo la costruzione ridipinge lo sfondo stock ad ogni layout pass, vincendo
            // sempre la corsa contro un setBackground() fatto una volta sola al costruttore.
            // Aggancio globale su View.setBackground*, filtrato per TIPO (instanceof
            // RoundFrameLayout, non per id/nome campo) — ogni tentativo successivo di ridipingerlo
            // viene sostituito con il nostro sfondo invece che bloccato a trasparente (qui non c'è
            // un genitore già navy sotto come nel caso gridview, serve un riempimento vero).
            // Guardia di rientranza: applyPopupWrapperBg() chiama v.setBackground(), che è UNO
            // dei metodi agganciati qui sotto — senza questa guardia si richiamerebbe da solo
            // all'infinito. ThreadLocal perché il layout/draw può girare su thread diversi.
            final ThreadLocal<Boolean> inOwnApply = ThreadLocal.withInitial(() -> false);
            XC_MethodHook blockHook = new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!mThemeApplied || !isNight() || inOwnApply.get()) return;
                    try {
                        if (!(p.thisObject instanceof View v) || !roundFrameLayoutCls.isInstance(v)) return;
                        p.setResult(null); // salta la chiamata originale...
                        inOwnApply.set(true);
                        try { applyPopupWrapperBg(v); } finally { inOwnApply.set(false); } // ...e mette la nostra al suo posto
                    } catch (Throwable ignored) {}
                }
            };
            findAndHookMethod(View.class, "setBackground", android.graphics.drawable.Drawable.class, blockHook);
            findAndHookMethod(View.class, "setBackgroundDrawable", android.graphics.drawable.Drawable.class, blockHook);
            findAndHookMethod(View.class, "setBackgroundColor", int.class, blockHook);
            findAndHookMethod(View.class, "setBackgroundResource", int.class, blockHook);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] SettingsCardBackgroundMod: installPopupMenuBorderObfuscatedFallback failed: " + t);
        }
    }

    private void applyPopupWrapperBg(View v) {
        float radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20,
                mContext.getResources().getDisplayMetrics());
        float stroke = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.5f,
                mContext.getResources().getDisplayMetrics());
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setColor(mCardColor);
        bg.setCornerRadius(radius);
        bg.setStroke((int) stroke, mAccentColor);
        v.setBackground(bg);
    }

    /** Barra livello batteria (com.oplus.battery, "Batteria") colorata ad accento invece del
     *  verde COUI fisso — richiesta esplicita dell'utente 2026-09-05. Trovata via decompile
     *  reale (BatteryLevelView.java): il colore viene letto ogni volta nel COSTRUTTORE con
     *  getResources().getColor(R.color.coui_color_primary_green) (overload deprecato a un
     *  argomento, niente Theme) — nessuna cache statica con guardia, si rilegge a ogni nuova
     *  istanza, quindi un hook sul resource id specifico basta senza corse di timing. */
    private void installBatteryBarAccent(XC_LoadPackage.LoadPackageParam lp) {
        try {
            final int greenId = mContext.getResources().getIdentifier(
                    "coui_color_primary_green", "color", lp.packageName);
            final int greenDarkId = mContext.getResources().getIdentifier(
                    "coui_color_primary_green_dark", "color", lp.packageName);
            if (greenId == 0 && greenDarkId == 0) return;
            XC_MethodHook hook = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!mThemeApplied || !isNight()) return;
                    try {
                        if (p.args.length < 1 || !(p.args[0] instanceof Integer)) return;
                        int id = (Integer) p.args[0];
                        if (id == greenId || id == greenDarkId) p.setResult(mAccentColor);
                    } catch (Throwable ignored) {}
                }
            };
            // Due overload: quello deprecato a un argomento (usato qui) e quello con Theme —
            // agganciare entrambi non fa danno, copre eventuali altre build/versioni Android.
            try {
                findAndHookMethod(android.content.res.Resources.class, "getColor", int.class, hook);
            } catch (Throwable ignored) {}
            try {
                findAndHookMethod(android.content.res.Resources.class, "getColor",
                        int.class, android.content.res.Resources.Theme.class, hook);
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] SettingsCardBackgroundMod: installBatteryBarAccent failed: " + t);
        }
    }

    /** Sfondo delle card "OnePlus AI" (vedi tintPantanalAiCardBorders per il bordo) — decompile
     *  reale di UMS.apk (com.ums.aisetting.GridPreference.U()): COUICardView.setCardBackgroundColor
     *  riceve nv.a.a(int), che chiama semplicemente getResources().getColor(R.color.
     *  super_item_background_0/1/2) del PROPRIO pacchetto (com.oplus.pantanal.ums) — MAI un attr
     *  COUI, quindi nessuno degli hook attr-based sopra lo tocca, e il colore resta fisso #1A1A1A
     *  indipendentemente da DST_BACKGROUND (segnalato dall'utente 2026-09-05 col proprio color
     *  picker dal vivo, dopo una prima falsa rassicurazione di Claude basata su uno screenshot
     *  compresso — non dare per corretto senza verifica reale). Il rendering avviene DENTRO il
     *  processo di Settings (vedi commento su tintPantanalAiCardBorders), ma l'id risorsa va
     *  risolto nel namespace di com.oplus.pantanal.ums, non di Settings — stesso principio già
     *  usato per gli attr COUI di ogni singola app in questo file. Hook su Resources.getColor
     *  (classe di framework, un solo oggetto per processo) invece che sulla classe nv.a: quella
     *  vive nel dex di UMS caricato dinamicamente dentro Settings, classloader diverso da quello
     *  visto da handleLoadPackage per com.android.settings — stesso identico principio già usato
     *  in installBatteryBarAccent per lo stesso motivo (build offuscata/dex separato).
     */
    private void installPantanalAiCardHook(XC_LoadPackage.LoadPackageParam lp) {
        try {
            final int bg0 = mContext.getResources().getIdentifier(
                    "super_item_background_0", "color", PANTANAL_UMS);
            final int bg1 = mContext.getResources().getIdentifier(
                    "super_item_background_1", "color", PANTANAL_UMS);
            final int bg2 = mContext.getResources().getIdentifier(
                    "super_item_background_2", "color", PANTANAL_UMS);
            if (bg0 == 0 && bg1 == 0 && bg2 == 0) return;
            XC_MethodHook hook = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!mThemeApplied || !isNight()) return;
                    try {
                        if (p.args.length < 1 || !(p.args[0] instanceof Integer)) return;
                        int id = (Integer) p.args[0];
                        if (id == bg0 || id == bg1 || id == bg2) p.setResult(mCardColor);
                    } catch (Throwable ignored) {}
                }
            };
            try {
                findAndHookMethod(android.content.res.Resources.class, "getColor", int.class, hook);
            } catch (Throwable ignored) {}
            try {
                findAndHookMethod(android.content.res.Resources.class, "getColor",
                        int.class, android.content.res.Resources.Theme.class, hook);
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] SettingsCardBackgroundMod: installPantanalAiCardHook failed: " + t);
        }
    }

    /** Finestra flottante post-screenshot (com.oplus.screenshot, 2026-09-07: "Salva"/"Scorri"/
     *  "Condividi" sotto l'anteprima) — richiesta dall'utente ("si possono fare navy con
     *  bordo?"). Layout reale trovato via decompile jadx di Screenshot.apk
     *  (res/layout/capture_screen_layout.xml): i 3 pulsanti sono istanze di
     *  com.oplus.screenshot.ui.drag.FloatButton (estende AppCompatButton, nessun onDraw custom —
     *  a differenza dei casi "stubborn" di sopra, uno sfondo qui dovrebbe reggere senza corse),
     *  stile condiviso "float_btn" con android:background="@drawable/float_btn_bg_os_13" (forma a
     *  pillola statica, non un attr tema). Hook sul costruttore (non un'Activity, è una finestra
     *  flottante aggiunta da un Service — il pattern "Activity.onResume" usato altrove non si
     *  applica) — sostituisce lo sfondo con lo stesso navy+bordo pillola usato altrove, raggio
     *  angoli enorme (999dp) per restare arrotondato a qualunque altezza reale del pulsante. */
    private void installScreenshotFloatButtonHook(XC_LoadPackage.LoadPackageParam lp) {
        try {
            findAndHookConstructor("com.oplus.screenshot.ui.drag.FloatButton", lp.classLoader,
                    Context.class, android.util.AttributeSet.class, int.class, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            if (!(p.thisObject instanceof View v)) return;
                            mFloatButtons.add(new WeakReference<>(v));
                            if (!mThemeApplied || !isNight()) return;
                            tintFloatButton(v);
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] SettingsCardBackgroundMod: installScreenshotFloatButtonHook failed: " + t);
        }
        // Bordo del riquadro anteprima stesso (id "float_preview") — richiesto dall'utente
        // 2026-09-07 ("puoi fare anche il bordo del preview? adesso è grigio, lo vorrei
        // accento"). Diverso dai pulsanti sopra: FloatPreviewWidget disegna il proprio bordo
        // via Canvas in onDraw() (Paint dedicato "borderPaint", non un semplice background) —
        // stesso identico limite di AodSurfaceAnimationView/RoundedConstraintLayout incontrato
        // oggi. QUI però esiste un vero setter pubblico pensato apposta per questo,
        // setBorderColor(int) (aggiorna sia il campo che il Paint), trovato leggendo il
        // sorgente decompilato — nessun bisogno di toccare onDraw/Canvas.
        try {
            findAndHookConstructor("com.oplus.screenshot.ui.widget.floating.FloatPreviewWidget", lp.classLoader,
                    Context.class, android.util.AttributeSet.class, int.class, int.class, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            mFloatPreviewWidgets.add(new WeakReference<>(p.thisObject));
                            if (!mThemeApplied || !isNight()) return;
                            tintFloatPreviewWidget(p.thisObject);
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] SettingsCardBackgroundMod: FloatPreviewWidget border hook failed: " + t);
        }
    }

    /** Alcuni widget OEM dentro i pannelli inferiori "bordo+navy" (GridView "gridview" su
     *  Illuminazione bordi/AodSurfaceHomeActivity, COUITabLayout "uxcolor_setting_tab_layout" su
     *  Colori/UxColorSettingActivity) si riassegnano da soli un proprio sfondo scuro ad OGNI
     *  passata di layout — confermato dal vivo via log ripetuti (stesso ColorDrawable riapplicato
     *  a ripetizione), vincendo sempre la corsa contro un setBackground() reattivo lato nostro
     *  (vedi tintNavyWithAccentBorder, che infatti ora usa il foreground per il bordo proprio per
     *  questo). Blocco le loro chiamate a setBackground* alla fonte (hook framework su View, una
     *  sola installazione per processo, come installPantanalAiCardHook) così il fill navy del
     *  genitore torna visibile invece di restare sempre coperto — confermato per "gridview"
     *  (Colori: titolo/tab ora navy) e "uxcolor_setting_tab_layout" (idem). LIMITE NOTO: le 4
     *  card colore dentro "gridview" su Illuminazione bordi (AodSurfaceAnimationView) restano
     *  scure — non chiamano mai setBackground*, disegnano il proprio sfondo via Canvas/onDraw
     *  (confermato dal vivo, zero hit sull'hook), stesso limite dei toast/dialoghi con draw
     *  custom altrove nel file. Bordo e angoli restano comunque corretti (foreground). */
    private void installStubbornBackgroundBlockHook(XC_LoadPackage.LoadPackageParam lp) {
        try {
            XC_MethodHook hook = new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!mThemeApplied || !isNight()) return;
                    try {
                        if (!(p.thisObject instanceof View v)) return;
                        if (!shouldBlockOwnBackground(v)) return;
                        String method = p.method.getName();
                        // Le 3 home card (aod/icon/more) sono la superficie visibile finale,
                        // non un contenitore con un genitore già navy sotto — a differenza di
                        // gridview/uxcolor_setting_tab_layout qui serve sostituire col nostro
                        // navy vero, non solo trasparente (altrimenti resta grigio scuro
                        // stock/#262626, confermato dal vivo col color picker dell'utente).
                        boolean solidReplace = isSolidCardId(v);
                        if ("setBackgroundColor".equals(method) || "setBackgroundResource".equals(method)) {
                            p.args[0] = solidReplace ? mCardColor : 0;
                        } else {
                            p.args[0] = solidReplace
                                    ? new android.graphics.drawable.ColorDrawable(mCardColor) : null;
                        }
                    } catch (Throwable ignored) {}
                }
            };
            findAndHookMethod(View.class, "setBackground", android.graphics.drawable.Drawable.class, hook);
            findAndHookMethod(View.class, "setBackgroundDrawable", android.graphics.drawable.Drawable.class, hook);
            findAndHookMethod(View.class, "setBackgroundColor", int.class, hook);
            findAndHookMethod(View.class, "setBackgroundResource", int.class, hook);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] SettingsCardBackgroundMod: installStubbornBackgroundBlockHook failed: " + t);
        }
    }

    private boolean shouldBlockOwnBackground(View v) {
        try {
            int id = v.getId();
            if (id == 0 || id == View.NO_ID) return false;
            String name = v.getResources().getResourceEntryName(id);
            // aodCardView/iconCardView/moreCardView (hub "Sfondi e stile", 2026-09-07): stesso
            // bug, si riassegnano da sole #262626 ad ogni layout — segnalato dall'utente via
            // color picker. Trasparente (come per gridview/uxcolor_setting_tab_layout, dove il
            // genitore sotto è già navy) NON basta qui: confermato dal vivo che resta grigio
            // (vedi isSolidCardId, sostituisce col navy vero invece di svuotare).
            // wallpaperCardView/fontCardView non sono in questa lista: il loro
            // setBackgroundColor(mCardColor) diretto già funziona (color picker: #1B2029 giusto).
            return "gridview".equals(name) || "uxcolor_setting_tab_layout".equals(name)
                    || "aodCardView".equals(name) || "iconCardView".equals(name)
                    || "moreCardView".equals(name);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isSolidCardId(View v) {
        try {
            String name = v.getResources().getResourceEntryName(v.getId());
            return "aodCardView".equals(name) || "iconCardView".equals(name)
                    || "moreCardView".equals(name);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void tintPopupWrapper(Object popupWindowInstance, String fieldName) {
        try {
            Object wrapper = de.robv.android.xposed.XposedHelpers.getObjectField(popupWindowInstance, fieldName);
            if (!(wrapper instanceof View)) return;
            View v = (View) wrapper;
            float radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20,
                    mContext.getResources().getDisplayMetrics());
            float stroke = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.5f,
                    mContext.getResources().getDisplayMetrics());
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            bg.setColor(mCardColor);
            bg.setCornerRadius(radius);
            bg.setStroke((int) stroke, mAccentColor);
            v.setBackground(bg);
        } catch (Throwable ignored) {}
    }

    /** com.heytap.mydevices ("I miei dispositivi", 2026-09-13): naviga la RecyclerView
     *  "home_device_list" ed applica navy piatto + bordo accento ad OGNI card dispositivo
     *  trovata — non un semplice findViewById(topContainer)/(bottomContainer), che prenderebbe
     *  solo il primo item anche con più dispositivi elencati. */
    private void tintViewByIdInPackage(Activity activity, String idName) {
        try {
            int id = activity.getResources().getIdentifier(idName, "id", activity.getPackageName());
            View v = (id != 0) ? activity.findViewById(id) : null;
            if (v == null) return;
            // 2026-09-05: provato disattivare "force dark" (setForceDarkAllowed(false)) —
            // confermato che il meccanismo ERA coinvolto (il rendering è cambiato, da nero a
            // BIANCO: lo stile reale/non invertito di questo toolbar è chiaro) ma il nostro
            // colore continua a non restare in NESSUNO dei due casi, e disabilitarlo fa anche
            // sparire il testo (che si affidava all'inversione per restare leggibile) — una
            // regressione netta. Rollback: qualcos'altro, ancora non identificato, ignora
            // setBackgroundColor() su questa view indipendentemente da force-dark. Vedi
            // [[project_settings_theme_header_gap]] — parcheggiato di nuovo, stesso esito di ieri.
            v.setBackgroundColor(mCardColor);
            // 2026-09-05: provato anche un OnPreDrawListener persistente (riapplica il colore ad
            // ogni frame, invece di un retry a tempo fisso) per "toolbar" — nessun effetto: il
            // readback confermava il colore Java-level già corretto in ogni istante, quindi non è
            // "qualcosa lo resetta più tardi" (un predraw listener risolverebbe quello), è la
            // RESA A SCHERMO stessa che ignora il valore. Rimosso, inutile overhead per niente.
        } catch (Throwable ignored) {}
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
            } catch (Throwable t) {
                // Build offuscate (Wallpapers/Battery/Pantanal UMS, 2026-09-04): refreshCardBg
                // non esiste in questa versione della libreria COUI — il colore arriva comunque
                // giusto dal hook universale su Theme.resolveAttribute qui sotto, serve solo
                // un invalidate() (metodo pubblico di View, mai offuscato) per far ridisegnare.
                try {
                    if (card instanceof View) ((View) card).invalidate();
                } catch (Throwable ignored) {}
            }
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
        // app_bar_layout e' il diretto genitore di collapsingToolbarLayout (trovato via
        // uiautomator dump dal vivo, non "app_bar" — id sbagliato di un tentativo precedente).
        // Durante lo scroll parziale, tra il titolo e la barra ricerca (entrambi figli di
        // collapsingToolbarLayout) si apre uno spazio vuoto che non viene ridipinto dal
        // background di collapsingToolbarLayout stesso — tingerlo NON basta da solo. Tingendo
        // anche il genitore, quello spazio mostra il nostro colore invece del nero della
        // finestra sotto, indipendentemente da come collapsingToolbarLayout dipinge se stesso.
        tintAppBarLayoutSafely(activity);
        tintViewById(activity, "collapsingToolbarLayout");
        tintViewById(activity, "searchView");
        // Sfondo della schermata risultati ricerca globale — id reale trovato dall'utente con
        // uiautomatorviewer (2026-09-03), stock nero puro invece del navy usato ovunque nel resto
        // dell'app. Stesso valore di mCardColor: pagina e card dello stesso colore = card
        // "invisibili" come nel resto dell'app (convenzione già in uso, vedi commento su mCardColor
        // in cima al file). Nella lista di applyHeaderTheme perché il listener sul layout globale
        // già presente in onResume ricontrolla tutto ad ogni apertura/chiusura della ricerca.
        tintViewById(activity, "search_bg_mask");
    }

    /** Bug segnalato dall'utente 2026-09-03: aprire la ricerca globale (stessa Activity, nessun
     *  nuovo onResume) fa restare app_bar_layout alto quanto la vista "espansa" (~632px, confermato
     *  via uiautomator dump dal vivo) invece di restringersi alla sola barra ricerca collassata
     *  (~208px, altezza di searchView). Il nostro tint opaco copriva quindi anche la parte alta
     *  della lista risultati sottostante (drawing-order piu' alto = disegnato sopra) — nascosta
     *  alla vista ma ancora toccabile (app_bar_layout non intercetta i tocchi, e' solo un
     *  LinearLayout senza click handling). Fix: tingere app_bar_layout SOLO quando la sua altezza
     *  corrisponde a quella reale della barra ricerca (fuori dalla ricerca globale); quando e'
     *  piu' alto (ricerca aperta) lo lasciamo trasparente, come nello stock. */
    private void tintAppBarLayoutSafely(Activity activity) {
        try {
            int appBarId = activity.getResources().getIdentifier("app_bar_layout", "id", SETTINGS);
            int searchId = activity.getResources().getIdentifier("searchView", "id", SETTINGS);
            View appBar = (appBarId != 0) ? activity.findViewById(appBarId) : null;
            View search = (searchId != 0) ? activity.findViewById(searchId) : null;
            if (appBar == null) return;
            boolean searchExpanded = search != null && appBar.getHeight() > search.getHeight() + 8;
            appBar.setBackgroundColor(searchExpanded ? 0 : mCardColor);
        } catch (Throwable ignored) {}
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!isCardTargetPackage(lp.packageName)) return;

        installPopupMenuBorder(lp);
        if (BATTERY.equals(lp.packageName)) installBatteryBarAccent(lp);
        if (SETTINGS.equals(lp.packageName) || PANTANAL_UMS.equals(lp.packageName)) {
            installPantanalAiCardHook(lp);
        }
        if (NOTIFICATION_CENTER.equals(lp.packageName) || UX_DESIGN.equals(lp.packageName)
                || WALLPAPERS.equals(lp.packageName)) {
            installStubbornBackgroundBlockHook(lp);
        }
        if (SCREENSHOT.equals(lp.packageName)) {
            installScreenshotFloatButtonHook(lp);
        }

        // L'attr couiColorCardBackground va risolto nel namespace del PACCHETTO CORRENTE, non
        // sempre SETTINGS — ognuna delle 3 app porta la propria copia compilata della libreria
        // COUI nel proprio resources.arsc.
        final int attrId = mContext.getResources().getIdentifier(
                "couiColorCardBackground", "attr", lp.packageName);
        // 2026-09-05: "Gestione app" (com.android.settings) usa un attr GEMELLO diverso per le
        // righe della RecyclerView — couiColorCard/couiColorCardPressed, non
        // couiColorCardBackground — trovato via aapt2 dump reale (card_list_item_body_bg.xml,
        // un selector state_pressed/state_focused/default che referenzia questi due). Stesso
        // colore per entrambi gli stati: nessuna necessità di una sfumatura "premuto" distinta,
        // l'importante è che non resti mai il grigio stock.
        final int attrIdCard = mContext.getResources().getIdentifier(
                "couiColorCard", "attr", lp.packageName);
        final int attrIdCardPressed = mContext.getResources().getIdentifier(
                "couiColorCardPressed", "attr", lp.packageName);
        // 2026-09-13: tentativo per l'header nero di WirelessSettings (Wifi/Bluetooth) — trovato
        // via jadx che COUIToolbar/AppBarLayout leggono ?attr/couiColorBackgroundWithCard nel
        // drawable compilato coui_window_bg_with_card. Aggiunto qui sperando nello stesso
        // meccanismo di couiColorCardBackground — MA un diagnostico dedicato (log su ogni v passato
        // a Theme.resolveAttribute per questo processo) ha confermato che l'attr NON passa MAI da
        // nessuno dei 3 hook qui sotto per questo pacchetto: la risoluzione del colore dentro un
        // drawable XML compilato (non una chiamata Java esplicita come COUIContextUtil.getAttrColor)
        // non passa da queste API. Root cause reale: stesso muro già documentato per header/dialog
        // USB/pannello sfondi in [[project_settings_theme_header_gap]] — il colore Java-level su
        // "toolbar"/"appBarLayout" (tintViewByIdInPackage) è confermato corretto in ogni istante
        // anche con un listener che lo riapplica ogni frame, ma la RESA A SCHERMO lo ignora
        // comunque — il motore di skin OPPO dipinge sopra a un livello più basso di setBackground().
        // Lasciato qui invariato (innocuo, harmless match su un attr specifico) come possibile aiuto
        // per altri pacchetti dove questo stesso attr potesse risolvere diversamente — ma NON
        // aspettarsi che risolva l'header di WirelessSettings/Cast. Non re-indagare senza un
        // approccio radicalmente diverso (serve capire cosa dipinge dopo la View, non nella View).
        final int attrIdBgWithCard = mContext.getResources().getIdentifier(
                "couiColorBackgroundWithCard", "attr", lp.packageName);

        try {
            Class<?> couiContextUtilCls = findClass("com.coui.appcompat.contextutil.COUIContextUtil", lp.classLoader);
            if (attrId != 0 || attrIdCard != 0 || attrIdCardPressed != 0 || attrIdBgWithCard != 0) {
                hookAllMethods(couiContextUtilCls, "getAttrColor", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (!mThemeApplied || !isNight()) return;
                        try {
                            if (p.args.length < 2 || !(p.args[1] instanceof Integer)) return;
                            int v = (Integer) p.args[1];
                            if (v == attrId || v == attrIdCard || v == attrIdCardPressed || v == attrIdBgWithCard) p.setResult(mCardColor);
                        } catch (Throwable ignored) {}
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] SettingsCardBackgroundMod: COUIContextUtil hook failed: " + t);
        }

        // Fallback universale, indipendente da nome classe/metodo: qualunque wrapper "getAttrColor"
        // (offuscato o no) risolve un singolo attr colore passando in ultima analisi da
        // Resources.Theme.resolveAttribute(int, TypedValue, boolean) — API di framework, mai
        // offuscata. Confermato necessario per Wallpapers/Battery/Pantanal UMS (2026-09-04): la
        // loro copia di COUIContextUtil e' rinominata "com.coui.appcompat.contextutil.a", hook per
        // nome-classe sopra fallisce con ClassNotFoundException. Filtro strettissimo (solo il nostro
        // attrId in questo pacchetto) per non toccare risoluzioni di altri attributi.
        if (attrId != 0 || attrIdCard != 0 || attrIdCardPressed != 0 || attrIdBgWithCard != 0) {
            try {
                findAndHookMethod(android.content.res.Resources.Theme.class, "resolveAttribute",
                        int.class, TypedValue.class, boolean.class, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (!mThemeApplied || !isNight()) return;
                        try {
                            int v = (Integer) p.args[0];
                            if (v != attrId && v != attrIdCard && v != attrIdCardPressed && v != attrIdBgWithCard) return;
                            TypedValue tv = (TypedValue) p.args[1];
                            tv.type = TypedValue.TYPE_INT_COLOR_ARGB8;
                            tv.data = mCardColor;
                            p.setResult(true);
                        } catch (Throwable ignored) {}
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] SettingsCardBackgroundMod: Theme.resolveAttribute hook failed: " + t);
            }

            // Il hook sopra NON basta per Wallpapers/Battery/Pantanal UMS: decompile reale
            // (com.coui.appcompat.contextutil.a.b(), 2026-09-04) mostra che la loro build passa
            // da context.getTheme().obtainStyledAttributes(new int[]{attr}).getColor(0, def) — un
            // percorso nativo di TypedArray che NON invoca mai Theme.resolveAttribute a livello
            // Java. Stesso principio "bypassa il wrapper offuscato, aggancia l'API di framework
            // stabile sotto" ma un punto diverso: marchiamo il TypedArray appena creato per il
            // NOSTRO singolo attr (array a un elemento == attrId, idioma standard di tutti questi
            // helper "getAttrColor"), poi forziamo il risultato quando arriva la getColor() su
            // quello stesso oggetto.
            try {
                findAndHookMethod(android.content.res.Resources.Theme.class, "obtainStyledAttributes",
                        int[].class, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        try {
                            int[] attrs = (int[]) p.args[0];
                            if (attrs == null || attrs.length != 1) return;
                            int v = attrs[0];
                            if (v != attrId && v != attrIdCard && v != attrIdCardPressed && v != attrIdBgWithCard) return;
                            Object result = p.getResult();
                            if (result instanceof android.content.res.TypedArray) {
                                mFlaggedArrays.put(result, Boolean.TRUE);
                            }
                        } catch (Throwable ignored) {}
                    }
                });
                findAndHookMethod(android.content.res.TypedArray.class, "getColor",
                        int.class, int.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            // Consuma il flag SUBITO, una volta sola: Android RICICLA le istanze
                            // TypedArray (un pool interno, non garbage-collected finché vive nel
                            // pool) — lasciare il flag nella WeakHashMap continuava a forzare
                            // mCardColor anche su chiamate successive scorrelate (testo/divider/
                            // altri colori) che riusano lo STESSO oggetto dopo un recycle().
                            // Bug reale confermato dall'utente 2026-09-04: testo intere pagine di
                            // Impostazioni sparito (leggibile solo su ripple/pressed).
                            if (mFlaggedArrays.remove(p.thisObject) == null) return;
                            if (!mThemeApplied || !isNight()) return;
                            p.setResult(mCardColor);
                        } catch (Throwable ignored) {}
                    }
                });
                // Rete di sicurezza: se il chiamante ricicla l'array senza mai leggerne il
                // colore, il flag va comunque tolto qui — stesso motivo di cui sopra.
                findAndHookMethod(android.content.res.TypedArray.class, "recycle", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        try { mFlaggedArrays.remove(p.thisObject); } catch (Throwable ignored) {}
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] SettingsCardBackgroundMod: TypedArray.getColor hook failed: " + t);
            }
        }

        Class<?> cardLayoutCls = null;
        try {
            cardLayoutCls = findClass(
                    "com.coui.appcompat.cardlist.COUICardListSelectedItemLayout", lp.classLoader);
            // 2026-09-06: per com.oplus.pantanal.ums la CLASSE risolve (non offuscata, confermato
            // via "dumpsys activity --views" dal vivo: nome pieno visibile nell'albero reale), ma
            // QUESTO overload di init(Context, boolean) lancia NoSuchMethodError — la copia della
            // libreria COUI dentro UMS.apk ha una firma diversa (versione diversa della stessa
            // libreria open-source, non offuscazione). Salvato qui per il bordo per-TIPO sotto
            // (tintPantanalAiCardBorders/findAllCardViews) — quello non dipende da QUALE overload
            // di init() esiste davvero, solo dal tipo runtime della View trovata nell'albero.
            if (PANTANAL_UMS.equals(lp.packageName)) mCardListLayoutCls = cardLayoutCls;
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
            // Build offuscate (Wallpapers/Battery, 2026-09-04, confermato via decompile jadx reale
            // di Wallpapers.apk): "init" qui non esiste, R8 lo ha rinominato in una lettera singola
            // ("m" nel caso osservato) — nome imprevedibile, diverso per ogni build. Fallback:
            // individuare lo stesso metodo per FORMA della firma (Context, boolean) -> void invece
            // che per nome, stesso pattern gia' usato per ScreenshotEnablerMod sui metodi offuscati
            // di com.oplus.screenshot. Per Pantanal UMS specificamente questo fallback non trova
            // nulla neanche lui (la firma reale e' diversa, non solo rinominata) — innocuo, il
            // bordo per-tipo sopra copre comunque il caso.
            if (cardLayoutCls != null) hookCardInitByShape(cardLayoutCls);
        }

        // Sfondo pagina per WirelessSettings/Cast — 2026-09-04, richiesta dell'utente dopo aver
        // visto le card "galleggiare" come riquadri navy su sfondo nero stock (in Impostazioni lo
        // sfondo pagina navy arriva da un overlay OMS compilato a parte per com.android.settings,
        // SettingsThemeCompiler — mai esistito per queste 2 app). Qui niente overlay: tingiamo
        // direttamente android:id/content (il FrameLayout radice di OGNI Activity, stesso approccio
        // già usato in LauncherCardBackgroundMod per lo sfondo pagina del Launcher) su OGNI
        // Activity del pacchetto, non solo una home page specifica — a differenza di Settings
        // queste app non hanno un'unica Activity "principale" filtrabile per nome.
        // 2026-09-05: Settings stesso aggiunto qui — scoperto che il suo overlay statico
        // (SettingsThemeCompiler) non copre OGNI schermata (es. "Carattere", struttura diversa
        // dalla home/preference-list standard che l'overlay conosce). NON return-are per Settings
        // qui sotto: deve proseguire fino alla logica header specifica della sola home page,
        // gated per nome Activity esatto — questo hook generico su Activity.onResume() gira
        // comunque anche lì (redundante ma innocuo: appBarLayout/list_layout/bg_view non esistono
        // sulla home, no-op; android:id/content viene ritinto due volte, stesso colore).
        if (isPageBgTargetPackage(lp.packageName) || SETTINGS.equals(lp.packageName)) {
            try {
                findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        try {
                            Activity activity = (Activity) p.thisObject;
                            if (!lp.packageName.equals(activity.getPackageName())) return;
                            mPageActivities.add(new WeakReference<>(activity));
                            // Stessa corsa già vista per l'header di Settings: appBarLayout/
                            // toolbar potrebbero non essere ancora inflate/attaccate quando
                            // onResume() scatta — un tint immediato trova findViewById() null e
                            // resta nero per sempre (bug segnalato dall'utente 2026-09-04, header
                            // "ancora nero" dopo il primo giro). Stessi retry ritardati.
                            View decor = activity.getWindow().getDecorView();
                            Runnable tint = () -> tintPageContent(activity);
                            tint.run();
                            decor.postDelayed(tint, 400);
                            decor.postDelayed(tint, 1500);
                            decor.postDelayed(tint, 4000);
                            // 2026-09-06: le card "OnePlus AI" (tintPantanalAiCardBorders, dentro
                            // tintPageContent) vengono ricostruite da zero (removeAllViews +
                            // nuove View) ad ogni re-bind di GridPreference — non solo alla prima
                            // apertura pagina. I retry a tempo fisso sopra arrivavano prima del
                            // re-bind vero (dati "Mind Space" caricati async) e il bordo restava
                            // sull'istanza precedente, buttata via — bordo mai visto sull'istanza
                            // finale (segnalato dall'utente: "il bordo non c'è"). Un listener
                            // persistente su ogni layout globale (stesso pattern già in uso per
                            // l'header di Settings) ririnfresca ad ogni ricostruzione reale invece
                            // di indovinare un ritardo fisso.
                            decor.getViewTreeObserver().addOnGlobalLayoutListener(tint::run);
                        } catch (Throwable ignored) {}
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] SettingsCardBackgroundMod: page bg hook failed for "
                        + lp.packageName + ": " + t);
            }
            // Solo per le app OEM (non Settings) questo hook basta da solo — Settings prosegue
            // sotto per la logica header specifica della home page.
            if (!SETTINGS.equals(lp.packageName)) return;
        }

        // Header ("Impostazioni" + barra ricerca) — SOLO Settings, filtrato per nome Activity
        // esatto più sotto: WirelessSettings/Cast non hanno un header analogo da tingere.
        if (!SETTINGS.equals(lp.packageName)) return;

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
                        // Aprire/chiudere la ricerca globale non rifà onResume (stessa Activity,
                        // solo un cambio di Fragment/visibilità interno) — un listener sul layout
                        // globale è l'unico modo per rivalutare tintAppBarLayoutSafely() ad ogni
                        // cambio di altezza reale di app_bar_layout, invece di un timing fisso.
                        decor.getViewTreeObserver().addOnGlobalLayoutListener(
                                () -> applyHeaderTheme(activity));
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] SettingsCardBackgroundMod: Activity.onResume hook failed: " + t);
        }
    }

    /** Cerca tra i metodi DICHIARATI (non ereditati) della classe l'unico con firma
     *  (Context, boolean) -> void — la forma del vero init/m() offuscato, verificata via
     *  decompile: unica corrispondenza in COUICardListSelectedItemLayout, nessuna ambiguità. */
    private void hookCardInitByShape(Class<?> cardLayoutCls) {
        try {
            for (Method m : cardLayoutCls.getDeclaredMethods()) {
                Class<?>[] params = m.getParameterTypes();
                if (params.length != 2 || params[0] != Context.class || params[1] != boolean.class) continue;
                if (m.getReturnType() != void.class) continue;
                if (Modifier.isStatic(m.getModifiers())) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        mCardInstances.add(new WeakReference<>(p.thisObject));
                        if (!mThemeApplied || !isNight()) return;
                        try {
                            if (p.thisObject instanceof View) ((View) p.thisObject).invalidate();
                        } catch (Throwable ignored) {}
                    }
                });
                return;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] SettingsCardBackgroundMod: hookCardInitByShape failed: " + t);
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

    private static boolean isCardTargetPackage(String packageName) {
        for (String p : CARD_TARGET_PACKAGES) if (p.equals(packageName)) return true;
        return false;
    }

    private static boolean isPageBgTargetPackage(String packageName) {
        for (String p : PAGE_BG_TARGET_PACKAGES) if (p.equals(packageName)) return true;
        return false;
    }

    @Override public boolean listensTo(String packageName) { return isCardTargetPackage(packageName); }
}
