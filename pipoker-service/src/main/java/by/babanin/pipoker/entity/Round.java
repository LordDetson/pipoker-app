package by.babanin.pipoker.entity;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.PersistenceCreator;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * A round of the room's history: the votes as they were when the cards were revealed, the task they estimated and
 * the estimate the team accepted after the discussion.
 */
@Getter
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
@EqualsAndHashCode
@ToString
public class Round {

    @NotNull
    private final Instant revealedAt;

    @NotNull
    private final List<Vote> votes;

    // Null when nobody named the task of the round
    private final Task task;

    // The value of the accepted card, null until someone accepts one; rounds stored before it was kept have none
    private final String estimate;

    public Round(Instant revealedAt, List<Vote> votes) {
        this(revealedAt, votes, null, null);
    }

    public Round withEstimate(String estimate) {
        return new Round(revealedAt, votes, task, estimate);
    }
}
