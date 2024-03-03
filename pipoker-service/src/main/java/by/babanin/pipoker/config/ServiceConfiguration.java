package by.babanin.pipoker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

@Configuration
@Profile("prod")
@EnableMongoRepositories
@PropertySources({
        @PropertySource("classpath:logback.properties"),
        @PropertySource("classpath:mongodbconfig.properties"),
})
public class ServiceConfiguration {

    @Bean
    ValidatorFactory validatorFactory() {
        return Validation.buildDefaultValidatorFactory();
    }

    @Bean
    Validator validator(ValidatorFactory validatorFactory) {
        return validatorFactory.getValidator();
    }
}
