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

    // Animazione/Transizioni/Etichette spostate in QsTilesMiscFragment (2026-09-26).

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

    // ── Cursore Volume (riempimento/sfondo — 2026-09-26, "Riquadro cursori" diviso in due
    // sezioni indipendenti, vedi nota gemella in QsTilesCustomizeMod) ─────────────────────
    private static final String KEY_VOLUME_SLIDER_CUSTOM_ON = "customize_volume_slider";
    private static final String KEY_VOLUME_SLIDER_MODE      = "volume_slider_progress_color_mode";
    private static final String KEY_VOLUME_SLIDER_COLOR     = "volume_slider_color";
    private static final String KEY_VOLUME_SLIDER_BG_ON     = "volume_slider_background_color_enabled";
    private static final String KEY_VOLUME_SLIDER_BG_COLOR  = "volume_slider_background_color";

    // ── Raggio riquadri ──────────────────────────────────────────────────────
    private static final String KEY_RADIUS_ON = "qs_sliders_radius_switch";
    private static final String KEY_RADIUS    = "qs_sliders_radius";
    private static final String KEY_VOLUME_RADIUS_ON = "qs_volume_slider_radius_switch";
    private static final String KEY_VOLUME_RADIUS    = "qs_volume_slider_radius";

    // ── Master indipendenti Luminosità/Volume (sostituiscono KEY_SLIDERS_ON, non più letto/
    // scritto — vedi nota gemella in QsTilesCustomizeMod) ───────────────────────────────────
    private static final String KEY_BRIGHTNESS_SLIDER_ON = "qs_brightness_slider_section_on";
    private static final String KEY_VOLUME_SLIDER_ON     = "qs_volume_slider_section_on";

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

    // Impostazioni Rapide Separati spostate in QsTilesMiscFragment (2026-09-26).
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
    // "Riquadro cursori" diviso in due card indipendenti 2026-09-26 (era mSlidersExpanded +
    // mSliderIconColorsExpanded, un unico switch con le due icone annidate in un collapsibleHeader
    // condiviso — ora ognuna ha la propria card, niente più annidamento extra).
    private boolean mBrightnessSliderExpanded = false;
    private boolean mVolumeSliderExpanded = false;
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
        SwitchWidgetAdapter.SwitchItem bgHlSwitch = prefSwitch(getString(R.string.qs_tiles_jump_highlight),
                getString(R.string.qs_tiles_jump_highlight_summary), KEY_TILE_BG_HL_ON);
        bgHlSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_TILE_BG_HL_ON, bgHlSwitch.checked);
            mBgHlExpanded = bgHlSwitch.checked;
            rebuild();
        };
        bgHlSwitch.onRowClick = () -> { mBgHlExpanded = !mBgHlExpanded; rebuild(); };
        SwitchWidgetAdapter.SwitchItem borderSwitch = prefSwitch(getString(R.string.qs_tiles_border_title),
                getString(R.string.qs_tiles_border_summary), KEY_TILE_BORDER_ON);
        borderSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_TILE_BORDER_ON, borderSwitch.checked);
            mBorderExpanded = borderSwitch.checked;
            rebuild();
        };
        borderSwitch.onRowClick = () -> { mBorderExpanded = !mBorderExpanded; rebuild(); };
        SwitchWidgetAdapter.SwitchItem bgMediaSwitch = prefSwitch(getString(R.string.qs_tiles_jump_media),
                getString(R.string.qs_tiles_jump_media_summary), KEY_TILE_BG_MEDIA_ON);
        bgMediaSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_TILE_BG_MEDIA_ON, bgMediaSwitch.checked);
            mBgMediaExpanded = bgMediaSwitch.checked;
            rebuild();
        };
        bgMediaSwitch.onRowClick = () -> { mBgMediaExpanded = !mBgMediaExpanded; rebuild(); };

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
        GroupUtils.addGroup(chain, pending);

        // ── Riquadro Luminosità / Riquadro Volume (2026-09-26: "Riquadro cursori" diviso in due
        // card indipendenti — prima condividevano lo stesso colore/sfondo/raggio perché il mod
        // non distingueva le due istanze di OplusQsVerticalSeekBar, vedi isVolumeSliderView() in
        // QsTilesCustomizeMod). Ognuna è una card a sé, stesso pattern di Riquadri grandi/piccoli/
        // Media sopra — non più annidate una dentro l'altra con un collapsibleHeader condiviso.
        SwitchWidgetAdapter.SwitchItem brightnessSliderSwitch = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.qs_tiles_brightness_slider_title), getString(R.string.qs_tiles_brightness_slider_summary),
                ObsidianPrefs.getBoolean(KEY_BRIGHTNESS_SLIDER_ON, true), null);
        brightnessSliderSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_BRIGHTNESS_SLIDER_ON, brightnessSliderSwitch.checked);
            mBrightnessSliderExpanded = brightnessSliderSwitch.checked;
            rebuild();
        };
        brightnessSliderSwitch.onRowClick = () -> { mBrightnessSliderExpanded = !mBrightnessSliderExpanded; rebuild(); };
        GroupUtils.addGroup(chain, List.of(brightnessSliderSwitch));
        if (mBrightnessSliderExpanded) {
            boolean brightnessOn = ObsidianPrefs.getBoolean(KEY_BRIGHTNESS_CUSTOM_ON, false);
            boolean radiusOn = ObsidianPrefs.getBoolean(KEY_RADIUS_ON, false);

            List<Object> rows = new ArrayList<>();
            mSingleColorKeys.put(203, KEY_BRIGHTNESS_ICON_COLOR);
            rows.add(singleChoiceRow(getString(R.string.qs_tiles_brightness_icon_title), KEY_BRIGHTNESS_ICON_MODE,
                    R.array.qs_brightness_icon_entries,
                    idx -> { if (idx == 4) openColorPicker(203, KEY_BRIGHTNESS_ICON_COLOR); }));
            rows.add(gatingSwitch(getString(R.string.qs_tiles_brightness_custom_title), null, KEY_BRIGHTNESS_CUSTOM_ON));
            if (brightnessOn) {
                mSingleColorKeys.put(201, KEY_BRIGHTNESS_COLOR);
                rows.add(singleChoiceRow(getString(R.string.qs_tiles_brightness_mode_title), KEY_BRIGHTNESS_MODE,
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
                rows.add(bgColorSwitch);
            }
            rows.add(gatingSwitch(getString(R.string.qs_tiles_radius_title), null, KEY_RADIUS_ON));
            if (radiusOn) {
                rows.add(sliderRow(getString(R.string.qs_tiles_radius_value_title), KEY_RADIUS, 0, 40, 20, "dp"));
            }
            GroupUtils.addGroup(chain, rows, true);
        }

        SwitchWidgetAdapter.SwitchItem volumeSliderSwitch = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.qs_tiles_volume_slider_title), getString(R.string.qs_tiles_volume_slider_summary),
                ObsidianPrefs.getBoolean(KEY_VOLUME_SLIDER_ON, true), null);
        volumeSliderSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_VOLUME_SLIDER_ON, volumeSliderSwitch.checked);
            mVolumeSliderExpanded = volumeSliderSwitch.checked;
            rebuild();
        };
        volumeSliderSwitch.onRowClick = () -> { mVolumeSliderExpanded = !mVolumeSliderExpanded; rebuild(); };
        GroupUtils.addGroup(chain, List.of(volumeSliderSwitch));
        if (mVolumeSliderExpanded) {
            boolean volumeOn = ObsidianPrefs.getBoolean(KEY_VOLUME_SLIDER_CUSTOM_ON, false);
            boolean volumeRadiusOn = ObsidianPrefs.getBoolean(KEY_VOLUME_RADIUS_ON, false);

            List<Object> rows = new ArrayList<>();
            mSingleColorKeys.put(204, KEY_VOLUME_ICON_COLOR);
            // Stessa preferenza di VolumePanelMod (qs_volume_icon_mode/_custom_color) — voce
            // duplicata qui su richiesta esplicita, non è un secondo controllo indipendente.
            rows.add(singleChoiceRow(getString(R.string.qs_tiles_brightness_icon_title_volume), KEY_VOLUME_ICON_MODE,
                    R.array.qs_brightness_icon_entries,
                    idx -> { if (idx == 4) openColorPicker(204, KEY_VOLUME_ICON_COLOR); }));
            rows.add(gatingSwitch(getString(R.string.qs_tiles_volume_custom_title), null, KEY_VOLUME_SLIDER_CUSTOM_ON));
            if (volumeOn) {
                mSingleColorKeys.put(214, KEY_VOLUME_SLIDER_COLOR);
                rows.add(singleChoiceRow(getString(R.string.qs_tiles_brightness_mode_title), KEY_VOLUME_SLIDER_MODE,
                        R.array.brightness_slider_style_entries,
                        idx -> { if (idx == 2) openColorPicker(214, KEY_VOLUME_SLIDER_COLOR); }));
                mSingleColorKeys.put(215, KEY_VOLUME_SLIDER_BG_COLOR);
                SwitchWidgetAdapter.SwitchItem volBgColorSwitch = gatingSwitch(getString(R.string.qs_tiles_brightness_bg_title), null, KEY_VOLUME_SLIDER_BG_ON);
                volBgColorSwitch.onRowClick = () -> {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).showColorPickerDialog(
                                215, ObsidianPrefs.getInt(KEY_VOLUME_SLIDER_BG_COLOR, 0xFFFFFFFF), true, true, true,
                                it.tugaia56.obsidian.utils.ObsidianTheme.bgDerivedPresets());
                    }
                };
                rows.add(volBgColorSwitch);
            }
            rows.add(gatingSwitch(getString(R.string.qs_tiles_radius_title), null, KEY_VOLUME_RADIUS_ON));
            if (volumeRadiusOn) {
                rows.add(sliderRow(getString(R.string.qs_tiles_radius_value_title), KEY_VOLUME_RADIUS, 0, 40, 20, "dp"));
            }
            GroupUtils.addGroup(chain, rows, true);
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
                    tileShapeChoiceRow(getString(R.string.qs_tiles_shape_preset_title), KEY_TILE_SHAPE,
                            R.array.qs_tiles_shape_entries)), true);
        }

        // ── Colori Icone (spostata sotto Sfondo Riquadri su richiesta esplicita) ────
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.qs_tiles_icon_colors_section))));
        SwitchWidgetAdapter.SwitchItem iconSwitch = prefSwitch(getString(R.string.qs_tiles_icon_colors_title),
                getString(R.string.qs_tiles_icon_colors_summary), KEY_ICON_COLORS_ON);
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

        // Animazione/Transizioni, Etichette, Impostazioni Rapide Separati spostate in
        // QsTilesMiscFragment ("Varie Riquadri", 2026-09-26) — voce di navigazione a sé in
        // Pannello Impostazioni Rapide, non più sezioni inline qui.

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

    // transitionsRow/currentTransitionUiIndex/showTransitionsDialog spostate in
    // QsTilesMiscFragment (2026-09-26).

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

    // bgButtonRow/colorModeLabel/showColorModeDialog/showLabelColorAccentChoice spostate in
    // QsTilesMiscFragment (2026-09-26).

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

    /** Come singleChoiceRow/showSingleChoiceDialog, ma per "Forma riquadri" (KEY_TILE_SHAPE) —
     *  richiesto 2026-09-26 mostrare le forme in ordine ALFABETICO senza però rinumerare gli indici
     *  salvati (il pref è un indice grezzo nell'array TILE_SHAPE_KIND lato hook — riordinare
     *  l'array.xml stesso avrebbe silenziosamente cambiato la forma già scelta da chi ha già
     *  configurato l'app). Qui si ordina solo l'ETICHETTA mostrata nel dialog; "which" dell'utente
     *  viene tradotto nell'indice stabile originale prima di scrivere il pref. */
    private ListWidgetAdapter.ListItem tileShapeChoiceRow(String title, String key, int entriesArrayRes) {
        return new ListWidgetAdapter.ListItem(
                title, choiceLabel(key, entriesArrayRes),
                () -> showTileShapeDialog(title, key, entriesArrayRes));
    }

    private void showTileShapeDialog(String title, String key, int entriesArrayRes) {
        String[] entries = getResources().getStringArray(entriesArrayRes);
        Integer[] order = new Integer[entries.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> entries[a].compareToIgnoreCase(entries[b]));
        String[] sortedLabels = new String[entries.length];
        for (int i = 0; i < order.length; i++) sortedLabels[i] = entries[order[i]];

        int currentIdx = 0;
        try { currentIdx = Integer.parseInt(ObsidianPrefs.getString(key, "0")); } catch (NumberFormatException ignored) {}
        int currentPos = 0;
        for (int i = 0; i < order.length; i++) if (order[i] == currentIdx) { currentPos = i; break; }

        final int[] selectedPos = {currentPos};
        int normalColor = ObsidianTheme.systemDialogTextColor(requireContext());
        int accentColor = ObsidianTheme.accentColor();
        int padH = ObsidianTheme.dp(requireContext(), 24), padV = ObsidianTheme.dp(requireContext(), 12);
        int gap = ObsidianTheme.dp(requireContext(), 12);
        // Riga costruita a mano (icona + etichetta a peso 1 + RadioButton), non il CheckedTextView
        // di android.R.layout.simple_list_item_single_choice: quel pallino si posiziona subito
        // dopo il testo qualunque LayoutParams gli si dia (segnalato 2026-09-26, screenshot — non
        // flush a destra come nelle liste native). Con l'etichetta a layout_weight=1 il RadioButton
        // finisce sempre allo stesso bordo destro, indipendentemente dalla lunghezza del nome.
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                requireContext(), android.R.layout.simple_list_item_1, sortedLabels) {
            @NonNull @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                android.widget.LinearLayout row = new android.widget.LinearLayout(requireContext());
                row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setPadding(padH, padV, padH, padV);
                row.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                boolean selected = position == selectedPos[0];
                int color = selected ? accentColor : normalColor;

                android.widget.TextView label = new android.widget.TextView(requireContext());
                label.setText(sortedLabels[position]);
                label.setTextColor(color);
                label.setTextSize(16); // stessa dimensione delle liste a scelta singola native
                // 2026-09-27: ingrandita 1.5x rispetto al testo (prima "della stessa dimensione
                // del nome") — richiesto esplicito, le icone erano troppo piccole per distinguere
                // le forme nuove (Ettagono/Decagono/Croce/Cuore/Quadrifoglio/Stella).
                int iconSize = (int) (label.getTextSize() * 1.5f);
                android.widget.ImageView icon = new android.widget.ImageView(requireContext());
                icon.setImageDrawable(shapeIconDrawable(order[position], iconSize, color));
                android.widget.LinearLayout.LayoutParams iconLp = new android.widget.LinearLayout.LayoutParams(iconSize, iconSize);
                iconLp.setMarginEnd(gap);
                row.addView(icon, iconLp);

                android.widget.LinearLayout.LayoutParams labelLp = new android.widget.LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                row.addView(label, labelLp);

                android.widget.RadioButton radio = new android.widget.RadioButton(requireContext());
                radio.setChecked(selected);
                radio.setClickable(false);
                // Un discendente focusable dentro la riga di una ListView le impedisce di ricevere
                // il tap (bug/gotcha noto di AbsListView) — setClickable(false) da solo non basta.
                radio.setFocusable(false);
                radio.setFocusableInTouchMode(false);
                row.addView(radio);
                return row;
            }
        };
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setSingleChoiceItems(adapter, currentPos, (d, which) -> {
                    selectedPos[0] = which;
                    adapter.notifyDataSetChanged(); // riaccenta nome/icona della riga appena scelta
                })
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putString(key, String.valueOf(order[selectedPos[0]]));
                    rebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        ObsidianTheme.themeDialog(dialog);
        dialog.show();
        // Bug 2026-09-26: alla PRIMA apertura la finestra del dialog è ancora wrap_content (la sua
        // larghezza finale non è ancora nota), quindi ListView misura le righe con MATCH_PARENT
        // ignorato (torna al wrap del testo, pallino vicino al nome) — solo DOPO che una vera
        // richiesta di layout con larghezza nota è avvenuta (es. il notifyDataSetChanged() sopra,
        // dopo un tocco) le righe si allargano per davvero. Fix: forziamo SUBITO una larghezza fissa
        // della finestra invece di lasciarla wrap_content, così anche il primissimo giro di misura
        // usa una larghezza reale e il pallino nasce già allineato a destra.
        if (dialog.getWindow() != null) {
            int w = (int) (getResources().getDisplayMetrics().widthPixels * 0.85f);
            dialog.getWindow().setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    /** Anteprima della forma scelta in "Forma riquadri" (richiesta 2026-09-26), mostrata prima del
     *  nome nel dialog di scelta — approssimazione visiva (stroke, non le stesse costanti esatte di
     *  QsTilesCustomizeMod.buildShapedPath, che vive in un'altra classe/contesto Xposed): l'utente
     *  ha confermato che va bene così ("le img non corrispondono esattamente, va bene comunque").
     *  shapeIndex è l'indice STABILE (non la posizione ordinata alfabeticamente nel dialog). */
    private android.graphics.drawable.Drawable shapeIconDrawable(int shapeIndex, int sizePx, int color) {
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(android.graphics.Paint.Style.STROKE);
        paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
        // 2026-09-27: spessore fisso (~2dp, come l'anello vuoto del RadioButton nativo accanto),
        // non più proporzionale alla dimensione dell'icona — era diventato troppo spesso dopo
        // l'ingrandimento 1.5x.
        paint.setStrokeWidth(ObsidianTheme.dp(requireContext(), 2));
        paint.setColor(color);
        float pad = sizePx * 0.12f;
        android.graphics.RectF b = new android.graphics.RectF(pad, pad, sizePx - pad, sizePx - pad);
        canvas.drawPath(shapeIconPath(shapeIndex, b), paint);
        return new android.graphics.drawable.BitmapDrawable(getResources(), bmp);
    }

    /** Path semplificata per icona — un poligono/ovale/round-rect di base per ciascuna delle 10
     *  forme (stesso ordine di TILE_SHAPE_KIND in QsTilesCustomizeMod), non la geometria esatta
     *  (chamfer in dp fisso, inset ellisse, ecc.) — sufficiente a farla riconoscere in piccolo. */
    private android.graphics.Path shapeIconPath(int shapeIndex, android.graphics.RectF b) {
        android.graphics.Path p = new android.graphics.Path();
        switch (shapeIndex) {
            case 0: case 2: { // Finestra, Ottagono: angolo tagliato
                float ch = b.width() * (shapeIndex == 0 ? 0.22f : 0.30f);
                p.moveTo(b.left + ch, b.top);
                p.lineTo(b.right - ch, b.top);
                p.lineTo(b.right, b.top + ch);
                p.lineTo(b.right, b.bottom - ch);
                p.lineTo(b.right - ch, b.bottom);
                p.lineTo(b.left + ch, b.bottom);
                p.lineTo(b.left, b.bottom - ch);
                p.lineTo(b.left, b.top + ch);
                p.close();
                break;
            }
            case 1: { // Goccia: TL/TR/BL larghi, BR stretto (segnalato 2026-09-26 "uguale a Rombo" —
                // il caso condiviso di prima usava lo STESSO pattern per entrambi, ora rispecchia i
                // veri raggi di QsTilesCustomizeMod.TILE_SHAPE_CORNERS_DP, {30,30,4,30}).
                float rNarrow = b.width() * 0.06f, rWide = b.width() * 0.42f;
                float[] radii = {rWide, rWide, rWide, rWide, rNarrow, rNarrow, rWide, rWide};
                p.addRoundRect(b, radii, android.graphics.Path.Direction.CW);
                break;
            }
            case 4: { // Rombo: TL/BR stretti (diagonale), TR/BL larghi — {4,30,4,30} reale.
                float rNarrow = b.width() * 0.06f, rWide = b.width() * 0.42f;
                float[] radii = {rNarrow, rNarrow, rWide, rWide, rNarrow, rNarrow, rWide, rWide};
                p.addRoundRect(b, radii, android.graphics.Path.Direction.CW);
                break;
            }
            case 7: { // Ellisse
                android.graphics.RectF r = new android.graphics.RectF(b);
                r.inset(b.width() * 0.12f, 0);
                p.addOval(r, android.graphics.Path.Direction.CW);
                break;
            }
            case 8: case 9: case 10: case 11: { // Esagono, Pentagono, Ettagono, Decagono
                int sides = shapeIndex == 8 ? 6 : shapeIndex == 9 ? 5 : shapeIndex == 10 ? 7 : 10;
                float cx = b.centerX(), cy = b.centerY(), r = Math.min(b.width(), b.height()) / 2f;
                for (int i = 0; i < sides; i++) {
                    double angle = -Math.PI / 2 + i * (2 * Math.PI / sides);
                    float x = cx + r * (float) Math.cos(angle), y = cy + r * (float) Math.sin(angle);
                    if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
                }
                p.close();
                break;
            }
            case 12: { // Croce — icona approssimata, geometria reale del riquadro non ancora scritta
                float cx = b.centerX(), cy = b.centerY(), armPad = b.width() * 0.30f;
                p.addRect(cx - armPad, b.top, cx + armPad, b.bottom, android.graphics.Path.Direction.CW);
                android.graphics.Path h = new android.graphics.Path();
                h.addRect(b.left, cy - armPad, b.right, cy + armPad, android.graphics.Path.Direction.CW);
                p.op(h, android.graphics.Path.Op.UNION);
                break;
            }
            case 13: { // Cuore — icona approssimata
                float w = b.width(), h2 = b.height();
                float cx = b.centerX(), top = b.top + h2 * 0.22f;
                p.moveTo(cx, b.bottom);
                p.cubicTo(b.left - w * 0.05f, top + h2 * 0.35f, b.left + w * 0.05f, top - h2 * 0.1f, cx, top + h2 * 0.18f);
                p.cubicTo(b.right - w * 0.05f, top - h2 * 0.1f, b.right + w * 0.05f, top + h2 * 0.35f, cx, b.bottom);
                p.close();
                break;
            }
            case 14: { // Quadrifoglio — icona approssimata, 4 petali circolari
                float cx = b.centerX(), cy = b.centerY(), petalR = b.width() * 0.28f, offset = b.width() * 0.26f;
                p.addCircle(cx, cy - offset, petalR, android.graphics.Path.Direction.CW);
                android.graphics.Path petal = new android.graphics.Path();
                petal.addCircle(cx, cy + offset, petalR, android.graphics.Path.Direction.CW);
                p.op(petal, android.graphics.Path.Op.UNION);
                petal.reset(); petal.addCircle(cx - offset, cy, petalR, android.graphics.Path.Direction.CW);
                p.op(petal, android.graphics.Path.Op.UNION);
                petal.reset(); petal.addCircle(cx + offset, cy, petalR, android.graphics.Path.Direction.CW);
                p.op(petal, android.graphics.Path.Op.UNION);
                break;
            }
            case 15: { // Stella — icona approssimata, 5 punte
                int points = 5;
                float cx = b.centerX(), cy = b.centerY();
                float rOuter = Math.min(b.width(), b.height()) / 2f, rInner = rOuter * 0.42f;
                for (int i = 0; i < points * 2; i++) {
                    double angle = -Math.PI / 2 + i * (Math.PI / points);
                    float r = (i % 2 == 0) ? rOuter : rInner;
                    float x = cx + r * (float) Math.cos(angle), y = cy + r * (float) Math.sin(angle);
                    if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
                }
                p.close();
                break;
            }
            default: { // Quadrato, Supercerchio 1/2: raggio uniforme crescente
                float r = b.width() * (shapeIndex == 3 ? 0.14f : shapeIndex == 5 ? 0.26f : 0.42f);
                p.addRoundRect(b, r, r, android.graphics.Path.Direction.CW);
            }
        }
        return p;
    }

    private SliderWidgetAdapter.SliderItem sliderRow(String title, String key, int min, int max, int def, String unit) {
        int current = ObsidianPrefs.getInt(key, def);
        return new SliderWidgetAdapter.SliderItem(
                title, current, min, max, unit, def,
                value -> ObsidianPrefs.putInt(key, value));
    }
}
