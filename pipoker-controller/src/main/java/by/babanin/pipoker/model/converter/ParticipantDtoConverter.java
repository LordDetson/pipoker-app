package by.babanin.pipoker.model.converter;

import org.modelmapper.AbstractConverter;

import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.model.ParticipantDto;

public class ParticipantDtoConverter extends AbstractConverter<ParticipantDto, Participant> {

    @Override
    protected Participant convert(ParticipantDto participant) {
        String nickname = participant.getNickname();
        return participant.isWatcher()
                ? Participant.createWatcher(nickname)
                : Participant.createParticipant(nickname);
    }
}
