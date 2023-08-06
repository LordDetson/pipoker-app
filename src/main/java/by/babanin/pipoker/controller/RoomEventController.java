package by.babanin.pipoker.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import by.babanin.pipoker.dto.RoomEvent;
import by.babanin.pipoker.dto.RoomEvent.RoomEventType;

@Controller
public class RoomEventController {

    @MessageMapping("/room/{id}/showVotingResult")
    @SendTo("/topic/room.{id}")
    RoomEvent showVotingResult() {
        return RoomEvent.builder()
                .type(RoomEventType.SHOW_VOTING_RESULT)
                .build();
    }
}
