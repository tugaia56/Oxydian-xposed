package it.tugaia56.obsidian.utils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import it.tugaia56.obsidian.BuildConfig;

/**
 * Logica di controllo aggiornamenti condivisa tra SettingsUpdateFragment (pulsante manuale)
 * e UpdateCheckWorker (controllo periodico in background) — estratta 2026-08-29 quando è
 * arrivato il secondo chiamante, per non duplicare fetch/confronto versioni in due posti.
 */
public class UpdateChecker {

    private static final String API_URL =
            "https://api.github.com/repos/tugaia56/Oxydian-xposed/releases/latest";

    public static class Result {
        public final boolean newer;
        public final String version;
        public final String changelog;
        public final String downloadUrl;

        Result(boolean newer, String version, String changelog, String downloadUrl) {
            this.newer = newer;
            this.version = version;
            this.changelog = changelog;
            this.downloadUrl = downloadUrl;
        }
    }

    /** Interroga la GitHub Releases API e confronta col BuildConfig.VERSION_NAME corrente.
     *  Lancia un'eccezione in caso di errore di rete/parsing — nessun try/catch interno, la
     *  gestione (toast, notifica, ecc.) resta a chi chiama. */
    public static Result check() throws Exception {
        JSONObject release = fetchLatestRelease();
        String tag = release.getString("tag_name"); // es. "v1.0.3"
        String latestVersion = tag.startsWith("v") ? tag.substring(1) : tag;
        String changelog = release.optString("body", "");

        String downloadUrl = null;
        JSONArray assets = release.getJSONArray("assets");
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (asset.optString("name", "").endsWith(".apk")) {
                downloadUrl = asset.getString("browser_download_url");
                break;
            }
        }

        boolean isNewer = compareVersions(latestVersion, BuildConfig.VERSION_NAME) > 0;
        return new Result(isNewer, latestVersion, changelog, downloadUrl);
    }

    private static JSONObject fetchLatestRelease() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(API_URL).openConnection();
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            return new JSONObject(sb.toString());
        } finally {
            conn.disconnect();
        }
    }

    /** Confronta due versioni "1.2.10" / "1.2.9" numero per numero, non lessicograficamente. */
    private static int compareVersions(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int va = i < pa.length ? parseIntSafe(pa[i]) : 0;
            int vb = i < pb.length ? parseIntSafe(pb[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Throwable t) {
            return 0;
        }
    }
}
