package it.tugaia56.obsidian.ui.activity;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.topjohnwu.superuser.Shell;

import java.util.concurrent.Executor;

import it.tugaia56.obsidian.R;

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

    private void showAdvancedReboot() {
        CharSequence[] list = new CharSequence[]{
                getString(R.string.advanced_reboot_recovery),
                getString(R.string.advanced_reboot_bootloader),
                getString(R.string.advanced_reboot_safe_mode),
                getString(R.string.advanced_reboot_fast_reboot),
                getString(R.string.advanced_reboot_systemui),
                getString(R.string.advanced_reboot_lock_screen),
                getString(R.string.advanced_reboot_screenshot),
                getString(R.string.advanced_reboot_fastbootd)
        };

        it.tugaia56.obsidian.utils.ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.advanced_reboot_title)
                .setItems(list, (dialog, which) -> {
                    String cmd = switch (which) {
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
                        // OOS ignores a synthetic KEYCODE_SYSRQ (confirmed on-device: no capture
                        // happened) — screencap writes the file directly instead, bypassing the
                        // key-injection path entirely. No system edit/share panel this way, but
                        // it reliably produces a real screenshot.
                        case 6 -> "mkdir -p /sdcard/Pictures/Screenshots && screencap -p /sdcard/Pictures/Screenshots/Screenshot_$(date +%Y%m%d_%H%M%S).png";
                        case 7 -> "reboot fastboot";
                        default -> "";
                    };
                    try { Shell.cmd(cmd).exec(); } catch (Throwable ignored) {}
                    finishAndRemoveTask();
                })
                .setOnCancelListener(dialog -> finishAndRemoveTask())
                .show());
    }
}
