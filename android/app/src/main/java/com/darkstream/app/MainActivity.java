package com.darkstream.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.UiModeManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Arayüz: assets/web/index.html (WebView).
 * Ağ istekleri ve oynatma uygulama tarafında yapılır, bu yüzden CORS engeli yoktur.
 */
public class MainActivity extends Activity {
    static final String UA =
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36";
    private static final int REQ_PLAY = 7;

    private WebView web;
    private SharedPreferences prefs;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("store", MODE_PRIVATE);

        web = new WebView(this);
        web.setBackgroundColor(0xFF0B0B0F);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " DarkStreamApp");

        web.setWebViewClient(new WebViewClient() {
            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("file://")) return false;
                openExternal(url);
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/web/index.html");
    }

    // ---------- JS köprüsü ----------
    class Bridge {
        /** Uygulama içinden HTTP isteği (CORS yok). Sonuç window.__nativeResponse ile döner. */
        @JavascriptInterface
        public void request(final String id, final String url, final String headersJson) {
            pool.execute(() -> {
                int status = 0;
                String body = "";
                String err = null;
                try {
                    if (url.startsWith("asset://")) {
                        body = readAll(getAssets().open("web/" + url.substring(8)));
                        status = 200;
                    } else {
                        HttpURLConnection c = httpOpen(url, headersJson);
                        status = c.getResponseCode();
                        InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
                        body = in == null ? "" : readAll(in);
                        c.disconnect();
                    }
                } catch (Exception e) {
                    err = String.valueOf(e.getMessage());
                }
                final String js = "window.__nativeResponse&&window.__nativeResponse("
                        + JSONObject.quote(id) + "," + status + ","
                        + JSONObject.quote(body) + ","
                        + (err == null ? "null" : JSONObject.quote(err)) + ")";
                runOnUiThread(() -> web.evaluateJavascript(js, null));
            });
        }

        /** Yerel oynatıcıyı (ExoPlayer) açar. */
        @JavascriptInterface
        public void play(final String json) {
            runOnUiThread(() -> {
                Intent i = new Intent(MainActivity.this, PlayerActivity.class);
                i.putExtra("data", json);
                startActivityForResult(i, REQ_PLAY);
            });
        }

        @JavascriptInterface
        public String load(String key) {
            return prefs.getString(key, "");
        }

        @JavascriptInterface
        public void save(String key, String value) {
            prefs.edit().putString(key, value).apply();
        }

        @JavascriptInterface
        public boolean isTv() {
            UiModeManager m = (UiModeManager) getSystemService(UI_MODE_SERVICE);
            return m != null && m.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        }

        @JavascriptInterface
        public void openUrl(String url) {
            runOnUiThread(() -> openExternal(url));
        }

        @JavascriptInterface
        public void exit() {
            runOnUiThread(MainActivity.this::finish);
        }
    }

    // ---------- yardımcılar ----------
    static HttpURLConnection httpOpen(String url, String headersJson) throws Exception {
        JSONObject h = null;
        try {
            if (headersJson != null && !headersJson.isEmpty()) h = new JSONObject(headersJson);
        } catch (Exception ignored) { }
        String cur = url;
        for (int i = 0; i < 6; i++) {
            HttpURLConnection c = (HttpURLConnection) new URL(cur).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", UA);
            if (h != null) {
                Iterator<String> it = h.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    c.setRequestProperty(k, h.optString(k));
                }
            }
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new Exception("Yönlendirme adresi yok");
                cur = new URL(new URL(cur), loc).toString();
                continue;
            }
            return c;
        }
        throw new Exception("Çok fazla yönlendirme");
    }

    static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) { }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PLAY || data == null) return;
        String key = data.getStringExtra("key");
        long pos = data.getLongExtra("pos", 0);
        long dur = data.getLongExtra("dur", 0);
        if (key == null) return;
        web.evaluateJavascript("window.__onPlayerClosed&&window.__onPlayerClosed("
                + JSONObject.quote(key) + "," + pos + "," + dur + ")", null);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.__onBack?String(window.__onBack()):'false'", v -> {
            if (v == null || !v.contains("true")) finish();
        });
    }

    @Override
    protected void onDestroy() {
        pool.shutdownNow();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
