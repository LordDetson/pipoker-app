package by.babanin.pipoker.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import by.babanin.pipoker.entity.Card;
import by.babanin.pipoker.entity.Deck;
import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.entity.Round;
import by.babanin.pipoker.entity.Timer;
import by.babanin.pipoker.entity.Vote;
import by.babanin.pipoker.exception.ConstraintException;
import by.babanin.pipoker.exception.ErrorCode;
import by.babanin.pipoker.exception.InvalidDataException;
import by.babanin.pipoker.exception.RoomNotFoundException;
import by.babanin.pipoker.exception.RoomServiceException;
import by.babanin.pipoker.exception.VoteServiceException;
import by.babanin.pipoker.repository.RoomRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

@ExtendWith(SpringExtension.class)
@TestPropertySource(properties = {
        "spring.main.banner-mode=off"
})
@RecordApplicationEvents
public class RoomServiceTest {

    @TestConfiguration
    static class TestConfig {

        @Bean
        Validator validator() {
            return Validation.buildDefaultValidatorFactory().getValidator();
        }

        @Bean
        RoomService roomService(RoomRepository roomRepository, Validator validator, ApplicationEventPublisher eventPublisher) {
            return new RoomService(roomRepository, validator, eventPublisher);
        }
    }

    @MockitoBean
    private RoomRepository roomRepository;

    @Autowired
    private RoomService roomService;

    @Autowired
    private Validator validator;

    @Autowired
    private ApplicationEvents events;

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
    @DisplayName("Get all rooms")
    void getAllRooms() {
        // Given
        Deck deck = new Deck();
        deck.add("1h");
        List<Room> rooms = List.of(new Room("first", deck), new Room("second", deck));
        Mockito.when(roomRepository.findAll())
                .thenReturn(rooms);

        // When
        List<Room> found = roomService.getAll();

        // Then
        assertEquals(rooms, found);
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
        assertThrows(RoomNotFoundException.class, () -> roomService.get(id));
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
        Mockito.when(roomRepository.addParticipant(Mockito.eq(roomId), Mockito.any()))
                .thenReturn(true);

        // When
        Participant watcher = roomService.addWatcher(roomId, "test");

        // Then
        assertAll(
                () -> assertEquals("test", watcher.getNickname()),
                () -> assertTrue(watcher.isWatcher())
        );
        Mockito.verify(roomRepository).addParticipant(roomId, watcher);
    }

    @Test
    @DisplayName("Participant is added to the stored room")
    void addParticipant() {
        // Given
        UUID roomId = UUID.randomUUID();
        Mockito.when(roomRepository.addParticipant(Mockito.eq(roomId), Mockito.any()))
                .thenReturn(true);

        // When
        Participant participant = roomService.addParticipant(roomId, " Dmitry ");

        // Then
        assertAll(
                () -> assertEquals("Dmitry", participant.getNickname()),
                () -> assertEquals("dmitry", participant.getKey()),
                () -> assertFalse(participant.isWatcher())
        );
        Mockito.verify(roomRepository).addParticipant(roomId, participant);
        Mockito.verify(roomRepository, Mockito.never()).save(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Participant with an invalid nickname is not added")
    void addParticipantWithInvalidNickname() {
        UUID roomId = UUID.randomUUID();

        RoomServiceException exception = assertThrows(RoomServiceException.class,
                () -> roomService.addParticipant(roomId, "a"));

        assertTrue(exception.getMessage().startsWith("Participant#nickname - "));
        Mockito.verify(roomRepository, Mockito.never()).addParticipant(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Participant with a blank nickname is not added")
    void addParticipantWithBlankNickname() {
        UUID roomId = UUID.randomUUID();

        assertThrows(ConstraintException.class, () -> roomService.addParticipant(roomId, "  "));
        Mockito.verify(roomRepository, Mockito.never()).addParticipant(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Participant with a taken nickname is not added")
    void addDuplicateParticipant() {
        // Given
        UUID roomId = UUID.randomUUID();
        Mockito.when(roomRepository.existsById(roomId))
                .thenReturn(true);

        // When and then: the stored room refuses the nickname
        ConstraintException exception = assertThrows(ConstraintException.class, () -> roomService.addWatcher(roomId, "dmitry"));
        assertTrue(exception.getMessage().contains("is already exist"));
    }

    @Test
    @DisplayName("Participant can't join a missing room")
    void addParticipantToMissingRoom() {
        UUID roomId = UUID.randomUUID();
        Mockito.when(roomRepository.existsById(roomId))
                .thenReturn(false);

        RoomNotFoundException exception = assertThrows(RoomNotFoundException.class,
                () -> roomService.addParticipant(roomId, "Dmitry"));

        assertEquals(String.format("Room \"%s\" is not found", roomId), exception.getMessage());
    }

    @Test
    @DisplayName("Room is kept when a participant leaves and others remain")
    void removeParticipant() {
        // Given
        Room before = new Room("test", new Deck());
        Participant first = before.addParticipant("First");
        before.addParticipant("Second");
        Mockito.when(roomRepository.removeParticipant(before.getId(), "first"))
                .thenReturn(Optional.of(before));

        // When
        Optional<Participant> removed = roomService.removeParticipant(before.getId(), " FIRST ");

        // Then: the room is deleted only if nobody is left in it at that moment
        assertEquals(Optional.of(first), removed);
        Mockito.verify(roomRepository).removeIfEmpty(before.getId());
        Mockito.verify(roomRepository, Mockito.never()).delete(ArgumentMatchers.any());
        assertEquals(0, events.stream(RoomRemovedEvent.class).count());
    }

    @Test
    @DisplayName("A room is announced as removed when the last participant leaves")
    void removeLastParticipant() {
        // Given
        Room before = new Room("test", new Deck());
        before.addParticipant("Last");
        Mockito.when(roomRepository.removeParticipant(before.getId(), "last"))
                .thenReturn(Optional.of(before));
        Mockito.when(roomRepository.removeIfEmpty(before.getId()))
                .thenReturn(true);

        // When
        roomService.removeParticipant(before.getId(), "Last");

        // Then
        assertEquals(List.of(new RoomRemovedEvent(before.getId())), events.stream(RoomRemovedEvent.class).toList());
    }

    @Test
    @DisplayName("Someone who steps away takes their vote along, and the room is kept")
    void stepAway() {
        // Given
        Deck deck = new Deck();
        deck.add("5");
        Room before = new Room("test", deck);
        Participant alex = before.addParticipant("Alex");
        Vote vote = before.addVote("Alex", "5");
        Mockito.when(roomRepository.removeParticipant(before.getId(), "alex"))
                .thenReturn(Optional.of(before));

        // When
        Optional<Departure> departure = roomService.stepAway(before.getId(), "Alex");

        // Then
        assertEquals(Optional.of(new Departure(alex, vote)), departure);
        assertEquals("5", departure.orElseThrow().vote().getCard().getValue());
        Mockito.verify(roomRepository, Mockito.never()).removeIfEmpty(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Someone who stepped away is brought back with their vote")
    void bringBack() {
        // Given
        UUID roomId = UUID.randomUUID();
        Participant alex = Participant.createParticipant("Alex");
        Departure departure = new Departure(alex, null);
        Mockito.when(roomRepository.returnParticipant(roomId, alex, null))
                .thenReturn(true);

        // When
        boolean broughtBack = roomService.bringBack(roomId, departure);

        // Then
        assertTrue(broughtBack);
    }

    @Test
    @DisplayName("Removing an unknown participant changes nothing")
    void removeUnknownParticipant() {
        // Given
        UUID roomId = UUID.randomUUID();
        Mockito.when(roomRepository.existsById(roomId))
                .thenReturn(true);

        // When
        Optional<Participant> removed = roomService.removeParticipant(roomId, "Alex");

        // Then
        assertTrue(removed.isEmpty());
        Mockito.verify(roomRepository, Mockito.never()).removeIfEmpty(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Leaving a missing room fails")
    void removeParticipantFromMissingRoom() {
        UUID roomId = UUID.randomUUID();
        Mockito.when(roomRepository.existsById(roomId))
                .thenReturn(false);

        assertThrows(RoomServiceException.class, () -> roomService.removeParticipant(roomId, "Dmitry"));
    }

    @Test
    @DisplayName("Room is kept after the last participant leaves when removal is turned off")
    void removeLastParticipantKeepsRoomWhenRemovalIsOff() {
        // Given
        RoomRepository repository = Mockito.mock(RoomRepository.class);
        RoomService service = new RoomService(repository, validator, event -> {});
        ReflectionTestUtils.setField(service, "allowRemoveRoomIfNotHaveParticipants", false);
        Room before = new Room("test", new Deck());
        before.addParticipant("last");
        Mockito.when(repository.removeParticipant(before.getId(), "last"))
                .thenReturn(Optional.of(before));

        // When
        service.removeParticipant(before.getId(), "last");

        // Then
        Mockito.verify(repository, Mockito.never()).removeIfEmpty(ArgumentMatchers.any());
    }

    // Votes

    @Test
    @DisplayName("Vote is stored with the card from the deck")
    void addVote() {
        // Given
        UUID roomId = UUID.randomUUID();
        Room room = new Room("test", deck("1h", "1d"));
        room.addParticipant("Dmitry");
        Mockito.when(roomRepository.findById(roomId))
                .thenReturn(Optional.of(room));
        Mockito.when(roomRepository.addVote(Mockito.eq(roomId), Mockito.any()))
                .thenReturn(true);

        // When
        Vote vote = roomService.addVote(roomId, "dmitry", "1D");

        // Then
        assertAll(
                () -> assertEquals("Dmitry", vote.getParticipant().getNickname()),
                () -> assertEquals("1d", vote.getCard().getValue())
        );
        Mockito.verify(roomRepository).addVote(roomId, vote);
        Mockito.verify(roomRepository, Mockito.never()).save(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Vote of someone who left after the room was read is rejected")
    void addVoteAfterLeaving() {
        // Given
        UUID roomId = UUID.randomUUID();
        Room before = new Room("test", deck("1h"));
        before.addParticipant("Dmitry");
        Room after = new Room("test", deck("1h"));
        Mockito.when(roomRepository.findById(roomId))
                .thenReturn(Optional.of(before), Optional.of(after));
        Mockito.when(roomRepository.addVote(Mockito.eq(roomId), Mockito.any()))
                .thenReturn(false);

        // When and then
        assertThrows(ConstraintException.class, () -> roomService.addVote(roomId, "Dmitry", "1h"));
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
        Mockito.verify(roomRepository, Mockito.never()).addVote(ArgumentMatchers.any(), ArgumentMatchers.any());
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
        Mockito.verify(roomRepository, Mockito.never()).addVote(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Vote removal")
    void removeVote() {
        // Given
        UUID roomId = UUID.randomUUID();
        Room room = new Room("test", deck("1h"));
        room.addParticipant("Dmitry");
        Vote vote = room.addVote("Dmitry", "1h");
        Mockito.when(roomRepository.removeVote(roomId, "dmitry"))
                .thenReturn(Optional.of(vote));

        // When
        Optional<Vote> removed = roomService.removeVote(roomId, "Dmitry");

        // Then
        assertEquals(Optional.of(vote), removed);
    }

    @Test
    @DisplayName("Removing a missing vote returns nothing")
    void removeMissingVote() {
        UUID roomId = UUID.randomUUID();
        Mockito.when(roomRepository.existsById(roomId))
                .thenReturn(true);

        assertTrue(roomService.removeVote(roomId, "Dmitry").isEmpty());
    }

    @Test
    @DisplayName("Clearing votes")
    void clearVotes() {
        UUID roomId = UUID.randomUUID();
        Mockito.when(roomRepository.clearVotes(roomId))
                .thenReturn(true);

        roomService.clearVotes(roomId);

        Mockito.verify(roomRepository).clearVotes(roomId);
        Mockito.verify(roomRepository, Mockito.never()).existsById(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Revealing the cards tells the round that entered the history only the first time")
    void showVotesTellsRecordedRound() {
        UUID roomId = UUID.randomUUID();
        Room hidden = new Room("test", deck("1h"));
        hidden.addParticipant("Dmitry");
        hidden.addVote("Dmitry", "1h");
        Room revealed = new Room("test", deck("1h"));
        revealed.addParticipant("Dmitry");
        revealed.addVote("Dmitry", "1h");
        revealed.showVotes(Instant.now());
        Mockito.when(roomRepository.showVotes(ArgumentMatchers.eq(roomId), ArgumentMatchers.any()))
                .thenReturn(Optional.of(hidden), Optional.of(revealed));

        Round round = roomService.showVotes(roomId).orElseThrow();
        Mockito.verify(roomRepository).showVotes(roomId, round.getRevealedAt());
        Optional<Round> again = roomService.showVotes(roomId);

        assertEquals(List.of(new Vote(Participant.createParticipant("Dmitry"), new Card("1h"))), round.getVotes());
        assertTrue(again.isEmpty());
    }

    @Test
    @DisplayName("Revealing the cards of a missing room fails")
    void showVotesOfMissingRoom() {
        UUID roomId = UUID.randomUUID();
        Mockito.when(roomRepository.showVotes(ArgumentMatchers.eq(roomId), ArgumentMatchers.any()))
                .thenReturn(Optional.empty());

        assertThrows(RoomServiceException.class, () -> roomService.showVotes(roomId));
    }

    @Test
    @DisplayName("Clearing votes of a missing room fails")
    void clearVotesOfMissingRoom() {
        UUID roomId = UUID.randomUUID();
        Mockito.when(roomRepository.clearVotes(roomId))
                .thenReturn(false);

        assertThrows(RoomServiceException.class, () -> roomService.clearVotes(roomId));
    }

    // Timer

    @Test
    @DisplayName("Starting the timer")
    void startTimer() {
        UUID roomId = UUID.randomUUID();
        Mockito.when(roomRepository.startTimer(ArgumentMatchers.eq(roomId), ArgumentMatchers.any(Timer.class)))
                .thenReturn(true);
        Instant before = Instant.now();

        Timer timer = roomService.startTimer(roomId, Duration.ofMinutes(2));

        assertAll(
                () -> assertEquals(120, timer.getSeconds()),
                () -> assertFalse(timer.getEndsAt().isBefore(before.plus(Duration.ofMinutes(2)))),
                () -> assertFalse(timer.getEndsAt().isAfter(Instant.now().plus(Duration.ofMinutes(2))))
        );
        Mockito.verify(roomRepository).startTimer(roomId, timer);
    }

    @Test
    @DisplayName("The timer runs from 10 seconds to 30 minutes")
    void timerDuration() {
        UUID roomId = UUID.randomUUID();
        Mockito.when(roomRepository.startTimer(ArgumentMatchers.eq(roomId), ArgumentMatchers.any(Timer.class)))
                .thenReturn(true);

        assertDoesNotThrow(() -> roomService.startTimer(roomId, Duration.ofSeconds(10)));
        assertDoesNotThrow(() -> roomService.startTimer(roomId, Duration.ofMinutes(30)));
        assertThrows(InvalidDataException.class, () -> roomService.startTimer(roomId, Duration.ofSeconds(9)));
        assertThrows(InvalidDataException.class, () -> roomService.startTimer(roomId, Duration.ofMinutes(30).plusSeconds(1)));
        assertThrows(InvalidDataException.class, () -> roomService.startTimer(roomId, Duration.ofSeconds(-60)));
        Mockito.verify(roomRepository, Mockito.times(2)).startTimer(ArgumentMatchers.eq(roomId), ArgumentMatchers.any());
    }

    @Test
    @DisplayName("The timer doesn't start once the cards are revealed")
    void timerAfterReveal() {
        UUID roomId = UUID.randomUUID();
        Mockito.when(roomRepository.startTimer(ArgumentMatchers.eq(roomId), ArgumentMatchers.any(Timer.class)))
                .thenReturn(false);
        Mockito.when(roomRepository.existsById(roomId))
                .thenReturn(true);

        RoomServiceException exception = assertThrows(RoomServiceException.class,
                () -> roomService.startTimer(roomId, Duration.ofMinutes(1)));
        assertEquals(ErrorCode.CARDS_REVEALED, exception.getCode());
    }

    @Test
    @DisplayName("Starting or stopping the timer of a missing room fails")
    void timerOfMissingRoom() {
        UUID roomId = UUID.randomUUID();
        Mockito.when(roomRepository.startTimer(ArgumentMatchers.eq(roomId), ArgumentMatchers.any(Timer.class)))
                .thenReturn(false);
        Mockito.when(roomRepository.stopTimer(roomId))
                .thenReturn(false);

        assertThrows(RoomNotFoundException.class, () -> roomService.startTimer(roomId, Duration.ofMinutes(1)));
        assertThrows(RoomNotFoundException.class, () -> roomService.stopTimer(roomId));
    }

    // Rooms with participants

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

    private static Deck deck(String... cardValues) {
        Deck deck = new Deck();
        for(String cardValue : cardValues) {
            deck.add(cardValue);
        }
        return deck;
    }
}
