package ml.docilealligator.infinityforreddit.video;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection;
import androidx.media3.exoplayer.upstream.BandwidthMeter;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;

/**
 * Makes a full-screen player start on the same HLS variant the feed player was showing, so the
 * segments the feed already put into SimpleCache are reused instead of downloading another
 * rendition from scratch.
 * <p>
 * {@link AdaptiveTrackSelection} picks its initial variant as the highest bitrate that fits in
 * {@code bitrateEstimate * bandwidthFraction}. Until the player's first media load completes,
 * this meter reports an estimate at which exactly the feed's bitrate fits; after that it reports
 * the shared process-wide estimate, and normal adaptation (including switching up once enough is
 * buffered) takes over. No track selection override is used, so lifting the seed never triggers a
 * track re-selection or discards buffered media.
 * <p>
 * Everything except {@link #getBitrateEstimate()} delegates to the process-wide
 * {@link DefaultBandwidthMeter}, which therefore keeps learning from this player's transfers.
 */
@OptIn(markerClass = UnstableApi.class)
public class SeededBandwidthMeter implements BandwidthMeter {

    private final BandwidthMeter delegate;
    private volatile long seededEstimate;

    private SeededBandwidthMeter(Context context, int initialVideoBitrate) {
        delegate = DefaultBandwidthMeter.getSingletonInstance(context);
        // +1 compensates for float rounding so the feed's variant is not just above the limit.
        seededEstimate = (long) Math.ceil(
                initialVideoBitrate / (double) AdaptiveTrackSelection.DEFAULT_BANDWIDTH_FRACTION) + 1;
    }

    /**
     * @param initialVideoBitrate {@code Format.bitrate} of the variant the feed was playing;
     *                            values {@code <= 0} disable seeding.
     * @return a seeded meter, or the plain process-wide meter when there is nothing to seed.
     */
    @NonNull
    public static BandwidthMeter create(@NonNull Context context, int initialVideoBitrate) {
        if (initialVideoBitrate <= 0) {
            return DefaultBandwidthMeter.getSingletonInstance(context);
        }
        return new SeededBandwidthMeter(context, initialVideoBitrate);
    }

    /**
     * Ends seeding once the player's first media chunk has loaded (from cache or network), or
     * playback fails. Call right after building the player. No-op for non-seeded meters.
     */
    public static void releaseSeedAfterFirstLoad(@NonNull BandwidthMeter meter, @NonNull ExoPlayer player) {
        if (!(meter instanceof SeededBandwidthMeter)) {
            return;
        }
        SeededBandwidthMeter seeded = (SeededBandwidthMeter) meter;
        player.addAnalyticsListener(new AnalyticsListener() {
            @Override
            public void onLoadCompleted(@NonNull EventTime eventTime, @NonNull LoadEventInfo loadEventInfo,
                                        @NonNull MediaLoadData mediaLoadData) {
                if (mediaLoadData.dataType == androidx.media3.common.C.DATA_TYPE_MEDIA) {
                    release();
                }
            }

            @Override
            public void onPlayerError(@NonNull EventTime eventTime,
                                      @NonNull androidx.media3.common.PlaybackException error) {
                release();
            }

            private void release() {
                seeded.seededEstimate = 0;
                player.removeAnalyticsListener(this);
            }
        });
    }

    @Override
    public long getBitrateEstimate() {
        long seed = seededEstimate;
        return seed > 0 ? seed : delegate.getBitrateEstimate();
    }

    @Override
    public long getTimeToFirstByteEstimateUs() {
        return delegate.getTimeToFirstByteEstimateUs();
    }

    @Nullable
    @Override
    public TransferListener getTransferListener() {
        return delegate.getTransferListener();
    }

    @Override
    public void addEventListener(@NonNull Handler eventHandler, @NonNull EventListener eventListener) {
        delegate.addEventListener(eventHandler, eventListener);
    }

    @Override
    public void removeEventListener(@NonNull EventListener eventListener) {
        delegate.removeEventListener(eventListener);
    }
}
