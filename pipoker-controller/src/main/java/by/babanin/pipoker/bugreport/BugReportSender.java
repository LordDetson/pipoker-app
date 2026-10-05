package by.babanin.pipoker.bugreport;

/**
 * Delivers a bug report to the owner of the site.
 */
public interface BugReportSender {

    /**
     * @throws BugReportDeliveryException if the report didn't reach its destination
     */
    void send(BugReportDto report);
}
