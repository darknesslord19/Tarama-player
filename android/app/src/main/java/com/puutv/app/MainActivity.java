package com.puutv.app;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.os.Build;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.graphics.Color;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

@OptIn(markerClass = UnstableApi.class)
public class MainActivity extends Activity {
    private static final String DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36";

    private FrameLayout root;
    private int aspect = 1;
    private static final int[] MODES = {AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_ZOOM, AspectRatioFrameLayout.RESIZE_MODE_FILL};
    private WebView web;
    private PlayerView pv;
    private ExoPlayer player;

    private boolean full = false;
    private float rx, ry, rw, rh, rr;
    private String curUrl, curUa, curRef;
    private boolean asHls = true, triedAlt = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        root = findViewById(R.id.root);
        web = findViewById(R.id.web);
        setupEdgeToEdge();
        pv = findViewById(R.id.player);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setTextZoom(100);
        web.setBackgroundColor(Color.rgb(20, 20, 20));
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Android");

        player = new ExoPlayer.Builder(this).build();
        pv.setPlayer(player);
        pv.setKeepContentOnPlayerReset(false);
        pv.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                float d = getResources().getDisplayMetrics().density;
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), full ? 0 : rr * d);
            }
        });
        pv.setClipToOutline(true);
        GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) { return true; }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                js("onNativeTap()");
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (full) js("cycleAspect()");
                return true;
            }
        });
        pv.setOnTouchListener((v, e) -> {
            gd.onTouchEvent(e);
            return true;
        });

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    pv.setVisibility(View.VISIBLE);
                    js("onNativeState('ready')");
                } else if (state == Player.STATE_BUFFERING) {
                    js("onNativeState('buffering')");
                }
            }

            @Override
            public void onPlayerError(PlaybackException e) {
                if (!triedAlt && curUrl != null) {
                    triedAlt = true;
                    asHls = !asHls;
                    start();
                    return;
                }
                pv.setVisibility(View.INVISIBLE);
                js("onNativeError(" + JSONObject.quote(e.getErrorCodeName()) + ")");
            }
        });

        web.loadUrl("file:///android_asset/index.html");
    }

    private void js(String code) {
        web.post(() -> web.evaluateJavascript("try{" + code + "}catch(e){}", null));
    }

    private static boolean guessHls(String url) {
        String u = url.toLowerCase();
        if (u.contains("m3u8")) return true;
        if (u.contains(".mp4") || u.contains(".mkv") || u.contains("/udp/") || u.contains("/rtp/")) return false;
        return !(u.endsWith(".ts") || u.contains(".ts?"));
    }

    private void start() {
        DefaultHttpDataSource.Factory f = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(10000)
                .setReadTimeoutMs(15000)
                .setUserAgent(curUa != null && !curUa.isEmpty() ? curUa : DEFAULT_UA);
        if (curRef != null && !curRef.isEmpty()) {
            Map<String, String> h = new HashMap<>();
            h.put("Referer", curRef);
            f.setDefaultRequestProperties(h);
        }
        MediaItem.Builder b = new MediaItem.Builder().setUri(Uri.parse(curUrl));
        if (asHls) b.setMimeType(MimeTypes.APPLICATION_M3U8);
        pv.setVisibility(View.INVISIBLE);
        player.stop();
        player.setMediaSource(new DefaultMediaSourceFactory(f).createMediaSource(b.build()));
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private void layoutPlayer() {
        float d = getResources().getDisplayMetrics().density;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pv.getLayoutParams();
        if (full) {
            lp.width = FrameLayout.LayoutParams.MATCH_PARENT;
            lp.height = FrameLayout.LayoutParams.MATCH_PARENT;
            lp.leftMargin = 0;
            lp.topMargin = 0;
        } else {
            lp.width = Math.max(1, Math.round(rw * d));
            lp.height = Math.max(1, Math.round(rh * d));
            lp.leftMargin = Math.round(rx * d);
            lp.topMargin = Math.round(ry * d);
        }
        pv.setLayoutParams(lp);
        pv.setResizeMode(full ? MODES[aspect] : AspectRatioFrameLayout.RESIZE_MODE_FIT);
        pv.invalidateOutline();
    }

    private static final int BASE_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;

    /** Her zaman kenardan kenara çiz; normal modda çubuk boşluklarını kendimiz bırakalım. */
    @SuppressWarnings("deprecation")
    private void setupEdgeToEdge() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) w.setDecorFitsSystemWindows(false);
        else w.getDecorView().setSystemUiVisibility(BASE_FLAGS);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (full) {
                v.setPadding(0, 0, 0, 0);
            } else if (Build.VERSION.SDK_INT >= 30) {
                Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(i.left, i.top, i.right, i.bottom);
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
    }

    private boolean isTv() {
        UiModeManager um = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        return um != null && um.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    @SuppressWarnings("deprecation")
    private void immersive(boolean on) {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams a = w.getAttributes();
            a.layoutInDisplayCutoutMode = on
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            w.setAttributes(a);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                int types = WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars();
                if (on) {
                    c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    c.hide(types);
                } else {
                    c.show(types);
                }
            }
        } else {
            w.getDecorView().setSystemUiVisibility(on
                    ? BASE_FLAGS | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    : BASE_FLAGS);
        }
        if (!isTv()) {
            setRequestedOrientation(on ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
        root.requestApplyInsets();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        String k = null;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP: k = "up"; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: k = "down"; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: k = "left"; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: k = "right"; break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_BUTTON_A: k = "ok"; break;
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP: k = "chup"; break;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN: k = "chdown"; break;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_BOOKMARK: k = "menu"; break;
            default: break;
        }
        if (k != null) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) js("onKey('" + k + "')");
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        web.evaluateJavascript("(window.onBack&&onBack())?'1':'0'", v -> {
            if (v == null || !v.contains("1")) finish();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        player.setPlayWhenReady(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (curUrl != null) player.setPlayWhenReady(true);
        if (full) immersive(true);
    }

    @Override
    protected void onDestroy() {
        player.release();
        web.destroy();
        super.onDestroy();
    }

    private class Bridge {
        @JavascriptInterface
        public void play(String url, String ua, String ref) {
            runOnUiThread(() -> {
                curUrl = url;
                curUa = ua;
                curRef = ref;
                triedAlt = false;
                asHls = guessHls(url);
                start();
            });
        }

        @JavascriptInterface
        public void stop() {
            runOnUiThread(() -> {
                curUrl = null;
                player.stop();
                player.clearMediaItems();
                pv.setVisibility(View.INVISIBLE);
            });
        }

        @JavascriptInterface
        public void setRect(float x, float y, float w, float h, float r) {
            runOnUiThread(() -> {
                rx = x; ry = y; rw = w; rh = h; rr = r;
                layoutPlayer();
            });
        }

        @JavascriptInterface
        public void setFullscreen(boolean on) {
            runOnUiThread(() -> {
                full = on;
                immersive(on);
                layoutPlayer();
            });
        }

        @JavascriptInterface
        public void setAspect(int m) {
            runOnUiThread(() -> {
                aspect = Math.max(0, Math.min(2, m));
                layoutPlayer();
            });
        }

        @JavascriptInterface
        public void setVolume(float v) {
            runOnUiThread(() -> player.setVolume(v));
        }
    }
}
