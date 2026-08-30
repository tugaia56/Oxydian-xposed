package it.tugaia56.obsidian.ui.fragments;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.utils.AppUtils;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/**
 * "Immagine" per il Pallino del Menù Accensione — stessa idea di FingerprintPresetFragment
 * (griglia dei preset fingerprint_N già inclusi nell'app, riusati qui come forme decorative
 * circolari pronte) PLUS una riga in cima per scegliere una foto propria dalla galleria —
 * unico screen invece di due, dato che qui non serve nemmeno il toggle remove/custom di
 * FingerprintIconFragment (l'attivazione è già lo switch "Colore Pallino" sulla schermata
 * precedente). Il preset scelto viene renderizzato in bitmap e salvato nello stesso file PNG
 * che una foto da galleria userebbe — MiscMods.java non distingue le due origini.
 */
public class PowerMenuHandlerPresetFragment extends Fragment {

    private static final String PREF_HANDLER_MODE = "power_menu_handler_mode";
    private static final String HANDLER_IMAGE_FILENAME = "power_menu_handler_image";
    private static final String BG_MODE_IMAGE = "image";

    private ActivityResultLauncher<String> mPickImage;
    private Adapter mAdapter;
    private int mCurrentStyle = -2; // -2 = nessuno evidenziato (foto propria attiva)

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

        ListWidgetAdapter.ListItem galleryItem = new ListWidgetAdapter.ListItem(
                getString(R.string.power_menu_handler_gallery_title),
                getString(R.string.power_menu_handler_gallery_summary),
                () -> mPickImage.launch("image/*"));
        ListWidgetAdapter galleryAdapter = new ListWidgetAdapter(List.of(galleryItem));

        List<Integer> styles = new ArrayList<>();
        Resources res = requireContext().getResources();
        String pkg = requireContext().getPackageName();
        for (int i = 0; ; i++) {
            if (res.getIdentifier("fingerprint_" + i, "drawable", pkg) == 0) break;
            styles.add(i);
        }
        mAdapter = new Adapter(styles);

        RecyclerView.Adapter<?>[] chain = {
                galleryAdapter,
                new SectionTitleAdapter(List.of(getString(R.string.power_menu_handler_style_section))),
                mAdapter
        };
        ((RecyclerView) view).setAdapter(new ConcatAdapter(chain));
    }

    // ── Foto propria da galleria ─────────────────────────────────────────────

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
            ObsidianPrefs.putString(PREF_HANDLER_MODE, BG_MODE_IMAGE);
            mCurrentStyle = -1;
            if (mAdapter != null) mAdapter.notifyDataSetChanged();
            AppUtils.showRestartReminder(requireContext());
        } catch (Throwable t) {
            Toast.makeText(requireContext(), R.string.qs_header_pick_error, Toast.LENGTH_SHORT).show();
        }
    }

    // ── Preset (stessi fingerprint_N già inclusi nell'app) ──────────────────

    private void applyStyle(int index) {
        try {
            int resId = requireContext().getResources().getIdentifier(
                    "fingerprint_" + index, "drawable", requireContext().getPackageName());
            Drawable d = ContextCompat.getDrawable(requireContext(), resId);
            if (d == null) return;

            int size = Math.max(1, ObsidianTheme.dp(requireContext(), 216));
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            d.setBounds(0, 0, size, size);
            d.draw(canvas);

            File dest = getImageFile();
            File dir = dest.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            try (FileOutputStream out = new FileOutputStream(dest)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            }

            ObsidianPrefs.putString(PREF_HANDLER_MODE, BG_MODE_IMAGE);
            mCurrentStyle = index;
            mAdapter.notifyDataSetChanged();
            AppUtils.showRestartReminder(requireContext());
        } catch (Throwable t) {
            Toast.makeText(requireContext(), R.string.qs_header_pick_error, Toast.LENGTH_SHORT).show();
        }
    }

    private File getImageFile() {
        return new File(Environment.getExternalStorageDirectory(), ".obsidian/" + HANDLER_IMAGE_FILENAME);
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

            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
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

            h.label.setText(getString(R.string.lockscreen_fp_style, index));
            h.label.setTextColor(active
                    ? ContextCompat.getColor(requireContext(), R.color.obs_primary)
                    : ObsidianTheme.textColor());

            h.itemView.setOnClickListener(v -> applyStyle(index));
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
