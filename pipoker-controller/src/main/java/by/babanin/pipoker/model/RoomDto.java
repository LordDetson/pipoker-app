package by.babanin.pipoker.model;

import java.util.LinkedHashSet;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RoomDto {

    @EqualsAndHashCode.Include
    @ToString.Include
    private UUID id;

    @NotBlank
    @Size(min = 2, max = 32)
    @ToString.Include
    private String name;

    @NotNull
    private DeckDto deck;

    @NotNull
    @JsonDeserialize(as = LinkedHashSet.class)
    private final LinkedHashSet<ParticipantDto> participants = new LinkedHashSet<>();

    @NotNull
    @JsonDeserialize(as = LinkedHashSet.class)
    private final LinkedHashSet<VoteDto> votes = new LinkedHashSet<>();

    // The cards of the current round are revealed; left out of the JSON while they are hidden
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean votesShown;

    // The discussion timer, left out of the JSON when nobody has started one in this round
    private TimerDto timer;
}
