package ml.docilealligator.infinityforreddit.video;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;

public final class AudioTrackPreference {

    private AudioTrackPreference() {
    }

    /**
     * Reddit HLS usually has two audio renditions without channel information: the first is mono,
     * the second (index 1) stereo, so the default selection picks mono. Selects index 1 with the
     * same override ViewVideoActivity applies, so a player handed over to full screen keeps its
     * audio track (an equal override is a no-op and triggers no re-selection).
     */
    public static void preferStereo(@NonNull ExoPlayer player, @NonNull Tracks.Group group) {
        if (group.getType() != C.TRACK_TYPE_AUDIO || group.length < 2) {
            return;
        }
        TrackSelectionOverride override = new TrackSelectionOverride(group.getMediaTrackGroup(), 1);
        TrackSelectionParameters parameters = player.getTrackSelectionParameters();
        if (override.equals(parameters.overrides.get(group.getMediaTrackGroup()))) {
            return;
        }
        player.setTrackSelectionParameters(parameters.buildUpon().setOverrideForType(override).build());
    }
}
