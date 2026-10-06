package by.babanin.pipoker.feedback;

import java.util.stream.Collectors;

import lombok.extern.log4j.Log4j2;

/**
 * Keeps feedback in the log of the backend. Used where Jira isn't set up, for example on a developer's computer.
 */
@Log4j2
public class LoggingFeedbackSender implements FeedbackSender {

    @Override
    public void send(FeedbackDto feedback) {
        String details = FeedbackDetails.of(feedback).stream()
                .map(detail -> detail.label() + ": " + detail.value())
                .collect(Collectors.joining("\n"));
        log.warn("Feedback, {} (Jira isn't set up, so it is only in this log):\n{}\n\n{}", feedback.getKind().label(),
                feedback.getMessage().strip(), details);
    }
}
