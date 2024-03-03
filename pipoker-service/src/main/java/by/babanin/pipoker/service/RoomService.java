package by.babanin.pipoker.service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import by.babanin.pipoker.entity.Deck;
import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.entity.Vote;
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
        return create(name, deck, Collections.emptyList());
    }

    public Room create(String name, Deck deck, List<Participant> participants) {
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

    public Participant addWatcher(UUID roomId, String nickname) {
        return addParticipant(roomId, nickname, true);
    }

    public Participant addParticipant(UUID roomId, String nickname) {
        return addParticipant(roomId, nickname, false);
    }

    private Participant addParticipant(UUID roomId, String nickname, boolean watcher) {
        Room room = get(roomId);
        Participant participant = watcher
                ? room.addWatcher(nickname)
                : room.addParticipant(nickname);
        AppUtils.validateAndThrow(validator, participant, RoomServiceException::new,
                () -> room.removeParticipant(nickname));
        AppUtils.validateAndThrow(validator, room, RoomServiceException::new,
                () -> room.removeParticipant(nickname));
        roomRepository.save(room);
        return participant;
    }

    public Optional<Participant> removeParticipant(UUID roomId, String nickname) {
        Room room = get(roomId);
        Optional<Participant> removed = room.removeParticipant(nickname);
        if(removed.isPresent()) {
            AppUtils.validateAndThrow(validator, room, RoomServiceException::new, () -> {
                if(removed.get().isWatcher()) {
                    room.addWatcher(nickname);
                }
                else {
                    room.addParticipant(nickname);
                }
            });
            if(allowRemoveRoomIfNotHaveParticipants && room.haveParticipants()) {
                remove(room.getId());
            }
            else {
                roomRepository.save(room);
            }
        }
        return removed;
    }

    // Votes

    public Vote addVote(UUID roomId, String nickname, String cardValue) {
        Room room = get(roomId);
        Vote vote = room.addVote(nickname, cardValue);
        AppUtils.validateAndThrow(validator, vote, RoomServiceException::new,
                () -> room.removeVote(nickname));
        roomRepository.save(room);
        return vote;
    }

    public Optional<Vote> removeVote(UUID roomId, String nickname) {
        Room room = get(roomId);
        Optional<Vote> removed = room.removeVote(nickname);
        removed.ifPresent(vote ->
                AppUtils.validateAndThrow(validator, room, RoomServiceException::new,
                        () -> room.addVote(nickname, vote.getCard().getValue())));
        roomRepository.save(room);
        return removed;
    }
}
