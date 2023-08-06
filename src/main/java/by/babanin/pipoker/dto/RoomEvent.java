package by.babanin.pipoker.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoomEvent {

    public enum RoomEventType {
        JOINED,
        LEFT,
        VOTED,
        SHOW_VOTING_RESULT,
        VOTE_CLEARED
    }

    private RoomEventType type;
    private Participant participant;
    private Card card;
}
