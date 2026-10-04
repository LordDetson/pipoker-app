package by.babanin.pipoker.controller;

import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import by.babanin.pipoker.presence.RoomPresence;

@Controller
public class PresenceController {

    private final RoomPresence roomPresence;

    public PresenceController(RoomPresence roomPresence) {
        this.roomPresence = roomPresence;
    }

    // A page that is being closed says so right before its connection closes
    @MessageMapping("/presence/page-closed")
    void pageClosed(@Header(SimpMessageHeaderAccessor.SESSION_ID_HEADER) String sessionId) {
        roomPresence.pageClosed(sessionId);
    }
}
