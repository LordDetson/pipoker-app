package by.babanin.pipoker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateRoom {

    @NotBlank
    @Size(min = 2, max = 32)
    private String nickname;

    @NotBlank
    @Size(min = 2, max = 32)
    private String roomName;

    @NotNull
    private Deck deck;

    private boolean watcher;
}
