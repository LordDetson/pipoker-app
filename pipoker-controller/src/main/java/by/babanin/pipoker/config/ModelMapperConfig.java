package by.babanin.pipoker.config;

import org.modelmapper.ModelMapper;
import org.modelmapper.config.Configuration.AccessLevel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import by.babanin.pipoker.entity.Card;
import by.babanin.pipoker.entity.Deck;
import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Vote;
import by.babanin.pipoker.model.DeckDto;
import by.babanin.pipoker.model.ParticipantDto;
import by.babanin.pipoker.model.VoteDto;
import by.babanin.pipoker.model.converter.CardConverter;
import by.babanin.pipoker.model.converter.DeckDtoConverter;
import by.babanin.pipoker.model.converter.ParticipantDtoConverter;
import by.babanin.pipoker.model.converter.ParticipantConverter;
import by.babanin.pipoker.model.converter.VoteToDtoConverter;

@Configuration
public class ModelMapperConfig {

    @Bean
    ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration()
                .setFieldMatchingEnabled(true)
                .setFullTypeMatchingRequired(true)
                .setCollectionsMergeEnabled(true)
                .setSkipNullEnabled(true)
                .setFieldAccessLevel(AccessLevel.PRIVATE);

        modelMapper.addConverter(new DeckDtoConverter(), DeckDto.class, Deck.class);
        modelMapper.addConverter(new ParticipantDtoConverter(), ParticipantDto.class, Participant.class);
        modelMapper.addConverter(new ParticipantConverter(), Participant.class, String.class);
        modelMapper.addConverter(new CardConverter(), Card.class, String.class);
        modelMapper.addConverter(new VoteToDtoConverter(), Vote.class, VoteDto.class);

        return modelMapper;
    }
}
