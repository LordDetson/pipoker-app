package by.babanin.pipoker.entity;

import java.util.Comparator;

import org.apache.commons.lang3.StringUtils;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Card {

    public static final Comparator<String> CARD_VALUE_COMPARATOR = new CardValueComparator();

    @NotBlank
    @Size(min = 1, max = 6)
    @ToString.Include
    private final String value;

    public Card(String value) {
        this.value = StringUtils.trimToNull(value);
    }

    @EqualsAndHashCode.Include
    public String normalizeValue() {
        return value.toLowerCase();
    }

    public static String normalizeValue(String value) {
        return StringUtils.toRootLowerCase(StringUtils.trimToNull(value));
    }

    private static final class CardValueComparator implements Comparator<String> {

        @Override
        public int compare(String value1, String value2) {
            return StringUtils.compare(
                    normalizeValue(value1),
                    normalizeValue(value2)
            );
        }
    }
}
