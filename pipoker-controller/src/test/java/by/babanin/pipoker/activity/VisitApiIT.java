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
        // Every source but the checks and tests, which are never counted
        assertEquals(Source.values().length - 1, registry.get("pipoker.visits").counters().size());
    }

    @Test
    @DisplayName("A check or a test is no visitor")
    void testsAreNotCounted() throws Exception {
        double before = registry.get("pipoker.visits").counters().stream().mapToDouble(counter -> counter.count()).sum();

        assertEquals(204, post("{\"from\": \"test\"}").statusCode());

        assertEquals(before, registry.get("pipoker.visits").counters().stream().mapToDouble(counter -> counter.count()).sum());
    }

    @Test
    @DisplayName("A search engine's robot running the page is counted apart from people")
    void robotsAreCountedApart() throws Exception {
        double botBefore = visits("bot");
        double habrBefore = visits("habr");

        assertEquals(204, post("{\"from\": \"habr\"}",
                "Mozilla/5.0 (compatible; YandexBot/3.0; +http://yandex.com/bots)").statusCode());

        assertEquals(botBefore + 1, visits("bot"));
        assertEquals(habrBefore, visits("habr"));
    }

    private double visits(String source) {
        return registry.get("pipoker.visits").tag("source", source).counter().count();
    }

    private HttpResponse<String> post(String json) throws Exception {
        return post(json, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36");
    }

    private HttpResponse<String> post(String json, String userAgent) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/visits"))
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
