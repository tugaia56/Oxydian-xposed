package it.tugaia56.obsidian.xposed.hooks.contacts;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Google Contatti. L'app dichiara una sola risorsa "overlayable" (e non e' un colore), quindi gli
 * overlay di Oxydian Theme non possono cambiarla (STATE_NO_IDMAP: "no resources were overlaid").
 * Si lavora quindi da dentro l'app:
 *  - colori di sistema dell'app (sfondo Blue Gray Dark, pulsante + e scheda selezionata
 *    nell'accento) con le sostituzioni risorse di Xposed (applyResources);
 *  - card dell'elenco (ListItemContainmentView, una MaterialCardView): il sorgente e' offuscato,
 *    quindi non si chiamano i suoi metodi ma si disegna sopra: sfondo prima dei figli, bordo dopo.
 *
 * La scelta delle card (con/senza bordo) arriva da una proprieta' di sistema, perche' Contatti non
 * vede le preferenze di Oxydian. Serve la spunta su Contatti nell'ambito di Oxydian in LSPosed.
 */
public class ContactsMod extends XposedMods {

    public static final String PKG = "com.google.android.contacts";
    public static final String PROP_BORDER = "persist.obsidian.ct_border";   // "1" con bordo, "0" senza bordo (predefinito)
    private static final String CARD = "com.google.android.apps.contacts.common.ui.containment.ListItemContainmentView";
    private static final int BLUE_GRAY_DARK = 0xFF1B2029;
    private static final float CARD_RADIUS_DP = 4f;

    private volatile boolean mBorder = false;
    private volatile int mAccent = 0xFF908DFF;
    private volatile int mFillNoBorder = 0xFF2B2F3A;
    private long mPropsAt = 0;
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    public ContactsMod(Context context) {
        super(context);
        mStroke.setStyle(Paint.Style.STROKE);
    }

    @Override
    public void updatePrefs(String... Key) {}

    @Override
    public boolean listensTo(String packageName) {
        return PKG.equals(packageName);
    }

    private static String prop(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            return (String) XposedHelpers.callStaticMethod(sp, "get", key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    /** Accento scelto dall'utente (Oxydian lo scrive in una proprieta' leggibile da ogni processo). */
    static int accent() {
        try {
            String a1 = prop("persist.obsidian.dst.a1", "");
            if (!a1.isEmpty()) return 0xFF000000 | Integer.parseInt(a1);
        } catch (NumberFormatException ignored) {}
        try {
            Resources r = Resources.getSystem();
            int id = r.getIdentifier("accent_material_dark", "color", "android");
            if (id != 0) return r.getColor(id, null);
        } catch (Throwable ignored) {}
        return 0xFF908DFF;
    }

    private void refreshProps() {
        long now = SystemClock.elapsedRealtime();
        if (now - mPropsAt < 2000) return;
        mPropsAt = now;
        mBorder = "1".equals(prop(PROP_BORDER, "0"));
        mAccent = accent();
        try {
            Resources r = Resources.getSystem();
            int id = r.getIdentifier("button_material_dark", "color", "android");
            if (id != 0) mFillNoBorder = r.getColor(id, null);
        } catch (Throwable ignored) {}
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        final Class<?> card = XposedHelpers.findClassIfExists(CARD, lpparam.classLoader);
        if (card == null) {
            XposedBridge.log("[ Obsidian ] ContactsMod: " + CARD + " not found");
            return;
        }
        java.lang.reflect.Method dispatchDraw = ViewGroup.class.getDeclaredMethod("dispatchDraw", Canvas.class);
        XposedBridge.hookMethod(dispatchDraw, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (param.thisObject.getClass() != card) return;
                try { paint((View) param.thisObject, (Canvas) param.args[0], true); } catch (Throwable t) { XposedBridge.log("[ Obsidian ] ContactsMod fill: " + t); }
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.thisObject.getClass() != card) return;
                try { paint((View) param.thisObject, (Canvas) param.args[0], false); } catch (Throwable t) { XposedBridge.log("[ Obsidian ] ContactsMod stroke: " + t); }
            }
        });
        XposedBridge.log("[ Obsidian ] ContactsMod: hooked " + CARD);
    }

    /** before=true: sfondo (sopra quello della card, sotto il contenuto); false: bordo sopra a tutto. */
    private void paint(View v, Canvas c, boolean before) {
        refreshProps();
        float d = v.getResources().getDisplayMetrics().density;
        float rad = CARD_RADIUS_DP * d;
        if (before) {
            mFill.setStyle(Paint.Style.FILL);
            mFill.setColor(mBorder ? BLUE_GRAY_DARK : mFillNoBorder);
            mRect.set(0, 0, v.getWidth(), v.getHeight());
            c.drawRoundRect(mRect, rad, rad, mFill);
        } else if (mBorder) {
            float w = Math.max(1f, d);   // 1 dp
            mStroke.setStrokeWidth(w);
            mStroke.setColor(mAccent);
            mRect.set(w / 2f, w / 2f, v.getWidth() - w / 2f, v.getHeight() - w / 2f);
            c.drawRoundRect(mRect, rad, rad, mStroke);
        }
    }

    /** Sostituzioni delle risorse colore dell'app (sfondo, pulsante +, scheda selezionata). */
    public static void applyResources(XC_InitPackageResources.InitPackageResourcesParam resparam) {
        if (!PKG.equals(resparam.packageName)) return;
        final int bg = BLUE_GRAY_DARK, acc = accent(), white = 0xFFFFFFFF;
        String[][] map = {
                {"background", "b"}, {"surface", "b"}, {"surface_bright", "b"}, {"surface_dim", "b"},
                {"surface_container", "b"}, {"surface_container_low", "b"}, {"surface_container_lowest", "b"},
                {"surface_container_high", "b"}, {"surface_container_highest", "b"}, {"surface_variant", "b"},
                {"primary_container", "a"}, {"secondary_container", "a"},
                {"on_primary_container", "w"}, {"on_secondary_container", "w"},
        };
        for (String[] m : map) {
            int value = "b".equals(m[1]) ? bg : "a".equals(m[1]) ? acc : white;
            try {
                resparam.res.setReplacement(PKG, "color", "gm3_sys_color_dynamic_dark_" + m[0], value);
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] ContactsMod res " + m[0] + ": " + t);
            }
        }
        XposedBridge.log("[ Obsidian ] ContactsMod: colour replacements registered");
    }
}
