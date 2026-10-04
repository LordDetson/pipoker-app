package by.babanin.pipoker.controller;

import static by.babanin.pipoker.util.StompTestClient.assertNoMessage;
import static by.babanin.pipoker.util.StompTestClient.next;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
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
// Serves the metrics the way the production backend does, tests leave them out otherwise
@AutoConfigureObservability(tracing = false)
class RoomApiIT {

    static final int GRACE_PERIOD_SECONDS = 2;

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        IntegrationTestContainers.start(registry);
    }

    @LocalServerPort
    private int port;

    @LocalManagementPort
    private int managementPort;

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
        RoomEvent shown = next(dmitryEvents);
        assertEquals(EventType.SHOW_VOTES, shown.getEventType());
        assertEquals(List.of(new VoteDto("Alex", "3"), new VoteDto("Dmitry", "2")), shown.getRound().getVotes());
        assertEquals("3", shown.getRound().getVotes().get(0).getCard());
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
        // The history keeps the round as it was revealed, with the vote Alex took back afterwards
        assertEquals(List.of(shown.getRound()), state.getHistory());
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

        // Then: the room is deleted while the event of the leaving is being sent, so either can come first
        assertEquals(Set.of(new RoomEvent(roomId, EventType.PARTICIPANT_REMOVED, new ParticipantDto("Alex", true)),
                new RoomEvent(roomId, EventType.ROOM_REMOVED)), Set.of(next(events), next(events)));
        assertFalse(roomRepository.existsById(roomId));
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
        assertEquals(new ErrorEvent(join, String.format("Participant \"dmitry\" is already exist in the room \"%s\"", roomId), null),
                next(alexErrors));

        // Missing room
        String missingJoin = "/app/room/" + missingRoomId + "/participants/add";
        alex.send(missingJoin, new ParticipantDto("Alex", false));
        assertEquals(new ErrorEvent(missingJoin, String.format("Room \"%s\" is not found", missingRoomId),
                ErrorEvent.Code.ROOM_NOT_FOUND), next(alexErrors));

        // Watcher's vote
        alex.send(join, new ParticipantDto("Alex", true));
        next(events);
        String vote = "/app/room/" + roomId + "/votes/add";
        alex.send(vote, new VoteDto("Alex", "1"));
        assertEquals(new ErrorEvent(vote, "The participant \"Alex\" is a watcher, so can't vote", null), next(alexErrors));

        // Card outside the deck
        dmitry.send(vote, new VoteDto("Dmitry", "100"));
        assertEquals(new ErrorEvent(vote, "Card with the value \"100\" is not found in the deck", null), next(dmitryErrors));

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
        BlockingQueue<RoomDto> rooms = dmitry.subscribeWithoutWaiting("/app/room/" + missingRoomId, RoomDto.class);
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
    @DisplayName("Someone who closes the page leaves the room at once")
    void closedPage() throws Exception {
        // Given
        UUID roomId = createRoom(dmitry, "test", List.of("1"), new ParticipantDto("Dmitry", false)).getId();
        String room = "/app/room/" + roomId;
        BlockingQueue<RoomEvent> events = dmitry.subscribe("/topic/room." + roomId, RoomEvent.class);
        alex.send(room + "/participants/add", new ParticipantDto("Alex", false));
        next(events);
        alex.send(room + "/votes/add", new VoteDto("Alex", "1"));
        next(events);

        // When the page says it is being closed, right before its connection closes
        alex.send("/app/presence/page-closed", "");
        alex.close();

        // Then Alex leaves with the vote long before the grace period is over
        RoomEvent left = events.poll(GRACE_PERIOD_SECONDS * 1000 / 2, TimeUnit.MILLISECONDS);
        assertEquals(new RoomEvent(roomId, EventType.PARTICIPANT_REMOVED, new ParticipantDto("Alex", false)), left);
        Room stored = roomRepository.findById(roomId).orElseThrow();
        assertFalse(stored.containsParticipant("Alex"));
        assertTrue(stored.getVotes().isEmpty());
    }

    @Test
    @DisplayName("Someone who refreshes the page comes back to the table with the vote, and everyone sees it")
    void refreshedPage() throws Exception {
        // Given
        UUID roomId = createRoom(dmitry, "test", List.of("1"), new ParticipantDto("Dmitry", false)).getId();
        String room = "/app/room/" + roomId;
        BlockingQueue<RoomEvent> events = dmitry.subscribe("/topic/room." + roomId, RoomEvent.class);
        alex.send(room + "/participants/add", new ParticipantDto("Alex", false));
        next(events);
        alex.send(room + "/votes/add", new VoteDto("Alex", "1"));
        next(events);

        // When the page says it is being closed
        alex.send("/app/presence/page-closed", "");
        alex.close();

        // Then Alex leaves the table at once
        RoomEvent left = events.poll(GRACE_PERIOD_SECONDS * 1000 / 2, TimeUnit.MILLISECONDS);
        assertEquals(new RoomEvent(roomId, EventType.PARTICIPANT_REMOVED, new ParticipantDto("Alex", false)), left);

        // When the refreshed page returns to the seat
        alex = connectClient();
        BlockingQueue<RoomEvent> returned = alex.subscribe("/user/topic/room.returned", RoomEvent.class);
        alex.send(room + "/participants/return", "Alex");

        // Then Alex is back at the table with the vote
        assertEquals(new RoomEvent(roomId, EventType.PARTICIPANT_RETURNED, new ParticipantDto("Alex", false)), next(returned));
        assertEquals(new RoomEvent(roomId, EventType.PARTICIPANT_ADDED, new ParticipantDto("Alex", false)), next(events));
        RoomEvent vote = next(events);
        assertEquals(EventType.VOTE_ADDED, vote.getEventType());
        assertEquals("1", vote.getVote().getCard());
        Thread.sleep(Duration.ofSeconds(GRACE_PERIOD_SECONDS + 1).toMillis());
        assertNoMessage(events);
        Room stored = roomRepository.findById(roomId).orElseThrow();
        assertTrue(stored.containsParticipant("Alex"));
        assertEquals("1", stored.getVote("Alex").getCard().getValue());
    }

    @Test
    @DisplayName("The room waits for the only person who refreshes the page")
    void onlyPersonRefreshes() throws Exception {
        // Given
        UUID roomId = createRoom(alex, "test", List.of("1"), new ParticipantDto("Alex", false)).getId();
        String room = "/app/room/" + roomId;

        // When the page says it is being closed
        alex.send("/app/presence/page-closed", "");
        alex.close();
        await().atMost(Duration.ofSeconds(GRACE_PERIOD_SECONDS))
                .until(() -> !roomRepository.findById(roomId).orElseThrow().haveParticipants());

        // Then the room is kept, and the refreshed page returns to it
        alex = connectClient();
        BlockingQueue<RoomEvent> returned = alex.subscribe("/user/topic/room.returned", RoomEvent.class);
        alex.send(room + "/participants/return", "Alex");
        assertEquals(new RoomEvent(roomId, EventType.PARTICIPANT_RETURNED, new ParticipantDto("Alex", false)), next(returned));
        assertTrue(roomRepository.findById(roomId).orElseThrow().containsParticipant("Alex"));

        // When the page is closed for good
        alex.send("/app/presence/page-closed", "");
        alex.close();

        // Then the room is deleted after the grace period
        await().atMost(Duration.ofSeconds(GRACE_PERIOD_SECONDS + 10)).until(() -> !roomRepository.existsById(roomId));
    }

    @Test
    @DisplayName("Someone who opened the invitation while the room was waiting is told when it is deleted")
    void roomRemovedWhileInvitationIsOpen() throws Exception {
        // Given the only person closed the page, and the room waits for them
        UUID roomId = createRoom(dmitry, "test", List.of("1"), new ParticipantDto("Dmitry", false)).getId();
        dmitry.send("/app/presence/page-closed", "");
        dmitry.close();
        await().atMost(Duration.ofSeconds(GRACE_PERIOD_SECONDS))
                .until(() -> !roomRepository.findById(roomId).orElseThrow().haveParticipants());

        // When someone opens the invitation meanwhile
        BlockingQueue<RoomEvent> events = alex.subscribe("/topic/room." + roomId, RoomEvent.class);
        assertEquals(roomId, alex.request("/app/room/" + roomId, RoomDto.class).getId());

        // Then they are told once nobody came back and the room is deleted
        assertEquals(new RoomEvent(roomId, EventType.ROOM_REMOVED), next(events));
        assertFalse(roomRepository.existsById(roomId));
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

    @Test
    @DisplayName("Dropping a subscription right after making it keeps the connection")
    void subscribeAndUnsubscribeAtOnce() throws Exception {
        UUID roomId = createRoom(dmitry, "Sprint", List.of("1"), new ParticipantDto("Dmitry", false)).getId();

        // Each pair is two frames back to back. If the UNSUBSCRIBE overtook its SUBSCRIBE, RabbitMQ would answer
        // with ERROR and the connection would be closed.
        for(int i = 0; i < 200; i++) {
            alex.subscribeAndUnsubscribe("/user/topic/room.errors");
        }

        assertEquals(roomId, alex.request("/app/room/" + roomId, RoomDto.class).getId());
        assertTrue(alex.isConnected());
    }

    @Test
    @DisplayName("The error for a request made right after subscribing to errors arrives")
    void errorRightAfterSubscribing() throws Exception {
        UUID missingRoomId = UUID.randomUUID();

        // The web client subscribes to errors and asks for the room at once, without waiting for the broker. The
        // request is handled after the subscription, so its error has somewhere to go.
        for(int i = 0; i < 20; i++) {
            try(StompTestClient client = connectClient()) {
                BlockingQueue<ErrorEvent> errors = client.subscribeWithoutWaiting("/user/topic/room.errors", ErrorEvent.class);
                BlockingQueue<RoomDto> rooms = client.subscribeWithoutWaiting("/app/room/" + missingRoomId, RoomDto.class);
                assertEquals(String.format("Room \"%s\" is not found", missingRoomId), next(errors).getMessage());
                assertTrue(rooms.isEmpty());
            }
        }
    }

    @Test
    @DisplayName("Activity metrics are served on the management port only")
    void activityMetrics() throws Exception {
        RoomDto room = createRoom(dmitry, "Sprint", List.of("1"), new ParticipantDto("Dmitry", false));
        alex.send("/app/room/" + room.getId() + "/join", new ParticipantDto("Alex", true));
        dmitry.send("/app/room/" + room.getId() + "/vote", new VoteDto("Dmitry", "1"));

        // The counters keep what the other tests did, so only the gauges have exact values
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            HttpResponse<String> metrics = get(managementPort, "/actuator/prometheus");
            assertEquals(200, metrics.statusCode());
            assertAll(
                    () -> assertTrue(metrics.body().contains("pipoker_rooms 1.0"), "rooms now"),
                    () -> assertTrue(metrics.body().contains("pipoker_people_online 2.0"), "people online"),
                    () -> assertTrue(metrics.body().contains("pipoker_rooms_created_total "), "rooms created"),
                    () -> assertTrue(metrics.body().contains("pipoker_participants_joined_total{role=\"watcher\""), "joined"),
                    () -> assertTrue(metrics.body().contains("pipoker_votes_total "), "votes"),
                    () -> assertTrue(metrics.body().contains("pipoker_connections_total "), "connections")
            );
        });
        // The public port, which the proxy forwards, has no metrics
        assertEquals(404, get(port, "/actuator/prometheus").statusCode());
    }

    private static HttpResponse<String> get(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
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
