package it.tugaia56.obsidian.ui.fragments;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
    private ImageView        mPreviewIv;
    private Bitmap           mPreviewBmp;
    private int              mPreviewScale = 0;   // mirrors PREF_HEADER_SCALE for live preview
    private ImagePreviewAdapter mPreviewAdapter;  // kept to notify when scale mode changes

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
                    Toast.makeText(requireContext(), R.string.obs_restart_ui_hint, Toast.LENGTH_SHORT).show();
                });

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

        // ── Vertical gravity slider (CENTER_CROP only: 0=top, 50=center, 100=bottom) ───
        SliderWidgetAdapter.SliderItem gravityItem = new SliderWidgetAdapter.SliderItem(
                getString(R.string.qs_header_gravity),
                ObsidianPrefs.getInt(PREF_HEADER_GRAVITY, 50),
                0, 100, "%", 50,
                value -> {
                    ObsidianPrefs.putInt(PREF_HEADER_GRAVITY, value);
                    Toast.makeText(requireContext(), R.string.obs_restart_ui_hint, Toast.LENGTH_SHORT).show();
                });
        // Live preview: update crop position while dragging
        gravityItem.onLivePreview = this::applyPreviewMatrix;

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
        chain.add(mPreviewAdapter = new ImagePreviewAdapter());
        GroupUtils.addGroup(chain, List.of(gravityItem, fadeItem, padHItem, padTItem));

        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
    }

    // ── Image preview helpers ─────────────────────────────────────────────────

    private void reloadPreviewBitmap() {
        File f = getImageFile();
        if (!f.exists()) { mPreviewBmp = null; return; }
        mPreviewBmp = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (mPreviewIv != null && mPreviewBmp != null) {
            mPreviewIv.setImageBitmap(mPreviewBmp);
            mPreviewIv.post(() -> applyPreviewMatrix(ObsidianPrefs.getInt(PREF_HEADER_GRAVITY, 50)));
        }
    }

    private void applyPreviewMatrix(int gravity) {
        if (mPreviewIv == null || mPreviewBmp == null) return;
        float vw = mPreviewIv.getWidth();
        float vh = mPreviewIv.getHeight();
        if (vw <= 0 || vh <= 0) return;

        float bmpW = mPreviewBmp.getWidth();
        float bmpH = mPreviewBmp.getHeight();

        // Non-crop modes: use standard scale types directly.
        if (mPreviewScale == 2) { // Intera
            mPreviewIv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            return;
        }
        if (mPreviewScale == 3) { // Originale
            mPreviewIv.setScaleType(ImageView.ScaleType.CENTER);
            return;
        }

        // Crop modes (0 = Riempi, 1 = Adatta larghezza):
        // Calculate scale using ACTUAL QS dimensions (screen width × user-set height)
        // so the preview crop position matches exactly what the QS panel shows.
        android.util.DisplayMetrics dm = requireContext().getResources().getDisplayMetrics();
        float screenW  = dm.widthPixels;
        float headerPx = ObsidianTheme.dp(requireContext(), ObsidianPrefs.getInt(PREF_HEADER_HEIGHT, 200));

        float refScale = (mPreviewScale == 0)
                ? Math.max(screenW / bmpW, headerPx / bmpH)  // Riempi: fill both dimensions
                : screenW / bmpW;                              // Adatta larghezza: fill width only

        // Fraction from image top where the QS visible window starts
        float refTy   = (headerPx - bmpH * refScale) * (gravity / 100f);
        float fracTop = (bmpH * refScale > 0f) ? (-refTy / (bmpH * refScale)) : 0f;

        // Apply the same fractional crop position to the preview card dimensions
        float previewScale = (mPreviewScale == 0)
                ? Math.max(vw / bmpW, vh / bmpH)
                : vw / bmpW;
        float tx = (vw - bmpW * previewScale) / 2f;
        float ty = -fracTop * bmpH * previewScale;
        // Clamp: never go outside the image bounds
        float minTy = vh - bmpH * previewScale;
        ty = Math.max(minTy, Math.min(0f, ty));

        Matrix m = new Matrix();
        m.setScale(previewScale, previewScale);
        m.postTranslate(tx, ty);
        mPreviewIv.setScaleType(ImageView.ScaleType.MATRIX);
        mPreviewIv.setImageMatrix(m);
    }

    /** Single-item adapter that shows the header image preview card. */
    private class ImagePreviewAdapter extends RecyclerView.Adapter<ImagePreviewAdapter.VH> {

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int ctx = parent.getContext().hashCode(); // unused, just to reference context
            FrameLayout card = new FrameLayout(parent.getContext());
            int hPx = ObsidianTheme.dp(parent.getContext(), 240);
            int mH  = ObsidianTheme.dp(parent.getContext(), 12);
            int mV  = ObsidianTheme.dp(parent.getContext(), 6);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, hPx);
            lp.setMargins(mH, mV, mH, mV);
            card.setLayoutParams(lp);

            // Rounded card background
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(ObsidianTheme.cardColor());
            bg.setCornerRadius(ObsidianTheme.dp(parent.getContext(), 16));
            card.setBackground(bg);
            card.setClipToOutline(true);

            ImageView iv = new ImageView(parent.getContext());
            iv.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(iv);

            // "Nessuna immagine" placeholder
            iv.setBackgroundColor(Color.argb(0x40, 0x80, 0x80, 0x80));

            return new VH(card, iv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            mPreviewIv = h.iv;
            reloadPreviewBitmap();
            if (mPreviewBmp != null) {
                h.iv.setImageBitmap(mPreviewBmp);
                h.iv.post(() -> applyPreviewMatrix(ObsidianPrefs.getInt(PREF_HEADER_GRAVITY, 50)));
            }
        }

        @Override public int getItemCount() { return mPreviewScale == 0 ? 1 : 0; }

        class VH extends RecyclerView.ViewHolder {
            final ImageView iv;
            VH(FrameLayout card, ImageView iv) { super(card); this.iv = iv; }
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
