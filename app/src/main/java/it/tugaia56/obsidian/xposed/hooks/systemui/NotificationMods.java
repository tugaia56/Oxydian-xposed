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
                     PREF_LINK_ICON_ACCENT, PREF_CLEAR_ICON_COLOR -> updateClearButton();
            }
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!SYSTEM_UI.equals(lp.packageName)) return;

        hookAppIcon(lp);
        hookExpansion(lp);
        hookClearButton(lp);
    }

    // ── App icon ──────────────────────────────────────────────────────────────

    private void hookAppIcon(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> statusBarIconView;
        try {
            statusBarIconView = findClass("com.android.systemui.statusbar.StatusBarIconView", lp.classLoader);
        } catch (Throwable t) { return; }

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
                    param.setResult(new BitmapDrawable(mContext.getResources(), bmp));
                } catch (Throwable ignored) {}
            }
        });
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

    // ── Utility ──────────────────────────────────────────────────────────────

    private Class<?> tryFindClass(XC_LoadPackage.LoadPackageParam lp, String... names) {
        for (String name : names) {
            try { return findClass(name, lp.classLoader); } catch (Throwable ignored) {}
        }
        return null;
    }

    @Override public boolean listensTo(String packageName) { return SYSTEM_UI.equals(packageName); }
}
