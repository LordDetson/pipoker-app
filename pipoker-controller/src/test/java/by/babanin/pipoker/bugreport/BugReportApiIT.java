package by.babanin.pipoker.bugreport;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import by.babanin.pipoker.IntegrationTestContainers;

/**
 * Sends bug reports to the running application the way the web client does, from behind the proxy
 * that tells the browser's address in X-Forwarded-For. The limits count for the whole class, so each test
 * sends from its own addresses.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "bug-report.limit-per-address=2"
})
@ActiveProfiles("prod")
class BugReportApiIT {

    private static final String REPORT = """
            {"message": "The cards don't turn over", "contact": "@alex", "page": "https://pipoker.duckdns.org/",
             "language": "ru", "screen": "1920x1080", "voters": 4, "watchers": 1, "voted": 2,
             "round": "revealed", "estimate": "3", "unknown": "ignored"}""";

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        IntegrationTestContainers.start(registry);
    }

    @LocalServerPort
    private int port;

    @MockitoBean
    private BugReportSender sender;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    @DisplayName("A report is passed on to the owner")
    void report() throws Exception {
        // When
        HttpResponse<String> response = post(REPORT, "5.5.5.5");

        // Then
        assertEquals(204, response.statusCode());
        ArgumentCaptor<BugReportDto> report = ArgumentCaptor.forClass(BugReportDto.class);
        verify(sender).send(report.capture());
        assertAll(
                () -> assertEquals("The cards don't turn over", report.getValue().getMessage()),
                () -> assertEquals("@alex", report.getValue().getContact()),
                () -> assertEquals("1920x1080", report.getValue().getScreen()),
                () -> assertEquals(4, report.getValue().getVoters()),
                () -> assertEquals(RoundStage.REVEALED, report.getValue().getRound()),
                () -> assertEquals("3", report.getValue().getEstimate())
        );
    }

    @Test
    @DisplayName("Each browser address sends a limited number of reports")
    void limitPerAddress() throws Exception {
        // When, then
        assertEquals(204, post(REPORT, "1.1.1.1").statusCode());
        assertEquals(204, post(REPORT, "1.1.1.1").statusCode());
        assertEquals(429, post(REPORT, "1.1.1.1").statusCode());
        assertEquals(204, post(REPORT, "2.2.2.2").statusCode());
        verify(sender, times(3)).send(any());
    }

    @Test
    @DisplayName("A report without a message, with too long a one or with impossible details isn't taken")
    void invalid() throws Exception {
        // When, then
        assertEquals(400, post("{\"message\": \" \"}", "3.3.3.3").statusCode());
        assertEquals(400, post("{\"message\": \"" + "a".repeat(2001) + "\"}", "3.3.3.3").statusCode());
        assertEquals(400, post("{\"message\": \"Broken\", \"round\": \"finished\"}", "3.3.3.3").statusCode());
        assertEquals(400, post("{\"message\": \"Broken\", \"voters\": -1}", "3.3.3.3").statusCode());
        verify(sender, never()).send(any());
    }

    @Test
    @DisplayName("The page learns when a report didn't reach the owner")
    void notDelivered() throws Exception {
        // Given
        doThrow(new BugReportDeliveryException("Telegram can't be reached")).when(sender).send(any());

        // When, then
        assertEquals(503, post(REPORT, "4.4.4.4").statusCode());
    }

    private HttpResponse<String> post(String body, String browserAddress) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/bug-reports"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", browserAddress)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
