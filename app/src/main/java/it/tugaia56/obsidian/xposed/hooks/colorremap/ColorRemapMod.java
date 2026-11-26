package it.tugaia56.obsidian.xposed.hooks.colorremap;

import android.app.AndroidAppHelper;
import android.app.Application;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;
import it.tugaia56.obsidian.xposed.utils.AccentUtil;

/**
 * App con i colori scritti nel codice (Compose, React Native, tavolozze proprie) che un overlay del Theme non raggiunge:
 * i colori fissi vengono sostituiti nel momento in cui l'app li passa alle view (Paint, testo, sfondi, filtri, liste di
 * colori, risorse). Ogni app ha una tabella "colore originale (rgb) -> ruolo": ACCENT = accento del Theme, PAGE = sfondo
 * (background_dark), TILE = tessere e barre (button_material_dark), CARD = card (holo_light_primary_dark), PILL = pillola
 * della barra di navigazione (system_secondary_container_dark), WHITE = bianco, RED = rosso del Theme (holo_red_dark), RIPPLE = ripple_material_dark, A30 = accento con trasparenza al 30% (solo per i colori che l'app usa gia' trasparenti; opachi diventano accento pieno). La trasparenza originale resta. Solo con il tema scuro.
 * Serve la spunta sull'app nell'ambito di Oxydian in LSPosed. Prima di aggiungere un'app controllare che non abbia PairIP.
 */
public class ColorRemapMod extends XposedMods {

    private static final int ACCENT = 0, PAGE = 1, TILE = 2, CARD = 3, PILL = 4, WHITE = 5, RED = 6, A30 = 7, RIPPLE = 8;

    private static final Map<String, Map<Integer, Integer>> RULES = new HashMap<>();

    /** App con una WebView (il contenuto e' un sito chiaro): si attiva la modalita' scura della WebView. */
    private static final java.util.Set<String> WEBVIEW_DARK = new java.util.HashSet<>();

    /** App costruite per un Android recente ma che aprono ancora siti http (Android li blocca da target 28 in su). */
    private static final java.util.Set<String> CLEARTEXT_OK = new java.util.HashSet<>();

    /** App con una WebView: si nascondono pubblicita' e banner promozionali del sito (CSS iniettato nella pagina). */
    private static final java.util.Set<String> AD_BLOCK = new java.util.HashSet<>();

    /** App con schermate chiare di sistema (Impostazioni): si applica il tema scuro di Android sopra quello dell'app. */
    private static final java.util.Set<String> DARK_ACTIVITIES = new java.util.HashSet<>();

    /** Barra di navigazione propria (libreria) da riempire con il colore delle tessere. */
    private static final Map<String, String> BAR_CLASS = new HashMap<>();

    /** App con pillola del tab attivo disegnata in accento pieno (rettangolo arrotondato largo e basso): si schiarisce verso lo sfondo, cosi' l'icona si vede. */
    private static final java.util.Set<String> PILL_FIX = new java.util.HashSet<>();

    /** Viste (per id) che diventano una pillola con sfondo del Theme e bordo accento; i figli diretti dei contenitori indicati (id "contenitore") ricevono lo stesso. */
    private static final Map<String, String[]> PILL_VIEWS = new HashMap<>();
    private static final Map<String, String[]> PILL_PARENTS = new HashMap<>();

    /** Viste (per id) che prendono come sfondo pieno il colore dello sfondo del Theme (barre nere scritte nel codice). */
    private static final Map<String, String[]> VIEW_BG = new HashMap<>();

    /** Colore diverso dallo sfondo per una vista di VIEW_BG: "pacchetto/id" -> ruolo. */
    private static final Map<String, Integer> VIEW_BG_ROLE = new HashMap<>();

    /** Viste (per id) su cui si spegne la scurezza forzata di ColorOS, che scurisce l'accento. */
    private static final Map<String, String[]> VIEW_NO_DARK = new HashMap<>();

    /** App dove il bianco pieno che copre una superficie grande (almeno un terzo dello schermo) diventa lo sfondo del Theme; bianchi piccoli (testi, icone) invariati. */
    private static final java.util.Set<String> BIG_WHITE = new java.util.HashSet<>(java.util.Arrays.asList("ginlemon.iconpackstudio"));

    /** Raggio (dp) del bordo di VIEW_FRAME quando non e' 12. */
    private static final Map<String, Integer> FRAME_RADIUS = new HashMap<>();

    /** Viste (per id, o per nome di classe se contiene un punto) a cui aggiungere un bordo accento arrotondato in primo piano (un disegno, non sul canvas: in alcune app OnePlus il canvas scurisce l'accento). */
    private static final Map<String, String[]> VIEW_FRAME = new HashMap<>();

    /** Pannelli e pulsanti (per id) con sfondo arrotondato: {id, ruolo del riempimento, raggio dp (0 = cerchio), spessore bordo dp (0 = senza), bordo accento 1/0}. */
    private static final Map<String, String[][]> VIEW_SHAPES = new HashMap<>();

    /** Sfondo a pillola creato dal modulo (serve a riconoscerlo e non rifarlo). */
    private static final class PillBg extends android.graphics.drawable.GradientDrawable {}

    static {
        // Box (com.box.android)
        Map<Integer, Integer> box = new HashMap<>();
        for (int c : new int[]{0x0061D5, 0x7FB0EA, 0x3381DD, 0x0065E3, 0x2972FF, 0x76ACFF, 0x3271EA, 0x4E8FF8, 0x2486FC}) box.put(c, ACCENT);
        box.put(0x222222, PAGE);
        box.put(0x212121, PAGE);
        box.put(0x383838, TILE);
        box.put(0x314358, PILL);
        RULES.put("com.box.android", box);
        // Fotocamera OnePlus (com.oplus.camera): la barra in basso (scatto, miniatura, modalita') e' nera nel codice (vista a colori invertiti)
        RULES.put("com.oplus.camera", new HashMap<Integer, Integer>());
        VIEW_BG.put("com.oplus.camera", new String[]{"control_panel_layout", "camera_root"});
        VIEW_BG.put("com.oneplus.filemanager", new String[]{"navigation_tab"});  // barra in basso di I miei file
        VIEW_FRAME.put("com.oneplus.filemanager", new String[]{"main_category_item", "phone_storage"});  // le 7 schede in alto
        // I miei dispositivi: schede dei dispositivi (due fasce grigie) -> sfondo del Theme con bordo accento
        VIEW_BG.put("com.heytap.mydevices", new String[]{"topContainer", "bottomContainer"});
        VIEW_BG_ROLE.put("com.heytap.mydevices/topContainer", TILE);     // fascia con l'immagine: button_material_dark
        VIEW_BG_ROLE.put("com.heytap.mydevices/bottomContainer", CARD);  // fascia con il nome, piu' chiara: holo_light_primary_dark
        VIEW_FRAME.put("com.heytap.mydevices", new String[]{"com.oplus.mydevices.deviceui.devicecard.view.homecard.HomeCardView"});
        FRAME_RADIUS.put("com.heytap.mydevices", 20);
        VIEW_NO_DARK.put("com.heytap.mydevices", new String[]{"connect_state_button"});  // pulsante Connetti nel pannello
        VIEW_SHAPES.put("com.heytap.mydevices", new String[][]{
                {"connect_state_button", String.valueOf(PAGE), "22", "1.5", "1"}});  // Connetti: scuro con bordo accento
        // finestra delle modalita' (si apre toccando due volte il tab attivo): pannello nel colore dello sfondo con bordo accento, icone nel colore delle tessere
        VIEW_SHAPES.put("com.oplus.camera", new String[][]{
                {"oplus_menu_bottom", String.valueOf(PAGE), "24", "0", "0"},
                {"bottom_menu_item", String.valueOf(TILE), "0", "0", "0"}});
        // Firefox (org.mozilla.firefox 160, design "Nova"): menu, barra e altre parti Compose usano la tavolozza scritta nel codice
        // (grigi caldi/viola): pagina, card e viola -> colori del Theme (il resto dei colori segue l'overlay)
        Map<Integer, Integer> firefox = new HashMap<>();
        for (int c : new int[]{0x1D1B1F, 0x171519, 0x131215}) firefox.put(c, PAGE);
        firefox.put(0x252428, TILE);
        for (int c : new int[]{0x312F33, 0x3F3E42, 0x515054}) firefox.put(c, CARD);
        for (int c : new int[]{0xCDB7FF, 0xB393FF}) firefox.put(c, ACCENT);
        for (int c : new int[]{0x5939A8, 0x3E2976}) firefox.put(c, PILL);
        RULES.put("org.mozilla.firefox", firefox);
        // Chrome (com.android.chrome): le pillole della pagina nuova scheda (Cerca con Google, AI Mode, Incognito) sono viste con un
        // colore calcolato da Chrome (#333654) e disegni cifrati: si ritrovano per id e si rifanno con sfondo del Theme e bordo accento
        RULES.put("com.android.chrome", new HashMap<Integer, Integer>());
        PILL_VIEWS.put("com.android.chrome", new String[]{"search_box_shadow_container", "composeplate_button", "incognito_button"});
        PILL_PARENTS.put("com.android.chrome", new String[]{"buttons_container"});
        // Call Recorder (org.skvalex.cr): riga verde "holo" (#99cc00) sotto le date, colorata nel codice
        Map<Integer, Integer> cr = new HashMap<>();
        cr.put(0x99CC00, ACCENT);
        RULES.put("org.skvalex.cr", cr);
        // Claude (com.anthropic.claude): grigi caldi scritti nel codice -> sfondo e superfici del Theme (l'arancione del marchio resta)
        Map<Integer, Integer> claude = new HashMap<>();
        claude.put(0x151515, PAGE);
        claude.put(0x20201F, TILE);
        claude.put(0x313131, CARD);
        claude.put(0x111111, PAGE);
        RULES.put("com.anthropic.claude", claude);
        // Dreo (com.hesung.dreo): il testo blu #3370ff e' scritto nel bundle JS (React Native); le card del profilo (#303236) cambiano sotto
        Map<Integer, Integer> dreo = new HashMap<>();
        dreo.put(0x3370FF, ACCENT);
        RULES.put("com.hesung.dreo", dreo);
        // Google Drive (com.google.android.apps.docs): sfondo e card seguono il Theme (colori di sistema); qui solo il riquadro attivo di scelta vista
        Map<Integer, Integer> drive = new HashMap<>();
        drive.put(0xCFC0E8, ACCENT); // riquadro attivo di scelta vista (elenco/griglia): sfondo
        drive.put(0x362B4B, WHITE);  // e icona
        RULES.put("com.google.android.apps.docs", drive);
        // APKUpdater (com.apkupdater.ci): le superfici seguono il Theme; la barra in alto e' un colore calcolato da Material 3
        // (superficie + velatura dell'accento) e va riportata al colore della pagina
        Map<Integer, Integer> apku = new HashMap<>();
        apku.put(0x25293B, PAGE);
        RULES.put("com.apkupdater.ci", apku);
        // APKMirror (cf.vojtechh.apkmirror): tema chiaro; sfondo e testo delle Impostazioni vengono dai colori di Android
        // (non cambiabili dall'overlay): sfondo -> colore del Theme, testo nero -> bianco (la trasparenza resta)
        Map<Integer, Integer> apkm = new HashMap<>();
        apkm.put(0xFAFAFA, PAGE);
        apkm.put(0x000000, WHITE);
        apkm.put(0xFF8B14, ACCENT);  // arancione dell'app (pulsante di ricerca, icona attiva in basso)
        apkm.put(0xF47D20, PAGE);    // barra di stato (versione piu' scura)
        RULES.put("cf.vojtechh.apkmirror", apkm);
        // Discord (com.discord, React Native): grigi scuri e blu/viola del marchio -> colori del Theme (letti a runtime, non fissi)
        Map<Integer, Integer> discord = new HashMap<>();
        discord.put(0x1A1A1E, PAGE);   // sfondo lista canali e pagine
        discord.put(0x121214, PAGE);   // barra dei server
        discord.put(0x222225, TILE);   // pulsanti tondi
        discord.put(0x29292D, TILE);   // campo Cerca e riquadri
        discord.put(0x242429, TILE);   // barra utente in basso
        discord.put(0x5865F2, ACCENT); // blu/viola (pulsanti, selezioni, link)
        discord.put(0x4752C4, ACCENT); // sua variante premuta
        RULES.put("com.discord", discord);
        WEBVIEW_DARK.add("cf.vojtechh.apkmirror");
        CLEARTEXT_OK.add("cf.vojtechh.apkmirror");
        AD_BLOCK.add("cf.vojtechh.apkmirror");
        DARK_ACTIVITIES.add("cf.vojtechh.apkmirror");
        BAR_CLASS.put("cf.vojtechh.apkmirror", "com.roughike.bottombar.BottomBar");
        // F-Droid (org.fdroid.fdroid): interfaccia Compose con colori Material fissi nel codice
        Map<Integer, Integer> fdroid = new HashMap<>();
        fdroid.put(0x111318, PAGE);   // sfondo usato ovunque
        fdroid.put(0x1D2024, PAGE);   // barra di navigazione
        fdroid.put(0xA6C8FF, ACCENT); // icona selezionata della barra di navigazione (e altri blu primari)
        fdroid.put(0x01315E, WHITE);  // icona sopra il colore primario
        fdroid.put(0xBACF82, ACCENT); // testo verde nelle Impostazioni
        RULES.put("org.fdroid.fdroid", fdroid);
        // Aurora Store (com.aurora.store): il numero degli aggiornamenti (pallino sull'icona Aggiornamenti) e' Compose con i
        // colori "errore" di Material 3 scritti nel codice: sfondo #f2b8b5 -> rosso del Theme (holo_red_dark), numero #601410 -> bianco
        Map<Integer, Integer> aurora = new HashMap<>();
        aurora.put(0xF2B8B5, RED);
        aurora.put(0x601410, WHITE);
        aurora.put(0x5E5CA6, ACCENT);  // pillola del tab selezionato della barra in basso
        RULES.put("com.aurora.store", aurora);
        // Dropbox (com.dropbox.android): le schermate nuove sono Compose con i colori "dig" (design system) scritti nel codice:
        // neri caldi di pagina e card -> sfondo e tessere, blu -> accento, blu scuro di selezione -> pillola
        Map<Integer, Integer> dropbox = new HashMap<>();
        dropbox.put(0x1A1918, PAGE);
        dropbox.put(0x121211, PAGE);
        dropbox.put(0x242321, TILE);
        dropbox.put(0x2D2B29, TILE);
        dropbox.put(0x313030, TILE);
        dropbox.put(0x3C4148, TILE);   // sfondo della barra di navigazione in basso
        PILL_FIX.add("com.dropbox.android");
        for (int c : new int[]{0x3080FF, 0x0061FA, 0x004DC7, 0x5F9DFF, 0x3984FF, 0x75AAFF, 0x1B7CFF}) dropbox.put(c, ACCENT);
        for (int c : new int[]{0x002969, 0x003280, 0x003A97}) dropbox.put(c, PILL);
        dropbox.put(0x00183E, WHITE);  // icona/testo sopra il blu
        RULES.put("com.dropbox.android", dropbox);
        // GitHub (com.github.android): la barra in alto e' Compose; icone (cerca, aggiorna, nuovo) azzurre #85beff -> accento
        Map<Integer, Integer> github = new HashMap<>();
        github.put(0x85BEFF, ACCENT);
        github.put(0x2E8FFF, A30);     // pillola del tab attivo della barra in basso: blu al 16% -> accento al 30%
        github.put(0x17181C, PAGE);    // sfondo di barre e pagine scritto nel codice
        github.put(0x050505, PAGE);
        RULES.put("com.github.android", github);
        // Icon Pack Studio (ginlemon.iconpackstudio): tutta Compose
        Map<Integer, Integer> ips = new HashMap<>();
        ips.put(0x000000, PAGE);    // sfondo e barra in basso
        ips.put(0x0A0A0A, TILE);    // card scure delle anteprime
        ips.put(0x13161B, TILE);    // card delle anteprime
        ips.put(0x1D1F24, CARD);    // campo di ricerca
        ips.put(0x317AFF, ACCENT);  // azzurro: tab attivo e filtro selezionato
        ips.put(0x006ED5, ACCENT);  // pulsante "Anteprima" nella modifica
        RULES.put("ginlemon.iconpackstudio", ips);
        // I miei file (com.oneplus.filemanager): card in alto colorate dal codice (BORDER_FILL; la barra in basso: VIEW_BG) (l'overlay del Theme fa il resto)
        Map<Integer, Integer> oneplusFiles = new HashMap<>();
        oneplusFiles.put(0x1A1A1A, PAGE);  // card "Tutti i file" e categorie; il bordo accento e' in VIEW_FRAME
        RULES.put("com.oneplus.filemanager", oneplusFiles);
        Map<Integer, Integer> myDevices = new HashMap<>();
        myDevices.put(0xFF6C61, ACCENT);  // testo "Elimina dispositivo" (rosso errore dell app)
        myDevices.put(0x1E1E1E, PAGE);  // pannello che si apre toccando una scheda
        myDevices.put(0x363636, CARD);  // riga "Impostazioni dispositivo audio" nel pannello
        RULES.put("com.heytap.mydevices", myDevices);
        Map<Integer, Integer> google = new HashMap<>();
        google.put(0x22242A, RIPPLE);  // pulsante rotondo del menu in alto a sinistra
        RULES.put("com.google.android.googlequicksearchbox", google);  // le pillole sotto la barra Cerca usano BORDER_FILL
    }

    /** Colori che cambiano solo quando l'app li disegna come rettangolo arrotondato (pulsanti, chip): il testo e le icone con lo stesso colore restano come sono. */
    private static final Map<String, Map<Integer, Integer>> FILL_RULES = new HashMap<>();

    static {
        Map<Integer, Integer> claudeFill = new HashMap<>();
        claudeFill.put(0xF9F9F7, ACCENT);   // pulsante "Nuova sessione" e pulsante di invio
        FILL_RULES.put("com.anthropic.claude", claudeFill);
    }

    /** Riempimenti che prendono il colore del tema e un bordo accento (card): colore originale -> ruolo del riempimento. Si agisce al momento del disegno. */
    private static final Map<String, Map<Integer, Integer>> BORDER_FILL = new HashMap<>();

    static {
        Map<Integer, Integer> dreoCards = new HashMap<>();
        dreoCards.put(0x303236, TILE);
        BORDER_FILL.put("com.hesung.dreo", dreoCards);
        // Google (app): pillole sotto la barra Cerca (Cerca, AI Mode, comando vocale, Lens) #2c2e35 -> sfondo del Theme con bordo accento
        Map<Integer, Integer> googlePills = new HashMap<>();
        googlePills.put(0x2C2E35, PAGE);
        BORDER_FILL.put("com.google.android.googlequicksearchbox", googlePills);
    }

    /** Barre di navigazione proprie dell'app: classe della vista -> sfondo pieno nel colore delle tessere, a pillola, con bordo accento. */
    private static final Map<String, String> SOLID_BAR = new HashMap<>();

    static {
        SOLID_BAR.put("com.hesung.dreo", "com.hesung.dreo.view.CustPageNavigationView");
    }

    public static boolean handles(String pkg) {
        return RULES.containsKey(pkg);
    }

    private static volatile boolean sReady;
    private static final int[] sColors = new int[9];
    private static int sRippleAlpha = 0xFF;
    /** load() legge colori con getColor, che e' agganciato: senza guardia si richiamerebbe all'infinito. */
    private static final ThreadLocal<Boolean> sBusy = new ThreadLocal<>();

    private Map<Integer, Integer> mRules;

    public ColorRemapMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {}

    @Override
    public boolean listensTo(String packageName) {
        return RULES.containsKey(packageName);
    }

    private static int resColor(Resources res, String name) {
        int id = Resources.getSystem().getIdentifier(name, "color", "android");
        return id == 0 ? -1 : (res.getColor(id, null) & 0xFFFFFF);
    }

    private static void load() {
        if (sReady || sBusy.get() != null) return;
        sBusy.set(Boolean.TRUE);
        try {
            Application app = AndroidAppHelper.currentApplication();
            if (app == null) return;
            Resources res = app.getResources();
            sColors[ACCENT] = AccentUtil.systemAccent() & 0xFFFFFF;
            sColors[PAGE] = resColor(res, "background_dark");
            sColors[TILE] = resColor(res, "button_material_dark");
            sColors[CARD] = resColor(res, "holo_light_primary_dark");
            sColors[PILL] = resColor(res, "system_secondary_container_dark");
            sColors[WHITE] = 0xFFFFFF;
            sColors[RED] = resColor(res, "holo_red_dark");
            sColors[A30] = sColors[ACCENT];
            sColors[RIPPLE] = resColor(res, "ripple_material_dark");
            int rid = Resources.getSystem().getIdentifier("ripple_material_dark", "color", "android");
            sRippleAlpha = rid == 0 ? 0xFF : (res.getColor(rid, null) >>> 24);
            sReady = true;
        } catch (Throwable ignored) {
        } finally {
            sBusy.remove();
        }
    }

    private static boolean night() {
        return (Resources.getSystem().getConfiguration().uiMode & 0x30) == 0x20;
    }

    private int map(int c) {
        Integer role = mRules.get(c & 0xFFFFFF);
        if (role == null) return c;
        if (!sReady) load();
        if (!sReady || sColors[role] < 0) return c;
        if (role == A30 && (c >>> 24) < 0xFF) return 0x4D000000 | sColors[role];
        if (role == RIPPLE) return (sRippleAlpha << 24) | sColors[role];
        return (c & 0xFF000000) | sColors[role];
    }

    private final class First extends XC_MethodHook {
        private final String mName;
        private boolean mSeen;

        First(String name) { mName = name; }

        @Override protected void beforeHookedMethod(MethodHookParam p) {
            try {
                if (sBusy.get() != null || !night()) return;
                int c = (Integer) p.args[0], n = map(c);
                if (n != c) {
                    p.args[0] = n;
                    if (!mSeen) {
                        mSeen = true;
                        XposedBridge.log("[ Obsidian ] ColorRemapMod: " + mName + " " + Integer.toHexString(c) + " -> " + Integer.toHexString(n));
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private final class ResultColor extends XC_MethodHook {
        @Override protected void afterHookedMethod(MethodHookParam p) {
            try {
                if (sBusy.get() != null || !night()) return;
                int c = (Integer) p.getResult(), n = map(c);
                if (n != c) p.setResult(n);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        mRules = RULES.get(lp.packageName);
        if (mRules == null) return;
        int ok = 0;
        try { XposedHelpers.findAndHookMethod(Paint.class, "setColor", int.class, new First("Paint.setColor")); ok++; } catch (Throwable t) { log("Paint.setColor", t); }
        try { XposedHelpers.findAndHookMethod(TextView.class, "setTextColor", int.class, new First("TextView.setTextColor")); ok++; } catch (Throwable t) { log("TextView.setTextColor", t); }
        try { XposedHelpers.findAndHookMethod(View.class, "setBackgroundColor", int.class, new First("View.setBackgroundColor")); ok++; } catch (Throwable t) { log("View.setBackgroundColor", t); }
        try { XposedHelpers.findAndHookMethod(ColorDrawable.class, "setColor", int.class, new First("ColorDrawable.setColor")); ok++; } catch (Throwable t) { log("ColorDrawable.setColor", t); }
        try { XposedHelpers.findAndHookConstructor(ColorDrawable.class, int.class, new First("ColorDrawable()")); ok++; } catch (Throwable t) { log("ColorDrawable()", t); }
        try { XposedHelpers.findAndHookMethod(GradientDrawable.class, "setColor", int.class, new First("GradientDrawable.setColor")); ok++; } catch (Throwable t) { log("GradientDrawable.setColor", t); }
        try { XposedHelpers.findAndHookMethod(Canvas.class, "drawColor", int.class, new First("Canvas.drawColor")); ok++; } catch (Throwable t) { log("Canvas.drawColor", t); }
        try { XposedHelpers.findAndHookConstructor(PorterDuffColorFilter.class, int.class, PorterDuff.Mode.class, new First("PorterDuffColorFilter()")); ok++; } catch (Throwable t) { log("PorterDuffColorFilter()", t); }
        try { XposedHelpers.findAndHookConstructor(android.graphics.BlendModeColorFilter.class, int.class, android.graphics.BlendMode.class, new First("BlendModeColorFilter()")); ok++; } catch (Throwable t) { log("BlendModeColorFilter()", t); }
        try { XposedHelpers.findAndHookMethod(Drawable.class, "setColorFilter", int.class, PorterDuff.Mode.class, new First("Drawable.setColorFilter")); ok++; } catch (Throwable t) { log("Drawable.setColorFilter", t); }
        try { XposedHelpers.findAndHookMethod(Drawable.class, "setTint", int.class, new First("Drawable.setTint")); ok++; } catch (Throwable t) { log("Drawable.setTint", t); }
        try { XposedHelpers.findAndHookMethod(android.widget.ImageView.class, "setColorFilter", int.class, new First("ImageView.setColorFilter")); ok++; } catch (Throwable t) { log("ImageView.setColorFilter", t); }
        try { XposedHelpers.findAndHookConstructor(ForegroundColorSpan.class, int.class, new First("ForegroundColorSpan()")); ok++; } catch (Throwable t) { log("ForegroundColorSpan()", t); }
        try { XposedHelpers.findAndHookMethod(ColorStateList.class, "valueOf", int.class, new First("ColorStateList.valueOf")); ok++; } catch (Throwable t) { log("ColorStateList.valueOf", t); }
        try {
            XposedHelpers.findAndHookConstructor(ColorStateList.class, int[][].class, int[].class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        int[] a = (int[]) p.args[1];
                        if (a == null || sBusy.get() != null || !night()) return;
                        int[] b = null;
                        for (int i = 0; i < a.length; i++) {
                            int n = map(a[i]);
                            if (n != a[i]) {
                                if (b == null) b = a.clone();
                                b[i] = n;
                            }
                        }
                        if (b != null) p.args[1] = b;
                    } catch (Throwable ignored) {}
                }
            });
            ok++;
        } catch (Throwable t) { log("ColorStateList()", t); }
        try {
            XposedHelpers.findAndHookMethod(Paint.class, "setColor", long.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (sBusy.get() != null || !night()) return;
                        int c = Color.toArgb((Long) p.args[0]), n = map(c);
                        if (n != c) p.args[0] = Color.pack(n);
                    } catch (Throwable ignored) {}
                }
            });
            ok++;
        } catch (Throwable t) { log("Paint.setColor(long)", t); }
        // indicatore attivo ("pillola") della barra di navigazione Material: colore dell'accento
        for (String cn : new String[]{"com.google.android.material.navigation.NavigationBarView",
                "com.google.android.material.navigation.NavigationBarMenuView"}) {
            try {
                Class<?> nc = XposedHelpers.findClassIfExists(cn, lp.classLoader);
                if (nc == null) continue;
                XposedHelpers.findAndHookMethod(nc, "setItemActiveIndicatorColor", ColorStateList.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            if (p.args[0] == null || sBusy.get() != null || !night()) return;
                            if (!sReady) load();
                            if (sReady) p.args[0] = ColorStateList.valueOf(0xFF000000 | sColors[ACCENT]);
                        } catch (Throwable ignored) {}
                    }
                });
                ok++;
            } catch (Throwable t) { log(cn + ".setItemActiveIndicatorColor", t); }
        }
        // sfondi a tinta unita presi da attributi/risorse (ColorDrawable): il colore si sostituisce sul drawable prima che la view lo usi
        try {
            XposedHelpers.findAndHookMethod(View.class, "setBackground", Drawable.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (!(p.args[0] instanceof ColorDrawable) || sBusy.get() != null || !night()) return;
                        ColorDrawable d = (ColorDrawable) p.args[0];
                        int c = d.getColor(), n = map(c);
                        if (n != c) {
                            ColorDrawable m = (ColorDrawable) d.mutate();
                            m.setColor(n);
                        }
                    } catch (Throwable ignored) {}
                }
            });
            ok++;
        } catch (Throwable t) { log("View.setBackground", t); }
        // gradienti (Compose usa LinearGradient per le sfumature, ad esempio sotto l'header): si mappano i colori delle tappe
        try {
            XposedHelpers.findAndHookConstructor(android.graphics.LinearGradient.class, float.class, float.class, float.class, float.class,
                    int[].class, float[].class, android.graphics.Shader.TileMode.class, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                int[] a = (int[]) p.args[4];
                                if (a == null || sBusy.get() != null || !night()) return;
                                int[] b = null;
                                for (int i = 0; i < a.length; i++) {
                                    int n = map(a[i]);
                                    if (n != a[i]) {
                                        if (b == null) b = a.clone();
                                        b[i] = n;
                                    }
                                }
                                if (b != null) p.args[4] = b;
                            } catch (Throwable ignored) {}
                        }
                    });
            ok++;
        } catch (Throwable t) { log("LinearGradient(int[])", t); }
        try {
            XposedHelpers.findAndHookConstructor(android.graphics.LinearGradient.class, float.class, float.class, float.class, float.class,
                    int.class, int.class, android.graphics.Shader.TileMode.class, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                if (sBusy.get() != null || !night()) return;
                                for (int i = 4; i <= 5; i++) {
                                    int c = (Integer) p.args[i], n = map(c);
                                    if (n != c) p.args[i] = n;
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
            ok++;
        } catch (Throwable t) { log("LinearGradient(int,int)", t); }
        final Map<Integer, Integer> fill = FILL_RULES.get(lp.packageName);
        if (fill != null) {
            try {
                Class<?> rc = XposedHelpers.findClassIfExists("android.graphics.BaseRecordingCanvas", lp.classLoader);
                if (rc != null) {
                    XC_MethodHook rr = new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                if (sBusy.get() != null || !night()) return;
                                Paint paint = (Paint) p.args[p.args.length - 1];
                                int c = paint.getColor();
                                Integer role = fill.get(c & 0xFFFFFF);
                                if (role == null) return;
                                if (!sReady) load();
                                if (!sReady || sColors[role] < 0) return;
                                p.setObjectExtra("old", c);
                                paint.setColor((c & 0xFF000000) | sColors[role]);
                            } catch (Throwable ignored) {}
                        }

                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                Object old = p.getObjectExtra("old");
                                if (old != null) ((Paint) p.args[p.args.length - 1]).setColor((Integer) old);
                            } catch (Throwable ignored) {}
                        }
                    };
                    XposedHelpers.findAndHookMethod(rc, "drawRoundRect", float.class, float.class, float.class, float.class, float.class, float.class, Paint.class, rr);
                    XposedHelpers.findAndHookMethod(rc, "drawRoundRect", android.graphics.RectF.class, float.class, float.class, Paint.class, rr);
                    ok += 2;
                    // pulsanti tondi o a pillola disegnati come cerchio/ovale/tracciato: solo se grandi (le icone, piccole, restano come sono)
                    final float dens = Resources.getSystem().getDisplayMetrics().density;
                    XC_MethodHook big = new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                if (sBusy.get() != null || !night()) return;
                                Paint paint = (Paint) p.args[p.args.length - 1];
                                int c = paint.getColor();
                                Integer role = fill.get(c & 0xFFFFFF);
                                if (role == null) return;
                                float minSide;
                                Object a0 = p.args[0];
                                if (a0 instanceof android.graphics.Path) {
                                    android.graphics.RectF b = new android.graphics.RectF();
                                    ((android.graphics.Path) a0).computeBounds(b, true);
                                    minSide = Math.min(b.width(), b.height());
                                } else if (a0 instanceof Float && p.args.length == 4) {   // drawCircle(cx, cy, radius, paint)
                                    minSide = 2f * (Float) p.args[2];
                                } else {
                                    return;
                                }
                                if (minSide < 36f * dens) return;
                                if (!sReady) load();
                                if (!sReady || sColors[role] < 0) return;
                                p.setObjectExtra("old", c);
                                paint.setColor((c & 0xFF000000) | sColors[role]);
                            } catch (Throwable ignored) {}
                        }

                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                Object old = p.getObjectExtra("old");
                                if (old != null) ((Paint) p.args[p.args.length - 1]).setColor((Integer) old);
                            } catch (Throwable ignored) {}
                        }
                    };
                    XposedHelpers.findAndHookMethod(rc, "drawPath", android.graphics.Path.class, Paint.class, big);
                    XposedHelpers.findAndHookMethod(rc, "drawCircle", float.class, float.class, float.class, Paint.class, big);
                    ok += 2;
                }
            } catch (Throwable t) { log("BaseRecordingCanvas.drawRoundRect", t); }
        }
        final Map<Integer, Integer> bf = BORDER_FILL.get(lp.packageName);
        if (bf != null) {
            try {
                Class<?> rc = XposedHelpers.findClassIfExists("android.graphics.BaseRecordingCanvas", lp.classLoader);
                if (rc != null) {
                    final float dens = Resources.getSystem().getDisplayMetrics().density;
                    final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
                    stroke.setStyle(Paint.Style.STROKE);
                    final android.graphics.RectF tmp = new android.graphics.RectF();
                    XC_MethodHook card = new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                if (sBusy.get() != null || !night()) return;
                                Paint paint = (Paint) p.args[p.args.length - 1];
                                int c = paint.getColor();
                                Integer role = bf.get(c & 0xFFFFFF);
                                if (role == null || paint.getStyle() == Paint.Style.STROKE) return;
                                if (!sReady) load();
                                if (!sReady || sColors[role] < 0) return;
                                p.setObjectExtra("old", c);
                                paint.setColor((c & 0xFF000000) | sColors[role]);
                            } catch (Throwable ignored) {}
                        }

                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                Object old = p.getObjectExtra("old");
                                if (old == null) return;
                                Paint paint = (Paint) p.args[p.args.length - 1];
                                paint.setColor((Integer) old);
                                Canvas cv = (Canvas) p.thisObject;
                                float w = 1.5f * dens;
                                stroke.setStrokeWidth(w);
                                stroke.setColor(0xFF000000 | sColors[ACCENT]);
                                String n = p.method.getName();
                                Object a0 = p.args[0];
                                if (a0 instanceof Float) {                       // coordinate float (rect o rect arrotondato)
                                    tmp.set((Float) a0, (Float) p.args[1], (Float) p.args[2], (Float) p.args[3]);
                                    tmp.inset(w / 2f, w / 2f);
                                    if (n.equals("drawRoundRect")) cv.drawRoundRect(tmp, (Float) p.args[4], (Float) p.args[5], stroke);
                                    else cv.drawRect(tmp, stroke);
                                } else if (a0 instanceof android.graphics.RectF) {
                                    tmp.set((android.graphics.RectF) a0);
                                    tmp.inset(w / 2f, w / 2f);
                                    if (n.equals("drawRoundRect")) cv.drawRoundRect(tmp, (Float) p.args[1], (Float) p.args[2], stroke);
                                    else cv.drawRect(tmp, stroke);
                                } else if (a0 instanceof android.graphics.Path) {
                                    cv.drawPath((android.graphics.Path) a0, stroke);
                                }
                            } catch (Throwable ignored) {}
                        }
                    };
                    XposedHelpers.findAndHookMethod(rc, "drawRoundRect", float.class, float.class, float.class, float.class, float.class, float.class, Paint.class, card);
                    XposedHelpers.findAndHookMethod(rc, "drawRoundRect", android.graphics.RectF.class, float.class, float.class, Paint.class, card);
                    XposedHelpers.findAndHookMethod(rc, "drawRect", float.class, float.class, float.class, float.class, Paint.class, card);
                    XposedHelpers.findAndHookMethod(rc, "drawRect", android.graphics.RectF.class, Paint.class, card);
                    XposedHelpers.findAndHookMethod(rc, "drawPath", android.graphics.Path.class, Paint.class, card);
                    ok += 5;
                }
            } catch (Throwable t) { log("card border hooks", t); }
        }
        final String barClass = SOLID_BAR.get(lp.packageName);
        if (barClass != null) {
            try {
                Class<?> bc = XposedHelpers.findClassIfExists(barClass, lp.classLoader);
                if (bc != null) {
                    final float dens = Resources.getSystem().getDisplayMetrics().density;
                    final Paint fillP = new Paint(Paint.ANTI_ALIAS_FLAG);
                    final Paint strokeP = new Paint(Paint.ANTI_ALIAS_FLAG);
                    strokeP.setStyle(Paint.Style.STROKE);
                    final android.graphics.RectF box = new android.graphics.RectF();
                    final Class<?> barCls = bc;
                    // la vista disegna da sola la barra (fondo semitrasparente e bordo sfumato): prima delle icone ci si disegna sopra una pillola piena
                    XposedHelpers.findAndHookMethod(android.view.ViewGroup.class, "dispatchDraw", Canvas.class, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                if (p.thisObject.getClass() != barCls || !night()) return;
                                if (!sReady) load();
                                if (!sReady) return;
                                View v = (View) p.thisObject;
                                float w = 1.5f * dens;
                                box.set(w / 2f, w / 2f, v.getWidth() - w / 2f, v.getHeight() - w / 2f);
                                float r = box.height() / 2f;
                                fillP.setColor(0xFF000000 | sColors[TILE]);
                                strokeP.setStrokeWidth(w);
                                strokeP.setColor(0xFF000000 | sColors[ACCENT]);
                                Canvas c = (Canvas) p.args[0];
                                c.drawRoundRect(box, r, r, fillP);
                                c.drawRoundRect(box, r, r, strokeP);
                            } catch (Throwable ignored) {}
                        }
                    });
                    ok++;
                    ok++;
                } else {
                    XposedBridge.log("[ Obsidian ] ColorRemapMod: " + barClass + " not found");
                }
            } catch (Throwable t) { log("solid bar", t); }
        }
        if (WEBVIEW_DARK.contains(lp.packageName)) {
            try {
                XposedBridge.hookAllConstructors(android.webkit.WebView.class, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        try {
                            android.webkit.WebSettings ws = ((android.webkit.WebView) p.thisObject).getSettings();
                            try { ws.setAlgorithmicDarkeningAllowed(true); } catch (Throwable ignored) {}
                            try { ws.setForceDark(android.webkit.WebSettings.FORCE_DARK_ON); } catch (Throwable ignored) {}
                        } catch (Throwable ignored) {}
                    }
                });
                ok++;
            } catch (Throwable t) { log("WebView dark", t); }
        }
        if (CLEARTEXT_OK.contains(lp.packageName)) {
            try {
                XC_MethodHook allow = new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) { p.setResult(true); }
                };
                XposedBridge.hookAllMethods(android.security.NetworkSecurityPolicy.class, "isCleartextTrafficPermitted", allow);
                ok++;
            } catch (Throwable t) { log("cleartext", t); }
        }
        // colori dati dal sito o calcolati a runtime: Color.parseColor("#...") e barra di stato
        try {
            XposedBridge.hookAllMethods(Color.class, "parseColor", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        if (sBusy.get() != null || !night() || !(p.getResult() instanceof Integer)) return;
                        int c = (Integer) p.getResult(), n = map(c);
                        if (n != c) p.setResult(n);
                    } catch (Throwable ignored) {}
                }
            });
            ok++;
        } catch (Throwable t) { log("Color.parseColor", t); }
        if (AD_BLOCK.contains(lp.packageName)) {
            try {
                final String js = "(function(){try{var s=document.createElement('style');s.textContent='ins.adsbygoogle,.adsbygoogle,.adslot,#apkm-ad-group,"
                        + ".advertisement-text,.membership-text,#bottom-slider,iframe[id^=google_ads],div[id^=google_ads],iframe[src*=doubleclick],"
                        + "iframe[src*=googlesyndication]{display:none!important;height:0!important;min-height:0!important;margin:0!important;padding:0!important}';"
                        + "(document.head||document.documentElement).appendChild(s);}catch(e){}})();";
                final XC_MethodHook inject = new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        try {
                            if (p.args.length > 0 && p.args[0] instanceof android.webkit.WebView)
                                ((android.webkit.WebView) p.args[0]).evaluateJavascript(js, null);
                        } catch (Throwable ignored) {}
                    }
                };
                XposedBridge.hookAllMethods(android.webkit.WebViewClient.class, "onPageStarted", inject);
                XposedBridge.hookAllMethods(android.webkit.WebViewClient.class, "onPageFinished", inject);
                final java.util.Set<Class<?>> seen = new java.util.HashSet<>();
                XposedBridge.hookAllMethods(android.webkit.WebView.class, "setWebViewClient", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        try {
                            if (p.args.length == 0 || p.args[0] == null) return;
                            Class<?> c = p.args[0].getClass();
                            if (c == android.webkit.WebViewClient.class || !seen.add(c)) return;
                            XposedBridge.hookAllMethods(c, "onPageStarted", inject);
                            XposedBridge.hookAllMethods(c, "onPageFinished", inject);
                        } catch (Throwable ignored) {}
                    }
                });
                ok += 3;
            } catch (Throwable t) { log("ad block", t); }
        }
        if (DARK_ACTIVITIES.contains(lp.packageName)) {
            try {
                XposedHelpers.findAndHookMethod(android.app.Activity.class, "onCreate", android.os.Bundle.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            if (!night()) return;
                            ((android.app.Activity) p.thisObject).getTheme().applyStyle(android.R.style.ThemeOverlay_Material_Dark, true);
                        } catch (Throwable ignored) {}
                    }
                });
                ok++;
            } catch (Throwable t) { log("dark activities", t); }
        }
        if (PILL_FIX.contains(lp.packageName)) {
            try {
                Class<?> rc = XposedHelpers.findClassIfExists("android.graphics.BaseRecordingCanvas", lp.classLoader);
                if (rc != null) {
                    XC_MethodHook pill = new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                if (!night()) return;
                                if (!sReady) load();
                                if (!sReady) return;
                                float l, t, r, b;
                                Paint paint;
                                if (p.args.length == 7) {
                                    l = (Float) p.args[0]; t = (Float) p.args[1]; r = (Float) p.args[2]; b = (Float) p.args[3];
                                    paint = (Paint) p.args[6];
                                } else {
                                    android.graphics.RectF rr = (android.graphics.RectF) p.args[0];
                                    l = rr.left; t = rr.top; r = rr.right; b = rr.bottom;
                                    paint = (Paint) p.args[3];
                                }
                                if (paint == null || paint.getColor() != (0xFF000000 | sColors[ACCENT])) return;
                                float dens = Resources.getSystem().getDisplayMetrics().density;
                                float h = b - t, w = r - l;
                                if (h < 26 * dens || h > 42 * dens || w < h * 1.5f || w > h * 3.5f) return;
                                // pillola: meta' accento e meta' colore della barra (opaca)
                                int acc = sColors[ACCENT], bar = sColors[TILE] < 0 ? 0 : sColors[TILE];
                                int mix = (((acc >> 16 & 255) + (bar >> 16 & 255)) / 2 << 16) | (((acc >> 8 & 255) + (bar >> 8 & 255)) / 2 << 8) | (((acc & 255) + (bar & 255)) / 2);
                                p.setObjectExtra("oldPillColor", paint.getColor());
                                paint.setColor(0xFF000000 | mix);
                            } catch (Throwable ignored) {}
                        }
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                Object old = p.getObjectExtra("oldPillColor");
                                if (old == null) return;
                                Paint paint = (Paint) (p.args.length == 7 ? p.args[6] : p.args[3]);
                                paint.setColor((Integer) old);
                            } catch (Throwable ignored) {}
                        }
                    };
                    XposedHelpers.findAndHookMethod(rc, "drawRoundRect", float.class, float.class, float.class, float.class, float.class, float.class, Paint.class, pill);
                    XposedHelpers.findAndHookMethod(rc, "drawRoundRect", android.graphics.RectF.class, float.class, float.class, Paint.class, pill);
                    ok += 2;
                }
            } catch (Throwable t) { log("BaseRecordingCanvas.drawRoundRect (pill)", t); }
        }
        final String[] pillIds = PILL_VIEWS.get(lp.packageName);
        if (pillIds != null) {
            try {
                final String[] parentIds = PILL_PARENTS.get(lp.packageName);
                XposedHelpers.findAndHookMethod(android.view.View.class, "onAttachedToWindow", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        try {
                            if (sBusy.get() != null || !night()) return;
                            android.view.View v = (android.view.View) p.thisObject;
                            int id = v.getId();
                            if (id == android.view.View.NO_ID) {
                                // senza id: e' un figlio diretto di uno dei contenitori
                                android.view.ViewParent vp = v.getParent();
                                if (!(vp instanceof android.view.View) || parentIds == null) return;
                                int pid = ((android.view.View) vp).getId();
                                boolean ok = false;
                                for (String pn : parentIds) if (pid != android.view.View.NO_ID && pid == v.getResources().getIdentifier(pn, "id", lp.packageName)) ok = true;
                                if (!ok || !v.isClickable()) return;
                            } else {
                                boolean ok = false;
                                for (String n : pillIds) if (id == v.getResources().getIdentifier(n, "id", lp.packageName)) ok = true;
                                if (!ok) {
                                    android.view.ViewParent vp = v.getParent();
                                    if (parentIds == null || !(vp instanceof android.view.View)) return;
                                    int pid = ((android.view.View) vp).getId();
                                    for (String pn : parentIds) if (pid != android.view.View.NO_ID && pid == v.getResources().getIdentifier(pn, "id", lp.packageName)) ok = true;
                                    if (!ok || !v.isClickable()) return;
                                }
                            }
                            if (!sReady) load();
                            if (!sReady || sColors[PAGE] < 0) return;
                            if (v.getBackground() instanceof PillBg) return;   // gia' fatta
                            float dens = v.getResources().getDisplayMetrics().density;
                            PillBg gd = new PillBg();
                            gd.setColor(0xFF000000 | sColors[PAGE]);
                            gd.setStroke(Math.max(1, Math.round(1.5f * dens)), 0xFF000000 | sColors[ACCENT]);
                            gd.setCornerRadius(200f * dens);
                            v.setBackground(gd);
                        } catch (Throwable ignored) {}
                    }
                });
                ok++;
            } catch (Throwable t) { log("View.onAttachedToWindow (pills)", t); }
        }
        if (BIG_WHITE.contains(lp.packageName)) {
            try {
                Class<?> rc = XposedHelpers.findClassIfExists("android.graphics.BaseRecordingCanvas", lp.classLoader);
                final android.util.DisplayMetrics dm = Resources.getSystem().getDisplayMetrics();
                final float min = dm.widthPixels * (float) dm.heightPixels / 3f;
                XC_MethodHook white = new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            if (sBusy.get() != null || !night()) return;
                            Paint paint = (Paint) p.args[p.args.length - 1];
                            if (paint.getColor() != 0xFFFFFFFF || paint.getShader() != null) return;
                            Object a0 = p.args[0];
                            float w, h;
                            if (a0 instanceof Float) { w = (Float) p.args[2] - (Float) a0; h = (Float) p.args[3] - (Float) p.args[1]; }
                            else if (a0 instanceof android.graphics.RectF) { android.graphics.RectF r = (android.graphics.RectF) a0; w = r.width(); h = r.height(); }
                            else if (a0 instanceof android.graphics.Rect) { android.graphics.Rect r = (android.graphics.Rect) a0; w = r.width(); h = r.height(); }
                            else if (a0 instanceof android.graphics.Path) { android.graphics.RectF r = new android.graphics.RectF(); ((android.graphics.Path) a0).computeBounds(r, true); w = r.width(); h = r.height(); }
                            else return;
                            if (w * h < min) return;
                            if (!sReady) load();
                            if (!sReady || sColors[PAGE] < 0) return;
                            p.setObjectExtra("white", 0xFFFFFFFF);
                            paint.setColor(0xFF000000 | sColors[PAGE]);
                        } catch (Throwable ignored) {}
                    }

                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        try {
                            Object old = p.getObjectExtra("white");
                            if (old != null) ((Paint) p.args[p.args.length - 1]).setColor((Integer) old);
                        } catch (Throwable ignored) {}
                    }
                };
                if (rc != null) {
                    XposedHelpers.findAndHookMethod(rc, "drawRect", float.class, float.class, float.class, float.class, Paint.class, white);
                    XposedHelpers.findAndHookMethod(rc, "drawRect", android.graphics.RectF.class, Paint.class, white);
                    XposedHelpers.findAndHookMethod(rc, "drawRect", android.graphics.Rect.class, Paint.class, white);
                    XposedHelpers.findAndHookMethod(rc, "drawRoundRect", float.class, float.class, float.class, float.class, float.class, float.class, Paint.class, white);
                    XposedHelpers.findAndHookMethod(rc, "drawPath", android.graphics.Path.class, Paint.class, white);
                    ok += 5;
                }
            } catch (Throwable t) { log("BaseRecordingCanvas (big white)", t); }
        }
        final String[] noDarkIds = VIEW_NO_DARK.get(lp.packageName);
        if (noDarkIds != null) {
            try {
                XposedHelpers.findAndHookMethod(android.view.View.class, "onAttachedToWindow", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        try {
                            if (sBusy.get() != null || !night()) return;
                            android.view.View v = (android.view.View) p.thisObject;
                            int id = v.getId();
                            if (id == android.view.View.NO_ID) return;
                            for (String n : noDarkIds) if (id == v.getResources().getIdentifier(n, "id", lp.packageName)) v.setForceDarkAllowed(false);
                        } catch (Throwable ignored) {}
                    }
                });
                ok++;
            } catch (Throwable t) { log("View.onAttachedToWindow (no dark)", t); }
        }
        final String[] frameIds = VIEW_FRAME.get(lp.packageName);
        if (frameIds != null) {
            try {
                XposedHelpers.findAndHookMethod(android.view.View.class, "onAttachedToWindow", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        try {
                            if (sBusy.get() != null || !night()) return;
                            android.view.View v = (android.view.View) p.thisObject;
                            int id = v.getId();
                            boolean ok = false;
                            for (String n : frameIds) {
                                if (n.indexOf('.') >= 0) ok |= n.equals(v.getClass().getName());
                                else ok |= id != android.view.View.NO_ID && id == v.getResources().getIdentifier(n, "id", lp.packageName);
                            }
                            if (!ok) return;
                            if (!sReady) load();
                            if (!sReady || sColors[ACCENT] < 0) return;
                            float dens = v.getResources().getDisplayMetrics().density;
                            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
                            g.setColor(0);
                            g.setStroke(Math.round(1.5f * dens), 0xFF000000 | sColors[ACCENT]);
                            g.setCornerRadius((FRAME_RADIUS.containsKey(lp.packageName) ? FRAME_RADIUS.get(lp.packageName) : 12) * dens);
                            v.setForceDarkAllowed(false);  // ColorOS scurisce i colori chiari di queste viste
                            v.setForeground(g);
                        } catch (Throwable ignored) {}
                    }
                });
                ok++;
            } catch (Throwable t) { log("View.onAttachedToWindow (frame)", t); }
        }
        final String[] bgIds = VIEW_BG.get(lp.packageName);
        if (bgIds != null) {
            try {
                XposedHelpers.findAndHookMethod(android.view.View.class, "onAttachedToWindow", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        try {
                            if (sBusy.get() != null || !night()) return;
                            android.view.View v = (android.view.View) p.thisObject;
                            int id = v.getId();
                            if (id == android.view.View.NO_ID) return;
                            String hit = null;
                            for (String n : bgIds) if (id == v.getResources().getIdentifier(n, "id", lp.packageName)) hit = n;
                            if (hit == null) return;
                            if (!sReady) load();
                            Integer role = VIEW_BG_ROLE.get(lp.packageName + "/" + hit);
                            int r = role == null ? PAGE : role;
                            if (!sReady || sColors[r] < 0) return;
                            v.setBackgroundColor(0xFF000000 | sColors[r]);
                        } catch (Throwable ignored) {}
                    }
                });
                ok++;
            } catch (Throwable t) { log("View.onAttachedToWindow (bg)", t); }
        }
        if ("com.oplus.camera".equals(lp.packageName)) {
            // sopra l'anteprima (fascia in alto): e' la superficie GL a darle il nero; subito dopo la sua vista si disegna un rettangolo
            // con lo sfondo del Theme fino all'inizio dell'anteprima (sotto le icone, che sono disegnate dopo)
            try {
                final Paint topFill = new Paint();
                XposedHelpers.findAndHookMethod(android.view.ViewGroup.class, "drawChild", Canvas.class, android.view.View.class, long.class, new XC_MethodHook() {
                    private int mGlId, mPrevId;
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        try {
                            if (sBusy.get() != null || !night()) return;
                            android.view.View child = (android.view.View) p.args[1];
                            int id = child.getId();
                            if (id == android.view.View.NO_ID) return;
                            if (mGlId == 0) {
                                Resources r = child.getResources();
                                mGlId = r.getIdentifier("gl_root_view", "id", lp.packageName);
                                mPrevId = r.getIdentifier("frame_layout", "id", lp.packageName);
                            }
                            if (id != mGlId || mGlId == 0) return;
                            android.view.ViewGroup parent = (android.view.ViewGroup) p.thisObject;
                            android.view.View pv = parent.findViewById(mPrevId);
                            if (pv == null || pv.getVisibility() != android.view.View.VISIBLE) return;
                            int[] a = new int[2], b = new int[2];
                            pv.getLocationInWindow(a);
                            parent.getLocationInWindow(b);
                            int top = a[1] - b[1];
                            if (top <= 0) return;
                            if (!sReady) load();
                            if (!sReady || sColors[PAGE] < 0) return;
                            topFill.setColor(0xFF000000 | sColors[PAGE]);
                            ((Canvas) p.args[0]).drawRect(0, 0, parent.getWidth(), top, topFill);
                        } catch (Throwable ignored) {}
                    }
                });
                ok++;
            } catch (Throwable t) { log("ViewGroup.drawChild (camera top)", t); }
        }
        final String[][] shapes = VIEW_SHAPES.get(lp.packageName);
        if (shapes != null) {
            try {
                final int[] shapeIds = new int[shapes.length];
                XposedHelpers.findAndHookMethod(android.view.View.class, "draw", Canvas.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            android.view.View v = (android.view.View) p.thisObject;
                            int id = v.getId();
                            if (id == android.view.View.NO_ID || sBusy.get() != null || !night()) return;
                            if (shapeIds[0] == 0) {
                                for (int i = 0; i < shapes.length; i++) shapeIds[i] = v.getResources().getIdentifier(shapes[i][0], "id", lp.packageName);
                            }
                            String[] sp = null;
                            for (int i = 0; i < shapes.length; i++) if (shapeIds[i] != 0 && id == shapeIds[i]) sp = shapes[i];
                            if (sp == null) return;
                            if (v.getBackground() instanceof PillBg) return;
                            if (!sReady) load();
                            int role = Integer.parseInt(sp[1]);
                            if (!sReady || sColors[role] < 0) return;
                            float dens = v.getResources().getDisplayMetrics().density;
                            PillBg gd = new PillBg();
                            gd.setColor(0xFF000000 | sColors[role]);
                            float rad = Float.parseFloat(sp[2]);
                            if (rad <= 0) gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                            else gd.setCornerRadius(rad * dens);
                            float sw = Float.parseFloat(sp[3]);
                            if (sw > 0) gd.setStroke(Math.max(1, Math.round(sw * dens)), 0xFF000000 | ("1".equals(sp[4]) ? sColors[ACCENT] : sColors[PAGE]));
                            v.setBackground(gd);
                        } catch (Throwable ignored) {}
                    }
                });
                ok++;
            } catch (Throwable t) { log("View.draw (shapes)", t); }
        }
        final String barName = BAR_CLASS.get(lp.packageName);
        if (barName != null) {
            try {
                Class<?> bc = XposedHelpers.findClassIfExists(barName, lp.classLoader);
                if (bc != null) {
                    XposedBridge.hookAllConstructors(bc, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                if (!night()) return;
                                if (!sReady) load();
                                if (!sReady) return;
                                final View v = (View) p.thisObject;
                                v.post(() -> v.setBackgroundColor(0xFF000000 | sColors[TILE]));
                            } catch (Throwable ignored) {}
                        }
                    });
                    ok++;
                }
            } catch (Throwable t) { log("bottom bar", t); }
        }
        ResultColor after = new ResultColor();
        try { XposedHelpers.findAndHookMethod(Resources.class, "getColor", int.class, Resources.Theme.class, after); ok++; } catch (Throwable t) { log("Resources.getColor", t); }
        try { XposedHelpers.findAndHookMethod(TypedArray.class, "getColor", int.class, int.class, after); ok++; } catch (Throwable t) { log("TypedArray.getColor", t); }
        XposedBridge.log("[ Obsidian ] ColorRemapMod: " + ok + " colour hooks installed in " + lp.packageName);
    }

    private static void log(String what, Throwable t) {
        XposedBridge.log("[ Obsidian ] ColorRemapMod: hook " + what + " failed: " + t);
    }
}
