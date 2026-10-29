package it.tugaia56.obsidian.ui.fragments;

import android.content.Context;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
import it.tugaia56.obsidian.ui.adapters.InfoCardAdapter;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/**
 * Log di avvio: gestisce il modulo root "oxydian_bootcap" (script in assets/oxydian_bootcap). Acceso, al prossimo
 * riavvio registra 4 minuti di carico (CPU, memoria, swap, processi piu' pesanti) in /data/local/tmp/bootcap.log e poi
 * si spegne da solo ("una tantum"), oppure resta acceso ad ogni avvio ("ogni avvio"). Da qui si legge il riassunto.
 */
public class BootLogFragment extends Fragment {

    private static final String MOD = "/data/adb/modules/oxydian_bootcap";
    private static final int MOD_VERSION = 6;
    private static final String ASSET_DIR = "oxydian_bootcap";

    private RecyclerView mRv;
    private boolean mArmed, mAlways, mHasLog;

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

    private void load() {
        final Context ctx = requireContext().getApplicationContext();
        new Thread(() -> {
            ensureModule(ctx);
            List<String> out = Shell.cmd("sh " + MOD + "/control.sh status").exec().getOut();
            boolean armed = out.contains("armed=1"), always = out.contains("always=1"), haslog = out.contains("haslog=1");
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                mArmed = armed;
                mAlways = always;
                mHasLog = haslog;
                if (isAdded()) rebuild();
            });
        }).start();
    }

    /** Copia gli script del modulo se mancano o sono vecchi (stato e log restano). Un modulo nuovo parte spento. */
    private static void ensureModule(Context ctx) {
        List<String> v = Shell.cmd("grep -m1 '^versionCode=' " + MOD + "/module.prop 2>/dev/null").exec().getOut();
        boolean existed = !v.isEmpty();
        int have = 0;
        if (existed) {
            try { have = Integer.parseInt(v.get(0).substring(v.get(0).indexOf('=') + 1).trim()); } catch (Exception ignored) {}
        }
        if (have >= MOD_VERSION) return;
        try {
            File dir = new File(ctx.getCacheDir(), "oxyboot");
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
                cmds.add("cp -f " + f.getAbsolutePath() + " " + MOD + "/" + n);
            }
            cmds.add("chmod 755 " + MOD + "/*.sh");
            cmds.add("chmod 644 " + MOD + "/module.prop");
            if (!existed) cmds.add("touch " + MOD + "/disable");   // nuovo: spento finche' non lo accendi
            Shell.cmd(cmds.toArray(new String[0])).exec();
        } catch (Exception ignored) {}
    }

    private void run(Runnable after, String... cmds) {
        new Thread(() -> {
            Shell.cmd(cmds).exec();
            if (getActivity() != null && after != null) requireActivity().runOnUiThread(after);
        }).start();
    }

    private void rebuild() {
        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();
        SwitchWidgetAdapter.SwitchItem on = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.boot_title), getString(R.string.boot_summary), mArmed, null);
        on.onChanged = () -> {
            mArmed = on.checked;
            if (!mArmed) mAlways = false;
            run(this::rebuild, "sh " + MOD + "/control.sh " + (mArmed ? "arm" : "disarm"),
                    mArmed ? "true" : "sh " + MOD + "/control.sh always-off");
        };
        SwitchWidgetAdapter.SwitchItem always = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.boot_always_title), getString(R.string.boot_always_summary), mAlways, null);
        always.onChanged = () -> {
            mAlways = always.checked;
            if (mAlways) mArmed = true;
            run(this::rebuild, "sh " + MOD + "/control.sh " + (mAlways ? "always-on" : "always-off"));
        };
        chain.add(new SwitchWidgetAdapter(List.of(on, always)));

        List<Object> rows = new ArrayList<>();
        rows.add(new ListWidgetAdapter.ListItem(getString(R.string.boot_show),
                mHasLog ? getString(R.string.boot_show_summary) : getString(R.string.boot_no_log), this::showReport));
        ListWidgetAdapter.ListItem del = new ListWidgetAdapter.ListItem(
                getString(R.string.boot_delete_title), getString(R.string.delete_summary), this::deleteLogs);
        del.actionLabel = getString(R.string.delete_button);
        del.onAction = this::deleteLogs;
        rows.add(del);
        GroupUtils.addGroup(chain, rows);
        chain.add(new InfoCardAdapter(getString(R.string.boot_note)));
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
    }

    /** Toglie solo i log di avvio (bootcap_*) dalla cartella dei log, con conferma. */
    private void deleteLogs() {
        it.tugaia56.obsidian.utils.LogFolder.confirmDelete(requireActivity(), "bootcap", "/data/local/tmp/bootcap.log", this::load);
    }

    private void showReport() {
        new Thread(() -> {
            List<String> out = Shell.cmd("sh " + MOD + "/control.sh report").exec().getOut();
            final String text = format(out);
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.boot_show)
                        .setMessage(text)
                        .setPositiveButton(android.R.string.ok, null)
                        .show());
            });
        }).start();
    }

    private String format(List<String> out) {
        Map<String, String> kv = new HashMap<>();
        List<String> cpu = new ArrayList<>(), mem = new ArrayList<>();
        for (String l : out) {
            int i = l.indexOf('=');
            if (i < 0) continue;
            String k = l.substring(0, i), v = l.substring(i + 1);
            if (k.equals("cpu")) cpu.add(v); else if (k.equals("mem")) mem.add(v); else kv.put(k, v);
        }
        int n = 0;
        try { n = Integer.parseInt(kv.getOrDefault("samples", "0")); } catch (Exception ignored) {}
        if (n < 2) return getString(R.string.boot_rep_none);
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.boot_rep_load, kv.getOrDefault("max_load", "?"), kv.getOrDefault("max_load_at", "?")));
        sb.append("\n").append(getString(R.string.boot_rep_mem, kv.getOrDefault("min_mem_mb", "?"), kv.getOrDefault("max_swap_mb", "?")));
        sb.append("\n").append(getString(R.string.boot_rep_psi, kv.getOrDefault("max_psi_cpu", "?"), kv.getOrDefault("max_psi_mem", "?")));
        if (!cpu.isEmpty()) {
            sb.append("\n\n").append(getString(R.string.boot_rep_cpu));
            for (String t : cpu) sb.append("\n• ").append(line(t, "%"));
        }
        if (!mem.isEmpty()) {
            sb.append("\n\n").append(getString(R.string.boot_rep_ram));
            for (String t : mem) sb.append("\n• ").append(line(t, " MB"));
        }
        return sb.toString();
    }

    private static String line(String t, String unit) {
        int c = t.lastIndexOf(':');
        return c > 0 ? t.substring(0, c) + "  " + t.substring(c + 1) + unit : t;
    }
}
