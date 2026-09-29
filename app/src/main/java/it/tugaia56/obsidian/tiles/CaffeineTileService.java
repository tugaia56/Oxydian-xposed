package it.tugaia56.obsidian.tiles;

import android.os.PowerManager;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Vero riquadro Quick Settings di sistema — si aggiunge/rimuove dall'editor Android delle
 * Impostazioni Rapide, non da Oxydian. Tocco = tiene/lascia lo schermo acceso, come le
 * classiche app "Caffeine". Il wake lock è statico: una TileService può essere ricreata dal
 * sistema tra un tocco e l'altro, il riferimento deve sopravvivere a quello.
 */
public class CaffeineTileService extends TileService {

    private static PowerManager.WakeLock sWakeLock;

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (isActive()) {
            releaseWakeLock();
        } else {
            acquireWakeLock();
        }
        updateTile();
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
        releaseWakeLock();
    }

    private boolean isActive() {
        return sWakeLock != null && sWakeLock.isHeld();
    }

    @SuppressWarnings("deprecation") // SCREEN_BRIGHT_WAKE_LOCK: no non-deprecated replacement
    // exists for "keep the screen on system-wide" outside of a visible Activity's own window
    // flag — this is the same technique every published Caffeine-style app uses.
    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm == null) return;
            sWakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                    "Oxydian:Caffeine");
            sWakeLock.acquire();
        } catch (Throwable ignored) {}
    }

    private void releaseWakeLock() {
        try {
            if (sWakeLock != null && sWakeLock.isHeld()) sWakeLock.release();
        } catch (Throwable ignored) {}
        sWakeLock = null;
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(isActive() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
