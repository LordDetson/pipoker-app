package by.babanin.pipoker.bugreport;

/**
 * Takes bug reports from the site, within the limits, and passes them on to the owner.
 */
public class BugReportService {

    private final BugReportRateLimiter rateLimiter;
    private final BugReportSender sender;

    public BugReportService(BugReportRateLimiter rateLimiter, BugReportSender sender) {
        this.rateLimiter = rateLimiter;
        this.sender = sender;
    }

    /**
     * @param address the address of the browser that sent the report
     * @throws TooManyBugReportsException if the address or everyone together sent too many reports lately
     * @throws BugReportDeliveryException if the report didn't reach the owner
     */
    public void report(BugReportDto report, String address) {
        if(!rateLimiter.tryAcquire(address)) {
            throw new TooManyBugReportsException();
        }
        sender.send(report);
    }
}
