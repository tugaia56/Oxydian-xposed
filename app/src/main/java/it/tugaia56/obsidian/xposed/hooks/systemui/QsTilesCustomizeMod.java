package it.tugaia56.obsidian.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedBridge.hookAllConstructors;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getFloatField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;
import static de.robv.android.xposed.XposedHelpers.setIntField;
import static de.robv.android.xposed.XposedHelpers.getStaticIntField;
import static de.robv.android.xposed.XposedHelpers.getStaticObjectField;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;
import it.tugaia56.obsidian.xposed.utils.DrawableConverter;

/**
 * Personalizza Riquadri — porting reale (parziale, deliberatamente) di OC's
 * QsTileCustomization. Copre solo la parte con tecniche già usate ovunque in Obsidian
 * (hookAllMethods + getObjectField/callMethod su classi OEM, niente di più): Colori Icone,
 * Etichette, Cursore Luminosità (colore/sfondo/icona scura/sfocatura), Animazione Riquadri
 * (ObjectAnimator puro su bindClickListener, nessuna dipendenza OEM), Transizioni pagine QS
 * (18 PageTransformer portati da OC in TileTransformers.java, agganciati su
 * com.android.systemui.qs.PagedTileLayout — classe AOSP, non OEM, quindi stabile).
 * Raggio Angoli Riquadri (KEY_RADIUS_ON/KEY_RADIUS nella Fragment) è in realtà il raggio del
 * CURSORE luminosità/volume in OC (qsSlidersRoundnessValue, OplusQsBaseToggleSliderLayout),
 * non dei riquadri — nome fuorviante ereditato dalla label OC, comportamento verificato nel
 * sorgente reale prima di portarlo.
 *
 * Colore SFONDO riquadri base/in evidenza (2026-08-19): il meccanismo di OC
 * (QsViewBackgroundProxy/BaseTileViewBackground) NON esiste su questa build — confermato
 * via reflection (0 metodi trovati). Trovato il vero meccanismo decompilando SystemUI.apk:
 * GradientTileDrawable (riquadri base, rettangolari) e MixColorTileDrawable (riquadri in
 * evidenza, circolari, con blur) — entrambi ricevono una mappa DrawableState->colore in
 * onStateChange(int[]), riscritta qui PRIMA che il metodo la consumi (vedi hookTileBgBase/
 * hookTileBgHighlight). Ancora NON portato: raggio angoli riquadri (QsViewOutlineProvider,
 * stessa famiglia di problema ma non ancora indagato), tile Media/copertina album.
 */
public class QsTilesCustomizeMod extends XposedMods {

    // ── Colori icone ─────────────────────────────────────────────────────────
    private static final String KEY_ICON_COLORS_ON     = "qs_custom_icon_colors";
    private static final String KEY_ICON_ACTIVE_ACCENT = "qs_custom_icon_active_accent_color";
    private static final String KEY_ICON_ACTIVE   = "qs_custom_icon_active_color";
    private static final String KEY_ICON_INACTIVE = "qs_custom_icon_inactive_color";
    private static final String KEY_ICON_DISABLED = "qs_custom_icon_disabled_color";

    // ── Etichette ────────────────────────────────────────────────────────────
    private static final String KEY_HIDE_LABELS    = "qs_hide_labels";
    private static final String KEY_LABEL_COLOR_ON = "qs_tile_label_enabled";
    private static final String KEY_LABEL_COLOR    = "qs_tile_label";

    // ── Cursore Luminosità ───────────────────────────────────────────────────
    /** "0"=predefinito "1"=scura "2"=bianca. */
    private static final String KEY_BRIGHTNESS_ICON_MODE = "qs_brightness_icon_mode";
    private static final String KEY_BRIGHTNESS_ICON_COLOR = "qs_brightness_icon_custom_color";

    // ── Cursore Volume (icona dentro il riquadro QS — stessa chiave di VolumePanelMod,
    // voce duplicata nella UI su richiesta esplicita, non un controllo indipendente) ──
    private static final String KEY_VOLUME_ICON_MODE  = "qs_volume_icon_mode";
    private static final String KEY_VOLUME_ICON_COLOR = "qs_volume_icon_custom_color";
    private static final String KEY_BRIGHTNESS_CUSTOM_ON = "customize_brightness_slider";
    private static final String KEY_BRIGHTNESS_MODE      = "brightness_slider_progress_color_mode";
    private static final String KEY_BRIGHTNESS_COLOR     = "brightness_slider_color";
    private static final String KEY_BRIGHTNESS_BG_ON     = "brightness_slider_background_color_enabled";
    private static final String KEY_BRIGHTNESS_BG_COLOR  = "brightness_slider_background_color";

    // ── Raggio cursore (vedi nota in cima: NON è il raggio dei riquadri) ───────
    private static final String KEY_RADIUS_ON = "qs_sliders_radius_switch";
    private static final String KEY_RADIUS    = "qs_sliders_radius";

    // ── Interruttore master "Cursori Impostazioni Rapide" (2026-08-20) — come "Personalizza
    // Cursori QS" di OC: se spento, azzera tutti i sotto-interruttori della sezione così il
    // resto del codice (già granulare, ogni opzione col proprio gate) non va toccato — un solo
    // punto di applicazione qui in updatePrefs() invece di sparpagliare il controllo ovunque.
    private static final String KEY_SLIDERS_ON = "qs_sliders_customize_enabled";

    // ── Animazione Riquadri ──────────────────────────────────────────────────
    private static final String KEY_ANIM_STYLE       = "qs_tile_animation_style";
    private static final String KEY_ANIM_DURATION    = "qs_tile_animation_duration";
    private static final String KEY_ANIM_INTERPOLATOR = "qs_tile_animation_interpolator";

    // ── Transizioni pagine QS ────────────────────────────────────────────────
    private static final String KEY_TRANSITIONS_ON = "qs_transitions_title_switch";
    private static final String KEY_TRANSITIONS    = "qs_tile_transformations";

    // ── Sfondo riquadri (base = GradientTileDrawable, in evidenza = MixColorTileDrawable) ────
    // Scoperto via decompilazione reale di SystemUI.apk (il meccanismo di OC, basato su
    // BaseTileViewBackground/QsViewBackgroundProxy, non esiste su questa build). Due colori
    // (attivo/inattivo) per non perdere la distinzione visiva tra riquadri accesi e spenti.
    // "Riquadri piccoli"/Media funzionano sia con "Classico" che "Separati" (ownerOfView()
    // riconosce entrambe le gerarchie di classi); "Riquadri grandi" (pillole 2x1) restano
    // Separati-only — vedi project_qs_tile_bg_color.md. UI in QsTilesCustomizeFragment
    // ("Personalizza Riquadri"), non più sotto una schermata "Separati" dedicata.
    private static final String KEY_TILE_BG_BASE_ON       = "qs_tile_bg_base_enabled";
    private static final String KEY_TILE_BG_BASE_ACTIVE   = "qs_tile_bg_base_active_color";
    private static final String KEY_TILE_BG_BASE_INACTIVE = "qs_tile_bg_base_inactive_color";
    private static final String KEY_TILE_BG_BASE_ACCENT   = "qs_tile_bg_base_active_accent";
    private static final String KEY_TILE_BG_HL_ON       = "qs_tile_bg_highlight_enabled";
    private static final String KEY_TILE_BG_HL_ACTIVE   = "qs_tile_bg_highlight_active_color";
    private static final String KEY_TILE_BG_HL_INACTIVE = "qs_tile_bg_highlight_inactive_color";
    private static final String KEY_TILE_BG_HL_ACCENT   = "qs_tile_bg_highlight_active_accent";
    // Media ha un solo colore, non attivo/inattivo: la sua stateListConfig/stateListColors ha
    // UNA sola voce (WILD_CARD) — confermato via log — non esiste distinzione stato per questa
    // vista. Riusiamo la chiave "_inactive_color" per compatibilità con il colore già scelto.
    private static final String KEY_TILE_BG_MEDIA_ON    = "qs_tile_bg_media_enabled";
    private static final String KEY_TILE_BG_MEDIA_COLOR = "qs_tile_bg_media_inactive_color";

    // ── Bordo pulsanti QS (base + in evidenza) — richiesta 2026-09-16: "si può mettere il bordo
    // ai pulsanti qs - cursori - media ed al pannello", un unico interruttore/colore condiviso
    // per pulsanti+cursori+media (separato dal bordo del pannello). Qui solo i pulsanti per ora.
    private static final String KEY_TILE_BORDER_ON     = "qs_tile_border_enabled";
    private static final String KEY_TILE_BORDER_COLOR  = "qs_tile_border_custom_color"; // "_use_accent" affiancata, stesso pattern di singleColorRow()
    private static final float TILE_BORDER_WIDTH_DP = 1.5f; // stesso spessore di Bordo barra volume

    // ── Bordo Pannello QS (2026-09-18) — 4a superficie della richiesta 09-16, switch/colore
    // separati dal bordo pulsanti/cursori/media sopra.
    private static final String KEY_PANEL_BORDER_ON    = "qs_panel_border_enabled";
    private static final String KEY_PANEL_BORDER_COLOR = "qs_panel_border_custom_color";

    // ── Copertina Album (filtro sulla vera artwork del brano nel riquadro Media) ────
    // Stesse 5 opzioni/tecnica di AlbumArtLockscreenMod (grayscale/accento/blur/grayscale+blur),
    // applicate al coverImg reale del pannello Media invece di iniettare una vista a schermo
    // intero — bindCoverImg(MediaData) è il punto dove OOS imposta l'artwork nativa, la
    // sovrascriviamo subito dopo con la versione filtrata.
    private static final String KEY_MEDIA_COVER_FILTER_ON = "qs_tile_media_cover_filter_enabled";
    private static final String KEY_MEDIA_COVER_FILTER    = "qs_tile_media_cover_filter"; // "0".."4"
    private static final String KEY_MEDIA_COVER_BLUR      = "qs_tile_media_cover_blur";   // 0-100
    private static final int COVER_FILTER_NONE      = 0;
    private static final int COVER_FILTER_GRAYSCALE = 1;
    private static final int COVER_FILTER_ACCENT    = 2;
    private static final int COVER_FILTER_BLUR      = 3;
    private static final int COVER_FILTER_GRAY_BLUR = 4;

    // ── Raggio angoli riquadri (base / in evidenza / media) ─────────────────
    // SepQSResPool/StdQSResPool.updateTileOutline/updateHighLightTileOutline/updateMediaPanelOutline
    // ricevono un CornerOutlineProvider — in pratica quasi sempre un RoundRectOutlineProvider reale
    // (confermato nel sorgente decompilato), con un metodo update(float) mutabile che riscrive sia
    // il raggio scalare che l'array cornerRadii usato per il path — a differenza del colore, qui
    // non serve distinguere il "proprietario": i tre metodi sono già specifici per categoria.
    // Nessun interruttore proprio: riusa mTileBgBaseOn/mTileBgHighlightOn/mTileBgMediaOn (stesso
    // toggle dello sfondo) per non duplicare la voce "Riquadri base/in evidenza/Media" nella UI.
    private static final String KEY_TILE_RADIUS_BASE  = "qs_tile_radius_base_dp";
    private static final String KEY_TILE_RADIUS_HL    = "qs_tile_radius_highlight_dp";
    private static final String KEY_TILE_RADIUS_MEDIA = "qs_tile_radius_media_dp";

    private static final int STATE_ACTIVE = 2;
    private static final int STATE_INACTIVE = 1;

    private static final int SLIDER_PROGRESS = 0;
    private static final int SLIDER_BACKGROUND = 1;
    private static final int BLEND_LUMINOSITY_COLOR_DODGE = 1;
    private static final int BLEND_COLOR_DODGE_LUMINOSITY = 2;
    private static final int BLEND_OVERLAY_LUMINOSITY = 3;
    private static final int BLEND_LUMINOSITY_OVERLAY = 4;

    private boolean mIconColorsOn, mIconActiveAccent;
    private int mIconActive = 0xFFFFFFFF, mIconInactive = 0xFFFFFFFF, mIconDisabled = 0xFFFFFFFF;

    private boolean mHideLabels, mLabelColorOn;
    private int mLabelColor = 0xFFFFFFFF;

    private int mBrightnessIconMode; // 0=predefinito 1=scura 2=bianca 3=accento 4=personalizzata
    private int mBrightnessIconColor = 0xFFFFFFFF;
    private int mVolumeIconMode; // stessa scala di mBrightnessIconMode
    private int mVolumeIconColor = 0xFFFFFFFF;
    private boolean mBrightnessCustomOn, mBrightnessBgOn;
    private int mBrightnessMode, mBrightnessColor = 0xFFFFFFFF, mBrightnessBgColor = 0x00000000;
    private boolean mRadiusOn;
    private int mRadiusDp = 20;
    private boolean mSlidersOn = true;

    private int mAnimStyle, mAnimDuration = 1, mAnimInterpolator;
    private boolean mTransitionsOn;
    private int mTransitionStyle;

    private boolean mTileBgBaseOn, mTileBgHighlightOn, mTileBgMediaOn;
    private boolean mTileBgBaseAccent, mTileBgHlAccent;
    private int mTileBgBaseActive = 0xFF908DFF, mTileBgBaseInactive = 0x19FFFFFF;
    private int mTileBgHlActive = 0xFF908DFF, mTileBgHlInactive = 0x19FFFFFF;
    private int mTileBgMediaColor = 0x19FFFFFF;
    private Integer mTileViewFlagActiveAttr;

    private boolean mTileBorderOn;
    private int mTileBorderColor = 0xFF908DFF;

    private boolean mPanelBorderOn;
    private int mPanelBorderColor = 0xFF908DFF;

    private boolean mMediaCoverFilterOn;
    private int mMediaCoverFilter = COVER_FILTER_NONE;
    private float mMediaCoverBlurRadius = 7.5f;
    /** Un ImageView-sfondo iniettato per istanza di pannello Media — mostra la vera copertina
     *  a grandezza intera invece del piccolo coverImg nativo (richiesta utente 2026-08-20). */
    private final WeakHashMap<Object, ImageView> mMediaCoverBackdrop = new WeakHashMap<>();
    private final WeakHashMap<Object, View> mMediaBorderView = new WeakHashMap<>();

    private int mTileRadiusBaseDp = 20, mTileRadiusHlDp = 20, mTileRadiusMediaDp = 20;

    private Object mPersonalityManager;
    private Class<?> mForegroundBlurParamClass;
    private final List<Object> mSeekBarInstances = new ArrayList<>();

    // Mitigazione BUG 2026-08-29: qualcosa (probabilmente il "Personality"/color-from-wallpaper
    // di OOS, non ancora identificato con certezza) sovrascrive di nuovo il tint di default
    // POCO DOPO che il nostro hook lo ha già colorato correttamente — confermato via log: 100+
    // applyIconColorTo "APPLIED" durante il burst iniziale di binding dopo un riavvio SystemUI,
    // eppure lo screenshot qualche secondo più tardi mostra di nuovo i colori stock. Finché non
    // si trova il vero punto che sovrascrive, si vince "per ultimo" riapplicando i colori un
    // paio di volte con un piccolo ritardo dopo che il burst di binding si è calmato (ogni
    // setIcon() riprogramma il timer, quindi scatta solo quando i tocchi si fermano davvero).
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Runnable mDelayedRefresh1 = this::refreshCachedIconColors;
    private final Runnable mDelayedRefresh2 = this::refreshCachedIconColors;

    public QsTilesCustomizeMod(Context context) { super(context); }

    @Override
    public void updatePrefs(String... key) {
        if (Xprefs == null) return;

        mIconColorsOn = Xprefs.getBoolean(KEY_ICON_COLORS_ON, false);
        mIconActiveAccent = Xprefs.getBoolean(KEY_ICON_ACTIVE_ACCENT, false);
        mIconActive = Xprefs.getInt(KEY_ICON_ACTIVE, 0xFFFFFFFF);
        mIconInactive = Xprefs.getInt(KEY_ICON_INACTIVE, 0xFFFFFFFF);
        mIconDisabled = Xprefs.getInt(KEY_ICON_DISABLED, 0xFFFFFFFF);

        mHideLabels = Xprefs.getBoolean(KEY_HIDE_LABELS, false);
        mLabelColorOn = Xprefs.getBoolean(KEY_LABEL_COLOR_ON, false);
        mLabelColor = Xprefs.getBoolean(KEY_LABEL_COLOR + "_use_accent", false)
                ? appAccentColor() : Xprefs.getInt(KEY_LABEL_COLOR, 0xFFFFFFFF);

        mBrightnessIconMode = parseInt(Xprefs.getString(KEY_BRIGHTNESS_ICON_MODE, "0"), 0);
        mBrightnessIconColor = Xprefs.getInt(KEY_BRIGHTNESS_ICON_COLOR, 0xFFFFFFFF);
        mVolumeIconMode = parseInt(Xprefs.getString(KEY_VOLUME_ICON_MODE, "0"), 0);
        mVolumeIconColor = Xprefs.getInt(KEY_VOLUME_ICON_COLOR, 0xFFFFFFFF);
        mBrightnessCustomOn = Xprefs.getBoolean(KEY_BRIGHTNESS_CUSTOM_ON, false);
        mBrightnessMode = parseInt(Xprefs.getString(KEY_BRIGHTNESS_MODE, "0"), 0);
        mBrightnessColor = Xprefs.getInt(KEY_BRIGHTNESS_COLOR, appAccentColor());
        mBrightnessBgOn = Xprefs.getBoolean(KEY_BRIGHTNESS_BG_ON, false);
        mBrightnessBgColor = Xprefs.getInt(KEY_BRIGHTNESS_BG_COLOR, 0x00000000);

        mRadiusOn = Xprefs.getBoolean(KEY_RADIUS_ON, false);
        mRadiusDp = Xprefs.getInt(KEY_RADIUS, 20);

        mSlidersOn = Xprefs.getBoolean(KEY_SLIDERS_ON, true);
        if (!mSlidersOn) {
            // Master spento: azzera tutti i sotto-interruttori invece di sparpagliare il
            // controllo negli hook — ogni opzione torna al comportamento nativo di OOS.
            mBrightnessIconMode = 0;
            mVolumeIconMode = 0;
            mBrightnessCustomOn = false;
            mBrightnessBgOn = false;
            mRadiusOn = false;
        }

        mAnimStyle = parseInt(Xprefs.getString(KEY_ANIM_STYLE, "0"), 0);
        mAnimDuration = Xprefs.getInt(KEY_ANIM_DURATION, 1);
        mAnimInterpolator = parseInt(Xprefs.getString(KEY_ANIM_INTERPOLATOR, "0"), 0);
        mTransitionsOn = Xprefs.getBoolean(KEY_TRANSITIONS_ON, false);
        mTransitionStyle = parseInt(Xprefs.getString(KEY_TRANSITIONS, "0"), 0);

        mTileBgBaseOn = Xprefs.getBoolean(KEY_TILE_BG_BASE_ON, false);
        mTileBgBaseActive = Xprefs.getInt(KEY_TILE_BG_BASE_ACTIVE, 0xFF908DFF);
        mTileBgBaseInactive = Xprefs.getInt(KEY_TILE_BG_BASE_INACTIVE, 0x19FFFFFF);
        mTileBgBaseAccent = Xprefs.getBoolean(KEY_TILE_BG_BASE_ACCENT, false);
        mTileBgHighlightOn = Xprefs.getBoolean(KEY_TILE_BG_HL_ON, false);
        mTileBgHlActive = Xprefs.getInt(KEY_TILE_BG_HL_ACTIVE, 0xFF908DFF);
        mTileBgHlInactive = Xprefs.getInt(KEY_TILE_BG_HL_INACTIVE, 0x19FFFFFF);
        mTileBgHlAccent = Xprefs.getBoolean(KEY_TILE_BG_HL_ACCENT, false);
        mTileBgMediaOn = Xprefs.getBoolean(KEY_TILE_BG_MEDIA_ON, false);
        mTileBgMediaColor = Xprefs.getBoolean(KEY_TILE_BG_MEDIA_COLOR + "_use_accent", false)
                ? appAccentColor() : Xprefs.getInt(KEY_TILE_BG_MEDIA_COLOR, 0x19FFFFFF);

        mTileBorderOn = Xprefs.getBoolean(KEY_TILE_BORDER_ON, false);
        mTileBorderColor = Xprefs.getBoolean(KEY_TILE_BORDER_COLOR + "_use_accent", false)
                ? appAccentColor() : Xprefs.getInt(KEY_TILE_BORDER_COLOR, 0xFF908DFF);

        mPanelBorderOn = Xprefs.getBoolean(KEY_PANEL_BORDER_ON, false);
        mPanelBorderColor = Xprefs.getBoolean(KEY_PANEL_BORDER_COLOR + "_use_accent", false)
                ? appAccentColor() : Xprefs.getInt(KEY_PANEL_BORDER_COLOR, 0xFF908DFF);

        mMediaCoverFilterOn = Xprefs.getBoolean(KEY_MEDIA_COVER_FILTER_ON, false);
        mMediaCoverFilter = parseInt(Xprefs.getString(KEY_MEDIA_COVER_FILTER, "0"), COVER_FILTER_NONE);
        mMediaCoverBlurRadius = (Xprefs.getInt(KEY_MEDIA_COVER_BLUR, 30) / 100f) * 25f;

        mTileRadiusBaseDp = Xprefs.getInt(KEY_TILE_RADIUS_BASE, 20);
        mTileRadiusHlDp = Xprefs.getInt(KEY_TILE_RADIUS_HL, 20);
        mTileRadiusMediaDp = Xprefs.getInt(KEY_TILE_RADIUS_MEDIA, 20);

        notifyQsUpdate();
        refreshCachedIconColors();
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Throwable t) { return def; }
    }

    private int appAccentColor() {
        if (Xprefs.getBoolean("DST_ACCENT1_on", false)) {
            return Xprefs.getInt("DST_ACCENT1", 0xFF908DFF) | 0xFF000000;
        }
        return 0xFF908DFF;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!SYSTEM_UI.equals(lp.packageName)) return;

        // Serve solo per forzare un refresh immediato del pannello dopo un cambio pref,
        // invece di aspettare il prossimo naturale (tocco su un riquadro, ecc.).
        Class<?> personalityManager = tryFindClass(lp,
                "com.oplus.systemui.qs.personality.PersonalityManager",
                "com.oplusos.systemui.qs.personality.PersonalityManager");
        if (personalityManager != null) {
            hookAllConstructors(personalityManager, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { mPersonalityManager = p.thisObject; }
            });
        }

        hookIconColors(lp);
        hookLabels(lp);
        hookLabelsClassic(lp);
        hookBrightnessSliderColor(lp);
        hookSliderBorder(lp);
        hookBrightnessIcon(lp);
        hookVolumeSliderIcon(lp);
        hookSliderBlur(lp);
        hookSliderRadius(lp);
        hookTileAnimation(lp);
        hookTileTransitions(lp);
        hookTileDrawableOwnership(lp);
        hookMediaPanelOwnership(lp);
        hookMediaBorder(lp);
        hookPanelBorder(lp);
        hookMediaCoverFilter(lp);
        hookTileBgBase(lp);
        hookTileBgHighlight(lp);
        hookTileRadius(lp);
    }

    // ── DIAGNOSTICA: sfondo riquadri (Riquadri base / Riquadri in evidenza) ──
    // Non ancora una feature vera — solo per scoprire nomi reali di classi/campi/metodi su
    // QUESTA build prima di scrivere la logica finale. Il meccanismo di OC (BaseTileViewBackground
    // / updateBackground(int,boolean,boolean) / initializeBackgroundProxy) NON esiste su questa
    // build OOS16 (confermato: 0 metodi trovati con quei nomi, su tutte le classi candidate).
    // Nuovo tentativo: i metodi realmente dichiarati su queste classi (visti nel dump precedente)
    // suggeriscono un pattern diverso — onDrawableUpdate / onQsColorStateChanged / getBackgroundView
    // / onStateChanged — verifichiamo quali esistono davvero e quando si attivano.
    private void diagHookMethod(Class<?> cls, String methodName) {
        try {
            java.util.Set<?> hooked = hookAllMethods(cls, methodName, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Object result = p.getResult();
                    dbg("DIAG " + p.thisObject.getClass().getSimpleName() + "." + methodName + " FIRED, args="
                            + java.util.Arrays.toString(p.args)
                            + " result=" + (result != null ? (result.getClass().getName() + "=" + result) : "null"));
                }
            });
            dbg("DIAG " + cls.getSimpleName() + "." + methodName + " matched=" + hooked.size() + " method(s)");
        } catch (Throwable t) { dbg("DIAG " + cls.getSimpleName() + "." + methodName + " hook threw: " + t); }
    }

    // GradientTileDrawable e MixColorTileDrawable sono classi GENERICHE riusate anche dal
    // riquadro Media (SepQSResPool.mediaPanelDrawable, OplusQsBaseMediaPanelView.onDrawableUpdate)
    // — senza distinzione, il nostro hook su onStateChange coloriva pure quello (bug segnalato
    // dall'utente: "si applica anche il media, prende inattivo di Riquadri in evidenza"). Fix:
    // al momento della creazione (Builder.build(View,boolean)), risaliamo l'albero dei parent
    // della View passata per vedere a quale vista reale appartiene — riquadro normale
    // (OplusQSResizeableTileViewOneXOne/TwoXOne) o pannello Media (OplusQsBaseMediaPanelView).
    // Il risultato ("tile"/"media"/null) si marca in una WeakHashMap chiave=istanza drawable,
    // controllata poi in onStateChange prima di toccare qualunque mappa colori.
    private static final String OWNER_TILE = "tile";
    private static final String OWNER_MEDIA = "media";
    private final java.util.Map<Object, String> mGradientOwner = new WeakHashMap<>();
    private final java.util.Map<Object, String> mMixColorOwner = new WeakHashMap<>();

    // Costruttore riflesso per kotlin.Triple, popolato da hookTileBgHighlight() e riusato da
    // hookTileDrawableOwnership() (vedi sotto, 2026-09-17: applichiamo la riscrittura colore anche
    // subito dopo la costruzione del drawable, non solo alla transizione di stato onStateChange —
    // quella non scatta mai per i riquadri il cui stato non cambia mai dopo il boot).
    private java.lang.reflect.Constructor<?> mMixTripleCtor;

    // La View "bg" del pannello Media (OplusQsBaseMediaPanelView.getBg()) risulta un
    // android.view.View "nudo", mai davvero aggiunto alla gerarchia reale (probabilmente creato
    // solo come contenitore per il tag del drawable via ViewTagUtils) — risalire i parent non
    // trova mai OplusQsBaseMediaPanelView (confermato via log: sempre owner=null per quelle
    // build()). Fix: OplusQsBaseMediaPanelView.onDrawableUpdate(boolean) chiama i builder in
    // modo sincrono al suo interno — un ThreadLocal impostato all'inizio/fine di quella singola
    // chiamata marca correttamente le build() che avvengono nel suo stack, senza dipendere
    // dalla gerarchia della View.
    private final ThreadLocal<Boolean> mInMediaOnDrawableUpdate = ThreadLocal.withInitial(() -> false);

    private String ownerOfView(Object view) {
        if (Boolean.TRUE.equals(mInMediaOnDrawableUpdate.get())) return OWNER_MEDIA;
        Object v = view;
        for (int i = 0; i < 12 && v != null; i++) {
            String cn = v.getClass().getName();
            if (cn.equals("com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewOneXOne")
                    || cn.equals("com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewTwoXOne")
                    // Equivalenti "Classico" (non-Separati) delle due classi sopra — stessa
                    // gerarchia GradientTileDrawable/MixColorTileDrawable, via StdQSResPool
                    // invece di SepQSResPool (OplusQSTileBaseView/OplusQSHighlightTileView,
                    // onDrawableUpdate reale confermato nel sorgente decompilato, 2026-08-20).
                    || cn.equals("com.oplus.systemui.qs.tileimpl.OplusQSTileViewImpl")
                    || cn.equals("com.oplus.systemui.qs.tileimpl.OplusQSHighlightTileViewImpl")) {
                return OWNER_TILE;
            }
            if (cn.equals("com.oplus.systemui.qs.media.OplusQsBaseMediaPanelView")) {
                return OWNER_MEDIA;
            }
            try { v = callMethod(v, "getParent"); } catch (Throwable t) { break; }
        }
        return null;
    }

    private void hookTileDrawableOwnership(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> gradBuilder = tryFindClass(lp, "com.oplus.systemui.qs.base.res.drawable.GradientTileDrawable$Builder");
        if (gradBuilder != null) {
            try {
                hookAllMethods(gradBuilder, "build", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        Object view = p.args.length > 0 ? p.args[0] : null;
                        Object result = p.getResult();
                        if (view != null && result != null) mGradientOwner.put(result, ownerOfView(view));
                    }
                });
            } catch (Throwable t) { dbg("hookTileDrawableOwnership GradientTileDrawable.Builder failed: " + t); }
        }
        Class<?> mixBuilder = tryFindClass(lp, "com.oplus.systemui.qs.base.res.drawable.MixColorTileDrawable$Builder");
        if (mixBuilder != null) {
            try {
                hookAllMethods(mixBuilder, "build", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        Object view = p.args.length > 0 ? p.args[0] : null;
                        Object result = p.getResult();
                        if (view == null || result == null) return;
                        String owner = ownerOfView(view);
                        mMixColorOwner.put(result, owner);
                        // Applica il colore SUBITO alla costruzione, non solo alla prossima
                        // onStateChange — quella non scatta mai per i riquadri il cui stato non
                        // cambia mai dopo il boot. Il bordo è gestito a parte (draw() disegnato a
                        // mano, vedi hookTileBgHighlight) quindi non dipende da questa chiamata.
                        try { applyMixColorStateOverrides(result, owner, lp); } catch (Throwable ignored) {}
                    }
                });
            } catch (Throwable t) { dbg("hookTileDrawableOwnership MixColorTileDrawable.Builder failed: " + t); }
        }
    }

    // Marca l'inizio/fine di OplusQsBaseMediaPanelView.onDrawableUpdate(boolean) — i build()
    // chiamati sincronamente al suo interno vengono così etichettati "media" anche se la View
    // passata non risale a nessun parent riconoscibile (vedi nota sopra su mInMediaOnDrawableUpdate).
    private void hookMediaPanelOwnership(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> mediaCls = tryFindClass(lp, "com.oplus.systemui.qs.media.OplusQsBaseMediaPanelView");
        if (mediaCls == null) return;
        try {
            hookAllMethods(mediaCls, "onDrawableUpdate", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) { mInMediaOnDrawableUpdate.set(true); }
                @Override protected void afterHookedMethod(MethodHookParam p) { mInMediaOnDrawableUpdate.set(false); }
            });
        } catch (Throwable t) { dbg("hookMediaPanelOwnership failed: " + t); }
    }

    /** Bordo Media — DUE tentativi via canvas-hook (sul drawable, poi su View.draw()) sono rimasti
     *  costantemente disallineati di qualche dp nonostante bounds/path nativi risultassero
     *  perfettamente combacianti via diagnostica — il blur in tempo reale (autoBlurDrawable)
     *  probabilmente ridisegna offscreen più volte per frame, e il nostro hook generico su
     *  View.draw() finiva per agganciare la passata sbagliata. Fix definitivo: una vera View
     *  trasparente aggiunta come ULTIMO figlio della card (stessa tecnica, già affidabile, usata
     *  per la copertina album qui sotto) — fa parte della gerarchia normale, quindi si allinea
     *  SEMPRE correttamente, nessun calcolo di bounds/offset da indovinare. */
    private void hookMediaBorder(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> mediaCls = tryFindClass(lp, "com.oplus.systemui.qs.media.OplusQsBaseMediaPanelView");
        if (mediaCls == null) return;
        try {
            hookAllMethods(mediaCls, "onDrawableUpdate", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try { syncMediaBorderView(p.thisObject); } catch (Throwable t) { dbg("syncMediaBorderView failed: " + t); }
                }
            });
        } catch (Throwable t) { dbg("hookMediaBorder install failed: " + t); }
    }

    private void syncMediaBorderView(Object panel) {
        if (!(panel instanceof ViewGroup)) return;
        View border = getOrCreateMediaBorderView(panel);
        if (border == null) return;
        border.bringToFront();
        // Con la copertina attiva il bordo va nascosto (richiesta utente 2026-09-17), il resto del
        // tempo segue solo lo switch "Bordo Pulsanti".
        ImageView backdrop = mMediaCoverBackdrop.get(panel);
        boolean coverActive = backdrop != null && backdrop.getVisibility() == View.VISIBLE;
        border.setVisibility(mTileBorderOn && !coverActive ? View.VISIBLE : View.GONE);
        border.invalidate();
    }

    private View getOrCreateMediaBorderView(Object panel) {
        View existing = mMediaBorderView.get(panel);
        if (existing != null) return existing;
        ViewGroup group = (ViewGroup) panel;
        View v = new View(mContext) {
            @Override protected void onDraw(android.graphics.Canvas canvas) {
                super.onDraw(canvas);
                float strokeWidth = tileBorderWidthPx();
                float inset = strokeWidth / 2f;
                android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                paint.setStyle(android.graphics.Paint.Style.STROKE);
                paint.setStrokeWidth(strokeWidth);
                paint.setColor(mTileBorderColor);
                canvas.drawRoundRect(inset, inset, getWidth() - inset, getHeight() - inset,
                        dp(mTileRadiusMediaDp), dp(mTileRadiusMediaDp), paint);
            }
        };
        v.setWillNotDraw(false);
        group.addView(v, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mMediaBorderView.put(panel, v);
        return v;
    }

    /** Bordo Pannello QS (2026-09-18) — 4a superficie della richiesta 09-16. Stesso contenitore
     *  già usato da QsBackground.java per lo sfondo (OplusQSContainerImpl/OplusQSRootView per
     *  split/landscape), ma qui aggiungiamo una vera View come ULTIMO figlio (non indice 0 come
     *  il tint di sfondo — deve stare SOPRA il contenuto per non finire coperta dai riquadri),
     *  stessa tecnica proven-affidabile della View reale già usata per Media sopra. Raggio
     *  angoli non misurato (nessun tempo per jadx su questo, a differenza degli altri bordi in
     *  questo file) — valore ragionevole scelto a occhio, da aggiustare se il risultato reale
     *  non combacia col bordo arrotondato nativo del pannello.
     */
    private final java.util.Map<Object, View> mPanelBorderView = new WeakHashMap<>();
    private static final int PANEL_BORDER_RADIUS_DP = 32;

    private void hookPanelBorder(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> containerCls = tryFindClass(lp, "com.oplus.systemui.qs.OplusQSContainerImpl");
        if (containerCls == null) containerCls = tryFindClass(lp, "com.oplusos.systemui.qs.OplusQSContainerImpl");
        if (containerCls != null) {
            try {
                hookAllMethods(containerCls, "onFinishInflate", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (p.thisObject instanceof ViewGroup) syncPanelBorderView((ViewGroup) p.thisObject);
                    }
                });
            } catch (Throwable t) { dbg("hookPanelBorder OplusQSContainerImpl failed: " + t); }
        }
        Class<?> rootViewCls = tryFindClass(lp, "com.oplus.systemui.plugins.qs.OplusQSRootView");
        if (rootViewCls != null) {
            try {
                hookAllMethods(rootViewCls, "onFinishInflate", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (p.thisObject instanceof ViewGroup) syncPanelBorderView((ViewGroup) p.thisObject);
                    }
                });
            } catch (Throwable t) { dbg("hookPanelBorder OplusQSRootView failed: " + t); }
        }
        // Con i pannelli "Separati" il pannello notifiche e' una vista a parte (non contiene
        // OplusQSContainerImpl/RootView): stesso bordo anche li' — 2026-09-19, richiesta utente.
        Class<?> notifPanelCls = tryFindClass(lp, "com.android.systemui.shade.NotificationPanelView");
        if (notifPanelCls != null) {
            try {
                hookAllConstructors(notifPanelCls, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (!(p.thisObject instanceof ViewGroup)) return;
                        final ViewGroup g = (ViewGroup) p.thisObject;
                        g.post(() -> { try { syncPanelBorderView(g, true); } catch (Throwable t) { dbg("notif panel border failed: " + t); } });
                    }
                });
            } catch (Throwable t) { dbg("hookPanelBorder NotificationPanelView failed: " + t); }
        }
    }

    private void syncPanelBorderView(ViewGroup container) { syncPanelBorderView(container, false); }

    /** hideOnKeyguard=true per il pannello notifiche (NotificationPanelView ospita anche la
     *  lockscreen: li' il bordo non deve comparire). */
    private void syncPanelBorderView(ViewGroup container, boolean hideOnKeyguard) {
        View existing = mPanelBorderView.get(container);
        if (existing == null) {
            View v = new View(mContext) {
                @Override protected void onDraw(android.graphics.Canvas canvas) {
                    super.onDraw(canvas);
                    if (!mPanelBorderOn) return;
                    if (hideOnKeyguard) {
                        try {
                            android.app.KeyguardManager km = getContext().getSystemService(android.app.KeyguardManager.class);
                            if (km != null && km.isKeyguardLocked()) return;
                        } catch (Throwable ignored) {}
                    }
                    float strokeWidth = tileBorderWidthPx();
                    float inset = strokeWidth / 2f;
                    android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                    paint.setStyle(android.graphics.Paint.Style.STROKE);
                    paint.setStrokeWidth(strokeWidth);
                    paint.setColor(mPanelBorderColor);
                    canvas.drawRoundRect(inset, inset, getWidth() - inset, getHeight() - inset,
                            dp(PANEL_BORDER_RADIUS_DP), dp(PANEL_BORDER_RADIUS_DP), paint);
                }
            };
            v.setWillNotDraw(false);
            container.addView(v, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            mPanelBorderView.put(container, v);
            existing = v;
        } else {
            existing.bringToFront();
        }
        existing.invalidate();
    }

    // Copertina Album: bindCoverImg(MediaData) è dove OOS imposta l'artwork reale del brano sul
    // piccolo COUIRoundImageView coverImg nativo. L'utente vuole la copertina grande quanto il
    // riquadro Media (non solo ricolorato in piccolo) — iniettiamo un ImageView a piena
    // dimensione subito sopra il layer di sfondo (getBg()) e sotto testo/controlli, nascondendo
    // il coverImg nativo quando abbiamo una vera artwork da mostrare.
    private void hookMediaCoverFilter(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> mediaCls = tryFindClass(lp, "com.oplus.systemui.qs.media.OplusQsBaseMediaPanelView");
        if (mediaCls == null) return;
        try {
            hookAllMethods(mediaCls, "bindCoverImg", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        applyMediaCover(p.thisObject, p.args.length > 0 ? p.args[0] : null);
                        syncMediaBorderView(p.thisObject);
                    } catch (Throwable t) { dbg("media cover apply failed: " + t); }
                }
            });
        } catch (Throwable t) { dbg("hookMediaCoverFilter install failed: " + t); }
    }

    private void applyMediaCover(Object panel, Object mediaData) {
        Object coverImgObj = callMethod(panel, "getCoverImg");
        ImageView coverImg = coverImgObj instanceof ImageView ? (ImageView) coverImgObj : null;

        if (!mMediaCoverFilterOn) {
            ImageView backdrop = mMediaCoverBackdrop.get(panel);
            if (backdrop != null) backdrop.setVisibility(View.GONE);
            return;
        }

        Bitmap bmp = null;
        if (mediaData != null) {
            Object artworkObj = callMethod(mediaData, "getArtwork");
            if (artworkObj instanceof Icon) {
                Drawable d = ((Icon) artworkObj).loadDrawable(mContext);
                if (d != null) bmp = DrawableConverter.drawableToBitmap(d);
            }
        }
        if (bmp == null) {
            // Nessuna vera artwork per questa traccia: lascia il fallback nativo di OOS visibile.
            ImageView backdrop = mMediaCoverBackdrop.get(panel);
            if (backdrop != null) backdrop.setVisibility(View.GONE);
            return;
        }

        Bitmap filtered = applyMediaCoverFilter(bmp);
        ImageView backdrop = getOrCreateMediaCoverBackdrop(panel);
        if (backdrop == null) return;
        backdrop.setImageBitmap(filtered);
        backdrop.setVisibility(View.VISIBLE);
        if (coverImg != null) coverImg.setVisibility(View.GONE);
    }

    private ImageView getOrCreateMediaCoverBackdrop(Object panel) {
        ImageView existing = mMediaCoverBackdrop.get(panel);
        if (existing != null) return existing;
        if (!(panel instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) panel;
        ImageView iv = new ImageView(mContext);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(mTileRadiusMediaDp));
            }
        });
        iv.setClipToOutline(true);
        int insertIndex = 0;
        try {
            Object bgView = callMethod(panel, "getBg");
            if (bgView instanceof View) {
                int idx = group.indexOfChild((View) bgView);
                if (idx >= 0) insertIndex = idx + 1;
            }
        } catch (Throwable ignored) {}
        group.addView(iv, insertIndex, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mMediaCoverBackdrop.put(panel, iv);
        return iv;
    }

    private Bitmap applyMediaCoverFilter(Bitmap art) {
        try {
            switch (mMediaCoverFilter) {
                case COVER_FILTER_GRAYSCALE: return DrawableConverter.toGrayscale(art);
                case COVER_FILTER_ACCENT:
                    return DrawableConverter.getColoredBitmap(
                            new BitmapDrawable(mContext.getResources(), art), appAccentColor());
                case COVER_FILTER_BLUR: return DrawableConverter.getBlurredImage(mContext, art, mMediaCoverBlurRadius);
                case COVER_FILTER_GRAY_BLUR: return DrawableConverter.getGrayscaleBlurredImage(mContext, art, mMediaCoverBlurRadius);
                default: return art;
            }
        } catch (Throwable t) {
            dbg("applyMediaCoverFilter failed: " + t);
            return art;
        }
    }

    /** true se lo stateSpec di una DrawableState contiene il flag "Active" (R.attr.state_active).
     *  attr trovato una volta sola e messo in cache in mTileViewFlagActiveAttr. Usato per Media
     *  (che ha sempre un solo colore, quindi non serve distinguere disabilitato). */
    private boolean isActiveDrawableState(XC_LoadPackage.LoadPackageParam lp, Object drawableStateKey) {
        try {
            if (mTileViewFlagActiveAttr == null) {
                Class<?> tileViewFlagCls = tryFindClass(lp, "com.oplus.systemui.qs.base.res.model.TileViewFlag");
                Object activeFlag = getStaticObjectField(tileViewFlagCls, "Active");
                mTileViewFlagActiveAttr = (int) callMethod(activeFlag, "getAttr");
            }
            int[] stateSpec = (int[]) callMethod(drawableStateKey, "getStateSpec");
            for (int s : stateSpec) if (s == mTileViewFlagActiveAttr) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    // Sfondo "Riquadri base" (rettangolari — Wi-Fi, Torcia, media, ecc.): OplusQSResizeableTileView
    // OneXOne/TwoXOne.onDrawableUpdate() chiama SepQSResPool.getTileViewDrawable().getValue()
    // .build(getBg(), true) → GradientTileDrawable, il cui onStateChange(int[]) calcola il colore
    // finale per lo stato corrente in una mappa "stateListColors" (DrawableState -> Integer) e lo
    // applica a un ColorDrawable interno. Riscriviamo la mappa PRIMA che onStateChange la consumi
    // (non il ColorDrawable dopo — viene risovrascritto ad ogni relayout/animazione del pannello).
    private int tileBorderWidthPx() {
        return Math.round(TILE_BORDER_WIDTH_DP * mContext.getResources().getDisplayMetrics().density);
    }

    private void hookTileBgBase(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> gradientTileDrawableCls = tryFindClass(lp, "com.oplus.systemui.qs.base.res.drawable.GradientTileDrawable");
        if (gradientTileDrawableCls == null) return;
        try {
            hookAllMethods(gradientTileDrawableCls, "onStateChange", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    String owner = mGradientOwner.get(p.thisObject);
                    int active, inactive;
                    if (OWNER_TILE.equals(owner) && mTileBgBaseOn) {
                        active = mTileBgBaseAccent ? appAccentColor() : mTileBgBaseActive;
                        inactive = mTileBgBaseInactive;
                    } else if (OWNER_MEDIA.equals(owner) && mTileBgMediaOn) {
                        active = inactive = mTileBgMediaColor;
                    } else return;
                    try {
                        Object stateListColors = getObjectField(p.thisObject, "stateListColors");
                        if (!(stateListColors instanceof java.util.Map)) return;
                        java.util.Map<Object, Object> map = (java.util.Map<Object, Object>) stateListColors;
                        for (Object key : map.keySet()) {
                            map.put(key, isActiveDrawableState(lp, key) ? active : inactive);
                        }
                    } catch (Throwable t) { dbg("hookTileBgBase override failed: " + t); }
                }
            });
        } catch (Throwable t) { dbg("hookTileBgBase install failed: " + t); }
        // Bordo "Riquadri base" — GradientTileDrawable non ha un meccanismo di stroke nativo (solo
        // un campo "paint" per il riempimento, confermato via reflection), quindi lo disegniamo a
        // mano SOPRA il riempimento nativo (afterHookedMethod su draw(Canvas), stesso Canvas) —
        // stesso pattern già usato per "Bordo barra" in VolumePanelMod. getCornerRadius() è un
        // metodo reale della classe base TileDrawableWrapper (confermato via reflection).
        try {
            hookAllMethods(gradientTileDrawableCls, "draw", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    // Media NON passa più di qui (vedi hookMediaBorder più sotto: il bordo sullo
                    // sfondo veniva coperto dalla copertina album, una View sopra) — solo pulsanti.
                    if (!mTileBorderOn || !OWNER_TILE.equals(mGradientOwner.get(p.thisObject))) return;
                    try {
                        android.graphics.Canvas canvas = (android.graphics.Canvas) p.args[0];
                        android.graphics.Rect bounds = ((Drawable) p.thisObject).getBounds();
                        float radius = 0f;
                        try { radius = (float) callMethod(p.thisObject, "getCornerRadius"); } catch (Throwable ignored) {}
                        float strokeWidth = tileBorderWidthPx();
                        float inset = strokeWidth / 2f;
                        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                        paint.setStyle(android.graphics.Paint.Style.STROKE);
                        paint.setStrokeWidth(strokeWidth);
                        paint.setColor(mTileBorderColor);
                        canvas.drawRoundRect(bounds.left + inset, bounds.top + inset,
                                bounds.right - inset, bounds.bottom - inset, radius, radius, paint);
                    } catch (Throwable t) { dbg("tile border draw failed: " + t); }
                }
            });
        } catch (Throwable t) { dbg("hookTileBgBase border install failed: " + t); }
    }

    // Sfondo "Riquadri in evidenza" (circolari — Posizione, Bluetooth, ecc.): usano
    // MixColorTileDrawable invece di GradientTileDrawable, con una mappa "stateListConfig"
    // (DrawableState -> kotlin.Triple<BlurMixConfig, Integer maskColor, StrokeParamsTemplate>).
    // Il maskColor (2° elemento) si disegna come overlay sopra il blur in draw() — con alpha piena
    // copre completamente il blur sottostante. Stessa tecnica: riscriviamo solo il maskColor,
    // lasciando invariati blur config e stroke template, prima che onStateChange consumi la mappa.
    private void hookTileBgHighlight(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> mixColorCls = tryFindClass(lp, "com.oplus.systemui.qs.base.res.drawable.MixColorTileDrawable");
        if (mixColorCls == null) return;
        Class<?> tripleCls = tryFindClass(lp, "kotlin.Triple");
        if (tripleCls == null) return;
        try {
            mMixTripleCtor = tripleCls.getConstructor(Object.class, Object.class, Object.class);
            hookAllMethods(mixColorCls, "onStateChange", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try { applyMixColorStateOverrides(p.thisObject, mMixColorOwner.get(p.thisObject), lp); }
                    catch (Throwable t) { dbg("hookTileBgHighlight override failed: " + t); }
                }
            });
            // Bordo "Riquadri in evidenza" — il meccanismo nativo (3° elemento della Triple, un vero
            // GradientStrokeLineAdapter$StrokeParamsTemplate) è stato provato ma abbandonato 2026-09-17:
            // diagnostica via reflection ha confermato che il campo strokeParamsTemplate risultava
            // corretto (colore giusto, lineWidth=5) su TUTTI i riquadri controllati, inclusi quelli
            // che visivamente non mostravano alcun bordo — draw() nativo lo ignora per un motivo mai
            // trovato (probabilmente una condizione interna legata al tipo/dimensione del riquadro,
            // non investigabile senza decompilare, jadx non disponibile su questa macchina). Stessa
            // soluzione già usata per "Riquadri base" (GradientTileDrawable, vedi hookTileBgBase sopra):
            // disegnato a mano, SOPRA il draw nativo — niente dipendenza da un meccanismo nascosto.
            hookAllMethods(mixColorCls, "draw", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    // Media NON passa più di qui (vedi hookMediaBorder più sotto: il bordo sullo
                    // sfondo veniva coperto dalla copertina album, una View sopra) — solo pulsanti.
                    if (!mTileBorderOn || !OWNER_TILE.equals(mMixColorOwner.get(p.thisObject))) return;
                    try {
                        android.graphics.Canvas canvas = (android.graphics.Canvas) p.args[0];
                        android.graphics.Rect bounds = ((Drawable) p.thisObject).getBounds();
                        float strokeWidth = tileBorderWidthPx();
                        float inset = strokeWidth / 2f;
                        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                        paint.setStyle(android.graphics.Paint.Style.STROKE);
                        paint.setStrokeWidth(strokeWidth);
                        paint.setColor(mTileBorderColor);
                        // Le card larghe 2 colonne (Wi-Fi/Torcia/Pixolor/Riavvia) condividono la
                        // STESSA classe anche per l'intera card (bounds ~414x186, non solo l'icona
                        // ~186x186), quindi un'ellisse sarebbe schiacciata — lì disegniamo un bordo
                        // arrotondato (pill) via getCornerRadius() invece, come per GradientTileDrawable.
                        if (bounds.width() > bounds.height() * 1.3f) {
                            float radius = 0f;
                            try { radius = (float) callMethod(p.thisObject, "getCornerRadius"); } catch (Throwable ignored) {}
                            canvas.drawRoundRect(bounds.left + inset, bounds.top + inset,
                                    bounds.right - inset, bounds.bottom - inset, radius, radius, paint);
                        } else {
                            canvas.drawOval(bounds.left + inset, bounds.top + inset,
                                    bounds.right - inset, bounds.bottom - inset, paint);
                        }
                    } catch (Throwable t) { dbg("mixcolor tile border draw failed: " + t); }
                }
            });
        } catch (Throwable t) { dbg("hookTileBgHighlight install failed: " + t); }
    }

    /** Riscrive stateListConfig (solo colore) per un MixColorTileDrawable — condiviso tra
     *  onStateChange (transizioni successive) e Builder.build() (subito alla costruzione, per i
     *  riquadri il cui stato non cambia mai dopo il boot). Il bordo NON passa più di qui, vedi
     *  commento sopra il draw() hook in hookTileBgHighlight. */
    private void applyMixColorStateOverrides(Object drawable, String owner, XC_LoadPackage.LoadPackageParam lp) {
        boolean doColor = false;
        int active = 0, inactive = 0;
        if (OWNER_TILE.equals(owner) && mTileBgHighlightOn) {
            doColor = true;
            active = mTileBgHlAccent ? appAccentColor() : mTileBgHlActive;
            inactive = mTileBgHlInactive;
        } else if (OWNER_MEDIA.equals(owner) && mTileBgMediaOn) {
            doColor = true;
            active = inactive = mTileBgMediaColor;
        }
        if (!doColor) return;
        try {
            Object stateListConfig = getObjectField(drawable, "stateListConfig");
            if (!(stateListConfig instanceof java.util.Map)) return;
            java.util.Map<Object, Object> map = (java.util.Map<Object, Object>) stateListConfig;
            for (java.util.Map.Entry<Object, Object> e : map.entrySet()) {
                Object triple = e.getValue();
                Object first = callMethod(triple, "getFirst");
                Object third = callMethod(triple, "getThird");
                Object second = isActiveDrawableState(lp, e.getKey()) ? active : inactive;
                e.setValue(mMixTripleCtor.newInstance(first, second, third));
            }
        } catch (Throwable t) { dbg("applyMixColorStateOverrides failed: " + t); }
    }

    // Raggio angoli riquadri: SepQSResPool/StdQSResPool.updateTileOutline/updateHighLightTileOutline/
    // updateMediaPanelOutline ricevono ciascuno un CornerOutlineProvider (in pratica quasi sempre
    // un RoundRectOutlineProvider reale, confermato nel sorgente decompilato) con un metodo
    // update(float) mutabile — riscrive sia il raggio scalare (usato per il ViewOutline/ombra) sia
    // l'array cornerRadii (usato per il path del bordo/riempimento). Qui, a differenza del colore,
    // non serve distinguere il proprietario: i tre metodi sono già specifici per categoria.
    private void hookOutlineUpdater(XC_LoadPackage.LoadPackageParam lp, String className, String methodName,
                                     java.util.function.BooleanSupplier onSupplier, java.util.function.IntSupplier dpSupplier) {
        Class<?> cls = tryFindClass(lp, className);
        if (cls == null) return;
        try {
            hookAllMethods(cls, methodName, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!onSupplier.getAsBoolean()) return;
                    Object provider = p.args.length > 0 ? p.args[0] : null;
                    if (provider == null || !provider.getClass().getName()
                            .equals("com.oplusos.systemui.common.outline.RoundRectOutlineProvider")) return;
                    try {
                        callMethod(provider, "update", (float) dp(dpSupplier.getAsInt()));
                    } catch (Throwable t) { dbg("hookOutlineUpdater " + methodName + " override failed: " + t); }
                }
            });
        } catch (Throwable t) { dbg("hookOutlineUpdater " + className + "." + methodName + " install failed: " + t); }
    }

    private void hookTileRadius(XC_LoadPackage.LoadPackageParam lp) {
        for (String cls : new String[]{
                "com.oplus.systemui.qs.base.res.SepQSResPool", "com.oplus.systemui.qs.base.res.StdQSResPool"}) {
            // Un solo interruttore per categoria (lo stesso dello sfondo) — niente doppia voce
            // "Riquadri base"/"in evidenza"/"Media" ripetuta anche per il raggio, su richiesta utente.
            hookOutlineUpdater(lp, cls, "updateTileOutline", () -> mTileBgBaseOn, () -> mTileRadiusBaseDp);
            hookOutlineUpdater(lp, cls, "updateHighLightTileOutline", () -> mTileBgHighlightOn, () -> mTileRadiusHlDp);
            hookOutlineUpdater(lp, cls, "updateMediaPanelOutline", () -> mTileBgMediaOn, () -> mTileRadiusMediaDp);
        }
    }

    // ── Colori Icone ─────────────────────────────────────────────────────────
    // OOS ha cambiato meccanismo dopo la versione che OC porta ("tintColor(int)" non esiste
    // più — verificato decompilando SystemUI.apk vero). Ora: setIcon(QSTile.State, boolean)
    // riceve lo stato standard AOSP (state.state: 0/1/2), onIconTintUpdate() applica il
    // colore separatamente senza sapere lo stato — bisogna ricordarselo tra le due chiamate.

    private final java.util.Map<Object, Integer> mTileStateCache = new WeakHashMap<>();

    // OplusQSIconView (package .plugins.qs.customize.view.tile, riquadri "grandi"/Separati) e
    // OplusQSIconViewImpl (package .qs.tileimpl, riquadri "piccoli"/Classico — stessa famiglia
    // di OplusQSTileViewImpl già confermata per etichette/sfondo) hanno lo STESSO schema
    // setIcon(QSTile.State,boolean)/onIconTintUpdate() — internamente diverso (Impl delega a
    // un iconViewProxy che legge StdQSResPool.tileIconColorState) ma getIconView() in entrambi
    // restituisce comunque una View tintabile via setImageTintList, quindi lo stesso hook
    // AFTER funziona identico su entrambe — nessuna logica duplicata, solo due classi in più.
    private void hookIconColors(XC_LoadPackage.LoadPackageParam lp) {
        for (String cn : new String[]{
                "com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSIconView", // "grandi"/Separati (OOS16)
                "com.oplus.systemui.plugins.qs.tile.OplusQSIconView", // fallback build più vecchie
                "com.oplus.systemui.qs.tileimpl.OplusQSIconViewImpl"}) { // "piccoli"/Classico
            Class<?> iconView = tryFindClass(lp, cn);
            if (iconView == null) continue;

            try {
                hookAllMethods(iconView, "setIcon", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (p.args.length == 0 || p.args[0] == null) return;
                        try {
                            int state = getIntField(p.args[0], "state");
                            mTileStateCache.put(p.thisObject, state);
                            // Riprogramma i due refresh ritardati ad ogni nuovo bind — scattano
                            // solo quando il burst di binding si placa davvero (vedi nota sopra).
                            mMainHandler.removeCallbacks(mDelayedRefresh1);
                            mMainHandler.removeCallbacks(mDelayedRefresh2);
                            mMainHandler.postDelayed(mDelayedRefresh1, 800);
                            mMainHandler.postDelayed(mDelayedRefresh2, 2500);
                        } catch (Throwable ignored) {}
                    }
                });
            } catch (Throwable t) { dbg("setIcon hook failed on " + cn + ": " + t); }

            try {
                hookAllMethods(iconView, "onIconTintUpdate", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        applyIconColorTo(p.thisObject, cn);
                    }
                });
            } catch (Throwable t) { dbg("onIconTintUpdate hook failed on " + cn + ": " + t); }
        }
    }

    /** Applica il colore corrente alla singola iconView (estratto da onIconTintUpdate per
     *  poterlo richiamare anche fuori dal callback OEM — vedi refreshCachedIconColors). */
    private void applyIconColorTo(Object iconViewInstance, String debugTag) {
        if (!mIconColorsOn) return;
        try {
            int tileState = mTileStateCache.getOrDefault(iconViewInstance, STATE_ACTIVE);
            int color = switch (tileState) {
                case STATE_ACTIVE -> mIconActiveAccent ? appAccentColor() : mIconActive;
                case STATE_INACTIVE -> mIconInactive;
                default -> mIconDisabled;
            };
            ImageView iv = (ImageView) callMethod(iconViewInstance, "getIconView");
            iv.setImageTintList(ColorStateList.valueOf(color));
        } catch (Throwable t) { dbg("icon color apply failed on " + debugTag + ": " + t); }
    }

    // BUG RISOLTO 2026-08-29 ("colore icone perso dopo riavvio SystemUI", vedi
    // project_qs_icon_refresh_bug). Due cause reali trovate, entrambe corrette qui:
    // 1) Il refresh forzato originale (notifyQsUpdate()) dipendeva solo da mPersonalityManager,
    //    non garantito esistere ancora appena dopo un riavvio — refreshCachedIconColors() qui
    //    sotto bypassa quella dipendenza del tutto, riapplicando il colore corrente a ogni
    //    iconView già vista (mTileStateCache, popolata ad OGNI setIcon() indipendentemente da
    //    mIconColorsOn) invece di sperare che PersonalityManager esista.
    // 2) Anche con (1) risolto, un secondo effetto — confermato via log diagnostici live: 100+
    //    chiamate "applicato" durante il burst iniziale di binding, eppure lo screenshot
    //    qualche secondo dopo mostrava di nuovo colori stock — mostra che QUALCOSA (probabile
    //    il sistema "Personality"/colore-da-sfondo di OOS, mai identificato con certezza)
    //    sovrascrive di nuovo il tint POCO DOPO che l'abbiamo già applicato correttamente.
    //    Mitigazione: ogni setIcon() riprogramma due refresh ritardati (mDelayedRefresh1/2,
    //    vedi campi sopra) che scattano solo quando il burst di binding si placa — vincono
    //    "per ultimo" contro qualunque cosa stia sovrascrivendo, senza sapere cos'è davvero.
    private void refreshCachedIconColors() {
        for (Object iconViewInstance : new ArrayList<>(mTileStateCache.keySet())) {
            applyIconColorTo(iconViewInstance, "refresh");
        }
    }

    // ── Etichette ────────────────────────────────────────────────────────────
    // OplusQSTileViewImpl (mLabelContainer/mLabel/mSecondLine) è la stessa classe superata
    // trovata oggi per le icone — non più usata. L'etichetta reale è QsLabelView (composito
    // con getTextView()/getIndicatorView(), stessa famiglia di OplusQSResizeableTileView*
    // già confermata oggi), ricomposta via updateColor(int,boolean,boolean) ad ogni cambio
    // stato — si aggancia DOPO quella chiamata, stesso schema che ha funzionato per le icone.

    private void hookLabels(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> qsLabelView = tryFindClass(lp,
                "com.oplus.systemui.plugins.qs.customize.view.tile.QsLabelView");
        if (qsLabelView == null) { dbg("QsLabelView not found — labels unavailable"); return; }

        try {
            hookAllMethods(qsLabelView, "updateColor", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try { applyLabel(p.thisObject); } catch (Throwable t) { dbg("label hook failed: " + t); }
                }
            });
        } catch (Throwable t) { dbg("QsLabelView updateColor hook failed: " + t); }

        // I riquadri "grandi" (2x1, es. Wi-Fi/Torcia a pillola) NON usano il testo dentro
        // QsLabelView per quello che si vede — hanno un secondo sistema, labelTitle/labelDesc
        // (TextSwitcher), colorato in handleLabelColorChanged(Pair). Su OneXOne quel metodo
        // ignora il Pair passato (chiama solo labelView.updateColor, già coperto sopra); su
        // TwoXOne invece USA il Pair per colorare i TextSwitcher — quindi qui si sostituisce
        // l'argomento invece di agganciare dopo (non c'è nessun "dopo" da correggere, il
        // colore vero passa proprio da questo parametro).
        Class<?> twoXOne = tryFindClass(lp,
                "com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewTwoXOne");
        if (twoXOne == null) { dbg("OplusQSResizeableTileViewTwoXOne not found — 2x1 label color unavailable"); return; }
        // CRASH TROVATO E RISOLTO: costruire "new kotlin.Pair<>(...)" con la classe Kotlin
        // COMPILATA DENTRO Obsidian manda in crash SystemUI ad ogni init di un riquadro 2x1
        // — java.lang.IllegalArgumentException: "has type kotlin.Pair, got kotlin.Pair".
        // Stesso nome, ClassLoader diverso: reflection Java fa un controllo di IDENTITÀ di
        // classe sugli argomenti, non solo sul nome. Bisogna prendere il kotlin.Pair del
        // classloader di SystemUI (lp.classLoader) e costruirlo via reflection pura, mai
        // istanziarlo direttamente col proprio import.
        Class<?> kotlinPairCls = tryFindClass(lp, "kotlin.Pair");
        if (kotlinPairCls == null) { dbg("kotlin.Pair not found in target classloader — 2x1 label color unavailable"); return; }
        try {
            java.lang.reflect.Constructor<?> pairCtor = kotlinPairCls.getConstructor(Object.class, Object.class);
            hookAllMethods(twoXOne, "handleLabelColorChanged", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!mLabelColorOn || p.args.length == 0) return;
                    try {
                        ColorStateList csl = ColorStateList.valueOf(mLabelColor);
                        p.args[0] = pairCtor.newInstance(csl, csl);
                    } catch (Throwable t) { dbg("TwoXOne label pair build failed: " + t); }
                }
            });
        } catch (Throwable t) { dbg("TwoXOne handleLabelColorChanged hook failed: " + t); }
    }

    private void applyLabel(Object labelView) {
        View view = (View) labelView;
        if (mHideLabels) {
            view.setVisibility(View.GONE);
            return;
        }
        // NON forzare VISIBLE qui: QsLabelView.updateColor gira anche su istanze usate per
        // stati transitori (drag/animazione) che l'OEM tiene apposta invisibili — forzarle
        // visibili le fa comparire fluttuanti fuori posto (bug segnalato: etichetta doppia
        // sui riquadri grandi). Si tocca solo il colore, mai la visibilità in questo ramo.

        if (mLabelColorOn) {
            try {
                TextView tv = (TextView) callMethod(labelView, "getTextView");
                if (tv != null) tv.setTextColor(mLabelColor);
            } catch (Throwable ignored) {}
            try {
                ImageView indicator = (ImageView) callMethod(labelView, "getIndicatorView");
                if (indicator != null) indicator.setImageTintList(ColorStateList.valueOf(mLabelColor));
            } catch (Throwable ignored) {}
        }
    }

    private <T> T fieldOrNull(Object obj, String field, Class<T> type) {
        try { return type.cast(getObjectField(obj, field)); } catch (Throwable t) { return null; }
    }

    // Equivalente "Classico" di hookLabels/applyLabel: OplusQSTileViewImpl (qs.tileimpl, NON
    // la stessa classe superata di QsLabelView citata sopra — quella era nel package
    // customize.view.tile, questa in tileimpl, famiglia OplusQSTileBaseView/Riquadri base
    // "Classico" già confermata per lo sfondo, 2026-08-20) usa un semplice TextView diretto
    // (mLabel/mSecondLine, campi pubblici) invece del composito QsLabelView — nessuna classe
    // condivisa con Separati, serve un hook separato. Stesso punto d'aggancio già valido per
    // colore icone/testo: handleQsColorStateChanged$1 (colore) e handleStateChanged (testo +
    // rivalutazione colore ad ogni cambio stato) — si applica DOPO entrambi, stesso schema.
    private void hookLabelsClassic(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> tileViewImpl = tryFindClass(lp, "com.oplus.systemui.qs.tileimpl.OplusQSTileViewImpl");
        if (tileViewImpl == null) return;
        XC_MethodHook applyHook = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try { applyLabelClassic(p.thisObject); } catch (Throwable t) { dbg("label classic hook failed: " + t); }
            }
        };
        try { hookAllMethods(tileViewImpl, "handleQsColorStateChanged$1", applyHook); }
        catch (Throwable t) { dbg("OplusQSTileViewImpl handleQsColorStateChanged$1 hook failed: " + t); }
        try { hookAllMethods(tileViewImpl, "handleStateChanged", applyHook); }
        catch (Throwable t) { dbg("OplusQSTileViewImpl handleStateChanged hook failed: " + t); }
    }

    private void applyLabelClassic(Object tileView) {
        // Stessa cautela di applyLabel: mai forzare VISIBLE quando l'hide è spento, solo
        // nascondere quando è acceso — evita di interferire con stati transitori dell'OEM.
        View labelContainer = fieldOrNull(tileView, "mLabelContainer", View.class);
        if (mHideLabels) {
            if (labelContainer != null) labelContainer.setVisibility(View.GONE);
            return;
        }
        if (!mLabelColorOn) return;
        TextView label = fieldOrNull(tileView, "mLabel", TextView.class);
        if (label != null) label.setTextColor(mLabelColor);
        TextView secondLine = fieldOrNull(tileView, "mSecondLine", TextView.class);
        if (secondLine != null) secondLine.setTextColor(mLabelColor);
    }

    // ── Cursore Luminosità: colore/sfondo ───────────────────────────────────

    // Trovata la causa reale (via decompilazione di OplusQsBrightnessSliderController +
    // OplusQsBaseToggleSliderLayout): il cursore luminosità nel pannello QS è un
    // OplusQsVerticalSeekBar (VERTICALE, extends COUIVerticalSeekBar) — non l'
    // OplusToggleSeekBar/COUISeekBar (ORIZZONTALE) agganciato finora, gerarchia di classi
    // completamente separata, motivo per cui l'hook precedente non scattava mai (confermato
    // da log: zero invocazioni dopo apertura pannello + trascinamento). Il metodo interno
    // OplusQsVerticalSeekBar.updateColor(boolean,boolean) chiama proprio setProgressColor/
    // setSeekBarBackgroundColor (ereditati da COUIVerticalSeekBar) con colori derivati dal
    // tema — li si intercetta sostituendo l'argomento prima che giri il metodo originale.
    private void hookBrightnessSliderColor(XC_LoadPackage.LoadPackageParam lp) {
        // setProgressColor/setSeekBarBackgroundColor sono dichiarati su COUIVerticalSeekBar,
        // NON su OplusQsVerticalSeekBar (che li eredita senza fare override — confermato nel
        // sorgente decompilato, nessuna dichiarazione locale, solo chiamate implicite
        // "this.setProgressColor(...)" dentro updateColor). hookAllMethods cerca solo i
        // metodi DICHIARATI sulla classe passata, non quelli ereditati — agganciare la
        // sottoclasse qui non trova nulla e fallisce silenziosamente (nessuna eccezione,
        // zero hook installati): motivo per cui il tentativo precedente non aveva effetto
        // pur avendo finalmente la classe "giusta" in mano.
        Class<?> seekBar = tryFindClass(lp,
                "com.coui.appcompat.seekbar.COUIVerticalSeekBar");
        if (seekBar == null) { dbg("COUIVerticalSeekBar not found — brightness slider color unavailable"); return; }

        try {
            hookAllMethods(seekBar, "setProgressColor", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    // COUIVerticalSeekBar è condivisa anche dal cursore del volume
                    // (OplusVolumeSeekBar) — senza questo filtro l'hook lo corromperebbe.
                    if (!p.thisObject.getClass().getName().contains("OplusQsVerticalSeekBar")) return;
                    dbg("DIAG setProgressColor fired on " + p.thisObject.getClass().getName()
                            + " mBrightnessCustomOn=" + mBrightnessCustomOn + " mBrightnessMode=" + mBrightnessMode);
                    if (!mBrightnessCustomOn || mBrightnessMode == 0 || p.args.length == 0) return;
                    int color = mBrightnessMode == 1 ? appAccentColor() : mBrightnessColor;
                    p.args[0] = ColorStateList.valueOf(color);
                }
            });
        } catch (Throwable t) { dbg("setProgressColor hook failed: " + t); }

        try {
            hookAllMethods(seekBar, "setSeekBarBackgroundColor", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!p.thisObject.getClass().getName().contains("OplusQsVerticalSeekBar")) return;
                    if (!mBrightnessBgOn || p.args.length == 0) return;
                    p.args[0] = ColorStateList.valueOf(mBrightnessBgColor);
                }
            });
        } catch (Throwable t) { dbg("setSeekBarBackgroundColor hook failed: " + t); }

        // Zero chiamate a setProgressColor osservate sul cursore luminosità — causa trovata
        // nel sorgente decompilato di OplusQsVerticalSeekBar.updateColor(boolean,boolean):
        // quando isDrawingWithMixColor risulta true (probabile su questo device) il metodo
        // ritorna PRIMA di chiamare setProgressColor/setSeekBarBackgroundColor, e disegna
        // invece un blur in tempo reale (AutoBlurDrawable) che vanifica qualsiasi colore
        // impostato — anche forzando il colore del blur stesso (tentativo precedente,
        // confermato via log setBlurColor senza eccezioni ma nessun cambiamento visivo reale,
        // 2026-08-20 mattina). isDrawingWithMixColor è calcolato da updateColor stesso come
        // "isSupportMixColor && !isGlobalThemeApplied" — isSupportMixColor è il primo
        // parametro (z) del metodo, quindi forzandolo a false PRIMA che il nativo lo elabori
        // il cursore prende il ramo a colore piatto, che chiama proprio setProgressColor/
        // setSeekBarBackgroundColor già agganciati sopra.
        Class<?> qsVerticalSeekBar = tryFindClass(lp, "com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar");
        if (qsVerticalSeekBar != null) {
            try {
                hookAllMethods(qsVerticalSeekBar, "updateColor", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (!mBrightnessCustomOn && !mBrightnessBgOn) return;
                        if (p.args.length == 0 || !(p.args[0] instanceof Boolean)) return;
                        p.args[0] = Boolean.FALSE;
                    }
                });
            } catch (Throwable t) { dbg("updateColor hook failed: " + t); }
        } else {
            dbg("OplusQsVerticalSeekBar not found — slider mix-color override unavailable");
        }
    }

    /** Bordo "Riquadro Cursori" — condivide switch/colore con "Bordo Pulsanti" (mTileBorderOn/
     *  mTileBorderColor, richiesta originale 2026-09-16: un unico controllo per pulsanti/cursori/
     *  media). Stesso pattern già usato per "Bordo barra" in VolumePanelMod: disegnato a mano su
     *  mBackgroundRect/mCurBackgroundRadius (i campi reali che COUIVerticalSeekBar.onDraw usa per
     *  il riempimento), scoped via instanceof a OplusQsVerticalSeekBar per non toccare il cursore
     *  del popup "Volume sistema" (OplusVolumeSeekBar, stessa classe base COUIVerticalSeekBar). */
    private void hookSliderBorder(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> qsSeekBarCls = tryFindClass(lp, "com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar");
        Class<?> couiSeekBarCls = tryFindClass(lp, "com.coui.appcompat.seekbar.COUIVerticalSeekBar");
        if (qsSeekBarCls == null || couiSeekBarCls == null) return;
        try {
            hookAllMethods(couiSeekBarCls, "onDraw", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!mTileBorderOn || !qsSeekBarCls.isInstance(p.thisObject)) return;
                    try {
                        android.graphics.Canvas canvas = (android.graphics.Canvas) p.args[0];
                        Object rectObj = getObjectField(p.thisObject, "mBackgroundRect");
                        if (!(rectObj instanceof android.graphics.Rect rect)) return;
                        float radius = getFloatField(p.thisObject, "mCurBackgroundRadius");
                        float strokeWidth = tileBorderWidthPx();
                        float inset = strokeWidth / 2f;
                        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                        paint.setStyle(android.graphics.Paint.Style.STROKE);
                        paint.setStrokeWidth(strokeWidth);
                        paint.setColor(mTileBorderColor);
                        canvas.drawRoundRect(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset,
                                radius, radius, paint);
                    } catch (Throwable t) { dbg("slider border draw failed: " + t); }
                }
            });
        } catch (Throwable t) { dbg("hookSliderBorder install failed: " + t); }
    }

    // ── Cursore Luminosità: icona scura ──────────────────────────────────────

    private void hookBrightnessIcon(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> clipBrightnessView = tryFindClass(lp, "com.oplus.systemui.qs.base.seek.ClipBrightnessView");
        if (clipBrightnessView == null) { dbg("ClipBrightnessView not found — icon color unavailable"); return; }

        Class<?> qsColorUtil = tryFindClass(lp, "com.oplus.systemui.qs.base.util.QsColorUtil");

        XC_MethodHook colorHook = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (mBrightnessIconMode == 0) return; // predefinito, non toccare
                try {
                    int color;
                    if (mBrightnessIconMode == 4) {
                        color = mBrightnessIconColor; // personalizzata
                    } else if (mBrightnessIconMode == 3) {
                        color = appAccentColor(); // accento
                    } else if (mBrightnessIconMode == 2) {
                        color = 0xFFFFFFFF; // bianca
                    } else if (qsColorUtil != null) {
                        color = getStaticIntField(qsColorUtil, "BRIGHTNESS_ICON_BG_LIGHT_COLOR"); // scura
                    } else {
                        return;
                    }
                    callMethod(p.thisObject, "setIconColorFilter", color);
                } catch (Throwable t) { dbg("brightness icon color hook failed: " + t); }
            }
        };
        try { hookAllMethods(clipBrightnessView, "updateIconColor", colorHook); } catch (Throwable ignored) {}
        try { hookAllMethods(clipBrightnessView, "setIconDrawable", colorHook); } catch (Throwable ignored) {}
    }

    // ── Cursore Volume: icona (dentro il riquadro QS, non il popup del Pannello Volume —
    // quello è un'altra vista, gestita da VolumePanelMod). OplusQsVolumeIconView extends
    // QSLottieAnimationView (package .qs.base.seek, "gemella" di ClipBrightnessView), ma a
    // differenza dell'icona del popup volume (com.oplus.anim.EffectiveAnimationView, serve
    // il vero KeyPath Lottie) il nativo stesso chiama setColorFilter(color, SRC_IN) dentro
    // updateIconColor(boolean,boolean) — QSLottieAnimationView deve avere un override che la
    // instrada davvero a Lottie, esattamente come ClipBrightnessView. Stesso pattern: hook
    // dopo la chiamata nativa, si sovrascrive col colore scelto. ──────────────────────
    private int volumeSliderIconColor() {
        return switch (mVolumeIconMode) {
            case 4 -> mVolumeIconColor;
            case 3 -> appAccentColor();
            case 2 -> 0xFFFFFFFF;
            default -> 0xFF404040; // scura
        };
    }

    /** true se applicato via addValueCallback (vero percorso Lottie — la libreria reale
     *  com.airbnb.lottie ignora silenziosamente Drawable/View.setColorFilter() da API recenti,
     *  esattamente come già scoperto per il pannello Volume Sistema/VolumePanelMod). */
    private boolean applyVolumeIconLottieColor(Object iconView, int color) {
        try {
            ClassLoader cl = iconView.getClass().getClassLoader();
            Class<?> keyPathCls = findClass("com.airbnb.lottie.model.KeyPath", cl);
            Class<?> valueCallbackCls = findClass("com.airbnb.lottie.value.LottieValueCallback", cl);
            Class<?> propertyCls = findClass("com.airbnb.lottie.LottieProperty", cl);
            Object keyPath = keyPathCls.getConstructor(String[].class).newInstance((Object) new String[]{"**"});
            Object colorFilterProperty = de.robv.android.xposed.XposedHelpers.getStaticObjectField(propertyCls, "COLOR_FILTER");
            android.graphics.PorterDuffColorFilter filter = new android.graphics.PorterDuffColorFilter(
                    color, android.graphics.PorterDuff.Mode.SRC_ATOP);
            Object valueCallback = valueCallbackCls.getConstructor(Object.class).newInstance(filter);
            callMethod(iconView, "addValueCallback", keyPath, colorFilterProperty, valueCallback);
            return true;
        } catch (Throwable t) {
            dbg("volume icon Lottie colorFilter failed: " + t);
            return false;
        }
    }

    // Per la route "normale" (altoparlante, il caso comune) OplusQsVolumeIconView non mostra
    // un drawable statico: cambia composizione Lottie ad ogni soglia di livello attraversata
    // durante il trascinamento (normalRouteIconState.updateAnimation$1 -> setImageDrawable(null)
    // + setAnimation(nuovoAsset)) — ogni volta il colore va riapplicato sulla NUOVA composizione,
    // motivo per cui l'icona torna al colore stock non appena si regola il volume. Fix: si
    // riapplica il colore (via KeyPath, non setColorFilter) dopo ogni setImageDrawable/
    // setImageResource/setAnimation/setComposition/updateIconColor/updateIconState — copre bene
    // ogni cambio DURANTE l'uso (confermato: il colore cambiava correttamente al tocco).
    //
    // 2026-09-16: mancava però lo stato "a riposo" — icona scura alla sola apertura del pannello
    // QS, prima di qualunque tocco. Investigato a fondo (log diagnostico, più giri): tutti gli
    // hook sopra SCATTANO anche al boot (non solo durante l'uso) e "applicano" senza eccezioni,
    // eppure l'icona restava visivamente scura — persino aspettando 300ms per un'eventuale
    // composizione Lottie caricata in modo asincrono (mai arrivata: getComposition() restava
    // null). La raffica di updateIconState/setAnimation osservata al boot non è quindi la stessa
    // composizione poi realmente mostrata nel pannello — probabile inizializzazione interna/
    // pre-warm non collegata al rendering visibile.
    // FIX REALE: OplusQsVolumeIconView dichiara anche getMuteIconColor()/getUnMuteIconColor(),
    // i due getter che il codice nativo stesso legge per sapere quale colore applicare ad ogni
    // stato — sovrascrivendo solo il valore restituito (non provando più a reimplementare la
    // colorazione via Lottie) si sfrutta la pipeline nativa, già corretta e già invocata al
    // momento giusto in OGNI caso (apertura E interazione), risolvendo lo stato a riposo. Il
    // meccanismo Lottie/addValueCallback sopra resta com'era prima di oggi (funzionava già per
    // l'interazione), i due approcci convivono senza conflitto.
    private void hookVolumeSliderIcon(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> volumeIconView = tryFindClass(lp, "com.oplus.systemui.qs.base.seek.OplusQsVolumeIconView");
        if (volumeIconView == null) { dbg("OplusQsVolumeIconView not found — volume slider icon color unavailable"); return; }
        XC_MethodHook reapplyHook = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (mVolumeIconMode == 0) return; // predefinito, non toccare
                applyVolumeIconLottieColor(p.thisObject, volumeSliderIconColor());
            }
        };
        try { hookAllMethods(volumeIconView, "updateIconColor", reapplyHook); } catch (Throwable t) { dbg("hookVolumeSliderIcon updateIconColor failed: " + t); }
        try { hookAllMethods(volumeIconView, "updateIconState", reapplyHook); } catch (Throwable t) { dbg("hookVolumeSliderIcon updateIconState failed: " + t); }
        XC_MethodHook overrideColorGetterHook = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (mVolumeIconMode == 0) return;
                p.setResult(volumeSliderIconColor());
            }
        };
        try { hookAllMethods(volumeIconView, "getMuteIconColor", overrideColorGetterHook); } catch (Throwable t) { dbg("hookVolumeSliderIcon getMuteIconColor failed: " + t); }
        try { hookAllMethods(volumeIconView, "getUnMuteIconColor", overrideColorGetterHook); } catch (Throwable t) { dbg("hookVolumeSliderIcon getUnMuteIconColor failed: " + t); }
        // setImageDrawable/setImageResource/setAnimation/setComposition NON sono ridichiarati in
        // OplusQsVolumeIconView — vivono nella classe reale com.airbnb.lottie.LottieAnimationView
        // (2 livelli sopra), hookAllMethods non risale la gerarchia quindi vanno agganciati lì,
        // filtrando per instanceof (stesso pattern "stubborn repaint" già usato altrove).
        Class<?> lottieViewCls = tryFindClass(lp, "com.airbnb.lottie.LottieAnimationView");
        if (lottieViewCls == null) { dbg("LottieAnimationView not found — volume icon reapply-on-recompose unavailable"); return; }
        XC_MethodHook scopedReapplyHook = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (mVolumeIconMode == 0 || !volumeIconView.isInstance(p.thisObject)) return;
                applyVolumeIconLottieColor(p.thisObject, volumeSliderIconColor());
            }
        };
        try { hookAllMethods(lottieViewCls, "setImageDrawable", scopedReapplyHook); } catch (Throwable t) { dbg("hookVolumeSliderIcon setImageDrawable failed: " + t); }
        try { hookAllMethods(lottieViewCls, "setImageResource", scopedReapplyHook); } catch (Throwable t) { dbg("hookVolumeSliderIcon setImageResource failed: " + t); }
        try { hookAllMethods(lottieViewCls, "setAnimation", scopedReapplyHook); } catch (Throwable t) { dbg("hookVolumeSliderIcon setAnimation failed: " + t); }
        try { hookAllMethods(lottieViewCls, "setComposition", scopedReapplyHook); } catch (Throwable t) { dbg("hookVolumeSliderIcon setComposition failed: " + t); }
    }

    // ── Cursore Luminosità: sfocatura ────────────────────────────────────────

    private void hookSliderBlur(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> foregroundBlurParam = tryFindClass(lp, "com.oplus.posteffect.ForegroundBlurParam");
        Class<?> seekBar = tryFindClass(lp, "com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar");
        if (foregroundBlurParam == null || seekBar == null) { dbg("blur classes not found — slider blur controls unavailable"); return; }
        mForegroundBlurParamClass = foregroundBlurParam;

        try {
            hookAllConstructors(seekBar, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    synchronized (mSeekBarInstances) { mSeekBarInstances.add(p.thisObject); }
                }
            });
        } catch (Throwable ignored) {}

        try {
            hookAllMethods(seekBar, "createActiveTrackBlurParams", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!mBrightnessCustomOn) return;
                    Object blur = buildForegroundBlur(SLIDER_PROGRESS);
                    if (blur != null) p.setResult(blur);
                }
            });
        } catch (Throwable ignored) {}

        try {
            hookAllMethods(seekBar, "createInactiveTrackBlurParams", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!mBrightnessBgOn) return;
                    Object blur = buildForegroundBlur(SLIDER_BACKGROUND);
                    if (blur != null) p.setResult(blur);
                }
            });
        } catch (Throwable ignored) {}

        try {
            hookAllMethods(seekBar, "drawForegroundBlur", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        Object foregroundBlurParam2 = p.args[2];
                        Object activeInstance = null;
                        Object activeTrackParam = null;
                        synchronized (mSeekBarInstances) {
                            for (Object instance : mSeekBarInstances) {
                                Object currentTrackParam = getObjectField(instance, "activeTrackParam");
                                if (currentTrackParam == foregroundBlurParam2) {
                                    activeInstance = instance;
                                    activeTrackParam = currentTrackParam;
                                    break;
                                }
                            }
                        }
                        if (activeInstance == null && !mSeekBarInstances.isEmpty()) {
                            activeInstance = mSeekBarInstances.get(mSeekBarInstances.size() - 1);
                            activeTrackParam = getObjectField(activeInstance, "activeTrackParam");
                        }
                        if (activeInstance == null) return;
                        boolean isActive = (foregroundBlurParam2 == activeTrackParam);
                        Object newForeground;
                        if (isActive) {
                            newForeground = (!mBrightnessCustomOn || mBrightnessMode == 0)
                                    ? callMethod(activeInstance, "createActiveTrackBlurParams")
                                    : buildForegroundBlur(SLIDER_PROGRESS);
                        } else {
                            newForeground = mBrightnessBgOn
                                    ? buildForegroundBlur(SLIDER_BACKGROUND)
                                    : callMethod(activeInstance, "createInactiveTrackBlurParams");
                        }
                        if (newForeground != null) p.args[2] = newForeground;
                    } catch (Throwable t) { dbg("drawForegroundBlur hook failed: " + t); }
                }
            });
        } catch (Throwable ignored) {}
    }

    /** ForegroundBlurParam(int blendMode, int color1, int color2) — costruito via reflection
     *  pura (3 int), non serve implementare nessuna interfaccia. */
    private Object buildForegroundBlur(int type) {
        if (mForegroundBlurParamClass == null) return null;
        try {
            int blend = getBlendMode();
            int color = type == SLIDER_PROGRESS
                    ? (mBrightnessMode == 2 ? mBrightnessColor : appAccentColor())
                    : mBrightnessBgColor;
            return mForegroundBlurParamClass.getConstructor(int.class, int.class, int.class)
                    .newInstance(blend, color, color);
        } catch (Throwable t) {
            dbg("buildForegroundBlur failed: " + t);
            return null;
        }
    }

    private int getBlendMode() {
        return BLEND_LUMINOSITY_COLOR_DODGE;
    }

    // ── Cursore: raggio (del cursore luminosità/volume, non dei riquadri) ────

    // "updateRadius(int,int)" (OC-era) non esiste più — verificato nel sorgente decompilato:
    // ora è "setCornerRadius(float)", un solo argomento. Stessa lezione di oggi (metodi
    // rinominati tra versioni OOS), corretto qui prima di lasciarlo silenziosamente rotto.
    private void hookSliderRadius(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> toggleSliderLayout = tryFindClass(lp, "com.oplus.systemui.qs.base.seek.OplusQsBaseToggleSliderLayout");
        if (toggleSliderLayout == null) { dbg("OplusQsBaseToggleSliderLayout not found — slider radius unavailable"); return; }

        try {
            hookAllMethods(toggleSliderLayout, "setCornerRadius", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!mRadiusOn || p.args.length == 0) return;
                    p.args[0] = (float) dp(mRadiusDp);
                }
            });
        } catch (Throwable t) { dbg("setCornerRadius hook failed: " + t); }
    }

    // ── Animazione Riquadri ──────────────────────────────────────────────────
    // bindClickListener è dove OOS16 collega il click reale del riquadro (letto nel
    // sorgente decompilato: setOnClickListener chiama function0.invoke() poi
    // onClickListener.onClick(view)) — a differenza dell'era OC, performClick non è più
    // dichiarato localmente su queste classi (solo ereditato da View), quindi non è
    // agganciabile direttamente con hookAllMethods (che cerca solo metodi DICHIARATI sulla
    // classe passata — stessa lezione imparata oggi con i cursori). Si avvolge invece il
    // listener passato a bindClickListener con uno che chiama l'originale e poi l'animazione.

    private void hookTileAnimation(XC_LoadPackage.LoadPackageParam lp) {
        for (String cn : new String[]{
                "com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewOneXOne",
                "com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewTwoXOne"}) {
            Class<?> tileCls = tryFindClass(lp, cn);
            if (tileCls == null) { dbg(cn + " not found — tile animation unavailable there"); continue; }
            try {
                hookAllMethods(tileCls, "bindClickListener", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (p.args.length == 0 || !(p.args[0] instanceof View.OnClickListener orig)) return;
                        p.args[0] = (View.OnClickListener) v -> {
                            orig.onClick(v);
                            if (mAnimStyle != 0) playTileAnimation(v);
                        };
                    }
                });
            } catch (Throwable t) { dbg("tile animation hook failed for " + cn + ": " + t); }
        }
    }

    private void playTileAnimation(View v) {
        android.animation.ObjectAnimator anim = switch (mAnimStyle) {
            case 1 -> android.animation.ObjectAnimator.ofFloat(v, "rotation", 0f, 360f);
            case 2 -> android.animation.ObjectAnimator.ofFloat(v, "rotationX", 0f, 360f);
            case 3 -> android.animation.ObjectAnimator.ofFloat(v, "rotationY", 0f, 360f);
            case 4 -> android.animation.ObjectAnimator.ofFloat(v, "translationX",
                    0, 25, -25, 25, -25, 15, -15, 6, -6, 0);
            case 5 -> android.animation.ObjectAnimator.ofFloat(v, "alpha", 0f, 1f);
            case 6 -> android.animation.ObjectAnimator.ofFloat(v, "scaleX", 1f, 1.2f, 0.8f, 1f);
            case 7 -> android.animation.ObjectAnimator.ofFloat(v, "scaleY", 1f, 1.2f, 0.8f, 1f);
            case 8 -> android.animation.ObjectAnimator.ofFloat(v, "scaleX", 1f, 1.1f, 1f);
            case 9 -> android.animation.ObjectAnimator.ofFloat(v, "scaleY", 1f, 1.1f, 1f);
            default -> null;
        };
        if (anim == null) return;
        anim.setInterpolator(switch (mAnimInterpolator) {
            case 1 -> new android.view.animation.AccelerateInterpolator();
            case 2 -> new android.view.animation.DecelerateInterpolator();
            case 3 -> new android.view.animation.AccelerateDecelerateInterpolator();
            case 4 -> new android.view.animation.BounceInterpolator();
            case 5 -> new android.view.animation.OvershootInterpolator();
            case 6 -> new android.view.animation.AnticipateInterpolator();
            case 7 -> new android.view.animation.AnticipateOvershootInterpolator();
            default -> new android.view.animation.LinearInterpolator();
        });
        anim.setDuration(mAnimDuration * 1000L);
        anim.start();
    }

    // ── Transizioni pagine QS ────────────────────────────────────────────────
    // PagedTileLayout è AOSP (extends ViewPager), non OEM — verificato nel sorgente
    // decompilato: campo pubblico mOnPageChangeListener, implementa ViewPager.
    // OnPageChangeListener con onPageScrolled reale. Stesso identico aggancio di OC.

    private void hookTileTransitions(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> pagedTileLayout = tryFindClass(lp, "com.android.systemui.qs.PagedTileLayout");
        dbg("DIAG hookTileTransitions: class lookup result=" + pagedTileLayout);
        if (pagedTileLayout == null) { dbg("PagedTileLayout not found — transitions unavailable"); return; }
        dbg("DIAG hookTileTransitions: constructors=" + pagedTileLayout.getDeclaredConstructors().length);

        try {
            hookAllConstructors(pagedTileLayout, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        Object listener = getObjectField(p.thisObject, "mOnPageChangeListener");
                        dbg("DIAG PagedTileLayout constructed, listener class=" + listener.getClass().getName());
                        Object pager = p.thisObject;
                        hookAllMethods(listener.getClass(), "onPageScrolled", new XC_MethodHook() {
                            @Override protected void beforeHookedMethod(MethodHookParam p2) {
                                dbg("DIAG onPageScrolled fired, mTransitionsOn=" + mTransitionsOn
                                        + " mTransitionStyle=" + mTransitionStyle);
                                if (!mTransitionsOn) return;
                                it.tugaia56.obsidian.xposed.utils.TileTransformers.Transformer transformer =
                                        it.tugaia56.obsidian.xposed.utils.TileTransformers.get(mTransitionStyle);
                                if (transformer == null) return;
                                try {
                                    int childCount = (int) callMethod(pager, "getChildCount");
                                    for (int i = 0; i < childCount; i++) {
                                        View child = (View) callMethod(pager, "getChildAt", i);
                                        Object childLp = callMethod(child, "getLayoutParams");
                                        try { if ((boolean) getObjectField(childLp, "isDecor")) continue; }
                                        catch (Throwable ignored) {}
                                        int scrollX = (int) callMethod(pager, "getScrollX");
                                        float transformPos = (float) (child.getLeft() - scrollX) / child.getWidth();
                                        dbg("DIAG transform child#" + i + " pos=" + transformPos);
                                        transformer.transformPage(child, transformPos);
                                    }
                                } catch (Throwable t) { dbg("transition apply failed: " + t); }
                            }
                        });
                    } catch (Throwable t) { dbg("PagedTileLayout listener hook failed: " + t); }
                }
            });
            dbg("DIAG hookTileTransitions: hookAllConstructors call completed without throwing");
        } catch (Throwable t) { dbg("PagedTileLayout constructor hook failed: " + t); }
    }

    // ── Refresh forzato del pannello dopo un cambio pref ─────────────────────

    private void notifyQsUpdate() {
        if (mPersonalityManager == null) return;
        try {
            int currentShape = (int) callMethod(mPersonalityManager, "getLastShapeType");
            callMethod(mPersonalityManager, "notifyListener", 0);
            callMethod(mPersonalityManager, "notifyListener", currentShape);
        } catch (Throwable ignored) {}
    }

    private int dp(int v) {
        return Math.round(v * mContext.getResources().getDisplayMetrics().density);
    }

    private Class<?> tryFindClass(XC_LoadPackage.LoadPackageParam lp, String... names) {
        for (String name : names) {
            try { return findClass(name, lp.classLoader); } catch (Throwable ignored) {}
        }
        return null;
    }

    private static void dbg(String msg) {
        XposedBridge.log("[ Obsidian ] QsTilesCustomizeMod: " + msg);
    }

    @Override public boolean listensTo(String packageName) { return SYSTEM_UI.equals(packageName); }
}
