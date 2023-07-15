package by.babanin.pipoker.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import by.babanin.pipoker.dto.Deck;
import by.babanin.pipoker.dto.Participant;
import by.babanin.pipoker.dto.Room;

@Service
public class RoomService {

    private final Map<String, Room> rooms = new HashMap<>();

    public Room create(String name, Deck deck, Participant participant) {
        String id = generateUUID();
        Room room = new Room(id, name, deck);
        room.getParticipants().add(participant);
        rooms.put(id, room);
        return room;
    }

    private String generateUUID() {
        String id;
        do {
            id = UUID.randomUUID().toString();
        }
        while(find(id).isPresent());
        return id;
    }

    public Room get(String id) {
        return find(id)
                .orElseThrow(() -> new RoomException(id + " room is not found"));
    }

    public Optional<Room> find(String id) {
        return Optional.ofNullable(rooms.get(id));
    }
}
