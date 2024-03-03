package by.babanin.pipoker.model;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.StringJoiner;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DeckDto {

    @NotEmpty
    @Size(min = 1, max = 20)
    @JsonDeserialize(as = LinkedHashSet.class)
    private final Set<String> cards = new LinkedHashSet<>();

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner("; ", "[", "]");
        cards.forEach(joiner::add);
        return joiner.toString();
    }
}
