package by.babanin.pipoker.bugreport;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * How far the round of the room got when the person reported a problem.
 */
public enum RoundStage {

    // The cards are hidden, people vote
    @JsonProperty("voting")
    VOTING,

    // The cards are on the table; the estimate may be accepted already
    @JsonProperty("revealed")
    REVEALED
}
