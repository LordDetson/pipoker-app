package by.babanin.pipoker.config;

import java.time.Clock;
import java.time.Duration;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import by.babanin.pipoker.bugreport.BugReportRateLimiter;
import by.babanin.pipoker.bugreport.BugReportSender;
import by.babanin.pipoker.bugreport.BugReportService;
import by.babanin.pipoker.bugreport.JiraBugReportSender;
import by.babanin.pipoker.bugreport.LoggingBugReportSender;
import lombok.extern.log4j.Log4j2;

@Configuration
@Log4j2
public class BugReportConfig {

    @Bean
    BugReportService bugReportService(BugReportSender bugReportSender,
            @Value("${bug-report.limit-per-address}") int limitPerAddress,
            @Value("${bug-report.limit-in-total}") int limitInTotal,
            @Value("${bug-report.limit-window}") Duration limitWindow) {
        BugReportRateLimiter rateLimiter = new BugReportRateLimiter(limitPerAddress, limitInTotal, limitWindow,
                Clock.systemUTC());
        return new BugReportService(rateLimiter, bugReportSender);
    }

    // Reports become Jira issues once the server is given the site and an API token; until then they are only logged
    @Bean
    BugReportSender bugReportSender(@Value("${bug-report.jira.url}") String url,
            @Value("${bug-report.jira.email}") String email,
            @Value("${bug-report.jira.api-token}") String apiToken,
            @Value("${bug-report.jira.project}") String project,
            @Value("${bug-report.jira.issue-type}") String issueType,
            @Value("${bug-report.environment}") String environment) {
        if(Stream.of(url, email, apiToken).anyMatch(String::isBlank)) {
            log.warn("Jira isn't set up for bug reports (JIRA_URL, JIRA_EMAIL, JIRA_API_TOKEN), they are only logged");
            return new LoggingBugReportSender();
        }
        return new JiraBugReportSender(url, email, apiToken, project, issueType, environment);
    }
}
