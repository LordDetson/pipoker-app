package by.babanin.pipoker.feedback;

import by.babanin.pipoker.humancheck.HumanCheck;

/**
 * Takes feedback from the site, within the limits and from people only, and passes it on to the owner.
 */
public class FeedbackService {

    // What the page runs the human check for before sending feedback
    static final String HUMAN_CHECK_ACTION = "feedback";

    private final FeedbackRateLimiter rateLimiter;
    private final HumanCheck humanCheck;
    private final FeedbackSender sender;

    public FeedbackService(FeedbackRateLimiter rateLimiter, HumanCheck humanCheck, FeedbackSender sender) {
        this.rateLimiter = rateLimiter;
        this.humanCheck = humanCheck;
        this.sender = sender;
    }

    /**
     * @param address the address of the browser that sent the feedback
     * @throws TooMuchFeedbackException if the address or everyone together sent too much feedback lately
     * @throws NotHumanException if the human check says a robot sent the feedback
     * @throws FeedbackDeliveryException if the feedback didn't reach the owner
     */
    public void send(FeedbackDto feedback, String address) {
        if(!rateLimiter.tryAcquire(address)) {
            throw new TooMuchFeedbackException();
        }
        // After the limits, so that a flood of requests doesn't turn into a flood of questions to the check
        if(!humanCheck.passed(feedback.getHumanCheckToken(), HUMAN_CHECK_ACTION, address)) {
            throw new NotHumanException();
        }
        sender.send(feedback);
    }
}
