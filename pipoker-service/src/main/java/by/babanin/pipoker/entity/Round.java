package by.babanin.pipoker.entity;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * A round of the room's history: the votes as they were when the cards were revealed.
 */
@Getter
@RequiredArgsConstructor
@EqualsAndHashCode
@ToString
public class Round {

    @NotNull
    private final Instant revealedAt;

    @NotNull
    private final List<Vote> votes;
}
