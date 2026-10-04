package by.babanin.pipoker.entity;

import java.time.Duration;
import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * The discussion timer of a room: counts down the given time for everyone in the room.
 * The room keeps the moment it runs out, so every page counts down to the same moment, also after a refresh.
 */
@Getter
@RequiredArgsConstructor
@EqualsAndHashCode
@ToString
public class Timer {

    // Long enough to say something, and not longer than a room stays open without activity
    public static final Duration MIN_DURATION = Duration.ofSeconds(10);
    public static final Duration MAX_DURATION = Duration.ofMinutes(30);

    // How long the timer runs, in seconds
    private final long seconds;

    @NotNull
    private final Instant endsAt;

    public static Timer start(Duration duration) {
        return new Timer(duration.toSeconds(), Instant.now().plus(duration));
    }

    /**
     * @return the time left until the timer runs out, zero when it already has
     */
    public Duration remaining() {
        Duration remaining = Duration.between(Instant.now(), endsAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }
}
