package it.tugaia56.obsidian.xposed.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;

import java.util.function.IntConsumer;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Stato UI della Schermata di Blocco / AOD (SHADE=1, LS=2, AOD=3), condiviso dai mod che
 * iniettano contenuti nel contenitore dell'orologio OEM (orologio, meteo, widget).
 *
 * Fino a OOS 16.0 la notifica arrivava da OplusKeyguardStyleClock.onUiStateChanged(int); con
 * OOS 16.1 quella classe è un semplice FrameLayout senza il metodo e la notifica passa da
 * ThemePlugin.onUiStateChanged(int, boolean). hookAllMethods() su un metodo inesistente non dà
 * errore: i mod restavano sempre in "Schermata di Blocco" e i loro contenuti comparivano
 * (rimpiccioliti) anche nell'AOD "Workshop". Si agganciano quindi entrambi i punti, e in più si
 * considera AOD qualunque momento in cui lo schermo non è interattivo.
 */
public final class KeyguardUiState {
    public static final int AOD = 3;
    /** Schermo spento con AOD disegnato dal suo layout (AodClockLayout): i contenuti iniettati
     *  nel contenitore della lockscreen non vanno mostrati, sarebbero doppioni. */
    public static final int SUPPRESSED = -1;

    private static final java.util.concurrent.CopyOnWriteArrayList<Runnable> sRefreshers =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    private KeyguardUiState() {}

    private static volatile java.lang.ref.WeakReference<android.view.View> sAodLayout;

    /** Memorizza l'AodClockLayout reale di OOS (chiamato dai suoi hook): serve a sapere se in
     *  questo momento l'AOD è disegnato dal suo layout. */
    public static void registerAodLayout(Object layout) {
        if (layout instanceof android.view.View v) {
            java.lang.ref.WeakReference<android.view.View> cur = sAodLayout;
            if (cur == null || cur.get() != v) sAodLayout = new java.lang.ref.WeakReference<>(v);
        }
    }

    public static android.view.View getAodLayout() {
        java.lang.ref.WeakReference<android.view.View> r = sAodLayout;
        return r == null ? null : r.get();
    }

    private static boolean aodLayoutVisible() {
        java.lang.ref.WeakReference<android.view.View> r = sAodLayout;
        android.view.View v = r == null ? null : r.get();
        return v != null && v.getVisibility() == android.view.View.VISIBLE && v.isAttachedToWindow();
    }

    /** Stato da usare al posto di quello notificato da OOS: tiene conto di schermo spento/AOD. */
    public static int effective(Context ctx, int uiState) {
        if (!isDozing(ctx)) return uiState;
        return aodLayoutVisible() ? SUPPRESSED : AOD;
    }

    public static void hook(ClassLoader cl, IntConsumer onState) {
        String[] classes = {
                "com.oplus.keyguard.OplusKeyguardStyleClock",   // OOS <= 16.0
                "com.oplus.keyguard.plugin.ThemePlugin"         // OOS 16.1
        };
        for (String name : classes) {
            try {
                Class<?> c = XposedHelpers.findClassIfExists(name, cl);
                if (c == null) continue;
                int n = XposedBridge.hookAllMethods(c, "onUiStateChanged", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (p.args.length > 0 && p.args[0] instanceof Integer) {
                            onState.accept((Integer) p.args[0]);
                        }
                    }
                }).size();
                XposedBridge.log("[ Obsidian ] KeyguardUiState: " + name + ".onUiStateChanged hooks=" + n);
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] KeyguardUiState: hook " + name + " failed: " + t);
            }
        }
    }

    /** Schermo non interattivo = spento o AOD: lì i contenuti della lockscreen non vanno mostrati. */
    public static boolean isDozing(Context ctx) {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            return pm != null && !pm.isInteractive();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Richiama {@code refresh} quando lo schermo si accende o si spegne (entra/esce l'AOD). */
    public static void watchScreen(Context ctx, Runnable refresh) {
        sRefreshers.add(refresh);
        try {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_SCREEN_OFF);
            ctx.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    try { refresh.run(); } catch (Throwable ignored) {}
                    if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                        // il layout dell'AOD compare con ritardo: si ricontrolla nei primi secondi
                        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
                        for (long d : new long[]{600, 1300, 2200, 3500}) h.postDelayed(refresh, d);
                    }
                }
            }, f, Context.RECEIVER_EXPORTED);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] KeyguardUiState: watchScreen failed: " + t);
        }
    }

    /** Diagnostica AOD: scrive nel log di LSPosed l'albero delle view del contenitore (e dei suoi
     *  genitori) con visibilità, alpha, scala e dimensioni — per capire cosa lascia visibile
     *  l'AOD. Solo log, non modifica nulla. */
    public static void dump(String label, android.view.View container) {
        try {
            StringBuilder sb = new StringBuilder("KeyguardUiState.dump[").append(label).append("]\n");
            android.view.View p = container;
            int up = 0;
            while (p != null && up < 5) {
                sb.append("  ^").append(up).append(' ').append(describe(p)).append('\n');
                p = p.getParent() instanceof android.view.View ? (android.view.View) p.getParent() : null;
                up++;
            }
            walk(container, 0, sb);
            XposedBridge.log("[ Obsidian ] " + sb);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] KeyguardUiState.dump failed: " + t);
        }
    }

    private static void walk(android.view.View v, int depth, StringBuilder sb) {
        if (depth > 4) return;
        for (int i = 0; i < 40 && v instanceof android.view.ViewGroup g && i < g.getChildCount(); i++) {
            android.view.View c = g.getChildAt(i);
            sb.append("  ").append("  ".repeat(depth + 1)).append(describe(c)).append('\n');
            walk(c, depth + 1, sb);
        }
    }

    private static String describe(android.view.View v) {
        String vis = v.getVisibility() == android.view.View.VISIBLE ? "V" : v.getVisibility() == android.view.View.INVISIBLE ? "I" : "G";
        return v.getClass().getSimpleName() + " tag=" + v.getTag() + " vis=" + vis + " a=" + v.getAlpha()
                + " s=" + v.getScaleX() + "x" + v.getScaleY() + " " + v.getWidth() + "x" + v.getHeight()
                + "@" + (int) v.getX() + "," + (int) v.getY() + " scr=" + screenPos(v) + (v.isShown() ? " SHOWN" : "");
    }

    private static String screenPos(android.view.View v) {
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        return loc[0] + "," + loc[1];
    }

    /** Diagnostica: a ogni spegnimento schermo scrive nel log l'albero dell'AodClockLayout di OOS
     *  (posizioni a schermo) dopo 1, 2,5 e 5 secondi, per misurare dove finisce il blocco AOD. */
    public static void dumpAodLayoutOnScreenOff(Context ctx) {
        try {
            ctx.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
                    for (long d : new long[]{1000, 2500, 5000}) {
                        h.postDelayed(() -> {
                            java.lang.ref.WeakReference<android.view.View> r = sAodLayout;
                            android.view.View v = r == null ? null : r.get();
                            if (v == null) { XposedBridge.log("[ Obsidian ] AOD dump: no AodClockLayout instance"); return; }
                            dump("AodClockLayout +" + d + "ms", v);
                        }, d);
                    }
                }
            }, new IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_EXPORTED);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] dumpAodLayoutOnScreenOff failed: " + t);
        }
    }
}
