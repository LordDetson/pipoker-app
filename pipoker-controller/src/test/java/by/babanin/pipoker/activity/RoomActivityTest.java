package by.babanin.pipoker.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionConnectedEvent;

import by.babanin.pipoker.service.RoomRemovedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class RoomActivityTest {

    private static final UUID ROOM = UUID.randomUUID();

    private SimpleMeterRegistry registry;
    private RoomActivity activity;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        activity = new RoomActivity(registry);
    }

    @Test
    void countsWhatPeopleDo() {
        activity.connected(new SessionConnectedEvent(this, MessageBuilder.withPayload(new byte[0]).build()));
        activity.visited(Source.HABR);
        activity.visited(Source.HABR);
        activity.visited(Source.DIRECT);
        activity.roomCreated(ROOM, Source.HABR);
        activity.joined(ROOM, false);
        activity.joined(ROOM, false);
        activity.joined(ROOM, true);
        activity.voted(ROOM);
        activity.voted(ROOM);
        activity.revealed(ROOM);
        activity.cleared(ROOM);
        activity.timerStarted(ROOM);
        activity.returned(ROOM);
        activity.left(ROOM, LeaveReason.CONNECTION_LOST);

        assertEquals(1, count("pipoker.connections"));
        assertEquals(2, registry.get("pipoker.visits").tag("source", "habr").counter().count());
        assertEquals(1, registry.get("pipoker.visits").tag("source", "direct").counter().count());
        assertEquals(1, registry.get("pipoker.room.creations").tag("source", "habr").counter().count());
        assertEquals(0, registry.get("pipoker.room.creations").tag("source", "direct").counter().count());
        assertEquals(2, registry.get("pipoker.participants.joined").tag("role", "voter").counter().count());
        assertEquals(1, registry.get("pipoker.participants.joined").tag("role", "watcher").counter().count());
        assertEquals(2, count("pipoker.votes"));
        assertEquals(1, count("pipoker.rounds.revealed"));
        assertEquals(1, count("pipoker.rounds.cleared"));
        assertEquals(1, count("pipoker.timers.started"));
        assertEquals(1, count("pipoker.participants.returned"));
        assertEquals(1, registry.get("pipoker.participants.left").tag("reason", "connection_lost").counter().count());
        assertEquals(0, registry.get("pipoker.participants.left").tag("reason", "left").counter().count());
    }

    @Test
    void everyReasonIsThereBeforeAnyoneLeaves() {
        // Each series exists from the start, so the dashboard shows a zero rather than no data
        assertEquals(LeaveReason.values().length, registry.get("pipoker.participants.left").counters().size());
    }

    @Test
    void everySourceIsThereBeforeAnyoneComes() {
        // All but the checks and tests, which are never counted
        assertEquals(Source.values().length - 1, registry.get("pipoker.visits").counters().size());
        assertEquals(Source.values().length - 1, registry.get("pipoker.room.creations").counters().size());
    }

    @Test
    void leavesOutChecksAndTests() {
        UUID testRoom = UUID.randomUUID();
        activity.visited(Source.TEST);
        activity.roomCreated(testRoom, Source.TEST);
        activity.joined(testRoom, false);
        activity.joined(testRoom, true);
        activity.roleChanged(testRoom, true);
        activity.voted(testRoom);
        activity.revealed(testRoom);
        activity.cleared(testRoom);
        activity.timerStarted(testRoom);
        activity.taskNamed(testRoom);
        activity.estimateAccepted(testRoom);
        activity.returned(testRoom);
        activity.left(testRoom, LeaveReason.LEFT);
        activity.joined(ROOM, false);

        assertTrue(activity.isTestRoom(testRoom));
        assertFalse(activity.isTestRoom(ROOM));
        assertEquals(1, activity.testRoomCount());
        assertEquals(0, registry.get("pipoker.visits").counters().stream().mapToDouble(counter -> counter.count()).sum());
        assertEquals(0, registry.get("pipoker.room.creations").counters().stream().mapToDouble(counter -> counter.count()).sum());
        assertEquals(1, registry.get("pipoker.participants.joined").tag("role", "voter").counter().count());
        assertEquals(0, registry.get("pipoker.participants.joined").tag("role", "watcher").counter().count());
        assertEquals(0, registry.get("pipoker.participants.role.changed").tag("role", "watcher").counter().count());
        assertEquals(0, count("pipoker.votes"));
        assertEquals(0, count("pipoker.rounds.revealed"));
        assertEquals(0, count("pipoker.rounds.cleared"));
        assertEquals(0, count("pipoker.timers.started"));
        assertEquals(0, count("pipoker.tasks.named"));
        assertEquals(0, count("pipoker.estimates.accepted"));
        assertEquals(0, count("pipoker.participants.returned"));
        assertEquals(0, registry.get("pipoker.participants.left").tag("reason", "left").counter().count());
    }

    @Test
    void forgetsTestRoomsOnceTheyAreGone() {
        UUID removed = UUID.randomUUID();
        UUID closed = UUID.randomUUID();
        activity.roomCreated(removed, Source.TEST);
        activity.roomCreated(closed, Source.TEST);

        activity.roomRemoved(new RoomRemovedEvent(removed));
        activity.roomClosed(closed);

        assertFalse(activity.isTestRoom(removed));
        assertFalse(activity.isTestRoom(closed));
        assertEquals(0, activity.testRoomCount());
    }

    private double count(String name) {
        return registry.get(name).counter().count();
    }
}
