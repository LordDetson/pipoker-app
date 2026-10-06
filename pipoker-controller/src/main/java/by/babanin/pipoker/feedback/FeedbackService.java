package by.babanin.pipoker.feedback;

/**
 * Takes feedback from the site, within the limits, and passes it on to the owner.
 */
public class FeedbackService {

    private final FeedbackRateLimiter rateLimiter;
    private final FeedbackSender sender;

    public FeedbackService(FeedbackRateLimiter rateLimiter, FeedbackSender sender) {
        this.rateLimiter = rateLimiter;
        this.sender = sender;
    }

    /**
     * @param address the address of the browser that sent the feedback
     * @throws TooMuchFeedbackException if the address or everyone together sent too much feedback lately
     * @throws FeedbackDeliveryException if the feedback didn't reach the owner
     */
    public void send(FeedbackDto feedback, String address) {
        if(!rateLimiter.tryAcquire(address)) {
            throw new TooMuchFeedbackException();
        }
        sender.send(feedback);
    }
}
