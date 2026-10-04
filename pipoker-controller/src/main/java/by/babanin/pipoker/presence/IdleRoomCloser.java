package by.babanin.pipoker.presence;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import by.babanin.pipoker.PiPokerApplication;
import by.babanin.pipoker.activity.LeaveReason;
import by.babanin.pipoker.activity.RoomActivity;
import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.event.RoomEvent;
import by.babanin.pipoker.event.RoomEvent.EventType;
import by.babanin.pipoker.service.RoomService;
import lombok.extern.log4j.Log4j2;

/**
 * Closes the rooms nobody has done anything in for the idle timeout, and everyone still in them leaves.
 * <p>
 * Only what people do on purpose keeps a room open (see {@link Room#getLastActivity()}). A page that merely stays
 * connected doesn't, so a room left open in a forgotten tab is closed too. The pages still in the room are told
 * that it is closed.
 */
@Component
@Log4j2
public class IdleRoomCloser {

    private final RoomService roomService;
    private final RoomPresence roomPresence;
    private final RoomActivity activity;
    private final SimpMessageSendingOperations messagingTemplate;
    private final TaskScheduler scheduler;
    private final Duration idleTimeout;
    private final Duration checkInterval;

    public IdleRoomCloser(RoomService roomService, RoomPresence roomPresence, RoomActivity activity,
            SimpMessageSendingOperations messagingTemplate, @Qualifier("roomPresenceScheduler") TaskScheduler scheduler,
            @Value("${room.idle-timeout:30m}") Duration idleTimeout,
            @Value("${room.idle-check-interval:1m}") Duration checkInterval) {
        this.roomService = roomService;
        this.roomPresence = roomPresence;
        this.activity = activity;
        this.messagingTemplate = messagingTemplate;
        this.scheduler = scheduler;
        this.idleTimeout = idleTimeout;
        this.checkInterval = checkInterval;
    }

    // A failed check is logged by the scheduler, and the next one runs as planned
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        roomService.markActiveIfUnknown();
        scheduler.scheduleWithFixedDelay(this::closeIdleRooms, Instant.now().plus(checkInterval), checkInterval);
    }

    void closeIdleRooms() {
        roomService.closeIdleRooms(idleTimeout).forEach(this::closed);
    }

    private void closed(Room room) {
        UUID roomId = room.getId();
        List<Participant> people = new ArrayList<>(room.getParticipants());
        people.addAll(roomPresence.roomClosed(roomId));
        people.forEach(participant -> activity.left(LeaveReason.ROOM_CLOSED));
        log.info("The room {} was closed after {} without activity, {} people left it", roomId, idleTimeout, people.size());
        messagingTemplate.convertAndSend(PiPokerApplication.TOPIC_ROOM_DESTINATION_PREFIX + "." + roomId,
                new RoomEvent(roomId, EventType.ROOM_CLOSED));
    }
}
