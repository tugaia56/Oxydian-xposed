package it.tugaia56.obsidian.xposed.hooks.framework;

import android.os.Build;
import android.view.SurfaceControl;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Member;
import java.lang.reflect.Method;

/**
 * Xposed hook: reimplementation of "Enable Screenshot" (io.github.lsposed.disableflagsecure),
 * ported from its real decompiled source (2026-09-02, user supplied Enable Screenshot_5.0.1.apk).
 * Single on/off toggle — "Abilita Screenshot" in Regolazioni Varie.
 *
 * All hooks live in system_server (packageName == processName == "android") — install() is
 * called once from XPLauncher.hookFramework() when lpparam.processName.equals("android"), plus
 * one extra hook installed in com.oplus.appplatform's own process (the only OEM-specific branch
 * kept, since that's the app that actually takes screenshots on this device's OOS/Oplus build —
 * the original module also has MIUI/Flyme-specific branches, dropped here as irrelevant hardware).
 *
 * KNOWN GAP: the original module's lowest-level hook — patching the "mSecureContentPolicy" /
 * "mCaptureSecureLayers" field directly on ScreenCapture's native capture calls — was NOT ported.
 * jadx itself failed to cleanly decompile that one method ("Code decompiled incorrectly") and its
 * helper class wasn't recoverable from the dex at all, so hand-porting it blind risked either a
 * silent no-op or a crash in the capture path on this exact Android build. Everything else below
 * (~9 hook points: WindowState.isSecureLocked, ScreenshotHardwareBuffer.containsSecureLayers,
 * forcing secure=true on virtual displays, the ActivityManagerService permission swap, the
 * OneUI/HyperOS/Oplus branches) already covers the same capability through multiple independent,
 * redundant layers — this is the one redundant layer left out, not the only one.
 */
public class ScreenshotEnablerMod {

    private static final String PREF_ON = "DST_SCREENSHOT_ENABLER_ON";
    private static final String PREFS_FILE =
        "/data/user_de/0/it.tugaia56.obsidian/shared_prefs/it.tugaia56.obsidian_preferences.xml";

    // Nuova funzionalità opt-in — parte OFF di default, come le altre funzioni che toccano
    // comportamenti di sicurezza/privacy in questa app (es. Blocca popup appunti).
    private static volatile boolean sOn = false;

    // ── Live pref read (stesso pattern di CorePatchMod/DstDialogStyle) ────────────────────────

    private static String readProp(String key, String def) {
        try {
            Class<?> sp = XposedHelpers.findClass("android.os.SystemProperties", null);
            Object val = XposedHelpers.callStaticMethod(sp, "get", key, def);
            return val != null ? (String) val : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private static void preloadFromFile() {
        try {
            java.io.File f = new java.io.File(PREFS_FILE);
            if (!f.exists()) { preloadFromProps(); return; }
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            String xml = sb.toString();
            int idx = xml.indexOf("name=\"" + PREF_ON + "\"");
            if (idx < 0) { sOn = false; return; }
            int s = xml.indexOf("value=\"", idx);
            if (s < 0) { sOn = false; return; }
            s += 7;
            int e = xml.indexOf("\"", s);
            sOn = e >= 0 && "true".equals(xml.substring(s, e));
        } catch (Throwable t) {
            preloadFromProps();
        }
    }

    private static void preloadFromProps() {
        sOn = "1".equals(readProp("persist.obsidian.dst.screenshot_enabler", "0"));
    }

    // ── Install (system_server) ───────────────────────────────────────────────────────────────

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        ClassLoader cl = lpparam.classLoader;
        preloadFromFile();

        deoptimizeAll(cl);
        installCreateDisplayHook(cl);
        installVirtualDisplayAdapterHook(cl);
        installMiscForceHooks(cl);

        int sdk = Build.VERSION.SDK_INT;
        if (sdk >= 35) {
            hookAllIfExists(cl, "com.android.server.wm.WindowManagerService", "registerScreenRecordingCallback",
                    guardedResult(Boolean.FALSE));
        }
        if (sdk >= 34) {
            hookAllIfExists(cl, "com.android.server.wm.ActivityTaskManagerService", "registerScreenCaptureObserver",
                    guardedResult(null));
            hookAllIfExists(cl, "com.android.server.wm.WindowManagerServiceImpl", "notAllowCaptureDisplay",
                    guardedResult(Boolean.FALSE));
        }
        if (sdk < 34) {
            hookAllIfExists(cl, "com.android.server.am.ActivityManagerService", "checkPermission",
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            preloadFromFile();
                            if (!sOn) return;
                            if ("android.permission.CAPTURE_BLACKOUT_CONTENT".equals(param.args[0])) {
                                param.args[0] = "android.permission.READ_FRAME_BUFFER";
                            }
                        }
                    });
        }

        XposedBridge.log("[ Obsidian ] ScreenshotEnablerMod: hooks installed in " + android.os.Process.myProcessName());
    }

    /** Hook aggiuntivo Oplus-specifico — solo com.oplus.appplatform (il processo che scatta
     *  davvero gli screenshot su questa build OOS), chiamato da XPLauncher.hookOtherPackage(). */
    public static void installOplusAppPlatform(XC_LoadPackage.LoadPackageParam lpparam) {
        ClassLoader cl = lpparam.classLoader;
        preloadFromFile();
        hookAllIfExists(cl, "android.view.SurfaceControl$ScreenshotHardwareBuffer", "containsSecureLayers",
                guardedResult(Boolean.FALSE));
        hookAllIfExists(cl, "android.window.ScreenCapture$ScreenshotHardwareBuffer", "containsSecureLayers",
                guardedResult(Boolean.FALSE));
        if (Build.VERSION.SDK_INT >= 35) {
            try {
                Class<?> builder = Class.forName("com.oplus.screenshot.OplusScreenCapture$CaptureArgs$Builder", false, cl);
                Method m = builder.getDeclaredMethod("setUid", long.class);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        preloadFromFile();
                        if (sOn) param.args[0] = -1L;
                    }
                });
            } catch (Throwable ignored) {
            }
        }
    }

    // ── Individual hook installers ────────────────────────────────────────────────────────────

    /** Sblocca (deoptimize) alcuni metodi hot-path prima di installare gli altri hook, altrimenti
     *  su alcune build il codice già compilato/inlineato dal runtime ignora l'hook — stessa
     *  tecnica dell'app originale, necessaria solo per WindowStateAnimator/WindowManagerService
     *  e per le lambda generate dal compilatore (RootWindowContainer$$ExternalSyntheticLambdaN /
     *  DisplayContent$N) che a runtime finiscono per implementare i controlli "è sicura questa
     *  finestra?" — non hookiamo queste lambda direttamente (sono generate, instabili tra build),
     *  le deottimizziamo soltanto per aiutare gli hook reali (isSecureLocked ecc.) a funzionare. */
    private static void deoptimizeAll(ClassLoader cl) {
        try {
            deoptimizeMethodsNamed(cl, "com.android.server.wm.WindowStateAnimator", "createSurfaceLocked");
            deoptimizeMethodsNamed(cl, "com.android.server.wm.WindowManagerService", "relayoutWindow");
            for (int i = 0; i < 20; i++) {
                try {
                    Class<?> lambda = Class.forName("com.android.server.wm.RootWindowContainer$$ExternalSyntheticLambda" + i, false, cl);
                    if (java.util.function.BiConsumer.class.isAssignableFrom(lambda)) {
                        deoptimizeMethodsNamed(lambda, "accept");
                    }
                } catch (Throwable ignored) {}
                try {
                    Class<?> inner = Class.forName("com.android.server.wm.DisplayContent$" + i, false, cl);
                    if (java.util.function.BiPredicate.class.isAssignableFrom(inner)) {
                        deoptimizeMethodsNamed(inner, "test");
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] ScreenshotEnablerMod: deoptimize failed: " + t);
        }
    }

    private static void deoptimizeMethodsNamed(ClassLoader cl, String className, String methodName) {
        try {
            deoptimizeMethodsNamed(Class.forName(className, false, cl), methodName);
        } catch (Throwable ignored) {
        }
    }

    private static void deoptimizeMethodsNamed(Class<?> cls, String methodName) {
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                deoptimize(m);
            }
        }
    }

    /** XposedBridge.deoptimizeMethod() è un'estensione runtime di LSPosed, assente nello stub
     *  api-82.jar usato in compilazione — chiamata via reflection per non rompere il compile. */
    private static void deoptimize(Method m) {
        try {
            Method deopt = XposedBridge.class.getDeclaredMethod("deoptimizeMethod", Member.class);
            deopt.invoke(null, m);
        } catch (Throwable ignored) {
        }
    }

    /** createDisplay/createVirtualDisplay (SurfaceControl o DisplayControl a seconda della
     *  versione): forza "secure=true" così la nostra cattura può vedere anche i livelli protetti
     *  — su Android &lt;34 lascia stare la chiamata legittima fatta da createVirtualDisplayLocked
     *  stesso (per non alterare display virtuali creati per altri scopi), da 34 in su la logica
     *  originale forza sempre, senza eccezioni (scelta dell'autore originale, replicata fedele). */
    private static void installCreateDisplayHook(ClassLoader cl) {
        try {
            int sdk = Build.VERSION.SDK_INT;
            Class<?> targetClass = sdk >= 34
                    ? Class.forName("com.android.server.display.DisplayControl", false, cl)
                    : SurfaceControl.class;
            String methodName = sdk >= 35 ? "createVirtualDisplay" : "createDisplay";
            Method m = targetClass.getDeclaredMethod(methodName, String.class, boolean.class);
            final ClassLoader targetLoader = targetClass.getClassLoader();
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    preloadFromFile();
                    if (!sOn) return;
                    if (Build.VERSION.SDK_INT < 34) {
                        for (StackTraceElement el : new Throwable().getStackTrace()) {
                            if ("createVirtualDisplayLocked".equals(el.getMethodName())) {
                                try {
                                    if (Class.forName(el.getClassName(), false, cl).getClassLoader() == targetLoader) {
                                        return; // chiamata legittima, non toccare
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                    param.args[1] = Boolean.TRUE;
                }
            });
        } catch (Throwable ignored) {
        }
    }

    /** VirtualDisplayAdapter.createVirtualDisplayLocked: aggiunge il flag di visibilità sui
     *  contenuti protetti (bit 4) solo quando la chiamata sembra legata a una vera proiezione
     *  schermo (uid privilegiato o proiezione presente) — non tocca i display virtuali creati
     *  da normali app di terze parti senza motivo. */
    private static void installVirtualDisplayAdapterHook(ClassLoader cl) {
        hookAllIfExists(cl, "com.android.server.display.VirtualDisplayAdapter", "createVirtualDisplayLocked",
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        preloadFromFile();
                        if (!sOn) return;
                        try {
                            if (param.args.length > 2 && param.args[2] instanceof Integer
                                    && (Integer) param.args[2] >= 10000 && param.args[1] == null) {
                                return;
                            }
                            for (int i = 3; i < param.args.length; i++) {
                                if (param.args[i] instanceof Integer) {
                                    param.args[i] = ((Integer) param.args[i]) | 4;
                                    return;
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                });
    }

    /** Gli hook "restituisci sempre X" più semplici: WindowState.isSecureLocked (falso, tranne
     *  quando la chiamata arriva davvero dal disegno a schermo — lì lasciamo il vero valore, così
     *  chi guarda lo schermo dal vivo vede comunque l'oscuramento reale), ScreenshotHardwareBuffer
     *  .containsSecureLayers (falso), WmScreenshotController.canBeScreenshotTarget — OneUI (vero),
     *  OplusLongshotMainWindow.hasSecure — screenshot lunghi Oplus (falso). */
    private static void installMiscForceHooks(ClassLoader cl) {
        hookAllIfExists(cl, "com.android.server.wm.WindowState", "isSecureLocked", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                preloadFromFile();
                if (!sOn) return;
                for (StackTraceElement el : new Throwable().getStackTrace()) {
                    String mn = el.getMethodName();
                    if ("setInitialSurfaceControlProperties".equals(mn) || "createSurfaceLocked".equals(mn)) {
                        try {
                            if (Class.forName(el.getClassName(), false, cl).getClassLoader() == cl) {
                                return; // disegno reale a schermo, non toccare
                            }
                        } catch (Throwable ignored) {}
                    }
                }
                param.setResult(Boolean.FALSE);
            }
        });
        hookAllIfExists(cl, "android.view.SurfaceControl$ScreenshotHardwareBuffer", "containsSecureLayers",
                guardedResult(Boolean.FALSE));
        hookAllIfExists(cl, "android.window.ScreenCapture$ScreenshotHardwareBuffer", "containsSecureLayers",
                guardedResult(Boolean.FALSE));
        hookAllIfExists(cl, "com.android.server.wm.WmScreenshotController", "canBeScreenshotTarget",
                guardedResult(Boolean.TRUE));
        hookAllIfExists(cl, "com.android.server.wm.OplusLongshotMainWindow", "hasSecure",
                guardedResult(Boolean.FALSE));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────────

    /** XC_MethodHook che, quando il toggle è ON, forza il risultato a un valore fisso senza
     *  eseguire il metodo originale — rilegge il pref a ogni chiamata (stesso pattern "live" di
     *  CorePatchMod/DstDialogStyle). */
    private static XC_MethodHook guardedResult(final Object result) {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                preloadFromFile();
                if (sOn) param.setResult(result);
            }
        };
    }

    private static void hookAllIfExists(ClassLoader cl, String className, String methodName, XC_MethodHook hook) {
        try {
            Class<?> cls = Class.forName(className, false, cl);
            XposedBridge.hookAllMethods(cls, methodName, hook);
        } catch (Throwable ignored) {
        }
    }
}
