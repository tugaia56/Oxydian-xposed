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
    private static final String KEY_ICON_BORDER_ON     = "qs_icon_border_enabled";
    private static final String KEY_ICON_BORDER_COLOR  = "qs_icon_border_custom_color";
    private static final float TILE_BORDER_WIDTH_DP = 1.5f; // stesso spessore di Bordo barra volume
    // Tono scuro nativo delle card larghe MAI attive (Torcia/Riavvia) — misurato via pixel-pick su
    // screenshot reale 2026-09-23 (RGB ~42,45,58) — usato per neutralizzare l'accento nativo sulle
    // card ATTIVE (Wi-Fi/Pixolor), vedi hookTileBgHighlight "neutralizeWideFill".
    private static final int WIDE_CARD_NEUTRAL_FILL = 0xFF2A2D3A;
    // Default sensato per lo sfondo/traccia dei cursori quando forziamo il ramo a colore piatto
    // (vedi updateColor in hookBrightnessSliderColor) senza che l'utente abbia personalizzato
    // "Colore cursore" — stesso tono neutro di WIDE_CARD_NEUTRAL_FILL.
    private static final int TILE_SHAPE_SLIDER_BG_FALLBACK = 0xFF2A2D3A;

    // ── Bordo Pannello QS (2026-09-18) — 4a superficie della richiesta 09-16, switch/colore
    // separati dal bordo pulsanti/cursori/media sopra.

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

    // Forma riquadri (2026-09-22) — vedi nota gemella nel Fragment. Quando attiva, sovrascrive
    // il raggio di "Riquadri base"/"in evidenza" con un preset fisso invece del valore manuale/
    // dello sfondo colore, e non richiede che quelle sezioni sfondo siano attive: un pref
    // indipendente aggiunto alla condizione (onSupplier) di hookOutlineUpdater. Media escluso
    // (fuori scope, il pannello non ha davvero "angoli" percepibili da forma).
    private static final String KEY_TILE_SHAPE_ON = "qs_tile_shape_enabled";
    private static final String KEY_TILE_SHAPE    = "qs_tile_shape_preset";
    // dp misurati/stimati per riquadro piccolo tipico (~44-48dp): Quadrato usa lo stesso valore
    // calibrato sui pixel del bordo (vedi hookTileBgHighlight); Supercerchio è una scelta estetica
    // (un vero "supercerchio"/squircle non è riproducibile con un solo raggio d'angolo).
    // Ordine alfabetico su richiesta 2026-09-23: Finestra, Goccia, Ottagono, Quadrato, Rombo,
    // Supercerchio 1, Supercerchio 2 ("Cerchio" rimosso 2026-09-23: doppione del "Predefinito"
    // nativo di OOS, che già fa i QS tondi). KIND per indice: 0=raggio uniforme, 1=ottagono
    // (angoli tagliati dritti, non un arco), 2=4 angoli diversi (Rombo/Goccia, stesso trucco di
    // SettingsIconsResourceManager per il pack icone Settings — un vero rombo/goccia non è un
    // round-rect).
    private static final int SHAPE_KIND_UNIFORM = 0, SHAPE_KIND_OCTAGON = 1, SHAPE_KIND_CORNERS = 2;
    private static final int[] TILE_SHAPE_KIND = {
            SHAPE_KIND_OCTAGON, SHAPE_KIND_CORNERS, SHAPE_KIND_OCTAGON,
            SHAPE_KIND_UNIFORM, SHAPE_KIND_CORNERS, SHAPE_KIND_UNIFORM, SHAPE_KIND_UNIFORM,
    };
    // Supercerchio 2 era 40dp ma segnalato "sono cerchi" (saturava) — abbassato a 26dp.
    private static final int[] TILE_SHAPE_UNIFORM_DP = {0, 0, 0, 10, 0, 16, 26}; // solo indici UNIFORM
    // {TL, TR, BR, BL} in dp — solo indici CORNERS. Goccia: angolo stretto uguagliato a Rombo (4dp,
    // era 22 — segnalato "fallo della stessa misura di rombo"). Angoli larghi 80dp->30dp (2026-09-23,
    // "media da correggere, angolo più stretto fai uguale a rombo"): 80dp si affidava al clamp
    // automatico di addRoundRect (radius > metà lato -> scala) per apparire "massimamente arrotondato"
    // sui riquadri piccoli (~62dp, clampato a ~31dp) — su Media (pannello molto più grande, nessun
    // clamp) restava un letterale 80dp, sproporzionatamente più tondo di quanto visto sui riquadri.
    // Stesso dp fisso di Rombo ovunque, mai clampato, stessa resa su qualunque superficie (stesso
    // principio già applicato a Finestra/Ottagono, vedi TILE_SHAPE_CHAMFER_*_DP).
    private static final float[][] TILE_SHAPE_CORNERS_DP = {
            null,
            {30f, 30f, 4f, 30f},         // Goccia
            null, null,
            {4f, 30f, 4f, 30f},          // Rombo (confermato via screenshot anteprima nativa 2026-09-23)
            null, null,
    };
    // Finestra/Ottagono: angolo tagliato dritto, nessun arco (confermato via screenshot 2026-09-23).
    // Taglio in dp FISSO (non più una frazione di bounds — vedi buildShapedPath, "Media accorcia i
    // lati degli angoli": su un pannello molto più largo che alto una frazione dava un taglio molto
    // più corto che sui riquadri/cursori). Calibrati sui valori già confermati corretti sui riquadri
    // piccoli (bounds ~186px, densità 3px/dp): Finestra 0.24*186≈44.6px≈15dp, Ottagono 0.30*186≈
    // 55.8px≈19dp.
    private static final int TILE_SHAPE_CHAMFER_FINESTRA_DP = 15;
    private static final int TILE_SHAPE_CHAMFER_OTTAGONO_DP = 19;

    /** Raggio "medio" per preset — usato per l'ombra/outline nativa (hookTileRadius, un solo
     *  float, invisibile comunque per i riquadri tondi/grandi disegnati a mano) e come log. Per
     *  Rombo/Goccia/Finestra/Ottagono (angoli diversi) è solo una stima, il vero disegno usa
     *  drawShaped(). */
    private int tileShapePresetRadiusDp() {
        switch (TILE_SHAPE_KIND[mTileShapePreset]) {
            case SHAPE_KIND_UNIFORM: return TILE_SHAPE_UNIFORM_DP[mTileShapePreset];
            case SHAPE_KIND_CORNERS:
                float[] c = TILE_SHAPE_CORNERS_DP[mTileShapePreset];
                return Math.round((c[0] + c[1] + c[2] + c[3]) / 4f);
            default: return 10; // Finestra/Ottagono
        }
    }

    /** Costruisce il Path della forma scelta in "Forma riquadri" dentro bounds — un solo punto sia
     *  per disegnarla (drawShaped) sia per ritagliare il contenuto nativo sulla stessa sagoma
     *  (clipShaped, cursori/media — 2026-09-23 "lo sfondo esce dal bordo"). */
    private android.graphics.Path buildShapedPath(android.graphics.Rect bounds) {
        android.graphics.Path p = new android.graphics.Path();
        switch (TILE_SHAPE_KIND[mTileShapePreset]) {
            case SHAPE_KIND_OCTAGON: {
                // Taglio d'angolo in dp FISSO (non più proporzionale a bounds) — segnalato
                // 2026-09-23 "Media accorcia i lati degli angoli" (il Media panel è molto più largo
                // che alto, quindi un taglio calcolato come frazione delle sue dimensioni risultava
                // molto più corto rispetto a quello sui riquadri/cursori — stesso taglio in dp ovunque
                // (riquadri piccoli, cursori, riquadri grandi, media), calibrato sul valore già
                // confermato corretto sui riquadri piccoli (Finestra ~44.6px/0.24, Ottagono
                // ~55.8px/0.30 con bounds 186px, densità 3px/dp). Cap a metà del lato più corto per
                // bounds molto piccoli (icone).
                int chamferDp = mTileShapePreset == 0 ? TILE_SHAPE_CHAMFER_FINESTRA_DP : TILE_SHAPE_CHAMFER_OTTAGONO_DP;
                float base = Math.min(dp(chamferDp), Math.min(bounds.width(), bounds.height()) / 2f);
                float chX = base, chY = base;
                p.moveTo(bounds.left + chX, bounds.top);
                p.lineTo(bounds.right - chX, bounds.top);
                p.lineTo(bounds.right, bounds.top + chY);
                p.lineTo(bounds.right, bounds.bottom - chY);
                p.lineTo(bounds.right - chX, bounds.bottom);
                p.lineTo(bounds.left + chX, bounds.bottom);
                p.lineTo(bounds.left, bounds.bottom - chY);
                p.lineTo(bounds.left, bounds.top + chY);
                p.close();
                break;
            }
            case SHAPE_KIND_CORNERS: {
                float[] c = TILE_SHAPE_CORNERS_DP[mTileShapePreset];
                float[] radii = new float[8];
                for (int i = 0; i < 4; i++) { radii[i * 2] = dp((int) c[i]); radii[i * 2 + 1] = dp((int) c[i]); }
                p.addRoundRect(new android.graphics.RectF(bounds), radii, android.graphics.Path.Direction.CW);
                break;
            }
            default: {
                float r = dp(TILE_SHAPE_UNIFORM_DP[mTileShapePreset]);
                p.addRoundRect(new android.graphics.RectF(bounds), r, r, android.graphics.Path.Direction.CW);
            }
        }
        return p;
    }

    /** Disegna (riempimento o contorno, secondo lo stile del Paint passato) la forma scelta in
     *  "Forma riquadri" dentro bounds — un solo punto per tutti gli usi (riquadri tondi, cursori,
     *  media), niente logica duplicata. */
    private void drawShaped(android.graphics.Canvas canvas, android.graphics.Rect bounds, android.graphics.Paint paint) {
        canvas.drawPath(buildShapedPath(bounds), paint);
    }

    // Riquadri grandi (Wi-Fi/Torcia/Pixolor/Riavvia), Finestra/Ottagono/Rombo: mostrano un alone
    // nativo intorno alla card (blur del drawable stesso, NON l'ombra di elevazione della View —
    // confermato 2026-09-24 dopo 5 tentativi falliti su Outline/setPath/setAlpha/elevation, sempre
    // riprodotto identico anche con bordo/ombra/riempimento matematicamente identici) che non
    // possiamo sopprimere da qui senza decompilare — stessa famiglia del limite già accettato per
    // il riempimento Media. Fallback (richiesto dall'utente): SOLO su questa superficie, per
    // queste 3 forme, bordo/riempimento/ombra usano tutti lo stesso rettangolo arrotondato
    // "sicuro" (raggio di Supercerchio 2, mai mostrato l'alone su nessun riavvio/apertura reale)
    // invece della Path precisa — combaciano sempre perché sono la STESSA identica forma semplice.
    // Riquadri piccoli, cursori, media e riquadri grandi per Goccia/Quadrato/Supercerchio restano
    // precisi (mai mostrato il problema).
    private static final int WIDE_CARD_SAFE_RADIUS_DP = 26;
    private boolean wideCardNeedsSafeShape() {
        return mTileShapePreset == 0 || mTileShapePreset == 2 || mTileShapePreset == 4; // Finestra, Ottagono, Rombo
    }
    private android.graphics.Path buildWideCardShapePath(android.graphics.Rect bounds) {
        if (!wideCardNeedsSafeShape()) return buildShapedPath(bounds);
        android.graphics.Path p = new android.graphics.Path();
        float r = dp(WIDE_CARD_SAFE_RADIUS_DP);
        p.addRoundRect(new android.graphics.RectF(bounds), r, r, android.graphics.Path.Direction.CW);
        return p;
    }
    private void drawWideCardShape(android.graphics.Canvas canvas, android.graphics.Rect bounds, android.graphics.Paint paint) {
        canvas.drawPath(buildWideCardShapePath(bounds), paint);
    }

    /** Ritaglia il canvas sulla forma scelta PRIMA che il contenuto nativo (riempimento/blur/
     *  progress bar) venga disegnato — l'unico modo per far combaciare un riempimento che non
     *  conosciamo/non possiamo ridisegnare a mano (cursori, media) con un bordo angolare come
     *  Finestra/Ottagono, invece di lasciarlo uscire dagli angoli tagliati. Va sempre accoppiato a
     *  canvas.restore() dopo che il contenuto nativo ha disegnato. Ritorna true se il clip è stato
     *  applicato (quindi serve il restore corrispondente). */
    private boolean clipShapedIfEnabled(android.graphics.Canvas canvas, android.graphics.Rect bounds) {
        if (!mTileShapeOn) return false;
        canvas.save();
        canvas.clipPath(buildShapedPath(bounds));
        return true;
    }

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
    private boolean mIconBorderOn;
    private int mIconBorderColor = 0xFF908DFF;


    private boolean mMediaCoverFilterOn;
    private int mMediaCoverFilter = COVER_FILTER_NONE;
    private float mMediaCoverBlurRadius = 7.5f;
    /** Un ImageView-sfondo iniettato per istanza di pannello Media — mostra la vera copertina
     *  a grandezza intera invece del piccolo coverImg nativo (richiesta utente 2026-08-20). */
    private final WeakHashMap<Object, ImageView> mMediaCoverBackdrop = new WeakHashMap<>();
    private final WeakHashMap<Object, View> mMediaBorderView = new WeakHashMap<>();

    private int mTileRadiusBaseDp = 20, mTileRadiusHlDp = 20, mTileRadiusMediaDp = 20;
    private boolean mTileShapeOn;
    private int mTileShapePreset;

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
    // BUG 2026-09-24 ("dopo un riavvio device serve 2 volte Riavvia SystemUI perché i riquadri
    // grandi tornino giusti"): stessa famiglia già risolta per i colori icone sopra —
    // notifyQsUpdate() (vedi sotto) dipende da mPersonalityManager, non garantito pronto appena
    // dopo il boot (race tra il suo hook costruttore e il caricamento di Xprefs) — se il primo
    // tentativo (dentro updatePrefs) lo trova null, il redraw forzato non parte mai e i riquadri
    // grandi (disegnati una sola volta al bind, non riguardati finché qualcosa non forza un vero
    // redraw) restano sull'aspetto stock finché non arriva un riavvio SystemUI vero e proprio a
    // reinflazionare tutto da capo. Stessa mitigazione: un paio di retry ritardati.
    private final Runnable mDelayedQsUpdate1 = this::notifyQsUpdate;
    private final Runnable mDelayedQsUpdate2 = this::notifyQsUpdate;

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

        mIconBorderOn = Xprefs.getBoolean(KEY_ICON_BORDER_ON, false);
        mIconBorderColor = Xprefs.getBoolean(KEY_ICON_BORDER_COLOR + "_use_accent", false)
                ? appAccentColor() : Xprefs.getInt(KEY_ICON_BORDER_COLOR, 0xFF908DFF);

        mMediaCoverFilterOn = Xprefs.getBoolean(KEY_MEDIA_COVER_FILTER_ON, false);
        mMediaCoverFilter = parseInt(Xprefs.getString(KEY_MEDIA_COVER_FILTER, "0"), COVER_FILTER_NONE);
        mMediaCoverBlurRadius = (Xprefs.getInt(KEY_MEDIA_COVER_BLUR, 30) / 100f) * 25f;

        mTileRadiusBaseDp = Xprefs.getInt(KEY_TILE_RADIUS_BASE, 20);
        mTileRadiusHlDp = Xprefs.getInt(KEY_TILE_RADIUS_HL, 20);
        mTileRadiusMediaDp = Xprefs.getInt(KEY_TILE_RADIUS_MEDIA, 20);

        mTileShapeOn = Xprefs.getBoolean(KEY_TILE_SHAPE_ON, false);
        mTileShapePreset = parseInt(Xprefs.getString(KEY_TILE_SHAPE, "0"), 0);

        notifyQsUpdate();
        refreshCachedIconColors();
        mMainHandler.removeCallbacks(mDelayedQsUpdate1);
        mMainHandler.removeCallbacks(mDelayedQsUpdate2);
        mMainHandler.postDelayed(mDelayedQsUpdate1, 1000);
        mMainHandler.postDelayed(mDelayedQsUpdate2, 3000);
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
        hookMediaShapeFill(lp);
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
    /** MixColorTileDrawable costruiti per un riquadro grande 2x1 (Wi-Fi/Torcia/Pixolor/Riavvia):
     *  card intera (bounds larghi) E icona (bounds quasi quadrati) usano la stessa classe. */
    private final java.util.Map<Object, Boolean> mBigCardDrawable = new WeakHashMap<>();

    /** Le 4 icone/pulsanti in alto nel pannello (Wi-Fi/Torcia/Pixolor/Riavvia) NON sono riquadri
     *  ma la riga "quick entrance" (OplusQSQuickEntranceContainerView) — confermato dal log
     *  diagnostico 2026-09-20: owner=null, catena view ImageButton/ImageView < ... < QuickEntrance. */
    private final java.util.Map<Object, Boolean> mQuickEntranceDrawable = new WeakHashMap<>();

    /** Drawable (MixColorTileDrawable) -> View proprietaria, popolata da hookTileDrawableOwnership —
     *  serve a hookTileBgHighlight per far seguire alla NOSTRA forma l'ombra di elevazione nativa
     *  della card larga quando "Forma riquadri" è attiva (Rombo, richiesta 2026-09-24 "angoli
     *  larghi hanno un pezzo di ombra rettangolare che sporge" — l'ombra nativa segue il profilo
     *  rettangolare/nativo, non la forma disegnata a mano sul Canvas). Un primo tentativo
     *  (azzerare l'elevazione via View.setElevation) si perdeva in modo incoerente tra un riavvio
     *  e l'altro — un VALORE può sempre essere riscritto da qualcos'altro dopo di noi; un
     *  ViewOutlineProvider è una funzione consultata ogni volta che serve, non un valore one-shot,
     *  vedi mWideCardOutlineSet sotto. */
    /** Views già dotate del nostro ViewOutlineProvider per l'ombra (vedi hookTileBgHighlight) —
     *  setOutlineProvider() va chiamato una sola volta per View, invalidateOutline() invece ad
     *  ogni draw per riflettere un eventuale cambio di preset. */
    private final java.util.Set<View> mWideCardOutlineSet = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private final java.util.Map<Object, View> mWideCardOwnerView = new WeakHashMap<>();

    private boolean isQuickEntranceView(Object view) {
        Object v = view;
        for (int i = 0; i < 8 && v != null; i++) {
            if (v.getClass().getSimpleName().equals("OplusQSQuickEntranceContainerView")) return true;
            try { v = callMethod(v, "getParent"); } catch (Throwable t) { break; }
        }
        return false;
    }

    private boolean isBigCardView(Object view) {
        Object v = view;
        for (int i = 0; i < 12 && v != null; i++) {
            if (v.getClass().getName().equals("com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileViewTwoXOne")) return true;
            try { v = callMethod(v, "getParent"); } catch (Throwable t) { break; }
        }
        return false;
    }

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
                    || cn.equals("com.oplus.systemui.qs.tileimpl.OplusQSHighlightTileViewImpl")
                    // I 5 riquadri del pannello COMPRESSO (visibili insieme alle notifiche, prima di
                    // espandere le Impostazioni rapide — com.android.systemui.qs.QuickQSPanel, classe
                    // AOSP stock) usano una terza variante, mai vista prima: OplusQSTileBaseViewImpl
                    // ("Base", non "Highlight"). Senza questa riga ownerOfView() tornava null per loro
                    // e il bordo non veniva mai disegnato, con nessuna forma (bug segnalato 2026-09-22,
                    // trovato via il log diagnostico in logViewChainOnce()).
                    || cn.equals("com.oplus.systemui.qs.tileimpl.OplusQSTileBaseViewImpl")) {
                return OWNER_TILE;
            }
            if (cn.equals("com.oplus.systemui.qs.media.OplusQsBaseMediaPanelView")) {
                return OWNER_MEDIA;
            }
            try { v = callMethod(v, "getParent"); } catch (Throwable t) { break; }
        }
        return null;
    }

    // Diagnostica temporanea 2026-09-22: sui 5 riquadri visibili insieme alle notifiche (pannello
    // ridotto, prima di espandere le Impostazioni rapide complete) il bordo non compare mai, con
    // nessuna forma — segno che ownerOfView() torna null per quella riga, cioè usano una classe
    // View diversa da tutte quelle già riconosciute (Separati/Classico). Logga la catena di parent
    // una volta per ogni classe mai vista, per trovare il nome vero senza decompilare (jadx non
    // disponibile su questa macchina). Da rimuovere una volta trovata la classe giusta.
    private static final java.util.Set<String> sLoggedChains = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    // Diagnostica temporanea 2026-09-22 (seconda parte): per costruire una tabella forma->raggio
    // corretta serve sapere quale intero restituisce getLastShapeType() per ciascuna delle 6 forme
    // di "Forma tessere", e quale raggio (px) leggiamo per quella forma sia per i riquadri (tile,
    // MixColorTileDrawable.getCornerRadius) sia per i cursori (slider, mCurBackgroundRadius) — il
    // bordo dei cursori risultava "troppo squadrato" con Quadrato selezionato, stesso sospetto
    // meccanismo rotto dei riquadri. Logga una volta per ogni (contesto, forma, raggio, lato) mai
    // visto. Da rimuovere una volta costruita la tabella.
    private static final java.util.Set<String> sLoggedShapeRadius = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private void logProviderClassOnce(String callerClass, String methodName, Object provider) {
        String providerClass = provider == null ? "null" : provider.getClass().getName();
        String key = callerClass + "|" + methodName + "|" + providerClass;
        if (sLoggedShapeRadius.add("provider|" + key)) {
            dbg("DIAG outlineProvider caller=" + callerClass + " method=" + methodName + " providerClass=" + providerClass);
        }
    }

    private void logShapeRadiusOnce(String context, float radius, int side) {
        int shape = -999;
        try { if (mPersonalityManager != null) shape = (int) callMethod(mPersonalityManager, "getLastShapeType"); }
        catch (Throwable ignored) {}
        String key = context + "|" + shape + "|" + Math.round(radius) + "|" + side;
        if (sLoggedShapeRadius.add(key)) {
            dbg("DIAG shapeRadius " + context + " shapeType=" + shape + " radius=" + radius + " side=" + side);
        }
    }

    private void logViewChainOnce(String tag, Object view) {
        StringBuilder sb = new StringBuilder();
        Object v = view;
        for (int i = 0; i < 14 && v != null; i++) {
            sb.append(v.getClass().getName()).append(" > ");
            try { v = callMethod(v, "getParent"); } catch (Throwable t) { break; }
        }
        String chain = sb.toString();
        if (sLoggedChains.add(chain)) {
            dbg("DIAG owner=null (" + tag + "): " + chain);
        }
    }

    private void hookTileDrawableOwnership(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> gradBuilder = tryFindClass(lp, "com.oplus.systemui.qs.base.res.drawable.GradientTileDrawable$Builder");
        if (gradBuilder != null) {
            try {
                hookAllMethods(gradBuilder, "build", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        Object view = p.args.length > 0 ? p.args[0] : null;
                        Object result = p.getResult();
                        if (view != null && result != null) {
                            String owner = ownerOfView(view);
                            mGradientOwner.put(result, owner);
                            if (isBigCardView(view)) mBigCardDrawable.put(result, Boolean.TRUE);
                            if (isQuickEntranceView(view)) mQuickEntranceDrawable.put(result, Boolean.TRUE);
                            if (owner == null) logViewChainOnce("Gradient", view);
                        }
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
                        if (isBigCardView(view)) mBigCardDrawable.put(result, Boolean.TRUE);
                        if (isQuickEntranceView(view)) mQuickEntranceDrawable.put(result, Boolean.TRUE);
                        if (view instanceof View realView) mWideCardOwnerView.put(result, realView);
                        if (owner == null) logViewChainOnce("MixColor", view);
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

    /** Riempimento Media che segue "Forma riquadri" — tentato 2026-09-23 via hook diretto su
     *  TileTransitionDrawable.draw() (la classe trovata dietro getBg() via dump della gerarchia)
     *  e via l'esistente GradientTileDrawable.draw() con owner=media: NESSUNO dei due fira mai
     *  (diagnostica loggata zero volte nonostante il pannello Media venga aperto e ridisegnato
     *  più volte) — il vero riempimento visibile non passa da lì, ancora non trovato. ABBANDONATO
     *  per ora: resta solo il ritaglio via Outline (syncMediaShapeClip sotto), che copre bene
     *  Finestra/Ottagono (conferma utente "perfetto") ma lascia un residuo visibile sull'angolo
     *  stretto+arrotondato di Goccia/Rombo ("manca sfondo nell'angolo più stretto") — limite noto,
     *  non risolto, da riprendere con più tempo/eventualmente jadx se mai disponibile. */
    private void hookMediaShapeFill(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> transitionCls = tryFindClass(lp, "com.oplus.systemui.qs.base.res.drawable.TileTransitionDrawable");
        if (transitionCls == null) return;
        try {
            hookAllMethods(transitionCls, "draw", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!mTileShapeOn || !mTileBgMediaOn) return;
                    try {
                        android.graphics.Rect bounds = ((Drawable) p.thisObject).getBounds();
                        if (bounds.width() < dp(150) || bounds.height() < dp(150)) return;
                        android.graphics.Canvas canvas = (android.graphics.Canvas) p.args[0];
                        android.graphics.Paint fillPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                        fillPaint.setStyle(android.graphics.Paint.Style.FILL);
                        fillPaint.setColor(mTileBgMediaColor);
                        drawShaped(canvas, bounds, fillPaint);
                    } catch (Throwable t) { dbg("media shape fill failed: " + t); }
                }
            });
        } catch (Throwable t) { dbg("hookMediaShapeFill install failed: " + t); }
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
        syncMediaShapeClip(panel, backdrop);
    }

    /** "Forma riquadri" sul riempimento Media (richiesta 2026-09-23 "lo sfondo esce dal bordo"):
     *  il layer "bg" nativo (GradientTileDrawable, via View.getBg()) è disegnato prima del bordo e
     *  ridisegnarlo a mano via canvas-hook era già stato provato e abbandonato per Media (vedi
     *  javadoc di hookMediaBorder, il blur ridisegna offscreen più volte e disallinea qualunque
     *  cosa disegnata a mano) — qui usiamo invece il ritaglio nativo via Outline/clipToOutline
     *  (View API reale, non canvas), che l'engine di rendering applica da solo al momento giusto,
     *  niente allineamento da indovinare. setConvexPath() (usato inizialmente) approssima il Path
     *  con un poligono a bassa precisione — segnalato 2026-09-23 su Goccia "manca sfondo
     *  nell'angolo più stretto, media non segue il bordo" (angolo 4dp accanto a uno 30dp: il
     *  bordo, disegnato col Path esatto, tagliava netto, ma il clip approssimato arrotondava
     *  ancora quell'angolo, lasciando una mezzaluna di sfondo nativo scoperta). setPath() (API 30+,
     *  minSdk di questo modulo è 31) usa il Path esatto, nessuna approssimazione — combacia sempre
     *  col bordo. Copre sia il layer "bg" sia la copertina album (già con un suo outline fisso, qui
     *  reso dinamico). */

    private void syncMediaShapeClip(Object panel, ImageView backdrop) {
        try {
            Object bgObj = callMethod(panel, "getBg");
            if (bgObj instanceof View bg) applyShapeClip(bg);
        } catch (Throwable t) { dbg("media bg clip failed: " + t); }
        if (backdrop != null) applyShapeClip(backdrop);
    }

    private void applyShapeClip(View v) {
        // Goccia/Rombo (SHAPE_KIND_CORNERS) esclusi 2026-09-23: il clip lasciava comunque scoperto
        // l'angolo stretto ("manca sfondo nell'angolo più stretto") E in più assottigliava il
        // bordo (il riempimento sotto, ritagliato esattamente sullo stesso Path, si sovrapponeva
        // metà dello stroke — "il bordo mi sembra più sottile degli altri qs") senza risolvere
        // nulla. Poi scoperto che il problema non era solo CORNERS: anche Quadrato/Supercerchio
        // (UNIFORM) mostravano lo stesso scarto (misurato via pixel: bordo ~10dp corretto, ma il
        // riempimento restava clippato a un raggio più grande, ~20dp, indipendentemente da 3
        // tentativi diversi di fix — precisione Outline, invalidate posticipato, vero
        // OnLayoutChangeListener — nessuno ha cambiato il risultato di un solo pixel). Conclusione:
        // il vero riempimento di Media (TileTransitionDrawable dietro getBg(), confermato via
        // reflection) non risponde in modo affidabile a NESSUN controllo di clip/outline per
        // qualunque forma con un raggio diverso da quello nativo di default — SOLO Finestra/Ottagono
        // (dove il taglio è un angolo dritto, non un raggio) restano affidabili, confermato "Media
        // perfetto". Per tutto il resto rinunciamo a ritagliare il riempimento: bordo e sfondo
        // restano entrambi sul raggio nativo di default, coerenti tra loro (nessuno sporge).
        if (mTileShapeOn && TILE_SHAPE_KIND[mTileShapePreset] == SHAPE_KIND_OCTAGON) {
            v.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    if (view.getWidth() <= 0 || view.getHeight() <= 0) return;
                    outline.setPath(buildShapedPath(new android.graphics.Rect(0, 0, view.getWidth(), view.getHeight())));
                }
            });
        } else {
            v.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(mTileRadiusMediaDp));
                }
            });
        }
        v.setClipToOutline(true);
        v.invalidateOutline();
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
                // "Forma riquadri" anche sul riquadro Media (richiesta 2026-09-23 "stesso angolo") —
                // SOLO per Finestra/Ottagono (taglio ad angolo dritto, non un raggio): per ogni altra
                // forma il riempimento nativo (TileTransitionDrawable dietro getBg()) non risponde in
                // modo affidabile a nessun tentativo di ritaglio (vedi commento in applyShapeClip, 3
                // tentativi diversi falliti), quindi bordo e sfondo qui usano LO STESSO raggio nativo
                // di default — nessuno "sporge", ma "Forma riquadri" non personalizza Media in quei
                // casi (limite noto, accettato su richiesta 2026-09-23 "lascia così").
                if (mTileShapeOn && TILE_SHAPE_KIND[mTileShapePreset] == SHAPE_KIND_OCTAGON) {
                    drawShaped(canvas, new android.graphics.Rect(0, 0, getWidth(), getHeight()), paint);
                } else {
                    canvas.drawRoundRect(inset, inset, getWidth() - inset, getHeight() - inset,
                            dp(mTileRadiusMediaDp), dp(mTileRadiusMediaDp), paint);
                }
            }
        };
        v.setWillNotDraw(false);
        group.addView(v, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mMediaBorderView.put(panel, v);
        return v;
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

    /** Come isActiveDrawableState(), ma legge lo stato CORRENTE del Drawable (getState(), la
     *  stessa API standard Android che alimenta onStateChange) invece dello stateSpec di una
     *  singola voce della mappa colori — serve in draw(), dove non abbiamo una DrawableState
     *  key sottomano, solo il Drawable stesso. Usato per "Forma riquadri" (riempimento a mano). */
    private boolean isDrawableStateActive(Drawable d, XC_LoadPackage.LoadPackageParam lp) {
        try {
            if (mTileViewFlagActiveAttr == null) {
                Class<?> tileViewFlagCls = tryFindClass(lp, "com.oplus.systemui.qs.base.res.model.TileViewFlag");
                Object activeFlag = getStaticObjectField(tileViewFlagCls, "Active");
                mTileViewFlagActiveAttr = (int) callMethod(activeFlag, "getAttr");
            }
            for (int s : d.getState()) if (s == mTileViewFlagActiveAttr) return true;
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
                    android.graphics.Rect gb = ((Drawable) p.thisObject).getBounds();
                    boolean gWide = gb.width() > gb.height() * 1.3f;
                    boolean gIcon = mQuickEntranceDrawable.containsKey(p.thisObject)
                            || (!gWide && mBigCardDrawable.containsKey(p.thisObject));
                    // "Forma riquadri" sui riquadri grandi ABBANDONATA 2026-09-23 (vedi commento
                    // gemello in hookTileBgHighlight): restano 100% stock, pillola/ovale nativa.
                    boolean gBorderOn = gIcon ? mIconBorderOn : (mTileBorderOn && OWNER_TILE.equals(mGradientOwner.get(p.thisObject)));
                    if (!gBorderOn) return;
                    try {
                        android.graphics.Canvas canvas = (android.graphics.Canvas) p.args[0];
                        android.graphics.Rect bounds = gb;
                        float radius = 0f;
                        try { radius = (float) callMethod(p.thisObject, "getCornerRadius"); } catch (Throwable ignored) {}
                        if (gBorderOn) {
                            float strokeWidth = tileBorderWidthPx();
                            float inset = strokeWidth / 2f;
                            android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                            paint.setStyle(android.graphics.Paint.Style.STROKE);
                            paint.setStrokeWidth(strokeWidth);
                            paint.setColor(gIcon ? mIconBorderColor : mTileBorderColor);
                            canvas.drawRoundRect(bounds.left + inset, bounds.top + inset,
                                    bounds.right - inset, bounds.bottom - inset, radius, radius, paint);
                        }
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
                    boolean quickIcon = mQuickEntranceDrawable.containsKey(p.thisObject);
                    if (!quickIcon && !OWNER_TILE.equals(mMixColorOwner.get(p.thisObject))) return;
                    android.graphics.Rect boundsProbe = ((Drawable) p.thisObject).getBounds();
                    boolean wideCard = boundsProbe.width() > boundsProbe.height() * 1.3f;
                    // Icona di un riquadro grande (bounds quasi quadrati, drawable di un 2x1):
                    // ha un interruttore suo ("Bordo icone" in Riquadri grandi), separato dal
                    // "Bordo riquadri" che disegna la card intera e i riquadri piccoli.
                    // "Bordo icone" (dentro Riquadri grandi) = solo i bordi ROTONDI: icone dei riquadri
                    // grandi 2x1 e pulsanti rotondi in alto (quick entrance). Le card rettangolari
                    // (larghe) e i riquadri piccoli restano sotto "Bordo riquadri" (richiesta 2026-09-20).
                    boolean bigCardIcon = quickIcon || (!wideCard && mBigCardDrawable.containsKey(p.thisObject));
                    boolean smallRoundTile = !bigCardIcon && !wideCard;
                    // "Forma riquadri" (2026-09-22, riquadri piccoli tondi soltanto): tentare di
                    // leggere/scrivere il raggio nativo (getCornerRadius(), RoundRectOutlineProvider)
                    // per QUESTI riquadri è stato abbandonato — diagnostica via log ha confermato che
                    // il loro riempimento è un drawOval FISSO nel codice nativo, che non consulta
                    // nessun raggio: cambiarlo non ha mai avuto alcun effetto visibile. Fix reale:
                    // disegniamo NOI il riempimento sopra (stesso trucco già usato per il bordo),
                    // nel colore che "Sfondo riquadri" ha già impostato per lo stato corrente — per
                    // questo richiede che "Sfondo riquadri (in evidenza)" sia attivo: è l'unico modo
                    // per conoscere il colore vero senza reimplementare anche il blur nativo.
                    // "Riquadri grandi" (CARD larga ~414x186 e relativa ICONA ~186x186, stessa classe):
                    // "Forma riquadri" sulla card intera RIPRISTINATA 2026-09-23 ora che il
                    // riempimento è disegnato a mano NOI (vedi neutralizeWideFill sotto, confermato
                    // funzionante via test rosso acceso) — il problema originale ("lo sfondo esce dal
                    // bordo") era il riempimento NATIVO non tagliato, non più un limite qui: la nostra
                    // forma è la stessa Path sia per riempimento che per bordo, combaciano sempre.
                    boolean useOwnShape = mTileShapeOn && (smallRoundTile || wideCard);
                    boolean shapeFillOn = useOwnShape && smallRoundTile && mTileBgHighlightOn;
                    boolean borderOn = bigCardIcon ? mIconBorderOn : mTileBorderOn;
                    // Riquadri grandi (riga in alto Wi-Fi/Torcia/Pixolor/Riavvia): quando un riquadro
                    // è "attivo" (es. Wi-Fi connesso) OOS riempie l'INTERA card di accento — 100%
                    // nativo, il nostro "Sfondo riquadri" non la tocca affatto per questa riga
                    // (ownerOfView non la riconosce, torna null: non è un tile normale né Media, vedi
                    // isQuickEntranceView). Richiesto 2026-09-23 "solo icona ad avere sfondo accento,
                    // non tutto il pulsante" — qui SEMPRE (indipendente da switch/Forma riquadri):
                    // ridisegniamo la card intera nel tono scuro neutro nativo (misurato via pixel su
                    // una card mai attiva, es. Torcia), coprendo l'accento nativo; l'icona (drawable
                    // separato, disegnato dopo) resta colorata normalmente. Segue "Forma riquadri"
                    // quando attiva (stessa Path del bordo sotto), altrimenti pillola/ovale nativa.
                    boolean neutralizeWideFill = wideCard;
                    if (!shapeFillOn && !borderOn && !neutralizeWideFill) return;
                    // Segnalato 2026-09-24 su Finestra/Ottagono/Rombo ("angoli/bordo con alone che
                    // sporge"): NON è l'ombra di elevazione della View (verificato — setElevation(0)
                    // reattivo, hook globale su setElevation, Outline esatta via setPath, e
                    // outline.setAlpha(0) hanno tutti fallito allo stesso modo su un'apertura REALE
                    // del pannello). È un alone del blur nativo del drawable stesso, indipendente da
                    // qualunque cosa disegniamo — visibile solo dove la NOSTRA forma taglia via più
                    // area di quanta ne copra quel blur. Vedi buildWideCardShapePath/
                    // wideCardNeedsSafeShape sopra: per queste 3 forme bordo+riempimento+ombra usano
                    // tutti lo stesso rettangolo arrotondato "sicuro", abbastanza vicino al blur
                    // nativo da coprirlo sempre. L'OutlineProvider (sotto) resta comunque il modo
                    // giusto per far seguire all'ombra la forma scelta, a prescindere dal resto.
                    if (wideCard && mTileShapeOn) {
                        View ownerView = mWideCardOwnerView.get(p.thisObject);
                        if (ownerView != null) {
                            // setOutlineProvider() una sola volta (il lambda legge i campi
                            // mTileShapeOn/mTileShapePreset dal vivo ad ogni chiamata, non serve
                            // un nuovo Provider ad ogni cambio forma) — invalidateOutline() invece
                            // SEMPRE, per forzare il ricalcolo con la forma/preset CORRENTE ogni
                            // volta che questa card ridisegna (es. dopo un cambio preset).
                            if (mWideCardOutlineSet.add(ownerView)) {
                                ownerView.setOutlineProvider(new ViewOutlineProvider() {
                                    @Override public void getOutline(View view, Outline outline) {
                                        if (view.getWidth() <= 0 || view.getHeight() <= 0) return;
                                        outline.setPath(buildWideCardShapePath(new android.graphics.Rect(0, 0, view.getWidth(), view.getHeight())));
                                    }
                                });
                            }
                            ownerView.invalidateOutline();
                        }
                    } else if (wideCard) {
                        // "Forma riquadri" spenta dopo essere stata accesa: il Provider a forma
                        // resterebbe agganciato per sempre altrimenti (questo ramo del draw() non
                        // passa più di qui una volta spento, quindi nessuno lo toglierebbe da solo).
                        View ownerView = mWideCardOwnerView.get(p.thisObject);
                        if (ownerView != null && mWideCardOutlineSet.remove(ownerView)) {
                            ownerView.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
                            ownerView.invalidateOutline();
                        }
                    }
                    try {
                        if (neutralizeWideFill) {
                            android.graphics.Canvas nc = (android.graphics.Canvas) p.args[0];
                            android.graphics.Paint nPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                            nPaint.setStyle(android.graphics.Paint.Style.FILL);
                            nPaint.setColor(WIDE_CARD_NEUTRAL_FILL);
                            if (mTileShapeOn) {
                                drawWideCardShape(nc, boundsProbe, nPaint);
                            } else {
                                float nRadius = boundsProbe.height() / 2f;
                                try { nRadius = (float) callMethod(p.thisObject, "getCornerRadius"); } catch (Throwable ignored) {}
                                nc.drawRoundRect(boundsProbe.left, boundsProbe.top, boundsProbe.right, boundsProbe.bottom,
                                        nRadius, nRadius, nPaint);
                            }
                        }
                    } catch (Throwable t) { dbg("wide card neutral fill failed: " + t); }
                    try {
                        android.graphics.Canvas canvas = (android.graphics.Canvas) p.args[0];
                        android.graphics.Rect bounds = boundsProbe;
                        float radius;
                        if (useOwnShape) {
                            // Raggio NOSTRO diretto (dp fisso per preset), non letto dal sistema —
                            // getCornerRadius()/RoundRectOutlineProvider si sono rivelati inaffidabili
                            // per questi riquadri, quindi non li consultiamo più qui. Rombo/Goccia/
                            // Finestra usano drawShaped() (Path dedicato), non un raggio semplice.
                            radius = dp(tileShapePresetRadiusDp());
                        } else {
                            // Le card larghe 2 colonne (Wi-Fi/Torcia/Pixolor/Riavvia) condividono la
                            // STESSA classe anche per l'intera card (bounds ~414x186, non solo l'icona
                            // ~186x186), quindi un'ellisse sarebbe schiacciata — lì disegniamo un bordo
                            // arrotondato (pill) via getCornerRadius() invece, come per GradientTileDrawable.
                            radius = bounds.height() / 2f; // fallback: cerchio, uguale al comportamento precedente
                            try { radius = (float) callMethod(p.thisObject, "getCornerRadius"); } catch (Throwable ignored) {}
                            // Toppa 2026-09-22 per il solo caso nativo Quadrato (Impostazioni > Notifiche
                            // e Impostazioni rapide > Impostazioni rapide > Modalità classica), quando
                            // "Forma riquadri" nostra non è attiva: getCornerRadius() legge lo STESSO
                            // valore per Predefinito e Quadrato (non li distingue), qui corretto col
                            // valore misurato a schermo (~23% del lato). Le altre 4 forme native restano
                            // fuori portata (leggono sempre 0 o non cambiano mai).
                            if (smallRoundTile) {
                                int shapeType = -999;
                                try { if (mPersonalityManager != null) shapeType = (int) callMethod(mPersonalityManager, "getLastShapeType"); }
                                catch (Throwable ignored) {}
                                if (shapeType == 1) radius = bounds.height() * 0.226f;
                            }
                        }
                        logShapeRadiusOnce("tile", radius, bounds.height());
                        if (shapeFillOn) {
                            boolean fillActive = isDrawableStateActive((Drawable) p.thisObject, lp);
                            int fillColor = fillActive ? (mTileBgHlAccent ? appAccentColor() : mTileBgHlActive) : mTileBgHlInactive;
                            android.graphics.Paint fillPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                            fillPaint.setStyle(android.graphics.Paint.Style.FILL);
                            fillPaint.setColor(fillColor);
                            if (useOwnShape) drawShaped(canvas, bounds, fillPaint);
                            else canvas.drawRoundRect(bounds.left, bounds.top, bounds.right, bounds.bottom, radius, radius, fillPaint);
                        }
                        if (borderOn) {
                            float strokeWidth = tileBorderWidthPx();
                            float inset = strokeWidth / 2f;
                            android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                            paint.setStyle(android.graphics.Paint.Style.STROKE);
                            paint.setStrokeWidth(strokeWidth);
                            paint.setColor(bigCardIcon ? mIconBorderColor : mTileBorderColor);
                            // inset non applicato: trascurabile per un bordo sottile
                            if (useOwnShape) { if (wideCard) drawWideCardShape(canvas, bounds, paint); else drawShaped(canvas, bounds, paint); }
                            else canvas.drawRoundRect(bounds.left + inset, bounds.top + inset,
                                    bounds.right - inset, bounds.bottom - inset, radius, radius, paint);
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
                    Object provider = p.args.length > 0 ? p.args[0] : null;
                    // Diagnostica temporanea 2026-09-22: "Forma riquadri" cambia i riquadri grandi in
                    // Separati ma non i piccoli tondi, e niente affatto in Classico — sospetto che
                    // quei casi ricevano un provider di classe diversa da RoundRectOutlineProvider
                    // (quindi il controllo sotto scarta la chiamata in silenzio). Logga la classe
                    // vera una volta per ogni (classe chiamante, metodo, classe provider) mai vista —
                    // PRIMA del controllo mTileShapeOn/mTileBgXxxOn, per vedere anche se il metodo
                    // nativo viene proprio chiamato quando l'interruttore nostro è spento.
                    logProviderClassOnce(className, methodName, provider);
                    if (!onSupplier.getAsBoolean()) return;
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
            hookOutlineUpdater(lp, cls, "updateTileOutline",
                    () -> mTileBgBaseOn || mTileShapeOn,
                    () -> mTileShapeOn ? tileShapePresetRadiusDp() : mTileRadiusBaseDp);
            hookOutlineUpdater(lp, cls, "updateHighLightTileOutline",
                    () -> mTileBgHighlightOn || mTileShapeOn,
                    () -> mTileShapeOn ? tileShapePresetRadiusDp() : mTileRadiusHlDp);
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
                    if (p.args.length == 0) return;
                    int color;
                    if (mBrightnessCustomOn && mBrightnessMode != 0) {
                        color = mBrightnessMode == 1 ? appAccentColor() : mBrightnessColor;
                    } else if (mTileShapeOn) {
                        // "Forma riquadri" sui cursori (2026-09-23): forzare isSupportMixColor=false
                        // (vedi updateColor sotto) fa passare al ramo a colore piatto, ma senza
                        // customizzazione esplicita il valore nativo di mProgressColorStateList non
                        // è mai stato inizializzato per QUESTO ramo (su questo device è sempre stato
                        // mix-color) — un default sensato qui evita un colore sbagliato/trasparente.
                        color = appAccentColor();
                    } else return;
                    p.args[0] = ColorStateList.valueOf(color);
                }
            });
        } catch (Throwable t) { dbg("setProgressColor hook failed: " + t); }

        try {
            hookAllMethods(seekBar, "setSeekBarBackgroundColor", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!p.thisObject.getClass().getName().contains("OplusQsVerticalSeekBar")) return;
                    if (p.args.length == 0) return;
                    int color;
                    if (mBrightnessBgOn) {
                        color = mBrightnessBgColor;
                    } else if (mTileShapeOn) {
                        color = TILE_SHAPE_SLIDER_BG_FALLBACK; // stesso motivo del default sopra
                    } else return;
                    p.args[0] = ColorStateList.valueOf(color);
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
                        // "Forma riquadri" sui cursori (2026-09-23, round 3): confermato via
                        // reflection ("DIAG slider fields") che il riempimento passa da
                        // activeMixColorDrawable/baseMixColorDrawable quando isDrawingWithMixColor è
                        // true — stessa famiglia "MixColor" dei riquadri tondi, blur in tempo reale
                        // che ridisegna offscreen e ignora sia canvas-clip sia View clipToOutline
                        // (stesso motivo per cui Media va in overlay View invece che canvas-hook).
                        // Forzare qui isSupportMixColor=false (stesso trucco già usato per
                        // "Colore cursore" custom) fa passare il cursore al ramo a colore piatto
                        // (drawActiveTrack/mProgressPaint/mBackgroundPaint, radius reale) — quello
                        // che il nostro clipToOutline (hookSliderBorder sopra) può davvero tagliare.
                        if (!mBrightnessCustomOn && !mBrightnessBgOn && !mTileShapeOn) return;
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
                // "Forma riquadri" sui cursori: il riempimento (sfondo + barra progresso) NON è
                // disegnato da questo onDraw — è il background Drawable della View, dipinto PRIMA
                // (View.draw(): drawBackground() gira prima di onDraw()), quindi un clipPath sul
                // Canvas qui dentro non ha mai effetto su di lui (tentativo 2026-09-23, "i cursori
                // sono uguali allo screen di prima" — stesso riempimento che esce dagli angoli).
                // Fix reale: come per Media, ritaglio via Outline/clipToOutline (applicato
                // dall'engine PRIMA di qualunque fase di disegno della View, background incluso),
                // non un canvas-hook. Il nostro bordo (disegnato sotto, ancora in questo stesso
                // onDraw) resta comunque leggermente rifilato sul bordo esterno dallo stesso
                // outline — trascurabile per uno stroke da 1.5dp.
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!(p.thisObject instanceof View sb) || !qsSeekBarCls.isInstance(p.thisObject)) return;
                    try {
                        if (mTileShapeOn) {
                            Object rectObj = getObjectField(p.thisObject, "mBackgroundRect");
                            if (rectObj instanceof android.graphics.Rect rect) {
                                sb.setOutlineProvider(new ViewOutlineProvider() {
                                    @Override public void getOutline(View view, Outline outline) {
                                        outline.setPath(buildShapedPath(rect));
                                    }
                                });
                                sb.setClipToOutline(true);
                                sb.invalidateOutline();
                            }
                        } else if (sb.getClipToOutline()) {
                            sb.setClipToOutline(false);
                        }
                    } catch (Throwable t) { dbg("slider clip failed: " + t); }
                }

                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!mTileBorderOn || !qsSeekBarCls.isInstance(p.thisObject)) return;
                    try {
                        android.graphics.Canvas canvas = (android.graphics.Canvas) p.args[0];
                        Object rectObj = getObjectField(p.thisObject, "mBackgroundRect");
                        if (!(rectObj instanceof android.graphics.Rect rect)) return;
                        if (mTileShapeOn) {
                            // Riempimento (sfondo + barra progresso) — il clipToOutline sopra taglia
                            // solo il layer di sfondo, non la barra progresso: campo dedicato
                            // mClipProgressPath/mProgressRect confermato via reflection, disegnata a
                            // parte con un proprio raggio (mCurProgressRadius) che ignora il nostro
                            // outline — segnalato 2026-09-23 "il fondo è ok ma la barra esce ancora
                            // dal bordo in basso". Fix: ridisegniamo NOI le due metà (stessa tecnica
                            // dei riquadri tondi), intersecando la forma scelta con un rettangolo
                            // superiore/inferiore via Path.op — gestisce da solo angoli/chamfer
                            // qualunque sia la forma, niente geometria duplicata a mano.
                            try {
                                int progress = (int) callMethod(p.thisObject, "getProgress");
                                int max = (int) callMethod(p.thisObject, "getMax");
                                float frac = max > 0 ? Math.max(0f, Math.min(1f, progress / (float) max)) : 0f;
                                float splitY = rect.top + rect.height() * (1f - frac);

                                int bgColor = mBrightnessBgOn ? mBrightnessBgColor : TILE_SHAPE_SLIDER_BG_FALLBACK;
                                int progressColor = (mBrightnessCustomOn && mBrightnessMode != 0)
                                        ? (mBrightnessMode == 1 ? appAccentColor() : mBrightnessColor)
                                        : appAccentColor();

                                android.graphics.Path shape = buildShapedPath(rect);
                                android.graphics.RectF upper = new android.graphics.RectF(rect.left, rect.top, rect.right, splitY);
                                android.graphics.RectF lower = new android.graphics.RectF(rect.left, splitY, rect.right, rect.bottom);
                                android.graphics.Paint fillPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                                fillPaint.setStyle(android.graphics.Paint.Style.FILL);

                                if (upper.height() > 0) {
                                    android.graphics.Path upperRectPath = new android.graphics.Path();
                                    upperRectPath.addRect(upper, android.graphics.Path.Direction.CW);
                                    android.graphics.Path bgPath = new android.graphics.Path();
                                    bgPath.op(shape, upperRectPath, android.graphics.Path.Op.INTERSECT);
                                    fillPaint.setColor(bgColor);
                                    canvas.drawPath(bgPath, fillPaint);
                                }
                                if (lower.height() > 0) {
                                    android.graphics.Path lowerRectPath = new android.graphics.Path();
                                    lowerRectPath.addRect(lower, android.graphics.Path.Direction.CW);
                                    android.graphics.Path progPath = new android.graphics.Path();
                                    progPath.op(shape, lowerRectPath, android.graphics.Path.Op.INTERSECT);
                                    fillPaint.setColor(progressColor);
                                    canvas.drawPath(progPath, fillPaint);
                                }
                            } catch (Throwable t) { dbg("slider shaped fill failed: " + t); }
                        }
                        float strokeWidth = tileBorderWidthPx();
                        float inset = strokeWidth / 2f;
                        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                        paint.setStyle(android.graphics.Paint.Style.STROKE);
                        paint.setStrokeWidth(strokeWidth);
                        paint.setColor(mTileBorderColor);
                        if (mTileShapeOn) {
                            // Bordo disegnato SOPRA (fuori dal clip, già ripristinato sopra, e sopra
                            // il riempimento appena ridisegnato) — stesso Path del clip/riempimento,
                            // quindi combacia esattamente.
                            drawShaped(canvas, rect, paint);
                        } else {
                            float radius = getFloatField(p.thisObject, "mCurBackgroundRadius");
                            canvas.drawRoundRect(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset,
                                    radius, radius, paint);
                        }
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
