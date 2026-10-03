package by.babanin.pipoker.presence;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import by.babanin.pipoker.PiPokerApplication;
import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.event.RoomEvent;
import by.babanin.pipoker.event.RoomEvent.EventType;
import by.babanin.pipoker.exception.RoomServiceException;
import by.babanin.pipoker.model.ParticipantDto;
import by.babanin.pipoker.service.RoomService;
import lombok.extern.log4j.Log4j2;

/**
 * Keeps people at the table while their connection is lost for a moment.
 * <p>
 * Everyone who creates or joins a room holds their seat with their STOMP session. When the last session holding
 * a seat is closed, the seat waits for the grace period: after a page refresh or a short network drop the browser
 * connects again and returns to the seat with {@link #returnTo}. When nobody returns in time, the person leaves
 * the room as if they left it themselves, so nobody stays at the table after closing the tab.
 * <p>
 * Seats are kept in memory, which is enough for the single backend. After a restart nobody holds a seat yet,
 * so everyone in the stored rooms gets the startup grace period to return.
 */
@Component
@Log4j2
public class RoomPresence {

    private final RoomService roomService;
    private final ModelMapper modelMapper;
    private final SimpMessageSendingOperations messagingTemplate;
    private final TaskScheduler scheduler;
    private final Duration gracePeriod;
    private final Duration startupGracePeriod;

    // Both maps are guarded by this lock
    private final Object lock = new Object();
    private final Map<SeatKey, Seat> seats = new HashMap<>();
    private final Map<String, Set<SeatKey>> sessionSeats = new HashMap<>();

    public RoomPresence(RoomService roomService, ModelMapper modelMapper, SimpMessageSendingOperations messagingTemplate,
            @Qualifier("roomPresenceScheduler") TaskScheduler scheduler,
            @Value("${presence.grace-period:10s}") Duration gracePeriod,
            @Value("${presence.startup-grace-period:30s}") Duration startupGracePeriod) {
        this.roomService = roomService;
        this.modelMapper = modelMapper;
        this.messagingTemplate = messagingTemplate;
        this.scheduler = scheduler;
        this.gracePeriod = gracePeriod;
        this.startupGracePeriod = startupGracePeriod;
    }

    /**
     * The participant has just created or joined the room from this session.
     */
    public void hold(UUID roomId, String nickname, String sessionId) {
        SeatKey seatKey = new SeatKey(roomId, Participant.normalizeNickname(nickname));
        synchronized(lock) {
            Seat previous = seats.remove(seatKey);
            if(previous != null) {
                release(seatKey, previous);
            }
            Seat seat = new Seat();
            seats.put(seatKey, seat);
            take(seatKey, seat, sessionId);
        }
    }

    /**
     * Returns the participant to their seat from a new session, if they haven't left the room yet.
     *
     * @return the participant as stored in the room
     * @throws RoomServiceException if the participant is no longer in the room
     */
    public Participant returnTo(UUID roomId, String nickname, String sessionId) {
        SeatKey seatKey = new SeatKey(roomId, Participant.normalizeNickname(nickname));
        Seat seat;
        synchronized(lock) {
            seat = seats.get(seatKey);
            if(seat != null && seat.leaving) {
                throw notInRoom(roomId, nickname);
            }
            if(seat == null) {
                // Someone stored in the room without a seat yet, for example right after a restart
                seat = new Seat();
                seats.put(seatKey, seat);
            }
            take(seatKey, seat, sessionId);
        }
        // The seat is taken first, so the participant can't leave between this check and the answer
        Optional<Participant> participant = roomService.find(roomId)
                .flatMap(room -> room.findParticipant(nickname));
        if(participant.isEmpty()) {
            synchronized(lock) {
                if(seats.remove(seatKey, seat)) {
                    release(seatKey, seat);
                }
            }
            throw notInRoom(roomId, nickname);
        }
        return participant.get();
    }

    /**
     * The participant has left the room on purpose, so nobody holds their seat anymore.
     */
    public void forget(UUID roomId, String nickname) {
        SeatKey seatKey = new SeatKey(roomId, Participant.normalizeNickname(nickname));
        synchronized(lock) {
            Seat seat = seats.remove(seatKey);
            if(seat != null) {
                release(seatKey, seat);
            }
        }
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        synchronized(lock) {
            Set<SeatKey> heldSeats = sessionSeats.remove(event.getSessionId());
            if(heldSeats == null) {
                return;
            }
            heldSeats.forEach(seatKey -> {
                Seat seat = seats.get(seatKey);
                if(seat != null && seat.sessionIds.remove(event.getSessionId()) && seat.sessionIds.isEmpty()) {
                    scheduleLeaving(seatKey, seat, gracePeriod);
                }
            });
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void waitForReturns() {
        for(Room room : roomService.getAll()) {
            for(Participant participant : room.getParticipants()) {
                SeatKey seatKey = new SeatKey(room.getId(), participant.normalizeNickname());
                synchronized(lock) {
                    if(!seats.containsKey(seatKey)) {
                        Seat seat = new Seat();
                        seats.put(seatKey, seat);
                        scheduleLeaving(seatKey, seat, startupGracePeriod);
                    }
                }
            }
        }
    }

    private void take(SeatKey seatKey, Seat seat, String sessionId) {
        seat.cancelLeaving();
        seat.sessionIds.add(sessionId);
        sessionSeats.computeIfAbsent(sessionId, id -> new HashSet<>()).add(seatKey);
    }

    private void release(SeatKey seatKey, Seat seat) {
        seat.cancelLeaving();
        seat.sessionIds.forEach(sessionId -> {
            Set<SeatKey> heldSeats = sessionSeats.get(sessionId);
            if(heldSeats != null) {
                heldSeats.remove(seatKey);
            }
        });
        seat.sessionIds.clear();
    }

    private void scheduleLeaving(SeatKey seatKey, Seat seat, Duration delay) {
        seat.cancelLeaving();
        int attempt = seat.leavingAttempt;
        seat.scheduledLeaving = scheduler.schedule(() -> leave(seatKey, seat, attempt), Instant.now().plus(delay));
    }

    private void leave(SeatKey seatKey, Seat seat, int attempt) {
        synchronized(lock) {
            if(seats.get(seatKey) != seat || seat.leavingAttempt != attempt || !seat.sessionIds.isEmpty()) {
                return;
            }
            seat.leaving = true;
        }
        try {
            roomService.removeParticipant(seatKey.roomId(), seatKey.nickname()).ifPresent(participant -> {
                log.info("{} left the room {} after losing the connection", participant.getNickname(), seatKey.roomId());
                ParticipantDto removed = modelMapper.map(participant, ParticipantDto.class);
                messagingTemplate.convertAndSend(PiPokerApplication.TOPIC_ROOM_DESTINATION_PREFIX + "." + seatKey.roomId(),
                        new RoomEvent(seatKey.roomId(), EventType.PARTICIPANT_REMOVED, removed));
            });
        }
        catch(RuntimeException exception) {
            // The room is gone already, or the database is not available: there is nobody to tell
            log.warn("Couldn't remove {} from the room {}: {}", seatKey.nickname(), seatKey.roomId(), exception.getMessage());
        }
        finally {
            synchronized(lock) {
                seats.remove(seatKey, seat);
            }
        }
    }

    private static RoomServiceException notInRoom(UUID roomId, String nickname) {
        return new RoomServiceException(String.format("Participant \"%s\" is not in the room \"%s\"", nickname, roomId));
    }

    private record SeatKey(UUID roomId, String nickname) {
    }

    private static final class Seat {

        private final Set<String> sessionIds = new HashSet<>();
        private ScheduledFuture<?> scheduledLeaving;
        private int leavingAttempt;
        // The participant is being removed from the room, so it is too late to return
        private boolean leaving;

        private void cancelLeaving() {
            if(scheduledLeaving != null) {
                scheduledLeaving.cancel(false);
                scheduledLeaving = null;
            }
            leavingAttempt++;
        }
    }
}
