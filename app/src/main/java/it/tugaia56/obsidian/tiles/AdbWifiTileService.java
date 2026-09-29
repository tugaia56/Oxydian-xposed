package it.tugaia56.obsidian.tiles;

import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import com.topjohnwu.superuser.Shell;

/**
 * Riquadro QS "Adb Over WiFi" — attiva/disattiva il debug wireless nativo di Android 11+
 * (Settings.Global "adb_wifi_enabled", nessuna costante pubblica nell'SDK per questa chiave
 * più recente). Stessa tecnica di AdbTileService: lettura diretta, scrittura via root shell,
 * esattamente quello che fa l'interruttore "Debug wireless" dentro Opzioni sviluppatore.
 */
public class AdbWifiTileService extends TileService {

    private static final String KEY = "adb_wifi_enabled";

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        boolean enabled = isEnabled();
        try {
            Shell.cmd("settings put global " + KEY + " " + (enabled ? 0 : 1)).exec();
        } catch (Throwable ignored) {}
        updateTile();
    }

    private boolean isEnabled() {
        return Settings.Global.getInt(getContentResolver(), KEY, 0) != 0;
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(isEnabled() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
