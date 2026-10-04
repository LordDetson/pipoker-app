package by.babanin.pipoker.event;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import by.babanin.pipoker.model.ParticipantDto;
import by.babanin.pipoker.model.RoundDto;
import by.babanin.pipoker.model.TimerDto;
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
         * Someone started the discussion timer, in place of the one that may be running.
         */
        TIMER_STARTED,
        /**
         * Someone stopped the discussion timer before it ran out, or took away the one that had.
         */
        TIMER_STOPPED,
        /**
         * The room was closed because nobody did anything in it for long, and everyone left it.
         */
        ROOM_CLOSED,
        /**
         * Everyone left the room, so it was deleted. Only pages that aren't at the table get it, like a join form;
         * it may come before the PARTICIPANT_REMOVED of the last person who left.
         */
        ROOM_REMOVED
    }

    @NotNull
    private UUID roomId;

    @NotNull
    private EventType eventType;

    private ParticipantDto participant;

    private VoteDto vote;

    // SHOW_VOTES: the round that entered the room's history, none when the cards were already revealed
    private RoundDto round;

    // TIMER_STARTED: the timer that was started
    private TimerDto timer;

    public RoomEvent(UUID roomId, EventType eventType) {
        this(roomId, eventType, null, null, null, null);
    }

    public RoomEvent(UUID roomId, EventType eventType, ParticipantDto participant) {
        this(roomId, eventType, participant, null, null, null);
    }

    public RoomEvent(UUID roomId, EventType eventType, VoteDto vote) {
        this(roomId, eventType, null, vote, null, null);
    }

    public RoomEvent(UUID roomId, EventType eventType, TimerDto timer) {
        this(roomId, eventType, null, null, null, timer);
    }

    public RoomEvent(UUID roomId, EventType eventType, RoundDto round) {
        this(roomId, eventType, null, null, round, null);
    }
}
