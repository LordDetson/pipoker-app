package by.babanin.pipoker.feedback;

public class TooMuchFeedbackException extends RuntimeException {

    public TooMuchFeedbackException() {
        super("Too much feedback lately, try again later");
    }
}
