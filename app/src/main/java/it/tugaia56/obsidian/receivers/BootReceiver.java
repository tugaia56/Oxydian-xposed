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

import static it.tugaia56.obsidian.utils.DarkShadowUtils.PREF_PIN;
import static it.tugaia56.obsidian.utils.DarkShadowUtils.PREF_PIN_CUSTOM_COLOR;
import static it.tugaia56.obsidian.utils.DarkShadowUtils.PREF_PIN_NUM;
import static it.tugaia56.obsidian.utils.DarkShadowUtils.PREF_PIN_NUM_CUSTOM_COLOR;
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

        // PIN overlays (target: com.android.systemui) — 5s is enough
        new Thread(() -> {
            try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            reapplyPinBg();
            reapplyPinNum();
        }).start();

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
            DstFabricatedUtil.reapplyAll(null);
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

    // ── PIN background ────────────────────────────────────────────────────────

    private static void reapplyPinBg() {
        String pinPref = ObsidianPrefs.getString(PREF_PIN, null);
        if (pinPref == null || "default".equals(pinPref)) return;

        int accent = ObsidianPrefs.getInt(PREF_PREFIX + "ACCENT1", 0xFFFFFFFF);
        int color;
        boolean shade;

        if ("DSTPINCustom".equals(pinPref)) {
            color = ObsidianPrefs.getInt(PREF_PIN_CUSTOM_COLOR, 0xFFFFFFFF);
            shade = false;
        } else {
            color = accent;
            shade = "DSTPINAccentShade".equals(pinPref);
        }

        int rgb    = color & 0x00FFFFFF;
        int full   = 0xFF000000 | rgb;
        int ripple = 0x80000000 | rgb;
        int outer1 = 0xCC000000 | rgb;
        int outer2 = 0x40000000 | rgb;
        int outer3 = 0x21000000 | rgb;
        int shadow = shade ? ripple : full;

        FabricatedUtil.buildAndEnableOverlays(
            new Object[]{Constants.SYSTEM_UI, "PIN_border",   "color",
                    "coui_numeric_keyboard_border_color",                        fmt(full)},
            new Object[]{Constants.SYSTEM_UI, "PIN_inner1",   "color",
                    "coui_numeric_keyboard_inner_gradient_color_1",              fmt(ripple)},
            new Object[]{Constants.SYSTEM_UI, "PIN_inner2",   "color",
                    "coui_numeric_keyboard_inner_gradient_color_2",              fmt(ripple)},
            new Object[]{Constants.SYSTEM_UI, "PIN_shadow",   "color",
                    "coui_numeric_keyboard_upper_inner_shadow_color",            fmt(shadow)},
            new Object[]{Constants.SYSTEM_UI, "PIN_outer1",   "color",
                    "coui_numeric_keyboard_outer_gradient_color_1",              fmt(outer1)},
            new Object[]{Constants.SYSTEM_UI, "PIN_outer2",   "color",
                    "coui_numeric_keyboard_outer_gradient_color_2",              fmt(outer2)},
            new Object[]{Constants.SYSTEM_UI, "PIN_outer3",   "color",
                    "coui_numeric_keyboard_outer_gradient_color_3",              fmt(outer3)},
            new Object[]{Constants.SYSTEM_UI, "PIN_dotfill",  "color",
                    "coui_simple_lock_transparent_filled_rectangle_icon_color",  fmt(full)},
            new Object[]{Constants.SYSTEM_UI, "PIN_dotout",   "color",
                    "coui_simple_lock_transparent_outlined_rectangle_icon_color","0x33FFFFFF"},
            new Object[]{Constants.SYSTEM_UI, "PIN_wordtxt",  "color",
                    "coui_numeric_keyboard_dark_word_text_normal_color",         fmt(full)},
            new Object[]{Constants.SYSTEM_UI, "PIN_wordtxtL", "color",
                    "coui_numeric_keyboard_dark_word_text_normal_light_color",   fmt(full)}
        );
    }

    // ── PIN number color ──────────────────────────────────────────────────────

    private static void reapplyPinNum() {
        String preset = ObsidianPrefs.getString(PREF_PIN_NUM, null);
        if (preset == null || "default".equals(preset)) return;

        int accent = ObsidianPrefs.getInt(PREF_PREFIX + "ACCENT1", 0xFFFFFFFF);
        int color;

        if ("DSTNUMPINCustom".equals(preset)) {
            color = ObsidianPrefs.getInt(PREF_PIN_NUM_CUSTOM_COLOR, 0xFFFFFFFF);
        } else if ("DSTNUMPINAccentShade".equals(preset)) {
            color = 0x80000000 | (accent & 0x00FFFFFF);
        } else { // DSTNUMPINAccent
            color = accent;
        }

        FabricatedUtil.buildAndEnableOverlays(
            new Object[]{Constants.SYSTEM_UI, "PIN_NUM_color", "color",
                    "coui_numeric_keyboard_number_color",
                    fmt(0xFF000000 | (color & 0x00FFFFFF))}
        );
    }

    private static String fmt(int color) {
        return String.format("0x%08X", 0xFFFFFFFFL & color);
    }
}
