package by.babanin.pipoker.activity;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Counts what people do in the rooms, for the activity dashboard. Nothing about who they are is kept:
 * no nicknames, room names or room ids, only how many times something happened.
 * <p>
 * The counters start from zero when the backend starts. Prometheus scrapes them from {@code /actuator/prometheus}
 * on the management port and takes care of the restarts.
 */
@Component
public class RoomActivity {

    private final Counter connections;
    private final Counter roomsCreated;
    private final Counter votersJoined;
    private final Counter watchersJoined;
    private final Counter returns;
    private final Counter votes;
    private final Counter reveals;
    private final Counter clears;
    private final Counter timers;
    private final Counter tasks;
    private final Counter estimates;
    private final Map<LeaveReason, Counter> leaves = new EnumMap<>(LeaveReason.class);

    public RoomActivity(MeterRegistry registry) {
        connections = Counter.builder("pipoker.connections")
                .description("Connections opened by browsers, including page refreshes and reconnects")
                .register(registry);
        // Not "pipoker.rooms.created": Prometheus reserves the _created suffix and would expose it as pipoker_rooms_total,
        // which clashes with the pipoker.rooms gauge
        roomsCreated = Counter.builder("pipoker.room.creations")
                .description("Rooms created")
                .register(registry);
        votersJoined = joined(registry, "voter");
        watchersJoined = joined(registry, "watcher");
        for(LeaveReason reason : LeaveReason.values()) {
            leaves.put(reason, Counter.builder("pipoker.participants.left")
                    .description("People who left a room, by the reason")
                    .tag("reason", reason.tag())
                    .register(registry));
        }
        returns = Counter.builder("pipoker.participants.returned")
                .description("People who came back to their seat after losing the connection")
                .register(registry);
        votes = Counter.builder("pipoker.votes")
                .description("Cards chosen, including changed votes")
                .register(registry);
        reveals = Counter.builder("pipoker.rounds.revealed")
                .description("Times the cards were turned over")
                .register(registry);
        clears = Counter.builder("pipoker.rounds.cleared")
                .description("Times the votes were cleared for a new round")
                .register(registry);
        timers = Counter.builder("pipoker.timers.started")
                .description("Times the discussion timer was started")
                .register(registry);
        tasks = Counter.builder("pipoker.tasks.named")
                .description("Times someone named the task of a round, including changed names")
                .register(registry);
        estimates = Counter.builder("pipoker.estimates.accepted")
                .description("Times someone accepted the estimate of a round, including changed estimates")
                .register(registry);
    }

    @EventListener
    public void connected(SessionConnectedEvent event) {
        connections.increment();
    }

    public void roomCreated() {
        roomsCreated.increment();
    }

    public void joined(boolean watcher) {
        (watcher ? watchersJoined : votersJoined).increment();
    }

    public void left(LeaveReason reason) {
        leaves.get(reason).increment();
    }

    public void returned() {
        returns.increment();
    }

    public void voted() {
        votes.increment();
    }

    public void revealed() {
        reveals.increment();
    }

    public void cleared() {
        clears.increment();
    }

    public void timerStarted() {
        timers.increment();
    }

    public void taskNamed() {
        tasks.increment();
    }

    public void estimateAccepted() {
        estimates.increment();
    }

    private static Counter joined(MeterRegistry registry, String role) {
        return Counter.builder("pipoker.participants.joined")
                .description("People who joined a room, by their role")
                .tag("role", role)
                .register(registry);
    }
}
