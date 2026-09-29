package it.tugaia56.obsidian.tiles;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import it.tugaia56.obsidian.BuildConfig;
import it.tugaia56.obsidian.ui.activity.AuthActivity;

/**
 * Riquadro QS "Riavvia" — apre direttamente "Riavvio Avanzato Riquadri" (la griglia, non la
 * lista, indipendentemente da quale stile è scelto in Menù Accensione: un tile QS è già di
 * per sé un tocco rapido, la griglia è il modo più veloce di scegliere). Nessuno stato
 * acceso/spento, è solo un lanciatore.
 */
public class RebootTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    @Override
    @SuppressWarnings("deprecation") // startActivityAndCollapse(Intent): still the only working
    // form pre-API 34 — the PendingIntent overload below doesn't exist on those OS versions.
    public void onClick() {
        super.onClick();
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(BuildConfig.APPLICATION_ID, AuthActivity.class.getName()));
        intent.putExtra("qsTileGrid", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            startActivityAndCollapse(pi);
        } else {
            startActivityAndCollapse(intent);
        }
    }
}
