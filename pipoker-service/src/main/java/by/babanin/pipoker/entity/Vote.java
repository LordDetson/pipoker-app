package by.babanin.pipoker.entity;

import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@RequiredArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class Vote implements Comparable<Vote> {

    @NotNull
    @EqualsAndHashCode.Include
    private final Participant participant;

    @NotNull
    private final Card card;

    @Override
    public int compareTo(Vote vote) {
        return participant.compareTo(vote.participant);
    }
}
