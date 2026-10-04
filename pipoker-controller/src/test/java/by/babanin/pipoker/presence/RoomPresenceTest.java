package by.babanin.pipoker.presence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.modelmapper.ModelMapper;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import by.babanin.pipoker.activity.LeaveReason;
import by.babanin.pipoker.activity.RoomActivity;
import by.babanin.pipoker.entity.Deck;
import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.entity.Vote;
import by.babanin.pipoker.event.RoomEvent;
import by.babanin.pipoker.event.RoomEvent.EventType;
import by.babanin.pipoker.exception.RoomServiceException;
import by.babanin.pipoker.model.ParticipantDto;
import by.babanin.pipoker.model.VoteDto;
import by.babanin.pipoker.service.Departure;
import by.babanin.pipoker.service.RoomRemovedEvent;
import by.babanin.pipoker.service.RoomService;

class RoomPresenceTest {

    private static final Duration GRACE_PERIOD = Duration.ofSeconds(10);
    private static final Duration STARTUP_GRACE_PERIOD = Duration.ofSeconds(30);
    private static final Duration LATER = Duration.ofMinutes(1);
    private static final Duration MOMENT = Duration.ofMillis(100);

    private RoomService roomService;
    private SimpMessageSendingOperations messagingTemplate;
    private RoomActivity activity;
    private FakeScheduler scheduler;
    private RoomPresence presence;
    private Room room;

    @BeforeEach
    void setUp() {
        roomService = mock(RoomService.class);
        messagingTemplate = mock(SimpMessageSendingOperations.class);
        activity = mock(RoomActivity.class);
        scheduler = new FakeScheduler();
        ModelMapper modelMapper = mock(ModelMapper.class);
        when(modelMapper.map(any(Participant.class), eq(ParticipantDto.class))).thenAnswer(invocation -> {
            Participant participant = invocation.getArgument(0);
            return new ParticipantDto(participant.getNickname(), participant.isWatcher());
        });
        when(modelMapper.map(any(Vote.class), eq(VoteDto.class))).thenAnswer(invocation -> {
            Vote vote = invocation.getArgument(0);
            return new VoteDto(vote.getParticipant().getNickname(), vote.getCard().getValue());
        });
        presence = new RoomPresence(roomService, new SeatLocks(), modelMapper, messagingTemplate, activity, scheduler, GRACE_PERIOD, STARTUP_GRACE_PERIOD);

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
        when(roomService.stepAway(eq(room.getId()), anyString())).thenAnswer(invocation -> stepAway(invocation.getArgument(1)));
        when(roomService.bringBack(eq(room.getId()), any(Departure.class))).thenAnswer(invocation -> {
            Departure departure = invocation.getArgument(1);
            String nickname = departure.participant().getNickname();
            if(room.containsParticipant(nickname)) {
                return false;
            }
            if(departure.participant().isWatcher()) {
                room.addWatcher(nickname);
            }
            else {
                room.addParticipant(nickname);
            }
            if(departure.vote() != null) {
                room.addVote(nickname, departure.vote().getCard().getValue());
            }
            return true;
        });
        doAnswer(invocation -> {
            if(!room.haveParticipants()) {
                when(roomService.find(room.getId())).thenReturn(Optional.empty());
            }
            return null;
        }).when(roomService).removeIfEmpty(room.getId());
    }

    @Test
    @DisplayName("Someone who closes the page leaves the table at once")
    void leavesAfterClosingPage() {
        presence.hold(room.getId(), "Dmitry", "dmitry-tab");
        presence.hold(room.getId(), "Alex", "alex-tab");
        room.addVote("Alex", "1");

        presence.pageClosed("alex-tab");
        presence.disconnected(disconnect("alex-tab", CloseStatus.GOING_AWAY));
        scheduler.advance(Duration.ZERO);

        assertFalse(room.containsParticipant("Alex"));
        assertTrue(room.getVotes().isEmpty());
        verify(messagingTemplate).convertAndSend("/topic/room." + room.getId(),
                new RoomEvent(room.getId(), EventType.PARTICIPANT_REMOVED, new ParticipantDto("Alex", false)));
        // The leaving counts once it is too late to come back
        verify(activity, never()).left(any());
        scheduler.advance(GRACE_PERIOD);
        verify(activity).left(LeaveReason.PAGE_CLOSED);
        verify(messagingTemplate, times(1)).convertAndSend(anyString(), any(Object.class));
        assertThrows(RoomServiceException.class, () -> presence.returnTo(room.getId(), "Alex", "alex-tab-after-refresh"));
    }

    @Test
    @DisplayName("The pages still open on a removed room are told")
    void roomRemoved() {
        presence.roomRemoved(new RoomRemovedEvent(room.getId()));

        verify(messagingTemplate).convertAndSend("/topic/room." + room.getId(),
                new RoomEvent(room.getId(), EventType.ROOM_REMOVED));
    }

    @Test
    @DisplayName("Nobody leaves or comes back to a closed room")
    void roomClosed() {
        presence.hold(room.getId(), "Dmitry", "dmitry-tab");
        presence.hold(room.getId(), "Alex", "alex-tab");
        presence.pageClosed("alex-tab");
        presence.disconnected(disconnect("alex-tab", CloseStatus.GOING_AWAY));
        scheduler.advance(Duration.ZERO);
        when(roomService.find(room.getId())).thenReturn(Optional.empty());

        List<Participant> steppedAway = presence.roomClosed(room.getId());
        presence.disconnected(disconnect("dmitry-tab", CloseStatus.GOING_AWAY));
        scheduler.advance(LATER);

        assertEquals(List.of(Participant.createParticipant("Alex")), steppedAway);
        assertEquals(0, presence.peopleOnline());
        verify(activity, never()).left(any());
        verify(roomService, never()).removeParticipant(any(), anyString());
        verify(roomService, never()).bringBack(any(), any());
        assertThrows(RoomServiceException.class, () -> presence.returnTo(room.getId(), "Alex", "alex-tab-after-refresh"));
    }

    @Test
    @DisplayName("Someone who refreshes the page comes back to the table with their vote")
    void refreshesPage() {
        presence.hold(room.getId(), "Dmitry", "dmitry-tab");
        presence.hold(room.getId(), "Alex", "alex-tab");
        room.addVote("Alex", "1");
        presence.pageClosed("alex-tab");
        presence.disconnected(disconnect("alex-tab", CloseStatus.GOING_AWAY));
        scheduler.advance(GRACE_PERIOD.minus(MOMENT));

        Participant returned = presence.returnTo(room.getId(), "alex", "alex-tab-after-refresh");

        assertEquals("Alex", returned.getNickname());
        assertTrue(room.containsParticipant("Alex"));
        assertEquals("1", room.getVote("Alex").getCard().getValue());
        String topic = "/topic/room." + room.getId();
        InOrder told = inOrder(messagingTemplate);
        told.verify(messagingTemplate).convertAndSend(topic,
                new RoomEvent(room.getId(), EventType.PARTICIPANT_REMOVED, new ParticipantDto("Alex", false)));
        told.verify(messagingTemplate).convertAndSend(topic,
                new RoomEvent(room.getId(), EventType.PARTICIPANT_ADDED, new ParticipantDto("Alex", false)));
        told.verify(messagingTemplate).convertAndSend(topic,
                new RoomEvent(room.getId(), EventType.VOTE_ADDED, new VoteDto("Alex", "1")));
        scheduler.advance(LATER);
        assertTrue(room.containsParticipant("Alex"));
        verify(activity, never()).left(any());
    }

    @Test
    @DisplayName("A refreshed page that comes back while the person is being taken away from the table brings them back")
    void refreshedPageComesBackWhileSteppingAway() throws Exception {
        presence.hold(room.getId(), "Dmitry", "dmitry-tab");
        presence.hold(room.getId(), "Alex", "alex-tab");
        room.addVote("Alex", "1");
        CountDownLatch steppingAway = new CountDownLatch(1);
        CountDownLatch stored = new CountDownLatch(1);
        when(roomService.stepAway(eq(room.getId()), anyString())).thenAnswer(invocation -> {
            steppingAway.countDown();
            // The stored room changes slowly, for example while the database is busy
            stored.await();
            return stepAway(invocation.getArgument(1));
        });
        presence.pageClosed("alex-tab");
        presence.disconnected(disconnect("alex-tab", CloseStatus.GOING_AWAY));
        Thread closing = new Thread(() -> scheduler.advance(Duration.ZERO));
        closing.start();
        assertTrue(steppingAway.await(5, TimeUnit.SECONDS));

        CompletableFuture<Participant> returned = CompletableFuture.supplyAsync(
                () -> presence.returnTo(room.getId(), "Alex", "alex-tab-after-refresh"));
        assertThrows(TimeoutException.class, () -> returned.get(200, TimeUnit.MILLISECONDS),
                "the page waits until the person has left the table");
        stored.countDown();
        closing.join(5000);

        assertEquals("Alex", returned.get(5, TimeUnit.SECONDS).getNickname());
        assertTrue(room.containsParticipant("Alex"));
        assertEquals("1", room.getVote("Alex").getCard().getValue());
        String topic = "/topic/room." + room.getId();
        InOrder told = inOrder(messagingTemplate);
        told.verify(messagingTemplate).convertAndSend(topic,
                new RoomEvent(room.getId(), EventType.PARTICIPANT_REMOVED, new ParticipantDto("Alex", false)));
        told.verify(messagingTemplate).convertAndSend(topic,
                new RoomEvent(room.getId(), EventType.PARTICIPANT_ADDED, new ParticipantDto("Alex", false)));
        told.verify(messagingTemplate).convertAndSend(topic,
                new RoomEvent(room.getId(), EventType.VOTE_ADDED, new VoteDto("Alex", "1")));
        scheduler.advance(LATER);
        assertTrue(room.containsParticipant("Alex"));
        verify(activity, never()).left(any());
    }

    @Test
    @DisplayName("Someone who refreshes the page after a new round started comes back without the old vote")
    void refreshesPageAfterNewRound() {
        presence.hold(room.getId(), "Dmitry", "dmitry-tab");
        presence.hold(room.getId(), "Alex", "alex-tab");
        room.addVote("Alex", "1");
        presence.pageClosed("alex-tab");
        presence.disconnected(disconnect("alex-tab", CloseStatus.GOING_AWAY));
        scheduler.advance(Duration.ZERO);

        room.clearVotes();
        presence.votesCleared(room.getId());
        presence.returnTo(room.getId(), "Alex", "alex-tab-after-refresh");

        assertTrue(room.containsParticipant("Alex"));
        assertTrue(room.findVote("Alex").isEmpty());
        verify(messagingTemplate, never()).convertAndSend(anyString(),
                eq(new RoomEvent(room.getId(), EventType.VOTE_ADDED, new VoteDto("Alex", "1"))));
    }

    @Test
    @DisplayName("A refreshed page doesn't bring back someone whose nickname was taken meanwhile")
    void nicknameTakenWhileAway() {
        presence.hold(room.getId(), "Alex", "alex-tab");
        presence.pageClosed("alex-tab");
        presence.disconnected(disconnect("alex-tab", CloseStatus.GOING_AWAY));
        scheduler.advance(Duration.ZERO);
        room.addWatcher("alex");

        assertThrows(RoomServiceException.class, () -> presence.returnTo(room.getId(), "Alex", "alex-tab-after-refresh"));

        assertTrue(room.getParticipant("Alex").isWatcher());
        verify(activity).left(LeaveReason.PAGE_CLOSED);
        // The failed attempt holds no seat
        presence.disconnected(disconnect("alex-tab-after-refresh", CloseStatus.NORMAL));
        scheduler.advance(LATER);
        assertTrue(room.containsParticipant("Alex"));
        verify(activity, times(1)).left(any());
    }

    @Test
    @DisplayName("The room waits for the last people who closed the page, and is deleted when they don't come back")
    void lastPeopleCloseThePage() {
        presence.hold(room.getId(), "Dmitry", "dmitry-tab");
        presence.hold(room.getId(), "Alex", "alex-tab");

        presence.pageClosed("dmitry-tab");
        presence.disconnected(disconnect("dmitry-tab", CloseStatus.GOING_AWAY));
        presence.pageClosed("alex-tab");
        presence.disconnected(disconnect("alex-tab", CloseStatus.GOING_AWAY));
        scheduler.advance(Duration.ZERO);

        assertFalse(room.haveParticipants());
        assertTrue(roomService.find(room.getId()).isPresent());
        verify(roomService, never()).removeIfEmpty(any());

        scheduler.advance(GRACE_PERIOD);

        assertTrue(roomService.find(room.getId()).isEmpty());
    }

    @Test
    @DisplayName("Someone whose page says it is closed after the connection closes leaves the table at once")
    void pageClosedAfterConnectionClosed() {
        presence.hold(room.getId(), "Alex", "alex-tab");
        presence.disconnected(disconnect("alex-tab", CloseStatus.NORMAL));
        scheduler.advance(MOMENT);
        assertTrue(room.containsParticipant("Alex"));

        presence.pageClosed("alex-tab");
        scheduler.advance(Duration.ZERO);

        assertFalse(room.containsParticipant("Alex"));
    }

    @Test
    @DisplayName("Someone whose connection is lost leaves after the grace period")
    void leavesAfterLosingConnection() {
        presence.hold(room.getId(), "Alex", "alex-tab");

        // A clean close too: the browser closes the connection itself when its heart-beat check fails, and the close
        // reaches the server when the internet is back
        presence.disconnected(disconnect("alex-tab", CloseStatus.NORMAL));

        scheduler.advance(GRACE_PERIOD.minus(MOMENT));
        assertTrue(room.containsParticipant("Alex"));
        scheduler.advance(MOMENT);
        assertFalse(room.containsParticipant("Alex"));
        verify(messagingTemplate).convertAndSend("/topic/room." + room.getId(),
                new RoomEvent(room.getId(), EventType.PARTICIPANT_REMOVED, new ParticipantDto("Alex", false)));
        verify(activity).left(LeaveReason.CONNECTION_LOST);
    }

    @Test
    @DisplayName("Someone who comes back in time keeps the seat")
    void returnsInTime() {
        presence.hold(room.getId(), "Alex", "alex-tab");
        presence.disconnected(disconnect("alex-tab", CloseStatus.NO_CLOSE_FRAME));
        scheduler.advance(GRACE_PERIOD.minus(MOMENT));

        Participant returned = presence.returnTo(room.getId(), "alex", "alex-tab-after-refresh");

        assertEquals("Alex", returned.getNickname());
        scheduler.advance(LATER);
        assertTrue(room.containsParticipant("Alex"));
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));

        // The new connection holds the seat now
        presence.disconnected(disconnect("alex-tab-after-refresh", CloseStatus.NO_CLOSE_FRAME));
        scheduler.advance(LATER);
        assertFalse(room.containsParticipant("Alex"));
    }

    @Test
    @DisplayName("The seat is kept while another tab of the same person is open")
    void anotherTabKeepsSeat() {
        presence.hold(room.getId(), "Alex", "first-tab");
        presence.returnTo(room.getId(), "Alex", "second-tab");

        presence.pageClosed("first-tab");
        presence.disconnected(disconnect("first-tab", CloseStatus.NORMAL));

        assertTrue(scheduler.tasks.isEmpty());

        presence.disconnected(disconnect("second-tab", CloseStatus.NO_CLOSE_FRAME));

        scheduler.advance(LATER);
        assertFalse(room.containsParticipant("Alex"));
    }

    @Test
    @DisplayName("Nobody is removed again after leaving the room on purpose")
    void leftOnPurpose() {
        presence.hold(room.getId(), "Alex", "alex-tab");

        presence.forget(room.getId(), "Alex");
        presence.disconnected(disconnect("alex-tab", CloseStatus.NORMAL));

        assertTrue(scheduler.tasks.isEmpty());
    }

    @Test
    @DisplayName("Nobody is removed again after leaving the room on purpose while the seat waits")
    void leftOnPurposeWhileSeatWaits() {
        presence.hold(room.getId(), "Alex", "alex-tab");
        presence.disconnected(disconnect("alex-tab", CloseStatus.NO_CLOSE_FRAME));

        presence.forget(room.getId(), "Alex");
        scheduler.advance(LATER);

        verify(roomService, never()).removeParticipant(any(), anyString());
    }

    @Test
    @DisplayName("Someone else who took the nickname is not removed when the first person's connection closes")
    void nicknameTakenAgain() {
        presence.hold(room.getId(), "Alex", "first-alex");
        presence.forget(room.getId(), "Alex");
        presence.hold(room.getId(), "Alex", "second-alex");

        presence.disconnected(disconnect("first-alex", CloseStatus.NO_CLOSE_FRAME));

        assertTrue(scheduler.tasks.isEmpty());
    }

    @Test
    @DisplayName("It's too late to come back after leaving the room")
    void returnAfterLeaving() {
        presence.hold(room.getId(), "Alex", "alex-tab");
        presence.disconnected(disconnect("alex-tab", CloseStatus.NO_CLOSE_FRAME));
        scheduler.advance(LATER);

        assertThrows(RoomServiceException.class, () -> presence.returnTo(room.getId(), "Alex", "alex-tab-later"));
    }

    @Test
    @DisplayName("Nobody can come back to a room they never joined")
    void returnToUnknownSeat() {
        assertThrows(RoomServiceException.class, () -> presence.returnTo(room.getId(), "Nobody", "some-tab"));
        assertThrows(RoomServiceException.class, () -> presence.returnTo(UUID.randomUUID(), "Alex", "some-tab"));

        // The failed attempts hold no seat
        presence.disconnected(disconnect("some-tab", CloseStatus.NORMAL));
        assertTrue(scheduler.tasks.isEmpty());
    }

    @Test
    @DisplayName("The room is deleted when the last person loses the connection for good")
    void lastPersonLeaves() {
        presence.hold(room.getId(), "Dmitry", "dmitry-tab");
        presence.hold(room.getId(), "Alex", "alex-tab");

        presence.disconnected(disconnect("dmitry-tab", CloseStatus.NO_CLOSE_FRAME));
        presence.disconnected(disconnect("alex-tab", CloseStatus.NO_CLOSE_FRAME));
        scheduler.advance(LATER);

        verify(roomService).removeParticipant(room.getId(), "dmitry");
        verify(roomService).removeParticipant(room.getId(), "alex");
        assertTrue(room.getParticipants().isEmpty());
    }

    @Test
    @DisplayName("A room that is already gone is not a problem")
    void roomAlreadyGone() {
        presence.hold(room.getId(), "Alex", "alex-tab");
        presence.disconnected(disconnect("alex-tab", CloseStatus.NO_CLOSE_FRAME));
        when(roomService.removeParticipant(room.getId(), "alex")).thenThrow(new RoomServiceException("Room is not found"));

        scheduler.advance(LATER);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        verify(activity, never()).left(any());
    }

    @Test
    @DisplayName("After a restart everyone in the stored rooms gets time to come back")
    void afterRestart() {
        when(roomService.getAll()).thenReturn(List.of(room));

        presence.waitForReturns();

        assertEquals(2, scheduler.tasks.size());
        assertTrue(scheduler.tasks.stream().allMatch(task -> task.time().compareTo(GRACE_PERIOD) > 0));

        presence.returnTo(room.getId(), "Dmitry", "dmitry-tab");
        scheduler.advance(LATER);

        assertTrue(room.containsParticipant("Dmitry"));
        assertFalse(room.containsParticipant("Alex"));
        verify(activity).left(LeaveReason.RESTART);
    }

    @Test
    @DisplayName("A seat taken before the restart check is not reset by it")
    void returnedBeforeRestartCheck() {
        when(roomService.getAll()).thenReturn(List.of(room));
        presence.returnTo(room.getId(), "Dmitry", "dmitry-tab");

        presence.waitForReturns();

        assertEquals(1, scheduler.tasks.size());
        scheduler.advance(LATER);
        assertTrue(room.containsParticipant("Dmitry"));
        assertFalse(room.containsParticipant("Alex"));
    }

    @Test
    @DisplayName("Only people with an open connection are online")
    void peopleOnline() {
        presence.hold(room.getId(), "Dmitry", "dmitry-tab");
        presence.hold(room.getId(), "Alex", "first-tab");
        presence.returnTo(room.getId(), "Alex", "second-tab");
        assertEquals(2, presence.peopleOnline());

        presence.disconnected(disconnect("first-tab", CloseStatus.NORMAL));
        assertEquals(2, presence.peopleOnline());

        // Waiting for Dmitry to come back
        presence.disconnected(disconnect("dmitry-tab", CloseStatus.NO_CLOSE_FRAME));
        assertEquals(1, presence.peopleOnline());

        presence.forget(room.getId(), "Alex");
        assertEquals(0, presence.peopleOnline());
    }

    private Optional<Departure> stepAway(String nickname) {
        Vote vote = room.findVote(nickname).orElse(null);
        return room.removeParticipant(nickname).map(participant -> new Departure(participant, vote));
    }

    private static SessionDisconnectEvent disconnect(String sessionId, CloseStatus closeStatus) {
        Message<byte[]> message = MessageBuilder.withPayload(new byte[0]).build();
        return new SessionDisconnectEvent(RoomPresenceTest.class, message, sessionId, closeStatus);
    }

    /**
     * Collects scheduled tasks and runs them when a test moves the clock past their time.
     */
    private static final class FakeScheduler implements TaskScheduler {

        private final List<Task> tasks = new ArrayList<>();
        // The time the test has moved on since it started
        private Duration now = Duration.ZERO;

        record Task(Runnable runnable, Duration time, ScheduledFuture<?> future) {
        }

        void advance(Duration duration) {
            Duration end = now.plus(duration);
            Optional<Task> next = nextTask(end);
            while(next.isPresent()) {
                Task task = next.get();
                tasks.remove(task);
                if(task.time().compareTo(now) > 0) {
                    now = task.time();
                }
                task.runnable().run();
                next = nextTask(end);
            }
            now = end;
        }

        private Optional<Task> nextTask(Duration end) {
            return tasks.stream()
                    .filter(task -> !task.future().isCancelled() && task.time().compareTo(end) <= 0)
                    .min(Comparator.comparing(Task::time));
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            when(future.cancel(false)).thenAnswer(invocation -> {
                when(future.isCancelled()).thenReturn(true);
                return true;
            });
            tasks.add(new Task(task, now.plus(Duration.between(Instant.now(), startTime)), future));
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
