package by.babanin.pipoker.presence;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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
import by.babanin.pipoker.activity.LeaveReason;
import by.babanin.pipoker.activity.RoomActivity;
import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.event.RoomEvent;
import by.babanin.pipoker.event.RoomEvent.EventType;
import by.babanin.pipoker.exception.ErrorCode;
import by.babanin.pipoker.exception.RoomServiceException;
import by.babanin.pipoker.model.ParticipantDto;
import by.babanin.pipoker.model.RoundDto;
import by.babanin.pipoker.model.VoteDto;
import by.babanin.pipoker.service.Departure;
import by.babanin.pipoker.service.RoomRemovedEvent;
import by.babanin.pipoker.service.RoomService;
import lombok.extern.log4j.Log4j2;

/**
 * Keeps people at the table while their connection is lost for a moment or their page is refreshed, and lets them go
 * as soon as they close the page.
 * <p>
 * Everyone who creates or joins a room holds their seat with their STOMP session. A page that is being closed or
 * refreshed says so with {@link #pageClosed} right before its connection closes. The server can't tell a refresh from
 * a close, so as soon as nobody holds the seat the person steps away: they leave the table with their vote, and the
 * seat remembers both for the grace period. A refreshed page brings them back with {@link #returnTo}, and everyone
 * sees them at the table again with the vote. When the last session holding a seat closes without saying so,
 * the connection was lost: the person stays at the table for the grace period, and the browser can connect again
 * and return to the seat. When nobody returns in time, the person leaves the room as if they left it themselves.
 * <p>
 * Every change of a person's place in the room holds their lock in {@link SeatLocks} until the room is told about it,
 * so the others hear about the changes in the order they were made.
 * <p>
 * Seats are kept in memory, which is enough for the single backend. After a restart nobody holds a seat yet,
 * so everyone in the stored rooms gets the startup grace period to return.
 */
@Component
@Log4j2
public class RoomPresence {

    private final RoomService roomService;
    private final SeatLocks seatLocks;
    private final ModelMapper modelMapper;
    private final SimpMessageSendingOperations messagingTemplate;
    private final RoomActivity activity;
    private final TaskScheduler scheduler;
    private final Duration gracePeriod;
    private final Duration startupGracePeriod;

    // The collections are guarded by this lock
    private final Object lock = new Object();
    private final Map<SeatKey, Seat> seats = new HashMap<>();
    private final Map<String, Set<SeatKey>> sessionSeats = new HashMap<>();
    // Sessions whose page said it was being closed, until their connection closes
    private final Set<String> closingSessions = new HashSet<>();

    public RoomPresence(RoomService roomService, SeatLocks seatLocks, ModelMapper modelMapper,
            SimpMessageSendingOperations messagingTemplate, RoomActivity activity, @Qualifier("roomPresenceScheduler") TaskScheduler scheduler,
            @Value("${presence.grace-period:10s}") Duration gracePeriod,
            @Value("${presence.startup-grace-period:30s}") Duration startupGracePeriod) {
        this.roomService = roomService;
        this.seatLocks = seatLocks;
        this.modelMapper = modelMapper;
        this.messagingTemplate = messagingTemplate;
        this.activity = activity;
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
     * Returns the participant to their seat from a new session, if they haven't left the room yet. Someone who has
     * stepped away comes back to the table with their vote. A page that comes back while the person is being taken
     * away from the table waits until everyone is told, and brings them back right after.
     *
     * @return the participant as stored in the room
     * @throws RoomServiceException if the participant is no longer in the room
     */
    public Participant returnTo(UUID roomId, String nickname, String sessionId) {
        SeatKey seatKey = new SeatKey(roomId, Participant.normalizeNickname(nickname));
        return seatLocks.change(roomId, nickname, () -> returnToSeat(seatKey, nickname, sessionId));
    }

    private Participant returnToSeat(SeatKey seatKey, String nickname, String sessionId) {
        UUID roomId = seatKey.roomId();
        Seat seat;
        Departure departure;
        synchronized(lock) {
            seat = seats.get(seatKey);
            if(seat == null) {
                // Someone stored in the room without a seat yet, for example right after a restart
                seat = new Seat();
                seats.put(seatKey, seat);
            }
            departure = seat.departure;
            seat.departure = null;
            take(seatKey, seat, sessionId);
        }
        if(departure != null) {
            return bringBack(seatKey, seat, departure);
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

    /**
     * The page of this session is being closed or refreshed, and its connection is about to close.
     * <p>
     * The frames of a connection are handled one after another, so this can come after the connection is closed,
     * when a previous frame took longer. Then the seats that the session has just left wait for the grace period
     * already, and the person steps away now.
     */
    public void pageClosed(String sessionId) {
        synchronized(lock) {
            if(sessionSeats.containsKey(sessionId)) {
                closingSessions.add(sessionId);
                return;
            }
            seats.forEach((seatKey, seat) -> {
                if(sessionId.equals(seat.closedSessionId) && seat.sessionIds.isEmpty() && !seat.leaving
                        && seat.departure == null) {
                    scheduleSteppingAway(seatKey, seat);
                }
            });
        }
    }

    /**
     * A new round starts in the room, so whoever stepped away comes back without the vote of the previous round.
     */
    public void votesCleared(UUID roomId) {
        synchronized(lock) {
            seats.forEach((seatKey, seat) -> {
                if(seat.departure != null && seatKey.roomId().equals(roomId)) {
                    seat.departure = seat.departure.withoutVote();
                }
            });
        }
    }

    /**
     * Reveals the cards by themselves once every voter at the table has voted. Someone who stepped
     * away while their page refreshes is still at the table, so the cards wait until they come back or leave for good.
     * <p>
     * It follows a change that has already happened and been told to the room, so a failure here doesn't undo it:
     * the cards stay hidden, and anyone can still reveal them.
     */
    public void revealIfEveryoneVoted(UUID roomId) {
        synchronized(lock) {
            boolean voterAway = seats.entrySet().stream().anyMatch(entry -> entry.getKey().roomId().equals(roomId)
                    && entry.getValue().departure != null && !entry.getValue().departure.participant().isWatcher());
            if(voterAway) {
                return;
            }
        }
        try {
            roomService.showVotesIfEveryoneVoted(roomId).ifPresent(round -> {
                log.info("The cards are revealed in the room {} because everyone has voted", roomId);
                activity.revealed();
                tellRoom(roomId, new RoomEvent(roomId, EventType.SHOW_VOTES, modelMapper.map(round, RoundDto.class)));
            });
        }
        catch(RuntimeException exception) {
            log.warn("Couldn't reveal the cards in the room {} after everyone voted: {}", roomId, exception.getMessage());
        }
    }

    /**
     * The room is closed, so nobody holds a seat in it anymore and nobody who stepped away can come back.
     *
     * @return the people who had stepped away from the table and could still come back
     */
    public List<Participant> roomClosed(UUID roomId) {
        List<Participant> steppedAway = new ArrayList<>();
        synchronized(lock) {
            Iterator<Map.Entry<SeatKey, Seat>> entries = seats.entrySet().iterator();
            while(entries.hasNext()) {
                Map.Entry<SeatKey, Seat> entry = entries.next();
                if(entry.getKey().roomId().equals(roomId)) {
                    Seat seat = entry.getValue();
                    release(entry.getKey(), seat);
                    if(seat.departure != null) {
                        steppedAway.add(seat.departure.participant());
                    }
                    entries.remove();
                }
            }
        }
        return steppedAway;
    }

    /**
     * How many people are at the table with an open connection right now.
     */
    public int peopleOnline() {
        synchronized(lock) {
            return (int) seats.values().stream()
                    .filter(seat -> !seat.sessionIds.isEmpty())
                    .count();
        }
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        synchronized(lock) {
            boolean pageClosed = closingSessions.remove(sessionId);
            Set<SeatKey> heldSeats = sessionSeats.remove(sessionId);
            if(heldSeats == null) {
                return;
            }
            heldSeats.forEach(seatKey -> {
                Seat seat = seats.get(seatKey);
                if(seat != null && seat.sessionIds.remove(sessionId) && seat.sessionIds.isEmpty()) {
                    if(pageClosed) {
                        scheduleSteppingAway(seatKey, seat);
                    }
                    else {
                        scheduleLeaving(seatKey, seat, Instant.now().plus(gracePeriod), LeaveReason.CONNECTION_LOST);
                    }
                    seat.closedSessionId = sessionId;
                }
            });
        }
    }

    /**
     * Everyone left the room, so it was deleted. The pages still open on it, like the join form of someone who followed
     * the invitation a moment before, are told that the room no longer exists.
     */
    @EventListener
    public void roomRemoved(RoomRemovedEvent event) {
        UUID roomId = event.roomId();
        tellRoom(roomId, new RoomEvent(roomId, EventType.ROOM_REMOVED));
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
                        scheduleLeaving(seatKey, seat, Instant.now().plus(startupGracePeriod), LeaveReason.RESTART);
                    }
                }
            }
        }
    }

    private void take(SeatKey seatKey, Seat seat, String sessionId) {
        seat.cancelLeaving();
        seat.closedSessionId = null;
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

    // The leaving runs on the scheduler, outside the lock, also when it is due at once. It holds the person's seat lock,
    // like every change of their place in the room.
    private void scheduleLeaving(SeatKey seatKey, Seat seat, Instant time, LeaveReason reason) {
        seat.cancelLeaving();
        int attempt = seat.leavingAttempt;
        seat.scheduledLeaving = scheduler.schedule(
                () -> seatLocks.change(seatKey.roomId(), seatKey.nickname(), () -> leave(seatKey, seat, attempt, reason)),
                time);
    }

    // Stepping away runs on the scheduler too, at once
    private void scheduleSteppingAway(SeatKey seatKey, Seat seat) {
        seat.cancelLeaving();
        int attempt = seat.leavingAttempt;
        seat.scheduledLeaving = scheduler.schedule(
                () -> seatLocks.change(seatKey.roomId(), seatKey.nickname(), () -> stepAway(seatKey, seat, attempt)),
                Instant.now());
    }

    private void stepAway(SeatKey seatKey, Seat seat, int attempt) {
        synchronized(lock) {
            if(seats.get(seatKey) != seat || seat.leavingAttempt != attempt || !seat.sessionIds.isEmpty()) {
                return;
            }
            // The closed page can't take the person away a second time meanwhile
            seat.leaving = true;
        }
        UUID roomId = seatKey.roomId();
        Departure departure = null;
        try {
            departure = roomService.stepAway(roomId, seatKey.nickname()).orElse(null);
            if(departure != null) {
                Participant participant = departure.participant();
                log.info("{} left the table in the room {} after closing the page", participant.getNickname(), roomId);
                tellRoom(roomId, new RoomEvent(roomId, EventType.PARTICIPANT_REMOVED, toDto(participant)));
            }
        }
        catch(RuntimeException exception) {
            // The room is gone already, or the database is not available: there is nobody to tell
            log.warn("Couldn't take {} away from the table in the room {}: {}", seatKey.nickname(), roomId,
                    exception.getMessage());
        }
        finally {
            synchronized(lock) {
                if(departure != null && seats.get(seatKey) == seat) {
                    seat.leaving = false;
                    seat.departure = departure;
                    int leavingAttempt = seat.leavingAttempt;
                    seat.scheduledLeaving = scheduler.schedule(() -> leaveForGood(seatKey, seat, leavingAttempt),
                            Instant.now().plus(gracePeriod));
                }
                else {
                    seats.remove(seatKey, seat);
                }
            }
        }
    }

    private Participant bringBack(SeatKey seatKey, Seat seat, Departure departure) {
        UUID roomId = seatKey.roomId();
        Participant participant = departure.participant();
        boolean broughtBack;
        try {
            broughtBack = roomService.bringBack(roomId, departure);
        }
        catch(RuntimeException exception) {
            log.warn("Couldn't bring {} back to the room {}: {}", participant.getNickname(), roomId, exception.getMessage());
            broughtBack = false;
        }
        if(!broughtBack) {
            // Someone else took the nickname meanwhile, or the room is gone
            synchronized(lock) {
                if(seats.remove(seatKey, seat)) {
                    release(seatKey, seat);
                }
            }
            log.info("{} left the room {} after {}", participant.getNickname(), roomId,
                    LeaveReason.PAGE_CLOSED.description());
            activity.left(LeaveReason.PAGE_CLOSED);
            throw notInRoom(roomId, participant.getNickname());
        }
        log.info("{} came back to the table in the room {}", participant.getNickname(), roomId);
        tellRoom(roomId, new RoomEvent(roomId, EventType.PARTICIPANT_ADDED, toDto(participant)));
        if(departure.vote() != null) {
            tellRoom(roomId, new RoomEvent(roomId, EventType.VOTE_ADDED, modelMapper.map(departure.vote(), VoteDto.class)));
        }
        // The cards waited for this person while they were away
        revealIfEveryoneVoted(roomId);
        return participant;
    }

    // Nobody came back to the seat of someone who stepped away, so they have left the room
    private void leaveForGood(SeatKey seatKey, Seat seat, int attempt) {
        Departure departure;
        synchronized(lock) {
            if(seats.get(seatKey) != seat || seat.leavingAttempt != attempt || seat.departure == null) {
                return;
            }
            departure = seat.departure;
            seats.remove(seatKey);
        }
        log.info("{} left the room {} after {}", departure.participant().getNickname(), seatKey.roomId(),
                LeaveReason.PAGE_CLOSED.description());
        activity.left(LeaveReason.PAGE_CLOSED);
        // The cards waited for this person, and the others may have all voted meanwhile
        revealIfEveryoneVoted(seatKey.roomId());
        try {
            roomService.removeIfEmpty(seatKey.roomId());
        }
        catch(RuntimeException exception) {
            log.warn("Couldn't delete the room {} after {} left: {}", seatKey.roomId(), seatKey.nickname(),
                    exception.getMessage());
        }
    }

    private void leave(SeatKey seatKey, Seat seat, int attempt, LeaveReason reason) {
        synchronized(lock) {
            if(seats.get(seatKey) != seat || seat.leavingAttempt != attempt || !seat.sessionIds.isEmpty()) {
                return;
            }
            seat.leaving = true;
        }
        UUID roomId = seatKey.roomId();
        try {
            roomService.removeParticipant(roomId, seatKey.nickname()).ifPresent(participant -> {
                log.info("{} left the room {} after {}", participant.getNickname(), roomId, reason.description());
                activity.left(reason);
                tellRoom(roomId, new RoomEvent(roomId, EventType.PARTICIPANT_REMOVED, toDto(participant)));
                // The others may have all voted, and only this person's vote was missing
                revealIfEveryoneVoted(roomId);
            });
        }
        catch(RuntimeException exception) {
            // The room is gone already, or the database is not available: there is nobody to tell
            log.warn("Couldn't remove {} from the room {}: {}", seatKey.nickname(), roomId, exception.getMessage());
        }
        finally {
            synchronized(lock) {
                seats.remove(seatKey, seat);
            }
        }
    }

    private void tellRoom(UUID roomId, RoomEvent event) {
        messagingTemplate.convertAndSend(PiPokerApplication.TOPIC_ROOM_DESTINATION_PREFIX + "." + roomId, event);
    }

    private ParticipantDto toDto(Participant participant) {
        return modelMapper.map(participant, ParticipantDto.class);
    }

    private static RoomServiceException notInRoom(UUID roomId, String nickname) {
        return new RoomServiceException(ErrorCode.PARTICIPANT_NOT_FOUND,
                String.format("Participant \"%s\" is not in the room \"%s\"", nickname, roomId));
    }

    private record SeatKey(UUID roomId, String nickname) {
    }

    private static final class Seat {

        private final Set<String> sessionIds = new HashSet<>();
        // The session whose connection closed last and left the seat empty
        private String closedSessionId;
        private ScheduledFuture<?> scheduledLeaving;
        private int leavingAttempt;
        // The participant is being taken away from the table or removed from the room
        private boolean leaving;
        // Who stepped away from the table with what vote, while they may come back
        private Departure departure;

        private void cancelLeaving() {
            if(scheduledLeaving != null) {
                scheduledLeaving.cancel(false);
                scheduledLeaving = null;
            }
            leavingAttempt++;
        }
    }
}
