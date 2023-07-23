package by.babanin.pipoker.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import by.babanin.pipoker.dto.CreateRoom;
import by.babanin.pipoker.dto.Participant;
import by.babanin.pipoker.dto.Room;
import by.babanin.pipoker.dto.Vote;
import by.babanin.pipoker.service.RoomService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@Validated
@RequestMapping("/room")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
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
        return roomService.addParticipant(id, participant);
    }

    @DeleteMapping("{id}/remove-participant")
    Participant removeParticipant(
            @PathVariable("id") @NotBlank String id,
            @RequestParam("nickname") @NotBlank String nickname
    ) {
        return roomService.removeParticipant(id, nickname);
    }

    @PostMapping("{id}/vote")
    Vote vote(
            @PathVariable("id") @NotBlank String id,
            @RequestBody @Valid Vote vote
    ) {
        roomService.vote(id, vote.getParticipant(), vote.getCard());
        return vote;
    }

    @GetMapping("{id}/clearVotingResult")
    void clearVotingResult(@PathVariable("id") @NotBlank String id) {
        roomService.clearVotingResult(id);
    }
}
