package by.babanin.pipoker.bugreport;

import java.util.stream.Collectors;

import lombok.extern.log4j.Log4j2;

/**
 * Keeps bug reports in the log of the backend. Used where Jira isn't set up, for example on a developer's computer.
 */
@Log4j2
public class LoggingBugReportSender implements BugReportSender {

    @Override
    public void send(BugReportDto report) {
        String details = BugReportDetails.of(report).stream()
                .map(detail -> detail.label() + ": " + detail.value())
                .collect(Collectors.joining("\n"));
        log.warn("Bug report (Jira isn't set up, so it is only in this log):\n{}\n\n{}", report.getMessage().strip(), details);
    }
}
