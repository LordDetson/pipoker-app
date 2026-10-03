package by.babanin.pipoker.controller;

import static by.babanin.pipoker.util.StompTestClient.assertNoMessage;
import static by.babanin.pipoker.util.StompTestClient.next;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.StompBrokerRelayMessageHandler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.fasterxml.jackson.databind.ObjectMapper;

import by.babanin.pipoker.IntegrationTestContainers;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.event.ErrorEvent;
import by.babanin.pipoker.event.RoomEvent;
import by.babanin.pipoker.event.RoomEvent.EventType;
import by.babanin.pipoker.model.DeckDto;
import by.babanin.pipoker.model.ParticipantDto;
import by.babanin.pipoker.model.RoomCreationDto;
import by.babanin.pipoker.model.RoomDto;
import by.babanin.pipoker.model.VoteDto;
import by.babanin.pipoker.repository.RoomRepository;
import by.babanin.pipoker.util.StompTestClient;

/**
 * Runs the whole application with the production configuration against real MongoDB and RabbitMQ
 * and drives it over STOMP the way the web client does.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "presence.grace-period=" + RoomApiIT.GRACE_PERIOD_SECONDS + "s")
@ActiveProfiles("prod")
class RoomApiIT {

    static final int GRACE_PERIOD_SECONDS = 2;

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        IntegrationTestContainers.start(registry);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private StompBrokerRelayMessageHandler brokerRelay;

    private StompTestClient dmitry;
    private StompTestClient alex;

    @BeforeEach
    void connect() throws Exception {
        await().atMost(Duration.ofSeconds(30)).until(brokerRelay::isBrokerAvailable);
        dmitry = connectClient();
        alex = connectClient();
    }

    @AfterEach
    void disconnect() {
        dmitry.close();
        alex.close();
        roomRepository.deleteAll();
    }

    @Test
    @DisplayName("Created room is sent to its creator, stored and returned on subscription")
    void createRoom() throws Exception {
        // When
        RoomDto created = createRoom(dmitry, "Sprint 42", List.of("1", "2", "?"),
                new ParticipantDto("Dmitry", false), new ParticipantDto("Alex", true));

        // Then
        assertAll(
                () -> assertNotNull(created.getId()),
                () -> assertEquals("Sprint 42", created.getName()),
                () -> assertEquals(List.of("1", "2", "?"), List.copyOf(created.getDeck().getCards())),
                () -> assertEquals(List.of(new ParticipantDto("Alex", true), new ParticipantDto("Dmitry", false)),
                        List.copyOf(created.getParticipants())),
                () -> assertTrue(created.getParticipants().stream().anyMatch(ParticipantDto::isWatcher)),
                () -> assertTrue(created.getVotes().isEmpty())
        );
        Room stored = roomRepository.findById(created.getId()).orElseThrow();
        assertEquals("Sprint 42", stored.getName());
        assertTrue(stored.getParticipant("Alex").isWatcher());

        // When
        RoomDto requested = alex.request("/app/room/" + created.getId(), RoomDto.class);

        // Then
        assertAll(
                () -> assertEquals(created.getId(), requested.getId()),
                () -> assertEquals(created.getName(), requested.getName()),
                () -> assertEquals(created.getDeck(), requested.getDeck()),
                () -> assertEquals(created.getParticipants(), requested.getParticipants())
        );
    }

    @Test
    @DisplayName("Only the creator gets the created room")
    void createdRoomGoesToCreatorOnly() throws Exception {
        BlockingQueue<RoomDto> alexRooms = alex.subscribe("/user/topic/room.created", RoomDto.class);

        createRoom(dmitry, "test", List.of("1"), new ParticipantDto("Dmitry", false));

        assertNoMessage(alexRooms);
    }

    @Test
    @DisplayName("Room events reach every participant through the broker")
    void planningSession() throws Exception {
        // Given
        UUID roomId = createRoom(dmitry, "test", List.of("1", "2", "3"), new ParticipantDto("Dmitry", false)).getId();
        String room = "/app/room/" + roomId;
        BlockingQueue<RoomEvent> dmitryEvents = dmitry.subscribe("/topic/room." + roomId, RoomEvent.class);
        BlockingQueue<RoomEvent> alexEvents = alex.subscribe("/topic/room." + roomId, RoomEvent.class);

        // Alex joins
        alex.send(room + "/participants/add", new ParticipantDto("Alex", false));
        RoomEvent joined = new RoomEvent(roomId, EventType.PARTICIPANT_ADDED, new ParticipantDto("Alex", false));
        assertEquals(joined, next(dmitryEvents));
        assertEquals(joined, next(alexEvents));
        assertTrue(roomRepository.findById(roomId).orElseThrow().containsParticipant("Alex"));

        // Both vote
        dmitry.send(room + "/votes/add", new VoteDto("Dmitry", "2"));
        RoomEvent dmitryVoted = new RoomEvent(roomId, EventType.VOTE_ADDED, new VoteDto("Dmitry", "2"));
        assertEquals(dmitryVoted, next(dmitryEvents));
        assertEquals(dmitryVoted, next(alexEvents));
        alex.send(room + "/votes/add", new VoteDto("Alex", "3"));
        assertEquals("3", next(dmitryEvents).getVote().getCard());
        next(alexEvents);
        assertEquals(2, roomRepository.findById(roomId).orElseThrow().getVotes().size());

        // Votes are shown
        dmitry.send(room + "/votes/show", "");
        RoomEvent shown = new RoomEvent(roomId, EventType.SHOW_VOTES);
        assertEquals(shown, next(dmitryEvents));
        assertEquals(shown, next(alexEvents));
        // Someone opening the room now sees the cards revealed
        assertTrue(alex.request(room, RoomDto.class).isVotesShown());

        // Alex takes the vote back
        alex.send(room + "/votes/remove", "Alex");
        RoomEvent voteRemoved = new RoomEvent(roomId, EventType.VOTE_REMOVED, new VoteDto("Alex", "3"));
        assertEquals(voteRemoved, next(dmitryEvents));
        assertEquals(voteRemoved, next(alexEvents));
        assertTrue(roomRepository.findById(roomId).orElseThrow().findVote("Alex").isEmpty());

        // Next round
        dmitry.send(room + "/votes/clear", "");
        RoomEvent cleared = new RoomEvent(roomId, EventType.CLEAR_VOTES);
        assertEquals(cleared, next(dmitryEvents));
        assertEquals(cleared, next(alexEvents));
        assertTrue(roomRepository.findById(roomId).orElseThrow().getVotes().isEmpty());

        // The room state matches the events
        RoomDto state = alex.request(room, RoomDto.class);
        assertEquals(2, state.getParticipants().size());
        assertTrue(state.getVotes().isEmpty());
        assertFalse(state.isVotesShown());
    }

    @Test
    @DisplayName("Room is deleted when the last participant leaves")
    void lastParticipantLeaves() throws Exception {
        // Given
        UUID roomId = createRoom(dmitry, "test", List.of("1"), new ParticipantDto("Dmitry", false)).getId();
        String room = "/app/room/" + roomId;
        BlockingQueue<RoomEvent> events = alex.subscribe("/topic/room." + roomId, RoomEvent.class);
        alex.send(room + "/participants/add", new ParticipantDto("Alex", true));
        next(events);

        // When
        dmitry.send(room + "/participants/remove", "Dmitry");

        // Then
        assertEquals(new RoomEvent(roomId, EventType.PARTICIPANT_REMOVED, new ParticipantDto("Dmitry", false)), next(events));
        assertTrue(roomRepository.existsById(roomId));

        // When
        alex.send(room + "/participants/remove", "Alex");

        // Then
        assertEquals(new RoomEvent(roomId, EventType.PARTICIPANT_REMOVED, new ParticipantDto("Alex", true)), next(events));
        await().atMost(Duration.ofSeconds(5)).until(() -> !roomRepository.existsById(roomId));
    }

    @Test
    @DisplayName("Errors are sent only to the user who caused them")
    void errorsGoToSender() throws Exception {
        // Given
        UUID roomId = createRoom(dmitry, "test", List.of("1"), new ParticipantDto("Dmitry", false)).getId();
        UUID missingRoomId = UUID.randomUUID();
        BlockingQueue<ErrorEvent> alexErrors = alex.subscribe("/user/topic/room.errors", ErrorEvent.class);
        BlockingQueue<ErrorEvent> dmitryErrors = dmitry.subscribe("/user/topic/room.errors", ErrorEvent.class);
        BlockingQueue<RoomEvent> events = dmitry.subscribe("/topic/room." + roomId, RoomEvent.class);

        // Taken nickname
        String join = "/app/room/" + roomId + "/participants/add";
        alex.send(join, new ParticipantDto("dmitry", false));
        assertEquals(new ErrorEvent(join, String.format("Participant \"dmitry\" is already exist in the room \"%s\"", roomId)),
                next(alexErrors));

        // Missing room
        String missingJoin = "/app/room/" + missingRoomId + "/participants/add";
        alex.send(missingJoin, new ParticipantDto("Alex", false));
        assertEquals(new ErrorEvent(missingJoin, String.format("Room \"%s\" is not found", missingRoomId)), next(alexErrors));

        // Watcher's vote
        alex.send(join, new ParticipantDto("Alex", true));
        next(events);
        String vote = "/app/room/" + roomId + "/votes/add";
        alex.send(vote, new VoteDto("Alex", "1"));
        assertEquals(new ErrorEvent(vote, "The participant \"Alex\" is a watcher, so can't vote"), next(alexErrors));

        // Card outside the deck
        dmitry.send(vote, new VoteDto("Dmitry", "100"));
        assertEquals(new ErrorEvent(vote, "Card with the value \"100\" is not found in the deck"), next(dmitryErrors));

        // Nobody else saw those errors and no room event was sent for them
        assertNoMessage(events);
        assertTrue(alexErrors.isEmpty());
        assertTrue(dmitryErrors.isEmpty());
    }

    @Test
    @DisplayName("Invalid requests are rejected with an error")
    void invalidRequests() throws Exception {
        // Given
        UUID roomId = createRoom(dmitry, "test", List.of("1"), new ParticipantDto("Dmitry", false)).getId();
        BlockingQueue<ErrorEvent> errors = dmitry.subscribe("/user/topic/room.errors", ErrorEvent.class);

        // Too short nickname
        String join = "/app/room/" + roomId + "/participants/add";
        dmitry.send(join, new ParticipantDto("A", false));
        assertEquals(join, next(errors).getDestination());

        // Room without a name
        dmitry.send("/app/room/create", RoomCreationDto.builder().deck(deck("1")).build());
        assertEquals("/app/room/create", next(errors).getDestination());

        // Room without cards
        dmitry.send("/app/room/create", RoomCreationDto.builder().name("test").deck(deck()).build());
        assertEquals("/app/room/create", next(errors).getDestination());

        // Subscribing to a missing room
        UUID missingRoomId = UUID.randomUUID();
        BlockingQueue<RoomDto> rooms = dmitry.subscribeToApplication("/app/room/" + missingRoomId, RoomDto.class);
        assertEquals(String.format("Room \"%s\" is not found", missingRoomId), next(errors).getMessage());
        assertNoMessage(rooms);

        assertEquals(1, roomRepository.count());
    }

    @Test
    @DisplayName("Someone whose connection is lost leaves the room after the grace period")
    void lostConnection() throws Exception {
        // Given
        UUID roomId = createRoom(dmitry, "test", List.of("1"), new ParticipantDto("Dmitry", false)).getId();
        String room = "/app/room/" + roomId;
        BlockingQueue<RoomEvent> events = dmitry.subscribe("/topic/room." + roomId, RoomEvent.class);
        alex.send(room + "/participants/add", new ParticipantDto("Alex", false));
        next(events);
        alex.send(room + "/votes/add", new VoteDto("Alex", "1"));
        next(events);

        // When
        alex.close();

        // Then nothing happens at once
        assertNoMessage(events);
        assertTrue(roomRepository.findById(roomId).orElseThrow().containsParticipant("Alex"));
        // and Alex leaves with the vote after the grace period
        assertEquals(new RoomEvent(roomId, EventType.PARTICIPANT_REMOVED, new ParticipantDto("Alex", false)), next(events));
        Room stored = roomRepository.findById(roomId).orElseThrow();
        assertFalse(stored.containsParticipant("Alex"));
        assertTrue(stored.getVotes().isEmpty());

        // Coming back now is too late
        try(StompTestClient alexAgain = connectClient()) {
            BlockingQueue<ErrorEvent> errors = alexAgain.subscribe("/user/topic/room.errors", ErrorEvent.class);
            alexAgain.send(room + "/participants/return", "Alex");
            assertEquals(String.format("Participant \"Alex\" is not in the room \"%s\"", roomId), next(errors).getMessage());
        }

        // When the last person's connection is lost, the room is deleted
        dmitry.close();
        await().atMost(Duration.ofSeconds(GRACE_PERIOD_SECONDS + 10)).until(() -> !roomRepository.existsById(roomId));
    }

    @Test
    @DisplayName("Someone who connects again in time keeps the seat and the vote")
    void reconnectInTime() throws Exception {
        // Given
        UUID roomId = createRoom(dmitry, "test", List.of("1"), new ParticipantDto("Dmitry", false)).getId();
        String room = "/app/room/" + roomId;
        BlockingQueue<RoomEvent> events = dmitry.subscribe("/topic/room." + roomId, RoomEvent.class);
        alex.send(room + "/participants/add", new ParticipantDto("Alex", true));
        next(events);

        // When Alex refreshes the page
        alex.close();
        alex = connectClient();
        BlockingQueue<RoomEvent> returned = alex.subscribe("/user/topic/room.returned", RoomEvent.class);
        alex.send(room + "/participants/return", "alex");

        // Then
        assertEquals(new RoomEvent(roomId, EventType.PARTICIPANT_RETURNED, new ParticipantDto("Alex", true)), next(returned));
        Thread.sleep(Duration.ofSeconds(GRACE_PERIOD_SECONDS + 1).toMillis());
        assertNoMessage(events);
        assertTrue(roomRepository.findById(roomId).orElseThrow().containsParticipant("Alex"));

        // Leaving on purpose still works at once
        alex.send(room + "/participants/remove", "Alex");
        assertEquals(new RoomEvent(roomId, EventType.PARTICIPANT_REMOVED, new ParticipantDto("Alex", true)), next(events));
    }

    private RoomDto createRoom(StompTestClient client, String name, List<String> cards, ParticipantDto... participants)
            throws Exception {
        BlockingQueue<RoomDto> rooms = client.subscribe("/user/topic/room.created", RoomDto.class);
        RoomCreationDto room = RoomCreationDto.builder()
                .name(name)
                .deck(deck(cards.toArray(String[]::new)))
                .build();
        room.getParticipants().addAll(List.of(participants));
        client.send("/app/room/create", room);
        return next(rooms);
    }

    private static DeckDto deck(String... cards) {
        DeckDto deck = new DeckDto();
        deck.getCards().addAll(List.of(cards));
        return deck;
    }

    private StompTestClient connectClient() throws Exception {
        return new StompTestClient(String.format("http://localhost:%d/ws", port), objectMapper);
    }
}
