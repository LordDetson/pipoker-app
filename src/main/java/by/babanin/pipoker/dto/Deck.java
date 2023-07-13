package by.babanin.pipoker.dto;

import java.util.LinkedHashSet;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class Deck {

    @NotEmpty
    @Size(min = 1, max = 20)
    private LinkedHashSet<Card> cards = new LinkedHashSet<>();
}
