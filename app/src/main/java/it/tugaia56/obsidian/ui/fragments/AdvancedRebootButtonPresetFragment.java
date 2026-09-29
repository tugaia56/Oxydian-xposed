package it.tugaia56.obsidian.ui.fragments;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SliderWidgetAdapter;
import it.tugaia56.obsidian.ui.widgets.ImageCropOverlayView;
import it.tugaia56.obsidian.utils.AppUtils;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/**
 * "Immagine" per il pulsante Riavvio Avanzato — copia di {@link PowerMenuHandlerPresetFragment}
 * (stessa griglia preset fingerprint_N + foto propria + anteprima ritaglio), dato che il
 * pulsante è anch'esso circolare. Prefs/file indipendenti dal Pallino.
 */
public class AdvancedRebootButtonPresetFragment extends Fragment {

    private static final java.util.Set<Integer> FINGERPRINT_CREDIT_RANGE =
            new java.util.HashSet<>(java.util.Arrays.asList(74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89));

    public static final String PREF_MODE = "advanced_reboot_mode"; // "accent"|"custom"|"image"
    private static final String IMAGE_FILENAME = "advanced_reboot_button_image";
    private static final String MODE_IMAGE = "image";
    private static final String PREF_CROP_CX   = "advanced_reboot_button_crop_cx";
    private static final String PREF_CROP_CY   = "advanced_reboot_button_crop_cy";
    private static final String PREF_CROP_ZOOM = "advanced_reboot_button_crop_zoom";

    private ActivityResultLauncher<String> mPickImage;
    private Adapter mAdapter;
    private int mCurrentStyle = -2;
    private Bitmap mCropPreviewBmp;
    private ImageCropOverlayView mCropOverlay;
    private boolean mScrollToTopOnNextRebuild = false;
    private List<Integer> mStyles = new ArrayList<>();
    private RecyclerView mRv;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPickImage = registerForActivityResult(
                new ActivityResultContracts.GetContent(), this::onImagePicked);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setPadding(0, 8, 0, 24);
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
        if (mRv != null) rebuild();
    }

    private void rebuild() {
        ListWidgetAdapter.ListItem galleryItem = new ListWidgetAdapter.ListItem(
                getString(R.string.power_menu_handler_gallery_title),
                getString(R.string.power_menu_handler_gallery_summary),
                () -> mPickImage.launch("image/*"));
        ListWidgetAdapter galleryAdapter = new ListWidgetAdapter(List.of(galleryItem));

        boolean isImage = MODE_IMAGE.equals(ObsidianPrefs.getString(PREF_MODE, "accent"))
                && getImageFile().exists();
        SliderWidgetAdapter.SliderItem cropZoomItem = new SliderWidgetAdapter.SliderItem(
                getString(R.string.qs_header_zoom),
                Math.min(100, ObsidianPrefs.getInt(PREF_CROP_ZOOM, 100)),
                20, 100, "%", 100,
                value -> {
                    ObsidianPrefs.putInt(PREF_CROP_ZOOM, value);
                    if (mCropOverlay != null) mCropOverlay.setZoomPercent(value);
                });
        cropZoomItem.onLivePreview = value -> { if (mCropOverlay != null) mCropOverlay.setZoomPercent(value); };

        List<SliderWidgetAdapter.SliderItem> sliderRows = new ArrayList<>();
        if (isImage) sliderRows.add(cropZoomItem);
        SliderWidgetAdapter scaleAdapter = new SliderWidgetAdapter(sliderRows);

        List<Integer> styles = new ArrayList<>();
        Resources res = requireContext().getResources();
        String pkg = requireContext().getPackageName();
        for (int i = 0; ; i++) {
            if (res.getIdentifier("fingerprint_" + i, "drawable", pkg) == 0) break;
            styles.add(i);
        }
        mStyles = styles;
        mAdapter = new Adapter(styles);

        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();
        chain.add(galleryAdapter);
        chain.add(scaleAdapter);
        if (isImage) chain.add(new CropPreviewAdapter());
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.power_menu_handler_style_section))));
        chain.add(mAdapter);
        if (mScrollToTopOnNextRebuild) {
            mScrollToTopOnNextRebuild = false;
            mRv.setAdapter(new ConcatAdapter(chain));
            mRv.scrollToPosition(0);
        } else {
            android.os.Parcelable scrollState = mRv.getLayoutManager() != null
                    ? mRv.getLayoutManager().onSaveInstanceState() : null;
            mRv.setAdapter(new ConcatAdapter(chain));
            if (scrollState != null && mRv.getLayoutManager() != null) {
                mRv.getLayoutManager().onRestoreInstanceState(scrollState);
            }
        }
    }

    private void onImagePicked(Uri uri) {
        if (uri == null) return;
        try {
            File dest = getImageFile();
            File dir = dest.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(dest)) {
                if (in == null) return;
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            ObsidianPrefs.putString(PREF_MODE, MODE_IMAGE);
            mCurrentStyle = -1;
            ObsidianPrefs.putInt(PREF_CROP_CX, 50);
            ObsidianPrefs.putInt(PREF_CROP_CY, 50);
            ObsidianPrefs.putInt(PREF_CROP_ZOOM, 100);
            mScrollToTopOnNextRebuild = true;
            rebuild();
            AppUtils.showRestartReminder(requireContext());
        } catch (Throwable t) {
            Toast.makeText(requireContext(), R.string.qs_header_pick_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmApplyStyle(int index) {
        boolean hasImage = MODE_IMAGE.equals(ObsidianPrefs.getString(PREF_MODE, "accent"))
                && getImageFile().exists();
        if (!hasImage) { applyStyle(index); return; }
        if (index == mCurrentStyle) return;
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.advanced_reboot_button_replace_title)
                .setMessage(R.string.advanced_reboot_button_replace_message)
                .setPositiveButton(R.string.apply, (d, w) -> applyStyle(index))
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private void applyStyle(int index) {
        try {
            int resId = requireContext().getResources().getIdentifier(
                    "fingerprint_" + index, "drawable", requireContext().getPackageName());
            Drawable d = ContextCompat.getDrawable(requireContext(), resId);
            if (d == null) return;

            int maxDim = Math.max(1, ObsidianTheme.dp(requireContext(), 216));
            int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) { iw = maxDim; ih = maxDim; }
            float scale = (float) maxDim / Math.max(iw, ih);
            int w = Math.max(1, Math.round(iw * scale));
            int h = Math.max(1, Math.round(ih * scale));
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            d.setBounds(0, 0, w, h);
            d.draw(canvas);

            File dest = getImageFile();
            File dir = dest.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            try (FileOutputStream out = new FileOutputStream(dest)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            }

            ObsidianPrefs.putString(PREF_MODE, MODE_IMAGE);
            mCurrentStyle = index;
            ObsidianPrefs.putInt(PREF_CROP_CX, 50);
            ObsidianPrefs.putInt(PREF_CROP_CY, 50);
            ObsidianPrefs.putInt(PREF_CROP_ZOOM, 100);
            mScrollToTopOnNextRebuild = true;
            rebuild();
            AppUtils.showRestartReminder(requireContext());
        } catch (Throwable t) {
            Toast.makeText(requireContext(), R.string.qs_header_pick_error, Toast.LENGTH_SHORT).show();
        }
    }

    private File getImageFile() {
        return new File(Environment.getExternalStorageDirectory(), ".obsidian/" + IMAGE_FILENAME);
    }

    private void scrollToActiveStyle() {
        if (mCurrentStyle < 0 || mRv == null) return;
        int idx = mStyles.indexOf(mCurrentStyle);
        if (idx < 0) return;
        int flatPos = 1 + 1 + 1 + 1 + idx;
        mRv.scrollToPosition(flatPos);
    }

    private void reloadCropPreviewBitmap() {
        File f = getImageFile();
        if (!f.exists()) { mCropPreviewBmp = null; return; }
        mCropPreviewBmp = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (mCropOverlay != null && mCropPreviewBmp != null) mCropOverlay.setBitmap(mCropPreviewBmp);
    }

    private class CropPreviewAdapter extends RecyclerView.Adapter<CropPreviewAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout card = new FrameLayout(parent.getContext());
            int hPx = ObsidianTheme.dp(parent.getContext(), 260);
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
            mCropOverlay.setAspect(1f);
            mCropOverlay.setContainOnly(true);
            mCropOverlay.setZoomPercent(ObsidianPrefs.getInt(PREF_CROP_ZOOM, 100));
            mCropOverlay.setCropCenter(
                    ObsidianPrefs.getInt(PREF_CROP_CX, 50) / 100f,
                    ObsidianPrefs.getInt(PREF_CROP_CY, 50) / 100f);
            mCropOverlay.setOnCropChangedListener((cx, cy) -> {
                ObsidianPrefs.putInt(PREF_CROP_CX, Math.round(cx * 100));
                ObsidianPrefs.putInt(PREF_CROP_CY, Math.round(cy * 100));
            });
            mCropOverlay.setOnTapListener(AdvancedRebootButtonPresetFragment.this::scrollToActiveStyle);
            reloadCropPreviewBitmap();
        }

        @Override public int getItemCount() { return 1; }

        class VH extends RecyclerView.ViewHolder {
            final ImageCropOverlayView overlay;
            VH(FrameLayout card, ImageCropOverlayView overlay) { super(card); this.overlay = overlay; }
        }
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        private final List<Integer> items;
        Adapter(List<Integer> items) { this.items = items; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context ctx = parent.getContext();
            int pad = ObsidianTheme.dp(ctx, 12);
            int radius = ObsidianTheme.dp(ctx, 12);

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(pad, ObsidianTheme.dp(ctx, 4), pad, ObsidianTheme.dp(ctx, 4));
            row.setLayoutParams(rowLp);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(ObsidianTheme.cardColor());
            bg.setCornerRadius(radius);
            row.setBackground(bg);

            ImageView preview = new ImageView(ctx);
            preview.setTag("preview");
            int size = ObsidianTheme.dp(ctx, 48);
            LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(size, size);
            preview.setLayoutParams(previewLp);
            row.addView(preview);

            TextView label = new TextView(ctx);
            label.setTag("label");
            label.setTextColor(ObsidianTheme.textColor());
            label.setTextSize(15);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelLp.setMarginStart(ObsidianTheme.dp(ctx, 16));
            label.setLayoutParams(labelLp);
            row.addView(label);

            return new VH(row);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            int index = items.get(pos);
            boolean active = index == mCurrentStyle;

            int resId = h.itemView.getResources().getIdentifier(
                    "fingerprint_" + index, "drawable", h.itemView.getContext().getPackageName());
            Drawable d = ContextCompat.getDrawable(h.itemView.getContext(), resId);
            h.preview.setImageDrawable(d);

            String label = getString(R.string.lockscreen_fp_style, index);
            if (FINGERPRINT_CREDIT_RANGE.contains(index)) {
                label += "\nby PUI (天伞桜&PanL)";
            }
            h.label.setText(label);
            h.label.setTextColor(active
                    ? ContextCompat.getColor(requireContext(), R.color.obs_primary)
                    : ObsidianTheme.textColor());

            h.itemView.setOnClickListener(v -> confirmApplyStyle(index));
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView preview;
            TextView  label;
            VH(View v) {
                super(v);
                preview = v.findViewWithTag("preview");
                label   = v.findViewWithTag("label");
            }
        }
    }
}
