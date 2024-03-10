package by.babanin.pipoker.controller;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.modelmapper.ModelMapper;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import by.babanin.pipoker.PiPokerApplication;
import by.babanin.pipoker.entity.Deck;
import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.entity.Vote;
import by.babanin.pipoker.event.RoomEvent;
import by.babanin.pipoker.event.RoomEvent.EventType;
import by.babanin.pipoker.model.ParticipantDto;
import by.babanin.pipoker.model.RoomCreationDto;
import by.babanin.pipoker.model.RoomDto;
import by.babanin.pipoker.model.VoteDto;
import by.babanin.pipoker.service.RoomService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@Controller
@Validated
@MessageMapping(PiPokerApplication.ROOM_DESTINATION_PREFIX)
public class RoomController {

    private final RoomService roomService;
    private final ModelMapper modelMapper;

    public RoomController(RoomService roomService, ModelMapper modelMapper) {
        this.roomService = roomService;
        this.modelMapper = modelMapper;
    }

    @MessageMapping("/create")
    @SendTo(PiPokerApplication.TOPIC_ROOM_DESTINATION_PREFIX)
    RoomDto create(@Valid RoomCreationDto roomCreationDto) {
        Deck deck = modelMapper.map(roomCreationDto.getDeck(), Deck.class);
        Set<Participant> participants = roomCreationDto.getParticipants().stream()
                .map(participantDto -> modelMapper.map(participantDto, Participant.class))
                .collect(Collectors.toUnmodifiableSet());
        Room room = roomService.create(roomCreationDto.getName(), deck, participants);
        RoomDto result = modelMapper.map(room, RoomDto.class);
        modelMapper.validate();
        return result;
    }

    @MessageMapping({ "/{roomId}/participants/add", "/{roomId}/join" })
    @SendTo(PiPokerApplication.TOPIC_ROOM_DESTINATION_PREFIX + ".{roomId}")
    ParticipantDto addParticipant(@DestinationVariable UUID roomId, @Valid ParticipantDto participantDto) {
        String nickname = participantDto.getNickname();
        Participant added = participantDto.isWatcher()
                ? roomService.addWatcher(roomId, nickname)
                : roomService.addParticipant(roomId, nickname);
        ParticipantDto result = modelMapper.map(added, ParticipantDto.class);
        modelMapper.validate();
        return result;
    }

    @MessageMapping({ "/{roomId}/participants/remove", "/{roomId}/participants/delete", "/{roomId}/left" })
    @SendTo(PiPokerApplication.TOPIC_ROOM_DESTINATION_PREFIX + ".{roomId}")
    ParticipantDto removeParticipant(@DestinationVariable UUID roomId, @NotBlank String nickname) {
        Optional<Participant> removed = roomService.removeParticipant(roomId, nickname);
        ParticipantDto result = modelMapper.map(removed, ParticipantDto.class);
        modelMapper.validate();
        return result;
    }

    @MessageMapping({ "/{roomId}/votes/add", "/{roomId}/vote" })
    @SendTo(PiPokerApplication.TOPIC_ROOM_DESTINATION_PREFIX + ".{roomId}")
    VoteDto addVote(@DestinationVariable UUID roomId, @Valid VoteDto vote) {
        Vote added = roomService.addVote(roomId, vote.getNickname(), vote.getCard());
        VoteDto result = modelMapper.map(added, VoteDto.class);
        modelMapper.validate();
        return result;
    }

    @MessageMapping({ "/{roomId}/votes/remove", "/{roomId}/votes/delete" })
    @SendTo(PiPokerApplication.TOPIC_ROOM_DESTINATION_PREFIX + ".{roomId}")
    VoteDto removeVote(@DestinationVariable UUID roomId, @NotBlank String nickname) {
        Optional<Vote> removed = roomService.removeVote(roomId, nickname);
        VoteDto result = modelMapper.map(removed, VoteDto.class);
        modelMapper.validate();
        return result;
    }

    @MessageMapping("/{roomId}/votes/clear")
    @SendTo(PiPokerApplication.TOPIC_ROOM_DESTINATION_PREFIX + ".{roomId}")
    RoomEvent clearVotes(@DestinationVariable UUID roomId) {
        roomService.clearVotes(roomId);
        return new RoomEvent(roomId, EventType.CLEAR_VOTES);
    }
}
