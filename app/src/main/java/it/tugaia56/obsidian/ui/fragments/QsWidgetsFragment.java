package it.tugaia56.obsidian.ui.fragments;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/**
 * Widget Impostazioni Rapide — struttura di navigazione + gestione elenco uguale a OC
 * (pulsante "Aggiungi Widget" → si vedono solo i widget scelti, non più un elenco statico
 * di tutti i tipi). Persiste l'elenco (stessa forma di OC: CSV di id — "photo","weather",
 * "w:wifi","ca1:<package>" ecc., chiave "qs_widgets_list") così un futuro hook lato
 * SystemUI può leggerlo direttamente. Riordino con TRASCINAMENTO non incluso (il vero
 * sistema drag-and-drop di OC resta un sottosistema grande a sé, stessa scala di "Aggiungi
 * Widget" della schermata di blocco) — al suo posto, "Sposta su/giù" nel dialog che si apre
 * toccando una riga: per un elenco di massimo 4 elementi ottiene lo stesso risultato con una
 * frazione del lavoro.
 */
public class QsWidgetsFragment extends Fragment {

    private static final String KEY_WIDGETS_ON = "qs_widgets_switch";
    private static final String KEY_WIDGETS_LIST = "qs_widgets_list";

    /** Il pannello QS reale mostra al massimo 2 righe da 2 (4 widget) — oltre, il resto viene
     *  tagliato via e non c'è modo di scorrere per raggiungerlo (limite del pannello media di
     *  OOS, non ancora superabile). */
    private static final int MAX_WIDGETS = 4;

    /** Stessi id di OC (QuickSettingsWidgets.mAvailableWidgets), minus "media" che lì è
     *  il widget di default sempre presente — qui trattato come uno scegliibile qualsiasi. */
    private static final String[] AVAILABLE = {
            "photo", "weather", "media",
            "w:wifi", "w:data", "w:calculator", "w:torch", "w:ringer", "w:bt",
            "w:homecontrols", "w:wallet",
            "ca1:", "ca2:", "ca3:", "ca4:"
    };

    private RecyclerView mRv;

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

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.qs_widgets_section))));
        GroupUtils.addGroup(chain, List.of(gatingSwitch(
                getString(R.string.qs_widgets_switch_title), getString(R.string.qs_widgets_switch_title_summary), KEY_WIDGETS_ON)));

        List<String> widgets = currentList();
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.qs_widgets_list_section))));
        if (widgets.size() > MAX_WIDGETS) {
            GroupUtils.addGroup(chain, List.of(
                    new ListWidgetAdapter.ListItem(getString(R.string.qs_widgets_too_many), null, null)));
        }
        if (widgets.isEmpty()) {
            GroupUtils.addGroup(chain, List.of(
                    new ListWidgetAdapter.ListItem(getString(R.string.qs_widgets_list_empty), null, null)));
        } else {
            List<Object> rows = new ArrayList<>();
            for (int i = 0; i < widgets.size(); i++) {
                String w = widgets.get(i);
                int index = i;
                rows.add(new ListWidgetAdapter.ListItem(widgetLabel(w),
                        getString(R.string.qs_widgets_row_hint), () -> showWidgetActionsDialog(index)));
            }
            GroupUtils.addGroup(chain, rows);
        }
        GroupUtils.addGroup(chain, List.of(
                new ListWidgetAdapter.ListItem(getString(R.string.qs_widgets_add), null, this::showAddWidgetDialog)));

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.qs_widgets_status_section))));
        GroupUtils.addGroup(chain, List.of(
                new ListWidgetAdapter.ListItem(getString(R.string.qs_widgets_status_body), null, null)));

        android.os.Parcelable scrollState = mRv.getLayoutManager() != null
                ? mRv.getLayoutManager().onSaveInstanceState() : null;
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
        if (scrollState != null && mRv.getLayoutManager() != null) {
            mRv.getLayoutManager().onRestoreInstanceState(scrollState);
        }
    }

    // ── Elenco widget aggiunti ───────────────────────────────────────────────

    private List<String> currentList() {
        String csv = ObsidianPrefs.getString(KEY_WIDGETS_LIST, "media");
        List<String> out = new ArrayList<>();
        for (String s : csv.split(",")) {
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private void saveList(List<String> widgets) {
        ObsidianPrefs.putString(KEY_WIDGETS_LIST, String.join(",", widgets));
    }

    private void removeWidget(String widget) {
        List<String> widgets = currentList();
        widgets.remove(widget);
        saveList(widgets);
        rebuild();
    }

    private void addWidget(String widget) {
        List<String> widgets = currentList();
        widgets.add(widget);
        saveList(widgets);
        rebuild();
    }

    /** Niente trascinamento (vedi nota in cima al file) — riordino con "Sposta su/giù" invece,
     *  molto più semplice da costruire per un elenco di al massimo 4 elementi e ugualmente
     *  efficace. Stesso tocco sulla riga apre anche "Rimuovi", sostituendo il tap diretto. */
    private void showWidgetActionsDialog(int index) {
        List<String> widgets = currentList();
        if (index < 0 || index >= widgets.size()) return;
        String widget = widgets.get(index);
        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (index > 0) {
            options.add(getString(R.string.qs_widgets_move_up));
            actions.add(() -> moveWidget(index, index - 1));
        }
        if (index < widgets.size() - 1) {
            options.add(getString(R.string.qs_widgets_move_down));
            actions.add(() -> moveWidget(index, index + 1));
        }
        options.add(getString(R.string.qs_widgets_remove_action));
        actions.add(() -> removeWidget(widget));
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(widgetLabel(widget))
                .setItems(options.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private void moveWidget(int from, int to) {
        List<String> widgets = currentList();
        if (from < 0 || from >= widgets.size() || to < 0 || to >= widgets.size()) return;
        String w = widgets.remove(from);
        widgets.add(to, w);
        saveList(widgets);
        rebuild();
    }

    // ── Selettore "Aggiungi Widget" ──────────────────────────────────────────

    private void showAddWidgetDialog() {
        Set<String> already = new LinkedHashSet<>(currentList());
        if (already.size() >= MAX_WIDGETS) {
            ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.qs_widgets_add)
                    .setMessage(R.string.qs_widgets_too_many)
                    .setPositiveButton(android.R.string.ok, null)
                    .show());
            return;
        }
        List<String> selectable = new ArrayList<>();
        for (String w : AVAILABLE) {
            if (w.startsWith("ca")) {
                // Slot app personalizzata: selezionabile finché non è già occupato.
                boolean taken = already.stream().anyMatch(a -> a.startsWith(w));
                if (!taken) selectable.add(w);
            } else if (!already.contains(w)) {
                selectable.add(w);
            }
        }
        if (selectable.isEmpty()) {
            ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.qs_widgets_add)
                    .setMessage(R.string.qs_widgets_add_all_used)
                    .setPositiveButton(android.R.string.ok, null)
                    .show());
            return;
        }
        String[] labels = selectable.stream().map(this::widgetLabel).toArray(String[]::new);
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.qs_widgets_add)
                .setItems(labels, (d, which) -> {
                    String widget = selectable.get(which);
                    if (widget.startsWith("ca")) {
                        showAppPickerDialog(widget);
                    } else {
                        addWidget(widget);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    /** label + icon già risolti, per non richiamare PackageManager durante lo scroll. */
    private record AppEntry(String packageName, String label, Drawable icon) {}

    private void showAppPickerDialog(String slotPrefix) {
        // Il dialog si apre SUBITO con uno spinner: la query + risoluzione icone
        // (~150-250 app su un telefono tipico) gira su thread separato e riempie la
        // RecyclerView solo a lavoro finito, così non c'è un'attesa a vuoto prima
        // che succeda qualcosa sullo schermo.
        FrameLayout container = new FrameLayout(requireContext());

        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        int pad = dp(8);
        rv.setPadding(pad, pad, pad, pad);
        rv.setClipToPadding(false);
        rv.setVisibility(View.GONE);
        container.addView(rv);

        ProgressBar progress = new ProgressBar(requireContext());
        FrameLayout.LayoutParams progressLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        progressLp.topMargin = dp(32);
        progressLp.bottomMargin = dp(32);
        progress.setLayoutParams(progressLp);
        container.addView(progress);

        androidx.appcompat.app.AlertDialog[] dlgRef = new androidx.appcompat.app.AlertDialog[1];
        dlgRef[0] = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.lockscreen_widgets_pick_app_title))
                .setView(container)
                .setNegativeButton(R.string.cancel, null)
                .show();
        ObsidianTheme.themeDialog(dlgRef[0]);

        new Thread(() -> {
            long t0 = android.os.SystemClock.elapsedRealtime();
            PackageManager pm = requireContext().getPackageManager();
            Intent launcherIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> apps = pm.queryIntentActivities(launcherIntent, 0);
            apps.sort(Comparator.comparing(r -> r.loadLabel(pm).toString().toLowerCase()));
            long t1 = android.os.SystemClock.elapsedRealtime();

            List<AppEntry> entries = new ArrayList<>();
            for (ResolveInfo info : apps) {
                entries.add(new AppEntry(
                        info.activityInfo.applicationInfo.packageName,
                        info.loadLabel(pm).toString(),
                        info.loadIcon(pm)));
            }
            long t2 = android.os.SystemClock.elapsedRealtime();
            android.util.Log.d("Obsidian", "QsWidgets appPicker: query+sort=" + (t1 - t0)
                    + "ms icons(" + entries.size() + " apps)=" + (t2 - t1) + "ms total=" + (t2 - t0) + "ms");

            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);
                rv.setVisibility(View.VISIBLE);
                rv.setAdapter(new AppPickerAdapter(entries, packageName -> {
                    addWidget(slotPrefix + packageName);
                    if (dlgRef[0] != null) dlgRef[0].dismiss();
                }));
            });
        }).start();
    }

    private class AppPickerAdapter extends RecyclerView.Adapter<AppPickerAdapter.VH> {
        private final List<AppEntry> items;
        private final java.util.function.Consumer<String> onPick;

        AppPickerAdapter(List<AppEntry> items, java.util.function.Consumer<String> onPick) {
            this.items = items;
            this.onPick = onPick;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));

            ImageView iv = new ImageView(parent.getContext());
            LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(dp(36), dp(36));
            ivLp.setMarginEnd(dp(16));
            iv.setLayoutParams(ivLp);
            row.addView(iv);

            TextView tv = new TextView(parent.getContext());
            tv.setTextColor(ObsidianTheme.systemDialogTextColor(parent.getContext()));
            tv.setTextSize(15);
            row.addView(tv);

            return new VH(row, iv, tv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            AppEntry entry = items.get(pos);
            h.icon.setImageDrawable(entry.icon());
            h.label.setText(entry.label());
            h.itemView.setOnClickListener(v -> onPick.accept(entry.packageName()));
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView label;
            VH(View v, ImageView icon, TextView label) {
                super(v);
                this.icon = icon;
                this.label = label;
            }
        }
    }

    // ── Etichette ─────────────────────────────────────────────────────────────

    private String widgetLabel(String widget) {
        if (widget.startsWith("ca")) {
            String[] split = widget.split(":", 2);
            if (split.length > 1 && !split[1].isEmpty()) {
                try {
                    return getString(R.string.qs_widgets_type_customapp) + " · "
                            + requireContext().getPackageManager()
                                    .getApplicationLabel(requireContext().getPackageManager()
                                            .getApplicationInfo(split[1], 0));
                } catch (PackageManager.NameNotFoundException ignored) {}
            }
            return getString(R.string.qs_widgets_type_customapp);
        }
        return switch (widget) {
            // "Foto" e "Dati Mobili" restano scelte selezionabili (come nel picker SdB) ma non
            // ancora collegate lato Mod (resolveWidget le nasconde) — suffisso per non far
            // credere che appariranno davvero nel pannello QS una volta scelte.
            case "photo" -> getString(R.string.qs_widgets_type_photo) + getString(R.string.widget_wip_suffix);
            case "weather" -> getString(R.string.qs_widgets_type_weather);
            case "media" -> getString(R.string.qs_widgets_type_media);
            case "w:wifi" -> getString(R.string.wifi);
            case "w:data" -> getString(R.string.data) + getString(R.string.widget_wip_suffix);
            case "w:calculator" -> getString(R.string.calculator);
            case "w:torch" -> getString(R.string.torch);
            case "w:ringer" -> getString(R.string.ringer);
            case "w:bt" -> getString(R.string.bt);
            case "w:homecontrols" -> getString(R.string.home_controls);
            case "w:wallet" -> getString(R.string.wallet);
            default -> widget;
        };
    }

    private SwitchWidgetAdapter.SwitchItem gatingSwitch(String title, String summary, String key) {
        SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                title, summary, ObsidianPrefs.getBoolean(key, false), null);
        item.onChanged = () -> ObsidianPrefs.putBoolean(key, item.checked);
        return item;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
