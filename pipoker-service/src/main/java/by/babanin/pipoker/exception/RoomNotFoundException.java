package by.babanin.pipoker.exception;

import lombok.experimental.StandardException;

/**
 * The room doesn't exist: everyone has left it, it was closed for inactivity, or the link is wrong.
 */
@StandardException
public class RoomNotFoundException extends RoomServiceException {

}
