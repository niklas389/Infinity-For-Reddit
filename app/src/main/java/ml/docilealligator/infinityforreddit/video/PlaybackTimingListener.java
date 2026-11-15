package ml.docilealligator.infinityforreddit.video;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;

import java.io.IOException;

import ml.docilealligator.infinityforreddit.BuildConfig;

/**
 * Debug-only playback timing log, tag {@link #TAG}. Per playback it logs:
 * <ul>
 *     <li>startup: time from IDLE to BUFFERING (prepare) until the first rendered frame,</li>
 *     <li>every stall after playback had started, with its duration,</li>
 *     <li>every media/manifest load with duration and size (a near-zero duration with no
 *     matching InfinityNet line means it was served from the cache),</li>
 *     <li>load errors.</li>
 * </ul>
 * Read with: {@code adb logcat -s InfinityPlayback}
 */
@OptIn(markerClass = UnstableApi.class)
public class PlaybackTimingListener implements AnalyticsListener {

    public static final String TAG = "InfinityPlayback";

    private final String label;
    private long prepareRealtimeMs = -1;
    private boolean firstFrameLogged;
    private boolean wasReady;
    private long stallStartRealtimeMs = -1;
    private int stallCount;
    private int lastState = Player.STATE_IDLE;

    private PlaybackTimingListener(String label) {
        this.label = label;
    }

    /** Attaches a listener in debug builds; no-op in release builds. */
    public static void attachIfDebug(@NonNull ExoPlayer player, @NonNull String label) {
        if (BuildConfig.DEBUG) {
            player.addAnalyticsListener(new PlaybackTimingListener(label));
        }
    }

    @Override
    public void onPlaybackStateChanged(@NonNull EventTime eventTime, int state) {
        long now = eventTime.realtimeMs;
        if (state == Player.STATE_BUFFERING && lastState == Player.STATE_IDLE) {
            prepareRealtimeMs = now;
            firstFrameLogged = false;
            wasReady = false;
            stallStartRealtimeMs = -1;
            stallCount = 0;
        } else if (state == Player.STATE_BUFFERING && wasReady) {
            stallStartRealtimeMs = now;
        } else if (state == Player.STATE_READY) {
            if (!wasReady && prepareRealtimeMs >= 0) {
                Log.d(TAG, label + " ready after " + (now - prepareRealtimeMs) + "ms");
            }
            if (stallStartRealtimeMs >= 0) {
                stallCount++;
                Log.d(TAG, label + " stall #" + stallCount + " lasted "
                        + (now - stallStartRealtimeMs) + "ms");
                stallStartRealtimeMs = -1;
            }
            wasReady = true;
        } else if (state == Player.STATE_IDLE && prepareRealtimeMs >= 0 && !firstFrameLogged) {
            Log.d(TAG, label + " stopped before first frame after "
                    + (now - prepareRealtimeMs) + "ms");
        }
        lastState = state;
    }

    @Override
    public void onRenderedFirstFrame(@NonNull EventTime eventTime, @NonNull Object output, long renderTimeMs) {
        if (!firstFrameLogged && prepareRealtimeMs >= 0) {
            firstFrameLogged = true;
            Log.d(TAG, label + " first frame after " + (eventTime.realtimeMs - prepareRealtimeMs) + "ms");
        }
    }

    @Override
    public void onLoadCompleted(@NonNull EventTime eventTime, @NonNull LoadEventInfo loadEventInfo,
                                @NonNull MediaLoadData mediaLoadData) {
        // A load served entirely from SimpleCache typically completes in a few ms with no network.
        Log.d(TAG, label + " load " + dataTypeName(mediaLoadData.dataType)
                + " " + loadEventInfo.uri.getHost() + loadEventInfo.uri.getPath()
                + " " + loadEventInfo.loadDurationMs + "ms "
                + (loadEventInfo.bytesLoaded / 1024) + "KiB");
    }

    @Override
    public void onLoadError(@NonNull EventTime eventTime, @NonNull LoadEventInfo loadEventInfo,
                            @NonNull MediaLoadData mediaLoadData, @NonNull IOException error,
                            boolean wasCanceled) {
        Log.w(TAG, label + " load error " + dataTypeName(mediaLoadData.dataType)
                + " " + loadEventInfo.uri.getHost() + loadEventInfo.uri.getPath()
                + " after " + loadEventInfo.loadDurationMs + "ms: " + error);
    }

    private static String dataTypeName(int dataType) {
        switch (dataType) {
            case C.DATA_TYPE_MEDIA:
                return "media";
            case C.DATA_TYPE_MANIFEST:
                return "manifest";
            case C.DATA_TYPE_MEDIA_INITIALIZATION:
                return "init";
            default:
                return "type" + dataType;
        }
    }
}
