package by.babanin.pipoker.exception;

/**
 * The data breaks the constraints of the entity it was meant for, see {@link ErrorCode#INVALID_DATA}.
 */
public class InvalidDataException extends RoomServiceException {

    public InvalidDataException(String message) {
        super(ErrorCode.INVALID_DATA, message);
    }
}
