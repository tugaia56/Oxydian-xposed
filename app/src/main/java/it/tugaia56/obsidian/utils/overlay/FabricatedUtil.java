package it.tugaia56.obsidian.utils.overlay;

import com.topjohnwu.superuser.Shell;

import it.tugaia56.obsidian.utils.ModuleConstants;

public class FabricatedUtil {

    /**
     * Overlay name prefix. cmd overlay fabricate runs as com.android.shell,
     * so the full overlay identity is "com.android.shell:ObsidianComponent<name>".
     */
    private static final String COMPONENT        = "ObsidianComponent";
    private static final String SHELL_COMPONENT  = "com.android.shell:" + COMPONENT;

    /** Magisk/KSU module dir — set once, then service.sh re-runs post-exec.sh on boot. */
    private static final String MODULE_DIR = ModuleConstants.MODULE_DIR;
    private static volatile boolean sModuleReady = false;

    /** Resource type codes required by cmd overlay fabricate (NOT the string "color" etc.) */
    private static String typeCode(String type) {
        switch (type) {
            case "color":   return "0x1c";
            case "bool":    return "0x12";
            case "integer": return "0x10";
            case "dimen":   return "0x05";
            default:        return "0x1c";
        }
    }

    /**
     * Build and enable a single fabricated overlay resource.
     * @param pkg        target package (e.g. "android")
     * @param name       unique overlay name (e.g. "ACCENT1_0")
     * @param type       resource type string (e.g. "color")
     * @param resName    resource name (e.g. "accent_material_dark")
     * @param value      value in 0xAARRGGBB hex (e.g. "0xFF6200EE")
     */
    public static void buildAndEnableOverlay(String pkg, String name,
                                             String type, String resName, String value) {
        String fullName = COMPONENT + name;
        String cmd1 = "cmd overlay fabricate --target " + pkg + " --name " + fullName
                + " " + pkg + ":" + type + "/" + resName + " " + typeCode(type) + " " + value;
        String cmd2 = "cmd overlay enable --user current " + SHELL_COMPONENT + name;
        Shell.cmd(cmd1, cmd2).exec();
        saveToPostExec(name, cmd1, cmd2);
    }

    /**
     * Batch build-and-enable. Each element is Object[]{pkg, name, type, resName, value}.
     * Runs all commands in a single Shell.cmd() call for speed.
     */
    public static void buildAndEnableOverlays(Object[]... args) {
        String[] cmds = new String[args.length * 2];
        int i = 0;
        for (Object[] a : args) {
            String pkg      = (String) a[0];
            String name     = (String) a[1];
            String type     = (String) a[2];
            String res      = (String) a[3];
            String val      = (String) a[4];
            String fullName = COMPONENT + name;
            cmds[i++] = "cmd overlay fabricate --target " + pkg + " --name " + fullName
                    + " " + pkg + ":" + type + "/" + res + " " + typeCode(type) + " " + val;
            cmds[i++] = "cmd overlay enable --user current " + SHELL_COMPONENT + name;
        }
        Shell.cmd(cmds).exec();
        // Persist to post-exec.sh (pairs: cmd[2k] = fabricate, cmd[2k+1] = enable)
        for (int k = 0; k < cmds.length; k += 2) {
            String name = (String) args[k / 2][1];
            saveToPostExec(name, cmds[k], cmds[k + 1]);
        }
    }

    /** Disable overlays by name. */
    public static void disableOverlays(String... names) {
        String[] cmds = new String[names.length];
        for (int i = 0; i < names.length; i++) {
            cmds[i] = "cmd overlay disable --user current " + SHELL_COMPONENT + names[i];
            removeFromPostExec(names[i]);
        }
        Shell.cmd(cmds).exec();
    }

    /** Disable a single overlay by name. */
    public static void disableOverlay(String name) {
        Shell.cmd("cmd overlay disable --user current " + SHELL_COMPONENT + name).exec();
        removeFromPostExec(name);
    }

    // ── Magisk/KSU module persistence ────────────────────────────────────────

    /**
     * Saves the fabricate+enable pair for {@code name} to post-exec.sh so that
     * Magisk/KSU re-applies it at every subsequent boot.
     * The module's service.sh runs post-exec.sh after boot_completed.
     */
    private static void saveToPostExec(String name, String cmd1, String cmd2) {
        ensureModule();
        // Remove any stale entry for this name, then append the fresh commands. Uses a
        // per-name temp file (not a shared one) and runs synchronously: buildAndEnableOverlays
        // calls this once per resource in a batch (e.g. 3 times for the Recents button's
        // color+drawable1+drawable2), and a shared temp file + async .submit() let those calls
        // race and clobber each other's writes to post-exec.sh — some entries silently lost,
        // so only part of a multi-resource overlay got re-applied on the next boot.
        String pex = MODULE_DIR + "/post-exec.sh";
        String tmp = MODULE_DIR + "/post-exec-" + name + ".tmp";
        Shell.cmd(
            "grep -v \"" + COMPONENT + name + "\" " + pex + " > " + tmp
                + " && mv " + tmp + " " + pex,
            "echo " + shellQuote(cmd1) + " >> " + pex,
            "echo " + shellQuote(cmd2) + " >> " + pex
        ).exec();
    }

    /**
     * Removes the entry for {@code name} from post-exec.sh so it is NOT
     * re-applied at next boot.
     */
    private static void removeFromPostExec(String name) {
        String pex = MODULE_DIR + "/post-exec.sh";
        String tmp = MODULE_DIR + "/post-exec-" + name + ".tmp";
        Shell.cmd(
            "[ -f " + pex + " ] && grep -v \"" + COMPONENT + name + "\" " + pex
                + " > " + tmp + " && mv " + tmp + " " + pex + " || true"
        ).exec();
    }

    /**
     * Creates the Magisk/KSU module structure if not already present.
     * Called lazily on first overlay apply.
     */
    private static void ensureModule() {
        if (sModuleReady) return;
        sModuleReady = true;
        String d = MODULE_DIR;
        // Module prop
        String prop = "id=Obsidian\\nname=Obsidian\\nversion=1.0\\n"
                    + "versionCode=1\\nauthor=tugaia56\\n"
                    + "description=Obsidian DST fabricated overlay persistence";
        // service.sh waits for boot_completed then runs post-exec.sh
        // Written via printf to avoid shell expansion of $() inside the script
        String svcCmd =
            "printf '%s\\n' "
            + "'MODDIR=${0%%/*}' '' "
            + "'while [ \"$(getprop sys.boot_completed | tr -d \\\"\\\\r\\\")\" != \"1\" ]' "
            + "'do' '  sleep 1' 'done' 'sleep 5' '' "
            + "'sh $MODDIR/post-exec.sh'"
            + " > " + d + "/service.sh";
        Shell.cmd(
            "mkdir -p " + d,
            "[ -f " + d + "/module.prop ] || printf '" + prop + "' > " + d + "/module.prop",
            "[ -f " + d + "/service.sh ] || " + svcCmd,
            "[ -f " + d + "/post-exec.sh ] || touch " + d + "/post-exec.sh",
            "chmod 755 " + d + "/service.sh",
            "chmod 755 " + d + "/post-exec.sh"
        ).submit();
    }

    /** Wraps a shell command in single quotes, escaping any internal single quotes. */
    private static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
