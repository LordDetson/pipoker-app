package by.babanin.pipoker.humancheck;

import by.babanin.pipoker.exception.ErrorCode;
import by.babanin.pipoker.exception.PiPokerException;

/**
 * The human check says a robot sent the request.
 */
public class NotHumanException extends PiPokerException {

    public NotHumanException(String action) {
        super(ErrorCode.NOT_HUMAN, "The human check didn't pass for " + action);
    }
}
