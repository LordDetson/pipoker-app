package by.babanin.pipoker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class Participant {

    @NotBlank
    @Size(min = 2, max = 32)
    private final String nickname;

    private final boolean watcher;
}
