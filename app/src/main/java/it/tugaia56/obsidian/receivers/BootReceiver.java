package it.tugaia56.obsidian.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.topjohnwu.superuser.Shell;

import it.tugaia56.obsidian.utils.Constants;
import it.tugaia56.obsidian.utils.DstFabricatedUtil;
import it.tugaia56.obsidian.utils.ModuleConstants;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;
import it.tugaia56.obsidian.utils.overlay.FabricatedUtil;

import static it.tugaia56.obsidian.utils.DarkShadowUtils.PREF_PREFIX;

/**
 * Fired on ACTION_BOOT_COMPLETED and ACTION_USER_UNLOCKED.
 *
 * Two-step persistence strategy (mirrors OC's approach):
 * 1. Magisk/KSU service.sh runs post-exec.sh early at boot (maintained by FabricatedUtil).
 * 2. This receiver re-applies after SystemUI is fully up (5s delay), catching any
 *    case where OOS theme service reset the overlays after service.sh ran.
 *
 * ACTION_USER_UNLOCKED is a safety net (added 2026-09-02, same pattern as OC's commit
 * bfe28e4e): if BOOT_COMPLETED fires while the device is still locked, ObsidianPrefs reads
 * below could come back empty/default (credential-encrypted storage isn't readable pre-unlock
 * — the same class of bug fixed today in Obsidian.java's WorkManager crash-loop), silently
 * skipping the reapply with no later retry. Listening for USER_UNLOCKED too means it just
 * runs again once the device is actually unlocked, catching that case.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_USER_UNLOCKED.equals(action)) return;

        // Launcher Recents button color — re-applied from prefs (live accent) after the
        // launcher/OMS settle, so a boot-time same-target overlay race can't leave it off.
        new Thread(() -> {
            try { Thread.sleep(20000); } catch (InterruptedException ignored) {}
            reapplyRecentsBtn();
        }).start();

        // DST ACCENT/BACKGROUND overlays (target: android) — OOS ThemeManager resets
        // them after boot, so we wait 15s to re-apply after ThemeManager finishes.
        new Thread(() -> {
            try { Thread.sleep(15000); } catch (InterruptedException ignored) {}
            DstFabricatedUtil.reapplyAll(null, true);
        }).start();
    }

    private static void reapplyRecentsBtn() {
        String key = "LAUNCHER_RECENTS_BTN_COLOR";
        if (!ObsidianPrefs.getBoolean(key + "_on", false)) return;
        int color = ObsidianPrefs.getBoolean(key + "_use_accent", false)
                ? ObsidianTheme.accentColor()
                : ObsidianPrefs.getInt(key, 0xFF6200EE);
        String hex = fmt(color);
        FabricatedUtil.buildAndEnableOverlays(
            new Object[]{Constants.LAUNCHER, "LAUNCHER_RECENTS_0", "color",
                    "toggle_bar_apply_btn_enabled_color", hex},
            new Object[]{Constants.LAUNCHER, "LAUNCHER_RECENTS_2", "drawable",
                    "recent_clear_circle", hex});
    }

    private static String fmt(int color) {
        return String.format("0x%08X", 0xFFFFFFFFL & color);
    }
}
