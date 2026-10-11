package by.babanin.pipoker.humancheck;

import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.extern.log4j.Log4j2;

/**
 * Cloudflare Turnstile: the page runs Cloudflare's check with the site key, and the server asks Cloudflare, with the
 * secret key, whether the token the page got is a person's. A token passes once and only for a few minutes.
 * The server's own checks, which create rooms without a browser (the monitoring of PROD, the load tests), send the
 * bypass token in place of Cloudflare's token; it is a secret of the server like the secret key.
 * <p>
 * The action the page names goes to Cloudflare's analytics; it isn't checked here, because Cloudflare's test keys,
 * which QA runs with, answer without it.
 * <p>
 * When Cloudflare can't be reached or doesn't know the secret key, the request passes: a person's message is worth more
 * than keeping a robot out while the check is broken, and the limits of the request still hold.
 *
 * @see <a href="https://developers.cloudflare.com/turnstile/get-started/server-side-validation/">Server-side validation</a>
 */
@Log4j2
public class TurnstileHumanCheck implements HumanCheck {

    static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    // What Cloudflare answers when the secret key is wrong or missing: the server's fault, not the page's
    private static final Set<String> SECRET_ERRORS = Set.of("missing-input-secret", "invalid-input-secret");
    // Cloudflare's tokens are at most this long
    private static final int TOKEN_MAX_LENGTH = 2048;
    // A person waits for the answer on the page
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient;
    private final String siteKey;
    private final String secretKey;
    // Empty when nothing may skip the check
    private final byte[] bypassToken;

    public TurnstileHumanCheck(String siteKey, String secretKey, String bypassToken) {
        this(RestClient.builder().requestFactory(requestFactory()), siteKey, secretKey, bypassToken);
    }

    TurnstileHumanCheck(RestClient.Builder restClientBuilder, String siteKey, String secretKey, String bypassToken) {
        this.restClient = restClientBuilder.build();
        this.siteKey = siteKey;
        this.secretKey = secretKey;
        this.bypassToken = bypassToken.getBytes(StandardCharsets.UTF_8);
    }

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
    public Optional<String> siteKey() {
        return Optional.of(siteKey);
    }

    @Override
    public boolean passed(String token, String action, String address) {
        if(token == null || token.isBlank() || token.length() > TOKEN_MAX_LENGTH) {
            log.info("A request for {} came without a human check token", action);
            return false;
        }
        // Compared in constant time, so the time of the answer doesn't tell how much of a guess was right
        if(bypassToken.length > 0 && MessageDigest.isEqual(bypassToken, token.getBytes(StandardCharsets.UTF_8))) {
            return true;
        }
        Verdict verdict;
        try {
            verdict = restClient.post()
                    .uri(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request(token, address))
                    .retrieve()
                    .body(Verdict.class);
        }
        catch(RestClientException e) {
            log.warn("Cloudflare can't check the token for {}, so it passes: {}", action, e.getMessage());
            return true;
        }
        if(verdict != null && verdict.errorCodes() != null && verdict.errorCodes().stream().anyMatch(SECRET_ERRORS::contains)) {
            log.error("Cloudflare doesn't know the secret key (TURNSTILE_SECRET_KEY), so the request for {} passes: {}",
                    action, verdict.errorCodes());
            return true;
        }
        if(verdict == null || !verdict.success()) {
            log.info("Cloudflare didn't take the token for {}: {}", action, verdict == null ? "no answer" : verdict.errorCodes());
            return false;
        }
        return true;
    }

    private Map<String, String> request(String token, String address) {
        Map<String, String> request = new HashMap<>();
        request.put("secret", secretKey);
        request.put("response", token);
        // Cloudflare weighs the token against the address it was got from
        if(address != null) {
            request.put("remoteip", address);
        }
        return request;
    }

    // What Cloudflare answers, of what is used here
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Verdict(boolean success, @JsonProperty("error-codes") List<String> errorCodes) {
    }
}
