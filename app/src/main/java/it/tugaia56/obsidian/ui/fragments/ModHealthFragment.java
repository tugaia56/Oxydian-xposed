package it.tugaia56.obsidian.ui.fragments;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.utils.Constants;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/**
 * "Stato Oxydian" — mostra, per ogni processo agganciato (SystemUI/Impostazioni/Launcher/
 * Framework), quali mod si sono installati correttamente all'ultimo avvio di quel processo
 * e quali no (findClass/findAndHookMethod che ha lanciato un'eccezione, tipicamente una
 * classe/metodo OEM rinominato da un aggiornamento ROM). Non esegue nessun controllo qui:
 * legge solo il risultato che XPLauncher.installHooks() già calcola ad ogni avvio di quel
 * processo (nessun costo aggiuntivo al boot) — per un dato aggiornato basta riavviare il
 * telefono e riaprire questa schermata, niente "verifica" attiva da lanciare.
 */
public class ModHealthFragment extends Fragment {

    private record ProcessGroup(String pkg, String label) {}

    private record ModEntry(String name, boolean ok, String exceptionType, String message) {}

    private List<ProcessGroup> groups() {
        return List.of(
                new ProcessGroup(Constants.Packages.SYSTEM_UI, "SystemUI"),
                new ProcessGroup(Constants.Packages.SETTINGS, getString(R.string.mod_health_process_settings)),
                new ProcessGroup(Constants.Packages.LAUNCHER, "Launcher"),
                new ProcessGroup(Constants.Packages.FRAMEWORK, getString(R.string.mod_health_process_framework))
        );
    }

    /** false = sezione chiusa (solo intestazione + riepilogo) — parte sempre chiusa,
     *  stesso pattern "espandi al tocco" usato altrove nell'app. */
    private final java.util.Map<String, Boolean> mExpanded = new java.util.HashMap<>();

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

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.mod_health_title))));
        GroupUtils.addGroup(chain, List.of(
                new ListWidgetAdapter.ListItem(getString(R.string.mod_health_intro), null, null)));

        for (ProcessGroup g : groups()) {
            List<ModEntry> entries = readEntries(g.pkg());
            long ts = ObsidianPrefs.getLong("mod_health_" + g.pkg() + "_ts", 0);
            int failCount = 0;
            for (ModEntry e : entries) if (!e.ok()) failCount++;
            boolean expanded = Boolean.TRUE.equals(mExpanded.get(g.pkg()));

            String arrow = expanded ? " ▾" : " ▸";
            String summary = entries.isEmpty()
                    ? getString(R.string.mod_health_never_checked)
                    : getString(R.string.mod_health_group_summary_fmt, entries.size() - failCount, entries.size());
            ListWidgetAdapter.ListItem header = new ListWidgetAdapter.ListItem(
                    g.label() + arrow, summary,
                    () -> { mExpanded.put(g.pkg(), !expanded); rebuild(); });
            header.useAccentColor = failCount > 0;
            GroupUtils.addGroup(chain, List.of(header));

            if (!expanded) continue;

            GroupUtils.addGroup(chain, List.of(new ListWidgetAdapter.ListItem(
                    getString(R.string.mod_health_last_checked),
                    ts > 0 ? formatTimestamp(ts) : getString(R.string.mod_health_never_checked),
                    null)));

            if (entries.isEmpty()) continue;
            List<Object> rows = new ArrayList<>();
            for (ModEntry e : entries) {
                ListWidgetAdapter.ListItem item = new ListWidgetAdapter.ListItem(
                        e.name(),
                        e.ok() ? getString(R.string.mod_health_ok)
                               : getString(R.string.mod_health_fail_fmt, e.exceptionType()),
                        e.ok() ? null : () -> showDetail(e));
                item.useAccentColor = !e.ok();
                rows.add(item);
            }
            GroupUtils.addGroup(chain, rows);
        }

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.mod_health_export_section))));
        GroupUtils.addGroup(chain, List.of(new ListWidgetAdapter.ListItem(
                getString(R.string.mod_health_export_title),
                getString(R.string.mod_health_export_summary),
                this::exportLog)));

        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
    }

    // ── Parsing ───────────────────────────────────────────────────────────────
    // Formato scritto da XPLauncher.installHooks(): "ModA=OK;ModB=FAIL:Tipo::messaggio"

    private List<ModEntry> readEntries(String pkg) {
        String raw = ObsidianPrefs.getString("mod_health_" + pkg, "");
        List<ModEntry> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String part : raw.split(";")) {
            int eq = part.indexOf('=');
            if (eq < 0) continue;
            String name = part.substring(0, eq);
            String status = part.substring(eq + 1);
            if ("OK".equals(status)) {
                out.add(new ModEntry(name, true, null, null));
            } else if (status.startsWith("FAIL:")) {
                String rest = status.substring(5);
                int sep = rest.indexOf("::");
                String type = sep >= 0 ? rest.substring(0, sep) : rest;
                String msg = sep >= 0 ? rest.substring(sep + 2) : "";
                out.add(new ModEntry(name, false, type, msg));
            }
        }
        return out;
    }

    private void showDetail(ModEntry e) {
        String body = e.exceptionType() + (e.message() != null && !e.message().isEmpty()
                ? "\n\n" + e.message() : "");
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(e.name())
                .setMessage(body)
                .setPositiveButton(android.R.string.ok, null)
                .show());
    }

    private String formatTimestamp(long ts) {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date(ts));
    }

    // ── Esporta log — file di testo leggibile, stesso contenuto della schermata ma con il
    // messaggio completo per ogni FAIL (troncato a 160 caratteri già in fase di scrittura). ──

    private void exportLog() {
        Context ctx = requireContext().getApplicationContext();
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("Oxydian - Stato Oxydian\n");
            sb.append("Generato: ").append(formatTimestamp(System.currentTimeMillis())).append("\n");
            for (ProcessGroup g : groups()) {
                long ts = ObsidianPrefs.getLong("mod_health_" + g.pkg() + "_ts", 0);
                sb.append("\n== ").append(g.pkg()).append(" (").append(g.label()).append(") == ");
                sb.append(ts > 0 ? "aggiornato: " + formatTimestamp(ts) : "mai verificato").append('\n');
                for (ModEntry e : readEntries(g.pkg())) {
                    if (e.ok()) {
                        sb.append("OK   ").append(e.name()).append('\n');
                    } else {
                        sb.append("FAIL ").append(e.name()).append("  ").append(e.exceptionType());
                        if (e.message() != null && !e.message().isEmpty()) sb.append(" :: ").append(e.message());
                        sb.append('\n');
                    }
                }
            }

            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(dir, "Oxydian_ModHealth.txt");
            String result;
            try {
                if (!dir.exists()) dir.mkdirs();
                try (FileWriter w = new FileWriter(file)) {
                    w.write(sb.toString());
                }
                result = file.getAbsolutePath();
            } catch (Exception ex) {
                result = null;
            }
            String finalResult = result;
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(ctx, finalResult != null
                            ? getString(R.string.mod_health_export_ok_fmt, finalResult)
                            : getString(R.string.mod_health_export_fail), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
}
