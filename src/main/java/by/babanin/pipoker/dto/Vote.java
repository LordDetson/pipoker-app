package by.babanin.pipoker.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class Vote {

    @NotNull
    private Participant participant;

    @NotNull
    private Card card;
}
