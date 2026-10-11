package by.babanin.pipoker.activity;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;

import by.babanin.pipoker.service.RoomRemovedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Counts what people do in the rooms, for the activity dashboard. The metrics keep nothing about who they are:
 * no nicknames, room names or room ids, only how many times something happened, and for visits and new rooms
 * where the person came from (see {@link Source}).
 * <p>
 * PiPoker's own checks and automated tests are no visitors: their visits and everything in the rooms they create
 * ({@link Source#TEST}) are left out, so the dashboard shows real people only.
 * <p>
 * The counters start from zero when the backend starts. Prometheus scrapes them from {@code /actuator/prometheus}
 * on the management port and takes care of the restarts.
 */
@Component
public class RoomActivity {

    private final Counter connections;
    private final Map<Source, Counter> visits = new EnumMap<>(Source.class);
    private final Map<Source, Counter> roomsCreated = new EnumMap<>(Source.class);
    private final Counter votersJoined;
    private final Counter watchersJoined;
    private final Counter becameWatchers;
    private final Counter becameVoters;
    private final Counter returns;
    private final Counter votes;
    private final Counter reveals;
    private final Counter clears;
    private final Counter timers;
    private final Counter tasks;
    private final Counter estimates;
    private final Map<LeaveReason, Counter> leaves = new EnumMap<>(LeaveReason.class);
    // Kept in memory only: a test room lives for seconds or minutes, so a backend restart while one is open is rare,
    // and then that room is counted like any other
    private final Set<UUID> testRooms = ConcurrentHashMap.newKeySet();

    public RoomActivity(MeterRegistry registry) {
        connections = Counter.builder("pipoker.connections")
                .description("Connections opened by browsers, including page refreshes and reconnects")
                .register(registry);
        for(Source source : Source.values()) {
            if(source == Source.TEST) {
                continue;
            }
            visits.put(source, Counter.builder("pipoker.visits")
                    .description("Start page opened, by where the person came from")
                    .tag("source", source.tag())
                    .register(registry));
            // Not "pipoker.rooms.created": Prometheus reserves the _created suffix and would expose it as
            // pipoker_rooms_total, which clashes with the pipoker.rooms gauge
            roomsCreated.put(source, Counter.builder("pipoker.room.creations")
                    .description("Rooms created, by where the person came from")
                    .tag("source", source.tag())
                    .register(registry));
        }
        votersJoined = joined(registry, "voter");
        watchersJoined = joined(registry, "watcher");
        for(LeaveReason reason : LeaveReason.values()) {
            leaves.put(reason, Counter.builder("pipoker.participants.left")
                    .description("People who left a room, by the reason")
                    .tag("reason", reason.tag())
                    .register(registry));
        }
        becameWatchers = roleChanged(registry, "watcher");
        becameVoters = roleChanged(registry, "voter");
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

    public void visited(Source source) {
        if(source != Source.TEST) {
            visits.get(source).increment();
        }
    }

    public void roomCreated(UUID roomId, Source source) {
        if(source == Source.TEST) {
            testRooms.add(roomId);
        }
        else {
            roomsCreated.get(source).increment();
        }
    }

    /**
     * Whether the room was created by a check or a test, so nothing in it is counted.
     */
    public boolean isTestRoom(UUID roomId) {
        return testRooms.contains(roomId);
    }

    /**
     * Test rooms that still exist.
     */
    public int testRoomCount() {
        return testRooms.size();
    }

    @EventListener
    public void roomRemoved(RoomRemovedEvent event) {
        roomClosed(event.roomId());
    }

    /**
     * The room was deleted.
     */
    public void roomClosed(UUID roomId) {
        testRooms.remove(roomId);
    }

    public void joined(UUID roomId, boolean watcher) {
        count(roomId, watcher ? watchersJoined : votersJoined);
    }

    public void roleChanged(UUID roomId, boolean watcher) {
        count(roomId, watcher ? becameWatchers : becameVoters);
    }

    public void left(UUID roomId, LeaveReason reason) {
        count(roomId, leaves.get(reason));
    }

    public void returned(UUID roomId) {
        count(roomId, returns);
    }

    public void voted(UUID roomId) {
        count(roomId, votes);
    }

    public void revealed(UUID roomId) {
        count(roomId, reveals);
    }

    public void cleared(UUID roomId) {
        count(roomId, clears);
    }

    public void timerStarted(UUID roomId) {
        count(roomId, timers);
    }

    public void taskNamed(UUID roomId) {
        count(roomId, tasks);
    }

    public void estimateAccepted(UUID roomId) {
        count(roomId, estimates);
    }

    private void count(UUID roomId, Counter counter) {
        if(!isTestRoom(roomId)) {
            counter.increment();
        }
    }

    private static Counter joined(MeterRegistry registry, String role) {
        return Counter.builder("pipoker.participants.joined")
                .description("People who joined a room, by their role")
                .tag("role", role)
                .register(registry);
    }

    private static Counter roleChanged(MeterRegistry registry, String role) {
        return Counter.builder("pipoker.participants.role.changed")
                .description("People who switched their role inside a room, by the new role")
                .tag("role", role)
                .register(registry);
    }
}
