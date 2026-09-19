package it.tugaia56.obsidian.utils;

import com.topjohnwu.superuser.Shell;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import it.tugaia56.obsidian.Obsidian;
import it.tugaia56.obsidian.ui.models.DarkShadowItem;
import it.tugaia56.obsidian.utils.overlay.FabricatedUtil;

/**
 * Applies every DST color resource as a Fabricated Overlay so Substratum and
 * RRO-based consumers can read them from the resource table.
 *
 * XResources.setReplacement() (MonetFreeze) is invisible at the resource-table
 * level. This class replicates OC's FabricatedUtil approach: one fabricated
 * overlay per resource, named ObsidianComponent<OVERLAYNAME>_<index>.
 *
 * Persistence: delegates to FabricatedUtil.buildAndEnableOverlays() which
 * calls saveToPostExec() internally — the same proven mechanism used for PIN.
 */
public class DstFabricatedUtil {

    private static final String PREFIX = "ObsidianComponent";
    private static final String TYPE   = "0x1c"; // TYPE_COLOR (used by applyMultiColorThenRun)

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Fabricate all overlays for this item on a background thread, then call
     * {@code onDone} (e.g. AppUtils::restartSystemUI) when all commands finish.
     * No-op if item.isEnabled() is false (onDone is NOT called in that case).
     *
     * Uses FabricatedUtil.buildAndEnableOverlays() so overlays are automatically
     * saved to post-exec.sh — same persistence used for PIN colors.
     */
    public static void applyThenRun(DarkShadowItem item, Runnable onDone) {
        if (!item.isEnabled()) return;

        int color = item.getColor();
        String pkg = item.getPackages().isEmpty() ? "android" : item.getPackages().get(0);
        String colorHex = toHex(color);

        List<Object[]> argsList = new ArrayList<>();
        int i = 0;
        for (String resName : item.getResourceNames()) {
            argsList.add(new Object[]{pkg, item.getOverlayName() + "_" + i++, "color", resName, colorHex});
        }
        for (Map.Entry<String, Integer> entry : item.getAdjustColors().entrySet()) {
            argsList.add(new Object[]{pkg, item.getOverlayName() + "_" + i++, "color",
                    entry.getKey(), toHex(adjustForItem(item, color, entry.getValue()))});
        }
        if (argsList.isEmpty()) return;

        final Object[][] args = argsList.toArray(new Object[0][]);
        new Thread(() -> {
            FabricatedUtil.buildAndEnableOverlays(args); // applies + saves to post-exec.sh
            saveBootProps();
            if (onDone != null) onDone.run();
        }).start();
    }

    /**
     * Re-applica tutti i FabricatedOverlay DST abilitati su un thread di sfondo,
     * poi chiama onDone (tipicamente AppUtils::restartSystemUI).
     * Usato dopo il ripristino del backup.
     */
    public static void reapplyAll(Runnable onDone) {
        new Thread(() -> {
            try {
                Context ctx = Obsidian.get();
                if (ctx != null) {
                    List<String> allCmds = new ArrayList<>();
                    for (DarkShadowItem item : DarkShadowUtils.getItems(ctx)) {
                        if (item.isEnabled()) allCmds.addAll(buildApplyCommands(item));
                    }
                    if (!allCmds.isEmpty()) Shell.cmd(String.join("; ", allCmds)).exec();
                    saveBootProps();
                }
            } catch (Throwable ignored) {}
            if (onDone != null) onDone.run();
        }).start();
    }

    /**
     * Fabricate overlays where each resource gets a different color (e.g. Accent Outline preset).
     * {@code resourceColors} is a LinkedHashMap preserving insertion order → index mapping.
     */
    public static void applyMultiColorThenRun(String overlayName, String pkg,
            LinkedHashMap<String, Integer> resourceColors, Runnable onDone) {
        List<String> commands = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Integer> entry : resourceColors.entrySet()) {
            addFabricateCommands(commands, pkg, overlayName, i, entry.getKey(), toHex(entry.getValue()));
            i++;
        }
        if (commands.isEmpty()) { if (onDone != null) onDone.run(); return; }
        final String joined = String.join("; ", commands);
        new Thread(() -> { Shell.cmd(joined).exec(); if (onDone != null) onDone.run(); }).start();
    }

    /**
     * Disable all fabricated overlays for the given item on a background thread,
     * then call {@code onDone} when finished.
     *
     * Uses FabricatedUtil.disableOverlays() so entries are also removed from post-exec.sh.
     */
    public static void disableThenRun(DarkShadowItem item, Runnable onDone) {
        int count = item.getResourceNames().size() + item.getAdjustColors().size();
        if (count == 0) { if (onDone != null) onDone.run(); return; }

        String[] names = new String[count];
        for (int i = 0; i < count; i++) names[i] = item.getOverlayName() + "_" + i;

        new Thread(() -> {
            FabricatedUtil.disableOverlays(names); // disables + removes from post-exec.sh
            saveBootProps();
            if (onDone != null) onDone.run();
        }).start();
    }

    // ── Internals ──────────────────────────────────────────────────────────────

    private static List<String> buildApplyCommands(DarkShadowItem item) {
        int color = item.getColor();
        String pkg = item.getPackages().isEmpty() ? "android" : item.getPackages().get(0);
        String colorHex = toHex(color);

        List<String> commands = new ArrayList<>();
        int i = 0;

        for (String resName : item.getResourceNames()) {
            addFabricateCommands(commands, pkg, item.getOverlayName(), i, resName, colorHex);
            i++;
        }
        for (Map.Entry<String, Integer> entry : item.getAdjustColors().entrySet()) {
            String adjHex = toHex(adjustForItem(item, color, entry.getValue()));
            addFabricateCommands(commands, pkg, item.getOverlayName(), i, entry.getKey(), adjHex);
            i++;
        }
        return commands;
    }

    /**
     * ACCENT1's adjust map (system_accentN_100..700) needs a real white/black blend so
     * light-tone containers and dark-tone text never collapse to the same color — see
     * DarkShadowUtils.ACCENT_TONE_ADJUST. Every other adjust map (BACKGROUND's lighter
     * card/button/dialog variants) keeps the original multiplicative scaling, unchanged.
     */
    private static int adjustForItem(DarkShadowItem item, int color, int amount) {
        return "ACCENT1".equals(item.getOverlayName())
                ? ColorUtils.blendTone(color, amount)
                : ColorUtils.adjustColor(color, amount);
    }

    private static void addFabricateCommands(List<String> out,
                                             String target, String overlayName,
                                             int index, String resource, String hex) {
        String fullName = PREFIX + overlayName + "_" + index;
        out.add("cmd overlay fabricate --target " + target
                + " --name " + fullName
                + " " + target + ":color/" + resource
                + " " + TYPE + " " + hex);
        out.add("cmd overlay enable --user current com.android.shell:" + fullName);
    }

    /** 0xAARRGGBB hex string; mask ensures unsigned 32-bit even for signed int. */
    private static String toHex(int color) {
        return String.format("0x%08X", 0xFFFFFFFFL & color);
    }

    // ── Boot-time property persistence ────────────────────────────────────────

    /**
     * Writes current DST pref values to persist.obsidian.dst.* system properties
     * so MonetFreeze can read them at boot via SystemProperties.get() when the
     * SharedPreferences XML file is not yet world-readable (EACCES from system server).
     */
    public static void saveBootProps() {
        try {
            boolean a1On = ObsidianPrefs.getBoolean("DST_ACCENT1_on",    false);
            int     a1   = ObsidianPrefs.getInt(    "DST_ACCENT1",        0);
            boolean a2On = ObsidianPrefs.getBoolean("DST_ACCENT2_on",    false);
            int     a2   = ObsidianPrefs.getInt(    "DST_ACCENT2",        0);
            boolean a3On = ObsidianPrefs.getBoolean("DST_ACCENT3_on",    false);
            int     a3   = ObsidianPrefs.getInt(    "DST_ACCENT3",        0);
            boolean bgOn = ObsidianPrefs.getBoolean("DST_BACKGROUND_on", false);
            int     bg   = ObsidianPrefs.getInt(    "DST_BACKGROUND",     0);
            // 2026-09-05: serve a SettingsCardBackgroundMod.preloadFallback() — alcune app
            // (com.oneplus.account, com.oplus.games) non possono MAI vedere il ContentProvider
            // di Obsidian (filtro di visibilità pacchetti di Android, permanente, non un
            // problema di timing) né leggere il file prefs (cartella privata di un'altra app,
            // permesso negato all'UID di quell'app anche con la stessa causa). Le system
            // properties sono l'UNICO canale leggibile da qualunque app indipendentemente da
            // entrambi questi limiti — stesso motivo per cui MonetFreeze le usa già.
            boolean settingsThemeApplied = ObsidianPrefs.getBoolean("settings_theme_applied", false);
            String  pin       = ObsidianPrefs.getString("DST_PIN",              "");
            String  pinNum    = ObsidianPrefs.getString("DST_PIN_NUM",          "");
            String  dlgPreset = ObsidianPrefs.getString("DST_DLG_PRESET_NAME",  "");
            // CPB / RVD / SVD / QS BG presets — needed by hooks that lack Xprefs at early boot
            String  cpbPreset   = ObsidianPrefs.getString("DST_PRESET_CPB",     "");
            String  rvdPreset   = ObsidianPrefs.getString("DST_PRESET_RVD",     "");
            String  svdPreset   = ObsidianPrefs.getString("DST_PRESET_SVD",     "");
            String  toastPreset  = ObsidianPrefs.getString("DST_PRESET_TOAST",  "");
            String  notifPreset  = ObsidianPrefs.getString("DST_PRESET_NOTIF",  "");
            int     notifCorner  = ObsidianPrefs.getInt(   "DST_NOTIF_CORNER",   24);
            int     toastCorner  = ObsidianPrefs.getInt(   "DST_TOAST_CORNER",   24);
            int     dlgCorner    = ObsidianPrefs.getInt(   "DST_DLG_CORNER",     24);
            int     notifTexSize  = ObsidianPrefs.getInt(  "DST_NOTIF_TEXTURE_SIZE",  100);
            int     notifTexAlpha = ObsidianPrefs.getInt(  "DST_NOTIF_TEXTURE_ALPHA", 25);
            String  notifTexColMode  = ObsidianPrefs.getString("DST_NOTIF_TEXTURE_COLOR_MODE", "accent");
            int     notifTexCol      = ObsidianPrefs.getInt(   "DST_NOTIF_TEXTURE_COLOR_CUSTOM", 0xFF9C27B0);
            boolean notifTexBrdOn    = ObsidianPrefs.getBoolean("DST_NOTIF_TEXTURE_BORDER_ENABLED", false);
            String  notifTexBrdMode  = ObsidianPrefs.getString("DST_NOTIF_TEXTURE_BORDER_MODE", "accent");
            int     notifTexBrdCol   = ObsidianPrefs.getInt(   "DST_NOTIF_TEXTURE_BORDER_CUSTOM", 0xFF9C27B0);
            int     notifImgOffsetY = ObsidianPrefs.getInt(   "DST_NOTIF_IMG_OFFSET_Y", 50);
            boolean qsBgOn       = ObsidianPrefs.getBoolean("DST_QS_BG_ENABLED", false);
            boolean cpDowngrade      = ObsidianPrefs.getBoolean("DST_COREPATCH_DOWNGRADE",      true);
            boolean cpBypassBlock    = ObsidianPrefs.getBoolean("DST_COREPATCH_BYPASS_BLOCK",    true);
            boolean cpDisableVerify  = ObsidianPrefs.getBoolean("DST_COREPATCH_DISABLE_VERIFY",  true);
            boolean screenshotEnabler = ObsidianPrefs.getBoolean("DST_SCREENSHOT_ENABLER_ON",    false);
            boolean lkAdbConfirm  = ObsidianPrefs.getBoolean("DST_LUCKY_ADB_NO_CONFIRM",     false);
            boolean lkPowerMenu   = ObsidianPrefs.getBoolean("DST_LUCKY_FAST_POWER_MENU",    false);
            boolean lkVolFlash    = ObsidianPrefs.getBoolean("DST_LUCKY_VOLUME_FLASHLIGHT",  false);
            boolean lkMultiApp    = ObsidianPrefs.getBoolean("DST_LUCKY_MULTIAPP_NO_BLACKLIST", false);
            boolean lkPngShot     = ObsidianPrefs.getBoolean("DST_LUCKY_PNG_SCREENSHOT", false);
            boolean lkLongshot    = ObsidianPrefs.getBoolean("DST_LUCKY_LONGSHOT_NO_LIMIT", false);
            // 2026-09-07: segnalato dall'utente ("il pillolone e lo sfondo spesso tornano
            // stock") — MiscMods (menù accensione: pillolone, sfondo, bordo, pallino) non aveva
            // MAI avuto un preloadFallback come SettingsCardBackgroundMod/DstCpbStyle/DstNotifStyle,
            // quindi era interamente in balìa della stessa corsa al boot (ContentProvider non
            // ancora raggiungibile) senza alcuna rete di sicurezza — se l'utente apriva il menù
            // accensione abbastanza presto, vedeva sempre lo stock finché updatePrefs() non
            // arrivava. Stesso canale/stesso motivo delle altre.
            String  pmGradientMode  = ObsidianPrefs.getString("power_menu_gradient_mode", "accent");
            int     pmGradientColor = ObsidianPrefs.getInt(   "power_menu_gradient_custom_color", 0xFF908DFF);
            String  pmBgMode        = ObsidianPrefs.getString("power_menu_bg_mode", "stock");
            int     pmBgColor       = ObsidianPrefs.getInt(   "power_menu_bg_custom_color", 0xFF908DFF);
            boolean pmBorderOn      = ObsidianPrefs.getBoolean("power_menu_border_enabled", false);
            boolean pmBorderAccent  = ObsidianPrefs.getBoolean("power_menu_border_use_accent", true);
            int     pmBorderColor   = ObsidianPrefs.getInt(   "power_menu_border_custom_color", 0xFF908DFF);
            String  pmHandlerMode   = ObsidianPrefs.getString("power_menu_handler_mode", "stock");
            int     pmHandlerColor  = ObsidianPrefs.getInt(   "power_menu_handler_custom_color", 0xFF908DFF);
            boolean pmHandlerBorderOn     = ObsidianPrefs.getBoolean("power_menu_handler_border_enabled", false);
            boolean pmHandlerBorderAccent = ObsidianPrefs.getBoolean("power_menu_handler_border_use_accent", true);
            int     pmHandlerBorderColor  = ObsidianPrefs.getInt(   "power_menu_handler_border_custom_color", 0xFF908DFF);
            String  pmMenuBgMode    = ObsidianPrefs.getString("power_menu_menu_bg_mode", "stock");
            int     pmMenuBgColor   = ObsidianPrefs.getInt(   "power_menu_menu_bg_custom_color", 0xFF908DFF);

            Shell.Result propsResult = Shell.cmd(
                "setprop persist.obsidian.dst.a1_on       " + (a1On ? "1" : "0"),
                "setprop persist.obsidian.dst.a1          " + a1,
                "setprop persist.obsidian.dst.a2_on       " + (a2On ? "1" : "0"),
                "setprop persist.obsidian.dst.a2          " + a2,
                "setprop persist.obsidian.dst.a3_on       " + (a3On ? "1" : "0"),
                "setprop persist.obsidian.dst.a3          " + a3,
                "setprop persist.obsidian.dst.bg_on       " + (bgOn ? "1" : "0"),
                "setprop persist.obsidian.dst.bg          " + bg,
                "setprop persist.obsidian.dst.settings_theme_on " + (settingsThemeApplied ? "1" : "0"),
                "setprop persist.obsidian.dst.pin          \"" + (pin       == null ? "" : pin)       + "\"",
                "setprop persist.obsidian.dst.pin_num      \"" + (pinNum    == null ? "" : pinNum)    + "\"",
                "setprop persist.obsidian.dst.dlg_preset   \"" + (dlgPreset == null ? "" : dlgPreset) + "\"",
                "setprop persist.obsidian.dst.cpb_preset   \"" + (cpbPreset == null ? "" : cpbPreset) + "\"",
                "setprop persist.obsidian.dst.rvd_preset   \"" + (rvdPreset == null ? "" : rvdPreset) + "\"",
                "setprop persist.obsidian.dst.svd_preset   \"" + (svdPreset   == null ? "" : svdPreset)   + "\"",
                "setprop persist.obsidian.dst.toast_preset  \"" + (toastPreset == null ? "" : toastPreset) + "\"",
                "setprop persist.obsidian.dst.notif_preset  \"" + (notifPreset == null ? "" : notifPreset) + "\"",
                "setprop persist.obsidian.dst.notif_corner  " + notifCorner,
                "setprop persist.obsidian.dst.toast_corner  " + toastCorner,
                "setprop persist.obsidian.dst.dlg_corner    " + dlgCorner,
                "setprop persist.obsidian.dst.notif_tex_size  " + notifTexSize,
                "setprop persist.obsidian.dst.notif_tex_alpha " + notifTexAlpha,
                "setprop persist.obsidian.dst.notif_tex_col_mode \"" + notifTexColMode + "\"",
                "setprop persist.obsidian.dst.notif_tex_col      " + notifTexCol,
                "setprop persist.obsidian.dst.notif_tex_brd_on   " + (notifTexBrdOn ? "true" : "false"),
                "setprop persist.obsidian.dst.notif_tex_brd_mode \"" + notifTexBrdMode + "\"",
                "setprop persist.obsidian.dst.notif_tex_brd_col  " + notifTexBrdCol,
                "setprop persist.obsidian.dst.notif_img_offset_y " + notifImgOffsetY,
                "setprop persist.obsidian.dst.qs_bg_on      " + (qsBgOn ? "1" : "0"),
                "setprop persist.obsidian.dst.cp_downgrade    " + (cpDowngrade ? "1" : "0"),
                "setprop persist.obsidian.dst.cp_bypassblock  " + (cpBypassBlock ? "1" : "0"),
                "setprop persist.obsidian.dst.cp_disableverify " + (cpDisableVerify ? "1" : "0"),
                "setprop persist.obsidian.dst.screenshot_enabler " + (screenshotEnabler ? "1" : "0"),
                "setprop persist.obsidian.dst.lk_adbconfirm  " + (lkAdbConfirm ? "1" : "0"),
                "setprop persist.obsidian.dst.lk_powermenu   " + (lkPowerMenu ? "1" : "0"),
                "setprop persist.obsidian.dst.lk_volflash    " + (lkVolFlash ? "1" : "0"),
                "setprop persist.obsidian.dst.lk_multiapp    " + (lkMultiApp ? "1" : "0"),
                "setprop persist.obsidian.dst.lk_pngshot     " + (lkPngShot ? "1" : "0"),
                "setprop persist.obsidian.dst.lk_longshot    " + (lkLongshot ? "1" : "0"),
                "setprop persist.obsidian.dst.pm_gradient_mode  \"" + pmGradientMode + "\"",
                "setprop persist.obsidian.dst.pm_gradient_color " + pmGradientColor,
                "setprop persist.obsidian.dst.pm_bg_mode        \"" + pmBgMode + "\"",
                "setprop persist.obsidian.dst.pm_bg_color       " + pmBgColor,
                "setprop persist.obsidian.dst.pm_border_on      " + (pmBorderOn ? "1" : "0"),
                "setprop persist.obsidian.dst.pm_border_accent  " + (pmBorderAccent ? "1" : "0"),
                "setprop persist.obsidian.dst.pm_border_color   " + pmBorderColor,
                "setprop persist.obsidian.dst.pm_handler_mode   \"" + pmHandlerMode + "\"",
                "setprop persist.obsidian.dst.pm_handler_color  " + pmHandlerColor,
                "setprop persist.obsidian.dst.pm_handler_border_on     " + (pmHandlerBorderOn ? "1" : "0"),
                "setprop persist.obsidian.dst.pm_handler_border_accent " + (pmHandlerBorderAccent ? "1" : "0"),
                "setprop persist.obsidian.dst.pm_handler_border_color  " + pmHandlerBorderColor,
                "setprop persist.obsidian.dst.pm_menu_bg_mode   \"" + pmMenuBgMode + "\"",
                "setprop persist.obsidian.dst.pm_menu_bg_color  " + pmMenuBgColor
            ).exec();
            if (!propsResult.isSuccess()) {
                android.util.Log.e("Obsidian", "DstFabricatedUtil.saveBootProps: setprop FAILED code="
                        + propsResult.getCode() + " out=" + propsResult.getOut()
                        + " err=" + propsResult.getErr());
            }

            Shell.Result chmodResult = Shell.cmd("chmod 644 /data/user_de/0/it.tugaia56.obsidian/shared_prefs/"
                    + "it.tugaia56.obsidian_preferences.xml").exec();
            if (!chmodResult.isSuccess()) {
                android.util.Log.e("Obsidian", "DstFabricatedUtil.saveBootProps: chmod FAILED code="
                        + chmodResult.getCode() + " err=" + chmodResult.getErr());
            }
        } catch (Throwable t) {
            android.util.Log.e("Obsidian", "DstFabricatedUtil.saveBootProps: EXCEPTION", t);
        }
    }
}
