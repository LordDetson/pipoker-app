package by.babanin.pipoker.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class ParticipantTest {

    private static Validator validator;

    @BeforeAll
    static void setup() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("Participant and watcher creation")
    void create() {
        Participant participant = Participant.createParticipant(" Dmitry ");
        Participant watcher = Participant.createWatcher("Alex");

        assertAll(
                () -> assertEquals("Dmitry", participant.getNickname()),
                () -> assertFalse(participant.isWatcher()),
                () -> assertEquals("Alex", watcher.getNickname()),
                () -> assertTrue(watcher.isWatcher())
        );
    }

    @Test
    @DisplayName("Participants with the same nickname are equal regardless of case and role")
    void equalsIgnoringCase() {
        Participant participant = Participant.createParticipant("Dmitry");
        Participant watcher = Participant.createWatcher("DMITRY");

        assertAll(
                () -> assertEquals(participant, watcher),
                () -> assertEquals(participant.hashCode(), watcher.hashCode()),
                () -> assertEquals(0, participant.compareTo(watcher)),
                () -> assertNotEquals(participant, Participant.createParticipant("Alex"))
        );
    }

    @Test
    @DisplayName("Participants are sorted by nickname ignoring case")
    void compare() {
        Participant alex = Participant.createParticipant("alex");
        Participant dmitry = Participant.createParticipant("Dmitry");

        assertAll(
                () -> assertTrue(alex.compareTo(dmitry) < 0),
                () -> assertTrue(dmitry.compareTo(alex) > 0),
                () -> assertTrue(Participant.NICKNAME_COMPARATOR.compare("Alex", "bob") < 0)
        );
    }

    @Test
    @DisplayName("Nickname normalization")
    void normalizeNickname() {
        assertAll(
                () -> assertEquals("dmitry", Participant.createParticipant("Dmitry").normalizeNickname()),
                () -> assertEquals("dmitry", Participant.normalizeNickname("  DMITRY ")),
                () -> assertNull(Participant.normalizeNickname(" ")),
                () -> assertNull(Participant.normalizeNickname(null))
        );
    }

    @Test
    @DisplayName("Nickname must have from 2 to 32 characters")
    void validate() {
        assertAll(
                () -> assertTrue(validator.validate(Participant.createParticipant("ab")).isEmpty()),
                () -> assertTrue(validator.validate(Participant.createParticipant("a".repeat(32))).isEmpty()),
                () -> assertFalse(validator.validate(Participant.createParticipant("a")).isEmpty()),
                () -> assertFalse(validator.validate(Participant.createParticipant("a".repeat(33))).isEmpty()),
                () -> assertFalse(validator.validate(Participant.createParticipant("  ")).isEmpty())
        );
    }

    @Test
    @DisplayName("Participant can become a watcher")
    void changeRole() {
        Participant participant = Participant.createParticipant("Dmitry");

        participant.setWatcher(true);

        assertTrue(participant.isWatcher());
    }
}
