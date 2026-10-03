package by.babanin.pipoker.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import org.bson.BsonBinarySubType;
import org.bson.BsonDocument;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
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

    @Autowired
    private ApplicationRunner storeRoomsWithArrays;

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
    @DisplayName("No join, vote or leave is lost when a whole team acts at the same moment")
    void simultaneousChanges() throws Exception {
        // Given
        UUID roomId = roomService.create("test", deck("1", "2", "3")).getId();
        List<String> team = IntStream.rangeClosed(1, 12).mapToObj(number -> "Member" + number).toList();

        // When
        simultaneously(team, nickname -> roomService.addParticipant(roomId, nickname));

        // Then
        assertEquals(team.size(), roomService.get(roomId).getParticipants().size());

        // When
        simultaneously(team, nickname -> roomService.addVote(roomId, nickname, "2"));
        simultaneously(team, nickname -> roomService.addVote(roomId, nickname, "3"));

        // Then
        Room voted = roomService.get(roomId);
        assertEquals(team.size(), voted.getVotes().size());
        assertTrue(voted.getVotes().stream().allMatch(vote -> vote.getCard().getValue().equals("3")));

        // When
        simultaneously(team, nickname -> roomService.removeParticipant(roomId, nickname));

        // Then
        assertTrue(roomService.find(roomId).isEmpty());
    }

    @Test
    @DisplayName("Only one of two people joining at once with the same nickname gets it")
    void simultaneousSameNickname() throws Exception {
        UUID roomId = roomService.create("test", deck("1")).getId();
        List<String> nicknames = List.of("Dmitry", "dmitry", " DMITRY ", "Dmitry");

        simultaneously(nicknames, nickname -> {
            try {
                roomService.addParticipant(roomId, nickname);
            }
            catch(ConstraintException exception) {
                // Taken by someone faster
            }
        });

        assertEquals(1, roomService.get(roomId).getParticipants().size());
    }

    @Test
    @DisplayName("Nicknames with dots and dollar signs can join, vote and leave")
    void nicknamesWithSpecialCharacters() {
        // Given
        UUID roomId = roomService.create("test", deck("1", "2"), Set.of(Participant.createParticipant("d.babanin"))).getId();

        // When
        roomService.addParticipant(roomId, "$money");
        roomService.addParticipant(roomId, "Dr. Who");
        roomService.addVote(roomId, "$money", "1");
        roomService.addVote(roomId, "dr. who", "2");
        roomService.addVote(roomId, "D.Babanin", "1");

        // Then
        Room stored = roomService.get(roomId);
        assertAll(
                () -> assertEquals(3, stored.getParticipants().size()),
                () -> assertEquals("1", stored.getVote("$money").getCard().getValue()),
                () -> assertEquals("2", stored.getVote("Dr. Who").getCard().getValue()),
                () -> assertEquals("1", stored.getVote("d.babanin").getCard().getValue())
        );

        // When
        roomService.removeParticipant(roomId, "$MONEY");

        // Then
        Room afterLeaving = roomService.get(roomId);
        assertEquals(2, afterLeaving.getParticipants().size());
        assertTrue(afterLeaving.findVote("$money").isEmpty());
        assertEquals(2, afterLeaving.getVotes().size());
    }

    @Test
    @DisplayName("Rooms stored with participants and votes in maps are read and changed after the release")
    void roomsStoredWithMaps() throws Exception {
        // Given: a room as the previous version stored it
        UUID roomId = UUID.randomUUID();
        Document old = new Document("_id", roomId)
                .append("name", "Sprint 42")
                .append("deck", new Document("cards", List.of(new Document("value", "1"), new Document("value", "2"))))
                .append("participantMap", new Document()
                        .append("dmitry", new Document("nickname", "Dmitry").append("watcher", false))
                        .append("alex", new Document("nickname", "Alex").append("watcher", true)))
                .append("voteMap", new Document()
                        .append("dmitry", new Document("participant", new Document("nickname", "Dmitry").append("watcher", false))
                                .append("card", new Document("value", "2"))));
        mongoTemplate.getCollection(Room.COLLECTION).insertOne(old);

        // When
        storeRoomsWithArrays.run(new DefaultApplicationArguments());
        roomService.addParticipant(roomId, "Bob");
        roomService.addVote(roomId, "Bob", "1");

        // Then
        Room stored = roomService.get(roomId);
        assertAll(
                () -> assertEquals(3, stored.getParticipants().size()),
                () -> assertTrue(stored.getParticipant("alex").isWatcher()),
                () -> assertEquals("2", stored.getVote("Dmitry").getCard().getValue()),
                () -> assertEquals("1", stored.getVote("Bob").getCard().getValue())
        );
        Document document = mongoTemplate.getCollection(Room.COLLECTION).find(new Document("_id", roomId)).first();
        assertNotNull(document);
        assertFalse(document.containsKey("participantMap"));
        assertFalse(document.containsKey("voteMap"));
        assertThrows(ConstraintException.class, () -> roomService.addParticipant(roomId, "DMITRY"));
    }

    private static void simultaneously(List<String> nicknames, Consumer<String> change) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(nicknames.size());
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> changes = nicknames.stream()
                    .<Future<?>>map(nickname -> executor.submit(() -> {
                        start.await();
                        change.accept(nickname);
                        return null;
                    }))
                    .toList();
            start.countDown();
            for(Future<?> future : changes) {
                future.get();
            }
        }
        finally {
            executor.shutdownNow();
        }
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
