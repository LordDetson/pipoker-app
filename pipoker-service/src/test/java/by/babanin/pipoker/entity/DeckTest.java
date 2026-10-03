package by.babanin.pipoker.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import by.babanin.pipoker.exception.ConstraintException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class DeckTest {

    private static Validator validator;

    @BeforeAll
    static void setup() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("Cards are kept in the order they were added")
    void addCards() {
        // Given
        Deck deck = new Deck();

        // When
        boolean first = deck.add("1");
        boolean second = deck.add("2");
        boolean inserted = deck.add(0, "0");

        // Then
        assertAll(
                () -> assertTrue(first),
                () -> assertTrue(second),
                () -> assertTrue(inserted),
                () -> assertEquals(List.of(new Card("0"), new Card("1"), new Card("2")), deck.get()),
                () -> assertEquals(3, deck.size()),
                () -> assertFalse(deck.isEmpty())
        );
    }

    @Test
    @DisplayName("Duplicate cards are ignored regardless of case")
    void addDuplicate() {
        // Given
        Deck deck = new Deck();
        deck.add("XL");

        // When
        boolean added = deck.add("xl");
        boolean inserted = deck.add(0, " Xl ");

        // Then
        assertAll(
                () -> assertFalse(added),
                () -> assertFalse(inserted),
                () -> assertEquals(1, deck.size())
        );
    }

    @Test
    @DisplayName("Deck can't be changed from outside")
    void unmodifiableCards() {
        Deck deck = new Deck();
        deck.add("1");

        List<Card> cards = deck.get();

        assertThrows(UnsupportedOperationException.class, () -> cards.add(new Card("2")));
    }

    @Test
    @DisplayName("Card lookup by value")
    void findCard() {
        // Given
        Deck deck = new Deck();
        deck.add("XL");

        // When and then
        assertAll(
                () -> assertEquals(new Card("XL"), deck.get("xl")),
                () -> assertEquals("XL", deck.get(" xl ").getValue()),
                () -> assertTrue(deck.find("xl").isPresent()),
                () -> assertTrue(deck.find("xs").isEmpty()),
                () -> assertTrue(deck.contains(new Card("xl"))),
                () -> assertEquals(0, deck.indexOf(new Card("xl"))),
                () -> assertEquals(-1, deck.indexOf(new Card("xs")))
        );
    }

    @Test
    @DisplayName("Lookup of a missing card fails")
    void getMissingCard() {
        Deck deck = new Deck();
        deck.add("1");

        ConstraintException exception = assertThrows(ConstraintException.class, () -> deck.get("2"));

        assertEquals("Card with the value \"2\" is not found in the deck", exception.getMessage());
    }

    @Test
    @DisplayName("Card removal and deck clearing")
    void removeAndClear() {
        // Given
        Deck deck = new Deck();
        deck.add("1");
        deck.add("2");

        // When
        boolean removed = deck.remove(new Card("1"));
        boolean removedAgain = deck.remove(new Card("1"));

        // Then
        assertAll(
                () -> assertTrue(removed),
                () -> assertFalse(removedAgain),
                () -> assertEquals(List.of(new Card("2")), deck.get())
        );

        // When
        deck.clear();

        // Then
        assertTrue(deck.isEmpty());
    }

    @Test
    @DisplayName("Deck text representation")
    void deckToString() {
        Deck deck = new Deck();
        assertEquals("Doesn't have cards", deck.toString());

        deck.add("1");
        deck.add("?");
        assertEquals("[1; ?]", deck.toString());
    }

    @Test
    @DisplayName("Decks with the same cards are equal")
    void equalDecks() {
        Deck deck1 = new Deck();
        deck1.add("1");
        Deck deck2 = new Deck();
        deck2.add("1");

        assertEquals(deck1, deck2);
        assertEquals(deck1.hashCode(), deck2.hashCode());

        deck2.add("2");
        assertNotEquals(deck1, deck2);
    }

    @Test
    @DisplayName("Deck must have from 1 to 20 cards")
    void validateSize() {
        Deck deck = new Deck();
        assertFalse(validator.validate(deck).isEmpty());

        IntStream.rangeClosed(1, 20).forEach(value -> deck.add(String.valueOf(value)));
        assertTrue(validator.validate(deck).isEmpty());

        deck.add("21");
        assertFalse(validator.validate(deck).isEmpty());
    }
}
