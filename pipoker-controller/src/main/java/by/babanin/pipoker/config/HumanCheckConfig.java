package by.babanin.pipoker.config;

import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import by.babanin.pipoker.humancheck.HumanCheck;
import by.babanin.pipoker.humancheck.NoHumanCheck;
import by.babanin.pipoker.humancheck.TurnstileHumanCheck;
import lombok.extern.log4j.Log4j2;

@Configuration
@Log4j2
public class HumanCheckConfig {

    // The check is on once the server is given both keys of a Turnstile widget; until then every request passes
    @Bean
    HumanCheck humanCheck(@Value("${human-check.turnstile.site-key}") String siteKey,
            @Value("${human-check.turnstile.secret-key}") String secretKey) {
        if(Stream.of(siteKey, secretKey).anyMatch(String::isBlank)) {
            log.warn("The human check isn't set up (TURNSTILE_SITE_KEY, TURNSTILE_SECRET_KEY), every request passes");
            return new NoHumanCheck();
        }
        return new TurnstileHumanCheck(siteKey, secretKey);
    }
}
