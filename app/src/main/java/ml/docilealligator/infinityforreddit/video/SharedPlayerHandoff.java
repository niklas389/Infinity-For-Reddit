package ml.docilealligator.infinityforreddit.video;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

/**
 * Lends an inline (feed / post screen) player to {@code ViewVideoActivity}, so opening a video
 * full screen keeps the same player, buffer and position instead of starting a new one.
 * <p>
 * Flow:
 * <ol>
 *     <li>The inline player calls {@link #lend} right before starting the activity and puts the
 *     returned token into the intent.</li>
 *     <li>The activity calls {@link #claim}. A null result (token stale, media differs, process
 *     was recreated) means it builds its own player as before.</li>
 *     <li>When the activity finishes it calls {@link #giveBack}; the lender re-attaches the player
 *     and restores its own settings.</li>
 * </ol>
 * At most one player is lent at a time. If the lender is released while its player is lent
 * ({@link #abandon}), the player is discarded instead of returned once the activity is done.
 * All methods must be called on the main thread.
 */
@MainThread
@OptIn(markerClass = UnstableApi.class)
public final class SharedPlayerHandoff {

    public interface Lender {
        /**
         * The borrower is done.
         *
         * @param mediaReplaced true if the borrower loaded different media (e.g. a fallback URL)
         *                      into the player, so the lender has to prepare its own again.
         */
        void onPlayerReturned(@NonNull ExoPlayer player, boolean mediaReplaced);

        /** The lender was released while the player was lent: stop and release the player. */
        void onPlayerDiscarded(@NonNull ExoPlayer player);
    }

    private static long nextToken = 1;

    private static long token;
    @Nullable
    private static ExoPlayer player;
    @Nullable
    private static Lender lender;
    @Nullable
    private static String mediaUri;
    private static boolean claimed;
    private static boolean abandoned;
    private static boolean mediaReplaced;

    private SharedPlayerHandoff() {
    }

    /** @return a non-zero token identifying this loan. */
    public static long lend(@NonNull ExoPlayer exoPlayer, @Nullable String uri, @NonNull Lender playerLender) {
        if (player != null) {
            // A previous loan was never finished (e.g. the activity did not start). Close it.
            returnToLender();
        }
        token = nextToken++;
        player = exoPlayer;
        lender = playerLender;
        mediaUri = uri;
        claimed = false;
        abandoned = false;
        mediaReplaced = false;
        return token;
    }

    /**
     * @param uri the media URI the borrower is about to play; the loan is only handed out if it
     *            matches the lender's media.
     * @return the lent player, or null if the loan does not exist (any more) or does not match.
     * May be called again with the same token after the borrower was recreated.
     */
    @Nullable
    public static ExoPlayer claim(long loanToken, @Nullable String uri) {
        if (loanToken == 0 || loanToken != token || player == null) {
            return null;
        }
        if (uri == null || !uri.equals(mediaUri)) {
            returnToLender();
            return null;
        }
        claimed = true;
        return player;
    }

    /** The borrower is done with the player. */
    public static void giveBack(long loanToken) {
        if (loanToken != 0 && loanToken == token && player != null) {
            returnToLender();
        }
    }

    /** Ends a loan that no borrower has claimed yet, e.g. when the lender needs its player again. */
    public static void cancelIfUnclaimed(long loanToken) {
        if (loanToken != 0 && loanToken == token && player != null && !claimed) {
            returnToLender();
        }
    }

    /** The borrower is about to load other media into the player. */
    public static void markMediaReplaced(long loanToken) {
        if (loanToken != 0 && loanToken == token) {
            mediaReplaced = true;
        }
    }

    /** The lender no longer exists; discard the player when the loan ends. */
    public static void abandon(long loanToken) {
        if (loanToken != 0 && loanToken == token && player != null) {
            abandoned = true;
            if (!claimed) {
                returnToLender();
            }
        }
    }

    private static void returnToLender() {
        ExoPlayer returnedPlayer = player;
        Lender returnedLender = lender;
        boolean wasAbandoned = abandoned;
        boolean wasMediaReplaced = mediaReplaced;

        token = 0;
        player = null;
        lender = null;
        mediaUri = null;
        claimed = false;
        abandoned = false;
        mediaReplaced = false;

        if (returnedPlayer == null || returnedLender == null) {
            return;
        }
        if (wasAbandoned) {
            returnedLender.onPlayerDiscarded(returnedPlayer);
        } else {
            returnedLender.onPlayerReturned(returnedPlayer, wasMediaReplaced);
        }
    }
}
