package by.babanin.pipoker.controller;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import by.babanin.pipoker.PiPokerApplication;
import by.babanin.pipoker.activity.LeaveReason;
import by.babanin.pipoker.activity.RoomActivity;
import by.babanin.pipoker.config.TestWebSocketConfig;
import by.babanin.pipoker.entity.Card;
import by.babanin.pipoker.entity.Deck;
import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.entity.Round;
import by.babanin.pipoker.entity.Vote;
import by.babanin.pipoker.event.ErrorEvent;
import by.babanin.pipoker.event.RoomEvent;
import by.babanin.pipoker.event.RoomEvent.EventType;
import by.babanin.pipoker.exception.RoomNotFoundException;
import by.babanin.pipoker.exception.RoomServiceException;
import by.babanin.pipoker.model.DeckDto;
import by.babanin.pipoker.model.ParticipantDto;
import by.babanin.pipoker.model.RoomCreationDto;
import by.babanin.pipoker.model.RoomDto;
import by.babanin.pipoker.model.RoundDto;
import by.babanin.pipoker.model.VoteDto;
import by.babanin.pipoker.presence.RoomPresence;
import by.babanin.pipoker.service.RoomService;
import by.babanin.pipoker.util.TestStompSession;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
/*@DataMongoTest*/
/*@TestPropertySource(properties = {
        "spring.data.mongodb.uuid-representation=standard",
        "spring.data.mongodb.port=27019",
})*/
@ActiveProfiles({ "test" })
@Import(TestWebSocketConfig.class)
class RoomControllerTest {

    @LocalServerPort
    private Integer port;

    @MockBean
    private RoomService roomService;

    @MockBean
    private RoomPresence roomPresence;

    @MockBean
    private RoomActivity activity;

    @Autowired
    private WebSocketStompClient webSocketStompClient;

    @Autowired
    private ModelMapper modelMapper;

    /*private static ReachedState<RunningMongodProcess> running;*/

    /*@BeforeAll
    static void setup() {
        running = Mongod.builder()
                .net(Start.to(Net.class).initializedWith(Net.defaults().withPort(27019)))
                .build()
                .transitions(Version.Main.V4_4)
                .walker()
                .initState(StateID.of(RunningMongodProcess.class));
    }

    @AfterAll
    static void tearDownAfterAll() {
        running.close();
    }*/

    @Test
    void createWithoutParticipants() throws Exception {
        // Given
        String name = "test";
        Deck deck = new Deck();
        deck.add("1d");
        Room room = new Room(name, deck);
        RoomDto expectedResult = modelMapper.map(room, RoomDto.class);

        when(roomService.create(name, deck, Collections.emptySet()))
                .thenReturn(room);

        // When
        Queue<RoomDto> results = buildUserSession(RoomDto.class, PiPokerApplication.TOPIC_ROOM_CREATED_DESTINATION).send(
                TestWebSocketConfig.BROKER_APP_DESTINATION_PREFIX + PiPokerApplication.ROOM_DESTINATION_PREFIX + "/create",
                RoomCreationDto.builder()
                        .name(name)
                        .deck(modelMapper.map(deck, DeckDto.class))
                        .build());

        // Then
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(expectedResult, results.poll()));
    }

    @Test
    void createWithParticipants() throws Exception {
        // Given
        String name = "test";
        Deck deck = new Deck();
        deck.add("1d");
        Room room = new Room(name, deck);
        room.addParticipant("Dmitry");
        room.addWatcher("Alex");
        RoomDto expectedResult = modelMapper.map(room, RoomDto.class);

        when(roomService.create(name, deck, room.getParticipants()))
                .thenReturn(room);

        // When
        RoomCreationDto roomCreationDto = RoomCreationDto.builder()
                .name(name)
                .deck(expectedResult.getDeck())
                .build();
        roomCreationDto.getParticipants().addAll(expectedResult.getParticipants());
        Queue<RoomDto> results = buildUserSession(RoomDto.class, PiPokerApplication.TOPIC_ROOM_CREATED_DESTINATION).send(
                TestWebSocketConfig.BROKER_APP_DESTINATION_PREFIX + PiPokerApplication.ROOM_DESTINATION_PREFIX + "/create",
                roomCreationDto);

        // Then
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(expectedResult, results.poll()));
        // The creator's connection holds the seats of the people in the new room
        Mockito.verify(roomPresence).hold(eq(room.getId()), eq("Dmitry"), anyString());
        Mockito.verify(roomPresence).hold(eq(room.getId()), eq("Alex"), anyString());
        Mockito.verify(activity).roomCreated();
        Mockito.verify(activity).joined(false);
        Mockito.verify(activity).joined(true);
    }

    @Test
    void addParticipant() throws Exception {
        // Given
        UUID roomId = UUID.randomUUID();
        Participant participant = Participant.createParticipant("Dmitry");
        ParticipantDto expectedResult = modelMapper.map(participant, ParticipantDto.class);

        Mockito.when(roomService.addParticipant(roomId, participant.getNickname()))
                .thenReturn(participant);

        // When
        String destination = String.format("/%s/participants/add", roomId);
        Queue<RoomEvent> results = buildSession(RoomEvent.class, 1, TimeUnit.SECONDS, String.format(".%s", roomId))
                .send(TestWebSocketConfig.BROKER_APP_DESTINATION_PREFIX + PiPokerApplication.ROOM_DESTINATION_PREFIX + destination,
                expectedResult);

        // Then
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(new RoomEvent(roomId, EventType.PARTICIPANT_ADDED, expectedResult), results.poll()));
        Mockito.verify(roomPresence).hold(eq(roomId), eq("Dmitry"), anyString());
        Mockito.verify(activity).joined(false);
    }

    @Test
    void removeParticipant() throws Exception {
        // Given
        UUID roomId = UUID.randomUUID();
        Participant participant = Participant.createParticipant("Dmitry");
        ParticipantDto expectedResult = modelMapper.map(participant, ParticipantDto.class);

        Mockito.when(roomService.removeParticipant(roomId, participant.getNickname()))
                .thenReturn(Optional.of(participant));

        // When
        String destination = String.format("/%s/participants/remove", roomId);
        Queue<RoomEvent> results = buildSession(RoomEvent.class, 1, TimeUnit.SECONDS, String.format(".%s", roomId))
                .send(TestWebSocketConfig.BROKER_APP_DESTINATION_PREFIX + PiPokerApplication.ROOM_DESTINATION_PREFIX + destination,
                        participant.getNickname());

        // Then
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(new RoomEvent(roomId, EventType.PARTICIPANT_REMOVED, expectedResult), results.poll()));
        Mockito.verify(roomPresence).forget(roomId, "Dmitry");
        Mockito.verify(activity).left(LeaveReason.LEFT);
    }

    @Test
    void returnParticipant() throws Exception {
        // Given
        UUID roomId = UUID.randomUUID();
        Participant participant = Participant.createWatcher("Dmitry");

        Mockito.when(roomPresence.returnTo(eq(roomId), eq("dmitry"), anyString()))
                .thenReturn(participant);

        // When
        Queue<RoomEvent> results = buildUserSession(RoomEvent.class, PiPokerApplication.TOPIC_ROOM_RETURNED_DESTINATION)
                .send(TestWebSocketConfig.BROKER_APP_DESTINATION_PREFIX + PiPokerApplication.ROOM_DESTINATION_PREFIX
                        + String.format("/%s/participants/return", roomId), "dmitry");

        // Then
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(
                        new RoomEvent(roomId, EventType.PARTICIPANT_RETURNED, new ParticipantDto("Dmitry", true)), results.poll()));
        Mockito.verify(activity).returned();
    }

    @Test
    void addVote() throws Exception {
        // Given
        UUID roomId = UUID.randomUUID();
        Participant participant = Participant.createParticipant("Dmitry");
        Card card = new Card("1d");
        Vote vote = new Vote(participant, card);
        VoteDto expectedResult = modelMapper.map(vote, VoteDto.class);

        Mockito.when(roomService.addVote(roomId, participant.getNickname(), card.getValue()))
                .thenReturn(vote);

        // When
        String destination = String.format("/%s/votes/add", roomId);
        Queue<RoomEvent> results = buildSession(RoomEvent.class, 1, TimeUnit.SECONDS, String.format(".%s", roomId))
                .send(TestWebSocketConfig.BROKER_APP_DESTINATION_PREFIX + PiPokerApplication.ROOM_DESTINATION_PREFIX + destination,
                        expectedResult);

        // Then
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(new RoomEvent(roomId, EventType.VOTE_ADDED, expectedResult), results.poll()));
        Mockito.verify(activity).voted();
    }

    @Test
    void removeVote() throws Exception {
        // Given
        UUID roomId = UUID.randomUUID();
        Participant participant = Participant.createParticipant("Dmitry");
        Card card = new Card("1d");
        Vote vote = new Vote(participant, card);
        VoteDto expectedResult = modelMapper.map(vote, VoteDto.class);

        Mockito.when(roomService.removeVote(roomId, participant.getNickname()))
                .thenReturn(Optional.of(vote));

        // When
        String destination = String.format("/%s/votes/remove", roomId);
        Queue<RoomEvent> results = buildSession(RoomEvent.class, 1, TimeUnit.SECONDS, String.format(".%s", roomId))
                .send(TestWebSocketConfig.BROKER_APP_DESTINATION_PREFIX + PiPokerApplication.ROOM_DESTINATION_PREFIX + destination,
                        participant.getNickname());

        // Then
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(new RoomEvent(roomId, EventType.VOTE_REMOVED, expectedResult), results.poll()));
    }

    @Test
    void clearVotes() throws Exception {
        // Given
        UUID roomId = UUID.randomUUID();

        // When
        String destination = String.format("/%s/votes/clear", roomId);
        Queue<RoomEvent> results = buildSession(RoomEvent.class, 1, TimeUnit.SECONDS, String.format(".%s", roomId))
                .send(TestWebSocketConfig.BROKER_APP_DESTINATION_PREFIX + PiPokerApplication.ROOM_DESTINATION_PREFIX + destination,
                        roomId);

        // Then
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(new RoomEvent(roomId, EventType.CLEAR_VOTES), results.poll()));
        // Whoever stepped away comes back without the vote of the previous round
        InOrder inOrder = Mockito.inOrder(roomPresence, roomService);
        inOrder.verify(roomPresence).votesCleared(roomId);
        inOrder.verify(roomService).clearVotes(roomId);
        Mockito.verify(activity).cleared();
    }

    @Test
    void get() throws Exception {
        // Given
        Deck deck = new Deck();
        deck.add("1d");
        Room room = new Room("test", deck);
        room.addParticipant("Dmitry");
        room.addVote("Dmitry", "1d");
        UUID roomId = UUID.randomUUID();
        RoomDto expectedResult = modelMapper.map(room, RoomDto.class);

        when(roomService.get(roomId))
                .thenReturn(room);

        // When
        Queue<RoomDto> results = TestStompSession.<RoomDto>builder()
                .stompClient(webSocketStompClient)
                .brokerUrl(String.format(TestWebSocketConfig.URL_FORMAT, port))
                .destination(TestWebSocketConfig.BROKER_APP_DESTINATION_PREFIX + PiPokerApplication.ROOM_DESTINATION_PREFIX + "/" + roomId)
                .resultType(RoomDto.class)
                .build()
                .getResults();

        // Then
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(expectedResult, results.poll()));
    }

    @Test
    void showVotes() throws Exception {
        // Given
        UUID roomId = UUID.randomUUID();
        Instant revealedAt = Instant.parse("2026-10-04T17:00:00.123Z");
        Round round = new Round(revealedAt, List.of(
                new Vote(Participant.createParticipant("Kate"), new Card("1d")),
                new Vote(Participant.createParticipant("Dmitry"), new Card("1h"))));
        when(roomService.showVotes(roomId))
                .thenReturn(Optional.of(round));

        // When
        String destination = String.format("/%s/votes/show", roomId);
        Queue<RoomEvent> results = buildSession(RoomEvent.class, 1, TimeUnit.SECONDS, String.format(".%s", roomId))
                .send(TestWebSocketConfig.BROKER_APP_DESTINATION_PREFIX + PiPokerApplication.ROOM_DESTINATION_PREFIX + destination,
                        roomId);

        // Then
        RoundDto expectedRound = new RoundDto(revealedAt, List.of(new VoteDto("Dmitry", "1h"), new VoteDto("Kate", "1d")));
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(new RoomEvent(roomId, EventType.SHOW_VOTES, expectedRound), results.poll()));
        Mockito.verify(roomService, times(1)).showVotes(roomId);
        Mockito.verify(activity).revealed();
    }

    @Test
    void sendErrorToUser() throws Exception {
        // Given
        UUID roomId = UUID.randomUUID();
        Participant participant = Participant.createParticipant("Dmitry");
        String errorMessage = "Participant \"Dmitry\" is already exist";

        Mockito.when(roomService.addParticipant(roomId, participant.getNickname()))
                .thenThrow(new RoomServiceException(errorMessage));

        // When
        String destination = TestWebSocketConfig.BROKER_APP_DESTINATION_PREFIX + PiPokerApplication.ROOM_DESTINATION_PREFIX
                + String.format("/%s/participants/add", roomId);
        Queue<ErrorEvent> results = buildUserSession(ErrorEvent.class, PiPokerApplication.TOPIC_ROOM_ERRORS_DESTINATION)
                .send(destination, modelMapper.map(participant, ParticipantDto.class));

        // Then
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(new ErrorEvent(destination, errorMessage, null), results.poll()));
    }

    @Test
    void sendRoomNotFoundToUser() throws Exception {
        // Given
        UUID roomId = UUID.randomUUID();
        String errorMessage = String.format("Room \"%s\" is not found", roomId);
        Mockito.when(roomService.addParticipant(roomId, "Dmitry"))
                .thenThrow(new RoomNotFoundException(errorMessage));

        // When
        String destination = TestWebSocketConfig.BROKER_APP_DESTINATION_PREFIX + PiPokerApplication.ROOM_DESTINATION_PREFIX
                + String.format("/%s/participants/add", roomId);
        Queue<ErrorEvent> results = buildUserSession(ErrorEvent.class, PiPokerApplication.TOPIC_ROOM_ERRORS_DESTINATION)
                .send(destination, new ParticipantDto("Dmitry", false));

        // Then
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(new ErrorEvent(destination, errorMessage, ErrorEvent.Code.ROOM_NOT_FOUND),
                        results.poll()));
    }

    private <T> TestStompSession<T> buildUserSession(Class<T> resultType, String destination)
            throws ExecutionException, InterruptedException, TimeoutException {
        return TestStompSession.<T>builder()
                .stompClient(webSocketStompClient)
                .brokerUrl(String.format(TestWebSocketConfig.URL_FORMAT, port))
                .destination(PiPokerApplication.USER_DESTINATION_PREFIX + destination)
                .resultType(resultType)
                .build();
    }

    private <T> TestStompSession<T> buildSession(Class<T> resultType, long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
        return buildSession(resultType, timeout, unit, "");
    }

    private <T> TestStompSession<T> buildSession(Class<T> resultType, long timeout, TimeUnit unit, String destinationSuffix)
            throws ExecutionException, InterruptedException, TimeoutException {
        return TestStompSession.<T>builder()
                .stompClient(webSocketStompClient)
                .brokerUrl(String.format(TestWebSocketConfig.URL_FORMAT, port))
                .destination(PiPokerApplication.TOPIC_ROOM_DESTINATION_PREFIX + destinationSuffix)
                .timeout(timeout)
                .timeUnit(unit)
                .resultType(resultType)
                .build();
    }
}