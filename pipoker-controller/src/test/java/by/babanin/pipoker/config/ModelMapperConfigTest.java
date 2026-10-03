package by.babanin.pipoker.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

import by.babanin.pipoker.entity.Card;
import by.babanin.pipoker.entity.Deck;
import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.entity.Vote;
import by.babanin.pipoker.model.DeckDto;
import by.babanin.pipoker.model.ParticipantDto;
import by.babanin.pipoker.model.RoomDto;
import by.babanin.pipoker.model.VoteDto;

class ModelMapperConfigTest {

    private static ModelMapper modelMapper;

    @BeforeAll
    static void setup() {
        modelMapper = new ModelMapperConfig().modelMapper();
    }

    @Test
    @DisplayName("Room is mapped with its deck, participants and votes")
    void mapRoom() {
        // Given
        Deck deck = new Deck();
        deck.add("1");
        deck.add("2");
        deck.add("?");
        Room room = new Room("Sprint 42", deck);
        room.addParticipant("Dmitry");
        room.addWatcher("Alex");
        room.addVote("Dmitry", "2");

        // When
        RoomDto dto = modelMapper.map(room, RoomDto.class);

        // Then
        assertAll(
                () -> assertEquals(room.getId(), dto.getId()),
                () -> assertEquals("Sprint 42", dto.getName()),
                () -> assertEquals(List.of("1", "2", "?"), List.copyOf(dto.getDeck().getCards())),
                () -> assertEquals(List.of(new ParticipantDto("Alex", true), new ParticipantDto("Dmitry", false)),
                        List.copyOf(dto.getParticipants())),
                () -> assertTrue(List.copyOf(dto.getParticipants()).get(0).isWatcher()),
                () -> assertFalse(List.copyOf(dto.getParticipants()).get(1).isWatcher()),
                () -> assertEquals(List.of(new VoteDto("Dmitry", "2")), List.copyOf(dto.getVotes())),
                () -> assertEquals("2", List.copyOf(dto.getVotes()).get(0).getCard())
        );
        modelMapper.validate();
    }

    @Test
    @DisplayName("Deck is created from the card values in their order")
    void mapDeck() {
        DeckDto dto = new DeckDto();
        dto.getCards().addAll(List.of("S", "M", "L"));

        Deck deck = modelMapper.map(dto, Deck.class);

        assertEquals(List.of(new Card("S"), new Card("M"), new Card("L")), deck.get());
    }

    @Test
    @DisplayName("Deck is mapped to its card values")
    void mapDeckToDto() {
        Deck deck = new Deck();
        deck.add("S");
        deck.add("M");

        DeckDto dto = modelMapper.map(deck, DeckDto.class);

        assertEquals(List.of("S", "M"), List.copyOf(dto.getCards()));
    }

    @Test
    @DisplayName("Participant and watcher are created from DTOs")
    void mapParticipant() {
        Participant participant = modelMapper.map(new ParticipantDto(" Dmitry ", false), Participant.class);
        Participant watcher = modelMapper.map(new ParticipantDto("Alex", true), Participant.class);

        assertAll(
                () -> assertEquals("Dmitry", participant.getNickname()),
                () -> assertFalse(participant.isWatcher()),
                () -> assertEquals("Alex", watcher.getNickname()),
                () -> assertTrue(watcher.isWatcher())
        );
    }

    @Test
    @DisplayName("Participant is mapped to a DTO")
    void mapParticipantToDto() {
        ParticipantDto dto = modelMapper.map(Participant.createWatcher("Alex"), ParticipantDto.class);

        assertEquals("Alex", dto.getNickname());
        assertTrue(dto.isWatcher());
    }

    @Test
    @DisplayName("Vote is mapped to the nickname and the card value")
    void mapVote() {
        Vote vote = new Vote(Participant.createParticipant("Dmitry"), new Card("XL"));

        VoteDto dto = modelMapper.map(vote, VoteDto.class);

        assertEquals("Dmitry", dto.getNickname());
        assertEquals("XL", dto.getCard());
    }
}
