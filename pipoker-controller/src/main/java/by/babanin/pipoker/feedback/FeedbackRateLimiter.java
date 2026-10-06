package by.babanin.pipoker.feedback;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Limits how many messages of feedback are taken in a window of time: from one address, and from everyone together.
 * The total limit keeps a flood from many addresses from burying the owner's chat.
 * <p>
 * Each address counts its messages in a window that starts with its first one. The counts live in memory only,
 * so a restart of the backend starts them anew.
 */
public class FeedbackRateLimiter {

    // Above this many addresses, the ones whose window has passed are forgotten
    private static final int ADDRESSES_TO_SWEEP = 10_000;
    private static final String EVERYONE = "";

    private final int limitPerAddress;
    private final int limitInTotal;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();

    public FeedbackRateLimiter(int limitPerAddress, int limitInTotal, Duration window, Clock clock) {
        this.limitPerAddress = limitPerAddress;
        this.limitInTotal = limitInTotal;
        this.window = window;
        this.clock = clock;
    }

    /**
     * Counts a message from the address.
     *
     * @return false if the address or everyone together has already sent as many messages as the window allows
     */
    public synchronized boolean tryAcquire(String address) {
        Instant now = clock.instant();
        if(windows.size() > ADDRESSES_TO_SWEEP) {
            windows.values().removeIf(counted -> counted.isOver(now));
        }
        Window ofAddress = current(address, now);
        Window ofEveryone = current(EVERYONE, now);
        if(ofAddress.count >= limitPerAddress || ofEveryone.count >= limitInTotal) {
            return false;
        }
        ofAddress.count++;
        ofEveryone.count++;
        return true;
    }

    private Window current(String key, Instant now) {
        Window counted = windows.get(key);
        if(counted == null || counted.isOver(now)) {
            counted = new Window(now.plus(window));
            windows.put(key, counted);
        }
        return counted;
    }

    private static final class Window {

        private final Instant end;
        private int count;

        private Window(Instant end) {
            this.end = end;
        }

        private boolean isOver(Instant now) {
            return !now.isBefore(end);
        }
    }
}
