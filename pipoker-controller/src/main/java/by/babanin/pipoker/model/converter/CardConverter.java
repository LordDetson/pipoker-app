package by.babanin.pipoker.model.converter;

import org.modelmapper.AbstractConverter;

import by.babanin.pipoker.entity.Card;

public final class CardConverter extends AbstractConverter<Card, String> {

    @Override
    protected String convert(Card card) {
        return card.getValue();
    }
}
