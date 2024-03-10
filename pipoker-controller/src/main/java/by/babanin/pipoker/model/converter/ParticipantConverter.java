package by.babanin.pipoker.model.converter;

import org.modelmapper.AbstractConverter;

import by.babanin.pipoker.entity.Participant;

public class ParticipantConverter extends AbstractConverter<Participant, String> {

    @Override
    protected String convert(Participant participant) {
        return participant.getNickname();
    }
}
