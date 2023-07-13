package by.babanin.pipoker.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import by.babanin.pipoker.dto.CreateRoom;
import by.babanin.pipoker.dto.Participant;
import by.babanin.pipoker.dto.Room;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@Validated
@RequestMapping("/room")
public class RoomController {

    private final Map<String, Room> rooms = new HashMap<>();

    @PostMapping
    Room create(@RequestBody @Valid CreateRoom createRoom) {
        Participant participant = new Participant(createRoom.getNickname(), createRoom.isWatcher());
        String id = UUID.randomUUID().toString();
        Room room = new Room(id, createRoom.getRoomName(), createRoom.getDeck());
        room.getParticipants().add(participant);
        rooms.put(id, room);
        return room;
    }

    @GetMapping("{id}")
    Room get(@PathVariable("id") @NotBlank String id) {
        return rooms.get(id);
    }
}
