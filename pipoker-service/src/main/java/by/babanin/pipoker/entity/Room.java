package by.babanin.pipoker.entity;

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
import org.springframework.data.mongodb.core.mapping.Field;

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
@Document(Room.COLLECTION)
public class Room {

    public static final String COLLECTION = "room";
    public static final String PARTICIPANTS = "participants";
    public static final String VOTES = "votes";
    public static final String VOTES_SHOWN = "votesShown";

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
    // (see RoomChanges), and a nickname may contain characters that are not allowed in a field name, like a dot.
    @NotNull
    @Field(PARTICIPANTS)
    private List<@Valid Participant> participantList = new CopyOnWriteArrayList<>();

    @NotNull
    @Field(VOTES)
    private List<Vote> voteList = new CopyOnWriteArrayList<>();

    // Whether the cards of this round are revealed, so people who join or reconnect later see them too
    @Getter
    @Field(VOTES_SHOWN)
    private boolean votesShown;

    public Room(String name, Deck deck) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.deck = deck;
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
        participantList.add(participant);
        return participant;
    }

    public Set<Participant> getParticipants() {
        return getParticipants(Participant::compareTo);
    }

    public Set<Participant> getParticipants(Comparator<Participant> comparator) {
        return Collections.unmodifiableSet((Set<? extends Participant>) participantList.stream()
                .sorted(comparator)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    public Participant getParticipant(String nickname) {
        return findParticipant(nickname)
                .orElseThrow(() -> new ConstraintException(String.format("Participant \"%s\" is not found in the room \"%s\"", nickname, id)));
    }

    public Optional<Participant> findParticipant(String nickname) {
        String key = Participant.normalizeNickname(nickname);
        return participantList.stream()
                .filter(participant -> participant.getKey().equals(key))
                .findFirst();
    }

    public boolean containsParticipant(String nickname) {
        return findParticipant(nickname).isPresent();
    }

    public boolean haveParticipants() {
        return !participantList.isEmpty();
    }

    public Optional<Participant> removeParticipant(String nickname) {
        removeVote(nickname);
        Optional<Participant> participant = findParticipant(nickname);
        participant.ifPresent(participantList::remove);
        return participant;
    }

    public void clearParticipants() {
        clearVotes();
        participantList.clear();
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
        voteList.add(vote);
        return vote;
    }

    public Set<Vote> getVotes() {
        return getVotes(Vote::compareTo);
    }

    public Set<Vote> getVotes(Comparator<Vote> comparator) {
        return Collections.unmodifiableSet((Set<? extends Vote>) voteList.stream()
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
        return voteList.stream()
                .filter(vote -> vote.getParticipant().getKey().equals(key))
                .findFirst();
    }

    public Optional<Vote> removeVote(String nickname) {
        Optional<Vote> vote = findVote(nickname);
        vote.ifPresent(voteList::remove);
        return vote;
    }

    public void showVotes() {
        votesShown = true;
    }

    public void clearVotes() {
        voteList.clear();
        votesShown = false;
    }
}
