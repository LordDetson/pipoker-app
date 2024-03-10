package by.babanin.pipoker.entity;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import by.babanin.pipoker.exception.ConstraintException;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class Deck {

    @NotEmpty
    @Size(min = 1, max = 20)
    private final List<Card> cards = new CopyOnWriteArrayList<>();

    public List<Card> get() {
        return Collections.unmodifiableList(cards);
    }

    public boolean add(String cardValue) {
        return add(new Card(cardValue));
    }

    private boolean add(Card card) {
        return doIfNotContains(card, () -> cards.add(card));
    }

    public boolean add(int index, String cardValue) {
        return add(index, new Card(cardValue));
    }

    private boolean add(int index, Card card) {
        return doIfNotContains(card, () -> cards.add(index, card));
    }

    public int size() {
        return cards.size();
    }

    public boolean isEmpty() {
        return cards.isEmpty();
    }

    public boolean contains(Card card) {
        return cards.contains(card);
    }

    public Card get(String cardValue) {
        return find(cardValue)
                .orElseThrow(() -> new ConstraintException(String.format("Card with the value \"%s\" is not found in the deck", cardValue)));
    }

    public Optional<Card> find(String cardValue) {
        return cards.stream()
                .filter(card -> Card.CARD_VALUE_COMPARATOR.compare(card.getValue(), cardValue) == 0)
                .findFirst();
    }

    public boolean remove(Card card) {
        return cards.remove(card);
    }

    public int indexOf(Card o) {
        return cards.indexOf(o);
    }

    public void clear() {
        cards.clear();
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner("; ", "[", "]");
        joiner.setEmptyValue("Doesn't have cards");
        cards.forEach(card -> joiner.add(card.getValue()));
        return joiner.toString();
    }

    private boolean doIfNotContains(Card card, Runnable runnable) {
        if(card == null) {
            throw new ConstraintException("The card should not be null");
        }
        if(!contains(card)) {
            runnable.run();
            return true;
        }
        return false;
    }

    private boolean doIfNotContains(Card card, Supplier<Boolean> runnable) {
        if(card == null) {
            throw new ConstraintException("The card should not be null");
        }
        return !contains(card) && runnable.get();
    }
}
