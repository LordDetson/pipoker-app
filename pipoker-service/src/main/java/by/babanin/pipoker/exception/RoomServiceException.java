package by.babanin.pipoker.exception;

public class RoomServiceException extends PiPokerException {

    public RoomServiceException(ErrorCode code, String message) {
        super(code, message);
    }
}
