package by.babanin.pipoker.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;

import by.babanin.pipoker.event.ErrorEvent;
import by.babanin.pipoker.event.RoomEvent;
import by.babanin.pipoker.event.RoomEvent.EventType;

/**
 * Checks the messages exchanged with the web client: their JSON form and their equality rules.
 */
@JsonTest
class ModelTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Participants are equal by nickname regardless of case")
    void participantEquality() {
        ParticipantDto participant = new ParticipantDto("Dmitry", false);

        assertAll(
                () -> assertEquals(participant, new ParticipantDto("DMITRY", true)),
                () -> assertEquals(participant.hashCode(), new ParticipantDto("dmitry", false).hashCode()),
                () -> assertNotEquals(participant, new ParticipantDto("Alex", false)),
                () -> assertTrue(new ParticipantDto("alex", false).compareTo(participant) < 0),
                () -> assertEquals("ParticipantDto(nickname=Dmitry)", participant.toString())
        );
    }

    @Test
    @DisplayName("Votes are equal by nickname regardless of case")
    void voteEquality() {
        VoteDto vote = new VoteDto("Dmitry", "1");

        assertAll(
                () -> assertEquals(vote, new VoteDto("dmitry", "2")),
                () -> assertNotEquals(vote, new VoteDto("Alex", "1")),
                () -> assertTrue(new VoteDto("alex", "1").compareTo(vote) < 0)
        );
    }

    @Test
    @DisplayName("Deck text representation")
    void deckToString() {
        DeckDto deck = new DeckDto();
        assertEquals("Doesn't have cards", deck.toString());

        deck.getCards().addAll(List.of("1", "2"));
        assertEquals("[1; 2]", deck.toString());
    }

    @Test
    @DisplayName("Room creation request is read from the web client's JSON")
    void readRoomCreation() throws Exception {
        String json = """
                {"name": "Sprint 42", "deck": {"cards": ["1", "2", "1"]},
                 "participants": [{"nickname": "Dmitry", "watcher": false}, {"nickname": "Alex", "watcher": true}]}
                """;

        RoomCreationDto room = objectMapper.readValue(json, RoomCreationDto.class);

        assertAll(
                () -> assertEquals("Sprint 42", room.getName()),
                () -> assertEquals(List.of("1", "2"), List.copyOf(room.getDeck().getCards())),
                () -> assertEquals(2, room.getParticipants().size()),
                () -> assertTrue(room.getParticipants().contains(new ParticipantDto("Alex", true)))
        );
    }

    @Test
    @DisplayName("Room event JSON leaves out what the event doesn't carry")
    void writeRoomEvent() throws Exception {
        UUID roomId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        String cleared = objectMapper.writeValueAsString(new RoomEvent(roomId, EventType.CLEAR_VOTES));
        String voted = objectMapper.writeValueAsString(new RoomEvent(roomId, EventType.VOTE_ADDED, new VoteDto("Dmitry", "5")));

        assertEquals("{\"roomId\":\"00000000-0000-0000-0000-000000000001\",\"eventType\":\"CLEAR_VOTES\"}", cleared);
        assertEquals("{\"roomId\":\"00000000-0000-0000-0000-000000000001\",\"eventType\":\"VOTE_ADDED\","
                + "\"vote\":{\"nickname\":\"Dmitry\",\"card\":\"5\"}}", voted);
    }

    @Test
    @DisplayName("Revealed cards event JSON carries the round that entered the history, with its time as text")
    void writeShowVotesEvent() throws Exception {
        UUID roomId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        RoundDto round = new RoundDto(Instant.parse("2026-10-04T17:00:00.123Z"), List.of(new VoteDto("Dmitry", "5")));

        String json = objectMapper.writeValueAsString(new RoomEvent(roomId, EventType.SHOW_VOTES, round));

        assertEquals("{\"roomId\":\"00000000-0000-0000-0000-000000000001\",\"eventType\":\"SHOW_VOTES\","
                + "\"round\":{\"revealedAt\":\"2026-10-04T17:00:00.123Z\",\"votes\":[{\"nickname\":\"Dmitry\",\"card\":\"5\"}]}}", json);
    }

    @Test
    @DisplayName("Room JSON leaves out empty participants, votes and history")
    void writeRoom() throws Exception {
        DeckDto deck = new DeckDto();
        deck.getCards().add("1");
        RoomDto room = RoomDto.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .name("test")
                .deck(deck)
                .build();

        String json = objectMapper.writeValueAsString(room);

        assertEquals("{\"id\":\"00000000-0000-0000-0000-000000000001\",\"name\":\"test\",\"deck\":{\"cards\":[\"1\"]}}", json);
    }

    @Test
    @DisplayName("Error event JSON")
    void writeErrorEvent() throws Exception {
        String json = objectMapper.writeValueAsString(new ErrorEvent("/app/room/create", "Deck can't be null", null));

        assertEquals("{\"destination\":\"/app/room/create\",\"message\":\"Deck can't be null\"}", json);
    }

    @Test
    @DisplayName("Error event JSON of a missing room")
    void writeRoomNotFoundErrorEvent() throws Exception {
        String json = objectMapper.writeValueAsString(new ErrorEvent("/app/room/1", "Room \"1\" is not found",
                ErrorEvent.Code.ROOM_NOT_FOUND));

        assertEquals("{\"destination\":\"/app/room/1\",\"message\":\"Room \\\"1\\\" is not found\",\"code\":\"ROOM_NOT_FOUND\"}", json);
    }
}
