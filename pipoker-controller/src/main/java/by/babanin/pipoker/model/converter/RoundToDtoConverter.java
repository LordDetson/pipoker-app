package by.babanin.pipoker.model.converter;

import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;
import org.modelmapper.spi.MappingEngine;

import by.babanin.pipoker.entity.Round;
import by.babanin.pipoker.entity.Vote;
import by.babanin.pipoker.model.RoundDto;
import by.babanin.pipoker.model.VoteDto;

public class RoundToDtoConverter implements Converter<Round, RoundDto> {

    @Override
    public RoundDto convert(MappingContext<Round, RoundDto> context) {
        Round round = context.getSource();
        MappingEngine mappingEngine = context.getMappingEngine();
        return RoundDto.builder()
                .revealedAt(round.getRevealedAt())
                .votes(round.getVotes().stream()
                        .sorted(Vote::compareTo)
                        .map(vote -> mappingEngine.map(context.create(vote, VoteDto.class)))
                        .toList())
                .build();
    }
}
