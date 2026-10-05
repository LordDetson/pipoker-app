package by.babanin.pipoker.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// On a developer's computer the web client runs on its own port (ng serve), so it calls the API from another origin.
// On the servers both are on one domain behind the proxy, and no other site may call the API from a browser.
@Configuration
@Profile("dev")
public class DevCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins("http://localhost:4200");
    }
}
