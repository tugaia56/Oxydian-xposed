package it.tugaia56.obsidian.tiles;

import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import com.topjohnwu.superuser.Shell;

/**
 * Riquadro QS "ADB" — attiva/disattiva il debug USB (Settings.Global.ADB_ENABLED). Lettura via
 * ContentResolver (pubblica, non serve root); scrittura via root shell, dato che WRITE_SECURE_
 * SETTINGS non è concedibile a un'app terza senza firma di sistema.
 */
public class AdbTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        boolean enabled = isAdbEnabled();
        try {
            Shell.cmd("settings put global " + Settings.Global.ADB_ENABLED + " " + (enabled ? 0 : 1)).exec();
        } catch (Throwable ignored) {}
        updateTile();
    }

    private boolean isAdbEnabled() {
        return Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0) != 0;
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(isAdbEnabled() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
