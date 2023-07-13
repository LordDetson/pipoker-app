package by.babanin.pipoker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class Card {

    @NotBlank
    @Size(min = 1, max = 6)
    @Pattern(regexp = "^[^;]*$")
    private String value;
}
