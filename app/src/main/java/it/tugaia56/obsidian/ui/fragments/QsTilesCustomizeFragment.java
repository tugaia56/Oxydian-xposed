package it.tugaia56.obsidian.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.DarkShadowColorListener;
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SliderWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.utils.ObsidianTheme;
import it.tugaia56.obsidian.utils.ObsidianTheme.GroupPos;
import it.tugaia56.obsidian.ui.events.ColorSelectedEvent;
import it.tugaia56.obsidian.ui.models.DarkShadowItem;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.xposed.hooks.systemui.QsSeparateMod;

/**
 * Personalizza Riquadri — porting UI/prefs reale di OC's QuickSettingsCustomization
 * (quick_settings_tiles_customizations_prefs.xml, 22 chiavi, stesse qui).
 *
 * Hook reale collegato (QsTilesCustomizeMod) per: Etichette, Cursore Luminosità
 * (colore/sfondo/icona scura/sfocatura/raggio — quest'ultimo è il raggio del CURSORE, non dei
 * riquadri, nome ereditato da OC ma comportamento verificato nel sorgente), Sfondo Riquadri
 * base/in evidenza/media (colore attivo/inattivo + raggio angoli — meccanismo reale trovato via
 * decompilazione di SystemUI.apk, GradientTileDrawable/MixColorTileDrawable, non quello di OC
 * che su questa build non esiste — 2026-08-19, confermato universale Classico+Separati il
 * 2026-08-20). Colori Icone vive invece in QsSeparateModsFragment: non ancora verificato se
 * funziona anche con "Classico" nativo OOS, quindi resta lì finché non si controlla.
 *
 * Animazione e Transizioni restano SOLO UI/prefs, nessun hook — l'utente non le usa e le ha
 * chieste di lasciare da parte per ora (2026-08-15).
 */
public class QsTilesCustomizeFragment extends Fragment {

    // ── Animazione ───────────────────────────────────────────────────────────
    private static final String KEY_ANIM_STYLE       = "qs_tile_animation_style";
    private static final String KEY_ANIM_DURATION    = "qs_tile_animation_duration";
    private static final String KEY_ANIM_INTERPOLATOR = "qs_tile_animation_interpolator";

    // ── Transizioni ──────────────────────────────────────────────────────────
    private static final String KEY_TRANSITIONS_ON = "qs_transitions_title_switch";
    private static final String KEY_TRANSITIONS    = "qs_tile_transformations";

    // ── Etichette ────────────────────────────────────────────────────────────
    private static final String KEY_HIDE_LABELS   = "qs_hide_labels";
    private static final String KEY_LABEL_COLOR_ON = "qs_tile_label_enabled";
    private static final String KEY_LABEL_COLOR    = "qs_tile_label";

    // ── Cursore Luminosità ───────────────────────────────────────────────────
    /** "0"=predefinito "1"=scura "2"=bianca — sostituisce il vecchio switch booleano di OC
     *  (solo scura/predefinita), su richiesta esplicita di poter forzare anche il bianco. */
    private static final String KEY_BRIGHTNESS_ICON_MODE = "qs_brightness_icon_mode";
    private static final String KEY_BRIGHTNESS_ICON_COLOR = "qs_brightness_icon_custom_color";
    /** Stessa preferenza di VolumePanelMod.PREF_ICON_MODE/PREF_ICON_COLOR — vedi nota sopra. */
    private static final String KEY_VOLUME_ICON_MODE  = "qs_volume_icon_mode";
    private static final String KEY_VOLUME_ICON_COLOR = "qs_volume_icon_custom_color";
    private static final String KEY_BRIGHTNESS_CUSTOM_ON = "customize_brightness_slider";
    private static final String KEY_BRIGHTNESS_MODE      = "brightness_slider_progress_color_mode";
    private static final String KEY_BRIGHTNESS_COLOR     = "brightness_slider_color";
    private static final String KEY_BRIGHTNESS_BG_ON     = "brightness_slider_background_color_enabled";
    private static final String KEY_BRIGHTNESS_BG_COLOR  = "brightness_slider_background_color";

    // ── Raggio riquadri ──────────────────────────────────────────────────────
    private static final String KEY_RADIUS_ON = "qs_sliders_radius_switch";
    private static final String KEY_RADIUS    = "qs_sliders_radius";

    // ── Interruttore master "Cursori Impostazioni Rapide" ───────────────────
    private static final String KEY_SLIDERS_ON = "qs_sliders_customize_enabled";

    // ── Colori Icone (2 swatch: attivo/inattivo — "disabilitato" tolto, mai funzionante) ──
    // Confermato universale (Classico + Separati) il 2026-08-20 — OplusQSIconView (Separati) e
    // OplusQSIconViewImpl (Classico, package .qs.tileimpl) hanno lo stesso schema
    // setIcon/onIconTintUpdate, entrambe agganciate dallo stesso hookIconColors nel Mod.
    private static final String KEY_ICON_COLORS_ON     = "qs_custom_icon_colors";
    private static final String KEY_ICON_ACTIVE_ACCENT = "qs_custom_icon_active_accent_color";
    private static final String[] ICON_COLOR_KEYS = {
            "qs_custom_icon_active_color", "qs_custom_icon_inactive_color",
    };

    // ── Sfondo riquadri (base / in evidenza / media) ────────────────────────
    // Meccanismo reale (GradientTileDrawable/MixColorTileDrawable via decompilazione) confermato
    // universale — funziona sia con "Classico" che "Separati" nativo OOS (2026-08-20), quindi
    // resta qui in generale invece che sotto QsSeparateModsFragment. Media ha un solo colore
    // (non attivo/inattivo): la sua vista non ha mai uno stato "attivo" reale (confermato via
    // log: stateListConfig ha una sola voce WILD_CARD).
    private static final String KEY_TILE_BG_BASE_ON     = "qs_tile_bg_base_enabled";
    private static final String KEY_TILE_BG_BASE_ACCENT = "qs_tile_bg_base_active_accent";
    private static final String[] TILE_BG_BASE_COLOR_KEYS = {
            "qs_tile_bg_base_active_color", "qs_tile_bg_base_inactive_color",
    };
    private static final String KEY_TILE_BG_HL_ON     = "qs_tile_bg_highlight_enabled";
    private static final String KEY_TILE_BG_HL_ACCENT = "qs_tile_bg_highlight_active_accent";
    private static final String[] TILE_BG_HL_COLOR_KEYS = {
            "qs_tile_bg_highlight_active_color", "qs_tile_bg_highlight_inactive_color",
    };
    private static final String KEY_TILE_BG_MEDIA_ON = "qs_tile_bg_media_enabled";
    private static final String KEY_TILE_BG_MEDIA_COLOR = "qs_tile_bg_media_inactive_color";
    // Bordo pulsanti QS (2026-09-16) — un unico switch+colore, pulsanti+cursori+media (2026-09-17).
    private static final String KEY_TILE_BORDER_ON    = "qs_tile_border_enabled";
    private static final String KEY_TILE_BORDER_COLOR = "qs_tile_border_custom_color";
    // Bordo icone dei riquadri grandi 2x1 (Wi-Fi/Torcia/Pixolor/Riavvia), 2026-09-20: switch/colore
    // separati dal "Bordo riquadri", stanno dentro "Riquadri grandi": coprono i 4 pulsanti in alto,
    // le icone e le card dei riquadri grandi ("Bordo riquadri" resta per i riquadri piccoli).
    private static final String KEY_ICON_BORDER_ON    = "qs_icon_border_enabled";
    private static final String KEY_ICON_BORDER_COLOR = "qs_icon_border_custom_color";
    // Copertina Album (filtro sulla vera artwork del brano, stessa tecnica/opzioni di
    // AlbumArtLockscreenMod — grayscale/accento/blur/grayscale+blur, riuso stringhe esistenti.
    private static final String KEY_MEDIA_COVER_FILTER_ON = "qs_tile_media_cover_filter_enabled";
    private static final String KEY_MEDIA_COVER_FILTER    = "qs_tile_media_cover_filter"; // "0".."4"
    private static final String KEY_MEDIA_COVER_BLUR      = "qs_tile_media_cover_blur";   // 0-100

    // ── Impostazioni Rapide Separati (ex QsSeparateModsFragment, ora sezione inline) ────
    private static final String KEY_SEP_HIDE_EDIT  = "OBS_QS_SEPARATE_HIDE_EDIT";
    private static final String KEY_SEP_HIDE_MENU  = "OBS_QS_SEPARATE_HIDE_MENU";
    private static final String KEY_SEP_WIDTH_ON   = "OBS_QS_SEPARATE_WIDTH_ON";
    private static final String KEY_SEP_WIDTH_VAL  = "OBS_QS_SEPARATE_WIDTH_VALUE";
    private static final String KEY_SEP_ON         = "OBS_QS_SEPARATE_MASTER_ON";
    private static final String KEY_TILE_RADIUS_BASE  = "qs_tile_radius_base_dp";
    private static final String KEY_TILE_RADIUS_HL    = "qs_tile_radius_highlight_dp";
    private static final String KEY_TILE_RADIUS_MEDIA = "qs_tile_radius_media_dp";
    // Forma riquadri (2026-09-22): indipendente da "Forma tessere" nativa OOS, che nello stile
    // Separati non si applica affatto ai riquadri (restano sempre tondi) e nei riquadri della
    // versione Classico è comunque inaffidabile (Predefinito e Quadrato letti come identici, le
    // altre 3 forme mai applicate — vedi QsTilesCustomizeMod). Riusa il meccanismo GIA' nostro e
    // funzionante di "Raggio angoli riquadri" (RoundRectOutlineProvider), che vale per entrambi
    // gli stili — tre preset invece di un valore dp libero, stessa idea della scelta nativa ma
    // affidabile. Copre solo le forme rappresentabili con un raggio d'angolo (Cerchio/Quadrato/
    // Supercerchio) — Finestra e Rombo no, richiederebbero un contorno personalizzato.
    private static final String KEY_TILE_SHAPE_ON = "qs_tile_shape_enabled";
    private static final String KEY_TILE_SHAPE    = "qs_tile_shape_preset"; // "0" Cerchio, "1" Quadrato, "2" Supercerchio

    private RecyclerView mRv;
    private final List<DarkShadowItem> mIconColorItems = new ArrayList<>();
    private final List<DarkShadowItem> mTileBgBaseColorItems = new ArrayList<>();
    private final List<DarkShadowItem> mTileBgHlColorItems = new ArrayList<>();
    private DarkShadowColorListener mIconColorAdapter;
    private DarkShadowColorListener mTileBgBaseColorAdapter;
    private DarkShadowColorListener mTileBgHlColorAdapter;
    // Stato SOLO visivo (non persistito) — vedi nota in QsSeparateModsFragment: lo switch
    // attiva soltanto, il tocco sul nome apre/chiude le opzioni sottostanti.
    private boolean mIconExpanded    = false;
    private boolean mBgBaseExpanded  = false;
    private boolean mBgHlExpanded    = false;
    private boolean mBgMediaExpanded = false;
    private boolean mBorderExpanded  = false;
    private boolean mShapeExpanded   = false;
    private boolean mSlidersExpanded = false;
    // Stato SOLO visivo — header senza switch, tocco sul nome apre/chiude, stesso pattern
    // di collapsibleHeader in LockscreenWidgetsFragment.
    private boolean mSliderIconColorsExpanded;
    private boolean mSepExpanded     = false;
    private boolean mSepBtnBgExpanded = false;
    /** dialogId -> pref key, per i due swatch singoli del Cursore Luminosità (non passano
     *  per DarkShadowItem/onColorSelected sopra, servono qui per sapere dove salvare). */
    private final java.util.Map<Integer, String> mSingleColorKeys = new java.util.HashMap<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setPadding(0, 12, 0, 24);
        rv.setClipToPadding(false);
        return rv;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mRv = (RecyclerView) view;
        rebuild();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    private void rebuild() {
        mIconColorItems.clear();
        mTileBgBaseColorItems.clear();
        mTileBgHlColorItems.clear();
        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();

        // ── Sfondo riquadri (base = rettangolari, in evidenza = circolari, media) ────────
        // Un solo interruttore per categoria: attiva/disattiva, il tocco sul nome apre/chiude
        // le opzioni (colore/raggio/accento) sottostanti. I tre switch condividono UNA sola
        // card (uno sfondo unico) invece di tre separate — Colori Icone e Cursori sono stati
        // spostati qui sotto su richiesta esplicita (2026-08-21).
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.qs_tiles_bg_section))));
        SwitchWidgetAdapter.SwitchItem bgBaseSwitch = prefSwitch(getString(R.string.qs_tiles_jump_base),
                getString(R.string.qs_tiles_jump_base_summary), KEY_TILE_BG_BASE_ON);
        bgBaseSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_TILE_BG_BASE_ON, bgBaseSwitch.checked);
            mBgBaseExpanded = bgBaseSwitch.checked;
            rebuild();
        };
        bgBaseSwitch.onRowClick = () -> { mBgBaseExpanded = !mBgBaseExpanded; rebuild(); };
        SwitchWidgetAdapter.SwitchItem bgHlSwitch = prefSwitch(getString(R.string.qs_tiles_jump_highlight), null, KEY_TILE_BG_HL_ON);
        bgHlSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_TILE_BG_HL_ON, bgHlSwitch.checked);
            mBgHlExpanded = bgHlSwitch.checked;
            rebuild();
        };
        bgHlSwitch.onRowClick = () -> { mBgHlExpanded = !mBgHlExpanded; rebuild(); };
        SwitchWidgetAdapter.SwitchItem borderSwitch = prefSwitch(getString(R.string.qs_tiles_border_title), null, KEY_TILE_BORDER_ON);
        borderSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_TILE_BORDER_ON, borderSwitch.checked);
            mBorderExpanded = borderSwitch.checked;
            rebuild();
        };
        borderSwitch.onRowClick = () -> { mBorderExpanded = !mBorderExpanded; rebuild(); };
        SwitchWidgetAdapter.SwitchItem bgMediaSwitch = prefSwitch(getString(R.string.qs_tiles_jump_media), null, KEY_TILE_BG_MEDIA_ON);
        bgMediaSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_TILE_BG_MEDIA_ON, bgMediaSwitch.checked);
            mBgMediaExpanded = bgMediaSwitch.checked;
            rebuild();
        };
        bgMediaSwitch.onRowClick = () -> { mBgMediaExpanded = !mBgMediaExpanded; rebuild(); };
        // Default true (non false come prefSwitch()): il Mod tratta questo master come attivo
        // finché non viene esplicitamente spento, per non disabilitare in silenzio le
        // personalizzazioni già configurate da chi aggiorna da prima che esistesse.
        SwitchWidgetAdapter.SwitchItem slidersSwitch = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.qs_tiles_sliders_master_title), null,
                ObsidianPrefs.getBoolean(KEY_SLIDERS_ON, true), null);
        slidersSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_SLIDERS_ON, slidersSwitch.checked);
            mSlidersExpanded = slidersSwitch.checked;
            rebuild();
        };
        slidersSwitch.onRowClick = () -> { mSlidersExpanded = !mSlidersExpanded; rebuild(); };

        // Le opzioni di ogni riquadro si aprono SUBITO SOTTO il suo switch (non tutte in fondo
        // alla card) — accumula in "pending" finché non serve interrompere per uno swatch grid
        // (DarkShadowColorListener, non è un ListItem/SwitchItem/SliderItem qualsiasi quindi non
        // entra in GroupUtils.addGroup), poi flush + resta lì + riprende un gruppo nuovo dopo.
        List<Object> pending = new ArrayList<>();
        pending.add(bgBaseSwitch);
        if (mBgBaseExpanded) {
            GroupUtils.addGroup(chain, pending);
            pending = new ArrayList<>();
            addNestedEdge(chain, gatingSwitch(getString(R.string.qs_tiles_bg_active_accent_title), null, KEY_TILE_BG_BASE_ACCENT), GroupPos.TOP);
            chain.add(tileBgBaseColorsRow());
            addNestedEdge(chain, sliderRow(getString(R.string.qs_tiles_radius_value_title), KEY_TILE_RADIUS_BASE, 0, 40, 20, "dp"), GroupPos.BOTTOM);
            List<Object> iconBorderRows = new ArrayList<>();
            iconBorderRows.add(gatingSwitch(getString(R.string.qs_big_icon_border_title), null, KEY_ICON_BORDER_ON));
            if (ObsidianPrefs.getBoolean(KEY_ICON_BORDER_ON, false)) {
                iconBorderRows.add(singleColorRow(getString(R.string.qs_tiles_border_color_title), KEY_ICON_BORDER_COLOR, 213));
            }
            GroupUtils.addGroup(chain, iconBorderRows, true);
        }
        pending.add(bgHlSwitch);
        if (mBgHlExpanded) {
            GroupUtils.addGroup(chain, pending);
            pending = new ArrayList<>();
            addNestedEdge(chain, gatingSwitch(getString(R.string.qs_tiles_bg_active_accent_title), null, KEY_TILE_BG_HL_ACCENT), GroupPos.TOP);
            chain.add(tileBgHlColorsRow());
            addNestedEdge(chain, sliderRow(getString(R.string.qs_tiles_radius_value_title), KEY_TILE_RADIUS_HL, 0, 40, 20, "dp"), GroupPos.BOTTOM);
        }
        pending.add(bgMediaSwitch);
        if (mBgMediaExpanded) {
            GroupUtils.addGroup(chain, pending);
            pending = new ArrayList<>();
            pending.add(singleColorRow(getString(R.string.qs_tiles_brightness_color_title), KEY_TILE_BG_MEDIA_COLOR, 207));
            pending.add(sliderRow(getString(R.string.qs_tiles_radius_value_title), KEY_TILE_RADIUS_MEDIA, 0, 40, 20, "dp"));

            boolean coverFilterOn = ObsidianPrefs.getBoolean(KEY_MEDIA_COVER_FILTER_ON, false);
            pending.add(gatingSwitch(getString(R.string.lockscreen_album_art), null, KEY_MEDIA_COVER_FILTER_ON));
            if (coverFilterOn) {
                int coverFilter = 0;
                try { coverFilter = Integer.parseInt(ObsidianPrefs.getString(KEY_MEDIA_COVER_FILTER, "0")); } catch (NumberFormatException ignored) {}
                pending.add(singleChoiceRow(getString(R.string.lockscreen_album_art_filter), KEY_MEDIA_COVER_FILTER,
                        R.array.lockscreen_album_art_filter_entries));
                if (coverFilter == 3 || coverFilter == 4) {
                    pending.add(sliderRow(getString(R.string.lockscreen_media_blur), KEY_MEDIA_COVER_BLUR, 0, 100, 30, "%"));
                }
            }
            GroupUtils.addGroup(chain, pending, true);
            pending = new ArrayList<>();
        }
        pending.add(slidersSwitch);
        GroupUtils.addGroup(chain, pending);

        // ── Riquadro cursori (ex "Cursori Impostazioni Rapide" — assorbito qui dentro,
        // niente più titolo sezione a sé, è il quarto switch della card di Sfondo Riquadri) ──
        if (mSlidersExpanded) {
            boolean brightnessOn = ObsidianPrefs.getBoolean(KEY_BRIGHTNESS_CUSTOM_ON, false);
            boolean radiusOn = ObsidianPrefs.getBoolean(KEY_RADIUS_ON, false);

            List<Object> sliderRows = new ArrayList<>();
            // 203/204 non passano più per singleColorRow() (righe separate tolte, stesso
            // motivo di 201) — registrati qui a mano per onColorSelected.
            mSingleColorKeys.put(203, KEY_BRIGHTNESS_ICON_COLOR);
            mSingleColorKeys.put(204, KEY_VOLUME_ICON_COLOR);
            sliderRows.add(collapsibleHeader(getString(R.string.qs_tiles_slider_icon_colors_title),
                    () -> { mSliderIconColorsExpanded = !mSliderIconColorsExpanded; rebuild(); }));
            if (mSliderIconColorsExpanded) {
                sliderRows.add(singleChoiceRow(getString(R.string.qs_tiles_brightness_icon_title), KEY_BRIGHTNESS_ICON_MODE,
                        R.array.qs_brightness_icon_entries,
                        idx -> { if (idx == 4) openColorPicker(203, KEY_BRIGHTNESS_ICON_COLOR); }));
                // Stessa preferenza di VolumePanelMod (qs_volume_icon_mode/_custom_color) — voce
                // duplicata qui su richiesta esplicita, non è un secondo controllo indipendente.
                // Funzionante dal 2026-08-20: VolumePanelMod.applyLottieColorFilter usa il vero
                // meccanismo Lottie (addValueCallback + KeyPath jolly), non tint/color filter.
                sliderRows.add(singleChoiceRow(getString(R.string.qs_tiles_brightness_icon_title_volume), KEY_VOLUME_ICON_MODE,
                        R.array.qs_brightness_icon_entries,
                        idx -> { if (idx == 4) openColorPicker(204, KEY_VOLUME_ICON_COLOR); }));
            }
            sliderRows.add(gatingSwitch(getString(R.string.qs_tiles_brightness_custom_title), null, KEY_BRIGHTNESS_CUSTOM_ON));
            if (brightnessOn) {
                // 201 non passa più per singleColorRow() (la riga separata è stata tolta),
                // quindi va registrato qui a mano perché openColorPicker/onColorSelected
                // sappiano dove salvare il colore scelto dal dialog "Modalità colore cursore".
                mSingleColorKeys.put(201, KEY_BRIGHTNESS_COLOR);
                sliderRows.add(singleChoiceRow(getString(R.string.qs_tiles_brightness_mode_title), KEY_BRIGHTNESS_MODE,
                        R.array.brightness_slider_style_entries,
                        idx -> { if (idx == 2) openColorPicker(201, KEY_BRIGHTNESS_COLOR); }));
                mSingleColorKeys.put(202, KEY_BRIGHTNESS_BG_COLOR);
                SwitchWidgetAdapter.SwitchItem bgColorSwitch = gatingSwitch(getString(R.string.qs_tiles_brightness_bg_title), null, KEY_BRIGHTNESS_BG_ON);
                bgColorSwitch.onRowClick = () -> {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).showColorPickerDialog(
                                202, ObsidianPrefs.getInt(KEY_BRIGHTNESS_BG_COLOR, 0xFFFFFFFF), true, true, true,
                                it.tugaia56.obsidian.utils.ObsidianTheme.bgDerivedPresets());
                    }
                };
                sliderRows.add(bgColorSwitch);
            }
            sliderRows.add(gatingSwitch(getString(R.string.qs_tiles_radius_title), null, KEY_RADIUS_ON));
            if (radiusOn) {
                sliderRows.add(sliderRow(getString(R.string.qs_tiles_radius_value_title), KEY_RADIUS, 0, 40, 20, "dp"));
            }
            GroupUtils.addGroup(chain, sliderRows, true);
        }

        // ── Bordo Pulsanti (spostato sotto Riquadro Cursori su richiesta esplicita 2026-09-16) ──
        GroupUtils.addGroup(chain, List.of(borderSwitch));
        if (mBorderExpanded) {
            GroupUtils.addGroup(chain, List.of(
                    singleColorRow(getString(R.string.qs_tiles_border_color_title), KEY_TILE_BORDER_COLOR, 211)), true);
        }

        // ── Forma riquadri (2026-09-22) — indipendente dalla "Forma tessere" nativa, vedi nota
        // sulla chiave sopra. Cerchio/Quadrato/Supercerchio, funziona sia in Classico che Separati.
        SwitchWidgetAdapter.SwitchItem shapeSwitch = prefSwitch(getString(R.string.qs_tiles_shape_title),
                getString(R.string.qs_tiles_shape_summary), KEY_TILE_SHAPE_ON);
        shapeSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_TILE_SHAPE_ON, shapeSwitch.checked);
            mShapeExpanded = shapeSwitch.checked;
            rebuild();
        };
        shapeSwitch.onRowClick = () -> { mShapeExpanded = !mShapeExpanded; rebuild(); };
        GroupUtils.addGroup(chain, List.of(shapeSwitch));
        if (mShapeExpanded) {
            GroupUtils.addGroup(chain, List.of(
                    singleChoiceRow(getString(R.string.qs_tiles_shape_preset_title), KEY_TILE_SHAPE,
                            R.array.qs_tiles_shape_entries)), true);
        }

        // ── Colori Icone (spostata sotto Sfondo Riquadri su richiesta esplicita) ────
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.qs_tiles_icon_colors_section))));
        SwitchWidgetAdapter.SwitchItem iconSwitch = prefSwitch(getString(R.string.qs_tiles_icon_colors_title), null, KEY_ICON_COLORS_ON);
        iconSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_ICON_COLORS_ON, iconSwitch.checked);
            mIconExpanded = iconSwitch.checked;
            rebuild();
        };
        iconSwitch.onRowClick = () -> { mIconExpanded = !mIconExpanded; rebuild(); };
        GroupUtils.addGroup(chain, List.of(iconSwitch));
        if (mIconExpanded) {
            GroupUtils.addGroup(chain, List.of(
                    gatingSwitch(getString(R.string.qs_tiles_icon_active_accent_title), null, KEY_ICON_ACTIVE_ACCENT)));
            chain.add(iconColorsRow());
        }

        // ── Animazione (meno importante, dopo i Cursori) ────────────────────────
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.qs_tiles_animation_section))));
        int animStyle = 0;
        try { animStyle = Integer.parseInt(ObsidianPrefs.getString(KEY_ANIM_STYLE, "0")); } catch (NumberFormatException ignored) {}
        List<Object> animRows = new ArrayList<>();
        animRows.add(singleChoiceRow(getString(R.string.qs_tiles_animation_style_title), KEY_ANIM_STYLE,
                R.array.qs_tile_animation_style_entries));
        if (animStyle != 0) {
            animRows.add(sliderRow(getString(R.string.qs_tiles_animation_duration_title), KEY_ANIM_DURATION, 1, 5, 1, ""));
            animRows.add(singleChoiceRow(getString(R.string.qs_tiles_animation_interpolator_title), KEY_ANIM_INTERPOLATOR,
                    R.array.qs_tile_animation_interpolator_entries));
        }
        // ── Transizioni (stesso gruppo di Animazione, sezione unica) — riga sola come
        // "Stile Animazione": "Disattivata" è l'ultima voce dell'elenco invece di uno
        // switch separato, tocco unico apre subito la scelta. ────────────────────
        animRows.add(transitionsRow());
        GroupUtils.addGroup(chain, animRows);

        // ── Etichette (meno importante, dopo i Cursori) ─────────────────────────
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.qs_tiles_labels_section))));
        mSingleColorKeys.put(205, KEY_LABEL_COLOR);
        SwitchWidgetAdapter.SwitchItem labelColorSwitch = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.qs_tiles_label_color_title), null,
                ObsidianPrefs.getBoolean(KEY_LABEL_COLOR_ON, false), null);
        labelColorSwitch.onChanged = () -> ObsidianPrefs.putBoolean(KEY_LABEL_COLOR_ON, labelColorSwitch.checked);
        labelColorSwitch.onRowClick = () -> showLabelColorAccentChoice();
        GroupUtils.addGroup(chain, List.of(
                prefSwitch(getString(R.string.qs_tiles_hide_labels_title), null, KEY_HIDE_LABELS),
                labelColorSwitch));

        // ── Impostazioni Rapide Separati (pulsanti/larghezza tendina) ───────────
        // Uniche opzioni davvero esclusive dello stile "Separati" (tutto il resto — sfondo,
        // colori icone, etichette — si è rivelato universale ed è già sopra) — portate qui
        // come sezione inline il 2026-08-20, non più una schermata/card di navigazione a sé.
        // Stesso pattern di Cursori Impostazioni Rapide: switch master attiva/disattiva,
        // il tocco sul nome apre/chiude le opzioni sottostanti.
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.qs_separate_mods))));
        SwitchWidgetAdapter.SwitchItem sepSwitch = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.qs_separate_mods), getString(R.string.qs_separate_mods_summary),
                ObsidianPrefs.getBoolean(KEY_SEP_ON, true), null);
        sepSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_SEP_ON, sepSwitch.checked);
            mSepExpanded = sepSwitch.checked;
            rebuild();
        };
        sepSwitch.onRowClick = () -> { mSepExpanded = !mSepExpanded; rebuild(); };
        GroupUtils.addGroup(chain, List.of(sepSwitch));
        if (mSepExpanded) {
            SwitchWidgetAdapter.SwitchItem btnBgSwitch = gatingSwitch(
                    getString(R.string.qs_separate_bg_section), null, QsSeparateMod.PREF_BTN_BG_ON);
            btnBgSwitch.onChanged = () -> {
                ObsidianPrefs.putBoolean(QsSeparateMod.PREF_BTN_BG_ON, btnBgSwitch.checked);
                mSepBtnBgExpanded = btnBgSwitch.checked;
                rebuild();
            };
            btnBgSwitch.onRowClick = () -> { mSepBtnBgExpanded = !mSepBtnBgExpanded; rebuild(); };
            GroupUtils.addGroup(chain, List.of(btnBgSwitch));
            if (mSepBtnBgExpanded) {
                GroupUtils.addGroup(chain, List.of(
                        bgButtonRow(getString(R.string.qs_separate_bg_edit), QsSeparateMod.PREF_EDIT_BG_ON,
                                QsSeparateMod.PREF_EDIT_BG_ACCENT, QsSeparateMod.PREF_EDIT_BG_COLOR, 208),
                        bgButtonRow(getString(R.string.qs_separate_bg_menu), QsSeparateMod.PREF_MENU_BG_ON,
                                QsSeparateMod.PREF_MENU_BG_ACCENT, QsSeparateMod.PREF_MENU_BG_COLOR, 209),
                        bgButtonRow(getString(R.string.qs_separate_bg_settings), QsSeparateMod.PREF_SETTINGS_BG_ON,
                                QsSeparateMod.PREF_SETTINGS_BG_ACCENT, QsSeparateMod.PREF_SETTINGS_BG_COLOR, 210)));
            }

            GroupUtils.addGroup(chain, List.of(
                    prefSwitch(getString(R.string.qs_separate_hide_edit), null, KEY_SEP_HIDE_EDIT),
                    prefSwitch(getString(R.string.qs_separate_hide_menu), null, KEY_SEP_HIDE_MENU)));
            boolean sepWidthOn = ObsidianPrefs.getBoolean(KEY_SEP_WIDTH_ON, false);
            List<Object> sepWidthRows = new ArrayList<>();
            sepWidthRows.add(gatingSwitch(getString(R.string.qs_separate_width_switch),
                    getString(R.string.qs_separate_width_switch_summary), KEY_SEP_WIDTH_ON));
            if (sepWidthOn) {
                sepWidthRows.add(sliderRow(getString(R.string.qs_separate_width_value), KEY_SEP_WIDTH_VAL, 10, 85, 50, "%"));
            }
            GroupUtils.addGroup(chain, sepWidthRows);
        }

        android.os.Parcelable scrollState = mRv.getLayoutManager() != null
                ? mRv.getLayoutManager().onSaveInstanceState() : null;
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
        if (scrollState != null && mRv.getLayoutManager() != null) {
            mRv.getLayoutManager().onRestoreInstanceState(scrollState);
        }
    }

    private DarkShadowColorListener iconColorsRow() {
        String[] labels = { getString(R.string.qs_tiles_color_active), getString(R.string.qs_tiles_color_inactive) };
        mIconColorItems.clear();
        for (int i = 0; i < ICON_COLOR_KEYS.length; i++) {
            mIconColorItems.add(new DarkShadowItem(labels[i], ICON_COLOR_KEYS[i],
                    Collections.emptyList(), Collections.emptyList(), null,
                    ObsidianPrefs.getInt(ICON_COLOR_KEYS[i], 0xFFFFFFFF),
                    ObsidianPrefs.getBoolean(ICON_COLOR_KEYS[i] + "_on", false)));
        }
        // "Attivo" (indice 0) è superfluo quando "Collega all'Accento" è attivo: bloccato per
        // non lasciare che il picker prevalga in silenzio sull'accento.
        mIconColorItems.get(0).setLocked(ObsidianPrefs.getBoolean(KEY_ICON_ACTIVE_ACCENT, false));
        mIconColorAdapter = new DarkShadowColorListener(mIconColorItems,
                this::onColorEnabled, this::onColorDisabled, (item, id) -> onColorSwatch(item, id, false));
        return mIconColorAdapter;
    }

    private DarkShadowColorListener tileBgBaseColorsRow() {
        String[] labels = { getString(R.string.qs_tiles_color_active), getString(R.string.qs_tiles_color_inactive) };
        mTileBgBaseColorItems.clear();
        for (int i = 0; i < TILE_BG_BASE_COLOR_KEYS.length; i++) {
            mTileBgBaseColorItems.add(new DarkShadowItem(labels[i], TILE_BG_BASE_COLOR_KEYS[i],
                    Collections.emptyList(), Collections.emptyList(), null,
                    ObsidianPrefs.getInt(TILE_BG_BASE_COLOR_KEYS[i], 0xFFFFFFFF), true));
        }
        mTileBgBaseColorItems.get(0).setLocked(ObsidianPrefs.getBoolean(KEY_TILE_BG_BASE_ACCENT, false));
        mTileBgBaseColorAdapter = new DarkShadowColorListener(mTileBgBaseColorItems,
                this::onColorEnabled, this::onColorDisabled, (item, id) -> onColorSwatch(item, id, false),
                true, GroupPos.MIDDLE);
        return mTileBgBaseColorAdapter;
    }

    private DarkShadowColorListener tileBgHlColorsRow() {
        String[] labels = { getString(R.string.qs_tiles_color_active), getString(R.string.qs_tiles_color_inactive) };
        mTileBgHlColorItems.clear();
        for (int i = 0; i < TILE_BG_HL_COLOR_KEYS.length; i++) {
            mTileBgHlColorItems.add(new DarkShadowItem(labels[i], TILE_BG_HL_COLOR_KEYS[i],
                    Collections.emptyList(), Collections.emptyList(), null,
                    ObsidianPrefs.getInt(TILE_BG_HL_COLOR_KEYS[i], 0xFFFFFFFF), true));
        }
        mTileBgHlColorItems.get(0).setLocked(ObsidianPrefs.getBoolean(KEY_TILE_BG_HL_ACCENT, false));
        mTileBgHlColorAdapter = new DarkShadowColorListener(mTileBgHlColorItems,
                this::onColorEnabled, this::onColorDisabled, (item, id) -> onColorSwatch(item, id, false),
                true, GroupPos.MIDDLE);
        return mTileBgHlColorAdapter;
    }

    /** Riga intestazione senza switch — solo tocco per aprire/chiudere le righe sotto. A
     *  differenza di LockscreenWidgetsFragment (dove va con chain.add()), qui deve restare
     *  un ListItem grezzo perché entra in sliderRows/GroupUtils.addGroup insieme alle altre
     *  righe del gruppo — un ListWidgetAdapter intero lì dentro viene ignorato in silenzio. */
    private ListWidgetAdapter.ListItem collapsibleHeader(String title, Runnable onToggle) {
        ListWidgetAdapter.ListItem item = new ListWidgetAdapter.ListItem(title, null, onToggle::run);
        item.useAccentColor = false;
        return item;
    }

    private void onColorEnabled(DarkShadowItem item) {
        item.setEnabled(true);
        ObsidianPrefs.putInt(item.getOverlayName(), item.getColor());
        ObsidianPrefs.putBoolean(item.getOverlayName() + "_on", true);
    }

    private void onColorDisabled(DarkShadowItem item) {
        item.setEnabled(false);
        ObsidianPrefs.putBoolean(item.getOverlayName() + "_on", false);
    }

    private void onColorSwatch(DarkShadowItem item, int dialogId, boolean isIcon) {
        if (getActivity() instanceof MainActivity) {
            // Gli swatch "Inattivo" vogliono in genere un colore scuro tipo sfondo — offri
            // le sfumature derivate dal colore sfondo attuale invece della palette Material.
            int[] presets = getString(R.string.qs_tiles_color_inactive).equals(item.getName())
                    ? it.tugaia56.obsidian.utils.ObsidianTheme.bgDerivedPresets() : null;
            ((MainActivity) getActivity()).showColorPickerDialog(dialogId, item.getColor(), true, true, true, presets);
        }
    }

    /** "Stile Transizioni" — riga sola come "Stile Animazione": "Disattivata" è la prima
     *  voce dell'elenco (indice 0) invece di un secondo switch "Abilita..." separato.
     *  L'indice mostrato/scelto qui è sempre "indice reale + 1" (0 = Disattivata) — quello
     *  salvato in KEY_TRANSITIONS per il Mod resta l'indice reale (indice UI - 1), così
     *  TileTransformers.get() non deve cambiare. KEY_TRANSITIONS_ON resta il vero
     *  interruttore letto dal Mod, sincronizzato qui in base alla voce scelta. */
    private ListWidgetAdapter.ListItem transitionsRow() {
        String[] entries = getResources().getStringArray(R.array.qs_tile_transitions_entries);
        return new ListWidgetAdapter.ListItem(getString(R.string.qs_tiles_transitions_style_title),
                entries[currentTransitionUiIndex(entries)], () -> showTransitionsDialog(entries));
    }

    private int currentTransitionUiIndex(String[] entries) {
        if (!ObsidianPrefs.getBoolean(KEY_TRANSITIONS_ON, false)) return 0;
        int idx = 0;
        try { idx = Integer.parseInt(ObsidianPrefs.getString(KEY_TRANSITIONS, "0")); } catch (NumberFormatException ignored) {}
        int uiIndex = idx + 1;
        return (uiIndex > 0 && uiIndex < entries.length) ? uiIndex : 0;
    }

    private void showTransitionsDialog(String[] entries) {
        int current = currentTransitionUiIndex(entries);
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new android.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.qs_tiles_transitions_style_title))
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    boolean off = selected[0] == 0;
                    ObsidianPrefs.putBoolean(KEY_TRANSITIONS_ON, !off);
                    if (!off) ObsidianPrefs.putString(KEY_TRANSITIONS, String.valueOf(selected[0] - 1));
                    rebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    /** Apre subito il color picker per un singleColorRow, invece di lasciare che l'utente
     *  debba toccare a parte la riga swatch appena comparsa sotto dopo la scelta
     *  "Personalizzata" — mSingleColorKeys è già popolato perché rebuild() (che ricrea la
     *  riga swatch via singleColorRow) gira prima di questa chiamata. */
    /** Aggiunge UNA riga nested con una posizione FORZATA (TOP/BOTTOM) invece di quella
     *  calcolata automaticamente da GroupUtils.addGroup — serve quando la riga fa parte di
     *  un blocco più ampio interrotto da uno swatch grid (DarkShadowColorListener) in mezzo,
     *  es. "Collega all'Accento" (TOP) → swatch grid (MIDDLE) → "Raggio" (BOTTOM), tutto un
     *  unico bordo continuo invece di tre riquadri separati. */
    private void addNestedEdge(List<RecyclerView.Adapter<?>> chain, Object row, GroupPos pos) {
        if (row instanceof SwitchWidgetAdapter.SwitchItem s) {
            s.nested = true;
            s.groupPos = pos;
            chain.add(new SwitchWidgetAdapter(List.of(s)));
        } else if (row instanceof SliderWidgetAdapter.SliderItem s) {
            s.nested = true;
            s.groupPos = pos;
            chain.add(new SliderWidgetAdapter(List.of(s)));
        } else if (row instanceof ListWidgetAdapter.ListItem s) {
            s.nested = true;
            s.groupPos = pos;
            chain.add(new ListWidgetAdapter(List.of(s)));
        }
    }

    /** Come colorModeItem (altri fragment), ma con uno switch acceso/spento indipendente per il singolo
     *  pulsante (2026-08-22) — stesso linguaggio "switch attiva, tocco nome configura" del
     *  resto dell'app: lo switch abilita/disabilita lo sfondo colorato di QUESTO pulsante,
     *  il tocco sul nome apre lo stesso dialog Accento/Personalizzato di colorModeItem. */
    private SwitchWidgetAdapter.SwitchItem bgButtonRow(String title, String onKey,
                                                        String accentKey, String colorKey, int dialogId) {
        mSingleColorKeys.put(dialogId, colorKey);
        SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                title, colorModeLabel(accentKey, colorKey),
                ObsidianPrefs.getBoolean(onKey, true), null);
        item.onChanged = () -> ObsidianPrefs.putBoolean(onKey, item.checked);
        item.onRowClick = () -> showColorModeDialog(title, accentKey, colorKey, dialogId);
        return item;
    }

    private String colorModeLabel(String accentKey, String colorKey) {
        if (ObsidianPrefs.getBoolean(accentKey, true)) return getString(R.string.color_mode_accent);
        return String.format("#%06X", 0xFFFFFF & ObsidianPrefs.getInt(colorKey, 0xFFFFFFFF));
    }

    private void showColorModeDialog(String title, String accentKey, String colorKey, int dialogId) {
        String[] entries = { getString(R.string.color_mode_accent), getString(R.string.color_mode_custom) };
        int current = ObsidianPrefs.getBoolean(accentKey, true) ? 0 : 1;
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    boolean accent = selected[0] == 0;
                    ObsidianPrefs.putBoolean(accentKey, accent);
                    rebuild();
                    if (!accent && getActivity() instanceof MainActivity) {
                        int currentColor = ObsidianPrefs.getInt(colorKey, 0xFFFFFFFF);
                        ((MainActivity) getActivity()).showColorPickerDialog(dialogId, currentColor, true, true, true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    /** Only "Colore Etichette" (205) gets Accento — the other three openColorPicker() callers
     *  (203/204/201) already have their OWN Accento entry inside a mode array and reach this as
     *  the "Personalizzata" fallback, so adding a second accent prompt here would double up. */
    private void showLabelColorAccentChoice() {
        String[] entries = { getString(R.string.color_mode_accent), getString(R.string.color_mode_custom) };
        boolean currentAccent = ObsidianPrefs.getBoolean(KEY_LABEL_COLOR + "_use_accent", false);
        final int[] selected = {currentAccent ? 0 : 1};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.qs_tiles_label_color_title)
                .setSingleChoiceItems(entries, selected[0], (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    boolean useAccent = selected[0] == 0;
                    ObsidianPrefs.putBoolean(KEY_LABEL_COLOR + "_use_accent", useAccent);
                    if (useAccent) {
                        ObsidianPrefs.putInt(KEY_LABEL_COLOR, ObsidianTheme.accentColor());
                        rebuild();
                    } else {
                        openColorPicker(205, KEY_LABEL_COLOR);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private void openColorPicker(int dialogId, String key) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showColorPickerDialog(
                    dialogId, ObsidianPrefs.getInt(key, 0xFFFFFFFF), true, true, true);
        }
    }

    private void showTileJumpBlockedDialog(int titleRes, int bodyRes) {
        ObsidianTheme.themeDialog(new android.app.AlertDialog.Builder(requireContext())
                .setTitle(titleRes)
                .setMessage(bodyRes)
                .setPositiveButton(android.R.string.ok, null)
                .show());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onColorSelected(ColorSelectedEvent event) {
        for (DarkShadowItem item : mIconColorItems) {
            if (event.dialogId() != System.identityHashCode(item)) continue;
            item.setColor(event.color());
            ObsidianPrefs.putInt(item.getOverlayName(), event.color());
            if (mIconColorAdapter != null) mIconColorAdapter.notifyDataSetChanged();
            return;
        }
        for (DarkShadowItem item : mTileBgBaseColorItems) {
            if (event.dialogId() != System.identityHashCode(item)) continue;
            item.setColor(event.color());
            ObsidianPrefs.putInt(item.getOverlayName(), event.color());
            if (mTileBgBaseColorAdapter != null) mTileBgBaseColorAdapter.notifyDataSetChanged();
            return;
        }
        for (DarkShadowItem item : mTileBgHlColorItems) {
            if (event.dialogId() != System.identityHashCode(item)) continue;
            item.setColor(event.color());
            ObsidianPrefs.putInt(item.getOverlayName(), event.color());
            if (mTileBgHlColorAdapter != null) mTileBgHlColorAdapter.notifyDataSetChanged();
            return;
        }
        String singleKey = mSingleColorKeys.get(event.dialogId());
        if (singleKey != null) {
            ObsidianPrefs.putBoolean(singleKey + "_use_accent", false); // picking a colour implies custom
            ObsidianPrefs.putInt(singleKey, event.color());
        }
    }

    // ── Colore singolo (cursore luminosità) — nessun grid, un solo swatch fisso ─

    private ListWidgetAdapter.ListItem singleColorRow(String title, String key, int dialogId) {
        mSingleColorKeys.put(dialogId, key);
        return new ListWidgetAdapter.ListItem(title, null, () -> showSingleColorAccentChoice(title, key, dialogId));
    }

    /** Only caller today is Sfondo Media — Accento/Personalizzato inserted before the row opens
     *  the raw picker. Baked at selection time (no live re-resolve), same as every other picker. */
    private void showSingleColorAccentChoice(String title, String key, int dialogId) {
        String[] entries = { getString(R.string.color_mode_accent), getString(R.string.color_mode_custom) };
        boolean currentAccent = ObsidianPrefs.getBoolean(key + "_use_accent", false);
        final int[] selected = {currentAccent ? 0 : 1};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setSingleChoiceItems(entries, selected[0], (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    boolean useAccent = selected[0] == 0;
                    ObsidianPrefs.putBoolean(key + "_use_accent", useAccent);
                    if (useAccent) {
                        ObsidianPrefs.putInt(key, ObsidianTheme.accentColor());
                        rebuild();
                    } else if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).showColorPickerDialog(
                                dialogId, ObsidianPrefs.getInt(key, 0xFFFFFFFF), true, true, true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    // ── Generic row helpers (stesso pattern delle altre schermate QS) ──────────

    private SwitchWidgetAdapter.SwitchItem prefSwitch(String title, String summary, String key) {
        SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                title, summary, ObsidianPrefs.getBoolean(key, false), null);
        item.onChanged = () -> ObsidianPrefs.putBoolean(key, item.checked);
        return item;
    }

    private SwitchWidgetAdapter.SwitchItem gatingSwitch(String title, String summary, String key) {
        SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                title, summary, ObsidianPrefs.getBoolean(key, false), null);
        item.onChanged = () -> {
            ObsidianPrefs.putBoolean(key, item.checked);
            rebuild();
        };
        return item;
    }

    private ListWidgetAdapter.ListItem singleChoiceRow(String title, String key, int entriesArrayRes) {
        return singleChoiceRow(title, key, entriesArrayRes, null);
    }

    /** rebuildOnChange è sempre implicito ora (serve comunque a rifare i gruppi
     *  GroupUtils quando cambia una riga sotto, es. lo swatch "Personalizzata"). onSelected
     *  riceve l'indice scelto SUBITO dopo l'apply, utile per incatenare un'azione — es.
     *  aprire subito il color picker invece di far comparire una riga separata da toccare. */
    private ListWidgetAdapter.ListItem singleChoiceRow(String title, String key, int entriesArrayRes,
                                                         java.util.function.IntConsumer onSelected) {
        return new ListWidgetAdapter.ListItem(
                title, choiceLabel(key, entriesArrayRes),
                () -> showSingleChoiceDialog(title, key, entriesArrayRes, onSelected));
    }

    private String choiceLabel(String key, int entriesArrayRes) {
        String[] entries = getResources().getStringArray(entriesArrayRes);
        int idx = 0;
        try { idx = Integer.parseInt(ObsidianPrefs.getString(key, "0")); } catch (NumberFormatException ignored) {}
        return (idx >= 0 && idx < entries.length) ? entries[idx] : entries[0];
    }

    private void showSingleChoiceDialog(String title, String key, int entriesArrayRes,
                                         java.util.function.IntConsumer onSelected) {
        String[] entries = getResources().getStringArray(entriesArrayRes);
        int current = 0;
        try { current = Integer.parseInt(ObsidianPrefs.getString(key, "0")); } catch (NumberFormatException ignored) {}
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new android.app.AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putString(key, String.valueOf(selected[0]));
                    rebuild();
                    if (onSelected != null) onSelected.accept(selected[0]);
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private SliderWidgetAdapter.SliderItem sliderRow(String title, String key, int min, int max, int def, String unit) {
        int current = ObsidianPrefs.getInt(key, def);
        return new SliderWidgetAdapter.SliderItem(
                title, current, min, max, unit, def,
                value -> ObsidianPrefs.putInt(key, value));
    }
}
