package by.babanin.pipoker.controller;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import by.babanin.pipoker.dto.Card;
import by.babanin.pipoker.dto.CreateRoom;
import by.babanin.pipoker.dto.Participant;
import by.babanin.pipoker.dto.Room;
import by.babanin.pipoker.dto.RoomEvent;
import by.babanin.pipoker.dto.RoomEvent.RoomEventType;
import by.babanin.pipoker.dto.Vote;
import by.babanin.pipoker.service.RoomService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@Validated
@RequestMapping("/api/room")
@CrossOrigin(maxAge = 3600)
public class RoomController {

    private static final String ROOM_TOPIC_FORMAT = "/topic/room.%s";

    private final RoomService roomService;
    private final SimpMessageSendingOperations messagingTemplate;

    public RoomController(RoomService roomService, SimpMessageSendingOperations messagingTemplate) {
        this.roomService = roomService;
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping
    Room create(@RequestBody @Valid CreateRoom createRoom) {
        Participant participant = new Participant(createRoom.getNickname(), createRoom.isWatcher());
        return roomService.create(createRoom.getRoomName(), createRoom.getDeck(), participant);
    }

    @GetMapping("{id}")
    Room get(@PathVariable("id") @NotBlank String id) {
        return roomService.get(id);
    }

    @GetMapping("{id}/check-if-nickname-exist")
    boolean checkIfNicknameExist(
            @PathVariable("id") @NotBlank String id,
            @RequestParam("nickname") @NotBlank String nickname
    ) {
        return roomService.containsParticipant(id, nickname);
    }

    @PutMapping("{id}/add-participant")
    Participant addParticipant(
            @PathVariable("id") @NotBlank String id,
            @RequestBody @Valid Participant participant
    ) {
        participant = roomService.addParticipant(id, participant);
        messagingTemplate.convertAndSend(
                String.format(ROOM_TOPIC_FORMAT, id),
                RoomEvent.builder()
                        .type(RoomEventType.JOINED)
                        .participant(participant)
                        .build()
        );
        return participant;
    }

    @DeleteMapping("{id}/remove-participant")
    Participant removeParticipant(
            @PathVariable("id") @NotBlank String id,
            @RequestParam("nickname") @NotBlank String nickname
    ) {
        Participant participant = roomService.removeParticipant(id, nickname);
        messagingTemplate.convertAndSend(
                String.format(ROOM_TOPIC_FORMAT, id),
                RoomEvent.builder()
                        .type(RoomEventType.LEFT)
                        .participant(participant)
                        .build()
        );
        return participant;
    }

    @PostMapping("{id}/vote")
    Vote vote(
            @PathVariable("id") @NotBlank String id,
            @RequestBody @Valid Vote vote
    ) {
        Participant participant = vote.getParticipant();
        Card card = vote.getCard();
        roomService.vote(id, participant, card);
        messagingTemplate.convertAndSend(
                String.format(ROOM_TOPIC_FORMAT, id),
                RoomEvent.builder()
                        .type(RoomEventType.VOTED)
                        .participant(participant)
                        .card(card)
                        .build()
        );
        return vote;
    }

    @GetMapping("{id}/clearVotingResult")
    void clearVotingResult(@PathVariable("id") @NotBlank String id) {
        roomService.clearVotingResult(id);
        messagingTemplate.convertAndSend(
                String.format(ROOM_TOPIC_FORMAT, id),
                RoomEvent.builder()
                        .type(RoomEventType.VOTE_CLEARED)
                        .build()
        );
    }
}
