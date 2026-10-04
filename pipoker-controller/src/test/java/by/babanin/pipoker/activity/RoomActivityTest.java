package by.babanin.pipoker.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionConnectedEvent;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class RoomActivityTest {

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
        activity.roomCreated();
        activity.joined(false);
        activity.joined(false);
        activity.joined(true);
        activity.voted();
        activity.voted();
        activity.revealed();
        activity.cleared();
        activity.timerStarted();
        activity.returned();
        activity.left(LeaveReason.CONNECTION_LOST);

        assertEquals(1, count("pipoker.connections"));
        assertEquals(1, count("pipoker.rooms.created"));
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

    private double count(String name) {
        return registry.get(name).counter().count();
    }
}
