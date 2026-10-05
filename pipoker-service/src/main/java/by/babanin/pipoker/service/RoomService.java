package by.babanin.pipoker.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import by.babanin.pipoker.entity.Deck;
import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.entity.Round;
import by.babanin.pipoker.entity.Task;
import by.babanin.pipoker.entity.Timer;
import by.babanin.pipoker.entity.Vote;
import by.babanin.pipoker.exception.ConstraintException;
import by.babanin.pipoker.exception.ErrorCode;
import by.babanin.pipoker.exception.InvalidDataException;
import by.babanin.pipoker.exception.RoomNotFoundException;
import by.babanin.pipoker.exception.RoomServiceException;
import by.babanin.pipoker.repository.RoomRepository;
import by.babanin.pipoker.util.AppUtils;
import jakarta.validation.Validator;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final Validator validator;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${service.room.allowRemoveRoomIfNotHaveParticipants:true}")
    private boolean allowRemoveRoomIfNotHaveParticipants;

    public RoomService(RoomRepository roomRepository, Validator validator, ApplicationEventPublisher eventPublisher) {
        this.roomRepository = roomRepository;
        this.validator = validator;
        this.eventPublisher = eventPublisher;
    }

    // Rooms

    public Room create(String name, Deck deck) {
        return create(name, deck, Collections.emptySet());
    }

    public Room create(String name, Deck deck, Set<Participant> participants) {
        return create(name, deck, participants, false);
    }

    /**
     * @param autoReveal whether the cards are revealed by themselves once everyone has voted
     */
    public Room create(String name, Deck deck, Set<Participant> participants, boolean autoReveal) {
        if(deck == null) {
            throw new InvalidDataException("Deck can't be null");
        }
        AppUtils.validateAndThrow(validator, deck, InvalidDataException::new);
        Room room = new Room(name, deck);
        room.setAutoReveal(autoReveal);
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
        AppUtils.validateAndThrow(validator, room, InvalidDataException::new);
        return roomRepository.save(room);
    }

    public Room get(UUID id) {
        return find(id).orElseThrow(() -> notFound(id));
    }

    public Optional<Room> find(UUID id) {
        return roomRepository.findById(id);
    }

    public List<Room> getAll() {
        return roomRepository.findAll();
    }

    public long count() {
        return roomRepository.count();
    }

    public Optional<Room> remove(UUID id) {
        Optional<Room> room = find(id);
        room.ifPresent(roomRepository::delete);
        return room;
    }

    /**
     * Closes the rooms nobody has done anything in for the given time: deletes them together with everyone still in them.
     * A room someone acts in at the same moment is kept.
     *
     * @return the closed rooms as they were, with the people who were in them
     */
    public List<Room> closeIdleRooms(Duration idleTimeout) {
        Instant idleSince = Instant.now().minus(idleTimeout);
        return roomRepository.findByLastActivityBefore(idleSince).stream()
                .flatMap(room -> roomRepository.removeIfIdle(room.getId(), idleSince).stream())
                .toList();
    }

    /**
     * Rooms stored before their last activity was kept count as active from now on, so they aren't closed at once.
     */
    public void markActiveIfUnknown() {
        roomRepository.markActiveIfUnknown(Instant.now());
    }

    // Participants
    // Each change below is one atomic update of the stored room (see AtomicRoomRepository), so people acting at the same
    // moment don't overwrite each other's changes. An update that changed nothing doesn't tell whether the room
    // is missing, so that is checked afterwards: a missing room is reported as before.

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
            throw new ConstraintException(ErrorCode.INVALID_DATA, "Nickname can't be blank");
        }
        AppUtils.validateAndThrow(validator, participant, InvalidDataException::new);
        if(!roomRepository.addParticipant(roomId, participant)) {
            checkExists(roomId);
            throw new ConstraintException(ErrorCode.NICKNAME_TAKEN,
                    String.format("Participant \"%s\" is already exist in the room \"%s\"", participant.getNickname(), roomId));
        }
        return participant;
    }

    public Optional<Participant> removeParticipant(UUID roomId, String nickname) {
        Optional<Participant> removed = stepAway(roomId, nickname).map(Departure::participant);
        if(removed.isPresent()) {
            removeIfEmpty(roomId);
        }
        return removed;
    }

    /**
     * Takes the participant away from the table together with their vote, but keeps the room even if nobody is left
     * in it: the participant may come back in a moment with {@link #bringBack}.
     *
     * @return the participant and their vote, empty when the room has no such participant
     */
    public Optional<Departure> stepAway(UUID roomId, String nickname) {
        String key = Participant.normalizeNickname(nickname);
        Optional<Room> before = roomRepository.removeParticipant(roomId, key);
        if(before.isEmpty()) {
            checkExists(roomId);
        }
        return before.flatMap(room -> room.findParticipant(key)
                .map(participant -> new Departure(participant, room.findVote(key).orElse(null))));
    }

    /**
     * Brings the participant back to the table with the vote they had when they stepped away.
     *
     * @return false when the room is missing or someone else took the nickname meanwhile
     */
    public boolean bringBack(UUID roomId, Departure departure) {
        return roomRepository.returnParticipant(roomId, departure.participant(), departure.vote());
    }

    /**
     * Makes the participant a watcher or a voter. A voter who becomes a watcher before the cards are revealed takes
     * their vote back.
     */
    public RoleChange changeRole(UUID roomId, String nickname, boolean watcher) {
        String key = Participant.normalizeNickname(nickname);
        Room before = roomRepository.changeRole(roomId, key, watcher).orElseThrow(() -> {
            checkExists(roomId);
            return new ConstraintException(ErrorCode.PARTICIPANT_NOT_FOUND,
                    String.format("Participant \"%s\" is not found in the room \"%s\"", nickname, roomId));
        });
        // The room as it was makes the same decision the update made
        Vote takenBack = before.changeRole(key, watcher).orElse(null);
        return new RoleChange(before.getParticipant(key), takenBack);
    }

    /**
     * Deletes the room if nobody is left in it, so someone joining at the same moment keeps the room.
     * A deleted room is announced with {@link RoomRemovedEvent}.
     */
    public void removeIfEmpty(UUID roomId) {
        if(allowRemoveRoomIfNotHaveParticipants && roomRepository.removeIfEmpty(roomId)) {
            eventPublisher.publishEvent(new RoomRemovedEvent(roomId));
        }
    }

    // Votes

    public Vote addVote(UUID roomId, String nickname, String cardValue) {
        // The room explains a refused vote: unknown participant, a watcher or a card outside the deck
        Vote vote = get(roomId).addVote(nickname, cardValue);
        AppUtils.validateAndThrow(validator, vote, InvalidDataException::new);
        if(!roomRepository.addVote(roomId, vote)) {
            // The participant left or the room was deleted after it was read
            get(roomId).getParticipant(nickname);
            throw new RoomServiceException(ErrorCode.UNEXPECTED,
                    String.format("Vote of \"%s\" can't be stored in the room \"%s\"", nickname, roomId));
        }
        return vote;
    }

    public Optional<Vote> removeVote(UUID roomId, String nickname) {
        Optional<Vote> removed = roomRepository.removeVote(roomId, Participant.normalizeNickname(nickname));
        if(removed.isEmpty()) {
            checkExists(roomId);
        }
        return removed;
    }

    // These updates find the room by its id alone, so finding nothing means the room is missing

    /**
     * Reveals the cards of the current round and stops its timer.
     *
     * @return the round that entered the room's history, empty when the cards were already revealed or nobody voted
     */
    public Optional<Round> showVotes(UUID roomId) {
        // MongoDB keeps time in milliseconds, so the round told to the pages is the same as the stored one
        Instant revealedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Room before = roomRepository.showVotes(roomId, revealedAt).orElseThrow(() -> notFound(roomId));
        // The room as it was makes the same decision the update made
        return before.showVotes(revealedAt);
    }

    /**
     * Reveals the cards of the current round, like {@link #showVotes} does, if the room reveals them by itself and
     * every voter at the table has voted (see {@link Room#everyoneVoted}).
     *
     * @return the round that entered the room's history, empty when the cards stay as they are
     */
    public Optional<Round> showVotesIfEveryoneVoted(UUID roomId) {
        Instant revealedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        // Only an update that revealed the cards finds the room, and the cards it revealed had votes
        return roomRepository.showVotesIfEveryoneVoted(roomId, revealedAt)
                .flatMap(before -> before.showVotes(revealedAt));
    }

    /**
     * Turns on or off revealing the cards by themselves once everyone has voted. Turning it on doesn't reveal
     * the cards by itself: see {@link #showVotesIfEveryoneVoted}.
     */
    public void setAutoReveal(UUID roomId, boolean autoReveal) {
        if(!roomRepository.setAutoReveal(roomId, autoReveal)) {
            throw notFound(roomId);
        }
    }

    /**
     * Starts a new round: no votes, cards hidden, no timer.
     *
     * @return the task of the new round: the same one when the team votes on it again, null when the previous round
     * got its estimate or had no task
     */
    public Task clearVotes(UUID roomId) {
        return roomRepository.clearVotes(roomId).orElseThrow(() -> notFound(roomId)).getTask();
    }

    // Task and estimate

    /**
     * Names what the current round estimates. Anyone in the room can change it while the cards are hidden.
     *
     * @param name blank to estimate nothing named
     * @param url blank when the task has no link
     * @return the task, null when the name is blank
     */
    public Task setTask(UUID roomId, String name, String url) {
        Task task = Task.of(name, url);
        if(task != null) {
            AppUtils.validateAndThrow(validator, task, InvalidDataException::new);
        }
        if(!roomRepository.setTask(roomId, task)) {
            checkExists(roomId);
            throw new RoomServiceException(ErrorCode.CARDS_REVEALED,
                    "The cards are revealed, so the task can change in the next round");
        }
        return task;
    }

    /**
     * Accepts the estimate the team agreed on for the round whose cards are revealed now: any card of the deck.
     *
     * @param revealedAt when the round was revealed, as the room's history tells it
     * @return the round with the estimate
     */
    public Round acceptEstimate(UUID roomId, Instant revealedAt, String cardValue) {
        // The deck explains a card it doesn't have, and gives the card as the deck writes it
        String estimate = get(roomId).getDeck().get(cardValue).getValue();
        return roomRepository.acceptEstimate(roomId, revealedAt, estimate).orElseThrow(() -> {
            checkExists(roomId);
            return new RoomServiceException(ErrorCode.ROUND_NOT_REVEALED,
                    String.format("The round revealed at %s is not on the table of the room \"%s\"", revealedAt, roomId));
        });
    }

    // Timer

    /**
     * Starts the discussion timer of the room for the given time, in place of the one that may be running.
     * A round whose cards are revealed has nothing left to discuss, so its timer doesn't start.
     *
     * @return the started timer
     */
    public Timer startTimer(UUID roomId, Duration duration) {
        if(duration.compareTo(Timer.MIN_DURATION) < 0 || duration.compareTo(Timer.MAX_DURATION) > 0) {
            throw new InvalidDataException(String.format("The timer can run from %d seconds to %d minutes",
                    Timer.MIN_DURATION.toSeconds(), Timer.MAX_DURATION.toMinutes()));
        }
        Timer timer = Timer.start(duration);
        if(!roomRepository.startTimer(roomId, timer)) {
            checkExists(roomId);
            throw new RoomServiceException(ErrorCode.CARDS_REVEALED,
                    "The cards are revealed, so the timer can start in the next round");
        }
        return timer;
    }

    public void stopTimer(UUID roomId) {
        if(!roomRepository.stopTimer(roomId)) {
            throw notFound(roomId);
        }
    }

    private void checkExists(UUID roomId) {
        if(!roomRepository.existsById(roomId)) {
            throw notFound(roomId);
        }
    }

    private static RoomNotFoundException notFound(UUID roomId) {
        return new RoomNotFoundException(String.format("Room \"%s\" is not found", roomId));
    }
}
