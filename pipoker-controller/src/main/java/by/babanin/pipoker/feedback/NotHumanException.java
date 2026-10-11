package by.babanin.pipoker.feedback;

public class NotHumanException extends RuntimeException {

    public NotHumanException() {
        super("The human check didn't pass");
    }
}
