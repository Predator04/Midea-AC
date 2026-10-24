package com.mideaac.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class GeofenceActivity extends Activity {
    private SharedPreferences p;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.geofence);
        setTitle("Geofence — auto on/off");
        p = getSharedPreferences("widget", MODE_PRIVATE);

        EditText url = findViewById(R.id.geo_url);
        EditText user = findViewById(R.id.geo_user);
        EditText pass = findViewById(R.id.geo_pass);
        Switch enabled = findViewById(R.id.geo_enabled);
        Switch autoOn = findViewById(R.id.geo_auto_on);
        Button setHome = findViewById(R.id.geo_set_home);

        url.setText(p.getString("url", "http://192.168.1.8:8814"));
        user.setText(p.getString("user", "william"));
        pass.setText(p.getString("pass", ""));
        enabled.setChecked(p.getBoolean("geo_enabled", false));
        autoOn.setChecked(p.getBoolean("geo_auto_on", true));

        setHome.setOnClickListener(v -> {
            saveCreds(url, user, pass);
            Location l = lastLocation();
            if (l == null) {
                Toast.makeText(this, "No location yet — enable GPS and retry", Toast.LENGTH_LONG).show();
                return;
            }
            p.edit().putLong("home_lat", Double.doubleToRawLongBits(l.getLatitude()))
                    .putLong("home_lng", Double.doubleToRawLongBits(l.getLongitude())).apply();
            Toast.makeText(this, "Home set to current location", Toast.LENGTH_SHORT).show();
        });

        enabled.setOnCheckedChangeListener((btn, on) -> {
            p.edit().putBoolean("geo_enabled", on).apply();
            saveCreds(url, user, pass);
            if (on) schedule(); else WorkManager.getInstance(this).cancelUniqueWork("geofence");
        });

        autoOn.setOnCheckedChangeListener((btn, on) -> p.edit().putBoolean("geo_auto_on", on).apply());
    }

    private void saveCreds(EditText url, EditText user, EditText pass) {
        p.edit().putString("url", url.getText().toString().trim())
                .putString("user", user.getText().toString().trim())
                .putString("pass", pass.getText().toString()).apply();
    }

    private void schedule() {
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(GeofenceWorker.class, 15, TimeUnit.MINUTES).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("geofence", ExistingPeriodicWorkPolicy.REPLACE, req);
    }

    private Location lastLocation() {
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Location l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (l == null) l = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            return l;
        } catch (SecurityException e) { return null; }
    }
}
