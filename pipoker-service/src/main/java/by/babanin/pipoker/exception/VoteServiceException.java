package by.babanin.pipoker.exception;

public class VoteServiceException extends PiPokerException {

    public VoteServiceException(ErrorCode code, String message) {
        super(code, message);
    }
}
