package it.tugaia56.obsidian.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedBridge.hookAllConstructors;
import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import java.util.Collection;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Real OC mechanism (StatusbarNotification.java), ported feature by feature:
 *  - notification app icon (real launcher icon instead of the small status icon)
 *  - default expansion mode (Default / Espandi sempre / Comprimi sempre — "Scelta per
 *    app" mode 3 has no per-app picker UI in Obsidian yet, treated as Default)
 *  - clear-all button customization (background/icon colour, optionally linked to accent)
 *
 * NOT ported: the expand/collapse-all button pair injected into OplusQSSimpleHeader —
 * that requires new drawable assets + an OOS-specific view-id lookup + (on SDK 36) a
 * PanelAnimationListener subsystem Obsidian doesn't have yet. Left as UI-only for now.
 */
public class NotificationMods extends XposedMods {

    private static final int DEFAULT = 0;
    private static final int EXPAND_ALWAYS = 1;
    private static final int COLLAPSE_ALWAYS = 2;

    private static final String PREF_APP_ICON        = "statusbar_notification_app_icon";
    private static final String PREF_APP_ICON_SCALE  = "statusbar_notification_app_icon_scale";
    private static final String PREF_DEFAULT_EXPANSION = "notificationDefaultExpansion";
    private static final String PREF_CUSTOMIZE_CLEAR = "customizeClearButton";
    private static final String PREF_LINK_BG_ACCENT   = "linkBackgroundAccent";
    private static final String PREF_CLEAR_BG_COLOR   = "clearButtonBgColor";
    private static final String PREF_LINK_ICON_ACCENT  = "linkIconAccent";
    private static final String PREF_CLEAR_ICON_COLOR  = "clearButtonIconColor";
    private static final String PREF_ACCENT1           = "DST_ACCENT1";
    private static final String PREF_HIDE_CLEAR_ALL    = "OBS_NOTIF_HIDE_CLEAR_ALL";

    private boolean mAppIconOn = false;
    private float mAppIconScale = 1f;
    private int mDefaultExpansion = DEFAULT;
    private boolean mCustomizeClear = false;
    private boolean mHideClearAll = false;
    private boolean mLinkBgAccent = false;
    private boolean mLinkIconAccent = false;
    private int mClearBgColor = 0xFF8C8C8C;
    private int mClearIconColor = 0xFFFFFFFF;
    private int mAccent = 0xFF9C27B0;

    private Object mScroller = null;
    private Object mNotifCollection = null;
    private ImageView mClearAllButton = null;
    private Drawable mDefaultClearIcon = null;
    private Drawable mDefaultClearBg = null;

    public NotificationMods(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mAppIconOn = Xprefs.getBoolean(PREF_APP_ICON, false);
        mAppIconScale = Xprefs.getSliderInt(PREF_APP_ICON_SCALE, 100) / 100f;
        try {
            int expansion = Integer.parseInt(Xprefs.getString(PREF_DEFAULT_EXPANSION, "0"));
            mDefaultExpansion = (expansion == EXPAND_ALWAYS || expansion == COLLAPSE_ALWAYS) ? expansion : DEFAULT;
        } catch (Throwable ignored) { mDefaultExpansion = DEFAULT; }

        mHideClearAll    = Xprefs.getBoolean(PREF_HIDE_CLEAR_ALL, false);
        mCustomizeClear  = Xprefs.getBoolean(PREF_CUSTOMIZE_CLEAR, false);
        mLinkBgAccent    = Xprefs.getBoolean(PREF_LINK_BG_ACCENT, false);
        mLinkIconAccent  = Xprefs.getBoolean(PREF_LINK_ICON_ACCENT, false);
        mClearBgColor    = Xprefs.getInt(PREF_CLEAR_BG_COLOR, 0xFF8C8C8C);
        mClearIconColor  = Xprefs.getInt(PREF_CLEAR_ICON_COLOR, 0xFFFFFFFF);
        int accent = Xprefs.getInt(PREF_ACCENT1, 0);
        if (accent != 0) mAccent = accent;

        if (Key.length > 0) {
            switch (Key[0]) {
                case PREF_CUSTOMIZE_CLEAR, PREF_LINK_BG_ACCENT, PREF_CLEAR_BG_COLOR,
                     PREF_LINK_ICON_ACCENT, PREF_CLEAR_ICON_COLOR -> { updateClearButton(); refreshFooter(); }
            }
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!SYSTEM_UI.equals(lp.packageName)) return;

        hookAppIcon(lp);
        hookExpansion(lp);
        hookClearButton(lp);
        if (!it.tugaia56.obsidian.xposed.XPLauncher.isOplusRom()) hookFooter(lp);
    }

    // ── App icon ──────────────────────────────────────────────────────────────

    private void hookAppIcon(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> statusBarIconView;
        try {
            statusBarIconView = findClass("com.android.systemui.statusbar.StatusBarIconView", lp.classLoader);
        } catch (Throwable t) { return; }

        hookAppIconUntint(statusBarIconView);
        hookAllMethods(statusBarIconView, "getIcon", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!mAppIconOn) return;
                try {
                    Object statusBarIcon = null;
                    for (Object a : param.args) {
                        if (a != null && a.getClass().getSimpleName().equals("StatusBarIcon")) { statusBarIcon = a; break; }
                    }
                    if (statusBarIcon == null) return;
                    String pkg = (String) getObjectField(statusBarIcon, "pkg");
                    if (pkg == null || pkg.startsWith("com.android") || pkg.equals(SYSTEM_UI)) return;

                    PackageManager pm = mContext.getPackageManager();
                    Drawable appIcon = pm.getApplicationIcon(pkg);
                    int size = Math.round(64 * mAppIconScale
                            * mContext.getResources().getDisplayMetrics().density / 3f);
                    if (size <= 0) return;
                    Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(bmp);
                    appIcon.setBounds(0, 0, size, size);
                    appIcon.draw(c);
                    param.setResult(new AppIconDrawable(mContext.getResources(), bmp));
                } catch (Throwable ignored) {}
            }
        });
    }

    /** Icona dell'app usata al posto di quella piccola della notifica: marcata per poterle togliere la tinta (vedi sotto). */
    private static final class AppIconDrawable extends BitmapDrawable {
        AppIconDrawable(android.content.res.Resources r, Bitmap b) { super(r, b); }
    }

    /**
     * Android puro (RisingOS): SystemUI colora di bianco ogni icona della barra di stato (tinta + filtro colore), e l'icona
     * dell'app, a colori e non trasparente, diventa un cerchio bianco. Alle sole icone create qui si toglie tinta e filtro.
     * Su OxygenOS non serve (li' l'icona resta a colori) e non si tocca niente.
     */
    private void hookAppIconUntint(Class<?> sbiv) {
        if (it.tugaia56.obsidian.xposed.XPLauncher.isOplusRom()) return;
        try {
            hookAllMethods(ImageView.class, "setImageTintList", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (sbiv.isInstance(p.thisObject) && ((ImageView) p.thisObject).getDrawable() instanceof AppIconDrawable) p.args[0] = null;
                    } catch (Throwable ignored) {}
                }
            });
            hookAllMethods(ImageView.class, "setImageDrawable", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        if (sbiv.isInstance(p.thisObject) && p.args[0] instanceof AppIconDrawable) {
                            ((ImageView) p.thisObject).setImageTintList(null);
                            ((ImageView) p.thisObject).clearColorFilter();
                        }
                    } catch (Throwable ignored) {}
                }
            });
            XC_MethodHook noTint = new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (p.thisObject instanceof AppIconDrawable) p.setResult(null);
                }
            };
            hookAllMethods(BitmapDrawable.class, "setColorFilter", noTint);
            hookAllMethods(BitmapDrawable.class, "setTintList", noTint);
            hookAllMethods(BitmapDrawable.class, "setTint", noTint);
        } catch (Throwable t) { log("[ Obsidian ] NotificationMods app icon untint: " + t); }
    }

    // ── Default expansion ────────────────────────────────────────────────────

    private void hookExpansion(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> notifCollection = tryFindClass(lp,
                "com.android.systemui.statusbar.notification.collection.NotifCollection");
        if (notifCollection != null) {
            hookAllConstructors(notifCollection, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) { mNotifCollection = param.thisObject; }
            });
        }

        Class<?> scrollLayout = tryFindClass(lp,
                "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout");
        if (scrollLayout != null) {
            hookAllConstructors(scrollLayout, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) { mScroller = param.thisObject; }
            });
        }

        Class<?> panelController = tryFindClass(lp,
                "com.android.systemui.shade.NotificationPanelViewController",
                "com.android.systemui.statusbar.phone.NotificationPanelViewController");
        if (panelController != null) {
            hookAllMethods(panelController, "notifyExpandingStarted", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (mDefaultExpansion != DEFAULT) expandAll(mDefaultExpansion);
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    private void expandAll(int expandMode) {
        if (mNotifCollection == null) return;
        try {
            if (expandMode != EXPAND_ALWAYS && mScroller != null) {
                callMethod(mScroller, "setOwnScrollY", 0, true);
            }
            Collection<Object> entries = (Collection<Object>) getObjectField(mNotifCollection, "mReadOnlyNotificationSet");
            for (Object entry : entries.toArray()) {
                Object row = getObjectField(entry, "row");
                if (row == null) continue;
                callMethod(row, "setUserExpanded", expandMode == EXPAND_ALWAYS, true);
            }
        } catch (Throwable ignored) {}
    }

    // ── Clear-all button ──────────────────────────────────────────────────────

    private void hookClearButton(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> clearAllButton = tryFindClass(lp,
                "com.oplus.systemui.notification.clearall.OplusClearAllButton",
                "com.oplus.systemui.statusbar.notification.view.OplusClearAllButton",
                "com.oplusos.systemui.notification.view.OplusClearAllButton");
        if (clearAllButton != null) {
            hookAllConstructors(clearAllButton, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof ImageView)) return;
                    mClearAllButton = (ImageView) param.thisObject;
                    if (mDefaultClearIcon == null) mDefaultClearIcon = mClearAllButton.getDrawable();
                    if (mDefaultClearBg == null) mDefaultClearBg = mClearAllButton.getBackground();
                    updateClearButton();
                    if (mHideClearAll) mClearAllButton.setVisibility(android.view.View.GONE);
                    mClearAllButton.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or_, ob) -> {
                        if (mHideClearAll && v.getVisibility() != android.view.View.GONE) {
                            v.setVisibility(android.view.View.GONE);   // il sistema lo rimostra di continuo
                            return;
                        }
                        if (v.getVisibility() == android.view.View.VISIBLE) updateClearButton();
                    });
                }
            });
        }

        Class<?> clearAllController = tryFindClass(lp,
                "com.oplus.systemui.notification.clearall.ClearAllController");
        if (clearAllController != null) {
            try {
                hookAllMethods(clearAllController, "getPlatformBlurDrawable", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!mCustomizeClear) return;
                        param.setResult(new ColorDrawable(mLinkBgAccent ? mAccent : mClearBgColor));
                    }
                });
            } catch (Throwable ignored) {}
            // OOS 16.0.10: sopra al fondo c'e' anche un colore "maschera" del pulsante, che va cambiato insieme
            try {
                hookAllMethods(clearAllController, "getClearAllMaskOverlayColor", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (!mCustomizeClear) return;
                        param.setResult(mLinkBgAccent ? mAccent : mClearBgColor);
                    }
                });
            } catch (Throwable ignored) {}
            try {
                hookAllMethods(clearAllController, "updateClearAllBackground", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) { updateClearButton(); }
                });
            } catch (Throwable ignored) {}
        }
    }

    private void updateClearButton() {
        if (mClearAllButton == null) return;
        if (mCustomizeClear) {
            int bg = mLinkBgAccent ? mAccent : mClearBgColor;
            int icon = mLinkIconAccent ? mAccent : mClearIconColor;
            mClearAllButton.setBackground(new ColorDrawable(bg));
            mClearAllButton.setImageTintList(ColorStateList.valueOf(icon));
        } else {
            mClearAllButton.setImageTintList(null);
            if (mDefaultClearIcon != null) mClearAllButton.setImageDrawable(mDefaultClearIcon);
            if (mDefaultClearBg != null) mClearAllButton.setBackground(mDefaultClearBg);
        }
    }

    // ── Pulsanti in fondo alle notifiche (Android puro, RisingOS) ───────────────────────────────

    private java.lang.ref.WeakReference<Object> mFooter;

    /** Accento del Theme (persist.oxytheme.accent) se c'e', altrimenti quello delle preferenze. */
    private int footerAccent() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            String v = String.valueOf(de.robv.android.xposed.XposedHelpers.callStaticMethod(sp, "get", "persist.oxytheme.accent", "")).trim();
            if (v.length() >= 6) return 0xFF000000 | (int) Long.parseLong(v.substring(v.length() - 6), 16);
        } catch (Throwable ignored) {}
        return mAccent;
    }

    /** FooterView di SystemUI: i pulsanti "Cancella tutto", "Gestisci" e "Cronologia" sono scuri. Con "Personalizza pulsante"
     *  acceso prendono sfondo (tinta, cosi' la forma resta) e testo scelti. */
    private void hookFooter(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> fv = tryFindClass(lp, "com.android.systemui.statusbar.notification.footer.ui.view.FooterView");
        if (fv == null) { log("[ Obsidian ] NotificationMods: FooterView not found"); return; }
        XC_MethodHook h = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                mFooter = new java.lang.ref.WeakReference<>(p.thisObject);
                applyFooter(p.thisObject);
            }
        };
        for (String m : new String[]{"onFinishInflate", "updateColors", "updateColors$2", "onConfigurationChanged"}) {
            try { hookAllMethods(fv, m, h); } catch (Throwable ignored) {}
        }
        log("[ Obsidian ] NotificationMods: FooterView hooked");
    }

    private void refreshFooter() {
        Object f = mFooter != null ? mFooter.get() : null;
        if (f instanceof android.view.View) ((android.view.View) f).post(() -> applyFooter(f));
    }

    private void applyFooter(Object footer) {
        if (!mCustomizeClear) return;
        int bg = mLinkBgAccent ? footerAccent() : mClearBgColor;
        int fg = mLinkIconAccent ? footerAccent() : mClearIconColor;
        for (String field : new String[]{"mClearAllButton", "mSettingsButton", "mHistoryButton"}) {
            try {
                Object b = getObjectField(footer, field);
                if (!(b instanceof android.view.View)) continue;
                android.view.View v = (android.view.View) b;
                v.setBackgroundTintList(ColorStateList.valueOf(bg));
                if (v instanceof android.widget.TextView) {
                    android.widget.TextView tv = (android.widget.TextView) v;
                    tv.setTextColor(fg);
                    tv.setCompoundDrawableTintList(ColorStateList.valueOf(fg));
                }
            } catch (Throwable ignored) {}
        }
    }

    // ── Utility ──────────────────────────────────────────────────────────────

    private Class<?> tryFindClass(XC_LoadPackage.LoadPackageParam lp, String... names) {
        for (String name : names) {
            try { return findClass(name, lp.classLoader); } catch (Throwable ignored) {}
        }
        return null;
    }

    @Override public boolean listensTo(String packageName) { return SYSTEM_UI.equals(packageName); }
}
