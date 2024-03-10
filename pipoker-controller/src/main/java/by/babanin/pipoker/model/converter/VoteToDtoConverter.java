package by.babanin.pipoker.model.converter;

import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;
import org.modelmapper.spi.MappingEngine;

import by.babanin.pipoker.entity.Vote;
import by.babanin.pipoker.model.VoteDto;

public class VoteToDtoConverter implements Converter<Vote, VoteDto> {

    @Override
    public VoteDto convert(MappingContext<Vote, VoteDto> context) {
        Vote vote = context.getSource();
        MappingEngine mappingEngine = context.getMappingEngine();
        return VoteDto.builder()
                .nickname(mappingEngine.map(context.create(vote.getParticipant(), String.class)))
                .card(mappingEngine.map(context.create(vote.getCard(), String.class)))
                .build();
    }
}
