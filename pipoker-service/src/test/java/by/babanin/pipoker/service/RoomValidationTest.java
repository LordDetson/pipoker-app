package by.babanin.pipoker.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.UUID;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import by.babanin.pipoker.entity.Deck;
import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.exception.ConstraintException;
import by.babanin.pipoker.exception.RoomServiceException;
import by.babanin.pipoker.repository.RoomRepository;
import jakarta.validation.Validation;

/**
 * Rooms get the same checks for their cards and participants whether they come with the new room or are added later.
 */
class RoomValidationTest {

    private RoomRepository roomRepository;
    private RoomService roomService;

    @BeforeEach
    void setup() {
        roomRepository = Mockito.mock(RoomRepository.class);
        roomService = new RoomService(roomRepository, Validation.buildDefaultValidatorFactory().getValidator());
    }

    @Test
    @DisplayName("Room isn't created with a too short nickname")
    void createRoomWithShortNickname() {
        RoomServiceException exception = assertThrows(RoomServiceException.class,
                () -> roomService.create("test", deck("1"), Set.of(Participant.createParticipant("a"))));

        assertTrue(exception.getMessage().contains("nickname - size must be between 2 and 32"), exception.getMessage());
        Mockito.verify(roomRepository, Mockito.never()).save(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Room isn't created with a blank nickname")
    void createRoomWithBlankNickname() {
        ConstraintException exception = assertThrows(ConstraintException.class,
                () -> roomService.create("test", deck("1"), Set.of(Participant.createParticipant(" "))));

        assertEquals("Nickname can't be blank", exception.getMessage());
        Mockito.verify(roomRepository, Mockito.never()).save(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Blank nickname can't join a room")
    void addBlankNickname() {
        UUID roomId = UUID.randomUUID();
        Room room = new Room("test", deck("1"));
        Mockito.when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        assertThrows(ConstraintException.class, () -> roomService.addParticipant(roomId, " "));
        assertFalse(room.haveParticipants());
    }

    @Test
    @DisplayName("Room isn't created with a too long card value")
    void createRoomWithLongCard() {
        RoomServiceException exception = assertThrows(RoomServiceException.class,
                () -> roomService.create("test", deck("1234567")));

        assertTrue(exception.getMessage().contains("value - size must be between 1 and 6"), exception.getMessage());
    }

    @Test
    @DisplayName("Room isn't created with a blank card")
    void createRoomWithBlankCard() {
        RoomServiceException exception = assertThrows(RoomServiceException.class,
                () -> roomService.create("test", deck("1", " ")));

        assertTrue(exception.getMessage().contains("value - must not be blank"), exception.getMessage());
    }

    @Test
    @DisplayName("Blank cards don't break the deck")
    void blankCardsInDeck() {
        Deck deck = deck(" ", "1", "");

        assertEquals(2, deck.size());
        assertTrue(deck.find("1").isPresent());
    }

    @Test
    @DisplayName("Valid room is still created")
    void createValidRoom() {
        Mockito.when(roomRepository.save(ArgumentMatchers.any(Room.class))).then(invocation -> invocation.getArgument(0));

        Room room = roomService.create("test", deck("1", "XXL", "?"),
                Set.of(Participant.createParticipant("Dmitry"), Participant.createWatcher("Al")));

        assertEquals(2, room.getParticipants().size());
        assertEquals(3, room.getDeck().size());
    }

    private static Deck deck(String... cardValues) {
        Deck deck = new Deck();
        for(String cardValue : cardValues) {
            deck.add(cardValue);
        }
        return deck;
    }
}
