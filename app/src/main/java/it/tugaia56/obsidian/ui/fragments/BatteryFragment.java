package it.tugaia56.obsidian.ui.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/**
 * Pagina Batteria: gestisce il modulo root "oxydian_battery" (script in assets/oxydian_battery, installati da qui).
 * Rapporto dei consumi (registro ogni 10 minuti, acceso/spento) e limiti in background: scansioni Wi-Fi/Bluetooth sempre
 * attive e/o app scelte. Le scelte stanno in battery.conf / restrict.list dentro il modulo, che le applica anche ad ogni avvio.
 */
public class BatteryFragment extends Fragment {

    private static final String MOD = "/data/adb/modules/oxydian_battery";
    private static final int MOD_VERSION = 2;
    private static final String ASSET_DIR = "oxydian_battery";

    private RecyclerView mRv;
    private boolean mReport;
    private int mMode;                       // bit 0 = scansioni spente, bit 1 = app limitate
    private final List<String> mApps = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
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
        load();
    }

    // ── stato del modulo ─────────────────────────────────────────────────────

    private void load() {
        final Context ctx = requireContext().getApplicationContext();
        new Thread(() -> {
            ensureModule(ctx);
            List<String> out = Shell.cmd("sh " + MOD + "/control.sh status").exec().getOut();
            boolean report = false;
            int mode = 0;
            List<String> apps = new ArrayList<>();
            for (String l : out) {
                if (l.equals("REPORT=1")) report = true;
                else if (l.equals("LIMITS=1")) mode |= 2;
                else if (l.equals("SCANS_OFF=1")) mode |= 1;
                else if (l.startsWith("app=")) apps.add(l.substring(4));
            }
            final boolean r = report;
            final int m = mode;
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                mReport = r;
                mMode = m;
                mApps.clear();
                mApps.addAll(apps);
                if (isAdded()) rebuild();
            });
        }).start();
    }

    /** Copia gli script del modulo se mancano o sono vecchi; battery.conf e restrict.list gia' presenti restano. */
    private static void ensureModule(Context ctx) {
        List<String> v = Shell.cmd("grep -m1 '^versionCode=' " + MOD + "/module.prop 2>/dev/null").exec().getOut();
        int have = 0;
        if (!v.isEmpty()) {
            try { have = Integer.parseInt(v.get(0).substring(v.get(0).indexOf('=') + 1).trim()); } catch (Exception ignored) {}
        }
        if (have >= MOD_VERSION) return;
        try {
            File dir = new File(ctx.getCacheDir(), "oxybat");
            dir.mkdirs();
            String[] names = ctx.getAssets().list(ASSET_DIR);
            if (names == null) return;
            List<String> cmds = new ArrayList<>();
            cmds.add("mkdir -p " + MOD);
            for (String n : names) {
                File f = new File(dir, n);
                try (InputStream in = ctx.getAssets().open(ASSET_DIR + "/" + n); FileOutputStream out = new FileOutputStream(f)) {
                    in.transferTo(out);
                }
                if (n.equals("battery.conf") || n.equals("restrict.list")) {
                    cmds.add("[ -f " + MOD + "/" + n + " ] || cp " + f.getAbsolutePath() + " " + MOD + "/" + n);
                } else {
                    cmds.add("cp -f " + f.getAbsolutePath() + " " + MOD + "/" + n);
                }
            }
            cmds.add("chmod 755 " + MOD + "/*.sh");
            cmds.add("chmod 644 " + MOD + "/module.prop " + MOD + "/battery.conf " + MOD + "/restrict.list");
            Shell.cmd(cmds.toArray(new String[0])).exec();
        } catch (Exception ignored) {}
    }

    private void run(Runnable after, String... cmds) {
        new Thread(() -> {
            Shell.cmd(cmds).exec();
            if (getActivity() != null && after != null) requireActivity().runOnUiThread(after);
        }).start();
    }

    // ── interfaccia ──────────────────────────────────────────────────────────

    private String[] modeNames() {
        return new String[]{getString(R.string.bat_mode_off), getString(R.string.bat_mode_scans),
                getString(R.string.bat_mode_apps), getString(R.string.bat_mode_both)};
    }

    private void rebuild() {
        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.bat_section_report))));
        SwitchWidgetAdapter.SwitchItem rep = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.bat_report_title), getString(R.string.bat_report_summary), mReport, null);
        rep.onChanged = () -> {
            mReport = rep.checked;
            run(null, "sh " + MOD + "/control.sh set REPORT " + (mReport ? 1 : 0),
                    "sh " + MOD + "/control.sh " + (mReport ? "logger-start" : "logger-stop"));
        };
        chain.add(new SwitchWidgetAdapter(List.of(rep)));
        List<Object> repRows = new ArrayList<>();
        repRows.add(new ListWidgetAdapter.ListItem(getString(R.string.bat_show_report),
                getString(R.string.bat_show_report_summary), this::showReport));
        repRows.add(new ListWidgetAdapter.ListItem(getString(R.string.bat_clear_log), "", () ->
                run(() -> android.widget.Toast.makeText(requireContext(), R.string.bat_cleared,
                        android.widget.Toast.LENGTH_SHORT).show(), "sh " + MOD + "/control.sh clear")));
        GroupUtils.addGroup(chain, repRows);

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.bat_section_limits))));
        List<Object> limRows = new ArrayList<>();
        limRows.add(new ListWidgetAdapter.ListItem(getString(R.string.bat_limits_title), modeNames()[mMode], this::showModeDialog));
        limRows.add(new ListWidgetAdapter.ListItem(getString(R.string.bat_apps_title),
                mApps.isEmpty() ? getString(R.string.bat_apps_none) : getString(R.string.bat_apps_summary, mApps.size()),
                this::showAppsDialog));
        GroupUtils.addGroup(chain, limRows);

        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
    }

    private void showModeDialog() {
        final int[] sel = {mMode};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.bat_limits_title)
                .setSingleChoiceItems(modeNames(), sel[0], (d, which) -> sel[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    mMode = sel[0];
                    run(this::rebuild,
                            "sh " + MOD + "/control.sh set LIMITS " + ((mMode & 2) != 0 ? 1 : 0),
                            "sh " + MOD + "/control.sh set SCANS_OFF " + ((mMode & 1) != 0 ? 1 : 0),
                            "sh " + MOD + "/control.sh " + (mMode == 0 ? "undo" : "apply"));
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private void showAppsDialog() {
        final PackageManager pm = requireContext().getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        final List<String> pkgs = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        List<String[]> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String p = ri.activityInfo.packageName;
            if (p.equals(requireContext().getPackageName()) || !seen.add(p)) continue;
            // si limitano le app installate dall'utente: quelle di sistema non servono e si rischia di romperle
            boolean sys = (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
            if (sys) continue;
            rows.add(new String[]{String.valueOf(ri.loadLabel(pm)), p});
        }
        Collections.sort(rows, (a, b) -> a[0].toLowerCase(Locale.ROOT).compareTo(b[0].toLowerCase(Locale.ROOT)));
        final boolean[] checked = new boolean[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            pkgs.add(rows.get(i)[1]);
            labels.add(rows.get(i)[0]);
            checked[i] = mApps.contains(rows.get(i)[1]);
        }
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.bat_apps_title)
                .setMultiChoiceItems(labels.toArray(new String[0]), checked, (d, which, on) -> checked[which] = on)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    List<String> chosen = new ArrayList<>();
                    // le app scelte che non sono piu' nell'elenco (disinstallate o di sistema) restano come sono
                    for (String p : mApps) if (!pkgs.contains(p)) chosen.add(p);
                    for (int i = 0; i < checked.length; i++) if (checked[i]) chosen.add(pkgs.get(i));
                    mApps.clear();
                    mApps.addAll(chosen);
                    List<String> cmds = new ArrayList<>();
                    cmds.add("sh " + MOD + "/control.sh undo");
                    cmds.add("printf '%s\\n' '# Una app per riga (nome del pacchetto).' > " + MOD + "/restrict.list");
                    for (String p : chosen) {
                        if (p.matches("[A-Za-z0-9._]+")) cmds.add("echo " + p + " >> " + MOD + "/restrict.list");
                    }
                    if (mMode != 0) cmds.add("sh " + MOD + "/control.sh apply");
                    run(this::rebuild, cmds.toArray(new String[0]));
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    // ── rapporto ─────────────────────────────────────────────────────────────

    private void showReport() {
        new Thread(() -> {
            List<String> out = Shell.cmd("sh " + MOD + "/control.sh report").exec().getOut();
            final String text = formatReport(out);
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.bat_show_report)
                        .setMessage(text)
                        .setPositiveButton(android.R.string.ok, null)
                        .show());
            });
        }).start();
    }

    private String formatReport(List<String> out) {
        java.util.Map<String, String> kv = new java.util.HashMap<>();
        List<String> tops = new ArrayList<>();
        for (String l : out) {
            int i = l.indexOf('=');
            if (i < 0) continue;
            String k = l.substring(0, i), v = l.substring(i + 1);
            if (k.equals("top")) tops.add(v); else kv.put(k, v);
        }
        int rows = 0;
        try { rows = Integer.parseInt(kv.getOrDefault("rows", "0")); } catch (Exception ignored) {}
        if (rows < 2) return getString(R.string.bat_rep_none);
        if (kv.containsKey("charging")) return getString(R.string.bat_rep_charging);
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.bat_rep_since, kv.getOrDefault("since", "?").replace('T', ' '), kv.getOrDefault("hours", "?")));
        sb.append("\n\n");
        try {
            sb.append(getString(R.string.bat_rep_level, Integer.parseInt(kv.get("level_start")), Integer.parseInt(kv.get("level_end"))));
        } catch (Exception ignored) {}
        if (kv.containsKey("off_ma"))
            sb.append("\n").append(getString(R.string.bat_rep_off, Integer.parseInt(kv.get("off_ma")), kv.getOrDefault("off_hours", "?")));
        if (kv.containsKey("on_ma"))
            sb.append("\n").append(getString(R.string.bat_rep_on, Integer.parseInt(kv.get("on_ma")), kv.getOrDefault("on_hours", "?")));
        if (!tops.isEmpty()) {
            sb.append("\n\n").append(getString(R.string.bat_rep_top));
            for (String t : tops) {
                int c = t.lastIndexOf(':');
                String name = c > 0 ? t.substring(0, c) : t;
                String cpu = c > 0 ? t.substring(c + 1) : "";
                sb.append("\n• ").append(name).append("  ").append(cpu).append("%");
            }
        }
        return sb.toString();
    }
}
