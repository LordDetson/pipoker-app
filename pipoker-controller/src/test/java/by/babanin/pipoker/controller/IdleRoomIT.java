package by.babanin.pipoker.controller;

import static by.babanin.pipoker.util.StompTestClient.next;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.json.JsonMapper;

import by.babanin.pipoker.IntegrationTestContainers;
import by.babanin.pipoker.event.ErrorEvent;
import by.babanin.pipoker.exception.ErrorCode;
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
 * Closing idle rooms in the whole application against real MongoDB and RabbitMQ, with a short idle timeout.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "room.idle-timeout=" + IdleRoomIT.IDLE_TIMEOUT_SECONDS + "s",
        "room.idle-check-interval=200ms" })
@ActiveProfiles("prod")
// Stops this application afterwards, so its short idle timeout doesn't close the rooms of other tests
@DirtiesContext
class IdleRoomIT {

    static final int IDLE_TIMEOUT_SECONDS = 3;

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        IntegrationTestContainers.start(registry);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private StompBrokerRelayMessageHandler brokerRelay;

    private StompTestClient dmitry;
    private StompTestClient alex;

    @BeforeEach
    void connect() throws Exception {
        await().atMost(Duration.ofSeconds(30)).until(brokerRelay::isBrokerAvailable);
        dmitry = new StompTestClient(String.format("http://localhost:%d/ws", port), jsonMapper);
        alex = new StompTestClient(String.format("http://localhost:%d/ws", port), jsonMapper);
    }

    @AfterEach
    void disconnect() {
        dmitry.close();
        alex.close();
        roomRepository.deleteAll();
    }

    @Test
    @DisplayName("A room where people only keep their pages open is closed, and the pages are told")
    void closeIdleRoom() throws Exception {
        // Given
        UUID roomId = createRoom();
        BlockingQueue<RoomEvent> events = alex.subscribe("/topic/room." + roomId, RoomEvent.class);
        alex.send("/app/room/" + roomId + "/participants/add", new ParticipantDto("Alex", false));
        assertEquals(EventType.PARTICIPANT_ADDED, next(events).getEventType());

        // When nobody does anything, while both pages stay connected
        // Then
        await().atMost(Duration.ofSeconds(IDLE_TIMEOUT_SECONDS + 5)).until(() -> !roomRepository.existsById(roomId));
        assertEquals(new RoomEvent(roomId, EventType.ROOM_CLOSED), next(events));

        // When the link is opened again
        String destination = "/app/room/" + roomId + "/participants/add";
        BlockingQueue<ErrorEvent> errors = alex.subscribe("/user/topic/room.errors", ErrorEvent.class);
        alex.send(destination, new ParticipantDto("Bob", false));

        // Then
        assertEquals(ErrorCode.ROOM_NOT_FOUND, next(errors).getCode());
    }

    @Test
    @DisplayName("Voting keeps a room open")
    void voteKeepsRoomOpen() throws Exception {
        // Given
        UUID roomId = createRoom();
        Thread.sleep(Duration.ofSeconds(IDLE_TIMEOUT_SECONDS).minusSeconds(1).toMillis());

        // When
        BlockingQueue<RoomEvent> events = dmitry.subscribe("/topic/room." + roomId, RoomEvent.class);
        dmitry.send("/app/room/" + roomId + "/votes/add", new VoteDto("Dmitry", "1"));
        assertEquals(EventType.VOTE_ADDED, next(events).getEventType());
        Thread.sleep(Duration.ofSeconds(IDLE_TIMEOUT_SECONDS).minusSeconds(1).toMillis());

        // Then: the room would be closed by now without the vote
        assertTrue(roomRepository.existsById(roomId));
        await().atMost(Duration.ofSeconds(IDLE_TIMEOUT_SECONDS + 5)).until(() -> !roomRepository.existsById(roomId));
    }

    private UUID createRoom() throws Exception {
        BlockingQueue<RoomDto> rooms = dmitry.subscribe("/user/topic/room.created", RoomDto.class);
        DeckDto deck = new DeckDto();
        deck.getCards().addAll(List.of("1", "2"));
        RoomCreationDto room = RoomCreationDto.builder()
                .name("test")
                .deck(deck)
                .build();
        room.getParticipants().add(new ParticipantDto("Dmitry", false));
        dmitry.send("/app/room/create", room);
        return next(rooms).getId();
    }
}
