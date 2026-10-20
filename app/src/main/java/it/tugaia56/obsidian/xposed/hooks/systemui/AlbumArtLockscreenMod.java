package it.tugaia56.obsidian.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;
import it.tugaia56.obsidian.xposed.utils.DrawableConverter;

/**
 * Lockscreen "Copertina Album" (mirrors OC's AlbumArtLockscreen): shows the currently
 * playing track's artwork as a full-screen layer behind the lock screen, with an
 * optional filter (grayscale / accent tint / blur / grayscale+blur).
 *
 * View injection reuses the same scrim-lookup technique already proven by
 * {@link QsBackground} (ScrimController.updateScrimColor, mScrimBehind field —
 * present in both statusbar.phone (≤Android 15) and shade (Android 16+) packages),
 * rather than porting OC's separate ScrimControllerEx wrapper.
 *
 * Media session tracking is a trimmed-down version of OC's AudioDataProvider:
 * only what's needed to get the current bitmap + playback state, no transport
 * controls / Monet colour-scheme machinery. The "Colore Accento" filter uses
 * Obsidian's own DST_ACCENT1 user preference instead of wallpaper-derived colours.
 */
public class AlbumArtLockscreenMod extends XposedMods {

    private static final String PREF_ENABLED = "lockscreen_album_art";
    private static final String PREF_FILTER  = "lockscreen_album_art_filter"; // "0".."4"
    private static final String PREF_BLUR    = "lockscreen_media_blur";      // 0-100 slider

    private static final int FILTER_NONE      = 0;
    private static final int FILTER_GRAYSCALE = 1;
    private static final int FILTER_ACCENT    = 2;
    private static final int FILTER_BLUR      = 3;
    private static final int FILTER_GRAY_BLUR = 4;

    private boolean mShowAlbumArt   = false;
    private int     mFilter         = FILTER_NONE;
    private float   mBlurRadius     = 7.5f;
    private int     mAccentColor    = 0xFF908DFF;

    private boolean mKeyguardShowing = false;

    private FrameLayout albumArtContainer;
    private ImageView   albumArtView;
    private boolean     mInjected = false;

    private MediaSessionManager mMediaSessionManager;
    private MediaController     mActiveController;
    private Bitmap              mRawArt;
    private int                 mLastPlaybackState = PlaybackState.STATE_NONE;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable pollTask = new Runnable() {
        @Override public void run() {
            updateActiveController();
            handler.postDelayed(this, 1000);
        }
    };

    private final MediaController.Callback mediaControllerCallback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) {
            refreshFromMetadata(metadata, mActiveController != null
                    ? mActiveController.getPlaybackState() : null);
        }
        @Override public void onPlaybackStateChanged(PlaybackState state) {
            refreshFromMetadata(mActiveController != null
                    ? mActiveController.getMetadata() : null, state);
        }
    };

    public AlbumArtLockscreenMod(Context context) { super(context); }

    @Override
    public void updatePrefs(String... key) {
        if (Xprefs == null) return;
        mShowAlbumArt = Xprefs.getBoolean(PREF_ENABLED, false);
        mFilter       = parseFilter(Xprefs.getString(PREF_FILTER, "0"));
        mBlurRadius   = (Xprefs.getInt(PREF_BLUR, 30) / 100f) * 25f;
        boolean accentOn = Xprefs.getBoolean("DST_ACCENT1_on", false);
        mAccentColor = accentOn ? (Xprefs.getInt("DST_ACCENT1", mAccentColor) | 0xFF000000) : it.tugaia56.obsidian.xposed.utils.AccentUtil.systemAccent();

        // Re-render whatever's currently playing with the new filter/enabled state.
        applyArt();
    }

    private int parseFilter(String s) {
        try {
            int v = Integer.parseInt(s);
            return (v >= FILTER_NONE && v <= FILTER_GRAY_BLUR) ? v : FILTER_NONE;
        } catch (Throwable t) { return FILTER_NONE; }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!SYSTEM_UI.equals(lp.packageName)) return;

        mMediaSessionManager = (MediaSessionManager)
                mContext.getSystemService(Context.MEDIA_SESSION_SERVICE);

        hookScrimInjection(lp);

        handler.post(pollTask);
    }

    // ── View injection + keyguard-showing detection ─────────────────────────────
    // Both reuse QsBackground's proven ScrimController.updateScrimColor hook:
    // mScrimBehind's parent for injection, mState (contains "KEYGUARD"/"BOUNCER")
    // for lock-state — no separate NotificationShadeWindowController hook needed
    // (that class doesn't exist under any tried name on this OOS16 build).

    private void hookScrimInjection(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> scrimControllerClass = findScrimControllerClass(lp.classLoader);
        if (scrimControllerClass == null) {
            dbg("ScrimController not found — album art view cannot be injected");
            return;
        }
        hookAllMethods(scrimControllerClass, "updateScrimColor", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!mInjected) {
                    try {
                        Field fBehind = findField(scrimControllerClass, "mScrimBehind");
                        View scrimBehind = (View) fBehind.get(p.thisObject);
                        if (scrimBehind != null && scrimBehind.getParent() instanceof ViewGroup) {
                            ViewGroup rootView = (ViewGroup) scrimBehind.getParent();
                            injectAlbumArtView(rootView);
                            mInjected = true;
                            dbg("album art container injected into " + rootView.getClass().getSimpleName());
                        }
                    } catch (Throwable t) {
                        dbg("scrim injection failed: " + t);
                    }
                }
                try {
                    Object stateObj = getObjectField(p.thisObject, "mState");
                    boolean showing = stateObj != null
                            && (stateObj.toString().contains("KEYGUARD") || stateObj.toString().contains("BOUNCER"));
                    if (showing != mKeyguardShowing) {
                        mKeyguardShowing = showing;
                        updateAlbumArtVisibility();
                    }
                } catch (Throwable ignored) {}
            }
        });
    }

    private void injectAlbumArtView(ViewGroup rootView) {
        albumArtContainer = new FrameLayout(mContext);
        albumArtContainer.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        albumArtContainer.setVisibility(View.GONE);

        albumArtView = new ImageView(mContext);
        albumArtView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        albumArtView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        albumArtContainer.addView(albumArtView);

        // Bottom-most layer: scrims / notifications / clock all draw on top of it.
        rootView.addView(albumArtContainer, 0);
    }

    private Class<?> findScrimControllerClass(ClassLoader cl) {
        try { return findClass("com.android.systemui.statusbar.phone.ScrimController", cl); }
        catch (Throwable ignored) {}
        try { return findClass("com.android.systemui.shade.ScrimController", cl); }
        catch (Throwable ignored) {}
        return null;
    }

    // ── Media session tracking (trimmed AudioDataProvider) ─────────────────────

    private void updateActiveController() {
        MediaController controller = getActiveLocalMediaController();
        if (controller != null && !sameSession(mActiveController, controller)) {
            if (mActiveController != null) mActiveController.unregisterCallback(mediaControllerCallback);
            mActiveController = controller;
            mActiveController.registerCallback(mediaControllerCallback);
            refreshFromMetadata(controller.getMetadata(), controller.getPlaybackState());
        } else if (controller == null && mActiveController != null) {
            mActiveController.unregisterCallback(mediaControllerCallback);
            mActiveController = null;
            refreshFromMetadata(null, null);
        }
    }

    private boolean sameSession(MediaController a, MediaController b) {
        return a != null && a.equals(b);
    }

    private MediaController getActiveLocalMediaController() {
        if (mMediaSessionManager == null) return null;
        List<String> remotePackages = new ArrayList<>();
        MediaController local = null;
        try {
            for (MediaController c : mMediaSessionManager.getActiveSessions(null)) {
                MediaController.PlaybackInfo info = c.getPlaybackInfo();
                PlaybackState state = c.getPlaybackState();
                if (info == null || state == null || state.getState() != PlaybackState.STATE_PLAYING) continue;
                if (info.getPlaybackType() == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE) {
                    if (local != null && local.getPackageName().equals(c.getPackageName())) local = null;
                    if (!remotePackages.contains(c.getPackageName())) remotePackages.add(c.getPackageName());
                } else if (local == null && !remotePackages.contains(c.getPackageName())) {
                    local = c;
                }
            }
        } catch (Throwable t) {
            dbg("getActiveSessions failed: " + t);
        }
        return local;
    }

    private void refreshFromMetadata(MediaMetadata metadata, PlaybackState state) {
        Bitmap art = null;
        if (metadata != null) {
            art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (art == null) art = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        }
        mRawArt = art;
        mLastPlaybackState = (state != null) ? state.getState() : PlaybackState.STATE_NONE;
        applyArt();
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    private boolean canShowArt() {
        return mRawArt != null && (mLastPlaybackState == PlaybackState.STATE_PLAYING
                || mLastPlaybackState == PlaybackState.STATE_BUFFERING);
    }

    private void applyArt() {
        if (albumArtView == null) return;
        if (mShowAlbumArt && canShowArt()) {
            Bitmap filtered = applyFilter(mRawArt);
            Drawable current = albumArtView.getDrawable();
            Drawable next = new BitmapDrawable(mContext.getResources(), filtered);
            if (current != null) {
                TransitionDrawable transition =
                        new TransitionDrawable(new Drawable[]{current, next});
                albumArtView.post(() -> {
                    albumArtView.setImageDrawable(transition);
                    transition.startTransition(250);
                });
            } else {
                albumArtView.post(() -> albumArtView.setImageDrawable(next));
            }
        } else {
            albumArtView.post(() -> albumArtView.setImageBitmap(null));
        }
        updateAlbumArtVisibility();
    }

    private Bitmap applyFilter(Bitmap art) {
        try {
            switch (mFilter) {
                case FILTER_GRAYSCALE: return DrawableConverter.toGrayscale(art);
                case FILTER_ACCENT:
                    return DrawableConverter.getColoredBitmap(
                            new BitmapDrawable(mContext.getResources(), art), mAccentColor);
                case FILTER_BLUR: return DrawableConverter.getBlurredImage(mContext, art, mBlurRadius);
                case FILTER_GRAY_BLUR: return DrawableConverter.getGrayscaleBlurredImage(mContext, art, mBlurRadius);
                default: return art;
            }
        } catch (Throwable t) {
            dbg("applyFilter failed: " + t);
            return art;
        }
    }

    private void updateAlbumArtVisibility() {
        if (albumArtContainer == null) return;
        boolean visible = mShowAlbumArt && mKeyguardShowing && canShowArt();
        albumArtContainer.post(() ->
                albumArtContainer.setVisibility(visible ? View.VISIBLE : View.GONE));
    }

    private static void dbg(String msg) {
        Log.d("ObsidianAlbumArt", msg);
        XposedBridge.log("[ Obsidian ] AlbumArtLockscreenMod: " + msg);
    }

    @Override public boolean listensTo(String packageName) { return SYSTEM_UI.equals(packageName); }
}
