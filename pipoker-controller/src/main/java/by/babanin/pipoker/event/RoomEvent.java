package by.babanin.pipoker.event;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import by.babanin.pipoker.model.ParticipantDto;
import by.babanin.pipoker.model.VoteDto;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoomEvent {

    public enum EventType {
        PARTICIPANT_ADDED,
        PARTICIPANT_REMOVED,
        PARTICIPANT_RETURNED,
        VOTE_ADDED,
        VOTE_REMOVED,
        CLEAR_VOTES,
        SHOW_VOTES,
        /**
         * The room was closed because nobody did anything in it for long, and everyone left it.
         */
        ROOM_CLOSED
    }

    @NotNull
    private UUID roomId;

    @NotNull
    private EventType eventType;

    private ParticipantDto participant;

    private VoteDto vote;

    public RoomEvent(UUID roomId, EventType eventType) {
        this(roomId, eventType, null, null);
    }

    public RoomEvent(UUID roomId, EventType eventType, ParticipantDto participant) {
        this(roomId, eventType, participant, null);
    }

    public RoomEvent(UUID roomId, EventType eventType, VoteDto vote) {
        this(roomId, eventType, null, vote);
    }
}
