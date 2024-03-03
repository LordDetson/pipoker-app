package by.babanin.pipoker.entity;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import by.babanin.pipoker.exception.ConstraintException;
import by.babanin.pipoker.exception.VoteServiceException;
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
    @Getter
    private Deck deck;

    @NotNull
    private final Map<String, Participant> participantMap = new ConcurrentHashMap<>();

    @NotNull
    private final Map<String, Vote> voteMap = new ConcurrentHashMap<>();

    public Room(String name, Deck deck) {
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
        if(containsParticipant(participant.getNickname())) {
            throw new ConstraintException(String.format("Participant \"%s\" is already exist in the room \"%s\"", participant.getNickname(),
                    id));
        }
        participantMap.put(participant.normalizeNickname(), participant);
        return participant;
    }

    public Set<Participant> getParticipants() {
        return getParticipants(Participant::compareTo);
    }

    public Set<Participant> getParticipants(Comparator<Participant> comparator) {
        return Collections.unmodifiableSet((Set<? extends Participant>) participantMap.values().stream()
                .sorted(comparator)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    public Participant getParticipant(String nickname) {
        return findParticipant(nickname)
                .orElseThrow(() -> new ConstraintException(String.format("Participant \"%s\" is not found in the room \"%s\"", nickname, id)));
    }

    public Optional<Participant> findParticipant(String nickname) {
        return Optional.ofNullable(participantMap.get(Participant.normalizeNickname(nickname)));
    }

    public boolean containsParticipant(String nickname) {
        return findParticipant(nickname).isPresent();
    }

    public boolean haveParticipants() {
        return !participantMap.isEmpty();
    }

    public Optional<Participant> removeParticipant(String nickname) {
        removeVote(nickname);
        return Optional.ofNullable(participantMap.remove(Participant.normalizeNickname(nickname)));
    }

    public void clearParticipants() {
        clearVotes();
        participantMap.clear();
    }

    // Votes

    public Vote addVote(String nickname, String cardValue) {
        Participant participant = getParticipant(nickname);
        if(participant.isWatcher()) {
            throw new VoteServiceException(String.format("The participant \"%s\" is a watcher, so can't vote", nickname));
        }
        Card card = getDeck().get(cardValue);
        Vote vote = new Vote(participant, card);
        voteMap.put(participant.normalizeNickname(), vote);
        return vote;
    }

    public Set<Vote> getVotes() {
        return getVotes(Vote::compareTo);
    }

    public Set<Vote> getVotes(Comparator<Vote> comparator) {
        return Collections.unmodifiableSet((Set<? extends Vote>) voteMap.values().stream()
                .sorted(comparator)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    public Vote getVote(String nickname) {
        return findVote(nickname)
                .orElseThrow(() -> new VoteServiceException(String.format("Vote is not found for the room \"%s\" and the participant \"%s\"",
                        id, nickname)));
    }

    public Optional<Vote> findVote(String nickname) {
        return Optional.ofNullable(voteMap.get(Participant.normalizeNickname(nickname)));
    }

    public Optional<Vote> removeVote(String nickname) {
        return Optional.ofNullable(voteMap.remove(Participant.normalizeNickname(nickname)));
    }

    public void clearVotes() {
        voteMap.clear();
    }
}
