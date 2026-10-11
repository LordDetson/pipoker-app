package by.babanin.pipoker.humancheck;

/**
 * How the page runs the human check.
 *
 * @param siteKey the key the page runs Cloudflare Turnstile with; null when the check is off and the page skips it
 */
public record HumanCheckDto(String siteKey) {
}
