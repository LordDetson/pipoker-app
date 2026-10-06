package by.babanin.pipoker.config;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import by.babanin.pipoker.feedback.FeedbackKind;
import by.babanin.pipoker.feedback.FeedbackRateLimiter;
import by.babanin.pipoker.feedback.FeedbackSender;
import by.babanin.pipoker.feedback.FeedbackService;
import by.babanin.pipoker.feedback.JiraFeedbackSender;
import by.babanin.pipoker.feedback.LoggingFeedbackSender;
import lombok.extern.log4j.Log4j2;

@Configuration
@Log4j2
public class FeedbackConfig {

    @Bean
    FeedbackService feedbackService(FeedbackSender feedbackSender,
            @Value("${feedback.limit-per-address}") int limitPerAddress,
            @Value("${feedback.limit-in-total}") int limitInTotal,
            @Value("${feedback.limit-window}") Duration limitWindow) {
        FeedbackRateLimiter rateLimiter = new FeedbackRateLimiter(limitPerAddress, limitInTotal, limitWindow,
                Clock.systemUTC());
        return new FeedbackService(rateLimiter, feedbackSender);
    }

    // Feedback becomes Jira issues once the server is given the site and an API token; until then it is only logged
    @Bean
    FeedbackSender feedbackSender(@Value("${feedback.jira.url}") String url,
            @Value("${feedback.jira.email}") String email,
            @Value("${feedback.jira.api-token}") String apiToken,
            @Value("${feedback.jira.project}") String project,
            @Value("${feedback.jira.issue-type.problem}") String problemIssueType,
            @Value("${feedback.jira.issue-type.idea}") String ideaIssueType,
            @Value("${feedback.jira.issue-type.review}") String reviewIssueType,
            @Value("${feedback.environment}") String environment) {
        if(Stream.of(url, email, apiToken).anyMatch(String::isBlank)) {
            log.warn("Jira isn't set up for feedback (JIRA_URL, JIRA_EMAIL, JIRA_API_TOKEN), it is only logged");
            return new LoggingFeedbackSender();
        }
        Map<FeedbackKind, String> issueTypes = Map.of(
                FeedbackKind.PROBLEM, problemIssueType,
                FeedbackKind.IDEA, ideaIssueType,
                FeedbackKind.REVIEW, reviewIssueType);
        return new JiraFeedbackSender(url, email, apiToken, project, issueTypes, environment);
    }
}
