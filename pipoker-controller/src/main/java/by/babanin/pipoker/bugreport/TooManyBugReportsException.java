package by.babanin.pipoker.bugreport;

public class TooManyBugReportsException extends RuntimeException {

    public TooManyBugReportsException() {
        super("Too many bug reports, try again later");
    }
}
