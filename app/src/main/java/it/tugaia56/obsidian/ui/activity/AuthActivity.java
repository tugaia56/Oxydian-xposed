package it.tugaia56.obsidian.ui.activity;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.topjohnwu.superuser.Shell;

import java.util.concurrent.Executor;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.fragments.AdvancedRebootTilesFragment;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/**
 * Transparent activity: shows the "Advanced Reboot" chooser, optionally behind biometric auth.
 * Launched by the SystemUI power-menu hook when the extra reboot button is tapped.
 */
public class AuthActivity extends FragmentActivity {

    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private int shown = 0;
    /** Set only when launched to gate the STOCK reboot/shutdown slider (not the advanced-reboot
     *  chooser) — "reboot" / "reboot_safe" / "shutdown". Null means the advanced-reboot flow. */
    private String pendingStockAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // "Riavvio Riquadri"/"Riavvio Lista" QS tiles — always open the matching style directly,
        // regardless of the Lista/Riquadri choice in Menù Accensione (a quick one-tap picker is
        // the whole point of a QS tile shortcut) and skip biometric auth (already behind the
        // lockscreen/QS panel).
        if (getIntent().getBooleanExtra("qsTileGrid", false)) {
            showAdvancedRebootGrid();
            return;
        }
        if (getIntent().getBooleanExtra("qsTileList", false)) {
            showAdvancedRebootList();
            return;
        }
        pendingStockAction = getIntent().getStringExtra("stockAction");
        if (pendingStockAction != null) {
            showAuth(); // MiscMods only launches this when auth is actually required
            return;
        }
        boolean shouldAuth = getIntent().getBooleanExtra("shouldAuth", false);
        if (shouldAuth) showAuth();
        else showAdvancedReboot();
    }

    private void showAuth() {
        executor = ContextCompat.getMainExecutor(this);
        biometricPrompt = new BiometricPrompt(AuthActivity.this,
                executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                if ((errorCode == BiometricPrompt.ERROR_CANCELED
                        || errorCode == BiometricPrompt.ERROR_USER_CANCELED) && shown < 2) {
                    biometricPrompt.cancelAuthentication();
                    runOnUiThread(() -> {
                        try {
                            biometricPrompt.authenticate(promptInfo);
                            shown++;
                        } catch (Throwable ignored) {}
                    });
                    return;
                }
                finishAndRemoveTask();
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                if (pendingStockAction != null) runStockAction();
                else showAdvancedReboot();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(getApplicationContext(), R.string.advanced_reboot_auth_failed, Toast.LENGTH_SHORT).show();
                finishAndRemoveTask();
            }
        });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.advanced_reboot_auth))
                .setSubtitle(getString(R.string.advanced_reboot_auth_summary))
                .setAllowedAuthenticators(BIOMETRIC_STRONG | BIOMETRIC_WEAK | DEVICE_CREDENTIAL)
                .setConfirmationRequired(true)
                .build();

        new Handler(Looper.getMainLooper()).postDelayed(() -> biometricPrompt.authenticate(promptInfo), 300);
        shown++;
    }

    /** Re-runs the SAME reboot/shutdown that MiscMods blocked (GlobalActionsComponent.reboot()/
     *  .shutdown() itself is unreachable from here — different process — so this uses root shell
     *  instead, same technique already used for the chooser's own entries below). */
    private void runStockAction() {
        String cmd = switch (pendingStockAction) {
            case "shutdown"    -> "reboot -p";
            case "reboot_safe" -> "reboot safemode";
            default             -> "reboot";
        };
        try { Shell.cmd(cmd).exec(); } catch (Throwable ignored) {}
        finishAndRemoveTask();
    }

    /** Same label→command mapping feeds both the plain list and the tile grid below — index
     *  order must stay identical between {@link #rebootLabels()} and {@link #rebootCommand}. */
    private CharSequence[] rebootLabels() {
        return new CharSequence[]{
                getString(R.string.advanced_reboot_recovery),
                getString(R.string.advanced_reboot_bootloader),
                getString(R.string.advanced_reboot_safe_mode),
                getString(R.string.advanced_reboot_fast_reboot),
                getString(R.string.advanced_reboot_systemui),
                getString(R.string.advanced_reboot_lock_screen),
                getString(R.string.advanced_reboot_screenshot),
                getString(R.string.advanced_reboot_fastbootd),
                getString(R.string.advanced_reboot_restart),
                getString(R.string.advanced_reboot_shutdown)
        };
    }

    private String rebootCommand(int which) {
        return switch (which) {
            case 0 -> "reboot recovery";
            case 1 -> "reboot bootloader";
            case 2 -> "reboot safemode";
            case 3 -> "killall zygote; killall zygote64";
            case 4 -> "killall " + SYSTEM_UI;
            // KEYCODE_POWER: locks the screen immediately, same as a physical power
            // press — not the OOS "true" lockdown (which also disables biometric
            // bypass until credential re-entry, needs a hidden LockPatternUtils
            // call not reachable from plain root shell).
            case 5 -> "input keyevent 26";
            // Screenshot nativo OPLUS via SystemUI (broadcast da root, risponde result=42);
            // se SystemUI non risponde (hook non attivo) ripiega su screencap, che scrive il
            // file direttamente ma senza pannello modifica/condividi. KEYCODE_SYSRQ sintetico
            // è ignorato da OOS (verificato su device).
            case 6 -> "out=$(am broadcast -a it.tugaia56.oxydian.ACTION_TAKE_SCREENSHOT -p com.android.systemui 2>&1); "
                    + "echo \"$out\" | grep -q 'result=42' || { mkdir -p /sdcard/Pictures/Screenshots && "
                    + "screencap -p /sdcard/Pictures/Screenshots/Screenshot_$(date +%Y%m%d_%H%M%S).png; }";
            case 7 -> "reboot fastboot";
            case 8 -> "reboot";
            case 9 -> "reboot -p";
            default -> "";
        };
    }

    /** Voci che riavviano/spengono (o riavviano SystemUI) — chiedono conferma prima di
     *  partire, così un tocco sbagliato si può annullare. Blocca Schermo (5) e Screenshot (6)
     *  partono subito, non ha senso confermarli. */
    private static boolean needsConfirm(int which) {
        return which != 5 && which != 6;
    }

    private void runRebootChoice(int which) {
        if (!needsConfirm(which)) {
            executeRebootChoice(which);
            return;
        }
        CharSequence label = rebootLabels()[which];
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(this)
                .setTitle(label)
                .setMessage(R.string.advanced_reboot_confirm_message)
                .setPositiveButton(android.R.string.ok, (d, w) -> executeRebootChoice(which))
                // Annulla riporta all'elenco/griglia, per scegliere un'altra voce o chiudere da lì.
                .setNegativeButton(R.string.cancel, (d, w) -> showAdvancedReboot())
                .setOnCancelListener(d -> showAdvancedReboot())
                .show());
    }

    private void executeRebootChoice(int which) {
        try { Shell.cmd(rebootCommand(which)).exec(); } catch (Throwable ignored) {}
        finishAndRemoveTask();
    }

    private void showAdvancedReboot() {
        if (ObsidianPrefs.getBoolean("advanced_reboot_grid_style", false)) {
            showAdvancedRebootGrid();
        } else {
            showAdvancedRebootList();
        }
    }

    private void showAdvancedRebootList() {
        java.util.List<Integer> indices = visibleTileIndices();
        CharSequence[] allLabels = rebootLabels();
        CharSequence[] labels = new CharSequence[indices.size()];
        for (int i = 0; i < indices.size(); i++) labels[i] = allLabels[indices.get(i)];

        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.advanced_reboot_title)
                .setItems(labels, (dialog, which) -> runRebootChoice(indices.get(which)))
                .setOnCancelListener(dialog -> finishAndRemoveTask())
                .show());
    }

    /** Which of the {@link #rebootLabels()} indices show, and in what order — user-configured
     *  from PowerMenuFragment's "Personalizza Riquadri" screen (drag to reorder, switch to
     *  show/hide). Order defaults to the natural 0..N index order; visibility defaults to all
     *  on, so both the grid and the list aren't empty before the user ever opens that screen.
     *  Shared by both the list and the grid — reordering/hiding applies to whichever style is
     *  active. */
    public static final String TILE_PREF_PREFIX = "advanced_reboot_tile_";
    public static final String PREF_TILE_ORDER = "advanced_reboot_tile_order";

    private java.util.List<Integer> tileOrder() {
        int count = rebootLabels().length;
        String raw = ObsidianPrefs.getString(PREF_TILE_ORDER, "");
        java.util.List<Integer> order = new java.util.ArrayList<>();
        if (!raw.isEmpty()) {
            for (String s : raw.split(",")) {
                try {
                    int idx = Integer.parseInt(s.trim());
                    if (idx >= 0 && idx < count && !order.contains(idx)) order.add(idx);
                } catch (NumberFormatException ignored) {}
            }
        }
        // Append any index missing from the stored order (e.g. a new entry added after the
        // user last customized this list) at the end, so nothing silently disappears.
        for (int i = 0; i < count; i++) if (!order.contains(i)) order.add(i);
        return order;
    }

    private java.util.List<Integer> visibleTileIndices() {
        java.util.List<Integer> result = new java.util.ArrayList<>();
        for (int idx : tileOrder()) {
            if (ObsidianPrefs.getBoolean(TILE_PREF_PREFIX + idx + "_on", true)) result.add(idx);
        }
        return result;
    }

    /** Tile background colour — "default" (cardColor(), unchanged look), "accent", or
     *  "custom", set from AdvancedRebootTilesFragment. */
    private int resolveTileColor() {
        String mode = ObsidianPrefs.getString(AdvancedRebootTilesFragment.PREF_TILE_COLOR_MODE, "default");
        if ("accent".equals(mode)) return ObsidianTheme.accentColor();
        if ("custom".equals(mode)) {
            return ObsidianPrefs.getInt(AdvancedRebootTilesFragment.PREF_TILE_CUSTOM_COLOR, ObsidianTheme.DEFAULT_ACCENT);
        }
        return ObsidianTheme.cardColor();
    }

    private int resolveTileBorderColor() {
        boolean useAccent = ObsidianPrefs.getBoolean(AdvancedRebootTilesFragment.PREF_TILE_BORDER_USE_ACCENT, true);
        return useAccent ? ObsidianTheme.accentColor()
                : ObsidianPrefs.getInt(AdvancedRebootTilesFragment.PREF_TILE_BORDER_CUSTOM_COLOR, ObsidianTheme.DEFAULT_ACCENT);
    }

    /** Custom app tiles, added from AdvancedRebootTilesFragment — appended after the built-in
     *  ones, each launches the app directly instead of running a reboot command. */
    private java.util.List<String> customAppPackages() {
        String raw = ObsidianPrefs.getString(AdvancedRebootTilesFragment.PREF_CUSTOM_APPS, "");
        java.util.List<String> result = new java.util.ArrayList<>();
        if (raw.isEmpty()) return result;
        for (String s : raw.split(",")) {
            if (!s.trim().isEmpty()) result.add(s.trim());
        }
        return result;
    }

    private void showAdvancedRebootGrid() {
        CharSequence[] labels = rebootLabels();
        java.util.List<Integer> indices = visibleTileIndices();
        java.util.List<String> customApps = customAppPackages();
        int builtInCount = indices.size();
        int pad = Math.round(8 * getResources().getDisplayMetrics().density);

        RecyclerView grid = new RecyclerView(this);
        grid.setLayoutManager(new GridLayoutManager(this, 2));
        grid.setPadding(pad, pad, pad, pad);
        grid.setClipToPadding(false);
        grid.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog[] dlgRef = new AlertDialog[1];
        grid.setAdapter(new RecyclerView.Adapter<TileVH>() {
            @NonNull @Override
            public TileVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                TextView label = new TextView(parent.getContext());
                label.setTextSize(14);
                label.setGravity(Gravity.CENTER);
                label.setMinHeight(Math.round(72 * getResources().getDisplayMetrics().density));
                label.setPadding(pad, pad, pad, pad);
                label.setTextColor(0xFFFFFFFF);

                GradientDrawable bg = new GradientDrawable();
                bg.setColor(resolveTileColor());
                bg.setCornerRadius(12 * getResources().getDisplayMetrics().density);
                if (ObsidianPrefs.getBoolean(AdvancedRebootTilesFragment.PREF_TILE_BORDER, false)) {
                    int strokeWidth = Math.round(2 * getResources().getDisplayMetrics().density);
                    bg.setStroke(strokeWidth, resolveTileBorderColor());
                }
                label.setBackground(bg);

                RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
                int m = Math.round(4 * getResources().getDisplayMetrics().density);
                lp.setMargins(m, m, m, m);
                label.setLayoutParams(lp);
                return new TileVH(label);
            }

            @Override public void onBindViewHolder(@NonNull TileVH holder, int position) {
                if (position < builtInCount) {
                    int rebootIndex = indices.get(position);
                    holder.label.setText(labels[rebootIndex]);
                    holder.label.setOnClickListener(v -> {
                        runRebootChoice(rebootIndex);
                        if (dlgRef[0] != null) dlgRef[0].dismiss();
                    });
                } else {
                    String pkg = customApps.get(position - builtInCount);
                    String appLabel = pkg;
                    try {
                        appLabel = getPackageManager().getApplicationLabel(
                                getPackageManager().getApplicationInfo(pkg, 0)).toString();
                    } catch (Throwable ignored) {}
                    holder.label.setText(appLabel);
                    holder.label.setOnClickListener(v -> {
                        try {
                            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
                            if (launch != null) {
                                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(launch);
                            }
                        } catch (Throwable ignored) {}
                        finishAndRemoveTask();
                    });
                }
            }

            @Override public int getItemCount() { return builtInCount + customApps.size(); }
        });

        AlertDialog dlg = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.advanced_reboot_title)
                .setView(grid)
                .setOnCancelListener(dialog -> finishAndRemoveTask())
                .setNegativeButton(R.string.cancel, (dialog, w) -> finishAndRemoveTask())
                .show();
        dlgRef[0] = dlg;
        ObsidianTheme.themeDialog(dlg);
    }

    private static class TileVH extends RecyclerView.ViewHolder {
        final TextView label;
        TileVH(TextView label) {
            super(label);
            this.label = label;
        }
    }
}
