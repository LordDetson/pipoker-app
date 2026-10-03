package by.babanin.pipoker.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bson.BsonBinarySubType;
import org.bson.BsonDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import by.babanin.pipoker.MongoDbContainer;
import by.babanin.pipoker.ServiceTestApplication;
import by.babanin.pipoker.entity.Card;
import by.babanin.pipoker.entity.Deck;
import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.exception.ConstraintException;
import by.babanin.pipoker.exception.RoomServiceException;
import by.babanin.pipoker.repository.RoomRepository;

/**
 * Runs the room service against a real MongoDB with the production configuration.
 */
@SpringBootTest(classes = ServiceTestApplication.class)
@ActiveProfiles("prod")
@Testcontainers
class RoomServiceIT {

    @Container
    private static final MongoDbContainer MONGODB = new MongoDbContainer();

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        MONGODB.registerProperties(registry);
    }

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @AfterEach
    void cleanUp() {
        roomRepository.deleteAll();
    }

    @Test
    @DisplayName("Created room is stored with its deck and participants")
    void createRoom() {
        // When
        Room created = roomService.create("Sprint 42", deck("1", "2", "?"), Set.of(
                Participant.createParticipant("Dmitry"),
                Participant.createWatcher("Alex")));

        // Then
        Room stored = roomRepository.findById(created.getId()).orElseThrow();
        assertAll(
                () -> assertEquals("Sprint 42", stored.getName()),
                () -> assertEquals(List.of(new Card("1"), new Card("2"), new Card("?")), stored.getDeck().get()),
                () -> assertFalse(stored.getParticipant("Dmitry").isWatcher()),
                () -> assertTrue(stored.getParticipant("Alex").isWatcher()),
                () -> assertTrue(stored.getVotes().isEmpty())
        );
    }

    @Test
    @DisplayName("Room id is stored as a standard UUID")
    void standardUuid() {
        // Given
        Room created = roomService.create("test", deck("1"));

        // When
        BsonDocument document = mongoTemplate.getCollection("room")
                .withDocumentClass(BsonDocument.class)
                .find()
                .first();

        // Then
        assertNotNull(document);
        assertEquals(BsonBinarySubType.UUID_STANDARD.getValue(), document.getBinary("_id").getType());
        assertEquals(created.getId(), document.getBinary("_id").asUuid());
    }

    @Test
    @DisplayName("Participants and votes survive reading the room back")
    void participantsAndVotes() {
        // Given
        UUID roomId = roomService.create("test", deck("1", "2")).getId();

        // When
        roomService.addParticipant(roomId, "Dmitry");
        roomService.addParticipant(roomId, "Bob");
        roomService.addWatcher(roomId, "Alex");
        roomService.addVote(roomId, "Dmitry", "2");
        roomService.addVote(roomId, "Bob", "1");
        roomService.removeVote(roomId, "Bob");

        // Then
        Room stored = roomService.get(roomId);
        assertAll(
                () -> assertEquals(3, stored.getParticipants().size()),
                () -> assertTrue(stored.getParticipant("Alex").isWatcher()),
                () -> assertEquals(1, stored.getVotes().size()),
                () -> assertEquals("2", stored.getVote("Dmitry").getCard().getValue()),
                () -> assertTrue(stored.findVote("Bob").isEmpty())
        );

        // When
        roomService.clearVotes(roomId);

        // Then
        assertTrue(roomService.get(roomId).getVotes().isEmpty());
    }

    @Test
    @DisplayName("Rejected changes are not stored")
    void rejectedChanges() {
        // Given
        UUID roomId = roomService.create("test", deck("1")).getId();
        roomService.addParticipant(roomId, "Dmitry");

        // When
        assertThrows(RoomServiceException.class, () -> roomService.addParticipant(roomId, "a"));
        assertThrows(ConstraintException.class, () -> roomService.addParticipant(roomId, "DMITRY"));
        assertThrows(ConstraintException.class, () -> roomService.addVote(roomId, "Dmitry", "100"));

        // Then
        Room stored = roomService.get(roomId);
        assertEquals(1, stored.getParticipants().size());
        assertTrue(stored.getVotes().isEmpty());
    }

    @Test
    @DisplayName("Room is deleted when the last participant leaves")
    void lastParticipantLeaves() {
        // Given
        UUID roomId = roomService.create("test", deck("1")).getId();
        roomService.addParticipant(roomId, "Dmitry");
        roomService.addWatcher(roomId, "Alex");

        // When
        roomService.removeParticipant(roomId, "Dmitry");

        // Then
        assertTrue(roomService.find(roomId).isPresent());

        // When
        roomService.removeParticipant(roomId, "Alex");

        // Then
        assertTrue(roomService.find(roomId).isEmpty());
        assertEquals(0, roomRepository.count());
    }

    @Test
    @DisplayName("Room removal")
    void removeRoom() {
        UUID roomId = roomService.create("test", deck("1")).getId();

        assertTrue(roomService.remove(roomId).isPresent());
        assertTrue(roomService.find(roomId).isEmpty());
        assertTrue(roomService.remove(roomId).isEmpty());
    }

    private static Deck deck(String... cardValues) {
        Deck deck = new Deck();
        for(String cardValue : cardValues) {
            deck.add(cardValue);
        }
        return deck;
    }
}
