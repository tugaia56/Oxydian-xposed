package it.tugaia56.obsidian.ui.fragments;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.utils.DstFabricatedUtil;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/**
 * Settings screen — backup / restore / clear all preferences.
 *
 * Backup:   writes YYYY-MM-DD_HH-mm_oxydian_backup.json to the chosen folder
 *           (defaults to external app storage; user can pick any folder via SAF).
 * Restore:  opens a file picker for any .json file and merges preferences.
 * Clear:    removes all saved preferences after confirmation.
 */
public class SettingsFragment extends Fragment {

    private static final String PREF_BACKUP_FOLDER_URI = "backup_folder_uri";

    private Context mAppCtx;

    // ── SAF launchers (must be registered before STARTED) ─────────────────────
    private ActivityResultLauncher<Uri>      mFolderPickerLauncher;
    private ActivityResultLauncher<String[]> mFilePickerLauncher;
    /** Cartella dei log: scelta con il selettore di Android (solo memoria interna). */
    private final ActivityResultLauncher<Uri> mLogFolderPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri == null) return;
                String path = it.tugaia56.obsidian.utils.LogFolder.pathFromTree(uri);
                if (path == null) {
                    android.widget.Toast.makeText(requireContext(), R.string.log_folder_internal_only, android.widget.Toast.LENGTH_LONG).show();
                    return;
                }
                it.tugaia56.obsidian.utils.LogFolder.setDir(path);
                if (isAdded() && getView() instanceof RecyclerView) onViewCreated(getView(), null);
            });

    // ── Adapters (need ref to update folder summary) ─────────────────────────
    private ListWidgetAdapter.ListItem mFolderItem;
    private ListWidgetAdapter          mFolderAdapter;

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mAppCtx = context.getApplicationContext();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Folder picker — stores tree URI and refreshes the row
        mFolderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                uri -> {
                    if (uri == null) return;
                    // Keep persistent access across reboots
                    requireActivity().getContentResolver().takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    ObsidianPrefs.putString(PREF_BACKUP_FOLDER_URI, uri.toString());
                    refreshFolderRow();
                });

        // File picker — opens a .json backup for restore
        mFilePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) return;
                    Context ctx = mAppCtx;
                    new Thread(() -> doRestoreFromUri(uri, ctx)).start();
                });
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
        RecyclerView rv = (RecyclerView) view;

        // ── Folder selection ──────────────────────────────────────────────────
        mFolderItem = new ListWidgetAdapter.ListItem(
                getString(R.string.settings_folder),
                getFolderSummary(),
                () -> mFolderPickerLauncher.launch(null));

        // ── Data e ora nel nome del file ──────────────────────────────────────
        ListWidgetAdapter.ListItem dateItem = new ListWidgetAdapter.ListItem(
                getString(R.string.settings_backup_date),
                it.tugaia56.obsidian.utils.BackupName.example(it.tugaia56.obsidian.utils.BackupName.date()),
                () -> chooseNameFormat(true, rv));
        ListWidgetAdapter.ListItem timeItem = new ListWidgetAdapter.ListItem(
                getString(R.string.settings_backup_time),
                it.tugaia56.obsidian.utils.BackupName.time().isEmpty() ? getString(R.string.settings_backup_time_none)
                        : it.tugaia56.obsidian.utils.BackupName.example(it.tugaia56.obsidian.utils.BackupName.time()),
                () -> chooseNameFormat(false, rv));
        dateItem.groupPos = it.tugaia56.obsidian.utils.ObsidianTheme.GroupPos.TOP;
        timeItem.groupPos = it.tugaia56.obsidian.utils.ObsidianTheme.GroupPos.BOTTOM;

        ListWidgetAdapter.ListItem backupItem = new ListWidgetAdapter.ListItem(
                getString(R.string.settings_backup),
                getString(R.string.settings_backup_summary),
                () -> {
                    Context ctx = mAppCtx;
                    new Thread(() -> doBackup(ctx)).start();
                });

        ListWidgetAdapter.ListItem restoreItem = new ListWidgetAdapter.ListItem(
                getString(R.string.settings_restore),
                getString(R.string.settings_restore_summary),
                () -> mFilePickerLauncher.launch(new String[]{"application/json",
                                                                "application/octet-stream",
                                                                "*/*"}));

        ListWidgetAdapter.ListItem clearItem = new ListWidgetAdapter.ListItem(
                getString(R.string.settings_clear),
                getString(R.string.settings_clear_summary),
                this::confirmClear);

        // Riga singola per mFolderItem (invece di GroupUtils.addGroup su tutte e 4) per
        // conservare il riferimento diretto a mFolderAdapter — serve più sotto per
        // aggiornare il riepilogo cartella con notifyDataSetChanged() dopo la scelta.
        mFolderItem.groupPos = it.tugaia56.obsidian.utils.ObsidianTheme.GroupPos.TOP;
        backupItem.groupPos  = it.tugaia56.obsidian.utils.ObsidianTheme.GroupPos.MIDDLE;
        restoreItem.groupPos = it.tugaia56.obsidian.utils.ObsidianTheme.GroupPos.MIDDLE;
        clearItem.groupPos   = it.tugaia56.obsidian.utils.ObsidianTheme.GroupPos.BOTTOM;
        mFolderAdapter = new ListWidgetAdapter(List.of(mFolderItem));

        // ── Log: salva i messaggi di Oxydian e scegli la cartella ─────────────
        ListWidgetAdapter.ListItem saveLogsItem = new ListWidgetAdapter.ListItem(
                getString(R.string.save_logs_title), getString(R.string.save_logs_summary), this::saveXposedLog);
        saveLogsItem.actionLabel = getString(R.string.save_logs_button);
        saveLogsItem.onAction = this::saveXposedLog;
        saveLogsItem.groupPos = it.tugaia56.obsidian.utils.ObsidianTheme.GroupPos.TOP;
        ListWidgetAdapter.ListItem logFolderItem = new ListWidgetAdapter.ListItem(
                getString(R.string.log_folder_title), it.tugaia56.obsidian.utils.LogFolder.dir(), this::showLogFolderDialog);
        logFolderItem.groupPos = it.tugaia56.obsidian.utils.ObsidianTheme.GroupPos.BOTTOM;

        rv.setAdapter(new ConcatAdapter(
                new SectionTitleAdapter(List.of(getString(R.string.settings_section))),
                mFolderAdapter,
                new ListWidgetAdapter(List.of(dateItem)),
                new ListWidgetAdapter(List.of(timeItem)),
                new ListWidgetAdapter(List.of(backupItem)),
                new ListWidgetAdapter(List.of(restoreItem)),
                new ListWidgetAdapter(List.of(clearItem)),
                new SectionTitleAdapter(List.of(getString(R.string.settings_logs_section))),
                new ListWidgetAdapter(List.of(saveLogsItem)),
                new ListWidgetAdapter(List.of(logFolderItem))));
    }

    // ── Log ───────────────────────────────────────────────────────────────────

    private void showLogFolderDialog() {
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.log_folder_title)
                .setMessage(getString(R.string.log_folder_message, it.tugaia56.obsidian.utils.LogFolder.dir()))
                .setPositiveButton(R.string.log_folder_pick, (d, w) -> mLogFolderPicker.launch(null))
                .setNeutralButton(R.string.log_folder_default, (d, w) -> {
                    it.tugaia56.obsidian.utils.LogFolder.setDir("");
                    if (getView() instanceof RecyclerView) onViewCreated(getView(), null);
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    /** Salva nella cartella dei log i messaggi di Oxydian presi dal log di LSPosed, con un riepilogo del telefono. */
    private void saveXposedLog() {
        final Context ctx = requireContext().getApplicationContext();
        new Thread(() -> {
            String header = "Oxydian - log dei mod\n"
                    + it.tugaia56.obsidian.utils.DeviceInfo.diagnosticSummary()
                    + it.tugaia56.obsidian.utils.DeviceInfo.rootDiagnostics();
            String path = it.tugaia56.obsidian.utils.LogFolder.saveXposedLog(ctx, header);
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                        .setMessage(path != null ? getString(R.string.mod_health_export_ok_fmt, path)
                                : getString(R.string.mod_health_export_fail))
                        .setPositiveButton(android.R.string.ok, null)
                        .show());
            });
        }).start();
    }

    // ── Formato data / ora del nome file ──────────────────────────────────────

    private void chooseNameFormat(boolean date, RecyclerView rv) {
        String[] values = date ? it.tugaia56.obsidian.utils.BackupName.DATES : it.tugaia56.obsidian.utils.BackupName.TIMES;
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++)
            names[i] = it.tugaia56.obsidian.utils.BackupName.label(requireContext(), values[i]);
        String cur = date ? it.tugaia56.obsidian.utils.BackupName.date() : it.tugaia56.obsidian.utils.BackupName.time();
        int idx = java.util.Arrays.asList(values).indexOf(cur);
        final int[] sel = {Math.max(0, idx)};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(date ? R.string.settings_backup_date : R.string.settings_backup_time)
                .setSingleChoiceItems(names, sel[0], (d, w) -> sel[0] = w)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putString(date ? it.tugaia56.obsidian.utils.BackupName.PREF_DATE
                            : it.tugaia56.obsidian.utils.BackupName.PREF_TIME, values[sel[0]]);
                    onViewCreated(rv, null);
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    // ── Backup ────────────────────────────────────────────────────────────────

    private void doBackup(Context ctx) {
        try {
            Map<String, ?> all = ObsidianPrefs.getPrefs().getAll();
            JSONObject prefs = new JSONObject();
            for (Map.Entry<String, ?> e : all.entrySet()) {
                Object val = e.getValue();
                JSONObject entry = new JSONObject();
                if (val instanceof Boolean) {
                    entry.put("t", "b"); entry.put("v", val);
                } else if (val instanceof Integer) {
                    entry.put("t", "i"); entry.put("v", val);
                } else if (val instanceof Float) {
                    entry.put("t", "f"); entry.put("v", ((Float) val).doubleValue());
                } else if (val instanceof String) {
                    entry.put("t", "s"); entry.put("v", val);
                } else {
                    continue;
                }
                prefs.put(e.getKey(), entry);
            }
            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("prefs", prefs);
            String json = root.toString(2);

            // Timestamped filename
            String ts       = it.tugaia56.obsidian.utils.BackupName.stamp();
            String filename = ts + "_oxydian_backup.json";

            String folderUriStr = ObsidianPrefs.getString(PREF_BACKUP_FOLDER_URI, null);
            if (folderUriStr != null) {
                // Write via SAF to user-chosen folder
                Uri treeUri = Uri.parse(folderUriStr);
                DocumentFile dir = DocumentFile.fromTreeUri(ctx, treeUri);
                if (dir == null || !dir.canWrite()) throw new Exception("Folder not writable");
                DocumentFile file = dir.createFile("application/json", filename);
                if (file == null) throw new Exception("Cannot create file in folder");
                try (OutputStream os = ctx.getContentResolver().openOutputStream(file.getUri())) {
                    if (os == null) throw new Exception("openOutputStream returned null");
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                toast(getString(R.string.settings_backup_ok) + "\n" + filename);
            } else {
                // Default: external app files dir (no permission needed)
                File dir  = ctx.getExternalFilesDir(null);
                if (dir == null) throw new Exception("External storage unavailable");
                File file = new File(dir, filename);
                try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
                    bw.write(json);
                }
                toast(getString(R.string.settings_backup_ok) + "\n" + file.getAbsolutePath());
            }
        } catch (Exception ex) {
            toast(getString(R.string.settings_error) + "\n" + ex.getMessage());
        }
    }

    // ── Restore ───────────────────────────────────────────────────────────────

    private void doRestoreFromUri(Uri uri, Context ctx) {
        try {
            StringBuilder sb = new StringBuilder();
            try (InputStream is = ctx.getContentResolver().openInputStream(uri);
                 BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            JSONObject root  = new JSONObject(sb.toString());
            JSONObject prefs = root.getJSONObject("prefs");
            Iterator<String> keys = prefs.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject e = prefs.getJSONObject(key);
                switch (e.getString("t")) {
                    case "b": ObsidianPrefs.putBoolean(key, e.getBoolean("v")); break;
                    case "i": ObsidianPrefs.putInt(key, e.getInt("v"));         break;
                    case "f": ObsidianPrefs.putFloat(key, (float) e.getDouble("v")); break;
                    case "s": ObsidianPrefs.putString(key, e.getString("v"));   break;
                }
            }
            toast(getString(R.string.settings_restore_ok));
            // Re-applica i FabricatedOverlay DST abilitati; il riavvio SystemUI
            // resta manuale (pulsante in alto), come per ogni altra opzione.
            DstFabricatedUtil.reapplyAll(() ->
                    Toast.makeText(requireContext(), R.string.obs_restart_ui_hint, Toast.LENGTH_SHORT).show());
        } catch (Exception ex) {
            toast(getString(R.string.settings_error) + "\n" + ex.getMessage());
        }
    }

    // ── Clear ─────────────────────────────────────────────────────────────────

    private void confirmClear() {
        AlertDialog d = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_clear)
                .setMessage(R.string.settings_clear_confirm)
                .setPositiveButton(R.string.settings_clear, (dlg, w) -> {
                    ObsidianPrefs.clear();
                    toast(getString(R.string.settings_clear_ok));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        fixButtonCaps(d);
        ObsidianTheme.themeDialog(d);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getFolderSummary() {
        String uriStr = ObsidianPrefs.getString(PREF_BACKUP_FOLDER_URI, null);
        if (uriStr == null) return getString(R.string.settings_folder_default);
        Uri uri = Uri.parse(uriStr);
        DocumentFile dir = DocumentFile.fromTreeUri(requireContext(), uri);
        String name = (dir != null) ? dir.getName() : null;
        if (name == null || name.isEmpty()) {
            // Fallback: show last path segment
            String seg = uri.getLastPathSegment();
            name = (seg != null) ? seg : uriStr;
        }
        return String.format(getString(R.string.settings_folder_summary_fmt), name);
    }

    private void refreshFolderRow() {
        if (mFolderItem == null || mFolderAdapter == null || !isAdded()) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            mFolderItem.valueSummary = getFolderSummary();
            mFolderAdapter.notifyDataSetChanged();
        });
    }

    private void toast(String msg) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(mAppCtx, msg, Toast.LENGTH_LONG).show());
    }

    private static void fixButtonCaps(AlertDialog d) {
        for (int w : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE}) {
            Button b = d.getButton(w);
            if (b == null) continue;
            b.setAllCaps(false);
            b.setSingleLine(false);
            b.setMaxLines(2);
            b.setEllipsize(null);
        }
    }
}
