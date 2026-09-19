package it.tugaia56.obsidian.ui.fragments;

import android.app.AlertDialog;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SliderWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.ui.widgets.ImageCropOverlayView;
import it.tugaia56.obsidian.utils.AppUtils;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/** QS header image sub-screen: switch + image picker + height/opacity sliders + adjustments. */
public class QsHeaderImageFragment extends Fragment {

    private static final String PREF_HEADER        = "OBS_QS_HEADER_ENABLED";
    private static final String PREF_HEADER_ALPHA  = "OBS_QS_HEADER_ALPHA";
    private static final String PREF_HEADER_HEIGHT = "OBS_QS_HEADER_HEIGHT";
    private static final String PREF_HEADER_FADE   = "OBS_QS_HEADER_FADE_A";
    private static final String PREF_HEADER_PAD_H  = "OBS_QS_HEADER_PAD_H";
    private static final String PREF_HEADER_PAD_T  = "OBS_QS_HEADER_PAD_T";
    private static final String PREF_HEADER_SCALE   = "OBS_QS_HEADER_SCALE";
    private static final String PREF_HEADER_GRAVITY = "OBS_QS_HEADER_GRAVITY";
    private static final String PREF_HEADER_CROP_CX   = "OBS_QS_HEADER_CROP_CX";
    private static final String PREF_HEADER_CROP_CY   = "OBS_QS_HEADER_CROP_CY";
    private static final String PREF_HEADER_CROP_ZOOM = "OBS_QS_HEADER_CROP_ZOOM";
    private static final String IMAGE_FILENAME     = "qs_header_image";

    // Must match QsHeaderImage.java SCALE_TYPES order
    private static final String[] SCALE_NAMES = {
            "Riempi (ritaglia)",   // 0 – CENTER_CROP via matrix (fill both, gravity crop)
            "Adatta larghezza",    // 1 – fill width via matrix (portrait may clip height)
            "Intera",              // 2 – FIT_CENTER (whole image, proportional, no crop)
            "Originale",           // 3 – CENTER (pixel 1:1, no scaling)
    };

    private ActivityResultLauncher<String> mPickImage;
    private RecyclerView mRv;

    // ── Image preview ─────────────────────────────────────────────────────────
    private Bitmap           mPreviewBmp;
    private int              mPreviewScale = 0;   // mirrors PREF_HEADER_SCALE for live preview
    private ImageCropOverlayView mCropOverlay;    // Riempi (ritaglia) mode only

    /** Returns the external storage file where the header image is saved. */
    private File getImageFile() {
        return new File(Environment.getExternalStorageDirectory(),
                ".obsidian/" + IMAGE_FILENAME);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPickImage = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) return;
                    if (copyImageToExternal(uri)) {
                        reloadPreviewBitmap();
                        rebuild();
                        Toast.makeText(requireContext(), R.string.obs_restart_ui_hint, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(),
                                R.string.qs_header_pick_error, Toast.LENGTH_SHORT).show();
                    }
                });
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

    private void rebuild() {
        // ── Enable switch ─────────────────────────────────────────────────────
        SwitchWidgetAdapter.SwitchItem switchItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.qs_header_enabled),
                getString(R.string.qs_header_enabled_summary),
                R.drawable.ic_qs,
                ObsidianPrefs.getBoolean(PREF_HEADER, false),
                null);
        switchItem.onChanged = () -> {
            ObsidianPrefs.putBoolean(PREF_HEADER, switchItem.checked);
            Toast.makeText(requireContext(), R.string.obs_restart_ui_hint, Toast.LENGTH_SHORT).show();
        };

        // ── Image picker ──────────────────────────────────────────────────────
        ListWidgetAdapter.ListItem imgPickItem = new ListWidgetAdapter.ListItem(
                getString(R.string.qs_header_pick),
                imageLabel(),
                () -> {
                    if (!AppUtils.hasStoragePermission()) {
                        AppUtils.requestStoragePermission(requireActivity());
                    } else {
                        mPickImage.launch("image/*");
                    }
                });

        // ── Height slider ─────────────────────────────────────────────────────
        SliderWidgetAdapter.SliderItem heightItem = new SliderWidgetAdapter.SliderItem(
                getString(R.string.qs_header_height),
                ObsidianPrefs.getInt(PREF_HEADER_HEIGHT, 200),
                50, 800, "dp", 200,
                value -> {
                    ObsidianPrefs.putInt(PREF_HEADER_HEIGHT, value);
                    updateCropAspectFromHeight(value);
                    Toast.makeText(requireContext(), R.string.obs_restart_ui_hint, Toast.LENGTH_SHORT).show();
                });
        // Live: the crop box's aspect ratio is screenW:headerPx, so it must follow this
        // slider in real time, otherwise the preview shape lies about what's really cropped.
        heightItem.onLivePreview = this::updateCropAspectFromHeight;

        // ── Opacity slider ────────────────────────────────────────────────────
        SliderWidgetAdapter.SliderItem alphaItem = new SliderWidgetAdapter.SliderItem(
                getString(R.string.qs_header_alpha),
                ObsidianPrefs.getInt(PREF_HEADER_ALPHA, 100),
                0, 100, "%", 100,
                value -> {
                    ObsidianPrefs.putInt(PREF_HEADER_ALPHA, value);
                    Toast.makeText(requireContext(), R.string.obs_restart_ui_hint, Toast.LENGTH_SHORT).show();
                });

        // ── Scale type (list → dialog) ────────────────────────────────────────
        int currScale = ObsidianPrefs.getInt(PREF_HEADER_SCALE, 0);
        mPreviewScale = currScale;
        ListWidgetAdapter.ListItem scaleItem = new ListWidgetAdapter.ListItem(
                getString(R.string.qs_header_scale),
                SCALE_NAMES[Math.min(currScale, SCALE_NAMES.length - 1)],
                this::showScaleDialog);

        // ── Vertical gravity slider (mode 1 "Adatta larghezza" only: 0=top, 50=center, 100=bottom) ──
        SliderWidgetAdapter.SliderItem gravityItem = new SliderWidgetAdapter.SliderItem(
                getString(R.string.qs_header_gravity),
                ObsidianPrefs.getInt(PREF_HEADER_GRAVITY, 50),
                0, 100, "%", 50,
                value -> {
                    ObsidianPrefs.putInt(PREF_HEADER_GRAVITY, value);
                    Toast.makeText(requireContext(), R.string.obs_restart_ui_hint, Toast.LENGTH_SHORT).show();
                });

        // ── Zoom slider (mode 0 "Riempi" only) — drives the crop-preview overlay ───────
        SliderWidgetAdapter.SliderItem zoomItem = new SliderWidgetAdapter.SliderItem(
                getString(R.string.qs_header_zoom),
                ObsidianPrefs.getInt(PREF_HEADER_CROP_ZOOM, 100),
                100, 300, "%", 100,
                value -> {
                    ObsidianPrefs.putInt(PREF_HEADER_CROP_ZOOM, value);
                    if (mCropOverlay != null) mCropOverlay.setZoomPercent(value);
                });
        zoomItem.onLivePreview = value -> { if (mCropOverlay != null) mCropOverlay.setZoomPercent(value); };

        // ── Fade intensity slider (0 = off) ───────────────────────────────────
        SliderWidgetAdapter.SliderItem fadeItem = new SliderWidgetAdapter.SliderItem(
                getString(R.string.qs_header_fade),
                ObsidianPrefs.getInt(PREF_HEADER_FADE, 0),
                0, 100, "%", 0,
                value -> {
                    ObsidianPrefs.putInt(PREF_HEADER_FADE, value);
                    Toast.makeText(requireContext(), R.string.obs_restart_ui_hint, Toast.LENGTH_SHORT).show();
                });

        // ── Side padding slider ───────────────────────────────────────────────
        SliderWidgetAdapter.SliderItem padHItem = new SliderWidgetAdapter.SliderItem(
                getString(R.string.qs_header_pad_h),
                ObsidianPrefs.getInt(PREF_HEADER_PAD_H, 0),
                0, 64, "dp", 0,
                value -> {
                    ObsidianPrefs.putInt(PREF_HEADER_PAD_H, value);
                    Toast.makeText(requireContext(), R.string.obs_restart_ui_hint, Toast.LENGTH_SHORT).show();
                });

        // ── Top padding slider ────────────────────────────────────────────────
        SliderWidgetAdapter.SliderItem padTItem = new SliderWidgetAdapter.SliderItem(
                getString(R.string.qs_header_pad_t),
                ObsidianPrefs.getInt(PREF_HEADER_PAD_T, 0),
                0, 64, "dp", 0,
                value -> {
                    ObsidianPrefs.putInt(PREF_HEADER_PAD_T, value);
                    Toast.makeText(requireContext(), R.string.obs_restart_ui_hint, Toast.LENGTH_SHORT).show();
                });

        // ── Assemble ──────────────────────────────────────────────────────────
        List<RecyclerView.Adapter<?>> chain = new java.util.ArrayList<>();
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.qs_header_section))));
        chain.add(new SwitchWidgetAdapter(List.of(switchItem)));

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.dst_qs_bg_customize))));
        GroupUtils.addGroup(chain, List.of(imgPickItem, heightItem, alphaItem));

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.qs_header_adjust))));
        // scaleItem is its own group — the live preview card right after it can't
        // participate in GroupUtils (custom adapter type), so it breaks the run.
        GroupUtils.addGroup(chain, List.of(scaleItem));
        if (currScale == 0) {
            chain.add(new HeaderCropPreviewAdapter());
        }
        List<SliderWidgetAdapter.SliderItem> adjustItems = new java.util.ArrayList<>();
        if (currScale == 0) adjustItems.add(zoomItem);
        if (currScale == 1) adjustItems.add(gravityItem);
        adjustItems.add(fadeItem);
        adjustItems.add(padHItem);
        adjustItems.add(padTItem);
        GroupUtils.addGroup(chain, adjustItems);

        android.os.Parcelable scrollState = mRv.getLayoutManager() != null
                ? mRv.getLayoutManager().onSaveInstanceState() : null;
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
        if (scrollState != null && mRv.getLayoutManager() != null) {
            mRv.getLayoutManager().onRestoreInstanceState(scrollState);
        }
    }

    // ── Image preview helpers ─────────────────────────────────────────────────

    /** The crop box's aspect ratio is screenW:headerPx — must track the Altezza slider live,
     *  otherwise the preview shape doesn't match what actually gets cropped on the QS panel. */
    private void updateCropAspectFromHeight(int heightDp) {
        if (mCropOverlay == null) return;
        android.util.DisplayMetrics dm = requireContext().getResources().getDisplayMetrics();
        float screenW  = dm.widthPixels;
        float headerPx = ObsidianTheme.dp(requireContext(), heightDp);
        mCropOverlay.setAspect(headerPx > 0 ? screenW / headerPx : 1f);
    }

    private void reloadPreviewBitmap() {
        File f = getImageFile();
        if (!f.exists()) { mPreviewBmp = null; return; }
        mPreviewBmp = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (mCropOverlay != null && mPreviewBmp != null) {
            mCropOverlay.setBitmap(mPreviewBmp);
        }
    }

    /** Single-item adapter that shows the fixed-box / pannable-photo crop preview, mode 0 only.
     *  Same pattern as VolumePanelColorsFragment.CropOverlayView, extracted into
     *  ImageCropOverlayView for reuse — aspect here is screenW:headerPx (not fixed, unlike the
     *  volume bar), since that's the real on-screen shape of the QS header in "Riempi" mode. */
    private class HeaderCropPreviewAdapter extends RecyclerView.Adapter<HeaderCropPreviewAdapter.VH> {

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout card = new FrameLayout(parent.getContext());
            int hPx = ObsidianTheme.dp(parent.getContext(), 240);
            int mH  = ObsidianTheme.dp(parent.getContext(), 12);
            int mV  = ObsidianTheme.dp(parent.getContext(), 6);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, hPx);
            lp.setMargins(mH, mV, mH, mV);
            card.setLayoutParams(lp);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(ObsidianTheme.cardColor());
            bg.setCornerRadius(ObsidianTheme.dp(parent.getContext(), 16));
            card.setBackground(bg);
            card.setClipToOutline(true);

            ImageCropOverlayView overlay = new ImageCropOverlayView(parent.getContext());
            overlay.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            card.addView(overlay);

            return new VH(card, overlay);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            mCropOverlay = h.overlay;
            updateCropAspectFromHeight(ObsidianPrefs.getInt(PREF_HEADER_HEIGHT, 200));
            mCropOverlay.setCornerRadiusPx(ObsidianTheme.dp(requireContext(), 16));
            mCropOverlay.setZoomPercent(ObsidianPrefs.getInt(PREF_HEADER_CROP_ZOOM, 100));
            mCropOverlay.setCropCenter(
                    ObsidianPrefs.getInt(PREF_HEADER_CROP_CX, 50) / 100f,
                    ObsidianPrefs.getInt(PREF_HEADER_CROP_CY, 50) / 100f);
            mCropOverlay.setOnCropChangedListener((cx, cy) -> {
                ObsidianPrefs.putInt(PREF_HEADER_CROP_CX, Math.round(cx * 100));
                ObsidianPrefs.putInt(PREF_HEADER_CROP_CY, Math.round(cy * 100));
            });
            reloadPreviewBitmap();
        }

        @Override public int getItemCount() { return 1; }

        class VH extends RecyclerView.ViewHolder {
            final FrameLayout card;
            final ImageCropOverlayView overlay;
            VH(FrameLayout card, ImageCropOverlayView overlay) {
                super(card);
                this.card = card;
                this.overlay = overlay;
            }
        }
    }

    // ── Other helpers ─────────────────────────────────────────────────────────

    private String imageLabel() {
        return getImageFile().exists()
                ? getString(R.string.qs_header_pick_change)
                : getString(R.string.qs_header_pick_none);
    }

    private void showScaleDialog() {
        int curr = ObsidianPrefs.getInt(PREF_HEADER_SCALE, 0);
        ObsidianTheme.themeDialog(new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.qs_header_scale))
                .setSingleChoiceItems(SCALE_NAMES, curr, (dialog, which) -> {
                    ObsidianPrefs.putInt(PREF_HEADER_SCALE, which);
                    mPreviewScale = which;
                    rebuild();
                    Toast.makeText(requireContext(), R.string.obs_restart_ui_hint, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());
    }

    private boolean copyImageToExternal(Uri uri) {
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), ".obsidian");
            if (!dir.exists() && !dir.mkdirs()) return false;
            File dest = getImageFile();
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
}
