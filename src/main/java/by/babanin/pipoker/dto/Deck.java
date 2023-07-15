package by.babanin.pipoker.dto;

import java.util.LinkedHashSet;
import java.util.StringJoiner;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@RequiredArgsConstructor
@EqualsAndHashCode
public class Deck {

    @NotEmpty
    @Size(min = 1, max = 20)
    private LinkedHashSet<Card> cards = new LinkedHashSet<>();

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner("; ", "[", "]");
        cards.forEach(card -> joiner.add(card.getValue()));
        return joiner.toString();
    }
}
