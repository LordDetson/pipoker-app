package by.babanin.pipoker.service;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import by.babanin.pipoker.entity.Deck;
import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.entity.Vote;
import by.babanin.pipoker.exception.ConstraintException;
import by.babanin.pipoker.exception.RoomServiceException;
import by.babanin.pipoker.repository.RoomRepository;
import by.babanin.pipoker.util.AppUtils;
import jakarta.validation.Validator;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final Validator validator;

    @Value("${service.room.allowRemoveRoomIfNotHaveParticipants:true}")
    private boolean allowRemoveRoomIfNotHaveParticipants;

    public RoomService(RoomRepository roomRepository, Validator validator) {
        this.roomRepository = roomRepository;
        this.validator = validator;
    }

    // Rooms

    public Room create(String name, Deck deck) {
        return create(name, deck, Collections.emptySet());
    }

    public Room create(String name, Deck deck, Set<Participant> participants) {
        if(deck == null) {
            throw new RoomServiceException("Deck can't be null");
        }
        AppUtils.validateAndThrow(validator, deck, RoomServiceException::new);
        Room room = new Room(name, deck);
        if(CollectionUtils.isNotEmpty(participants)) {
            participants.forEach(participant -> {
                if(participant.isWatcher()) {
                    room.addWatcher(participant.getNickname());
                }
                else {
                    room.addParticipant(participant.getNickname());
                }
            });
        }
        AppUtils.validateAndThrow(validator, room, RoomServiceException::new);
        return roomRepository.save(room);
    }

    public Room get(UUID id) {
        return find(id).orElseThrow(() -> new RoomServiceException(String.format("Room \"%s\" is not found", id)));
    }

    public Optional<Room> find(UUID id) {
        return roomRepository.findById(id);
    }

    public Optional<Room> remove(UUID id) {
        Optional<Room> room = find(id);
        room.ifPresent(roomRepository::delete);
        return room;
    }

    // Participants
    // Each change below is one atomic update of the stored room (see RoomChanges), so people acting at the same
    // moment don't overwrite each other's changes. The room is read only to explain why a change was refused.

    public Participant addWatcher(UUID roomId, String nickname) {
        return addParticipant(roomId, nickname, true);
    }

    public Participant addParticipant(UUID roomId, String nickname) {
        return addParticipant(roomId, nickname, false);
    }

    private Participant addParticipant(UUID roomId, String nickname, boolean watcher) {
        Participant participant = watcher
                ? Participant.createWatcher(nickname)
                : Participant.createParticipant(nickname);
        if(participant.getKey() == null) {
            throw new ConstraintException("Nickname can't be blank");
        }
        AppUtils.validateAndThrow(validator, participant, RoomServiceException::new);
        if(!roomRepository.addParticipant(roomId, participant)) {
            Room room = get(roomId);
            throw new ConstraintException(String.format("Participant \"%s\" is already exist in the room \"%s\"",
                    participant.getNickname(), room.getId()));
        }
        return participant;
    }

    public Optional<Participant> removeParticipant(UUID roomId, String nickname) {
        Optional<Participant> removed = roomRepository.removeParticipant(roomId, Participant.normalizeNickname(nickname));
        if(removed.isEmpty()) {
            get(roomId);
        }
        else if(allowRemoveRoomIfNotHaveParticipants) {
            roomRepository.removeIfEmpty(roomId);
        }
        return removed;
    }

    // Votes

    public Vote addVote(UUID roomId, String nickname, String cardValue) {
        // The room explains a refused vote: unknown participant, a watcher or a card outside the deck
        Vote vote = get(roomId).addVote(nickname, cardValue);
        AppUtils.validateAndThrow(validator, vote, RoomServiceException::new);
        if(!roomRepository.addVote(roomId, vote)) {
            // The participant left or the room was deleted after it was read
            get(roomId).getParticipant(nickname);
            throw new RoomServiceException(String.format("Vote of \"%s\" can't be stored in the room \"%s\"", nickname, roomId));
        }
        return vote;
    }

    public Optional<Vote> removeVote(UUID roomId, String nickname) {
        Optional<Vote> removed = roomRepository.removeVote(roomId, Participant.normalizeNickname(nickname));
        if(removed.isEmpty()) {
            get(roomId);
        }
        return removed;
    }

    public void clearVotes(UUID roomId) {
        if(!roomRepository.clearVotes(roomId)) {
            get(roomId);
        }
    }
}
