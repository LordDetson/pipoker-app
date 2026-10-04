package by.babanin.pipoker.service;

import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Vote;

/**
 * A participant who stepped away from the table, with the vote they had.
 *
 * @param vote null when they hadn't voted
 */
public record Departure(Participant participant, Vote vote) {

    /**
     * The same participant without a vote, as after a new round started.
     */
    public Departure withoutVote() {
        return new Departure(participant, null);
    }
}
