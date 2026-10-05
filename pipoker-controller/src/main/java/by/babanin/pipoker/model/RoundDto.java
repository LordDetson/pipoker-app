package by.babanin.pipoker.model;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoundDto {

    @NotNull
    private Instant revealedAt;

    // Sorted by nickname, like the votes of the room
    @NotNull
    private List<VoteDto> votes;

    // What the round estimated, left out when nobody named it
    private TaskDto task;

    // The accepted card, left out until someone accepts one
    private String estimate;

    public RoundDto(Instant revealedAt, List<VoteDto> votes) {
        this(revealedAt, votes, null, null);
    }
}
