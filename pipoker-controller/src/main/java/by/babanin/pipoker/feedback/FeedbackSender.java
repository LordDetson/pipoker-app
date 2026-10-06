package by.babanin.pipoker.feedback;

/**
 * Delivers feedback from the site to its owner.
 */
public interface FeedbackSender {

    /**
     * @throws FeedbackDeliveryException if the feedback didn't reach its destination
     */
    void send(FeedbackDto feedback);
}
