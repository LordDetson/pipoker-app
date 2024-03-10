package by.babanin.pipoker.model.converter;

import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;

import by.babanin.pipoker.entity.Deck;
import by.babanin.pipoker.model.DeckDto;

public class DeckDtoConverter implements Converter<DeckDto, Deck> {

    @Override
    public Deck convert(MappingContext<DeckDto, Deck> context) {
        Deck deck = new Deck();
        context.getSource().getCards().forEach(deck::add);
        return deck;
    }
}
