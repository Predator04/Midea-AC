package com.mideaac.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

public class NotifyWorker extends Worker {
    public NotifyWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    @NonNull
    @Override
    public Result doWork() {
        SharedPreferences p = getApplicationContext().getSharedPreferences("widget", Context.MODE_PRIVATE);
        String base = p.getString("url", "http://192.168.1.8:8814");
        String user = p.getString("user", "william");
        String pass = p.getString("pass", "");
        try {
            String cookie = login(base, user, pass);
            if (cookie == null) return Result.success();
            JSONObject s = new JSONObject(get(base + "/state", cookie));
            String alert = null;
            if (s.optBoolean("filter_alert", false)) alert = "AC filter needs cleaning";
            else if (s.optInt("error_code", 0) != 0) alert = "AC fault code E" + s.optInt("error_code", 0);
            else if (s.optBoolean("power", false)) {
                int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                if (h >= 23 || h < 6) alert = "AC still ON at " + h + ":00";
            }
            if (alert != null) notify(alert);
        } catch (Exception ignored) {}
        return Result.success();
    }

    private void notify(String msg) {
        Context c = getApplicationContext();
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel("ac", "Midea AC", NotificationManager.IMPORTANCE_DEFAULT));
        }
        Notification n = new NotificationCompat.Builder(c, "ac")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Midea AC")
            .setContentText(msg)
            .setAutoCancel(true)
            .build();
        nm.notify(1, n);
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
}
