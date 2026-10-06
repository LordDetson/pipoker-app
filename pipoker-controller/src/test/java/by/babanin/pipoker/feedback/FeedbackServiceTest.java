package by.babanin.pipoker.feedback;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeedbackServiceTest {

    private final FeedbackRateLimiter rateLimiter = mock(FeedbackRateLimiter.class);
    private final FeedbackSender sender = mock(FeedbackSender.class);
    private final FeedbackService service = new FeedbackService(rateLimiter, sender);
    private final FeedbackDto feedback = FeedbackDto.builder().kind(FeedbackKind.PROBLEM).message("Broken").build();

    @Test
    @DisplayName("Feedback within the limits goes to the owner")
    void withinLimits() {
        // Given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(true);

        // When
        service.send(feedback, "1.1.1.1");

        // Then
        verify(sender).send(feedback);
    }

    @Test
    @DisplayName("Feedback over the limit isn't sent")
    void overLimit() {
        // Given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(false);

        // When, then
        assertThrows(TooMuchFeedbackException.class, () -> service.send(feedback, "1.1.1.1"));
        verify(sender, never()).send(any());
    }
}
