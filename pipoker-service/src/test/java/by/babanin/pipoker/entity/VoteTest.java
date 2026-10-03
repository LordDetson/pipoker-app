package by.babanin.pipoker.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VoteTest {

    @Test
    @DisplayName("Votes of the same participant are equal whatever the card")
    void equalsByParticipant() {
        Vote vote = new Vote(Participant.createParticipant("Dmitry"), new Card("1"));
        Vote changed = new Vote(Participant.createParticipant("dmitry"), new Card("2"));
        Vote other = new Vote(Participant.createParticipant("Alex"), new Card("1"));

        assertAll(
                () -> assertEquals(vote, changed),
                () -> assertEquals(vote.hashCode(), changed.hashCode()),
                () -> assertNotEquals(vote, other)
        );
    }

    @Test
    @DisplayName("Votes are sorted by participant nickname")
    void compare() {
        Vote alex = new Vote(Participant.createParticipant("Alex"), new Card("5"));
        Vote dmitry = new Vote(Participant.createParticipant("Dmitry"), new Card("1"));

        assertTrue(alex.compareTo(dmitry) < 0);
        assertTrue(dmitry.compareTo(alex) > 0);
    }

    @Test
    @DisplayName("Vote text representation shows the participant and the card")
    void voteToString() {
        Vote vote = new Vote(Participant.createParticipant("Dmitry"), new Card("1"));

        assertEquals("Vote(participant=Participant(nickname=Dmitry), card=Card(value=1))", vote.toString());
    }
}
