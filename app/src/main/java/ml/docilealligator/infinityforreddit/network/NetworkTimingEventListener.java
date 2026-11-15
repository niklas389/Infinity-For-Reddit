package ml.docilealligator.infinityforreddit.network;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;

import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.EventListener;
import okhttp3.Handshake;
import okhttp3.Protocol;
import okhttp3.Response;

/**
 * Debug-only per-request timing log. One line per finished call, tag {@link #TAG}:
 * <pre>
 * GET v.redd.it/abc/HLS_720.ts new h2 dns=12 connect=48 tls=61 ttfb=140 total=310
 * GET v.redd.it/abc/HLS_720.ts reused h2 ttfb=35 total=120
 * </pre>
 * "new" means a fresh TCP/TLS connection was opened for the call; "reused" means a pooled one was
 * used. The query string is dropped so tokens and signatures are not logged.
 * <p>
 * Read with: {@code adb logcat -s InfinityNet}
 */
public class NetworkTimingEventListener extends EventListener {

    public static final String TAG = "InfinityNet";

    public static final EventListener.Factory FACTORY = call -> new NetworkTimingEventListener();

    private long callStart;
    private long dnsStart = -1, dnsMs = -1;
    private long connectStart = -1, connectMs = -1;
    private long tlsStart = -1, tlsMs = -1;
    private long ttfbMs = -1;
    private boolean newConnection;
    @Nullable
    private Protocol protocol;

    private static long now() {
        return SystemClock.elapsedRealtime();
    }

    @Override
    public void callStart(@NonNull Call call) {
        callStart = now();
    }

    @Override
    public void dnsStart(@NonNull Call call, @NonNull String domainName) {
        dnsStart = now();
    }

    @Override
    public void dnsEnd(@NonNull Call call, @NonNull String domainName, @NonNull List<InetAddress> inetAddressList) {
        if (dnsStart >= 0) dnsMs = now() - dnsStart;
    }

    @Override
    public void connectStart(@NonNull Call call, @NonNull InetSocketAddress inetSocketAddress, @NonNull Proxy proxy) {
        connectStart = now();
        newConnection = true;
    }

    @Override
    public void secureConnectStart(@NonNull Call call) {
        tlsStart = now();
    }

    @Override
    public void secureConnectEnd(@NonNull Call call, @Nullable Handshake handshake) {
        if (tlsStart >= 0) tlsMs = now() - tlsStart;
    }

    @Override
    public void connectEnd(@NonNull Call call, @NonNull InetSocketAddress inetSocketAddress,
                           @NonNull Proxy proxy, @Nullable Protocol protocol) {
        if (connectStart >= 0) connectMs = now() - connectStart;
    }

    @Override
    public void connectionAcquired(@NonNull Call call, @NonNull Connection connection) {
        protocol = connection.protocol();
    }

    @Override
    public void responseHeadersEnd(@NonNull Call call, @NonNull Response response) {
        if (ttfbMs < 0) ttfbMs = now() - callStart;
    }

    @Override
    public void callEnd(@NonNull Call call) {
        log(call, null);
    }

    @Override
    public void callFailed(@NonNull Call call, @NonNull IOException ioe) {
        log(call, ioe);
    }

    @Override
    public void canceled(@NonNull Call call) {
        Log.d(TAG, describe(call) + " canceled after " + (now() - callStart) + "ms");
    }

    private void log(Call call, @Nullable IOException failure) {
        StringBuilder sb = new StringBuilder(describe(call))
                .append(newConnection ? " new" : " reused");
        if (protocol != null) sb.append(' ').append(protocol);
        if (dnsMs >= 0) sb.append(" dns=").append(dnsMs);
        if (connectMs >= 0) sb.append(" connect=").append(connectMs);
        if (tlsMs >= 0) sb.append(" tls=").append(tlsMs);
        if (ttfbMs >= 0) sb.append(" ttfb=").append(ttfbMs);
        sb.append(" total=").append(now() - callStart);
        if (failure != null) {
            sb.append(" FAILED ").append(failure.getClass().getSimpleName())
                    .append(": ").append(failure.getMessage());
            Log.w(TAG, sb.toString());
        } else {
            Log.d(TAG, sb.toString());
        }
    }

    private static String describe(Call call) {
        return call.request().method() + " " + call.request().url().host()
                + call.request().url().encodedPath();
    }
}
