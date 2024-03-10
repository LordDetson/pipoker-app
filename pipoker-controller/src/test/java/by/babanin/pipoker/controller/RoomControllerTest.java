package by.babanin.pipoker.controller;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
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
import by.babanin.pipoker.config.TestWebSocketConfig;
import by.babanin.pipoker.entity.Card;
import by.babanin.pipoker.entity.Deck;
import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.entity.Vote;
import by.babanin.pipoker.event.RoomEvent;
import by.babanin.pipoker.event.RoomEvent.EventType;
import by.babanin.pipoker.model.DeckDto;
import by.babanin.pipoker.model.ParticipantDto;
import by.babanin.pipoker.model.RoomCreationDto;
import by.babanin.pipoker.model.RoomDto;
import by.babanin.pipoker.model.VoteDto;
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
        Queue<RoomDto> results = buildSession(RoomDto.class, 1, TimeUnit.SECONDS).send(
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
        Queue<RoomDto> results = buildSession(RoomDto.class, 1, TimeUnit.SECONDS).send(
                TestWebSocketConfig.BROKER_APP_DESTINATION_PREFIX + PiPokerApplication.ROOM_DESTINATION_PREFIX + "/create",
                roomCreationDto);

        // Then
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(expectedResult, results.poll()));
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
        Queue<ParticipantDto> results = buildSession(ParticipantDto.class, 1, TimeUnit.SECONDS, String.format(".%s", roomId))
                .send(TestWebSocketConfig.BROKER_APP_DESTINATION_PREFIX + PiPokerApplication.ROOM_DESTINATION_PREFIX + destination,
                expectedResult);

        // Then
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(expectedResult, results.poll()));
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
        Queue<ParticipantDto> results = buildSession(ParticipantDto.class, 1, TimeUnit.SECONDS, String.format(".%s", roomId))
                .send(TestWebSocketConfig.BROKER_APP_DESTINATION_PREFIX + PiPokerApplication.ROOM_DESTINATION_PREFIX + destination,
                        participant.getNickname());

        // Then
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(expectedResult, results.poll()));
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
        Queue<VoteDto> results = buildSession(VoteDto.class, 1, TimeUnit.SECONDS, String.format(".%s", roomId))
                .send(TestWebSocketConfig.BROKER_APP_DESTINATION_PREFIX + PiPokerApplication.ROOM_DESTINATION_PREFIX + destination,
                        expectedResult);

        // Then
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(expectedResult, results.poll()));
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
        Queue<VoteDto> results = buildSession(VoteDto.class, 1, TimeUnit.SECONDS, String.format(".%s", roomId))
                .send(TestWebSocketConfig.BROKER_APP_DESTINATION_PREFIX + PiPokerApplication.ROOM_DESTINATION_PREFIX + destination,
                        participant.getNickname());

        // Then
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(expectedResult, results.poll()));
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
        Mockito.verify(roomService, times(1)).clearVotes(roomId);
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