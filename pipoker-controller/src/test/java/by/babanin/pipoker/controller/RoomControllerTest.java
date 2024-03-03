package by.babanin.pipoker.controller;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
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
import by.babanin.pipoker.entity.Deck;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.model.DeckDto;
import by.babanin.pipoker.model.RoomCreationDto;
import by.babanin.pipoker.model.RoomDto;
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
    void create() throws Exception {
        // Given
        String name = "test";
        Deck deck = new Deck();
        deck.add("1d");
        Room expected = new Room(name, deck);
        RoomDto expectedDto = modelMapper.map(expected, RoomDto.class);

        when(roomService.create(name, deck, Collections.emptyList()))
                .thenReturn(expected);

        // When
        Queue<RoomDto> results = buildSession(1, TimeUnit.SECONDS).send(
                TestWebSocketConfig.BROKER_APP_DESTINATION_PREFIX + PiPokerApplication.ROOM_DESTINATION_PREFIX + "/create",
                RoomCreationDto.builder()
                        .name(name)
                        .deck(modelMapper.map(deck, DeckDto.class))
                        .build());

        // Then
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(expectedDto, results.poll()));
    }

    private TestStompSession<RoomDto> buildSession(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
        return TestStompSession.<RoomDto>builder()
                .stompClient(webSocketStompClient)
                .brokerUrl(String.format(TestWebSocketConfig.URL_FORMAT, port))
                .destination(PiPokerApplication.TOPIC_ROOM_DESTINATION_PREFIX)
                .timeout(timeout)
                .timeUnit(unit)
                .resultType(RoomDto.class)
                .build();
    }
}