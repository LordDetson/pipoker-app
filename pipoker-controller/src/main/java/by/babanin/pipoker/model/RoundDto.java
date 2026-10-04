package by.babanin.pipoker.model;

import java.time.Instant;
import java.util.List;

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
public class RoundDto {

    @NotNull
    private Instant revealedAt;

    // Sorted by nickname, like the votes of the room
    @NotNull
    private List<VoteDto> votes;
}
