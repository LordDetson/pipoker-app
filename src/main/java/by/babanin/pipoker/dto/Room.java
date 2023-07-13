package by.babanin.pipoker.dto;

import java.util.LinkedHashSet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class Room {

    @NotNull
    private final String id;

    @NotBlank
    @Size(min = 2, max = 32)
    private final String name;

    @NotNull
    private final Deck deck;

    @NotEmpty
    @Size(min = 1, max = 10)
    private final LinkedHashSet<Participant> participants = new LinkedHashSet<>();

    private final VotingResult votingResult = new VotingResult();
}
