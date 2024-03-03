package by.babanin.pipoker.config;

import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import by.babanin.pipoker.entity.Card;
import by.babanin.pipoker.entity.Deck;
import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.entity.Vote;
import by.babanin.pipoker.model.DeckDto;
import by.babanin.pipoker.model.ParticipantDto;
import by.babanin.pipoker.model.RoomDto;
import by.babanin.pipoker.model.VoteDto;
import by.babanin.pipoker.service.RoomService;

@Configuration
public class ModelMapperConfig {

    @Bean
    ModelMapper modelMapper(RoomService roomService) {
        ModelMapper modelMapper = new ModelMapper();

        modelMapper.addConverter(context -> {
            DeckDto deckDto = new DeckDto();
            deckDto.getCards().addAll(context.getSource().get().stream()
                    .map(Card::getValue)
                    .toList());
            return deckDto;
        }, Deck.class, DeckDto.class);

        modelMapper.addConverter(context -> {
            Deck deck = new Deck();
            context.getSource().getCards().forEach(deck::add);
            return deck;
        }, DeckDto.class, Deck.class);

        modelMapper.addConverter(context -> {
            Vote vote = context.getSource();
            return VoteDto.builder()
                    .nickname(vote.getParticipant().getNickname())
                    .card(vote.getCard().getValue())
                    .build();
        }, Vote.class, VoteDto.class);

        modelMapper.addConverter(context -> {
            VoteDto voteDto = context.getSource();
            Room room = roomService.get(voteDto.getRoomId());
            Participant participant = room.getParticipant(voteDto.getNickname());
            Card card = room.getDeck().get(voteDto.getCard());
            return new Vote(participant, card);
        }, VoteDto.class, Vote.class);

        modelMapper.addConverter(context -> {
            Participant participant = context.getSource();
            return ParticipantDto.builder()
                    .nickname(participant.getNickname())
                    .watcher(participant.isWatcher())
                    .build();
        }, Participant.class, ParticipantDto.class);

        modelMapper.addConverter(context -> {
            ParticipantDto participantDto = context.getSource();
            String nickname = participantDto.getNickname();
            return participantDto.isWatcher()
                    ? Participant.createWatcher(nickname)
                    : Participant.createParticipant(nickname);
        }, ParticipantDto.class, Participant.class);

        modelMapper.addConverter(context -> {
            Room room = context.getSource();
            DeckDto deckDto = modelMapper.map(room.getDeck(), DeckDto.class);
            RoomDto roomDto = RoomDto.builder()
                    .id(room.getId())
                    .name(room.getName())
                    .deck(deckDto)
                    .build();
            room.getParticipants().stream()
                    .map(participant -> modelMapper.map(participant, ParticipantDto.class))
                    .forEach(participantDto -> roomDto.getParticipants().add(participantDto));
            room.getVotes().stream()
                    .map(vote -> modelMapper.map(vote, VoteDto.class))
                    .forEach(voteDto -> roomDto.getVotes().add(voteDto));
            return roomDto;
        }, Room.class, RoomDto.class);

        modelMapper.addConverter(context -> {
            RoomDto roomDto = context.getSource();
            Deck deck = modelMapper.map(roomDto.getDeck(), Deck.class);
            Room room = new Room(roomDto.getName(), deck);
            roomDto.getParticipants().forEach(participantDto -> {
                Participant participant = participantDto.isWatcher()
                        ? room.addWatcher(participantDto.getNickname())
                        : room.addParticipant(participantDto.getNickname());
                roomDto.getVotes().stream()
                        .filter(voteDto -> Participant.NICKNAME_COMPARATOR.compare(voteDto.getNickname(), participant.getNickname()) == 0)
                        .findFirst()
                        .ifPresent(voteDto -> room.addVote(participant.getNickname(), voteDto.getCard()));
            });
            return room;
        }, RoomDto.class, Room.class);

        return modelMapper;
    }
}
