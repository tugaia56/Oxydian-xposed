package it.tugaia56.obsidian.utils;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import it.tugaia56.obsidian.R;

/** Apre l'app Oxydian Theme (dove si scelgono accento, sfondo, icone, PIN e temi delle app). */
public final class OxydianThemeLauncher {
    public static final String PACKAGE = "it.tugaia56.oxydian.theme";

    private OxydianThemeLauncher() {}

    public static void open(Context ctx) {
        Intent i = ctx.getPackageManager().getLaunchIntentForPackage(PACKAGE);
        if (i == null) {
            Toast.makeText(ctx, R.string.oxytheme_not_installed, Toast.LENGTH_LONG).show();
            return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }
}
