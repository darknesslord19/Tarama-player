package com.darkstream.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Yerel oynatıcı. Başlıklar (Referer, User-Agent, Origin...) burada eklenir;
 * tarayıcı olmadığı için CORS kuralı uygulanmaz.
 * Bir kaynak çalışmazsa listedeki sıradaki kaynağa geçer.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends Activity {
    private ExoPlayer player;
    private PlayerView pv;
    private TextView info;
    private JSONArray sources = new JSONArray();
    private int idx = 0;
    private String key = "";
    private String title = "";
    private boolean finished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemUi();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        pv = new PlayerView(this);
        pv.setKeepScreenOn(true);
        pv.setShowSubtitleButton(true);
        pv.setShowNextButton(false);
        pv.setShowPreviousButton(false);
        root.addView(pv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        info = new TextView(this);
        info.setTextColor(Color.WHITE);
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        info.setShadowLayer(6f, 0f, 0f, Color.BLACK);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        info.setPadding(pad, pad, pad, pad);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        root.addView(info, lp);
        setContentView(root);

        pv.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
            info.setVisibility(visibility);
            if (visibility != View.VISIBLE) hideSystemUi();
        });

        long start = 0;
        try {
            JSONObject d = new JSONObject(getIntent().getStringExtra("data"));
            key = d.optString("key", "");
            title = d.optString("title", "");
            start = d.optLong("start", 0);
            JSONArray s = d.optJSONArray("sources");
            if (s != null) sources = s;
        } catch (Exception e) {
            Toast.makeText(this, "Geçersiz oynatma verisi", Toast.LENGTH_LONG).show();
        }
        if (sources.length() == 0) {
            finish();
            return;
        }

        player = new ExoPlayer.Builder(this).build();
        pv.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                long pos = player.getCurrentPosition();
                if (idx + 1 < sources.length()) {
                    Toast.makeText(PlayerActivity.this,
                            "Kaynak açılmadı, sıradaki deneniyor…", Toast.LENGTH_SHORT).show();
                    startSource(idx + 1, pos);
                } else {
                    Toast.makeText(PlayerActivity.this,
                            "Oynatılamadı: " + error.getErrorCodeName(), Toast.LENGTH_LONG).show();
                    pv.showController();
                }
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) finishWithResult();
            }
        });
        startSource(0, start);
    }

    private void startSource(int i, long pos) {
        idx = i;
        JSONObject s = sources.optJSONObject(i);
        if (s == null) return;
        String url = s.optString("url", "");
        String name = s.optString("name", "");

        String ua = MainActivity.UA;
        Map<String, String> hdr = new HashMap<>();
        JSONObject h = s.optJSONObject("headers");
        if (h != null) {
            Iterator<String> it = h.keys();
            while (it.hasNext()) {
                String k = it.next();
                String v = h.optString(k);
                if (k.equalsIgnoreCase("user-agent")) ua = v;
                else hdr.put(k, v);
            }
        }
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(ua)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)
                .setDefaultRequestProperties(hdr);
        DefaultDataSource.Factory ds = new DefaultDataSource.Factory(this, http);

        MediaItem.Builder mb = new MediaItem.Builder().setUri(Uri.parse(url));
        String type = s.optString("type", "").toLowerCase();
        String lower = url.toLowerCase();
        if (type.equals("hls") || (type.isEmpty() && lower.contains(".m3u8"))) {
            mb.setMimeType(MimeTypes.APPLICATION_M3U8);
        } else if (type.equals("dash") || (type.isEmpty() && lower.contains(".mpd"))) {
            mb.setMimeType(MimeTypes.APPLICATION_MPD);
        }

        JSONArray subs = s.optJSONArray("subtitles");
        if (subs != null && subs.length() > 0) {
            List<MediaItem.SubtitleConfiguration> list = new ArrayList<>();
            for (int j = 0; j < subs.length(); j++) {
                JSONObject sub = subs.optJSONObject(j);
                if (sub == null) continue;
                String su = sub.optString("url", "");
                if (su.isEmpty()) continue;
                String mime = su.toLowerCase().contains(".srt")
                        ? MimeTypes.APPLICATION_SUBRIP : MimeTypes.TEXT_VTT;
                list.add(new MediaItem.SubtitleConfiguration.Builder(Uri.parse(su))
                        .setMimeType(mime)
                        .setLanguage(sub.optString("lang", "tr"))
                        .setLabel(sub.optString("label", sub.optString("lang", "Altyazı")))
                        .setSelectionFlags(j == 0 ? C.SELECTION_FLAG_DEFAULT : 0)
                        .build());
            }
            mb.setSubtitleConfigurations(list);
        }

        MediaSource ms = new DefaultMediaSourceFactory(this)
                .setDataSourceFactory(ds)
                .createMediaSource(mb.build());

        String label = title;
        if (sources.length() > 1) {
            label += "  ·  " + (name.isEmpty() ? "Kaynak" : name) + " (" + (i + 1) + "/" + sources.length() + ")";
        }
        info.setText(label);

        player.setMediaSource(ms);
        player.prepare();
        if (pos > 0) player.seekTo(pos);
        player.setPlayWhenReady(true);
    }

    private void finishWithResult() {
        if (finished) return;
        finished = true;
        Intent r = new Intent();
        r.putExtra("key", key);
        if (player != null) {
            long dur = player.getDuration();
            r.putExtra("pos", player.getCurrentPosition());
            r.putExtra("dur", dur == C.TIME_UNSET ? 0 : dur);
        }
        setResult(RESULT_OK, r);
        finish();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (player != null && e.getAction() == KeyEvent.ACTION_DOWN && !pv.isControllerFullyVisible()) {
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    player.seekTo(Math.max(0, player.getCurrentPosition() - 10000));
                    pv.showController();
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    player.seekTo(player.getCurrentPosition() + 10000);
                    pv.showController();
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    pv.showController();
                    return true;
                default:
                    break;
            }
        }
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(e);
        return pv.dispatchKeyEvent(e) || super.dispatchKeyEvent(e);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        finishWithResult();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
