package by.babanin.pipoker.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class RoomTest {

    private static Validator validator;

    @BeforeAll
    static void setup() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("Watcher creation")
    void addWatcher() {
        // Given
        String nickname = "test";
        Participant expected = Participant.createWatcher(nickname);
        Room room = new Room("test", new Deck());
        assertTrue(validator.validate(room).isEmpty());

        // Then
        Participant watcher = room.addWatcher(nickname);

        // When
        assertTrue(validator.validate(room).isEmpty());
        assertNotNull(watcher);
        assertTrue(validator.validate(watcher).isEmpty());
        assertAll(
                () -> assertEquals(nickname, watcher.getNickname()),
                () -> assertTrue(watcher.isWatcher()),
                () -> assertTrue(room.haveParticipants()),
                () -> assertTrue(room.containsParticipant(nickname)),
                () -> assertEquals(1, room.getParticipants().size()),
                () -> assertEquals(expected, room.getParticipant(nickname))
        );
    }

    @Test
    @DisplayName("Participant creation")
    void addParticipant() {
        // Given
        String nickname = "test";
        Participant expected = Participant.createParticipant(nickname);
        Room room = new Room("test", new Deck());
        assertTrue(validator.validate(room).isEmpty());

        // Then
        Participant participant = room.addParticipant(nickname);

        // When
        assertTrue(validator.validate(room).isEmpty());
        assertNotNull(participant);
        assertTrue(validator.validate(participant).isEmpty());
        assertAll(
                () -> assertEquals(nickname, participant.getNickname()),
                () -> assertFalse(participant.isWatcher()),
                () -> assertTrue(room.haveParticipants()),
                () -> assertTrue(room.containsParticipant(nickname)),
                () -> assertEquals(1, room.getParticipants().size()),
                () -> assertEquals(expected, room.getParticipant(nickname))
        );
    }

    @Test
    @DisplayName("Participant deletion")
    void removeParticipant() {
        // Given
        String nickname = "test";
        String cardValue = "1h";
        Deck deck = new Deck();
        deck.add(cardValue);
        Room room = new Room("test", deck);
        Participant participant = room.addParticipant(nickname);
        room.addVote(nickname, cardValue);
        assertTrue(validator.validate(room).isEmpty());

        // When
        Optional<Participant> removed = room.removeParticipant(nickname);

        // Then
        assertTrue(validator.validate(room).isEmpty());
        assertTrue(removed.isPresent());
        assertAll(
                () -> assertEquals(participant, removed.get()),
                () -> assertFalse(room.haveParticipants()),
                () -> assertTrue(room.getVotes().isEmpty())
        );
    }
}