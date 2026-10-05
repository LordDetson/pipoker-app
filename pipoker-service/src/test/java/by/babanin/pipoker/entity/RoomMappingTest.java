package by.babanin.pipoker.entity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

class RoomMappingTest {

    private static MappingMongoConverter converter;

    @BeforeAll
    static void setup() {
        MongoCustomConversions conversions = new MongoCustomConversions(List.of());
        MongoMappingContext mappingContext = new MongoMappingContext();
        mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        mappingContext.afterPropertiesSet();
        converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mappingContext);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
    }

    @Test
    @DisplayName("New rooms get their own ids")
    void newRoomHasId() {
        Room room1 = new Room("test", new Deck());
        Room room2 = new Room("test", new Deck());

        assertNotNull(room1.getId());
        assertNotEquals(room1.getId(), room2.getId());
    }

    @Test
    @DisplayName("Room is read back the same as it was written")
    void writeAndRead() {
        // Given
        Deck deck = new Deck();
        deck.add("1h");
        deck.add("1d");
        Room room = new Room("test", deck);
        room.addParticipant("Dmitry");
        room.addWatcher("Alex");
        room.setTask(Task.of("PIP-25", "https://example.com/PIP-25"));
        room.addVote("Dmitry", "1d");
        room.showVotes(Instant.parse("2026-10-04T17:00:00Z"));
        room.acceptEstimate(Instant.parse("2026-10-04T17:00:00Z"), "1h");

        // When
        Document document = new Document();
        converter.write(room, document);
        Room result = converter.read(Room.class, document);

        // Then
        assertAll(
                () -> assertEquals(room.getId(), result.getId()),
                () -> assertEquals(room.getName(), result.getName()),
                () -> assertEquals(List.of(new Card("1h"), new Card("1d")), result.getDeck().get()),
                () -> assertEquals(room.getParticipants(), result.getParticipants()),
                () -> assertEquals(room.getVotes(), result.getVotes()),
                () -> assertEquals("1d", result.getVote("Dmitry").getCard().getValue()),
                () -> assertEquals(room.getTask(), result.getTask()),
                () -> assertEquals(room.getHistory(), result.getHistory()),
                () -> assertEquals("PIP-25", result.getHistory().get(0).getTask().getName()),
                () -> assertEquals("1h", result.getHistory().get(0).getEstimate()),
                () -> assertEquals("1d", result.getHistory().get(0).getVotes().get(0).getCard().getValue())
        );
    }

    @Test
    @DisplayName("A room stored before the history was kept is read with an empty history")
    void readWithoutHistory() {
        Document document = new Document();
        converter.write(new Room("test", new Deck()), document);
        document.remove("history");

        assertEquals(List.of(), converter.read(Room.class, document).getHistory());
    }
}
