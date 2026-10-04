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
            throw new ConstraintException("Nickname can't be blank");
        }
        if(containsParticipant(participant.getNickname())) {
            throw new ConstraintException(String.format("Participant \"%s\" is already exist in the room \"%s\"", participant.getNickname(),
                    id));
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
                .orElseThrow(() -> new ConstraintException(String.format("Participant \"%s\" is not found in the room \"%s\"", nickname, id)));
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

    public void clearParticipants() {
        clearVotes();
        participants.clear();
    }

    // Votes

    public Vote addVote(String nickname, String cardValue) {
        Participant participant = getParticipant(nickname);
        if(participant.isWatcher()) {
            throw new VoteServiceException(String.format("The participant \"%s\" is a watcher, so can't vote", nickname));
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
                .orElseThrow(() -> new VoteServiceException(String.format("Vote is not found for the room \"%s\" and the participant \"%s\"",
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

    /**
     * Reveals the cards of the current round. The first time a round with votes is revealed, it enters the history.
     *
     * @return the round that entered the history, empty when the cards were already revealed or nobody voted
     */
    public Optional<Round> showVotes(Instant revealedAt) {
        Optional<Round> round = votesShown || votes.isEmpty()
                ? Optional.empty()
                : Optional.of(new Round(revealedAt, List.copyOf(votes)));
        round.ifPresent(added -> {
            history.add(added);
            if(history.size() > HISTORY_LIMIT) {
                history.remove(0);
            }
        });
        votesShown = true;
        return round;
    }

    public List<Round> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public void clearVotes() {
        votes.clear();
        votesShown = false;
    }
}
