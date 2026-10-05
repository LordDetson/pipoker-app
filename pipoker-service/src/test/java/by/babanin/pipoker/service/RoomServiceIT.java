package by.babanin.pipoker.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mongodb.client.MongoCollection;

import by.babanin.pipoker.MongoDbContainer;
import by.babanin.pipoker.ServiceTestApplication;
import by.babanin.pipoker.entity.Card;
import by.babanin.pipoker.entity.Deck;
import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.entity.Round;
import by.babanin.pipoker.entity.Task;
import by.babanin.pipoker.entity.Timer;
import by.babanin.pipoker.exception.ConstraintException;
import by.babanin.pipoker.exception.ErrorCode;
import by.babanin.pipoker.exception.InvalidDataException;
import by.babanin.pipoker.exception.RoomServiceException;
import by.babanin.pipoker.exception.VoteServiceException;
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
    @DisplayName("Revealed cards stay revealed for everyone until the next round")
    void votesShown() {
        // Given
        UUID roomId = roomService.create("test", deck("1"), Set.of(Participant.createParticipant("Dmitry"))).getId();
        roomService.addVote(roomId, "Dmitry", "1");
        assertFalse(roomService.get(roomId).isVotesShown());

        // When
        roomService.showVotes(roomId);
        roomService.addParticipant(roomId, "Alex");

        // Then
        assertTrue(roomService.get(roomId).isVotesShown());

        // When
        roomService.clearVotes(roomId);

        // Then
        Room nextRound = roomService.get(roomId);
        assertFalse(nextRound.isVotesShown());
        assertTrue(nextRound.getVotes().isEmpty());
        assertThrows(RoomServiceException.class, () -> roomService.showVotes(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Everyone who opens the room sees the timer until it is stopped, the cards are revealed or the next round starts")
    void timer() {
        // Given
        UUID roomId = roomService.create("test", deck("1"), Set.of(Participant.createParticipant("Dmitry"))).getId();
        assertNull(roomService.get(roomId).getTimer());

        // When
        Timer started = roomService.startTimer(roomId, Duration.ofMinutes(2));

        // Then
        Timer stored = roomService.get(roomId).getTimer();
        assertEquals(120, stored.getSeconds());
        assertEquals(started.getEndsAt().toEpochMilli(), stored.getEndsAt().toEpochMilli());

        // When another one is started, it takes the place of the first
        roomService.startTimer(roomId, Duration.ofMinutes(1));

        // Then
        assertEquals(60, roomService.get(roomId).getTimer().getSeconds());

        // When
        roomService.stopTimer(roomId);

        // Then
        assertNull(roomService.get(roomId).getTimer());

        // When
        roomService.startTimer(roomId, Duration.ofMinutes(1));
        roomService.clearVotes(roomId);

        // Then
        assertNull(roomService.get(roomId).getTimer());

        // When the cards are revealed, the discussion is over
        roomService.startTimer(roomId, Duration.ofMinutes(1));
        roomService.showVotes(roomId);

        // Then
        assertNull(roomService.get(roomId).getTimer());
        RoomServiceException revealed = assertThrows(RoomServiceException.class,
                () -> roomService.startTimer(roomId, Duration.ofMinutes(1)));
        assertEquals(ErrorCode.CARDS_REVEALED, revealed.getCode());
        assertNull(roomService.get(roomId).getTimer());

        // When the next round starts, the timer can run again
        roomService.clearVotes(roomId);

        // Then
        assertEquals(60, roomService.startTimer(roomId, Duration.ofMinutes(1)).getSeconds());
        assertThrows(RoomServiceException.class, () -> roomService.startTimer(UUID.randomUUID(), Duration.ofMinutes(1)));
        assertThrows(RoomServiceException.class, () -> roomService.stopTimer(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Every revealed round is kept in the room's history with who picked which card")
    void history() {
        // Given
        UUID roomId = roomService.create("test", deck("1", "2"), Set.of(Participant.createParticipant("Dmitry"))).getId();
        roomService.addParticipant(roomId, "Alex");
        roomService.addVote(roomId, "Dmitry", "2");
        roomService.addVote(roomId, "Alex", "1");

        // When
        Round first = roomService.showVotes(roomId).orElseThrow();
        Optional<Round> again = roomService.showVotes(roomId);
        roomService.clearVotes(roomId);
        roomService.removeParticipant(roomId, "Alex");
        roomService.addVote(roomId, "Dmitry", "1");
        Round second = roomService.showVotes(roomId).orElseThrow();

        // Then
        List<Round> history = roomService.get(roomId).getHistory();
        assertAll(
                () -> assertTrue(again.isEmpty()),
                () -> assertEquals(List.of(first, second), history),
                () -> assertEquals(List.of("Dmitry:2", "Alex:1"), votesOf(history.get(0))),
                () -> assertEquals(List.of("Dmitry:1"), votesOf(history.get(1)))
        );
    }

    @Test
    @DisplayName("A round revealed by several people at once enters the history once")
    void historyOfSimultaneousReveal() throws Exception {
        UUID roomId = roomService.create("test", deck("1"), Set.of(Participant.createParticipant("Dmitry"))).getId();
        roomService.addVote(roomId, "Dmitry", "1");

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Optional<Round>>> reveals = Collections.nCopies(8, () -> roomService.showVotes(roomId));
            int recorded = 0;
            for(Future<Optional<Round>> reveal : executor.invokeAll(reveals)) {
                recorded += reveal.get().isPresent() ? 1 : 0;
            }

            assertEquals(1, recorded);
            assertEquals(1, roomService.get(roomId).getHistory().size());
        }
        finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("The history keeps only the latest rounds")
    void historyLimit() {
        UUID roomId = roomService.create("test", deck("1"), Set.of(Participant.createParticipant("Dmitry"))).getId();
        List<Round> recorded = new ArrayList<>();
        for(int i = 0; i <= Room.HISTORY_LIMIT; i++) {
            roomService.addVote(roomId, "Dmitry", "1");
            recorded.add(roomService.showVotes(roomId).orElseThrow());
            roomService.clearVotes(roomId);
        }

        assertEquals(recorded.subList(1, recorded.size()), roomService.get(roomId).getHistory());
    }

    @Test
    @DisplayName("A room stored before the history was kept starts one with its next revealed round")
    void historyOfOldRoom() {
        UUID roomId = roomService.create("test", deck("1"), Set.of(Participant.createParticipant("Dmitry"))).getId();
        roomService.addVote(roomId, "Dmitry", "1");
        mongoTemplate.updateFirst(query(where("id").is(roomId)), new Update().unset("history").unset("votesShown"), Room.class);

        Round round = roomService.showVotes(roomId).orElseThrow();

        assertEquals(List.of(round), roomService.get(roomId).getHistory());
    }

    @Test
    @DisplayName("The task of the round and its accepted estimate are stored with the round")
    void taskAndEstimate() {
        // Given
        UUID roomId = roomService.create("test", deck("1", "2"), Set.of(Participant.createParticipant("Dmitry"))).getId();
        Task task = roomService.setTask(roomId, " PIP-25 ", "https://example.com/PIP-25");
        roomService.addVote(roomId, "Dmitry", "1");

        // When
        Round revealed = roomService.showVotes(roomId).orElseThrow();
        Round accepted = roomService.acceptEstimate(roomId, revealed.getRevealedAt(), "2");

        // Then
        assertAll(
                () -> assertEquals(new Task("PIP-25", "https://example.com/PIP-25"), task),
                () -> assertEquals(task, revealed.getTask()),
                () -> assertEquals(new Round(revealed.getRevealedAt(), revealed.getVotes(), task, "2"), accepted),
                () -> assertEquals(List.of(accepted), roomService.get(roomId).getHistory()),
                () -> assertEquals(ErrorCode.CARDS_REVEALED,
                        assertThrows(RoomServiceException.class, () -> roomService.setTask(roomId, "PIP-26", null)).getCode()),
                () -> assertEquals(ErrorCode.CARD_NOT_IN_DECK,
                        assertThrows(ConstraintException.class, () -> roomService.acceptEstimate(roomId, revealed.getRevealedAt(), "3")).getCode()),
                () -> assertEquals(ErrorCode.ROUND_NOT_REVEALED,
                        assertThrows(RoomServiceException.class, () -> roomService.acceptEstimate(roomId, revealed.getRevealedAt().minusSeconds(1), "1")).getCode()),
                () -> assertThrows(RoomServiceException.class, () -> roomService.setTask(UUID.randomUUID(), "PIP-25", null)),
                () -> assertThrows(RoomServiceException.class, () -> roomService.acceptEstimate(UUID.randomUUID(), revealed.getRevealedAt(), "1"))
        );

        // When the next round starts after the estimate, it goes on to the next task
        roomService.clearVotes(roomId);

        // Then
        assertNull(roomService.get(roomId).getTask());
        assertEquals(ErrorCode.ROUND_NOT_REVEALED,
                assertThrows(RoomServiceException.class, () -> roomService.acceptEstimate(roomId, revealed.getRevealedAt(), "1")).getCode());
        assertEquals(List.of(accepted), roomService.get(roomId).getHistory());
    }

    @Test
    @DisplayName("Without an accepted estimate the next round votes on the same task again, and a blank name clears it")
    void taskOfRevote() {
        UUID roomId = roomService.create("test", deck("1"), Set.of(Participant.createParticipant("Dmitry"))).getId();
        roomService.setTask(roomId, "PIP-25", null);
        roomService.addVote(roomId, "Dmitry", "1");
        roomService.showVotes(roomId);

        roomService.clearVotes(roomId);

        assertEquals(new Task("PIP-25", null), roomService.get(roomId).getTask());
        assertNull(roomService.setTask(roomId, "  ", "https://example.com"));
        assertNull(roomService.get(roomId).getTask());
        assertThrows(InvalidDataException.class, () -> roomService.setTask(roomId, "PIP-25", "javascript:alert(1)"));
        assertNull(roomService.get(roomId).getTask());
    }

    @Test
    @DisplayName("A round without a task, and rounds stored before estimates were kept, take an estimate")
    void estimateOfOldRound() {
        UUID roomId = roomService.create("test", deck("1"), Set.of(Participant.createParticipant("Dmitry"))).getId();
        roomService.addVote(roomId, "Dmitry", "1");
        Round revealed = roomService.showVotes(roomId).orElseThrow();
        Document stored = mongoTemplate.findById(roomId, Document.class, "room");
        Document round = stored.getList("history", Document.class).getFirst();

        assertFalse(round.containsKey("task"));
        assertFalse(round.containsKey("estimate"));
        assertNull(revealed.getTask());
        assertEquals("1", roomService.acceptEstimate(roomId, revealed.getRevealedAt(), "1").getEstimate());

        // A room stored before the task was kept starts the next round without one
        roomService.clearVotes(roomId);
        assertNull(roomService.get(roomId).getTask());
    }

    @Test
    @DisplayName("A voter who becomes a watcher before the reveal loses the vote, a revealed vote stays with its round")
    void changeRole() {
        // Given
        UUID roomId = roomService.create("test", deck("1"), Set.of(Participant.createParticipant("$Dmitry"))).getId();
        roomService.addParticipant(roomId, "Alex");
        roomService.addVote(roomId, "$Dmitry", "1");
        roomService.addVote(roomId, "Alex", "1");

        // When
        RoleChange watcher = roomService.changeRole(roomId, "$DMITRY", true);

        // Then
        assertTrue(watcher.participant().isWatcher());
        assertEquals("1", watcher.takenBackVote().getCard().getValue());
        Room stored = roomService.get(roomId);
        assertTrue(stored.getParticipant("$Dmitry").isWatcher());
        assertTrue(stored.findVote("$Dmitry").isEmpty());
        assertTrue(stored.findVote("Alex").isPresent());
        assertThrows(VoteServiceException.class, () -> roomService.addVote(roomId, "$Dmitry", "1"));

        // When
        RoleChange voter = roomService.changeRole(roomId, "$Dmitry", false);
        roomService.addVote(roomId, "$Dmitry", "1");
        roomService.showVotes(roomId);
        RoleChange afterReveal = roomService.changeRole(roomId, "Alex", true);

        // Then
        assertFalse(voter.participant().isWatcher());
        assertNull(afterReveal.takenBackVote());
        Room revealed = roomService.get(roomId);
        assertTrue(revealed.getParticipant("Alex").isWatcher());
        assertEquals(2, revealed.getVotes().size());
        assertEquals(2, revealed.getHistory().getFirst().getVotes().size());
        assertThrows(ConstraintException.class, () -> roomService.changeRole(roomId, "Bob", true));
        assertThrows(RoomServiceException.class, () -> roomService.changeRole(UUID.randomUUID(), "Alex", true));
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
    @DisplayName("Someone who steps away comes back with their vote, unless someone took the nickname")
    void stepAwayAndComeBack() {
        // Given
        UUID roomId = roomService.create("test", deck("1", "2")).getId();
        roomService.addParticipant(roomId, "Alex");
        roomService.addVote(roomId, "Alex", "2");

        // When the last person steps away
        Departure departure = roomService.stepAway(roomId, "Alex").orElseThrow();

        // Then the room is kept for them
        Room away = roomService.get(roomId);
        assertFalse(away.haveParticipants());
        assertTrue(away.getVotes().isEmpty());

        // When
        assertTrue(roomService.bringBack(roomId, departure));

        // Then
        Room back = roomService.get(roomId);
        assertTrue(back.containsParticipant("Alex"));
        assertEquals("2", back.getVote("Alex").getCard().getValue());

        // When someone else takes the nickname while Alex is away
        roomService.stepAway(roomId, "Alex");
        roomService.addWatcher(roomId, "alex");

        // Then Alex can't come back
        assertFalse(roomService.bringBack(roomId, departure));
        assertTrue(roomService.get(roomId).getParticipant("Alex").isWatcher());
        assertTrue(roomService.get(roomId).getVotes().isEmpty());
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
        MongoCollection<Document> rooms = mongoTemplate.getCollection(mongoTemplate.getCollectionName(Room.class));
        rooms.insertOne(old);

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
        Document document = rooms.find(new Document("_id", roomId)).first();
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

    @Test
    @DisplayName("A room nobody acted in for the idle timeout is closed with everyone in it, an active one stays")
    void closeIdleRooms() {
        // Given
        UUID idleRoomId = roomService.create("idle", deck("1")).getId();
        roomService.addParticipant(idleRoomId, "Dmitry");
        UUID activeRoomId = roomService.create("active", deck("1")).getId();
        roomService.addParticipant(activeRoomId, "Alex");
        setLastActivity(idleRoomId, Instant.now().minus(Duration.ofMinutes(31)));
        setLastActivity(activeRoomId, Instant.now().minus(Duration.ofMinutes(31)));
        roomService.addVote(activeRoomId, "Alex", "1");

        // When
        List<Room> closed = roomService.closeIdleRooms(Duration.ofMinutes(30));

        // Then
        assertEquals(List.of(idleRoomId), closed.stream().map(Room::getId).toList());
        assertTrue(closed.get(0).containsParticipant("Dmitry"));
        assertTrue(roomService.find(idleRoomId).isEmpty());
        assertTrue(roomService.find(activeRoomId).isPresent());
    }

    @Test
    @DisplayName("What people do in a room marks it active, stepping away and coming back doesn't")
    void lastActivity() {
        UUID roomId = roomService.create("test", deck("1")).getId();
        assertMarksActive(roomId, () -> roomService.addParticipant(roomId, "Dmitry"));
        assertMarksActive(roomId, () -> roomService.addVote(roomId, "Dmitry", "1"));
        assertMarksActive(roomId, () -> roomService.removeVote(roomId, "Dmitry"));
        assertMarksActive(roomId, () -> roomService.showVotes(roomId));
        assertMarksActive(roomId, () -> roomService.clearVotes(roomId));
        assertMarksActive(roomId, () -> roomService.startTimer(roomId, Duration.ofMinutes(1)));
        assertMarksActive(roomId, () -> roomService.stopTimer(roomId));
        assertMarksActive(roomId, () -> roomService.setTask(roomId, "PIP-25", null));
        assertMarksActive(roomId, () -> roomService.addVote(roomId, "Dmitry", "1"));
        Round revealed = roomService.showVotes(roomId).orElseThrow();
        assertMarksActive(roomId, () -> roomService.acceptEstimate(roomId, revealed.getRevealedAt(), "1"));

        Instant past = Instant.now().minus(Duration.ofHours(1));
        setLastActivity(roomId, past);
        Departure departure = roomService.stepAway(roomId, "Dmitry").orElseThrow();
        roomService.bringBack(roomId, departure);
        assertEquals(past.toEpochMilli(), roomService.get(roomId).getLastActivity().toEpochMilli());
    }

    @Test
    @DisplayName("Rooms stored before their last activity was kept count as active from the start of the backend")
    void markActiveIfUnknown() {
        // Given
        UUID roomId = roomService.create("test", deck("1")).getId();
        mongoTemplate.updateFirst(query(where("id").is(roomId)), new Update().unset("lastActivity"), Room.class);

        // Then
        assertTrue(roomService.closeIdleRooms(Duration.ZERO).isEmpty());

        // When
        Instant start = Instant.now().minusMillis(1);
        roomService.markActiveIfUnknown();

        // Then
        assertTrue(roomService.get(roomId).getLastActivity().isAfter(start));
        assertEquals(List.of(roomId), roomService.closeIdleRooms(Duration.ZERO).stream().map(Room::getId).toList());
    }

    private void assertMarksActive(UUID roomId, Runnable action) {
        setLastActivity(roomId, Instant.now().minus(Duration.ofHours(1)));
        Instant before = Instant.now().minusMillis(1);
        action.run();
        assertTrue(roomService.get(roomId).getLastActivity().isAfter(before));
    }

    private void setLastActivity(UUID roomId, Instant time) {
        mongoTemplate.updateFirst(query(where("id").is(roomId)), new Update().set("lastActivity", time), Room.class);
    }

    private static Deck deck(String... cardValues) {
        Deck deck = new Deck();
        for(String cardValue : cardValues) {
            deck.add(cardValue);
        }
        return deck;
    }

    private static List<String> votesOf(Round round) {
        return round.getVotes().stream()
                .map(vote -> vote.getParticipant().getNickname() + ":" + vote.getCard().getValue())
                .toList();
    }
}
