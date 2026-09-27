package it.tugaia56.obsidian.weather;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Client minimale per api.met.no (MET Norway) — gratis, senza chiave, richiede uno
 *  User-Agent identificativo per policy del servizio. */
public class MetNorwayClient {

    private static final String URL_FMT =
        "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%f&lon=%f";
    private static final String USER_AGENT = "Oxydian-Xposed-Module github.com/tugaia56/Oxydian-xposed";

    public static WeatherInfo fetchCurrent(double lat, double lon, String cityName) {
        try {
            String url = String.format(Locale.US, URL_FMT, lat, lon);
            JSONObject root = new JSONObject(httpGet(url));
            JSONArray series = root.getJSONObject("properties").getJSONArray("timeseries");
            JSONObject first = series.getJSONObject(0);
            JSONObject details = first.getJSONObject("data").getJSONObject("instant").getJSONObject("details");

            String symbol = "cloudy";
            boolean isDay = true;
            JSONObject next1h = first.getJSONObject("data").optJSONObject("next_1_hours");
            if (next1h != null) {
                symbol = next1h.getJSONObject("summary").optString("symbol_code", "cloudy");
                isDay = !symbol.contains("_night");
            }

            WeatherInfo w = new WeatherInfo();
            w.tempC = (int) Math.round(details.getDouble("air_temperature"));
            w.humidityPct = (int) Math.round(details.optDouble("relative_humidity", 0));
            w.windKmh = details.optDouble("wind_speed", 0) * 3.6;
            w.weatherCode = symbolToWmo(symbol);
            w.isDay = isDay;
            w.cityName = cityName;
            w.fetchedAtMillis = System.currentTimeMillis();
            return w;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Mappa i symbol_code di MET Norway (senza suffisso _day/_night/_polartwilight) sui
     *  codici WMO usati da WeatherInfo. */
    private static int symbolToWmo(String symbolCode) {
        String base = symbolCode;
        int us = base.indexOf('_');
        if (us > 0) base = base.substring(0, us);

        if (base.contains("thunder")) return 95;
        if (base.equals("clearsky")) return 0;
        if (base.equals("fair")) return 1;
        if (base.equals("partlycloudy")) return 2;
        if (base.equals("cloudy")) return 3;
        if (base.equals("fog")) return 45;
        if (base.contains("sleet")) return 66;
        if (base.contains("snowshowers")) return 85;
        if (base.contains("snow")) return 73;
        if (base.contains("rainshowers")) return 80;
        if (base.contains("rain")) return 63;
        return 3;
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        try (InputStream in = conn.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }
}
