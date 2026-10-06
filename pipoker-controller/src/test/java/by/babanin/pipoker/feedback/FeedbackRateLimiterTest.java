package by.babanin.pipoker.feedback;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeedbackRateLimiterTest {

    private static final Duration WINDOW = Duration.ofHours(1);

    // Moved by the tests
    private Instant now = Instant.parse("2026-10-05T12:00:00Z");
    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    };

    @Test
    @DisplayName("An address sends up to its limit in a window, and again once the window has passed")
    void limitPerAddress() {
        // Given
        FeedbackRateLimiter limiter = new FeedbackRateLimiter(2, 100, WINDOW, clock);

        // When, then
        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertFalse(limiter.tryAcquire("1.1.1.1"));
        assertTrue(limiter.tryAcquire("2.2.2.2"), "Another address has its own limit");

        // When
        now = now.plus(WINDOW).minusSeconds(1);

        // Then
        assertFalse(limiter.tryAcquire("1.1.1.1"));

        // When
        now = now.plusSeconds(1);

        // Then
        assertTrue(limiter.tryAcquire("1.1.1.1"));
    }

    @Test
    @DisplayName("Everyone together sends up to the total limit in a window")
    void limitInTotal() {
        // Given
        FeedbackRateLimiter limiter = new FeedbackRateLimiter(5, 3, WINDOW, clock);

        // When, then
        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertTrue(limiter.tryAcquire("2.2.2.2"));
        assertTrue(limiter.tryAcquire("3.3.3.3"));
        assertFalse(limiter.tryAcquire("4.4.4.4"));

        // When
        now = now.plus(WINDOW);

        // Then
        assertTrue(limiter.tryAcquire("4.4.4.4"));
    }

    @Test
    @DisplayName("A refused message isn't counted")
    void refusedNotCounted() {
        // Given
        FeedbackRateLimiter limiter = new FeedbackRateLimiter(1, 2, WINDOW, clock);
        assertTrue(limiter.tryAcquire("1.1.1.1"));

        // When
        assertFalse(limiter.tryAcquire("1.1.1.1"));

        // Then
        assertTrue(limiter.tryAcquire("2.2.2.2"), "The refused message took no place in the total limit");
    }

    @Test
    @DisplayName("Addresses whose window has passed are forgotten when there are many of them")
    void sweep() {
        // Given
        FeedbackRateLimiter limiter = new FeedbackRateLimiter(1, Integer.MAX_VALUE, WINDOW, clock);
        for(int i = 0; i <= 10_000; i++) {
            assertTrue(limiter.tryAcquire("address " + i));
        }
        now = now.plus(WINDOW);

        // When, then
        assertTrue(limiter.tryAcquire("one more"));
        assertTrue(limiter.tryAcquire("address 1"));
    }
}
