package by.babanin.pipoker.bugreport;

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import by.babanin.pipoker.bugreport.BugReportDetails.Detail;

/**
 * Turns bug reports into issues of a Jira project, on behalf of the owner of an API token. The issue's summary is
 * the first line of the report, its description holds the whole report and what the page added to it.
 *
 * @see <a href="https://developer.atlassian.com/cloud/jira/platform/rest/v3/api-group-issues/#api-rest-api-3-issue-post">Create issue</a>
 */
public class JiraBugReportSender implements BugReportSender {

    // Marks the issues made from reports, so they can be found and told apart from the team's own ones
    static final String LABEL = "site-bug-report";

    private static final int SUMMARY_MAX_LENGTH = 100;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final RestClient restClient;
    private final String projectKey;
    private final String issueType;
    private final String environment;

    /**
     * @param url the site, like https://example.atlassian.net, or for a scoped API token
     * https://api.atlassian.com/ex/jira/{cloudId}
     * @param environment where the report came from, like qa or prod; it goes into the summary and the labels
     */
    public JiraBugReportSender(String url, String email, String apiToken, String projectKey, String issueType,
            String environment) {
        this(RestClient.builder().requestFactory(requestFactory()), url, email, apiToken, projectKey, issueType, environment);
    }

    JiraBugReportSender(RestClient.Builder restClientBuilder, String url, String email, String apiToken,
            String projectKey, String issueType, String environment) {
        this.restClient = restClientBuilder
                .baseUrl(URI.create(url.replaceAll("/+$", "")))
                .defaultHeaders(headers -> headers.setBasicAuth(email, apiToken))
                .build();
        this.projectKey = projectKey;
        this.issueType = issueType;
        this.environment = environment;
    }

    // A person waits for the answer on the page, so a request that hangs fails after a while
    private static JdkClientHttpRequestFactory requestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .proxy(ProxySelector.getDefault())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(TIMEOUT);
        return requestFactory;
    }

    @Override
    public void send(BugReportDto report) {
        try {
            restClient.post()
                    .uri("/rest/api/3/issue")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("fields", fields(report)))
                    .retrieve()
                    .toBodilessEntity();
        }
        catch(RestClientResponseException e) {
            throw new BugReportDeliveryException("Jira didn't take the bug report: " + e.getStatusCode()
                    + " " + e.getResponseBodyAsString());
        }
        catch(RestClientException e) {
            throw new BugReportDeliveryException("Jira can't be reached: " + e.getMessage());
        }
    }

    private Map<String, Object> fields(BugReportDto report) {
        List<String> labels = new ArrayList<>(List.of(LABEL));
        if(!environment.isBlank()) {
            labels.add(environment.toLowerCase());
        }
        return Map.of(
                "project", Map.of("key", projectKey),
                "issuetype", Map.of("name", issueType),
                "summary", summary(report.getMessage()),
                "labels", labels,
                "description", description(report));
    }

    // Like "[QA] The cards don't turn over": the first line of the report, cut to a readable length.
    // The label site-bug-report already says where the issue came from.
    String summary(String message) {
        String firstLine = message.strip().lines().findFirst().orElse("").strip();
        if(firstLine.length() > SUMMARY_MAX_LENGTH) {
            firstLine = firstLine.substring(0, SUMMARY_MAX_LENGTH - 1).strip() + "…";
        }
        String source = environment.isBlank() ? "" : "[" + environment.toUpperCase() + "] ";
        return source + firstLine;
    }

    // Jira's rich text (Atlassian Document Format): the report as the person wrote it, then a list of the details.
    // The person's text is plain text in it, nothing they type is taken as formatting.
    Map<String, Object> description(BugReportDto report) {
        List<Object> content = new ArrayList<>();
        content.add(paragraph(lines(report.getMessage().strip())));
        List<Detail> details = BugReportDetails.of(report);
        if(!details.isEmpty()) {
            content.add(Map.of("type", "bulletList", "content", details.stream()
                    .map(detail -> Map.of("type", "listItem", "content", List.of(paragraph(List.of(
                            Map.of("type", "text", "text", detail.label() + ": ", "marks", List.of(Map.of("type", "strong"))),
                            text(detail.value()))))))
                    .toList()));
        }
        return Map.of("type", "doc", "version", 1, "content", content);
    }

    // Line breaks stay as the person typed them. Jira refuses empty text, so an empty line is a break alone.
    private static List<Object> lines(String text) {
        List<Object> nodes = new ArrayList<>();
        text.lines().forEach(line -> {
            if(!nodes.isEmpty()) {
                nodes.add(Map.of("type", "hardBreak"));
            }
            if(!line.isEmpty()) {
                nodes.add(text(line));
            }
        });
        return nodes;
    }

    // An address is a link that opens from the issue
    private static Map<String, Object> text(String text) {
        if(text.startsWith("https://") || text.startsWith("http://")) {
            return Map.of("type", "text", "text", text, "marks", List.of(Map.of("type", "link", "attrs", Map.of("href", text))));
        }
        return Map.of("type", "text", "text", text);
    }

    private static Map<String, Object> paragraph(List<?> content) {
        return Map.of("type", "paragraph", "content", content);
    }
}
