package by.babanin.pipoker.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.entity.Timer;
import by.babanin.pipoker.entity.Vote;

/**
 * Changes of a stored room, each made by one atomic MongoDB update of the room document.
 * Several people act in a room at the same time, so a change never reads the room and saves it back whole:
 * that would overwrite whatever someone else changed in between.
 * <p>
 * The changes someone makes on purpose also mark the room as active (see {@link Room#getLastActivity()}): joining,
 * voting, taking a vote back, revealing the cards, starting a new round and starting or stopping the timer. Leaving and coming back after a refresh
 * or a lost connection don't, so a room where open pages merely stay connected is still idle.
 */
public interface AtomicRoomRepository {

    /**
     * @return false when the room is missing or already has a participant with this nickname
     */
    boolean addParticipant(UUID roomId, Participant participant);

    /**
     * Adds the participant back together with the vote they had.
     *
     * @param vote null when they hadn't voted
     * @return false when the room is missing or already has a participant with this nickname
     */
    boolean returnParticipant(UUID roomId, Participant participant, Vote vote);

    /**
     * Removes the participant together with their vote.
     *
     * @return the room as it was before, with the participant and their vote, empty when the room is missing or has
     * no such participant
     */
    Optional<Room> removeParticipant(UUID roomId, String key);

    /**
     * Deletes the room only if nobody is left in it, so someone joining at the same moment keeps the room.
     */
    boolean removeIfEmpty(UUID roomId);

    /**
     * Deletes the room only if nobody has done anything in it since the given time, so someone acting in it at the same
     * moment keeps the room.
     *
     * @return the deleted room, empty when the room is missing or has been active since then
     */
    Optional<Room> removeIfIdle(UUID roomId, Instant idleSince);

    /**
     * Marks the rooms that don't know their last activity, stored before it was kept, as active at the given time.
     *
     * @return how many rooms were marked
     */
    long markActiveIfUnknown(Instant time);

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
     * Reveals the cards of the current round. The first time a round with votes is revealed, it enters the history
     * in the same update (see {@link Room#showVotes}), so two people revealing it at once record it once.
     *
     * @param revealedAt when the round enters the history
     * @return the room as it was before, empty when the room is missing
     */
    Optional<Room> showVotes(UUID roomId, Instant revealedAt);

    /**
     * Starts a new round: no votes, cards hidden, no timer.
     *
     * @return false when the room is missing
     */
    boolean clearVotes(UUID roomId);

    /**
     * Starts the discussion timer in place of the one that may be running.
     *
     * @return false when the room is missing
     */
    boolean startTimer(UUID roomId, Timer timer);

    /**
     * @return false when the room is missing
     */
    boolean stopTimer(UUID roomId);
}
