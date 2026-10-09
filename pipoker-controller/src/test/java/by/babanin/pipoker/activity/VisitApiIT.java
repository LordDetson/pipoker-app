package by.babanin.pipoker.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import by.babanin.pipoker.IntegrationTestContainers;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Reports visits to the running application the way the start page does and reads the counters it feeds.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
class VisitApiIT {

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        IntegrationTestContainers.start(registry);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private MeterRegistry registry;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    @DisplayName("Visits are counted by where people came from, and nothing else about them is kept")
    void visits() throws Exception {
        double habrBefore = visits("habr");
        double yandexBefore = visits("yandex");
        double directBefore = visits("direct");

        assertEquals(204, post("{\"from\": \"habr\", \"referrer\": \"https://habr.com/ru/articles/1/\"}").statusCode());
        assertEquals(204, post("{\"referrer\": \"https://yandex.ru/search/?text=planning+poker\"}").statusCode());
        assertEquals(204, post("{}").statusCode());

        assertEquals(habrBefore + 1, visits("habr"));
        assertEquals(yandexBefore + 1, visits("yandex"));
        assertEquals(directBefore + 1, visits("direct"));
    }

    @Test
    @DisplayName("A page can't invent a source of its own")
    void unknownSourcesAreCountedTogether() throws Exception {
        double otherBefore = visits("other_link");

        assertEquals(204, post("{\"from\": \"my-newsletter\"}").statusCode());
        assertEquals(400, post("{\"from\": \"" + "x".repeat(65) + "\"}").statusCode());

        assertEquals(otherBefore + 1, visits("other_link"));
        assertEquals(Source.values().length, registry.get("pipoker.visits").counters().size());
    }

    private double visits(String source) {
        return registry.get("pipoker.visits").tag("source", source).counter().count();
    }

    private HttpResponse<String> post(String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/visits"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
