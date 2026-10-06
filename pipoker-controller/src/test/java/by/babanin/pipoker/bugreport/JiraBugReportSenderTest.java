package by.babanin.pipoker.bugreport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class JiraBugReportSenderTest {

    private static final String CREATE_ISSUE_URL = "https://api.atlassian.com/ex/jira/cloud-1/rest/api/3/issue";

    private final RestClient.Builder restClientBuilder = RestClient.builder();
    private final MockRestServiceServer jira = MockRestServiceServer.bindTo(restClientBuilder).build();

    private JiraBugReportSender sender(String environment) {
        return new JiraBugReportSender(restClientBuilder, "https://api.atlassian.com/ex/jira/cloud-1/", "owner@example.com",
                "token-1", "PIP", "Bug", environment);
    }

    @Test
    @DisplayName("A report becomes an issue of the project, on behalf of the token's owner")
    void send() {
        // Given
        String credentials = Base64.getEncoder().encodeToString("owner@example.com:token-1".getBytes(StandardCharsets.UTF_8));
        jira.expect(requestTo(CREATE_ISSUE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + credentials))
                .andExpect(content().json("""
                        {"fields": {
                          "project": {"key": "PIP"},
                          "issuetype": {"name": "Bug"},
                          "summary": "[PROD] The cards don't turn over",
                          "labels": ["site-bug-report", "prod"],
                          "description": {"type": "doc", "version": 1, "content": [
                            {"type": "paragraph", "content": [{"type": "text", "text": "The cards don't turn over"}]},
                            {"type": "bulletList", "content": [
                              {"type": "listItem", "content": [{"type": "paragraph", "content": [
                                {"type": "text", "text": "Контакт: ", "marks": [{"type": "strong"}]},
                                {"type": "text", "text": "@alex"}]}]},
                              {"type": "listItem", "content": [{"type": "paragraph", "content": [
                                {"type": "text", "text": "Страница: ", "marks": [{"type": "strong"}]},
                                {"type": "text", "text": "https://pipoker.duckdns.org/room/3f2b6a8e-0c1d-4e5f-9a7b-1c2d3e4f5a6b",
                                 "marks": [{"type": "link", "attrs": {"href": "https://pipoker.duckdns.org/room/3f2b6a8e-0c1d-4e5f-9a7b-1c2d3e4f5a6b"}}]}]}]},
                              {"type": "listItem", "content": [{"type": "paragraph", "content": [
                                {"type": "text", "text": "Комната: ", "marks": [{"type": "strong"}]},
                                {"type": "text", "text": "3f2b6a8e-0c1d-4e5f-9a7b-1c2d3e4f5a6b"}]}]},
                              {"type": "listItem", "content": [{"type": "paragraph", "content": [
                                {"type": "text", "text": "Участники: ", "marks": [{"type": "strong"}]},
                                {"type": "text", "text": "голосуют 4, наблюдают 1"}]}]},
                              {"type": "listItem", "content": [{"type": "paragraph", "content": [
                                {"type": "text", "text": "Раунд: ", "marks": [{"type": "strong"}]},
                                {"type": "text", "text": "голосование, проголосовали 2 из 4"}]}]},
                              {"type": "listItem", "content": [{"type": "paragraph", "content": [
                                {"type": "text", "text": "Время: ", "marks": [{"type": "strong"}]},
                                {"type": "text", "text": "2026-10-05 17:05:00 +03:00 (Europe/Minsk)"}]}]},
                              {"type": "listItem", "content": [{"type": "paragraph", "content": [
                                {"type": "text", "text": "Язык: ", "marks": [{"type": "strong"}]},
                                {"type": "text", "text": "ru (браузер: ru-RU, en)"}]}]},
                              {"type": "listItem", "content": [{"type": "paragraph", "content": [
                                {"type": "text", "text": "Экран: ", "marks": [{"type": "strong"}]},
                                {"type": "text", "text": "1920x1080 (окно 1366x768)"}]}]},
                              {"type": "listItem", "content": [{"type": "paragraph", "content": [
                                {"type": "text", "text": "Браузер: ", "marks": [{"type": "strong"}]},
                                {"type": "text", "text": "Mozilla/5.0 Firefox/150.0"}]}]}
                            ]}
                          ]}
                        }}""", true))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\": \"10200\", \"key\": \"PIP-50\"}"));

        // When
        sender("prod").send(BugReportDetailsTest.FULL_REPORT);

        // Then
        jira.verify();
    }

    @Test
    @DisplayName("The person's line breaks stay, and an empty line is a break of its own")
    void lineBreaks() {
        // Given
        jira.expect(requestTo(CREATE_ISSUE_URL))
                .andExpect(content().json("""
                        {"fields": {
                          "summary": "First line",
                          "labels": ["site-bug-report"],
                          "description": {"content": [{"type": "paragraph", "content": [
                            {"type": "text", "text": "First line"},
                            {"type": "hardBreak"},
                            {"type": "hardBreak"},
                            {"type": "text", "text": "*not bold*"}
                          ]}]}
                        }}"""))
                .andRespond(withStatus(HttpStatus.CREATED));

        // When
        sender("").send(BugReportDto.builder().message("\n First line\n\n*not bold*\n").build());

        // Then
        jira.verify();
    }

    @Test
    @DisplayName("A long first line is cut in the summary")
    void longSummary() {
        String summary = sender("qa").summary("a".repeat(150) + "\nsecond line");

        assertEquals("[QA] " + "a".repeat(99) + "…", summary);
    }

    @Test
    @DisplayName("An issue Jira refuses is reported with its answer")
    void refused() {
        // Given
        jira.expect(requestTo(CREATE_ISSUE_URL))
                .andRespond(withBadRequest().body("{\"errors\": {\"issuetype\": \"Specify a valid issue type\"}}"));

        // When
        BugReportDeliveryException e = assertThrows(BugReportDeliveryException.class,
                () -> sender("qa").send(BugReportDto.builder().message("Broken").build()));

        // Then
        assertEquals("Jira didn't take the bug report: 400 BAD_REQUEST {\"errors\": {\"issuetype\": \"Specify a valid issue type\"}}",
                e.getMessage());
    }

    @Test
    @DisplayName("A Jira that can't be reached is reported")
    void unreachable() {
        // Given
        jira.expect(requestTo(CREATE_ISSUE_URL)).andRespond(withException(new SocketTimeoutException("timed out")));

        // When, then
        assertThrows(BugReportDeliveryException.class, () -> sender("qa").send(BugReportDto.builder().message("Broken").build()));
    }
}
