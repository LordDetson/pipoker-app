package by.babanin.pipoker.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import by.babanin.pipoker.entity.Deck;
import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.exception.RoomServiceException;
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

    // Rooms

    @Test
    @DisplayName("Room creation")
    void createRoom() {
        // Given
        String roomName = "test";
        Deck deck = new Deck();
        deck.add("1h");

        Room expectedRoom = new Room(roomName, deck);
        Mockito.when(roomRepository.save(expectedRoom))
                .thenReturn(expectedRoom);

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
}