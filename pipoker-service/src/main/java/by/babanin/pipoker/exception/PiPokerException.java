package by.babanin.pipoker.exception;

import lombok.Getter;

/**
 * A refused request. The message explains it to developers in the logs, the code tells the person why.
 */
@Getter
public abstract class PiPokerException extends RuntimeException {

    private final ErrorCode code;

    protected PiPokerException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }
}
