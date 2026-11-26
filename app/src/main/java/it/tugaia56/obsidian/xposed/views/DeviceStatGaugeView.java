package it.tugaia56.obsidian.xposed.views;

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

/**
 * Gauge live per un singolo dato di sistema (batteria/RAM/volume/temperatura), in due stili:
 * CIRCULAR (arco 135°→275°, come OC's ArcProgressWidget/ProgressImageView — stesso algoritmo,
 * ridisegnato qui invece che come bitmap generata) o LINEAR (barra orizzontale semplice).
 * Nessuna dipendenza da risorse SystemUI — l'icona va passata da fuori (vedi setIcon), i
 * colori sono quelli di "Colore Personalizzato"/"Colore testo" del widget dispositivo.
 */
public class DeviceStatGaugeView extends View {

    public enum StatType { BATTERY, RAM, VOLUME, TEMPERATURE, WIFI, BLUETOOTH, STORAGE, DND }
    public enum Style { CIRCULAR, LINEAR }

    private static final int POLL_INTERVAL_MS = 2000;

    private final Context mContext;
    private StatType mType = StatType.BATTERY;
    private Style mStyle = Style.CIRCULAR;
    private int mProgressColor = it.tugaia56.obsidian.xposed.utils.AccentUtil.themeAccent(0xFF908DFF);
    private int mTextColor = Color.WHITE;
    private Drawable mIcon;

    private int mPercent = -1;
    private int mBatteryTempC = -1;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mAttached = false;
    private boolean mBatteryReceiverRegistered = false;

    private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            mBatteryTempC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10;
            if (mType == StatType.BATTERY) setPercent(scale > 0 ? (level * 100) / scale : 0);
            else if (mType == StatType.TEMPERATURE) setPercent(Math.max(0, Math.min(mBatteryTempC, 100)));
        }
    };

    private final Runnable mPollRunnable = new Runnable() {
        @Override public void run() {
            if (!mAttached) return;
            if (mType == StatType.RAM) setPercent(readRamPercent());
            else if (mType == StatType.VOLUME) setPercent(readVolumePercent());
            else if (mType == StatType.WIFI) setPercent(readWifiPercent());
            else if (mType == StatType.BLUETOOTH) setPercent(readBluetoothPercent());
            else if (mType == StatType.STORAGE) setPercent(readStoragePercent());
            else if (mType == StatType.DND) setPercent(readDndPercent());
            mHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    public DeviceStatGaugeView(Context context) {
        super(context);
        mContext = context;
    }

    public void setType(StatType type) {
        if (mType == type) return;
        mType = type;
        mPercent = -1;
        refreshNow();
        invalidate();
    }

    public void setStyle(Style style) {
        mStyle = style;
        invalidate();
    }

    public void setColors(int progressColor, int textColor) {
        mProgressColor = progressColor;
        mTextColor = textColor;
        invalidate();
    }

    public void setIcon(Drawable icon) {
        mIcon = icon != null ? icon.mutate() : null;
        invalidate();
    }

    private void setPercent(int p) {
        int clamped = Math.max(0, Math.min(p, 100));
        if (clamped != mPercent) {
            mPercent = clamped;
            postInvalidate();
        }
    }

    private void refreshNow() {
        if (mType == StatType.RAM) setPercent(readRamPercent());
        else if (mType == StatType.VOLUME) setPercent(readVolumePercent());
        else if (mType == StatType.TEMPERATURE) setPercent(Math.max(0, Math.min(mBatteryTempC, 100)));
        else if (mType == StatType.WIFI) setPercent(readWifiPercent());
        else if (mType == StatType.BLUETOOTH) setPercent(readBluetoothPercent());
        else if (mType == StatType.STORAGE) setPercent(readStoragePercent());
        else if (mType == StatType.DND) setPercent(readDndPercent());
        // BATTERY aggiornata solo dal broadcast — valore iniziale arriva alla prima ricezione
        // "sticky" del registerReceiver (ACTION_BATTERY_CHANGED è sempre sticky).
    }

    @SuppressWarnings("deprecation")
    private int readWifiPercent() {
        try {
            WifiManager wifi = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
            if (wifi == null || !wifi.isWifiEnabled()) return 0;
            int rssi = wifi.getConnectionInfo().getRssi();
            int level = WifiManager.calculateSignalLevel(rssi, 5); // 0..4
            return level <= 0 ? 10 : level * 25; // acceso ma non connesso -> anello minimo, non vuoto
        } catch (Throwable t) { return 0; }
    }

    private int readBluetoothPercent() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            return adapter != null && adapter.isEnabled() ? 100 : 0;
        } catch (Throwable t) { return 0; }
    }

    /** Spazio usato della memoria interna (/data), in percentuale. */
    private int readStoragePercent() {
        try {
            android.os.StatFs fs = new android.os.StatFs(android.os.Environment.getDataDirectory().getPath());
            long total = fs.getTotalBytes();
            if (total <= 0) return 0;
            return (int) (((total - fs.getAvailableBytes()) * 100) / total);
        } catch (Throwable t) { return 0; }
    }

    /** Non disturbare: 100 se e' acceso (qualsiasi filtro diverso da "tutto"), 0 se e' spento. */
    private int readDndPercent() {
        try {
            android.app.NotificationManager nm = (android.app.NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            return nm != null && nm.getCurrentInterruptionFilter() != android.app.NotificationManager.INTERRUPTION_FILTER_ALL ? 100 : 0;
        } catch (Throwable t) { return 0; }
    }

    private int readRamPercent() {
        try {
            ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);
            if (info.totalMem <= 0) return 0;
            long used = info.totalMem - info.availMem;
            return (int) ((used * 100) / info.totalMem);
        } catch (Throwable t) { return 0; }
    }

    private int readVolumePercent() {
        try {
            AudioManager audio = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            return max > 0 ? (cur * 100) / max : 0;
        } catch (Throwable t) { return 0; }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAttached = true;
        if (!mBatteryReceiverRegistered) {
            try {
                mContext.registerReceiver(mBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                mBatteryReceiverRegistered = true;
            } catch (Throwable ignored) {}
        }
        refreshNow();
        mHandler.postDelayed(mPollRunnable, POLL_INTERVAL_MS);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mAttached = false;
        mHandler.removeCallbacks(mPollRunnable);
        if (mBatteryReceiverRegistered) {
            try { mContext.unregisterReceiver(mBatteryReceiver); } catch (Throwable ignored) {}
            mBatteryReceiverRegistered = false;
        }
    }

    // ── Disegno ──────────────────────────────────────────────────────────────────────────

    private static final int MIN_ANGLE = 135;
    private static final int SWEEP_ANGLE = 275;

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mStyle == Style.CIRCULAR) drawCircular(canvas);
        else drawLinear(canvas);
    }

    private String centerText() {
        if (mPercent < 0) return "…";
        if (mType == StatType.TEMPERATURE) return mPercent + "°";
        if (mType == StatType.BLUETOOTH || mType == StatType.DND) return mPercent > 0 ? "ON" : "OFF";
        return mPercent + "%";
    }

    private void drawCircular(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float stroke = w * 0.09f;
        RectF arc = new RectF(stroke / 2f, stroke / 2f, w - stroke / 2f, h - stroke / 2f);

        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(stroke);
        ring.setStrokeCap(Paint.Cap.ROUND);

        ring.setColor(Color.argb(60, 255, 255, 255));
        canvas.drawArc(arc, MIN_ANGLE, SWEEP_ANGLE, false, ring);

        int pct = Math.max(mPercent, 0);
        ring.setColor(mProgressColor);
        canvas.drawArc(arc, MIN_ANGLE, (SWEEP_ANGLE / 100f) * pct, false, ring);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(mTextColor);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setTextSize(w * 0.2f);
        float cx = w / 2f;
        float cy = h / 2f - (text.ascent() + text.descent()) / 2f;
        canvas.drawText(centerText(), cx, cy, text);

        if (mIcon != null) {
            int iconSize = Math.round(w * 0.22f);
            int left = Math.round(cx - iconSize / 2f);
            int top = Math.round(h * 0.66f);
            mIcon.setBounds(left, top, left + iconSize, top + iconSize);
            mIcon.setTint(mTextColor);
            mIcon.draw(canvas);
        }
    }

    private void drawLinear(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        int iconSize = Math.round(h * 0.55f);
        int barLeft = mIcon != null ? iconSize + Math.round(h * 0.3f) : 0;

        if (mIcon != null) {
            int top = (h - iconSize) / 2;
            mIcon.setBounds(0, top, iconSize, top + iconSize);
            mIcon.setTint(mTextColor);
            mIcon.draw(canvas);
        }

        float barTop = h * 0.62f, barBottom = h * 0.78f;
        float radius = (barBottom - barTop) / 2f;
        RectF track = new RectF(barLeft, barTop, w, barBottom);
        Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        bar.setColor(Color.argb(60, 255, 255, 255));
        canvas.drawRoundRect(track, radius, radius, bar);

        int pct = Math.max(mPercent, 0);
        float fillRight = barLeft + (w - barLeft) * (pct / 100f);
        if (fillRight > barLeft) {
            RectF fill = new RectF(barLeft, barTop, Math.max(fillRight, barLeft + radius * 2), barBottom);
            bar.setColor(mProgressColor);
            canvas.drawRoundRect(fill, radius, radius, bar);
        }

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(mTextColor);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setTextSize(h * 0.32f);
        canvas.drawText(centerText(), barLeft, barTop - h * 0.08f, text);
    }
}
