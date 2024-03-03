package by.babanin.pipoker.controller;

import java.util.List;

import org.modelmapper.ModelMapper;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import by.babanin.pipoker.PiPokerApplication;
import by.babanin.pipoker.entity.Deck;
import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.model.RoomCreationDto;
import by.babanin.pipoker.model.RoomDto;
import by.babanin.pipoker.service.RoomService;
import jakarta.validation.Valid;

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
        List<Participant> participants = roomCreationDto.getParticipants().stream()
                .map(participantDto -> modelMapper.map(participantDto, Participant.class))
                .toList();
        Room room = roomService.create(roomCreationDto.getName(), deck, participants);
        return modelMapper.map(room, RoomDto.class);
    }
}
