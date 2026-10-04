package by.babanin.pipoker.exception;

/**
 * Why a request was refused. The web client shows its own text for each code in the language of the page,
 * so the codes are part of the API: rename or remove one only together with the client.
 */
public enum ErrorCode {

    /**
     * The room doesn't exist: everyone has left it, it was closed for inactivity, or the link is wrong.
     */
    ROOM_NOT_FOUND,

    /**
     * Someone with the same nickname (ignoring case and spaces around it) is already in the room.
     */
    NICKNAME_TAKEN,

    /**
     * The person is no longer in the room, for example they were removed after losing the connection.
     */
    PARTICIPANT_NOT_FOUND,

    /**
     * Watchers don't vote.
     */
    WATCHER_CANNOT_VOTE,

    /**
     * The card isn't in the room's deck.
     */
    CARD_NOT_IN_DECK,

    /**
     * The cards of the round are revealed, so its discussion timer can't start: the next round can have one.
     */
    CARDS_REVEALED,

    /**
     * The request breaks a limit the client checks too, such as the length of a nickname or the size of a deck.
     */
    INVALID_DATA,

    /**
     * Anything else: a bug or a failure on the server rather than something the person can fix.
     */
    UNEXPECTED
}
