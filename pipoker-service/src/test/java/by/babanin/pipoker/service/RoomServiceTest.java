package by.babanin.pipoker.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import by.babanin.pipoker.entity.Deck;
import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.entity.Vote;
import by.babanin.pipoker.exception.ConstraintException;
import by.babanin.pipoker.exception.RoomServiceException;
import by.babanin.pipoker.exception.VoteServiceException;
import by.babanin.pipoker.repository.RoomRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

@ExtendWith(SpringExtension.class)
@TestPropertySource(properties = {
        "spring.main.banner-mode=off"
})
public class RoomServiceTest {

    @TestConfiguration
    static class TestConfig {

        @Bean
        Validator validator() {
            return Validation.buildDefaultValidatorFactory().getValidator();
        }

        @Bean
        RoomService roomService(RoomRepository roomRepository, Validator validator) {
            return new RoomService(roomRepository, validator);
        }
    }

    @MockBean
    private RoomRepository roomRepository;

    @Autowired
    private RoomService roomService;

    @Autowired
    private Validator validator;

    // Rooms

    @Test
    @DisplayName("Room creation")
    void createRoom() {
        // Given
        String roomName = "test";
        Deck deck = new Deck();
        deck.add("1h");

        Mockito.when(roomRepository.save(ArgumentMatchers.any(Room.class)))
                .then(AdditionalAnswers.returnsFirstArg());

        // When
        Room room = roomService.create(roomName, deck);

        // Then
        assertAll(
                () -> assertEquals(roomName, room.getName()),
                () -> assertEquals(deck, room.getDeck()),
                () -> assertFalse(room.haveParticipants()),
                () -> assertTrue(room.getVotes().isEmpty())
        );
    }

    @Test
    @DisplayName("Room creation with blank name")
    void createRoomWithBlankName() {
        // Given
        String roomName = "";
        Deck deck = new Deck();
        deck.add("1h");

        Room expectedRoom = new Room(roomName, deck);
        Mockito.when(roomRepository.save(expectedRoom))
                .thenReturn(expectedRoom);

        // When and then
        assertThrows(RoomServiceException.class, () -> roomService.create(roomName, deck));
    }

    @Test
    @DisplayName("Room creation with null name")
    void createRoomWithNullName() {
        // Given
        String roomName = null;
        Deck deck = new Deck();
        deck.add("1h");

        Room expectedRoom = new Room(roomName, deck);
        Mockito.when(roomRepository.save(expectedRoom))
                .thenReturn(expectedRoom);

        // When and then
        assertThrows(RoomServiceException.class, () -> roomService.create(roomName, deck));
    }

    @Test
    @DisplayName("Room creation with empty deck")
    void createRoomWithEmptyDeck() {
        // Given
        String roomName = "test";
        Deck deck = new Deck();

        Room expectedRoom = new Room(roomName, deck);
        Mockito.when(roomRepository.save(expectedRoom))
                .thenReturn(expectedRoom);

        // When and then
        assertThrows(RoomServiceException.class, () -> roomService.create(roomName, deck));
    }

    @Test
    @DisplayName("Room creation with null deck")
    void createRoomWithNullDeck() {
        // Given
        String roomName = "test";
        Deck deck = null;

        Room expectedRoom = new Room(roomName, deck);
        Mockito.when(roomRepository.save(expectedRoom))
                .thenReturn(expectedRoom);

        // When and then
        assertThrows(RoomServiceException.class, () -> roomService.create(roomName, deck));
    }

    @Test
    @DisplayName("Find a room by id")
    void findRoomById() {
        // Given
        UUID id = UUID.randomUUID();
        String roomName = "test";
        Deck deck = new Deck();
        deck.add("1h");

        Room expectedRoom = new Room(roomName, deck);
        Mockito.when(roomRepository.findById(id))
                .thenReturn(Optional.of(expectedRoom));

        // When
        Optional<Room> found = roomService.find(id);

        // Then
        assertTrue(found.isPresent());
    }

    @Test
    @DisplayName("Find no room by id")
    void findNoRoomById() {
        // Given
        UUID id = UUID.randomUUID();
        Mockito.when(roomRepository.findById(id))
                .thenReturn(Optional.empty());

        // When
        Optional<Room> found = roomService.find(id);

        // Then
        assertFalse(found.isPresent());
    }

    @Test
    @DisplayName("Find no room by null id")
    void findNoRoomByNullId() {
        // Given
        UUID id = null;
        Mockito.when(roomRepository.findById(id))
                .thenReturn(Optional.empty());

        // When
        Optional<Room> found = roomService.find(id);

        // Then
        assertFalse(found.isPresent());
    }

    @Test
    @DisplayName("Get a room by id")
    void getRoomById() {
        // Given
        UUID id = UUID.randomUUID();
        String roomName = "test";
        Deck deck = new Deck();
        deck.add("1h");

        Room expectedRoom = new Room(roomName, deck);
        Mockito.when(roomRepository.findById(id))
                .thenReturn(Optional.of(expectedRoom));

        // When
        Room room = roomService.get(id);

        // Then
        assertEquals(roomName, room.getName());
    }

    @Test
    @DisplayName("Get no room by id")
    void getNoRoomById() {
        // Given
        UUID id = UUID.randomUUID();
        Mockito.when(roomRepository.findById(id))
                .thenReturn(Optional.empty());

        // When and then
        assertThrows(RoomServiceException.class, () -> roomService.get(id));
    }

    @Test
    @DisplayName("Get no room by null id")
    void getNoRoomByNullId() {
        // Given
        UUID id = null;
        Mockito.when(roomRepository.findById(id))
                .thenReturn(Optional.empty());

        // When
        assertThrows(RoomServiceException.class, () -> roomService.get(id));
    }

    @Test
    @DisplayName("Remove a room by id")
    void removeRoomById() {
        // Given
        UUID id = UUID.randomUUID();
        String roomName = "test";
        Deck deck = new Deck();
        deck.add("1h");

        Room expectedRoom = new Room(roomName, deck);
        Mockito.when(roomRepository.findById(id))
                .thenReturn(Optional.of(expectedRoom));

        // When
        Optional<Room> removed = roomService.remove(id);

        // Then
        assertTrue(removed.isPresent());
        Mockito.verify(roomRepository, Mockito.times(1))
                .delete(expectedRoom);
    }

    @Test
    @DisplayName("Remove no room by id")
    void removeNoRoomById() {
        // Given
        UUID id = UUID.randomUUID();
        Mockito.when(roomRepository.findById(id))
                .thenReturn(Optional.empty());

        // When
        Optional<Room> removed = roomService.find(id);

        // Then
        assertFalse(removed.isPresent());
        Mockito.verify(roomRepository, Mockito.never())
                .delete(null);
    }

    @Test
    @DisplayName("Remove no room by null id")
    void removeNoRoomByNullId() {
        // Given
        UUID id = null;
        Mockito.when(roomRepository.findById(id))
                .thenReturn(Optional.empty());

        // When
        Optional<Room> removed = roomService.find(id);

        // Then
        assertFalse(removed.isPresent());
        Mockito.verify(roomRepository, Mockito.never())
                .delete(null);
    }

    // Participants

    @Test
    @DisplayName("Watcher creation")
    void addWatcher() {
        // Given
        UUID roomId = UUID.randomUUID();
        String name = "test";
        Deck deck = new Deck();
        deck.add("1h");
        String nickname = "test";
        Room expectedRoom = new Room(name, deck);

        Mockito.when(roomRepository.findById(roomId))
                .thenReturn(Optional.of(expectedRoom));

        // When
        Participant watcher = roomService.addWatcher(roomId, nickname);

        // Then
        assertAll(
                () -> assertNotNull(watcher),
                () -> assertEquals(nickname, watcher.getNickname()),
                () -> assertTrue(watcher.isWatcher())
        );
    }

    @Test
    @DisplayName("Participant creation")
    void addParticipant() {
        // Given
        UUID roomId = UUID.randomUUID();
        String name = "test";
        Deck deck = new Deck();
        deck.add("1h");
        String nickname = "test";
        Room expectedRoom = new Room(name, deck);

        Mockito.when(roomRepository.findById(roomId))
                .thenReturn(Optional.of(expectedRoom));

        // When
        Participant participant = roomService.addParticipant(roomId, nickname);

        // Then
        assertAll(
                () -> assertNotNull(participant),
                () -> assertEquals(nickname, participant.getNickname()),
                () -> assertFalse(participant.isWatcher())
        );
    }

    @Test
    @DisplayName("Room is kept when a participant leaves and others remain")
    void removeParticipantKeepsRoomWithRemainingParticipants() {
        // Given
        UUID roomId = UUID.randomUUID();
        Deck deck = new Deck();
        deck.add("1h");
        Room room = new Room("test", deck);
        room.addParticipant("first");
        room.addParticipant("second");

        Mockito.when(roomRepository.findById(roomId))
                .thenReturn(Optional.of(room));

        // When
        Optional<Participant> removed = roomService.removeParticipant(roomId, "first");

        // Then
        assertTrue(removed.isPresent());
        assertTrue(room.haveParticipants());
        Mockito.verify(roomRepository, Mockito.times(1))
                .save(room);
        Mockito.verify(roomRepository, Mockito.never())
                .delete(Mockito.any());
    }

    @Test
    @DisplayName("Room is removed when the last participant leaves")
    void removeLastParticipantRemovesRoom() {
        // Given
        UUID roomId = UUID.randomUUID();
        Deck deck = new Deck();
        deck.add("1h");
        Room room = new Room("test", deck);
        room.addParticipant("last");

        Mockito.when(roomRepository.findById(roomId))
                .thenReturn(Optional.of(room));

        // When
        Optional<Participant> removed = roomService.removeParticipant(roomId, "last");

        // Then
        assertTrue(removed.isPresent());
        assertFalse(room.haveParticipants());
        Mockito.verify(roomRepository, Mockito.times(1))
                .delete(room);
        Mockito.verify(roomRepository, Mockito.never())
                .save(Mockito.any());
    }


    @Test
    @DisplayName("Room creation with participants and watchers")
    void createRoomWithParticipants() {
        // Given
        Deck deck = new Deck();
        deck.add("1h");
        Set<Participant> participants = Set.of(
                Participant.createParticipant("Dmitry"),
                Participant.createWatcher("Alex"));

        Mockito.when(roomRepository.save(ArgumentMatchers.any(Room.class)))
                .then(AdditionalAnswers.returnsFirstArg());

        // When
        Room room = roomService.create("test", deck, participants);

        // Then
        assertAll(
                () -> assertEquals(2, room.getParticipants().size()),
                () -> assertFalse(room.getParticipant("Dmitry").isWatcher()),
                () -> assertTrue(room.getParticipant("Alex").isWatcher())
        );
        Mockito.verify(roomRepository).save(room);
    }

    @Test
    @DisplayName("Invalid room is not saved")
    void createInvalidRoomIsNotSaved() {
        Deck deck = new Deck();
        deck.add("1h");

        assertThrows(RoomServiceException.class, () -> roomService.create("a", deck));
        Mockito.verify(roomRepository, Mockito.never()).save(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Participant is saved to the room")
    void addParticipantSavesRoom() {
        // Given
        UUID roomId = UUID.randomUUID();
        Room room = new Room("test", deck("1h"));
        Mockito.when(roomRepository.findById(roomId))
                .thenReturn(Optional.of(room));

        // When
        roomService.addParticipant(roomId, "Dmitry");

        // Then
        assertTrue(room.containsParticipant("Dmitry"));
        Mockito.verify(roomRepository).save(room);
    }

    @Test
    @DisplayName("Participant with an invalid nickname is not added")
    void addParticipantWithInvalidNickname() {
        // Given
        UUID roomId = UUID.randomUUID();
        Room room = new Room("test", deck("1h"));
        Mockito.when(roomRepository.findById(roomId))
                .thenReturn(Optional.of(room));

        // When
        RoomServiceException exception = assertThrows(RoomServiceException.class,
                () -> roomService.addParticipant(roomId, "a"));

        // Then
        assertTrue(exception.getMessage().startsWith("Participant#nickname - "));
        assertFalse(room.haveParticipants());
        Mockito.verify(roomRepository, Mockito.never()).save(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Participant with a taken nickname is not added")
    void addDuplicateParticipant() {
        // Given
        UUID roomId = UUID.randomUUID();
        Room room = new Room("test", deck("1h"));
        room.addParticipant("Dmitry");
        Mockito.when(roomRepository.findById(roomId))
                .thenReturn(Optional.of(room));

        // When and then
        assertThrows(ConstraintException.class, () -> roomService.addWatcher(roomId, "dmitry"));
        assertFalse(room.getParticipant("Dmitry").isWatcher());
        Mockito.verify(roomRepository, Mockito.never()).save(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Participant can't join a missing room")
    void addParticipantToMissingRoom() {
        UUID roomId = UUID.randomUUID();
        Mockito.when(roomRepository.findById(roomId))
                .thenReturn(Optional.empty());

        RoomServiceException exception = assertThrows(RoomServiceException.class,
                () -> roomService.addParticipant(roomId, "Dmitry"));

        assertEquals(String.format("Room \"%s\" is not found", roomId), exception.getMessage());
    }

    @Test
    @DisplayName("Removing an unknown participant changes nothing")
    void removeUnknownParticipant() {
        // Given
        UUID roomId = UUID.randomUUID();
        Room room = new Room("test", deck("1h"));
        room.addParticipant("Dmitry");
        Mockito.when(roomRepository.findById(roomId))
                .thenReturn(Optional.of(room));

        // When
        Optional<Participant> removed = roomService.removeParticipant(roomId, "Alex");

        // Then
        assertTrue(removed.isEmpty());
        assertTrue(room.containsParticipant("Dmitry"));
        Mockito.verify(roomRepository, Mockito.never()).save(ArgumentMatchers.any());
        Mockito.verify(roomRepository, Mockito.never()).delete(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Removing a participant removes the vote too")
    void removeParticipantRemovesVote() {
        // Given
        UUID roomId = UUID.randomUUID();
        Room room = new Room("test", deck("1h"));
        room.addParticipant("Dmitry");
        room.addWatcher("Alex");
        room.addVote("Dmitry", "1h");
        Mockito.when(roomRepository.findById(roomId))
                .thenReturn(Optional.of(room));

        // When
        roomService.removeParticipant(roomId, "Dmitry");

        // Then
        assertTrue(room.getVotes().isEmpty());
        Mockito.verify(roomRepository).save(room);
    }

    @Test
    @DisplayName("Room is kept after the last participant leaves when removal is turned off")
    void removeLastParticipantKeepsRoomWhenRemovalIsOff() {
        // Given
        RoomRepository repository = Mockito.mock(RoomRepository.class);
        RoomService service = new RoomService(repository, validator);
        ReflectionTestUtils.setField(service, "allowRemoveRoomIfNotHaveParticipants", false);
        UUID roomId = UUID.randomUUID();
        Room room = new Room("test", deck("1h"));
        room.addParticipant("last");
        Mockito.when(repository.findById(roomId))
                .thenReturn(Optional.of(room));

        // When
        service.removeParticipant(roomId, "last");

        // Then
        Mockito.verify(repository).save(room);
        Mockito.verify(repository, Mockito.never()).delete(ArgumentMatchers.any());
    }

    // Votes

    @Test
    @DisplayName("Vote is saved to the room")
    void addVote() {
        // Given
        UUID roomId = UUID.randomUUID();
        Room room = new Room("test", deck("1h", "1d"));
        room.addParticipant("Dmitry");
        Mockito.when(roomRepository.findById(roomId))
                .thenReturn(Optional.of(room));

        // When
        Vote vote = roomService.addVote(roomId, "Dmitry", "1D");

        // Then
        assertAll(
                () -> assertEquals("Dmitry", vote.getParticipant().getNickname()),
                () -> assertEquals("1d", vote.getCard().getValue()),
                () -> assertEquals(vote, room.getVote("Dmitry"))
        );
        Mockito.verify(roomRepository).save(room);
    }

    @Test
    @DisplayName("Watcher's vote is rejected")
    void addWatcherVote() {
        UUID roomId = UUID.randomUUID();
        Room room = new Room("test", deck("1h"));
        room.addWatcher("Alex");
        Mockito.when(roomRepository.findById(roomId))
                .thenReturn(Optional.of(room));

        assertThrows(VoteServiceException.class, () -> roomService.addVote(roomId, "Alex", "1h"));
        Mockito.verify(roomRepository, Mockito.never()).save(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Vote with a card outside the deck is rejected")
    void addVoteWithUnknownCard() {
        UUID roomId = UUID.randomUUID();
        Room room = new Room("test", deck("1h"));
        room.addParticipant("Dmitry");
        Mockito.when(roomRepository.findById(roomId))
                .thenReturn(Optional.of(room));

        assertThrows(ConstraintException.class, () -> roomService.addVote(roomId, "Dmitry", "2h"));
        assertTrue(room.getVotes().isEmpty());
        Mockito.verify(roomRepository, Mockito.never()).save(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Vote removal")
    void removeVote() {
        // Given
        UUID roomId = UUID.randomUUID();
        Room room = new Room("test", deck("1h"));
        room.addParticipant("Dmitry");
        Vote vote = room.addVote("Dmitry", "1h");
        Mockito.when(roomRepository.findById(roomId))
                .thenReturn(Optional.of(room));

        // When
        Optional<Vote> removed = roomService.removeVote(roomId, "Dmitry");

        // Then
        assertEquals(Optional.of(vote), removed);
        assertTrue(room.getVotes().isEmpty());
        Mockito.verify(roomRepository).save(room);
    }

    @Test
    @DisplayName("Removing a missing vote returns nothing")
    void removeMissingVote() {
        UUID roomId = UUID.randomUUID();
        Room room = new Room("test", deck("1h"));
        room.addParticipant("Dmitry");
        Mockito.when(roomRepository.findById(roomId))
                .thenReturn(Optional.of(room));

        assertTrue(roomService.removeVote(roomId, "Dmitry").isEmpty());
    }

    @Test
    @DisplayName("Clearing votes")
    void clearVotes() {
        // Given
        UUID roomId = UUID.randomUUID();
        Room room = new Room("test", deck("1h"));
        room.addParticipant("Dmitry");
        room.addVote("Dmitry", "1h");
        Mockito.when(roomRepository.findById(roomId))
                .thenReturn(Optional.of(room));

        // When
        roomService.clearVotes(roomId);

        // Then
        assertTrue(room.getVotes().isEmpty());
        assertTrue(room.containsParticipant("Dmitry"));
        Mockito.verify(roomRepository).save(room);
    }

    @Test
    @DisplayName("Clearing votes of a missing room fails")
    void clearVotesOfMissingRoom() {
        UUID roomId = UUID.randomUUID();
        Mockito.when(roomRepository.findById(roomId))
                .thenReturn(Optional.empty());

        assertThrows(RoomServiceException.class, () -> roomService.clearVotes(roomId));
    }

    private static Deck deck(String... cardValues) {
        Deck deck = new Deck();
        for(String cardValue : cardValues) {
            deck.add(cardValue);
        }
        return deck;
    }
}
