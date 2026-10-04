package by.babanin.pipoker.exception;

/**
 * The room doesn't exist: everyone has left it, it was closed for inactivity, or the link is wrong.
 */
public class RoomNotFoundException extends RoomServiceException {

    public RoomNotFoundException(String message) {
        super(ErrorCode.ROOM_NOT_FOUND, message);
    }
}
