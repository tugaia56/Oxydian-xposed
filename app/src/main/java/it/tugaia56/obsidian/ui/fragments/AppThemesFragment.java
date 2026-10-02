package it.tugaia56.obsidian.ui.fragments;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.topjohnwu.superuser.Shell;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;
import it.tugaia56.obsidian.utils.overlay.compiler.GenericAppThemeCompiler;

/**
 * Tema per le app Google e utente (al posto di Substratum): elenco delle app installate che
 * hanno un tema incluso (assets/CompileOnDemand/&lt;pacchetto&gt;/APP), un interruttore per
 * ognuna e un solo Applica che compila/abilita le selezionate e disabilita le altre.
 */
public class AppThemesFragment extends Fragment {

    private static final String KEY_PREFIX = "app_theme_enabled_";
    private static final String BUILT_PREFIX = "app_theme_built_";

    private static class AppEntry {
        final String pkg;
        final String label;
        AppEntry(String pkg, String label) { this.pkg = pkg; this.label = label; }
    }

    private RecyclerView mRv;
    private List<AppEntry> mApps = new ArrayList<>();
    private boolean mBusy = false;

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
        mApps = loadInstalledApps();
        rebuild();
    }

    /** Pacchetti con un tema incluso negli assets e installati su questo telefono. */
    private List<AppEntry> loadInstalledApps() {
        List<AppEntry> out = new ArrayList<>();
        PackageManager pm = requireContext().getPackageManager();
        try {
            String[] dirs = requireContext().getAssets().list("CompileOnDemand");
            if (dirs == null) return out;
            for (String pkg : dirs) {
                String[] sub = requireContext().getAssets().list("CompileOnDemand/" + pkg);
                if (sub == null || !Arrays.asList(sub).contains("APP")) continue;
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                    out.add(new AppEntry(pkg, String.valueOf(pm.getApplicationLabel(ai))));
                } catch (PackageManager.NameNotFoundException ignored) {
                    // app non installata: niente riga
                }
            }
        } catch (Exception ignored) {}
        out.sort(Comparator.comparing(e -> e.label.toLowerCase(Locale.ROOT)));
        return out;
    }

    private void rebuild() {
        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();

        chain.add(new NoticeAdapter(getString(R.string.app_themes_notice)));

        GroupUtils.addGroup(chain, List.of(
                new ListWidgetAdapter.ListItem(getString(R.string.app_themes_apply),
                        mBusy ? getString(R.string.app_themes_working)
                              : getString(R.string.app_themes_apply_summary),
                        this::onApplyClicked),
                new ListWidgetAdapter.ListItem(getString(R.string.app_themes_enable_all),
                        getString(R.string.app_themes_enable_all_summary),
                        this::onEnableAllClicked),
                new ListWidgetAdapter.ListItem(getString(R.string.app_themes_disable_all),
                        getString(R.string.app_themes_disable_all_summary),
                        this::onDisableAllClicked)));

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.app_themes_section))));
        List<Object> rows = new ArrayList<>();
        for (AppEntry e : mApps) {
            SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                    e.label, e.pkg, ObsidianPrefs.getBoolean(KEY_PREFIX + e.pkg, false), null);
            item.onChanged = () -> ObsidianPrefs.putBoolean(KEY_PREFIX + e.pkg, item.checked);
            rows.add(item);
        }
        if (rows.isEmpty()) {
            rows.add(new ListWidgetAdapter.ListItem(getString(R.string.app_themes_none), "", null));
        }
        GroupUtils.addGroup(chain, rows);

        android.os.Parcelable state = mRv.getLayoutManager() != null
                ? mRv.getLayoutManager().onSaveInstanceState() : null;
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
        if (state != null && mRv.getLayoutManager() != null) {
            mRv.getLayoutManager().onRestoreInstanceState(state);
        }
    }

    /** Overlay delle app temate attualmente abilitati — una sola chiamata di shell invece di
     *  interrogare/disabilitare ogni app una per una (erano ~90 chiamate inutili). */
    private static java.util.Set<String> enabledAppOverlays() {
        java.util.Set<String> out = new java.util.HashSet<>();
        try {
            for (String line : Shell.cmd("cmd overlay list | grep GApp_").exec().getOut()) {
                line = line.trim();
                if (line.startsWith("[x]")) out.add(line.substring(3).trim());
            }
        } catch (Throwable ignored) {}
        return out;
    }

    /** Cambia se si reinstalla Oxydian o si cambia accento: allora gli overlay vanno rifatti. */
    private String buildSignature() {
        long updated = 0;
        try {
            updated = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).lastUpdateTime;
        } catch (Exception ignored) {}
        return updated + ":" + String.format("%08X", ObsidianTheme.accentColor());
    }

    private void onApplyClicked() {
        if (mBusy) return;
        mBusy = true;
        rebuild();
        List<AppEntry> apps = new ArrayList<>(mApps);
        new Thread(() -> {
            int failed = 0, enabled = 0;
            java.util.Set<String> before = enabledAppOverlays();
            String signature = buildSignature();
            List<String> refresh = new ArrayList<>();   // ricompilate e già attive: da riaccendere
            List<String> toDisable = new ArrayList<>(); // spente ma ancora attive nel sistema
            List<String> toRemove = new ArrayList<>();  // spente: via anche l'APK dal telefono
            boolean batchOpen = false;
            try {
                for (AppEntry e : apps) {
                    boolean wanted = ObsidianPrefs.getBoolean(KEY_PREFIX + e.pkg, false);
                    boolean active = before.contains(GenericAppThemeCompiler.overlayPackage(e.pkg));
                    if (!wanted) {
                        if (active) toDisable.add(e.pkg);
                        toRemove.add(e.pkg);
                        ObsidianPrefs.remove(BUILT_PREFIX + e.pkg);
                        continue;
                    }
                    enabled++;
                    // già compilata e attiva con lo stesso accento/versione di Oxydian: si salta
                    if (active && signature.equals(ObsidianPrefs.getString(BUILT_PREFIX + e.pkg, ""))) continue;
                    if (!batchOpen) { GenericAppThemeCompiler.beginBatch(); batchOpen = true; }
                    try {
                        if (GenericAppThemeCompiler.buildInBatch(e.pkg)) {
                            failed++;
                        } else {
                            ObsidianPrefs.putString(BUILT_PREFIX + e.pkg, signature);
                            if (active) refresh.add(e.pkg);
                        }
                    } catch (Throwable t) {
                        failed++;
                    }
                }
            } finally {
                if (batchOpen) GenericAppThemeCompiler.endBatch(refresh);
            }
            GenericAppThemeCompiler.disable(toDisable);
            GenericAppThemeCompiler.removeApks(toRemove);
            java.util.Set<String> after = enabledAppOverlays();
            int notYetActive = 0;
            for (AppEntry e : apps) {
                if (ObsidianPrefs.getBoolean(KEY_PREFIX + e.pkg, false)
                        && !after.contains(GenericAppThemeCompiler.overlayPackage(e.pkg))) notYetActive++;
            }
            final int f = failed, n = notYetActive, en = enabled;
            new Handler(Looper.getMainLooper()).post(() -> {
                mBusy = false;
                if (!isAdded()) return;
                rebuild();
                String msg;
                if (f > 0) msg = getString(R.string.app_themes_result_failed, f);
                else if (n > 0) msg = getString(R.string.app_themes_result_reboot);
                else msg = getString(en > 0 ? R.string.toast_applied : R.string.app_themes_result_none);
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    /** Seleziona tutte le app (senza compilare): poi si preme Applica. */
    private void onEnableAllClicked() {
        if (mBusy) return;
        for (AppEntry e : mApps) ObsidianPrefs.putBoolean(KEY_PREFIX + e.pkg, true);
        rebuild();
    }

    private void onDisableAllClicked() {
        if (mBusy) return;
        mBusy = true;
        rebuild();
        List<AppEntry> apps = new ArrayList<>(mApps);
        new Thread(() -> {
            java.util.Set<String> before = enabledAppOverlays();
            List<String> toDisable = new ArrayList<>();
            for (AppEntry e : apps) {
                if (before.contains(GenericAppThemeCompiler.overlayPackage(e.pkg))) toDisable.add(e.pkg);
                ObsidianPrefs.putBoolean(KEY_PREFIX + e.pkg, false);
            }
            GenericAppThemeCompiler.disable(toDisable);
            List<String> all = new ArrayList<>();
            for (AppEntry e : apps) { all.add(e.pkg); ObsidianPrefs.remove(BUILT_PREFIX + e.pkg); }
            GenericAppThemeCompiler.removeApks(all);
            new Handler(Looper.getMainLooper()).post(() -> {
                mBusy = false;
                if (!isAdded()) return;
                rebuild();
                Toast.makeText(requireContext(), R.string.toast_applied, Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    // ── Nota in testa ─────────────────────────────────────────────────────────

    private static class NoticeAdapter extends RecyclerView.Adapter<NoticeAdapter.VH> {
        private final String text;
        NoticeAdapter(String text) { this.text = text; }

        static class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setTextColor(0x99FFFFFF);
            tv.setTextSize(13);
            float d = parent.getResources().getDisplayMetrics().density;
            tv.setPadding((int) (16 * d), (int) (4 * d), (int) (16 * d), (int) (12 * d));
            tv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new VH(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            TextView tv = (TextView) h.itemView;
            tv.setText(text);
            tv.setTextColor(ObsidianTheme.textColor(0x99));
        }

        @Override public int getItemCount() { return 1; }
    }
}
