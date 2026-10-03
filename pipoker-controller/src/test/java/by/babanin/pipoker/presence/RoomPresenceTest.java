package by.babanin.pipoker.presence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import by.babanin.pipoker.entity.Deck;
import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.event.RoomEvent;
import by.babanin.pipoker.event.RoomEvent.EventType;
import by.babanin.pipoker.exception.RoomServiceException;
import by.babanin.pipoker.model.ParticipantDto;
import by.babanin.pipoker.service.RoomService;

class RoomPresenceTest {

    private static final Duration GRACE_PERIOD = Duration.ofSeconds(10);
    private static final Duration STARTUP_GRACE_PERIOD = Duration.ofSeconds(30);

    private RoomService roomService;
    private SimpMessageSendingOperations messagingTemplate;
    private FakeScheduler scheduler;
    private RoomPresence presence;
    private Room room;

    @BeforeEach
    void setUp() {
        roomService = mock(RoomService.class);
        messagingTemplate = mock(SimpMessageSendingOperations.class);
        scheduler = new FakeScheduler();
        ModelMapper modelMapper = mock(ModelMapper.class);
        when(modelMapper.map(any(Participant.class), eq(ParticipantDto.class))).thenAnswer(invocation -> {
            Participant participant = invocation.getArgument(0);
            return new ParticipantDto(participant.getNickname(), participant.isWatcher());
        });
        presence = new RoomPresence(roomService, modelMapper, messagingTemplate, scheduler, GRACE_PERIOD, STARTUP_GRACE_PERIOD);

        Deck deck = new Deck();
        deck.add("1");
        room = new Room("test", deck);
        room.addParticipant("Dmitry");
        room.addParticipant("Alex");
        when(roomService.find(room.getId())).thenReturn(Optional.of(room));
        when(roomService.removeParticipant(eq(room.getId()), anyString())).thenAnswer(invocation -> {
            Optional<Participant> removed = room.removeParticipant(invocation.getArgument(1));
            if(!room.haveParticipants()) {
                when(roomService.find(room.getId())).thenReturn(Optional.empty());
            }
            return removed;
        });
    }

    @Test
    @DisplayName("Someone who lost the connection leaves the room after the grace period")
    void leavesAfterGracePeriod() {
        presence.hold(room.getId(), "Alex", "alex-tab");

        presence.disconnected(disconnect("alex-tab"));

        FakeScheduler.Task leaving = scheduler.single();
        assertTrue(leaving.delay().compareTo(GRACE_PERIOD.minusSeconds(1)) > 0);
        assertTrue(room.containsParticipant("Alex"));

        leaving.run();

        assertTrue(room.findParticipant("Alex").isEmpty());
        verify(messagingTemplate).convertAndSend("/topic/room." + room.getId(),
                new RoomEvent(room.getId(), EventType.PARTICIPANT_REMOVED, new ParticipantDto("Alex", false)));
    }

    @Test
    @DisplayName("Someone who comes back in time keeps the seat")
    void returnsInTime() {
        presence.hold(room.getId(), "Alex", "alex-tab");
        presence.disconnected(disconnect("alex-tab"));
        FakeScheduler.Task leaving = scheduler.single();

        Participant returned = presence.returnTo(room.getId(), "alex", "alex-tab-after-refresh");

        assertEquals("Alex", returned.getNickname());
        assertTrue(leaving.future().isCancelled());
        // Even if the cancelled task runs, nobody leaves
        leaving.run();
        assertTrue(room.containsParticipant("Alex"));
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));

        // The new connection holds the seat now
        presence.disconnected(disconnect("alex-tab-after-refresh"));
        scheduler.last().run();
        assertTrue(room.findParticipant("Alex").isEmpty());
    }

    @Test
    @DisplayName("The seat is kept while another tab of the same person is open")
    void anotherTabKeepsSeat() {
        presence.hold(room.getId(), "Alex", "first-tab");
        presence.returnTo(room.getId(), "Alex", "second-tab");

        presence.disconnected(disconnect("first-tab"));

        assertTrue(scheduler.tasks.isEmpty());

        presence.disconnected(disconnect("second-tab"));

        scheduler.single().run();
        assertTrue(room.findParticipant("Alex").isEmpty());
    }

    @Test
    @DisplayName("Nobody is removed again after leaving the room on purpose")
    void leftOnPurpose() {
        presence.hold(room.getId(), "Alex", "alex-tab");

        presence.forget(room.getId(), "Alex");
        presence.disconnected(disconnect("alex-tab"));

        assertTrue(scheduler.tasks.isEmpty());
    }

    @Test
    @DisplayName("Someone else who took the nickname is not removed when the first person's connection closes")
    void nicknameTakenAgain() {
        presence.hold(room.getId(), "Alex", "first-alex");
        presence.forget(room.getId(), "Alex");
        presence.hold(room.getId(), "Alex", "second-alex");

        presence.disconnected(disconnect("first-alex"));

        assertTrue(scheduler.tasks.isEmpty());
    }

    @Test
    @DisplayName("It's too late to come back after leaving the room")
    void returnAfterLeaving() {
        presence.hold(room.getId(), "Alex", "alex-tab");
        presence.disconnected(disconnect("alex-tab"));
        scheduler.single().run();

        assertThrows(RoomServiceException.class, () -> presence.returnTo(room.getId(), "Alex", "alex-tab-later"));
    }

    @Test
    @DisplayName("Nobody can come back to a room they never joined")
    void returnToUnknownSeat() {
        assertThrows(RoomServiceException.class, () -> presence.returnTo(room.getId(), "Nobody", "some-tab"));
        assertThrows(RoomServiceException.class, () -> presence.returnTo(UUID.randomUUID(), "Alex", "some-tab"));

        // The failed attempts hold no seat
        presence.disconnected(disconnect("some-tab"));
        assertTrue(scheduler.tasks.isEmpty());
    }

    @Test
    @DisplayName("The room is deleted when the last person loses the connection for good")
    void lastPersonLeaves() {
        presence.hold(room.getId(), "Dmitry", "dmitry-tab");
        presence.hold(room.getId(), "Alex", "alex-tab");

        presence.disconnected(disconnect("dmitry-tab"));
        presence.disconnected(disconnect("alex-tab"));
        scheduler.tasks.forEach(FakeScheduler.Task::run);

        verify(roomService).removeParticipant(room.getId(), "dmitry");
        verify(roomService).removeParticipant(room.getId(), "alex");
        assertTrue(room.getParticipants().isEmpty());
    }

    @Test
    @DisplayName("A room that is already gone is not a problem")
    void roomAlreadyGone() {
        presence.hold(room.getId(), "Alex", "alex-tab");
        presence.disconnected(disconnect("alex-tab"));
        when(roomService.removeParticipant(room.getId(), "alex")).thenThrow(new RoomServiceException("Room is not found"));

        scheduler.single().run();

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("After a restart everyone in the stored rooms gets time to come back")
    void afterRestart() {
        when(roomService.getAll()).thenReturn(List.of(room));

        presence.waitForReturns();

        assertEquals(2, scheduler.tasks.size());
        assertTrue(scheduler.tasks.stream().allMatch(task -> task.delay().compareTo(GRACE_PERIOD) > 0));

        presence.returnTo(room.getId(), "Dmitry", "dmitry-tab");
        scheduler.tasks.forEach(FakeScheduler.Task::run);

        assertTrue(room.containsParticipant("Dmitry"));
        assertTrue(room.findParticipant("Alex").isEmpty());
    }

    @Test
    @DisplayName("A seat taken before the restart check is not reset by it")
    void returnedBeforeRestartCheck() {
        when(roomService.getAll()).thenReturn(List.of(room));
        presence.returnTo(room.getId(), "Dmitry", "dmitry-tab");

        presence.waitForReturns();

        assertEquals(1, scheduler.tasks.size());
        scheduler.single().run();
        assertTrue(room.containsParticipant("Dmitry"));
        assertTrue(room.findParticipant("Alex").isEmpty());
    }

    private static SessionDisconnectEvent disconnect(String sessionId) {
        Message<byte[]> message = MessageBuilder.withPayload(new byte[0]).build();
        return new SessionDisconnectEvent(RoomPresenceTest.class, message, sessionId, CloseStatus.NORMAL);
    }

    /**
     * Collects scheduled tasks, so a test decides when the grace period is over.
     */
    private static final class FakeScheduler implements TaskScheduler {

        private final List<Task> tasks = new ArrayList<>();

        record Task(Runnable runnable, Duration delay, ScheduledFuture<?> future) {

            void run() {
                runnable.run();
            }
        }

        Task single() {
            assertEquals(1, tasks.size());
            return tasks.get(0);
        }

        Task last() {
            return tasks.get(tasks.size() - 1);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            when(future.cancel(false)).thenAnswer(invocation -> {
                when(future.isCancelled()).thenReturn(true);
                return true;
            });
            tasks.add(new Task(task, Duration.between(Instant.now(), startTime), future));
            return future;
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, org.springframework.scheduling.Trigger trigger) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
            throw new UnsupportedOperationException();
        }
    }
}
