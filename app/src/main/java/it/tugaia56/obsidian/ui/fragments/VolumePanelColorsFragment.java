package it.tugaia56.obsidian.ui.fragments;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.DarkShadowColorListener;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SliderWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.ui.events.ColorSelectedEvent;
import it.tugaia56.obsidian.ui.models.DarkShadowItem;
import it.tugaia56.obsidian.utils.AppUtils;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/**
 * Color pickers for the volume slider (progress bar + background) e per l'icona del
 * cursore (colore icona — stessa struttura di qs_brightness_icon_mode in
 * QsTilesCustomizeFragment, su richiesta esplicita dell'utente).
 * Pref keys match {@link it.tugaia56.obsidian.xposed.hooks.systemui.VolumePanelMod}.
 */
public class VolumePanelColorsFragment extends Fragment {

    private static final String PREF_CUSTOM_PROGRESS = "volume_panel_seekbar_color_enabled";
    private static final String PREF_PROGRESS_COLOR  = "volume_panel_seekbar_color";
    private static final String PREF_CUSTOM_BG       = "volume_panel_seekbar_bg_color_enabled";
    private static final String PREF_BG_COLOR        = "volume_panel_seekbar_bg_color";
    /** "custom"|"gradient"|"image" — sotto PREF_CUSTOM_BG (switch on/off). */
    private static final String PREF_BG_MODE           = "volume_panel_seekbar_bg_mode";
    private static final String PREF_BG_GRADIENT_INDEX = "volume_panel_seekbar_bg_gradient_index";
    private static final String PREF_BG_IMAGE_CROP_CX   = "volume_panel_seekbar_bg_image_crop_cx";
    private static final String PREF_BG_IMAGE_CROP_CY   = "volume_panel_seekbar_bg_image_crop_cy";
    private static final String PREF_BG_IMAGE_CROP_SIZE = "volume_panel_seekbar_bg_image_crop_size";
    private static final String BG_IMAGE_FILENAME     = "volume_panel_seekbar_bg_image";
    private static final String PREF_ICON_MODE       = "qs_volume_icon_mode";
    private static final String PREF_ICON_COLOR      = "qs_volume_icon_custom_color";
    private static final String PREF_PROGRESS_LINE      = "volume_panel_seekbar_progress_line_enabled";
    private static final String PREF_PROGRESS_LINE_USE_ACCENT = "volume_panel_seekbar_progress_line_use_accent";
    private static final String PREF_PROGRESS_LINE_CUSTOM     = "volume_panel_seekbar_progress_line_custom_color";
    private static final int DIALOG_PROGRESS_LINE_CUSTOM_COLOR = PREF_PROGRESS_LINE_CUSTOM.hashCode();
    private static final String PREF_PROGRESS_LINE_FILL_MODE = "volume_panel_seekbar_progress_line_fill_mode";
    private static final String PREF_PROGRESS_LINE_GRADIENT_INDEX = "volume_panel_seekbar_progress_line_gradient_index";
    private static final String PREF_PROGRESS_LINE_FULL = "volume_panel_seekbar_progress_line_full";
    private static final String PREF_PROGRESS_LINE_WAVE = "volume_panel_seekbar_progress_line_wave";
    private static final String PREF_BORDER_ON         = "volume_panel_border_enabled";
    private static final String PREF_BORDER_USE_ACCENT = "volume_panel_border_use_accent";
    private static final String PREF_BORDER_CUSTOM     = "volume_panel_border_custom_color";
    private static final int DIALOG_BORDER_CUSTOM_COLOR = PREF_BORDER_CUSTOM.hashCode();

    private final List<DarkShadowItem> mProgressItems = new ArrayList<>();
    private final List<DarkShadowItem> mBgItems       = new ArrayList<>();
    private DarkShadowColorListener mProgressAdapter;
    private DarkShadowColorListener mBgAdapter;

    private int     mPendingDialogId = -1;
    private boolean mIsProgress      = true; // true=progress, false=bg
    private RecyclerView mRv;
    private final java.util.Map<Integer, String> mSingleColorKeys = new java.util.HashMap<>();
    private ActivityResultLauncher<String> mPickImage;

    // ── Anteprima ritaglio (solo modalità "Immagine sfondo" > Foto) — foto intera + riquadro
    // trascinabile, non più una card già ritagliata con slider di posizione. ──────────────────
    private Bitmap    mBgPreviewBmp;
    private CropOverlayView mCropOverlay;
    private BgImagePreviewAdapter mBgPreviewAdapter;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
        mPickImage = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) return;
                    if (copyImageToExternal(uri, getBgImageFile())) {
                        ObsidianPrefs.putString(PREF_BG_MODE, "image");
                        ObsidianPrefs.putBoolean(PREF_CUSTOM_BG, true);
                        // Foto nuova: il riquadro di una foto precedente non ha senso, riparte
                        // centrato a dimensione minima (più zoom, il default più "sicuro").
                        ObsidianPrefs.putInt(PREF_BG_IMAGE_CROP_CX, 50);
                        ObsidianPrefs.putInt(PREF_BG_IMAGE_CROP_CY, 50);
                        ObsidianPrefs.putInt(PREF_BG_IMAGE_CROP_SIZE, 100);
                        rebuild();
                        Toast.makeText(requireContext(), R.string.obs_restart_ui_hint, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), R.string.qs_header_pick_error, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private File getBgImageFile() {
        return new File(Environment.getExternalStorageDirectory(), ".obsidian/" + BG_IMAGE_FILENAME);
    }

    private boolean copyImageToExternal(Uri uri, File dest) {
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), ".obsidian");
            if (!dir.exists() && !dir.mkdirs()) return false;
            try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(dest)) {
                if (in == null) return false;
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void pickBgImage() {
        if (!AppUtils.hasStoragePermission()) {
            AppUtils.requestStoragePermission(requireActivity());
        } else {
            mPickImage.launch("image/*");
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setPadding(0, 8, 0, 8);
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
    public void onResume() {
        super.onResume();
        // Rinfresca le etichette (es. "Gradiente personalizzato") al ritorno da
        // GradientStopsFragment — quello screen scrive le pref ma non richiama rebuild() qui.
        if (mRv != null) rebuild();
    }

    private void rebuild() {
        // Letti subito: "Immagine sfondo"/"Progresso a riga" attivi rendono "Colore sfondo"/
        // "Colore barra progresso" non effettivi (uno vince sull'altro nel backend, if/else-if
        // in VolumePanelMod) — gli switch qui devono mostrarlo, non solo il loro pref grezzo,
        // altrimenti sembrano entrambi accesi quando in realtà uno dei due non fa nulla.
        boolean progressLineOn = ObsidianPrefs.getBoolean(PREF_PROGRESS_LINE, false);
        String  bgMode         = ObsidianPrefs.getString(PREF_BG_MODE, "custom");
        boolean bgImageActive  = !"custom".equals(bgMode);

        // ── Progress color ────────────────────────────────────────────────────
        int     progressColor = ObsidianPrefs.getInt(PREF_PROGRESS_COLOR, 0xFFFFFFFF);
        boolean progressOn    = ObsidianPrefs.getBoolean(PREF_CUSTOM_PROGRESS, false) && !progressLineOn;
        mProgressItems.clear();
        mProgressItems.add(new DarkShadowItem(
                getString(R.string.vol_panel_progress_color), "VOL_PROGRESS",
                Collections.emptyList(), Collections.emptyList(),
                null, progressColor, progressOn));

        DarkShadowColorListener.OnEnabled onProgressEnabled = item -> {
            item.setEnabled(true);
            ObsidianPrefs.putBoolean(PREF_CUSTOM_PROGRESS, true);
            ObsidianPrefs.putBoolean(PREF_PROGRESS_LINE, false); // mutuamente esclusivi
            ObsidianPrefs.putInt(PREF_PROGRESS_COLOR, item.getColor());
        };
        mProgressAdapter = new DarkShadowColorListener(mProgressItems,
                onProgressEnabled,
                item -> {
                    item.setEnabled(false);
                    ObsidianPrefs.putBoolean(PREF_CUSTOM_PROGRESS, false);
                },
                (item, dialogId) -> {
                    mPendingDialogId = dialogId;
                    mIsProgress = true;
                    showColorAccentChoice(item, dialogId, PREF_PROGRESS_COLOR, onProgressEnabled, mProgressAdapter);
                });

        // ── Background color ──────────────────────────────────────────────────
        int     bgColor = ObsidianPrefs.getInt(PREF_BG_COLOR, 0xFF808080);
        boolean bgOn    = ObsidianPrefs.getBoolean(PREF_CUSTOM_BG, false) && !bgImageActive;
        mBgItems.clear();
        mBgItems.add(new DarkShadowItem(
                getString(R.string.vol_panel_bg_color), "VOL_BG",
                Collections.emptyList(), Collections.emptyList(),
                null, bgColor, bgOn));

        DarkShadowColorListener.OnEnabled onBgEnabled = item -> {
            item.setEnabled(true);
            ObsidianPrefs.putBoolean(PREF_CUSTOM_BG, true);
            ObsidianPrefs.putString(PREF_BG_MODE, "custom"); // mutuamente esclusivo con Immagine sfondo
            ObsidianPrefs.putInt(PREF_BG_COLOR, item.getColor());
        };
        mBgAdapter = new DarkShadowColorListener(mBgItems,
                onBgEnabled,
                item -> {
                    item.setEnabled(false);
                    ObsidianPrefs.putBoolean(PREF_CUSTOM_BG, false);
                },
                (item, dialogId) -> {
                    mPendingDialogId = dialogId;
                    mIsProgress = false;
                    // No Accento here — this is a track/background colour, same treatment as
                    // "Inattivo" in Personalizza Riquadri: dark presets, not the accent option.
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).showColorPickerDialog(
                                dialogId, item.getColor(), true, true, true, ObsidianTheme.bgDerivedPresets());
                    }
                });

        // ── Immagine sfondo (separata da "Colore sfondo" su richiesta dell'utente — non voleva
        // il picker Colore/Immagine annidato dentro la riga colore) ────────────────────────────
        ListWidgetAdapter.ListItem bgImageItem = new ListWidgetAdapter.ListItem(
                getString(R.string.vol_panel_bg_image_title),
                getString(R.string.vol_panel_bg_image_summary),
                this::showBgImageModeDialog);

        // ── Ritaglio foto (solo se il mode è "image") — 2026-09-15: foto INTERA visibile in
        // anteprima con un riquadro (forma barra) trascinabile a mano invece di 2 slider di
        // posizione su una card già ritagliata — chiesto esplicitamente dall'utente. Resta un
        // solo slider, "Dimensione", percentuale DIRETTA del riquadro massimo che rientra nella
        // foto (200%=massimo, 100%=metà, scendibile a 20% per zoomare molto di più — "voglio la
        // possibilità di avere un ritaglio più piccolo", il range 100..200 iniziale non bastava). ──
        boolean bgIsPhoto = "image".equals(bgMode);
        SliderWidgetAdapter.SliderItem bgSizeItem = new SliderWidgetAdapter.SliderItem(
                getString(R.string.vol_panel_bg_image_size),
                ObsidianPrefs.getInt(PREF_BG_IMAGE_CROP_SIZE, 100),
                20, 200, "%", 100,
                value -> {
                    ObsidianPrefs.putInt(PREF_BG_IMAGE_CROP_SIZE, value);
                    // Il reset (e qualunque altra via non-trascinamento) passa da qui, non da
                    // onLivePreview — senza aggiornare l'overlay anche qui il riquadro restava
                    // fermo al valore vecchio finché non si toccava proprio il cursore.
                    if (mCropOverlay != null) mCropOverlay.setSizePercent(value);
                });
        bgSizeItem.onLivePreview = size -> { if (mCropOverlay != null) mCropOverlay.setSizePercent(size); };

        // ── Progresso a riga (invece di riempimento pieno — lascia vedere il preset/immagine
        // sotto per intero, solo una sottile riga bianca segna il livello) ─────────────────────
        SwitchWidgetAdapter.SwitchItem progressLineItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.vol_panel_progress_line_title),
                progressLineOn ? progressLineFillLabel() : null,
                progressLineOn, null);
        progressLineItem.onChanged = () -> {
            ObsidianPrefs.putBoolean(PREF_PROGRESS_LINE, progressLineItem.checked);
            if (progressLineItem.checked) ObsidianPrefs.putBoolean(PREF_CUSTOM_PROGRESS, false); // mutuamente esclusivi
            rebuild();
            if (progressLineItem.checked) showProgressLineColorDialog();
        };
        progressLineItem.onRowClick = () -> {
            if (ObsidianPrefs.getBoolean(PREF_PROGRESS_LINE, false)) showProgressLineColorDialog();
        };

        // Due switch indipendenti e combinabili, visibili solo a riga attiva:
        // "Riempimento pieno" (riga sottile vs copre tutta l'area — "non solo la righetta, ma
        // tutta la parte del progresso") e "Ondulata" (bordo dritto vs a onda — nel riempimento
        // pieno è il bordo superiore mobile che diventa dritto/onda, MAI arrotondato, richiesta
        // esplicita "non mi piace il bordo arrotondato").
        SwitchWidgetAdapter.SwitchItem fullFillItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.vol_panel_progress_line_full_title),
                getString(R.string.vol_panel_progress_line_full_summary),
                ObsidianPrefs.getBoolean(PREF_PROGRESS_LINE_FULL, false),
                null);
        fullFillItem.onChanged = () -> ObsidianPrefs.putBoolean(PREF_PROGRESS_LINE_FULL, fullFillItem.checked);

        SwitchWidgetAdapter.SwitchItem waveItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.vol_panel_progress_line_wave_title),
                null,
                ObsidianPrefs.getBoolean(PREF_PROGRESS_LINE_WAVE, false),
                null);
        waveItem.onChanged = () -> ObsidianPrefs.putBoolean(PREF_PROGRESS_LINE_WAVE, waveItem.checked);

        // ── Bordo barra ───────────────────────────────────────────────────────
        boolean borderOn = ObsidianPrefs.getBoolean(PREF_BORDER_ON, false);
        SwitchWidgetAdapter.SwitchItem borderItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.vol_panel_border_title),
                borderOn ? accentCustomLabel(PREF_BORDER_USE_ACCENT, PREF_BORDER_CUSTOM) : null,
                borderOn, null);
        borderItem.onChanged = () -> {
            ObsidianPrefs.putBoolean(PREF_BORDER_ON, borderItem.checked);
            rebuild();
            if (borderItem.checked) showBorderColorDialog();
        };
        borderItem.onRowClick = () -> {
            if (ObsidianPrefs.getBoolean(PREF_BORDER_ON, false)) showBorderColorDialog();
        };

        // ── Colore icona ─────────────────────────────────────────────────────
        // Niente SectionTitleAdapter sopra ciascun gruppo: il nome della riga sotto è già
        // identico al titolo di sezione (es. "Colore sfondo" ripetuto due volte) — tolto su
        // richiesta esplicita dell'utente.
        List<Object> lineAndBorderRows = new ArrayList<>();
        lineAndBorderRows.add(progressLineItem);
        if (progressLineOn) {
            lineAndBorderRows.add(fullFillItem);
            lineAndBorderRows.add(waveItem);
        }
        lineAndBorderRows.add(borderItem);

        // Ordine su richiesta esplicita dell'utente ("cerco di semplificarne l'uso"): Colore icona
        // per primo, poi i colori piatti, poi riga/bordo, e "Immagine sfondo" (+ anteprima
        // ritaglio, l'unica card toccabile/trascinabile) per ultima — così scorrendo per
        // raggiungere le altre opzioni non si passa sopra l'anteprima e non si rischia di
        // spostare per sbaglio il ritaglio già impostato.
        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();

        int iconMode = 0;
        try { iconMode = Integer.parseInt(ObsidianPrefs.getString(PREF_ICON_MODE, "0")); } catch (NumberFormatException ignored) {}
        List<Object> iconRows = new ArrayList<>();
        iconRows.add(iconModeItem());
        if (iconMode == 4) {
            iconRows.add(singleIconColorItem(getString(R.string.qs_tiles_brightness_icon_color_title), PREF_ICON_COLOR, 901));
        }
        it.tugaia56.obsidian.ui.adapters.GroupUtils.addGroup(chain, iconRows);

        chain.add(mProgressAdapter);
        chain.add(mBgAdapter);
        it.tugaia56.obsidian.ui.adapters.GroupUtils.addGroup(chain, lineAndBorderRows);

        if (bgIsPhoto) {
            // La preview card (custom adapter, non un ListItem) interrompe il "run" di
            // GroupUtils — stessa nota già presente in QsHeaderImageFragment per lo stesso motivo.
            it.tugaia56.obsidian.ui.adapters.GroupUtils.addGroup(chain, List.of(bgImageItem, bgSizeItem));
            chain.add(mBgPreviewAdapter = new BgImagePreviewAdapter());
        } else {
            it.tugaia56.obsidian.ui.adapters.GroupUtils.addGroup(chain, List.of(bgImageItem));
        }

        android.os.Parcelable scrollState = mRv.getLayoutManager() != null
                ? mRv.getLayoutManager().onSaveInstanceState() : null;
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
        if (scrollState != null && mRv.getLayoutManager() != null) {
            mRv.getLayoutManager().onRestoreInstanceState(scrollState);
        }
    }

    private ListWidgetAdapter.ListItem iconModeItem() {
        String[] entries = getResources().getStringArray(R.array.qs_brightness_icon_entries);
        int idx = 0;
        try { idx = Integer.parseInt(ObsidianPrefs.getString(PREF_ICON_MODE, "0")); } catch (NumberFormatException ignored) {}
        String summary = (idx >= 0 && idx < entries.length) ? entries[idx] : entries[0];
        return new ListWidgetAdapter.ListItem(
                getString(R.string.qs_tiles_brightness_icon_title_volume), summary,
                this::showIconModeDialog);
    }

    private void showIconModeDialog() {
        String[] entries = getResources().getStringArray(R.array.qs_brightness_icon_entries);
        int current = 0;
        try { current = Integer.parseInt(ObsidianPrefs.getString(PREF_ICON_MODE, "0")); } catch (NumberFormatException ignored) {}
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.qs_tiles_brightness_icon_title_volume)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putString(PREF_ICON_MODE, String.valueOf(selected[0]));
                    rebuild();
                    // Apre subito il picker su "Personalizzata" invece di lasciare una riga
                    // separata da toccare a parte — stessa correzione già fatta nella copia
                    // di questa opzione dentro QsTilesCustomizeFragment.
                    if (selected[0] == 4) {
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).showColorPickerDialog(
                                    901, ObsidianPrefs.getInt(PREF_ICON_COLOR, 0xFFFFFFFF), true, true, true);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private ListWidgetAdapter.ListItem singleIconColorItem(String title, String key, int dialogId) {
        mSingleColorKeys.put(dialogId, key);
        return new ListWidgetAdapter.ListItem(title, null, () -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showColorPickerDialog(
                        dialogId, ObsidianPrefs.getInt(key, 0xFFFFFFFF), true, true, true);
            }
        });
    }

    // ── Immagine sfondo (separata da "Colore sfondo") — reale, via il metodo pubblico
    // setInactiveTrackDrawable(Drawable) di COUIVerticalSeekBar (jadx): se non null, onDraw()
    // clippa alla stessa forma arrotondata del background e disegna QUELLO invece del colore
    // flat. Due sorgenti: preset (gradiente generato al volo, nessun file) o una foto scelta
    // dal telefono (stesso flusso copia-in-.obsidian/ usato altrove nel progetto). ─────────────

    private static final String[] BG_GRADIENT_PRESET_NAMES = {
            "Notte", "Grafite", "Ardesia", "Nebbia", "Oceano",
            "Tramonto", "Aurora", "Corallo", "Viola elettrico", "Foresta",
            "Antracite", "Cobalto", "Smeraldo", "Vinaccia",
            "Accento"
    };

    private void showBgImageModeDialog() {
        String[] entries = {
                getString(R.string.vol_panel_bg_image_preset),
                getString(R.string.vol_panel_gradient_custom),
                getString(R.string.vol_panel_bg_image_photo)
        };
        String currentMode = ObsidianPrefs.getString(PREF_BG_MODE, "custom");
        int current = "image".equals(currentMode) ? 2 : "gradient_custom".equals(currentMode) ? 1 : 0;
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.vol_panel_bg_image_title)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    if (selected[0] == 2) pickBgImage();
                    else if (selected[0] == 1) navigateToGradientStops("bg");
                    else showBgGradientPresetDialog();
                })
                .setNeutralButton(R.string.dst_disable, (d, w) -> {
                    ObsidianPrefs.putString(PREF_BG_MODE, "custom"); // torna a "Colore sfondo"
                    rebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private void navigateToGradientStops(String target) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateTo(
                    it.tugaia56.obsidian.ui.fragments.GradientStopsFragment.newInstance(target),
                    getString(R.string.gradient_stops_section));
        }
    }

    private void showBgGradientPresetDialog() {
        int current = ObsidianPrefs.getInt(PREF_BG_GRADIENT_INDEX, 0);
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.vol_panel_bg_image_preset)
                .setSingleChoiceItems(BG_GRADIENT_PRESET_NAMES, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putInt(PREF_BG_GRADIENT_INDEX, selected[0]);
                    ObsidianPrefs.putString(PREF_BG_MODE, "gradient");
                    ObsidianPrefs.putBoolean(PREF_CUSTOM_BG, true);
                    rebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    // ── Anteprima ritaglio foto — 2026-09-15, riscritta su richiesta dell'utente: foto INTERA
    // visibile (FIT_CENTER) con sopra un riquadro trascinabile a forma di barra volume, invece
    // della vecchia card già ritagliata con slider di posizione. Il riquadro (CropOverlayView)
    // replica ESATTAMENTE la stessa formula di VolumePanelMod.CenterCropBitmapDrawable (stessa
    // MIN/MAX_SIZE_FRACTION) — duplicata qui per indipendenza (stesso motivo di altre classi
    // duplicate nel progetto, es. DstDialogStyle), ma deve restare sincronizzata a mano se quella
    // cambia: quello che l'utente trascina qui deve combaciare 1:1 con quello che poi si vede
    // sulla barra reale. ───────────────────────────────────────────────────────────────────────

    private void reloadBgPreviewBitmap() {
        File f = getBgImageFile();
        if (!f.exists()) { mBgPreviewBmp = null; return; }
        mBgPreviewBmp = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (mCropOverlay != null && mBgPreviewBmp != null) {
            mCropOverlay.setBitmap(mBgPreviewBmp);
        }
    }

    /** Card con un riquadro FISSO (sempre della stessa dimensione comoda da vedere/trascinare) —
     *  è la FOTO sotto che si sposta e si ingrandisce, come un vero cropper foto (Google Photos
     *  ecc.), non più un riquadro che si rimpicciolisce fino a diventare inutilizzabile con zoom
     *  alti ("L'anteprima stessa si ingrandisce", scelto dall'utente tra le alternative). */
    private class BgImagePreviewAdapter extends RecyclerView.Adapter<BgImagePreviewAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout card = new FrameLayout(parent.getContext());
            int cardH = ObsidianTheme.dp(parent.getContext(), 440);
            int mH = ObsidianTheme.dp(parent.getContext(), 12), mV = ObsidianTheme.dp(parent.getContext(), 6);
            RecyclerView.LayoutParams cardLp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, cardH);
            cardLp.setMargins(mH, mV, mH, mV);
            card.setLayoutParams(cardLp);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(ObsidianTheme.cardColor());
            bg.setCornerRadius(ObsidianTheme.dp(parent.getContext(), 16));
            card.setBackground(bg);
            card.setClipToOutline(true);

            CropOverlayView overlay = new CropOverlayView(parent.getContext());
            overlay.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            card.addView(overlay);

            return new VH(card, overlay);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            mCropOverlay = h.overlay;
            mCropOverlay.setSizePercent(ObsidianPrefs.getInt(PREF_BG_IMAGE_CROP_SIZE, 100));
            reloadBgPreviewBitmap();
        }

        @Override public int getItemCount() { return 1; }

        class VH extends RecyclerView.ViewHolder {
            final CropOverlayView overlay;
            VH(FrameLayout card, CropOverlayView overlay) { super(card); this.overlay = overlay; }
        }
    }

    /** Riquadro FISSO a forma di barra volume (stesso rapporto d'aspetto reale ~138:540,
     *  misurato dal vivo) — disegna da sé sia la foto (trasformata via Matrix: scala+traslazione)
     *  sia lo scrim/bordo, un'unica view invece di ImageView+overlay separati, per avere pieno
     *  controllo sulla trasformazione. Trascinare sposta la FOTO sotto il riquadro (non il
     *  riquadro), come un vero cropper — lo zoom (mSizePercent, via lo slider "Zoom") controlla
     *  quanti pixel sorgente riempiono il riquadro fisso: più zoom = meno pixel sorgente = foto
     *  visivamente più ingrandita, il riquadro stesso non cambia mai dimensione. Scrive
     *  PREF_BG_IMAGE_CROP_CX/CY al termine del trascinamento (ACTION_UP). */
    private class CropOverlayView extends View {
        private static final float BAR_ASPECT = 138f / 540f;
        private android.graphics.Bitmap mBmp;
        private float mBmpW = 1f, mBmpH = 1f;
        private float mCx, mCy; // 0..1 — quale punto della foto è al centro del riquadro fisso
        private int mSizePercent = 100;
        private final android.graphics.Paint mPhotoPaint = new android.graphics.Paint(
                android.graphics.Paint.FILTER_BITMAP_FLAG | android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint mScrimPaint = new android.graphics.Paint();
        private final android.graphics.Paint mBorderPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF mBoxRectF = new android.graphics.RectF(); // fisso, ricalcolato solo a resize
        private float mDragStartX, mDragStartY, mDragStartCx, mDragStartCy;
        private boolean mDragging = false;

        CropOverlayView(android.content.Context ctx) {
            super(ctx);
            mCx = ObsidianPrefs.getInt(PREF_BG_IMAGE_CROP_CX, 50) / 100f;
            mCy = ObsidianPrefs.getInt(PREF_BG_IMAGE_CROP_CY, 50) / 100f;
            mScrimPaint.setColor(0xAA000000);
            mBorderPaint.setStyle(android.graphics.Paint.Style.STROKE);
            mBorderPaint.setStrokeWidth(ObsidianTheme.dp(ctx, 2));
            mBorderPaint.setColor(0xFFFFFFFF);
        }

        void setBitmap(android.graphics.Bitmap bmp) {
            mBmp = bmp;
            mBmpW = Math.max(1, bmp.getWidth()); mBmpH = Math.max(1, bmp.getHeight());
            invalidate();
        }

        void setSizePercent(int percent) { mSizePercent = percent; invalidate(); }

        @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            super.onSizeChanged(w, h, oldW, oldH);
            // Riquadro all'85% dell'altezza disponibile (comodo, sempre visibile e trascinabile),
            // mai più largo del 90% della card — resta fisso finché la view non cambia dimensione.
            float boxH = h * 0.85f, boxW = boxH * BAR_ASPECT;
            if (boxW > w * 0.9f) { boxW = w * 0.9f; boxH = boxW / BAR_ASPECT; }
            float left = (w - boxW) / 2f, top = (h - boxH) / 2f;
            mBoxRectF.set(left, top, left + boxW, top + boxH);
        }

        /** Stessa idea di VolumePanelMod.CenterCropBitmapDrawable — finestra massima che rientra
         *  nella sorgente all'aspect ratio della barra, poi mSizePercent (invertito: più alto =
         *  meno pixel sorgente = più zoom) la riduce. */
        private float[] computeCropSizeSrcPx() {
            float srcAspect = mBmpW / mBmpH;
            float maxCropW, maxCropH;
            if (srcAspect > BAR_ASPECT) { maxCropH = mBmpH; maxCropW = mBmpH * BAR_ASPECT; }
            else { maxCropW = mBmpW; maxCropH = mBmpW / BAR_ASPECT; }
            float frac = Math.max(0.05f, Math.min(1f, (220 - mSizePercent) / 200f));
            return new float[]{maxCropW * frac, maxCropH * frac};
        }

        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            if (mBmp == null || mBmpW <= 1f || mBmpH <= 1f || mBoxRectF.width() <= 0) return;
            float[] size = computeCropSizeSrcPx();
            float cropWSrc = size[0], cropHSrc = size[1];
            // Scala della foto: i cropWSrc pixel sorgente devono riempire esattamente il riquadro
            // fisso — più cropWSrc è piccolo (zoom alto), più la foto appare grande sullo schermo.
            float scale = mBoxRectF.width() / cropWSrc;
            float leftSrc = clamp(mBmpW * mCx - cropWSrc / 2f, 0f, mBmpW - cropWSrc);
            float topSrc  = clamp(mBmpH * mCy - cropHSrc / 2f, 0f, mBmpH - cropHSrc);
            float tx = mBoxRectF.left - leftSrc * scale, ty = mBoxRectF.top - topSrc * scale;

            android.graphics.Matrix m = new android.graphics.Matrix();
            m.setScale(scale, scale);
            m.postTranslate(tx, ty);
            canvas.drawBitmap(mBmp, m, mPhotoPaint);

            // Riquadro a forma di pillola — scrim scuro fuori con ritaglio arrotondato
            // (clipOutPath), non 4 strisce a spigolo vivo.
            float radius = mBoxRectF.width() / 2f;
            android.graphics.Path clip = new android.graphics.Path();
            clip.addRoundRect(mBoxRectF, radius, radius, android.graphics.Path.Direction.CW);
            canvas.save();
            canvas.clipOutPath(clip);
            canvas.drawRect(0, 0, getWidth(), getHeight(), mScrimPaint);
            canvas.restore();
            canvas.drawRoundRect(mBoxRectF, radius, radius, mBorderPaint);
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent event) {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    mDragStartX = event.getX(); mDragStartY = event.getY();
                    mDragStartCx = mCx; mDragStartCy = mCy;
                    mDragging = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case android.view.MotionEvent.ACTION_MOVE: {
                    if (!mDragging) return false;
                    float[] size = computeCropSizeSrcPx();
                    float cropWSrc = size[0], cropHSrc = size[1];
                    float scale = mBoxRectF.width() / cropWSrc;
                    // Trascinare la foto verso destra deve rivelare la parte SINISTRA della foto
                    // (si trascina il contenuto sotto il riquadro fermo, non il riquadro stesso).
                    float dxSrc = -(event.getX() - mDragStartX) / scale;
                    float dySrc = -(event.getY() - mDragStartY) / scale;
                    float leftSrcStart = mDragStartCx * mBmpW - cropWSrc / 2f;
                    float topSrcStart  = mDragStartCy * mBmpH - cropHSrc / 2f;
                    float newLeftSrc = clamp(leftSrcStart + dxSrc, 0f, mBmpW - cropWSrc);
                    float newTopSrc  = clamp(topSrcStart + dySrc, 0f, mBmpH - cropHSrc);
                    mCx = (newLeftSrc + cropWSrc / 2f) / mBmpW;
                    mCy = (newTopSrc + cropHSrc / 2f) / mBmpH;
                    invalidate();
                    return true;
                }
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    mDragging = false;
                    ObsidianPrefs.putInt(PREF_BG_IMAGE_CROP_CX, Math.round(mCx * 100));
                    ObsidianPrefs.putInt(PREF_BG_IMAGE_CROP_CY, Math.round(mCy * 100));
                    return true;
            }
            return super.onTouchEvent(event);
        }

        private float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    }

    // ── Bordo barra ───────────────────────────────────────────────────────────

    private String accentCustomLabel(String useAccentKey, String customColorKey) {
        boolean useAccent = ObsidianPrefs.getBoolean(useAccentKey, true);
        if (useAccent) return getString(R.string.color_mode_accent);
        return String.format("#%06X", 0xFFFFFF & ObsidianPrefs.getInt(customColorKey, 0xFF908DFF));
    }

    private void showBorderColorDialog() {
        String[] entries = { getString(R.string.color_mode_accent), getString(R.string.color_mode_custom) };
        boolean currentAccent = ObsidianPrefs.getBoolean(PREF_BORDER_USE_ACCENT, true);
        final int[] selected = {currentAccent ? 0 : 1};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.vol_panel_border_title)
                .setSingleChoiceItems(entries, selected[0], (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    boolean useAccent = selected[0] == 0;
                    ObsidianPrefs.putBoolean(PREF_BORDER_USE_ACCENT, useAccent);
                    rebuild();
                    if (!useAccent && getActivity() instanceof MainActivity) {
                        int current = ObsidianPrefs.getInt(PREF_BORDER_CUSTOM, 0xFF908DFF);
                        ((MainActivity) getActivity()).showColorPickerDialog(
                                DIALOG_BORDER_CUSTOM_COLOR, current, true, true, true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    /** Etichetta della riga "Progresso a riga": nome del preset se il riempimento è "gradient"
     *  (stessa lista di "Immagine sfondo", BG_GRADIENT_PRESET_NAMES, indice indipendente),
     *  altrimenti Accento/Personalizzato come prima. */
    private String progressLineFillLabel() {
        String mode = ObsidianPrefs.getString(PREF_PROGRESS_LINE_FILL_MODE, "color");
        if ("gradient_custom".equals(mode)) return getString(R.string.vol_panel_gradient_custom);
        if ("gradient".equals(mode)) {
            int idx = ObsidianPrefs.getInt(PREF_PROGRESS_LINE_GRADIENT_INDEX, 0);
            return (idx >= 0 && idx < BG_GRADIENT_PRESET_NAMES.length) ? BG_GRADIENT_PRESET_NAMES[idx] : BG_GRADIENT_PRESET_NAMES[0];
        }
        return accentCustomLabel(PREF_PROGRESS_LINE_USE_ACCENT, PREF_PROGRESS_LINE_CUSTOM);
    }

    /** "Progresso a riga" row tap — 4-way Accento/Personalizzato/Preset/Gradiente personalizzato
     *  chooser (richiesta esplicita: "si può usare il preset di immagine sfondo?" — stessa lista
     *  di preset, ma un indice indipendente: la riga può avere un preset diverso da quello attivo
     *  sullo sfondo; "posso mettere più di due colori?" — Gradiente personalizzato). */
    private void showProgressLineColorDialog() {
        String[] entries = {
                getString(R.string.color_mode_accent),
                getString(R.string.color_mode_custom),
                getString(R.string.vol_panel_progress_line_fill_preset),
                getString(R.string.vol_panel_gradient_custom)
        };
        String currentMode = ObsidianPrefs.getString(PREF_PROGRESS_LINE_FILL_MODE, "color");
        boolean currentAccent = ObsidianPrefs.getBoolean(PREF_PROGRESS_LINE_USE_ACCENT, true);
        int current = "gradient_custom".equals(currentMode) ? 3 : "gradient".equals(currentMode) ? 2 : (currentAccent ? 0 : 1);
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.vol_panel_progress_line_title)
                .setSingleChoiceItems(entries, selected[0], (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    if (selected[0] == 3) { navigateToGradientStops("line"); return; }
                    if (selected[0] == 2) { showProgressLineGradientPresetDialog(); return; }
                    boolean useAccent = selected[0] == 0;
                    ObsidianPrefs.putString(PREF_PROGRESS_LINE_FILL_MODE, "color");
                    ObsidianPrefs.putBoolean(PREF_PROGRESS_LINE_USE_ACCENT, useAccent);
                    rebuild();
                    if (!useAccent && getActivity() instanceof MainActivity) {
                        int current2 = ObsidianPrefs.getInt(PREF_PROGRESS_LINE_CUSTOM, 0xFFFFFFFF);
                        ((MainActivity) getActivity()).showColorPickerDialog(
                                DIALOG_PROGRESS_LINE_CUSTOM_COLOR, current2, true, true, true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }


    private void showProgressLineGradientPresetDialog() {
        int current = ObsidianPrefs.getInt(PREF_PROGRESS_LINE_GRADIENT_INDEX, 0);
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.vol_panel_progress_line_fill_preset)
                .setSingleChoiceItems(BG_GRADIENT_PRESET_NAMES, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putInt(PREF_PROGRESS_LINE_GRADIENT_INDEX, selected[0]);
                    ObsidianPrefs.putString(PREF_PROGRESS_LINE_FILL_MODE, "gradient");
                    rebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    // ── Color picker ──────────────────────────────────────────────────────────

    private void showColorPicker(int dialogId, int color) {
        if (getActivity() instanceof MainActivity)
            ((MainActivity) getActivity()).showColorPickerDialog(dialogId, color, true, true, true);
    }

    /** Accento/Personalizzato inserted before the swatch opens the raw picker — Accento resolves
     *  immediately (reuses the existing onXxxEnabled save path), Personalizzato opens the picker
     *  as before. Baked at selection time (no live re-resolve), same as every other picker. */
    private void showColorAccentChoice(DarkShadowItem item, int dialogId, String colorKey,
                                        DarkShadowColorListener.OnEnabled onEnabled,
                                        DarkShadowColorListener adapter) {
        String[] entries = { getString(R.string.color_mode_accent), getString(R.string.color_mode_custom) };
        boolean currentAccent = ObsidianPrefs.getBoolean(colorKey + "_use_accent", false);
        final int[] selected = {currentAccent ? 0 : 1};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(item.getName())
                .setSingleChoiceItems(entries, selected[0], (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    boolean useAccent = selected[0] == 0;
                    ObsidianPrefs.putBoolean(colorKey + "_use_accent", useAccent);
                    if (useAccent) {
                        item.setColor(ObsidianTheme.accentColor());
                        onEnabled.run(item);
                        if (adapter != null) adapter.notifyDataSetChanged();
                    } else {
                        showColorPicker(dialogId, item.getColor());
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onColorSelected(ColorSelectedEvent event) {
        if (event.dialogId() == DIALOG_BORDER_CUSTOM_COLOR) {
            ObsidianPrefs.putInt(PREF_BORDER_CUSTOM, event.color());
            rebuild();
            return;
        }
        if (event.dialogId() == DIALOG_PROGRESS_LINE_CUSTOM_COLOR) {
            ObsidianPrefs.putInt(PREF_PROGRESS_LINE_CUSTOM, event.color());
            rebuild();
            return;
        }
        String singleKey = mSingleColorKeys.get(event.dialogId());
        if (singleKey != null) {
            ObsidianPrefs.putInt(singleKey, event.color());
            return;
        }
        if (event.dialogId() != mPendingDialogId) return;
        mPendingDialogId = -1;

        if (mIsProgress) {
            DarkShadowItem item = mProgressItems.get(0);
            ObsidianPrefs.putBoolean(PREF_PROGRESS_COLOR + "_use_accent", false); // picking implies custom
            item.setColor(event.color());
            ObsidianPrefs.putInt(PREF_PROGRESS_COLOR, event.color());
            if (mProgressAdapter != null) mProgressAdapter.notifyDataSetChanged();
        } else {
            DarkShadowItem item = mBgItems.get(0);
            item.setColor(event.color());
            ObsidianPrefs.putInt(PREF_BG_COLOR, event.color());
            ObsidianPrefs.putString(PREF_BG_MODE, "custom"); // vince su un gradiente/foto già impostati
            if (mBgAdapter != null) mBgAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}
