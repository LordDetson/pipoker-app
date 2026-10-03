package by.babanin.pipoker.repository;

import java.util.Optional;
import java.util.UUID;

import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Vote;

/**
 * Changes of a stored room, each made by one atomic MongoDB update of the room document.
 * Several people act in a room at the same time, so a change never reads the room and saves it back whole:
 * that would overwrite whatever someone else changed in between.
 */
public interface AtomicRoomRepository {

    /**
     * @return false when the room is missing or already has a participant with this nickname
     */
    boolean addParticipant(UUID roomId, Participant participant);

    /**
     * Removes the participant together with their vote.
     *
     * @return the removed participant, empty when the room is missing or has no such participant
     */
    Optional<Participant> removeParticipant(UUID roomId, String key);

    /**
     * Deletes the room only if nobody is left in it, so someone joining at the same moment keeps the room.
     */
    boolean removeIfEmpty(UUID roomId);

    /**
     * Stores the vote in place of the participant's previous one.
     *
     * @return false when the room is missing or the participant is not a voter in it
     */
    boolean addVote(UUID roomId, Vote vote);

    /**
     * @return the removed vote, empty when the room is missing or the participant hasn't voted
     */
    Optional<Vote> removeVote(UUID roomId, String key);

    /**
     * @return false when the room is missing
     */
    boolean clearVotes(UUID roomId);
}
