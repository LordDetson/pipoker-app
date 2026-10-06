package by.babanin.pipoker.feedback;

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
 * Sends feedback to the running application the way the web client does, from behind the proxy
 * that tells the browser's address in X-Forwarded-For. The limits count for the whole class, so each test
 * sends from its own addresses.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "feedback.limit-per-address=2"
})
@ActiveProfiles("prod")
class FeedbackApiIT {

    private static final String PROBLEM = """
            {"kind": "problem", "message": "The cards don't turn over", "contact": "@alex", "page": "https://pipoker.duckdns.org/",
             "language": "ru", "screen": "1920x1080", "voters": 4, "watchers": 1, "voted": 2,
             "round": "revealed", "estimate": "3", "unknown": "ignored"}""";

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        IntegrationTestContainers.start(registry);
    }

    @LocalServerPort
    private int port;

    @MockitoBean
    private FeedbackSender sender;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    @DisplayName("Feedback is passed on to the owner")
    void send() throws Exception {
        // When
        HttpResponse<String> response = post(PROBLEM, "5.5.5.5");

        // Then
        assertEquals(204, response.statusCode());
        ArgumentCaptor<FeedbackDto> feedback = ArgumentCaptor.forClass(FeedbackDto.class);
        verify(sender).send(feedback.capture());
        assertAll(
                () -> assertEquals(FeedbackKind.PROBLEM, feedback.getValue().getKind()),
                () -> assertEquals("The cards don't turn over", feedback.getValue().getMessage()),
                () -> assertEquals("@alex", feedback.getValue().getContact()),
                () -> assertEquals("1920x1080", feedback.getValue().getScreen()),
                () -> assertEquals(4, feedback.getValue().getVoters()),
                () -> assertEquals(RoundStage.REVEALED, feedback.getValue().getRound()),
                () -> assertEquals("3", feedback.getValue().getEstimate())
        );
    }

    @Test
    @DisplayName("Each browser address sends a limited number of messages")
    void limitPerAddress() throws Exception {
        // When, then
        assertEquals(204, post(PROBLEM, "1.1.1.1").statusCode());
        assertEquals(204, post(PROBLEM, "1.1.1.1").statusCode());
        assertEquals(429, post(PROBLEM, "1.1.1.1").statusCode());
        assertEquals(204, post(PROBLEM, "2.2.2.2").statusCode());
        verify(sender, times(3)).send(any());
    }

    @Test
    @DisplayName("Feedback without a kind or a message, with too long a message or with impossible details isn't taken")
    void invalid() throws Exception {
        // When, then
        assertEquals(400, post("{\"message\": \"Broken\"}", "3.3.3.3").statusCode());
        assertEquals(400, post("{\"kind\": \"praise\", \"message\": \"Broken\"}", "3.3.3.3").statusCode());
        assertEquals(400, post("{\"kind\": \"idea\", \"message\": \" \"}", "3.3.3.3").statusCode());
        assertEquals(400, post("{\"kind\": \"idea\", \"message\": \"" + "a".repeat(2001) + "\"}", "3.3.3.3").statusCode());
        assertEquals(400, post("{\"kind\": \"problem\", \"message\": \"Broken\", \"round\": \"finished\"}", "3.3.3.3").statusCode());
        assertEquals(400, post("{\"kind\": \"problem\", \"message\": \"Broken\", \"voters\": -1}", "3.3.3.3").statusCode());
        verify(sender, never()).send(any());
    }

    @Test
    @DisplayName("The page learns when feedback didn't reach the owner")
    void notDelivered() throws Exception {
        // Given
        doThrow(new FeedbackDeliveryException("Jira can't be reached")).when(sender).send(any());

        // When, then
        assertEquals(503, post(PROBLEM, "4.4.4.4").statusCode());
    }

    private HttpResponse<String> post(String body, String browserAddress) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/feedback"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", browserAddress)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
