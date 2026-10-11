package by.babanin.pipoker.humancheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TurnstileHumanCheckTest {

    private final RestClient.Builder restClientBuilder = RestClient.builder();
    private final MockRestServiceServer cloudflare = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final TurnstileHumanCheck check = new TurnstileHumanCheck(restClientBuilder, "site-1", "secret-1", "bypass-1");

    private void cloudflareAnswers(String verdict) {
        cloudflare.expect(requestTo(TurnstileHumanCheck.VERIFY_URL))
                .andRespond(withSuccess(verdict, MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("The page runs the check with the site key")
    void siteKey() {
        assertEquals(Optional.of("site-1"), check.siteKey());
    }

    @Test
    @DisplayName("A token Cloudflare takes as a person's passes")
    void person() {
        // Given
        cloudflare.expect(requestTo(TurnstileHumanCheck.VERIFY_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"secret": "secret-1", "response": "token-1", "remoteip": "1.1.1.1"}"""))
                .andRespond(withSuccess("""
                        {"success": true, "challenge_ts": "2026-10-11T11:00:00.000Z", "hostname": "pipoker.app",
                         "error-codes": [], "action": "feedback", "cdata": ""}""", MediaType.APPLICATION_JSON));

        // When, then
        assertTrue(check.passed("token-1", "feedback", "1.1.1.1"));
        cloudflare.verify();
    }

    @Test
    @DisplayName("A token Cloudflare doesn't take doesn't pass")
    void robot() {
        // Given
        cloudflareAnswers("""
                {"success": false, "error-codes": ["invalid-input-response"]}""");

        // When, then
        assertFalse(check.passed("token-1", "feedback", "1.1.1.1"));
    }

    @Test
    @DisplayName("The server's own checks pass with the bypass token, and Cloudflare isn't asked")
    void bypass() {
        // When, then
        assertTrue(check.passed("bypass-1", "room", null));
        cloudflare.verify();
    }

    @Test
    @DisplayName("Without a bypass token nothing skips the check")
    void noBypass() {
        // Given
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(TurnstileHumanCheck.VERIFY_URL)).andRespond(withSuccess("""
                {"success": false, "error-codes": ["invalid-input-response"]}""", MediaType.APPLICATION_JSON));
        TurnstileHumanCheck withoutBypass = new TurnstileHumanCheck(builder, "site-1", "secret-1", "");

        // When, then
        assertFalse(withoutBypass.passed("", "room", null));
        assertFalse(withoutBypass.passed("anything", "room", null));
    }

    @Test
    @DisplayName("A request without a token doesn't pass, and Cloudflare isn't asked")
    void noToken() {
        // When, then
        assertFalse(check.passed(null, "feedback", "1.1.1.1"));
        assertFalse(check.passed(" ", "feedback", "1.1.1.1"));
        assertFalse(check.passed("a".repeat(2049), "feedback", "1.1.1.1"));
        cloudflare.verify();
    }

    @Test
    @DisplayName("When Cloudflare can't be reached, fails or doesn't know the secret key, the request passes")
    void checkBroken() {
        // Given
        cloudflare.expect(requestTo(TurnstileHumanCheck.VERIFY_URL)).andRespond(withException(new SocketTimeoutException()));
        cloudflare.expect(requestTo(TurnstileHumanCheck.VERIFY_URL)).andRespond(withServerError());
        cloudflareAnswers("""
                {"success": false, "error-codes": ["invalid-input-secret"]}""");

        // When, then
        assertTrue(check.passed("token-1", "feedback", "1.1.1.1"));
        assertTrue(check.passed("token-2", "feedback", "1.1.1.1"));
        assertTrue(check.passed("token-3", "feedback", "1.1.1.1"));
    }
}
