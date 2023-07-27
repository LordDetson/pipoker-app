package by.babanin.pipoker.service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import by.babanin.pipoker.dto.Card;
import by.babanin.pipoker.dto.Deck;
import by.babanin.pipoker.dto.Participant;
import by.babanin.pipoker.dto.Room;
import by.babanin.pipoker.dto.VotingResult;

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

    public Room remove(String id) {
        return rooms.remove(id);
    }

    public Participant addParticipant(String roomId, Participant participant) {
        return addParticipant(get(roomId), participant);
    }

    public Participant addParticipant(Room room, Participant participant) {
        if(!room.getParticipants().add(participant)) {
            throw new RoomException(participant.getNickname() + " participant is already exist");
        }
        return participant;
    }

    public Participant getParticipant(Room room, String nickname) {
        return findParticipant(room, nickname)
                .orElseThrow(() -> new RoomException(nickname + " participant is not found"));
    }

    public boolean containsParticipant(String roomId, String nickname) {
        return containsParticipant(get(roomId), nickname);
    }

    public boolean containsParticipant(Room room, String nickname) {
        return findParticipant(room, nickname).isPresent();
    }

    public Optional<Participant> findParticipant(Room room, String nickname) {
        return room.getParticipants().stream()
                .filter(participant -> participant.getNickname().equalsIgnoreCase(nickname))
                .findFirst();
    }

    public Participant removeParticipant(String roomId, String nickname) {
        Room room = get(roomId);
        return removeParticipant(room, getParticipant(room, nickname));
    }

    public Participant removeParticipant(String roomId, Participant participant) {
        return removeParticipant(get(roomId), participant);
    }

    public Participant removeParticipant(Room room, Participant participant) {
        LinkedHashSet<Participant> participants = room.getParticipants();
        if(!participants.remove(participant)) {
            throw new RoomException(participant.getNickname() + " participant is not found");
        }
        if(participants.isEmpty()) {
            remove(room.getId());
        }
        return participant;
    }

    public VotingResult vote(String id, Participant participant, Card card) {
        VotingResult votingResult = get(id).getVotingResult();
        votingResult.getMap().put(participant.getNickname(), card);
        return votingResult;
    }

    public void clearVotingResult(String id) {
        get(id).getVotingResult().getMap().clear();
    }
}
