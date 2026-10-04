package by.babanin.pipoker.config;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

import by.babanin.pipoker.entity.Card;
import by.babanin.pipoker.entity.Deck;
import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.entity.Round;
import by.babanin.pipoker.entity.Timer;
import by.babanin.pipoker.entity.Vote;
import by.babanin.pipoker.model.DeckDto;
import by.babanin.pipoker.model.ParticipantDto;
import by.babanin.pipoker.model.RoomDto;
import by.babanin.pipoker.model.RoundDto;
import by.babanin.pipoker.model.TimerDto;
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
        room.showVotes(Instant.parse("2026-10-04T17:00:00Z"));

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
                () -> assertEquals("2", List.copyOf(dto.getVotes()).get(0).getCard()),
                () -> assertEquals(List.of(new RoundDto(Instant.parse("2026-10-04T17:00:00Z"), List.of(new VoteDto("Dmitry", "2")))),
                        dto.getHistory()),
                () -> assertNull(dto.getTimer())
        );
        modelMapper.validate();
    }

    @Test
    @DisplayName("Round is mapped with its votes sorted by nickname")
    void mapRound() {
        Instant revealedAt = Instant.parse("2026-10-04T17:00:00Z");
        Round round = new Round(revealedAt, List.of(
                new Vote(Participant.createParticipant("kate"), new Card("1")),
                new Vote(Participant.createParticipant("Alex"), new Card("?"))));

        RoundDto dto = modelMapper.map(round, RoundDto.class);

        assertEquals(new RoundDto(revealedAt, List.of(new VoteDto("Alex", "?"), new VoteDto("kate", "1"))), dto);
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

    @Test
    @DisplayName("Timer is mapped to its length and the time left")
    void mapTimer() {
        Timer running = new Timer(120, Instant.now().plusSeconds(90));
        Timer runOut = new Timer(60, Instant.now().minusSeconds(5));

        TimerDto runningDto = modelMapper.map(running, TimerDto.class);
        TimerDto runOutDto = modelMapper.map(runOut, TimerDto.class);

        assertAll(
                () -> assertEquals(120, runningDto.getSeconds()),
                () -> assertTrue(runningDto.getRemainingMillis() > 89_000 && runningDto.getRemainingMillis() <= 90_000),
                () -> assertEquals(60, runOutDto.getSeconds()),
                () -> assertEquals(0, runOutDto.getRemainingMillis())
        );
    }
}
