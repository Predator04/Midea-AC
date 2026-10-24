package com.mideaac.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class WidgetUpdateService {

    static void update(Context ctx, int widgetId, boolean toggle) {
        new Thread(() -> {
            SharedPreferences p = ctx.getSharedPreferences("widget", Context.MODE_PRIVATE);
            String base = p.getString("url", "http://192.168.1.8:8814");
            String user = p.getString("user", "");
            String pass = p.getString("pass", "");

            String cookie = login(base, user, pass);
            if (cookie == null) {
                postRemote(ctx, widgetId, "—", "Sign in needed", false);
                return;
            }

            JSONObject s;
            try {
                s = getState(base, cookie);
            } catch (Exception e) {
                postRemote(ctx, widgetId, "—", "Offline", false);
                return;
            }

            boolean power = s.optBoolean("power", false);
            if (toggle) {
                try {
                    post(base, cookie, "{\"power\":" + (!power) + "}");
                    s = getState(base, cookie);
                    power = s.optBoolean("power", false);
                } catch (Exception ignored) {}
            }

            double indoor = s.optDouble("indoor_temp", 0);
            String unit = p.getString("unit", "C");
            double shown = "F".equals(unit) ? indoor * 9 / 5 + 32 : indoor;
            String temp = Math.round(shown) + "°";
            String label = power ? (s.optString("mode_name", "") + " · " + s.optString("fan_name", "")) : "Off";
            postRemote(ctx, widgetId, temp, label, power);
        }).start();
    }

    static void postRemote(Context ctx, int widgetId, String temp, String label, boolean power) {
        Handler h = new Handler(Looper.getMainLooper());
        h.post(() -> {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
            v.setTextViewText(R.id.wtemp, temp);
            v.setTextViewText(R.id.wlabel, label);
            v.setInt(R.id.wpower, "setBackgroundResource", power ? R.drawable.w_on : R.drawable.w_off);

            Intent t = new Intent(ctx, ACWidgetProvider.class).setAction(ACWidgetProvider.ACTION_TOGGLE);
            t.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, widgetId, t,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(R.id.wpower, pi);

            Intent r = new Intent(ctx, ACWidgetProvider.class).setAction(ACWidgetProvider.ACTION_REFRESH);
            r.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            PendingIntent pr = PendingIntent.getBroadcast(ctx, widgetId + 1000, r,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(R.id.wtemp, pr);

            mgr.updateAppWidget(widgetId, v);
        });
    }

    // ---- HTTP helpers (manual cookie handling) ----

    private static String login(String base, String user, String pass) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(base + "/login").openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setDoOutput(true);
            byte[] body = ("{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            OutputStream os = c.getOutputStream();
            os.write(body); os.close();
            int code = c.getResponseCode();
            if (code != 200) return null;
            Map<String, List<String>> headers = c.getHeaderFields();
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if ("Set-Cookie".equalsIgnoreCase(e.getKey())) {
                    for (String v : e.getValue()) {
                        if (v.startsWith("session=")) return v.split(";")[0];
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static JSONObject getState(String base, String cookie) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base + "/state").openConnection();
        c.setRequestProperty("Cookie", cookie);
        InputStream is = c.getInputStream();
        String body = readAll(is);
        is.close();
        return new JSONObject(body);
    }

    private static void post(String base, String cookie, String json) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base + "/control").openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Cookie", cookie);
        c.setDoOutput(true);
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        OutputStream os = c.getOutputStream();
        os.write(b); os.close();
        c.getInputStream().close();
    }

    private static String readAll(InputStream is) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String l;
        while ((l = r.readLine()) != null) sb.append(l);
        return sb.toString();
    }
}
