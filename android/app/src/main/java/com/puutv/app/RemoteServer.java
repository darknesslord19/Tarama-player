package com.puutv.app;

import android.content.Context;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import fi.iki.elonen.NanoHTTPD;

/** Telefondan kumanda için yerel ağda çalışan küçük HTTP sunucusu. */
public class RemoteServer extends NanoHTTPD {

    public interface Host {
        /** JS'i arayüz iş parçacığında çalıştır, sonucu beklemeden dön. */
        void fire(String code);
        /** JS ifadesini çalıştırıp sonucunu (metin olarak) bekle. */
        String eval(String expr);
        void onNewRemote(String ip);
    }

    private static final List<String> KEYS = Arrays.asList(
            "up", "down", "left", "right", "ok", "back", "chup", "chdown", "menu");
    private static final List<String> CMDS = Arrays.asList(
            "theme", "full", "volup", "voldown", "refresh", "aspect", "src", "fav");

    private final Context ctx;
    private final String token;
    private final Host host;
    private final Set<String> seen = Collections.synchronizedSet(new HashSet<>());

    public RemoteServer(Context ctx, int port, String token, Host host) {
        super(port);
        this.ctx = ctx.getApplicationContext();
        this.token = token;
        this.host = host;
    }

    @Override
    @SuppressWarnings("deprecation")
    public Response serve(IHTTPSession s) {
        String uri = s.getUri();
        Map<String, String> q = s.getParms();
        try {
            if (uri.equals("/") || uri.equals("/index.html") || uri.equals("/remote")) {
                return asset("remote.html", "text/html; charset=utf-8");
            }
            if (!uri.startsWith("/api/")) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "bulunamadi");
            }
            if (!token.equals(q.get("t"))) {
                return json(Response.Status.FORBIDDEN, "{\"error\":\"yetkisiz\"}");
            }
            String ip = s.getRemoteIpAddress();
            if (ip != null && seen.add(ip)) host.onNewRemote(ip);

            switch (uri) {
                case "/api/key": {
                    String k = q.get("k");
                    if (!KEYS.contains(k)) return bad();
                    host.fire("onKey(" + JSONObject.quote(k) + ")");
                    return ok();
                }
                case "/api/cmd": {
                    String c = q.get("c");
                    if (!CMDS.contains(c)) return bad();
                    host.fire("remoteCmd(" + JSONObject.quote(c) + ")");
                    return ok();
                }
                case "/api/play": {
                    int no;
                    try { no = Integer.parseInt(q.get("no")); } catch (Exception e) { return bad(); }
                    host.fire("playNo(" + no + ")");
                    return ok();
                }
                case "/api/state":
                    return jsJson("JSON.stringify(window.remoteState ? remoteState() : {})");
                case "/api/channels":
                    return jsJson("JSON.stringify(window.remoteChannels ? remoteChannels() : [])");
                default:
                    return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "bulunamadi");
            }
        } catch (Exception e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", String.valueOf(e));
        }
    }

    private Response jsJson(String expr) throws Exception {
        String raw = host.eval(expr);
        if (raw == null || raw.equals("null")) return json(Response.Status.SERVICE_UNAVAILABLE, "{\"error\":\"hazir degil\"}");
        Object v = new JSONTokener(raw).nextValue();   // evaluateJavascript sonucu JSON-kodlu bir metin
        return json(Response.Status.OK, String.valueOf(v));
    }

    private Response asset(String name, String mime) throws Exception {
        try (InputStream in = ctx.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            Response r = newFixedLengthResponse(Response.Status.OK, mime, out.toString("UTF-8"));
            r.addHeader("Cache-Control", "no-store");
            return r;
        }
    }

    private static Response json(Response.Status st, String body) {
        Response r = newFixedLengthResponse(st, "application/json; charset=utf-8", body);
        r.addHeader("Cache-Control", "no-store");
        return r;
    }

    private static Response ok() { return json(Response.Status.OK, "{\"ok\":true}"); }

    private static Response bad() { return json(Response.Status.BAD_REQUEST, "{\"error\":\"gecersiz\"}"); }

    /** Cihazın yerel ağ (Wi-Fi/Ethernet) IPv4 adresi. */
    public static String localIp() {
        String fallback = null;
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                String name = ni.getName() == null ? "" : ni.getName();
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || a.isLoopbackAddress()) continue;
                    String ip = a.getHostAddress();
                    if (a.isSiteLocalAddress() && (name.startsWith("wlan") || name.startsWith("eth"))) return ip;
                    if (a.isSiteLocalAddress() && fallback == null) fallback = ip;
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }
}
