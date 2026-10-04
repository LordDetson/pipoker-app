package by.babanin.pipoker.presence;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.TaskScheduler;

import by.babanin.pipoker.activity.LeaveReason;
import by.babanin.pipoker.activity.RoomActivity;
import by.babanin.pipoker.entity.Deck;
import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.event.RoomEvent;
import by.babanin.pipoker.event.RoomEvent.EventType;
import by.babanin.pipoker.service.RoomService;

class IdleRoomCloserTest {

    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration CHECK_INTERVAL = Duration.ofMinutes(1);

    private RoomService roomService;
    private RoomPresence roomPresence;
    private RoomActivity activity;
    private SimpMessageSendingOperations messagingTemplate;
    private TaskScheduler scheduler;
    private IdleRoomCloser closer;

    @BeforeEach
    void setUp() {
        roomService = mock(RoomService.class);
        roomPresence = mock(RoomPresence.class);
        activity = mock(RoomActivity.class);
        messagingTemplate = mock(SimpMessageSendingOperations.class);
        scheduler = mock(TaskScheduler.class);
        closer = new IdleRoomCloser(roomService, roomPresence, activity, messagingTemplate, scheduler, IDLE_TIMEOUT,
                CHECK_INTERVAL);
    }

    @Test
    @DisplayName("Rooms stored before are marked active, then the idle rooms are looked for regularly")
    void start() {
        closer.start();

        verify(roomService).markActiveIfUnknown();
        verify(scheduler).scheduleWithFixedDelay(any(Runnable.class), any(Instant.class), eq(CHECK_INTERVAL));
    }

    @Test
    @DisplayName("Everyone in a closed room leaves it, and the pages in it are told")
    void closeIdleRooms() {
        Deck deck = new Deck();
        deck.add("1");
        Room room = new Room("test", deck);
        room.addParticipant("Dmitry");
        room.addWatcher("Alex");
        when(roomService.closeIdleRooms(IDLE_TIMEOUT)).thenReturn(List.of(room));
        // Bob refreshed the page a moment ago and could still come back
        when(roomPresence.roomClosed(room.getId())).thenReturn(List.of(Participant.createParticipant("Bob")));

        closer.closeIdleRooms();

        verify(activity, times(3)).left(LeaveReason.ROOM_CLOSED);
        verify(messagingTemplate).convertAndSend("/topic/room." + room.getId(),
                new RoomEvent(room.getId(), EventType.ROOM_CLOSED));
    }

    @Test
    @DisplayName("Nothing happens while every room is active")
    void noIdleRooms() {
        when(roomService.closeIdleRooms(IDLE_TIMEOUT)).thenReturn(List.of());

        closer.closeIdleRooms();

        verify(activity, never()).left(any());
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }
}
