package by.babanin.pipoker.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import by.babanin.pipoker.exception.ConstraintException;
import by.babanin.pipoker.exception.VoteServiceException;
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


    @Test
    @DisplayName("Nicknames are unique in a room regardless of case")
    void addDuplicateParticipant() {
        // Given
        Room room = new Room("test", new Deck());
        room.addParticipant("Dmitry");

        // When and then
        assertAll(
                () -> assertThrows(ConstraintException.class, () -> room.addParticipant("dmitry")),
                () -> assertThrows(ConstraintException.class, () -> room.addWatcher(" DMITRY ")),
                () -> assertEquals(1, room.getParticipants().size())
        );
    }

    @Test
    @DisplayName("Participants are sorted by nickname")
    void sortedParticipants() {
        // Given
        Room room = new Room("test", new Deck());
        room.addParticipant("Dmitry");
        room.addWatcher("alex");
        room.addParticipant("Bob");

        // When
        List<String> nicknames = room.getParticipants().stream()
                .map(Participant::getNickname)
                .toList();

        // Then
        assertEquals(List.of("alex", "Bob", "Dmitry"), nicknames);
        assertThrows(UnsupportedOperationException.class,
                () -> room.getParticipants().add(Participant.createParticipant("Eve")));
    }

    @Test
    @DisplayName("Lookup of a missing participant")
    void missingParticipant() {
        Room room = new Room("test", new Deck());

        assertAll(
                () -> assertTrue(room.findParticipant("Dmitry").isEmpty()),
                () -> assertFalse(room.containsParticipant("Dmitry")),
                () -> assertThrows(ConstraintException.class, () -> room.getParticipant("Dmitry")),
                () -> assertTrue(room.removeParticipant("Dmitry").isEmpty())
        );
    }

    @Test
    @DisplayName("Participant votes and changes the vote")
    void addVote() {
        // Given
        Room room = new Room("test", deck("1", "2"));
        room.addParticipant("Dmitry");

        // When
        Vote first = room.addVote("Dmitry", "1");
        Vote changed = room.addVote("dmitry", "2");

        // Then
        assertAll(
                () -> assertEquals("1", first.getCard().getValue()),
                () -> assertEquals(1, room.getVotes().size()),
                () -> assertEquals(changed, room.getVote("Dmitry")),
                () -> assertEquals("2", room.getVote("Dmitry").getCard().getValue()),
                () -> assertTrue(validator.validate(room).isEmpty())
        );
    }

    @Test
    @DisplayName("Watcher can't vote")
    void watcherCantVote() {
        Room room = new Room("test", deck("1"));
        room.addWatcher("Alex");

        assertThrows(VoteServiceException.class, () -> room.addVote("Alex", "1"));
        assertTrue(room.getVotes().isEmpty());
    }

    @Test
    @DisplayName("Vote needs a participant of the room and a card of the deck")
    void invalidVote() {
        Room room = new Room("test", deck("1"));
        room.addParticipant("Dmitry");

        assertAll(
                () -> assertThrows(ConstraintException.class, () -> room.addVote("Alex", "1")),
                () -> assertThrows(ConstraintException.class, () -> room.addVote("Dmitry", "100")),
                () -> assertTrue(room.getVotes().isEmpty())
        );
    }

    @Test
    @DisplayName("Votes are sorted by nickname")
    void sortedVotes() {
        // Given
        Room room = new Room("test", deck("1", "2"));
        room.addParticipant("Dmitry");
        room.addParticipant("alex");
        room.addVote("Dmitry", "1");
        room.addVote("alex", "2");

        // When
        List<String> nicknames = room.getVotes().stream()
                .map(vote -> vote.getParticipant().getNickname())
                .toList();

        // Then
        assertEquals(List.of("alex", "Dmitry"), nicknames);
    }

    @Test
    @DisplayName("Vote removal")
    void removeVote() {
        // Given
        Room room = new Room("test", deck("1"));
        room.addParticipant("Dmitry");
        Vote vote = room.addVote("Dmitry", "1");

        // When
        Optional<Vote> removed = room.removeVote("DMITRY");

        // Then
        assertAll(
                () -> assertEquals(Optional.of(vote), removed),
                () -> assertTrue(room.findVote("Dmitry").isEmpty()),
                () -> assertThrows(VoteServiceException.class, () -> room.getVote("Dmitry")),
                () -> assertTrue(room.removeVote("Dmitry").isEmpty()),
                () -> assertTrue(room.containsParticipant("Dmitry"))
        );
    }

    @Test
    @DisplayName("Clearing votes keeps participants")
    void clearVotes() {
        Room room = new Room("test", deck("1"));
        room.addParticipant("Dmitry");
        room.addVote("Dmitry", "1");

        room.clearVotes();

        assertTrue(room.getVotes().isEmpty());
        assertTrue(room.containsParticipant("Dmitry"));
    }

    @Test
    @DisplayName("Clearing participants also clears votes")
    void clearParticipants() {
        Room room = new Room("test", deck("1"));
        room.addParticipant("Dmitry");
        room.addVote("Dmitry", "1");

        room.clearParticipants();

        assertFalse(room.haveParticipants());
        assertTrue(room.getVotes().isEmpty());
    }

    @Test
    @DisplayName("Changing the deck clears votes")
    void setDeck() {
        // Given
        Room room = new Room("test", deck("1"));
        room.addParticipant("Dmitry");
        room.addVote("Dmitry", "1");
        Deck newDeck = deck("S", "M", "L");

        // When
        room.setDeck(newDeck);
        room.setName("renamed");

        // Then
        assertAll(
                () -> assertEquals(newDeck, room.getDeck()),
                () -> assertEquals("renamed", room.getName()),
                () -> assertTrue(room.getVotes().isEmpty()),
                () -> assertTrue(room.containsParticipant("Dmitry"))
        );
    }

    @Test
    @DisplayName("Room name must have from 2 to 32 characters")
    void validateName() {
        assertAll(
                () -> assertTrue(validator.validate(new Room("ab", deck("1"))).isEmpty()),
                () -> assertFalse(validator.validate(new Room("a", deck("1"))).isEmpty()),
                () -> assertFalse(validator.validate(new Room("a".repeat(33), deck("1"))).isEmpty()),
                () -> assertFalse(validator.validate(new Room(" ", deck("1"))).isEmpty()),
                () -> assertFalse(validator.validate(new Room("test", null)).isEmpty())
        );
    }

    @Test
    @DisplayName("Rooms are equal by id")
    void equalsById() {
        Room room = new Room("test", deck("1"));
        Room other = new Room("test", deck("1"));

        assertNotEquals(room, other);
        assertEquals(room, room);
        assertTrue(room.toString().contains(room.getId().toString()));
    }

    private static Deck deck(String... cardValues) {
        Deck deck = new Deck();
        for(String cardValue : cardValues) {
            deck.add(cardValue);
        }
        return deck;
    }
}
