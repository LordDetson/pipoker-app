package by.babanin.pipoker.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class CardTest {

    private static Validator validator;

    @BeforeAll
    static void setup() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("Card value is trimmed")
    void trimValue() {
        assertEquals("1h", new Card("  1h ").getValue());
    }

    @Test
    @DisplayName("Blank card value becomes null")
    void blankValue() {
        assertNull(new Card("   ").getValue());
    }

    @Test
    @DisplayName("Cards are equal regardless of case")
    void equalsIgnoringCase() {
        Card lower = new Card("xl");
        Card upper = new Card("XL");

        assertAll(
                () -> assertEquals(lower, upper),
                () -> assertEquals(lower.hashCode(), upper.hashCode()),
                () -> assertNotEquals(lower, new Card("xs"))
        );
    }

    @Test
    @DisplayName("Card value normalization")
    void normalizeValue() {
        assertAll(
                () -> assertEquals("xl", new Card("XL").normalizeValue()),
                () -> assertEquals("xl", Card.normalizeValue(" Xl ")),
                () -> assertNull(Card.normalizeValue(" ")),
                () -> assertNull(Card.normalizeValue(null))
        );
    }

    @Test
    @DisplayName("Card values are compared ignoring case and spaces")
    void compareValues() {
        assertAll(
                () -> assertEquals(0, Card.CARD_VALUE_COMPARATOR.compare("XL", " xl ")),
                () -> assertTrue(Card.CARD_VALUE_COMPARATOR.compare("1", "2") < 0),
                () -> assertTrue(Card.CARD_VALUE_COMPARATOR.compare(null, "1") < 0)
        );
    }

    @Test
    @DisplayName("Card validation")
    void validate() {
        assertAll(
                () -> assertTrue(validator.validate(new Card("1")).isEmpty()),
                () -> assertTrue(validator.validate(new Card("123456")).isEmpty()),
                () -> assertFalse(validator.validate(new Card("1234567")).isEmpty()),
                () -> assertFalse(validator.validate(new Card(" ")).isEmpty()),
                () -> assertFalse(validator.validate(new Card(null)).isEmpty())
        );
    }
}
