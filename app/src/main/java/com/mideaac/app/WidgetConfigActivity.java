package com.mideaac.app;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class WidgetConfigActivity extends Activity {
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.widget_config);

        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);

        SharedPreferences p = getSharedPreferences("widget", MODE_PRIVATE);
        EditText url = findViewById(R.id.cfg_url);
        EditText user = findViewById(R.id.cfg_user);
        EditText pass = findViewById(R.id.cfg_pass);
        EditText unit = findViewById(R.id.cfg_unit);
        url.setText(p.getString("url", "http://192.168.1.8:8814"));
        user.setText(p.getString("user", "william"));
        pass.setText(p.getString("pass", ""));
        unit.setText(p.getString("unit", "C"));

        Button save = findViewById(R.id.cfg_save);
        save.setOnClickListener(v -> {
            p.edit()
                .putString("url", url.getText().toString().trim())
                .putString("user", user.getText().toString().trim())
                .putString("pass", pass.getText().toString())
                .putString("unit", unit.getText().toString().trim().toUpperCase())
                .apply();
            WidgetUpdateService.update(this, widgetId, false);
            Intent ok = new Intent();
            ok.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            setResult(RESULT_OK, ok);
            finish();
        });
    }
}
