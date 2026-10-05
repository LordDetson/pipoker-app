package by.babanin.pipoker.entity;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import by.babanin.pipoker.exception.ConstraintException;
import by.babanin.pipoker.exception.ErrorCode;
import by.babanin.pipoker.exception.VoteServiceException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@Document("room")
public class Room {

    // The history keeps this many latest rounds, so a room that is used for months stays far below the document size limit
    public static final int HISTORY_LIMIT = 100;

    @EqualsAndHashCode.Include
    @ToString.Include
    @Getter
    @Id
    private UUID id;

    @NotBlank
    @Size(min = 2, max = 32)
    @ToString.Include
    @Getter
    @Setter
    private String name;

    @NotNull
    @Valid
    @Getter
    private Deck deck;

    // Stored as arrays, not as maps keyed by nickname: each change is one atomic update of these arrays
    // (see AtomicRoomRepository), and a nickname may contain characters that are not allowed in a field name, like a dot.
    @NotNull
    private List<@Valid Participant> participants = new CopyOnWriteArrayList<>();

    @NotNull
    private List<Vote> votes = new CopyOnWriteArrayList<>();

    // Whether the cards of this round are revealed, so people who join or reconnect later see them too
    @Getter
    private boolean votesShown;

    // The revealed rounds, oldest first. It lives as long as the room; rooms stored before it was kept start with none.
    @NotNull
    private List<Round> history = new CopyOnWriteArrayList<>();

    // When someone last did something in the room: created it, joined it, voted, revealed the cards or started a new
    // round. A room nobody acts in for long is closed (see RoomService#closeIdleRooms). Rooms stored before this field
    // existed have none until the backend marks them active when it starts.
    @Getter
    private Instant lastActivity;

    // The discussion timer, null when nobody has started one in this round
    @Getter
    private Timer timer;

    // What the current round estimates, null when nobody has named it
    @Getter
    private Task task;

    public Room(String name, Deck deck) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.deck = deck;
        this.lastActivity = Instant.now();
    }

    public void setDeck(Deck deck) {
        clearVotes();
        this.deck = deck;
    }

    // Participants

    public Participant addWatcher(String nickname) {
        return addParticipant(Participant.createWatcher(nickname));
    }

    public Participant addParticipant(String nickname) {
        return addParticipant(Participant.createParticipant(nickname));
    }

    private Participant addParticipant(Participant participant) {
        if(participant.normalizeNickname() == null) {
            throw new ConstraintException(ErrorCode.INVALID_DATA, "Nickname can't be blank");
        }
        if(containsParticipant(participant.getNickname())) {
            throw new ConstraintException(ErrorCode.NICKNAME_TAKEN,
                    String.format("Participant \"%s\" is already exist in the room \"%s\"", participant.getNickname(), id));
        }
        participants.add(participant);
        return participant;
    }

    public Set<Participant> getParticipants() {
        return getParticipants(Participant::compareTo);
    }

    public Set<Participant> getParticipants(Comparator<Participant> comparator) {
        return Collections.unmodifiableSet((Set<? extends Participant>) participants.stream()
                .sorted(comparator)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    public Participant getParticipant(String nickname) {
        return findParticipant(nickname)
                .orElseThrow(() -> new ConstraintException(ErrorCode.PARTICIPANT_NOT_FOUND,
                        String.format("Participant \"%s\" is not found in the room \"%s\"", nickname, id)));
    }

    public Optional<Participant> findParticipant(String nickname) {
        String key = Participant.normalizeNickname(nickname);
        return participants.stream()
                .filter(participant -> participant.getKey().equals(key))
                .findFirst();
    }

    public boolean containsParticipant(String nickname) {
        return findParticipant(nickname).isPresent();
    }

    public boolean haveParticipants() {
        return !participants.isEmpty();
    }

    public Optional<Participant> removeParticipant(String nickname) {
        removeVote(nickname);
        Optional<Participant> participant = findParticipant(nickname);
        participant.ifPresent(participants::remove);
        return participant;
    }

    /**
     * Makes the participant a watcher or a voter. A voter who becomes a watcher before the cards are revealed takes
     * their vote back, while a revealed vote stays with its round. A watcher who becomes a voter can vote in this round
     * until its cards are revealed.
     *
     * @return the vote taken back, empty when there was none to take back
     */
    public Optional<Vote> changeRole(String nickname, boolean watcher) {
        getParticipant(nickname).setWatcher(watcher);
        return watcher && !votesShown ? removeVote(nickname) : Optional.empty();
    }

    public void clearParticipants() {
        clearVotes();
        participants.clear();
    }

    // Votes

    public Vote addVote(String nickname, String cardValue) {
        Participant participant = getParticipant(nickname);
        if(participant.isWatcher()) {
            throw new VoteServiceException(ErrorCode.WATCHER_CANNOT_VOTE,
                    String.format("The participant \"%s\" is a watcher, so can't vote", nickname));
        }
        Card card = getDeck().get(cardValue);
        Vote vote = new Vote(participant, card);
        removeVote(nickname);
        votes.add(vote);
        return vote;
    }

    public Set<Vote> getVotes() {
        return getVotes(Vote::compareTo);
    }

    public Set<Vote> getVotes(Comparator<Vote> comparator) {
        return Collections.unmodifiableSet((Set<? extends Vote>) votes.stream()
                .sorted(comparator)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    public Vote getVote(String nickname) {
        return findVote(nickname)
                .orElseThrow(() -> new VoteServiceException(ErrorCode.UNEXPECTED,
                        String.format("Vote is not found for the room \"%s\" and the participant \"%s\"",
                        id, nickname)));
    }

    public Optional<Vote> findVote(String nickname) {
        String key = Participant.normalizeNickname(nickname);
        return votes.stream()
                .filter(vote -> vote.getParticipant().getKey().equals(key))
                .findFirst();
    }

    public Optional<Vote> removeVote(String nickname) {
        Optional<Vote> vote = findVote(nickname);
        vote.ifPresent(votes::remove);
        return vote;
    }

    // Task

    /**
     * Names what the current round estimates, in place of what it was. People name it while they vote,
     * so the revealed round keeps the task its votes were for.
     *
     * @param task null to estimate nothing named
     */
    public void setTask(Task task) {
        if(votesShown) {
            throw new ConstraintException(ErrorCode.CARDS_REVEALED,
                    "The cards are revealed, so the task can change in the next round");
        }
        this.task = task;
    }

    /**
     * Reveals the cards of the current round. The first time a round with votes is revealed, it enters the history
     * together with its task.
     *
     * @return the round that entered the history, empty when the cards were already revealed or nobody voted
     */
    public Optional<Round> showVotes(Instant revealedAt) {
        Optional<Round> round = votesShown || votes.isEmpty()
                ? Optional.empty()
                : Optional.of(new Round(revealedAt, List.copyOf(votes), task, null));
        round.ifPresent(added -> {
            history.add(added);
            if(history.size() > HISTORY_LIMIT) {
                history.remove(0);
            }
        });
        votesShown = true;
        // The discussion ends when the cards are revealed, so its timer stops
        timer = null;
        return round;
    }

    /**
     * Accepts the estimate the team agreed on for the revealed round, in place of the one accepted before.
     * Only the round on the table can get it: once a new round starts, the previous one is closed.
     *
     * @param revealedAt when the round was revealed, which tells it apart from the rounds before it
     * @return the round with the estimate
     */
    public Round acceptEstimate(Instant revealedAt, String cardValue) {
        Card card = deck.get(cardValue);
        if(!isRevealed(revealedAt)) {
            throw new ConstraintException(ErrorCode.ROUND_NOT_REVEALED,
                    String.format("The round revealed at %s is not on the table of the room \"%s\"", revealedAt, id));
        }
        Round accepted = history.getLast().withEstimate(card.getValue());
        history.set(history.size() - 1, accepted);
        return accepted;
    }

    private boolean isRevealed(Instant revealedAt) {
        return votesShown && !history.isEmpty() && history.getLast().getRevealedAt().equals(revealedAt);
    }

    public List<Round> getHistory() {
        return Collections.unmodifiableList(history);
    }

    /**
     * Starts a new round. It also ends the discussion of the previous one, so its timer stops too.
     * Once an estimate is accepted, the team goes on to the next task, so the new round starts without one;
     * otherwise the team votes on the same task again, and it stays.
     */
    public void clearVotes() {
        if(votesShown && !history.isEmpty() && history.getLast().getEstimate() != null) {
            task = null;
        }
        votes.clear();
        votesShown = false;
        timer = null;
    }
}
