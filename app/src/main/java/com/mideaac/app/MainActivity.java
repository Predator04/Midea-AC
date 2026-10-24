package com.mideaac.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final String DEFAULT_URL = "http://192.168.1.8:8814";
    private static final String GITHUB_LATEST = "https://api.github.com/repos/Predator04/Midea-AC/releases/latest";
    private static final long UPDATE_COOLDOWN = 12L * 3600 * 1000; // 12h

    private WebView webView;
    private SharedPreferences prefs;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("midea", MODE_PRIVATE);
        webView = new WebView(this);
        webView.setBackgroundColor(0xFF0D1117);
        setContentView(webView);
        setTitle("Midea AC");

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false; // stay in-app
            }
        });

        webView.loadUrl(prefs.getString("url", DEFAULT_URL));
        checkForUpdates(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Reload");
        menu.add(0, 2, 0, "Change URL");
        menu.add(0, 3, 0, "Check for updates");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) { webView.reload(); return true; }
        if (item.getItemId() == 2) { showUrlDialog(); return true; }
        if (item.getItemId() == 3) { checkForUpdates(true); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void showUrlDialog() {
        final EditText input = new EditText(this);
        input.setText(prefs.getString("url", DEFAULT_URL));
        input.setHint("http://ip:8814");
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * density);
        LinearLayout container = new LinearLayout(this);
        container.setPadding(pad, pad, pad, pad);
        container.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("Panel URL")
                .setMessage("LAN: http://192.168.1.8:8814\nRemote: http://<your-url>:8814")
                .setView(container)
                .setPositiveButton("Save", (d, w) -> {
                    String u = input.getText().toString().trim();
                    if (!u.isEmpty()) {
                        prefs.edit().putString("url", u).apply();
                        webView.loadUrl(u);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---- Auto-update from GitHub Releases ----

    private void checkForUpdates(boolean manual) {
        long now = System.currentTimeMillis();
        long last = prefs.getLong("last_update_check", 0);
        if (!manual && now - last < UPDATE_COOLDOWN) return;
        prefs.edit().putLong("last_update_check", now).apply();

        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(GITHUB_LATEST).openConnection();
                c.setRequestProperty("User-Agent", "Midea-AC");
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                InputStream is = c.getInputStream();
                StringBuilder sb = new StringBuilder();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) sb.append(new String(buf, 0, n));
                is.close();

                JSONObject o = new JSONObject(sb.toString());
                int latest = Integer.parseInt(o.getString("tag_name").replaceAll("[^0-9]", ""));
                if (latest <= BuildConfig.VERSION_CODE) {
                    if (manual) runOnUiThread(() -> Toast.makeText(this, "You're up to date", Toast.LENGTH_SHORT).show());
                    return;
                }
                if (latest == prefs.getInt("skipped_version", 0)) {
                    if (manual) runOnUiThread(() -> Toast.makeText(this, "You're up to date", Toast.LENGTH_SHORT).show());
                    return;
                }

                String dl = null;
                JSONArray assets = o.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        String name = assets.getJSONObject(i).getString("name");
                        if (name.endsWith(".apk")) {
                            dl = assets.getJSONObject(i).getString("browser_download_url");
                            break;
                        }
                    }
                }
                if (dl == null) return;

                final int latestCode = latest;
                final String url = dl;
                runOnUiThread(() -> showUpdateDialog(latestCode, url));
            } catch (Exception e) {
                if (manual) runOnUiThread(() -> Toast.makeText(this, "Update check failed", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showUpdateDialog(int latestCode, String url) {
        new AlertDialog.Builder(this)
                .setTitle("Update available")
                .setMessage("Version " + latestCode + " is available. Download and install now?")
                .setPositiveButton("Update", (d, w) -> downloadAndInstall(url))
                .setNegativeButton("Skip this version", (d, w) ->
                        prefs.edit().putInt("skipped_version", latestCode).apply())
                .setNeutralButton("Later", null)
                .show();
    }

    private void downloadAndInstall(String url) {
        Toast.makeText(this, "Downloading update…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                File dir = new File(getCacheDir(), "updates");
                dir.mkdirs();
                File apk = new File(dir, "app-release.apk");
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setRequestProperty("User-Agent", "Midea-AC");
                InputStream in = c.getInputStream();
                FileOutputStream out = new FileOutputStream(apk);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                out.close();
                in.close();

                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apk);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(uri, "application/vnd.android.package-archive");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Update download failed", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
