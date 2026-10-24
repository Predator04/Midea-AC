package com.mideaac.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

public class GeofenceWorker extends Worker {
    public GeofenceWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    @NonNull
    @Override
    public Result doWork() {
        SharedPreferences p = getApplicationContext().getSharedPreferences("widget", Context.MODE_PRIVATE);
        if (!p.getBoolean("geo_enabled", false)) return Result.success();
        double hlat = Double.longBitsToDouble(p.getLong("home_lat", 0));
        double hlng = Double.longBitsToDouble(p.getLong("home_lng", 0));
        if (hlat == 0 && hlng == 0) return Result.success();

        Location loc = lastLocation();
        if (loc == null) return Result.success();
        double dist = distM(loc.getLatitude(), loc.getLongitude(), hlat, hlng);
        boolean away = dist > 1000; // >1 km from home = away

        String base = p.getString("url", "http://192.168.1.8:8814");
        String user = p.getString("user", "");
        String pass = p.getString("pass", "");
        try {
            boolean on = getPower(base, user, pass);
            if (away && on) {
                setPower(base, user, pass, false);
            } else if (!away && !on && p.getBoolean("geo_auto_on", true)) {
                setPower(base, user, pass, true);
            }
        } catch (Exception ignored) {}
        return Result.success();
    }

    private Location lastLocation() {
        try {
            LocationManager lm = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
            Location l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (l == null) l = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            return l;
        } catch (SecurityException e) { return null; }
    }

    private double distM(double la1, double lo1, double la2, double lo2) {
        double R = 6371000, dLa = Math.toRadians(la2 - la1), dLo = Math.toRadians(lo2 - lo1);
        double a = Math.sin(dLa/2)*Math.sin(dLa/2) + Math.cos(Math.toRadians(la1))*Math.cos(Math.toRadians(la2))*Math.sin(dLo/2)*Math.sin(dLo/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    }

    private boolean getPower(String base, String user, String pass) throws Exception {
        String cookie = login(base, user, pass);
        if (cookie == null) throw new Exception("no cookie");
        JSONObject s = new JSONObject(get(base + "/state", cookie));
        return s.optBoolean("power", false);
    }

    private void setPower(String base, String user, String pass, boolean power) throws Exception {
        String cookie = login(base, user, pass);
        if (cookie == null) throw new Exception("no cookie");
        post(base + "/control", cookie, "{\"power\":" + power + "}");
    }

    private String login(String base, String user, String pass) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(base + "/login").openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setDoOutput(true);
            byte[] b = ("{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}").getBytes(StandardCharsets.UTF_8);
            OutputStream os = c.getOutputStream(); os.write(b); os.close();
            if (c.getResponseCode() != 200) return null;
            for (Map.Entry<String, List<String>> e : c.getHeaderFields().entrySet()) {
                if ("Set-Cookie".equalsIgnoreCase(e.getKey()))
                    for (String v : e.getValue())
                        if (v.startsWith("session=")) return v.split(";")[0];
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String get(String url, String cookie) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("Cookie", cookie);
        InputStream is = c.getInputStream();
        BufferedReader r = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder(); String l;
        while ((l = r.readLine()) != null) sb.append(l);
        is.close();
        return sb.toString();
    }

    private void post(String url, String cookie, String json) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Cookie", cookie);
        c.setDoOutput(true);
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        OutputStream os = c.getOutputStream(); os.write(b); os.close();
        c.getInputStream().close();
    }
}
