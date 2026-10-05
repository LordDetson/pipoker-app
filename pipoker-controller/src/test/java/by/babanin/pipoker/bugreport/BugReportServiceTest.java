package by.babanin.pipoker.bugreport;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BugReportServiceTest {

    private final BugReportRateLimiter rateLimiter = mock(BugReportRateLimiter.class);
    private final BugReportSender sender = mock(BugReportSender.class);
    private final BugReportService service = new BugReportService(rateLimiter, sender);
    private final BugReportDto report = BugReportDto.builder().message("Broken").build();

    @Test
    @DisplayName("A report within the limits goes to the owner")
    void withinLimits() {
        // Given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(true);

        // When
        service.report(report, "1.1.1.1");

        // Then
        verify(sender).send(report);
    }

    @Test
    @DisplayName("A report over the limit isn't sent")
    void overLimit() {
        // Given
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(false);

        // When, then
        assertThrows(TooManyBugReportsException.class, () -> service.report(report, "1.1.1.1"));
        verify(sender, never()).send(any());
    }
}
