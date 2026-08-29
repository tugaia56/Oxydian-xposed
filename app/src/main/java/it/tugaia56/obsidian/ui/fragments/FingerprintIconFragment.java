package it.tugaia56.obsidian.ui.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.ArrayList;
import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
import it.tugaia56.obsidian.ui.adapters.NavAdapter;
import it.tugaia56.obsidian.ui.adapters.SliderWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.utils.AppUtils;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.xposed.hooks.systemui.FingerprintIconMods;

/**
 * Icona Impronta Digitale hub — mirrors OC's fingerprint icon section:
 *   - Remove / Custom icon toggles
 *   - Preset picker (61 presets, same order as OC)
 *   - Custom image picker (pick from gallery)
 *   - Icon scaling
 */
public class FingerprintIconFragment extends Fragment {

    private ActivityResultLauncher<String> mImagePicker;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mImagePicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(), this::onImagePicked);
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
        RecyclerView rv = (RecyclerView) view;

        SwitchWidgetAdapter.SwitchItem removeItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.lockscreen_fp_remove_icon), null,
                ObsidianPrefs.getBoolean("lockscreen_fp_remove_icon", false),
                null);
        removeItem.onChanged = () -> {
            ObsidianPrefs.putBoolean("lockscreen_fp_remove_icon", removeItem.checked);
            AppUtils.showRestartReminder(requireContext());
        };

        SwitchWidgetAdapter.SwitchItem customItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.lockscreen_fp_custom_icon), null,
                ObsidianPrefs.getBoolean("lockscreen_fp_custom_icon", false),
                null);
        customItem.onChanged = () -> {
            ObsidianPrefs.putBoolean("lockscreen_fp_custom_icon", customItem.checked);
            AppUtils.showRestartReminder(requireContext());
        };
        List<RecyclerView.Adapter<?>> toggleChain = new ArrayList<>();
        GroupUtils.addGroup(toggleChain, List.of(removeItem, customItem));

        List<NavAdapter.NavItem> navItems = List.of(
                new NavAdapter.NavItem(
                        R.drawable.ic_lock,
                        getString(R.string.lockscreen_fp_icon_title), null,
                        () -> navigate(new FingerprintPresetFragment(), getString(R.string.lockscreen_fp_icon_title))),
                new NavAdapter.NavItem(
                        R.drawable.ic_lock,
                        getString(R.string.lockscreen_fp_icon_picker_title),
                        getString(R.string.lockscreen_fp_icon_picker_summary),
                        () -> mImagePicker.launch("image/*"))
        );
        NavAdapter navAdapter = new NavAdapter(navItems, 0xFF00BCD4); // cyan, colore categoria "Schermata di Blocco"

        SliderWidgetAdapter scaleAdapter = scaleRow();

        List<RecyclerView.Adapter<?>> chain = new ArrayList<>(toggleChain);
        chain.add(navAdapter);
        chain.add(scaleAdapter);
        rv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
    }

    // ── Custom image picker ─────────────────────────────────────────────────

    private void onImagePicked(Uri uri) {
        if (uri == null) return;
        try {
            File dir = new File(FingerprintIconMods.CUSTOM_ICON_FILE).getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(FingerprintIconMods.CUSTOM_ICON_FILE)) {
                if (in == null) return;
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            ObsidianPrefs.putBoolean("lockscreen_fp_custom_icon", true);
            ObsidianPrefs.putInt("lockscreen_fp_icon_custom", -1);
            AppUtils.showRestartReminder(requireContext());
        } catch (Throwable t) {
            Toast.makeText(requireContext(), t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ── Scale slider ─────────────────────────────────────────────────────────

    /** Range 0.5–2.0 stored as a float pref, shown/edited here as a 50–200% integer slider. */
    private SliderWidgetAdapter scaleRow() {
        float current = ObsidianPrefs.getFloat("lockscreen_fp_icon_scaling", 1.0f);
        int currentPct = Math.round(current * 100);
        SliderWidgetAdapter.SliderItem item = new SliderWidgetAdapter.SliderItem(
                getString(R.string.lockscreen_fp_icon_scale), currentPct, 50, 200, "%", 100,
                value -> {
                    ObsidianPrefs.putFloat("lockscreen_fp_icon_scaling", value / 100f);
                    AppUtils.showRestartReminder(requireContext());
                });
        return new SliderWidgetAdapter(List.of(item));
    }

    private void navigate(Fragment fragment, String title) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateTo(fragment, title);
        }
    }
}
