package it.tugaia56.obsidian.ui.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.AuthActivity;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.ui.events.ColorSelectedEvent;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/**
 * "Personalizza Riquadri" — quali delle 8 voci di Riavvio Avanzato compaiono nella griglia a
 * riquadri (vedi "Riavvio Avanzato Riquadri" in PowerMenuFragment), più app personalizzate
 * aggiuntive lanciabili direttamente dalla griglia. Stesso ordine/indici di
 * {@link AuthActivity#rebootLabels()} — se cambia là, va aggiornato anche qui.
 */
public class AdvancedRebootTilesFragment extends Fragment {

    /** Package list, comma-separated, in display order — see {@link AuthActivity} which reads
     *  the same key to render these as extra tiles after the built-in ones. */
    public static final String PREF_CUSTOM_APPS = "advanced_reboot_custom_apps";

    /** Tile background colour — "default" (the neutral cardColor() look already shipped,
     *  default so this new option doesn't change anyone's existing grid), "accent", or
     *  "custom". Read directly (no picker) by AuthActivity when drawing. */
    public static final String PREF_TILE_COLOR_MODE = "advanced_reboot_tile_color_mode";
    public static final String PREF_TILE_CUSTOM_COLOR = "advanced_reboot_tile_custom_color";
    private static final int DIALOG_TILE_COLOR = PREF_TILE_CUSTOM_COLOR.hashCode();

    /** Tile border — same Accento/Personalizzato switch pattern as "Bordo Pulsante" in
     *  PowerMenuFragment. Read directly (no picker) by AuthActivity when drawing. */
    public static final String PREF_TILE_BORDER = "advanced_reboot_tile_border_enabled";
    public static final String PREF_TILE_BORDER_USE_ACCENT = "advanced_reboot_tile_border_use_accent";
    public static final String PREF_TILE_BORDER_CUSTOM_COLOR = "advanced_reboot_tile_border_custom_color";
    private static final int DIALOG_TILE_BORDER_COLOR = PREF_TILE_BORDER_CUSTOM_COLOR.hashCode();

    private static final int[] LABEL_RES = {
            R.string.advanced_reboot_recovery,
            R.string.advanced_reboot_bootloader,
            R.string.advanced_reboot_safe_mode,
            R.string.advanced_reboot_fast_reboot,
            R.string.advanced_reboot_systemui,
            R.string.advanced_reboot_lock_screen,
            R.string.advanced_reboot_screenshot,
            R.string.advanced_reboot_fastbootd,
            R.string.advanced_reboot_restart,
            R.string.advanced_reboot_shutdown,
    };

    private RecyclerView mRv;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onColorSelected(ColorSelectedEvent event) {
        if (event.dialogId() == DIALOG_TILE_COLOR) {
            ObsidianPrefs.putInt(PREF_TILE_CUSTOM_COLOR, event.color());
        } else if (event.dialogId() == DIALOG_TILE_BORDER_COLOR) {
            ObsidianPrefs.putInt(PREF_TILE_BORDER_CUSTOM_COLOR, event.color());
        } else {
            return;
        }
        rebuild();
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

    private void rebuild() {
        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();
        ListWidgetAdapter.ListItem tileColorItem = new ListWidgetAdapter.ListItem(
                getString(R.string.advanced_reboot_tile_color_title),
                tileColorLabel(), this::showTileColorDialog);

        SwitchWidgetAdapter.SwitchItem tileBorderItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.advanced_reboot_tile_border_title),
                ObsidianPrefs.getBoolean(PREF_TILE_BORDER, false)
                        ? tileBorderColorLabel()
                        : getString(R.string.advanced_reboot_tile_border_summary),
                ObsidianPrefs.getBoolean(PREF_TILE_BORDER, false),
                null);
        tileBorderItem.onChanged = () -> {
            ObsidianPrefs.putBoolean(PREF_TILE_BORDER, tileBorderItem.checked);
            rebuild();
            if (tileBorderItem.checked) showTileBorderColorDialog();
        };
        tileBorderItem.onRowClick = () -> {
            if (ObsidianPrefs.getBoolean(PREF_TILE_BORDER, false)) showTileBorderColorDialog();
        };

        GroupUtils.addGroup(chain, List.of(tileColorItem, tileBorderItem));

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.advanced_reboot_customize_tiles_title))));
        chain.add(new ReorderSectionAdapter());

        // ── App personalizzate: ognuna lancia direttamente l'app scelta invece di un comando
        // di riavvio — tocca una riga per rimuoverla, "Aggiungi App" apre il selettore. ──
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.advanced_reboot_custom_apps_section))));
        List<String> customApps = customAppPackages();
        List<Object> appRows = new ArrayList<>();
        PackageManager pm = requireContext().getPackageManager();
        for (String pkg : customApps) {
            String label = pkg;
            try {
                label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
            } catch (PackageManager.NameNotFoundException ignored) {}
            ListWidgetAdapter.ListItem item = new ListWidgetAdapter.ListItem(
                    label, getString(R.string.advanced_reboot_tap_to_remove), () -> {
                List<String> updated = new ArrayList<>(customAppPackages());
                updated.remove(pkg);
                ObsidianPrefs.putString(PREF_CUSTOM_APPS, String.join(",", updated));
                rebuild();
            });
            appRows.add(item);
        }
        appRows.add(new ListWidgetAdapter.ListItem(
                getString(R.string.advanced_reboot_add_app), null, this::showAppPickerDialog));
        GroupUtils.addGroup(chain, appRows);

        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
    }

    private String tileColorLabel() {
        String mode = ObsidianPrefs.getString(PREF_TILE_COLOR_MODE, "default");
        if ("accent".equals(mode)) return getString(R.string.color_mode_accent);
        if ("custom".equals(mode)) {
            return String.format("#%06X", 0xFFFFFF & ObsidianPrefs.getInt(PREF_TILE_CUSTOM_COLOR, ObsidianTheme.DEFAULT_ACCENT));
        }
        return getString(R.string.color_mode_default);
    }

    private void showTileColorDialog() {
        String[] entries = {
                getString(R.string.color_mode_default),
                getString(R.string.color_mode_accent),
                getString(R.string.color_mode_custom)
        };
        String currentMode = ObsidianPrefs.getString(PREF_TILE_COLOR_MODE, "default");
        int current = "custom".equals(currentMode) ? 2 : "accent".equals(currentMode) ? 1 : 0;
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.advanced_reboot_tile_color_title)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    String newMode = selected[0] == 2 ? "custom" : selected[0] == 1 ? "accent" : "default";
                    ObsidianPrefs.putString(PREF_TILE_COLOR_MODE, newMode);
                    rebuild();
                    if ("custom".equals(newMode) && getActivity() instanceof MainActivity) {
                        int currentColor = ObsidianPrefs.getInt(PREF_TILE_CUSTOM_COLOR, ObsidianTheme.DEFAULT_ACCENT);
                        ((MainActivity) getActivity()).showColorPickerDialog(DIALOG_TILE_COLOR, currentColor, true, true, true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private String tileBorderColorLabel() {
        boolean useAccent = ObsidianPrefs.getBoolean(PREF_TILE_BORDER_USE_ACCENT, true);
        if (useAccent) return getString(R.string.color_mode_accent);
        return String.format("#%06X", 0xFFFFFF & ObsidianPrefs.getInt(PREF_TILE_BORDER_CUSTOM_COLOR, ObsidianTheme.DEFAULT_ACCENT));
    }

    private void showTileBorderColorDialog() {
        String[] entries = { getString(R.string.color_mode_accent), getString(R.string.power_menu_bg_mode_custom) };
        int current = ObsidianPrefs.getBoolean(PREF_TILE_BORDER_USE_ACCENT, true) ? 0 : 1;
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.advanced_reboot_tile_border_title)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    boolean useAccent = selected[0] == 0;
                    ObsidianPrefs.putBoolean(PREF_TILE_BORDER_USE_ACCENT, useAccent);
                    rebuild();
                    if (!useAccent && getActivity() instanceof MainActivity) {
                        int currentColor = ObsidianPrefs.getInt(PREF_TILE_BORDER_CUSTOM_COLOR, ObsidianTheme.DEFAULT_ACCENT);
                        ((MainActivity) getActivity()).showColorPickerDialog(DIALOG_TILE_BORDER_COLOR, currentColor, true, true, true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private List<String> customAppPackages() {
        String raw = ObsidianPrefs.getString(PREF_CUSTOM_APPS, "");
        List<String> result = new ArrayList<>();
        if (raw.isEmpty()) return result;
        for (String s : raw.split(",")) {
            if (!s.trim().isEmpty()) result.add(s.trim());
        }
        return result;
    }

    private void showAppPickerDialog() {
        PackageManager pm = requireContext().getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(launcherIntent, 0);
        apps.sort(Comparator.comparing(r -> r.loadLabel(pm).toString().toLowerCase()));

        LinearLayout list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(8);
        list.setPadding(pad, pad, pad, pad);

        androidx.appcompat.app.AlertDialog[] dlgRef = new androidx.appcompat.app.AlertDialog[1];
        for (ResolveInfo info : apps) {
            ApplicationInfo appInfo = info.activityInfo.applicationInfo;
            String packageName = appInfo.packageName;
            String label = info.loadLabel(pm).toString();
            Drawable icon = info.loadIcon(pm);

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));

            ImageView iv = new ImageView(requireContext());
            LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(dp(36), dp(36));
            ivLp.setMarginEnd(dp(16));
            iv.setLayoutParams(ivLp);
            iv.setImageDrawable(icon);
            row.addView(iv);

            TextView tv = new TextView(requireContext());
            tv.setText(label);
            tv.setTextColor(ObsidianTheme.textColor());
            tv.setTextSize(15);
            row.addView(tv);

            row.setOnClickListener(v -> {
                List<String> updated = new ArrayList<>(customAppPackages());
                if (!updated.contains(packageName)) {
                    updated.add(packageName);
                    ObsidianPrefs.putString(PREF_CUSTOM_APPS, String.join(",", updated));
                    rebuild();
                } else {
                    Toast.makeText(requireContext(), R.string.advanced_reboot_app_already_added, Toast.LENGTH_SHORT).show();
                }
                if (dlgRef[0] != null) dlgRef[0].dismiss();
            });
            list.addView(row);
        }

        androidx.core.widget.NestedScrollView scroll = new androidx.core.widget.NestedScrollView(requireContext());
        scroll.addView(list);

        dlgRef[0] = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.advanced_reboot_add_app))
                .setView(scroll)
                .setNegativeButton(R.string.cancel, null)
                .show();
        ObsidianTheme.themeDialog(dlgRef[0]);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ── Riordino voci (drag) — stesso ordine letto da AuthActivity per lista/griglia ─────
    // Nested RecyclerView + ItemTouchHelper dentro un unico item della ConcatAdapter esterna,
    // stesso pattern già usato altrove (CropPreviewAdapter ecc.) per evitare la matematica
    // delle posizioni "piatte" che una ConcatAdapter avrebbe altrimenti richiesto.

    private List<Integer> orderList() {
        int count = LABEL_RES.length;
        String raw = ObsidianPrefs.getString(AuthActivity.PREF_TILE_ORDER, "");
        List<Integer> order = new ArrayList<>();
        if (!raw.isEmpty()) {
            for (String s : raw.split(",")) {
                try {
                    int idx = Integer.parseInt(s.trim());
                    if (idx >= 0 && idx < count && !order.contains(idx)) order.add(idx);
                } catch (NumberFormatException ignored) {}
            }
        }
        for (int i = 0; i < count; i++) if (!order.contains(i)) order.add(i);
        return order;
    }

    private void persistOrder(List<Integer> order) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < order.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(order.get(i));
        }
        ObsidianPrefs.putString(AuthActivity.PREF_TILE_ORDER, sb.toString());
    }

    private class ReorderSectionAdapter extends RecyclerView.Adapter<ReorderSectionAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            RecyclerView nested = new RecyclerView(parent.getContext());
            nested.setLayoutManager(new LinearLayoutManager(parent.getContext()));
            nested.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            nested.setNestedScrollingEnabled(false);
            return new VH(nested);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            List<Integer> order = orderList();
            TileOrderAdapter adapter = new TileOrderAdapter(order);
            h.nested.setAdapter(adapter);
            ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                    ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
                @Override
                public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                       @NonNull RecyclerView.ViewHolder target) {
                    int from = vh.getBindingAdapterPosition();
                    int to = target.getBindingAdapterPosition();
                    if (from < 0 || to < 0) return false;
                    Collections.swap(order, from, to);
                    adapter.notifyItemMoved(from, to);
                    persistOrder(order);
                    return true;
                }

                @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {}
            });
            helper.attachToRecyclerView(h.nested);
            adapter.touchHelper = helper;
        }

        @Override public int getItemCount() { return 1; }

        class VH extends RecyclerView.ViewHolder {
            final RecyclerView nested;
            VH(RecyclerView nested) { super(nested); this.nested = nested; }
        }
    }

    private class TileOrderAdapter extends RecyclerView.Adapter<TileOrderAdapter.RowVH> {
        private final List<Integer> order;
        ItemTouchHelper touchHelper;

        TileOrderAdapter(List<Integer> order) { this.order = order; }

        @NonNull @Override
        public RowVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context ctx = parent.getContext();
            int pad = dp(14);
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(pad, pad, pad, pad);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(ObsidianTheme.cardColor());
            bg.setCornerRadius(dp(12));
            row.setBackground(bg);
            RecyclerView.LayoutParams rowLp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int m = dp(4);
            rowLp.setMargins(dp(16), m, dp(16), m);
            row.setLayoutParams(rowLp);

            ImageView handle = new ImageView(ctx);
            handle.setImageResource(R.drawable.ic_drag_handle);
            handle.setImageTintList(ColorStateList.valueOf(ObsidianTheme.textColor(0x99)));
            LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(24), dp(24));
            handleLp.setMarginEnd(dp(12));
            handle.setLayoutParams(handleLp);
            row.addView(handle);

            TextView label = new TextView(ctx);
            label.setTextColor(ObsidianTheme.textColor());
            label.setTextSize(15);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            label.setLayoutParams(labelLp);
            row.addView(label);

            SwitchCompat sw = new SwitchCompat(ctx);
            row.addView(sw);

            return new RowVH(row, handle, label, sw);
        }

        @Override
        public void onBindViewHolder(@NonNull RowVH h, int pos) {
            int idx = order.get(pos);
            h.label.setText(getString(LABEL_RES[idx]));

            int accent = ObsidianTheme.accentColor();
            int accentDim = ObsidianTheme.accentDim();
            h.sw.setTrackTintList(new ColorStateList(
                    new int[][]{{android.R.attr.state_checked}, {}},
                    new int[]{accent, accentDim}));
            h.sw.setThumbTintList(ColorStateList.valueOf(0xFFFFFFFF));

            String key = AuthActivity.TILE_PREF_PREFIX + idx + "_on";
            h.sw.setOnCheckedChangeListener(null);
            h.sw.setChecked(ObsidianPrefs.getBoolean(key, true));
            h.sw.setOnCheckedChangeListener((btn, checked) -> ObsidianPrefs.putBoolean(key, checked));

            h.handle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && touchHelper != null) {
                    touchHelper.startDrag(h);
                }
                return false;
            });
        }

        @Override public int getItemCount() { return order.size(); }

        class RowVH extends RecyclerView.ViewHolder {
            final ImageView handle;
            final TextView label;
            final SwitchCompat sw;
            RowVH(View v, ImageView handle, TextView label, SwitchCompat sw) {
                super(v);
                this.handle = handle;
                this.label = label;
                this.sw = sw;
            }
        }
    }
}
