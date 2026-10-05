package by.babanin.pipoker.model;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * The estimate a page accepts for the round whose cards are revealed: the round as the room's history tells it,
 * and a card of the deck.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class EstimateDto {

    @NotNull
    private Instant revealedAt;

    @NotBlank
    private String card;
}
