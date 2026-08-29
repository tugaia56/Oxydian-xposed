package it.tugaia56.obsidian.ui.fragments;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import java.util.ArrayList;
import java.util.List;

import it.tugaia56.obsidian.BuildConfig;
import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;
import it.tugaia56.obsidian.utils.UpdateChecker;
import it.tugaia56.obsidian.utils.UpdateScheduler;

/**
 * "Aggiornamento" — controllo reale via GitHub Releases API (repo tugaia56/Obsidian-xposed,
 * lo stesso che [[project_ci_cd_release_automation]] pubblica ad ogni tag). Il pulsante
 * "Controlla aggiornamenti" confronta BuildConfig.VERSION_NAME col tag_name della release più
 * recente, mostra il changelog (release body) e scarica/installa l'APK allegato tramite
 * DownloadManager (logica condivisa con UpdateCheckWorker via UpdateChecker).
 *
 * "Aggiornamento automatico"/"Solo Wi-Fi" (2026-08-29, reali): avviano/fermano un controllo
 * periodico ogni 12h via WorkManager (UpdateScheduler), che notifica se trova una versione
 * più recente — nessun download automatico, solo l'avviso (l'installazione resta manuale,
 * come da richiesta implicita di non installare mai nulla senza conferma).
 */
public class SettingsUpdateFragment extends Fragment {

    private static final String KEY_AUTO_CHECK = "update_auto_check";
    private static final String KEY_WIFI_ONLY  = "update_wifi_only";

    private RecyclerView mRv;
    private long mDownloadId = -1;
    private BroadcastReceiver mDownloadReceiver;
    private ActivityResultLauncher<String> mRequestNotifPermission;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRequestNotifPermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> { /* niente da fare:
                    con permesso negato il controllo periodico gira comunque, solo senza notifica */ });
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

        ListWidgetAdapter checkAdapter = new ListWidgetAdapter(List.of(
                new ListWidgetAdapter.ListItem(
                        getString(R.string.settings_check_updates),
                        BuildConfig.VERSION_NAME,
                        this::checkForUpdates)));

        SwitchWidgetAdapter.SwitchItem autoUpdateItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.settings_auto_update),
                getString(R.string.settings_auto_update_summary),
                ObsidianPrefs.getBoolean(KEY_AUTO_CHECK, false), null);
        autoUpdateItem.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_AUTO_CHECK, autoUpdateItem.checked);
            UpdateScheduler.reschedule(requireContext());
            if (autoUpdateItem.checked) maybeRequestNotifPermission();
        };

        SwitchWidgetAdapter.SwitchItem wifiOnlyItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.settings_update_wifi_only),
                getString(R.string.settings_update_wifi_only_summary),
                ObsidianPrefs.getBoolean(KEY_WIFI_ONLY, false), null);
        wifiOnlyItem.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_WIFI_ONLY, wifiOnlyItem.checked);
            UpdateScheduler.reschedule(requireContext());
        };

        List<RecyclerView.Adapter<?>> switchChain = new ArrayList<>();
        GroupUtils.addGroup(switchChain, List.of(autoUpdateItem, wifiOnlyItem));

        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();
        chain.add(checkAdapter);
        chain.addAll(switchChain);
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
    }

    private void maybeRequestNotifPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            mRequestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    // ── Controlla aggiornamenti (GitHub Releases API) ───────────────────────
    private void checkForUpdates() {
        Toast.makeText(requireContext(), R.string.update_checking, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                UpdateChecker.Result result = UpdateChecker.check();
                if (getActivity() == null) return;
                requireActivity().runOnUiThread(() -> {
                    if (!result.newer) {
                        Toast.makeText(requireContext(), R.string.update_up_to_date, Toast.LENGTH_SHORT).show();
                    } else if (result.downloadUrl == null) {
                        Toast.makeText(requireContext(), R.string.update_no_apk_asset, Toast.LENGTH_SHORT).show();
                    } else {
                        showUpdateDialog(result.version, result.changelog, result.downloadUrl);
                    }
                });
            } catch (Throwable t) {
                if (getActivity() == null) return;
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), R.string.update_check_failed, Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showUpdateDialog(String version, String changelog, String downloadUrl) {
        String message = getString(R.string.update_available_body, version);
        if (changelog != null && !changelog.isBlank()) message += "\n\n" + changelog;
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.update_available_title)
                .setMessage(message)
                .setPositiveButton(R.string.update_word, (d, w) -> startDownload(downloadUrl, version))
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    // ── Download + installazione ────────────────────────────────────────────
    private void startDownload(String url, String version) {
        DownloadManager dm = (DownloadManager) requireContext().getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return;

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                .setTitle(getString(R.string.settings_check_updates))
                .setMimeType("application/vnd.android.package-archive")
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Obsidian-" + version + ".apk")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        mDownloadId = dm.enqueue(request);
        Toast.makeText(requireContext(), R.string.update_download_started, Toast.LENGTH_SHORT).show();

        mDownloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (completedId != mDownloadId) return;
                try { requireContext().unregisterReceiver(this); } catch (Throwable ignored) {}
                mDownloadReceiver = null;
                promptInstall(dm, completedId);
            }
        };
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(mDownloadReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            requireContext().registerReceiver(mDownloadReceiver, filter);
        }
    }

    private void promptInstall(DownloadManager dm, long downloadId) {
        try {
            Uri uri = dm.getUriForDownloadedFile(downloadId);
            if (uri == null) {
                Toast.makeText(requireContext(), R.string.update_download_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            Intent install = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(install);
        } catch (Throwable t) {
            Toast.makeText(requireContext(), R.string.update_download_failed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mDownloadReceiver != null) {
            try { requireContext().unregisterReceiver(mDownloadReceiver); } catch (Throwable ignored) {}
            mDownloadReceiver = null;
        }
    }
}
