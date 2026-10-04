package by.babanin.pipoker.repository;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.entity.Vote;

class AtomicRoomRepositoryImpl implements AtomicRoomRepository {

    // Names of the stored fields of a room
    private static final String PARTICIPANTS = "participants";
    private static final String VOTES = "votes";
    private static final String VOTES_SHOWN = "votesShown";
    private static final String LAST_ACTIVITY = "lastActivity";
    private static final String PARTICIPANT_KEY = "key";
    private static final String VOTE_KEY = "participant.key";

    private final MongoTemplate mongoTemplate;

    AtomicRoomRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public boolean addParticipant(UUID roomId, Participant participant) {
        // The condition on the nickname and the push are one update, so two people can't take the same nickname
        Query room = query(where("id").is(roomId)
                .and(PARTICIPANTS + "." + PARTICIPANT_KEY).ne(participant.getKey()));
        Update update = new Update().push(PARTICIPANTS, toDocument(participant)).set(LAST_ACTIVITY, Instant.now());
        return mongoTemplate.updateFirst(room, update, Room.class).getModifiedCount() == 1;
    }

    @Override
    public boolean returnParticipant(UUID roomId, Participant participant, Vote vote) {
        Query room = query(where("id").is(roomId)
                .and(PARTICIPANTS + "." + PARTICIPANT_KEY).ne(participant.getKey()));
        Update update = new Update().push(PARTICIPANTS, toDocument(participant));
        if(vote != null) {
            update.push(VOTES, toDocument(vote));
        }
        return mongoTemplate.updateFirst(room, update, Room.class).getModifiedCount() == 1;
    }

    @Override
    public Optional<Room> removeParticipant(UUID roomId, String key) {
        Query room = query(where("id").is(roomId).and(PARTICIPANTS + "." + PARTICIPANT_KEY).is(key));
        Update update = new Update()
                .pull(PARTICIPANTS, new Document(PARTICIPANT_KEY, key))
                .pull(VOTES, new Document(VOTE_KEY, key));
        // Returns the room as it was before the update, which still has the participant
        return Optional.ofNullable(mongoTemplate.findAndModify(room, update, Room.class));
    }

    @Override
    public boolean removeIfEmpty(UUID roomId) {
        Query emptyRoom = query(where("id").is(roomId).and(PARTICIPANTS).size(0));
        return mongoTemplate.remove(emptyRoom, Room.class).getDeletedCount() == 1;
    }

    @Override
    public Optional<Room> removeIfIdle(UUID roomId, Instant idleSince) {
        Query idleRoom = query(where("id").is(roomId).and(LAST_ACTIVITY).lt(idleSince));
        return Optional.ofNullable(mongoTemplate.findAndRemove(idleRoom, Room.class));
    }

    @Override
    public long markActiveIfUnknown(Instant time) {
        Query rooms = query(where(LAST_ACTIVITY).exists(false));
        return mongoTemplate.updateMulti(rooms, new Update().set(LAST_ACTIVITY, time), Room.class).getModifiedCount();
    }

    @Override
    public boolean addVote(UUID roomId, Vote vote) {
        String key = vote.getParticipant().getKey();
        // The participant must still be a voter in the room at the moment of the update
        Query room = query(where("id").is(roomId).and(PARTICIPANTS)
                .elemMatch(where(PARTICIPANT_KEY).is(key).and("watcher").is(false)));
        // One update replaces the previous vote: keep the other votes and add the new one.
        // $literal keeps values like a nickname starting with $ from being read as field paths.
        Document otherVotes = new Document("$filter", new Document("input", new Document("$ifNull", List.of("$" + VOTES, List.of())))
                .append("cond", new Document("$ne", List.of("$$this." + VOTE_KEY, new Document("$literal", key)))));
        Document votes = new Document("$concatArrays", List.of(otherVotes, new Document("$literal", List.of(toDocument(vote)))));
        AggregationUpdate update = AggregationUpdate.from(List.of(context -> new Document("$set", new Document(VOTES, votes)
                .append(LAST_ACTIVITY, Instant.now()))));
        return mongoTemplate.updateFirst(room, update, Room.class).getMatchedCount() == 1;
    }

    @Override
    public Optional<Vote> removeVote(UUID roomId, String key) {
        Query room = query(where("id").is(roomId).and(VOTES + "." + VOTE_KEY).is(key));
        Update update = new Update().pull(VOTES, new Document(VOTE_KEY, key)).set(LAST_ACTIVITY, Instant.now());
        return Optional.ofNullable(mongoTemplate.findAndModify(room, update, Room.class))
                .flatMap(before -> before.findVote(key));
    }

    @Override
    public boolean showVotes(UUID roomId) {
        Update update = new Update().set(VOTES_SHOWN, true).set(LAST_ACTIVITY, Instant.now());
        return mongoTemplate.updateFirst(query(where("id").is(roomId)), update, Room.class).getMatchedCount() == 1;
    }

    @Override
    public boolean clearVotes(UUID roomId) {
        Update update = new Update().set(VOTES, List.of()).set(VOTES_SHOWN, false).set(LAST_ACTIVITY, Instant.now());
        return mongoTemplate.updateFirst(query(where("id").is(roomId)), update, Room.class).getMatchedCount() == 1;
    }

    // Written exactly as MongoDB stores the same object inside a saved room
    private Document toDocument(Object value) {
        Document document = new Document();
        mongoTemplate.getConverter().write(value, document);
        document.remove("_class");
        return document;
    }
}
