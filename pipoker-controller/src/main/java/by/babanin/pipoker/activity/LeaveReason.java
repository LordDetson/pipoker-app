package by.babanin.pipoker.activity;

/**
 * Why someone left a room.
 */
public enum LeaveReason {

    /**
     * Left the room with the button, or was removed by someone in the room.
     */
    LEFT("left", "leaving the room"),
    PAGE_CLOSED("page_closed", "closing the page"),
    CONNECTION_LOST("connection_lost", "losing the connection"),
    /**
     * Didn't come back after the backend restarted.
     */
    RESTART("restart", "the restart"),
    /**
     * Was in a room that nobody did anything in for long, so the room was closed.
     */
    ROOM_CLOSED("room_closed", "the room was closed for inactivity");

    private final String tag;
    private final String description;

    LeaveReason(String tag, String description) {
        this.tag = tag;
        this.description = description;
    }

    /**
     * The value of the {@code reason} tag of the metric.
     */
    public String tag() {
        return tag;
    }

    /**
     * Finishes the sentence "left the room after ...".
     */
    public String description() {
        return description;
    }
}
