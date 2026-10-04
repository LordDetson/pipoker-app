package by.babanin.pipoker.exception;

public class ConstraintException extends PiPokerException {

    public ConstraintException(ErrorCode code, String message) {
        super(code, message);
    }
}
