package by.babanin.pipoker.repository;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.entity.Round;
import by.babanin.pipoker.entity.Task;
import by.babanin.pipoker.entity.Timer;
import by.babanin.pipoker.entity.Vote;

class AtomicRoomRepositoryImpl implements AtomicRoomRepository {

    // Names of the stored fields of a room
    private static final String PARTICIPANTS = "participants";
    private static final String VOTES = "votes";
    private static final String VOTES_SHOWN = "votesShown";
    private static final String LAST_ACTIVITY = "lastActivity";
    private static final String TIMER = "timer";
    private static final String TASK = "task";
    private static final String HISTORY = "history";
    private static final String ROUND_REVEALED_AT = "revealedAt";
    private static final String ROUND_VOTES = "votes";
    private static final String ROUND_TASK = "task";
    private static final String ROUND_ESTIMATE = "estimate";
    private static final String PARTICIPANT_KEY = "key";
    private static final String PARTICIPANT_WATCHER = "watcher";
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
    public Optional<Room> changeRole(UUID roomId, String key, boolean watcher) {
        Query room = query(where("id").is(roomId).and(PARTICIPANTS + "." + PARTICIPANT_KEY).is(key));
        // $literal keeps values like a nickname starting with $ from being read as field paths
        Document literalKey = new Document("$literal", key);
        Document participants = new Document("$map", new Document("input", "$" + PARTICIPANTS)
                .append("in", new Document("$cond", List.of(
                        new Document("$eq", List.of("$$this." + PARTICIPANT_KEY, literalKey)),
                        new Document("$mergeObjects", List.of("$$this", new Document(PARTICIPANT_WATCHER, watcher))),
                        "$$this"))));
        Document changes = new Document(PARTICIPANTS, participants).append(LAST_ACTIVITY, Instant.now());
        if(watcher) {
            // Every expression of the $set stage reads the room as it was before it, like Room#changeRole does:
            // the vote goes only while the cards are hidden
            Document votes = new Document("$ifNull", List.of("$" + VOTES, List.of()));
            Document otherVotes = new Document("$filter", new Document("input", votes)
                    .append("cond", new Document("$ne", List.of("$$this." + VOTE_KEY, literalKey))));
            changes.append(VOTES, new Document("$cond", List.of(
                    new Document("$eq", List.of("$" + VOTES_SHOWN, true)), votes, otherVotes)));
        }
        AggregationUpdate update = AggregationUpdate.from(List.of(context -> new Document("$set", changes)));
        // Returns the room as it was before the update, which tells whether the vote was taken back
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
    public Optional<Room> showVotes(UUID roomId, Instant revealedAt) {
        // Every expression of the $set stage reads the room as it was before it, like Room#showVotes does
        Document votes = new Document("$ifNull", List.of("$" + VOTES, List.of()));
        Document history = new Document("$ifNull", List.of("$" + HISTORY, List.of()));
        // A room without a task records a round without one: a missing field stays missing in a new document
        Document round = new Document(ROUND_REVEALED_AT, revealedAt).append(ROUND_VOTES, votes).append(ROUND_TASK, "$" + TASK);
        Document notRecorded = new Document("$or", List.of(
                new Document("$eq", List.of("$" + VOTES_SHOWN, true)),
                new Document("$eq", List.of(new Document("$size", votes), 0))));
        Document recorded = new Document("$slice", List.of(
                new Document("$concatArrays", List.of(history, List.of(round))), -Room.HISTORY_LIMIT));
        AggregationUpdate update = AggregationUpdate.from(List.of(context -> new Document("$set", new Document(HISTORY,
                new Document("$cond", List.of(notRecorded, history, recorded)))
                .append(VOTES_SHOWN, true)
                .append(TIMER, "$$REMOVE")
                .append(LAST_ACTIVITY, Instant.now()))));
        // Returns the room as it was before the update, which tells whether this update recorded the round
        return Optional.ofNullable(mongoTemplate.findAndModify(query(where("id").is(roomId)), update, Room.class));
    }

    @Override
    public Optional<Room> clearVotes(UUID roomId) {
        // Reads the room as it was, like Room#clearVotes does: the task goes once the revealed round got its estimate
        // An estimate is a card value, never empty, so a missing one alone counts as false
        Document estimated = new Document("$and", List.of(
                new Document("$eq", List.of("$" + VOTES_SHOWN, true)),
                new Document("$ifNull", List.of(lastRound(ROUND_ESTIMATE), false))));
        AggregationUpdate update = AggregationUpdate.from(List.of(context -> new Document("$set", new Document(VOTES, List.of())
                .append(VOTES_SHOWN, false)
                .append(TIMER, "$$REMOVE")
                .append(TASK, new Document("$cond", List.of(estimated, "$$REMOVE", "$" + TASK)))
                .append(LAST_ACTIVITY, Instant.now()))));
        // Returns the room after the update, which tells the task the new round has
        return Optional.ofNullable(mongoTemplate.findAndModify(query(where("id").is(roomId)), update,
                FindAndModifyOptions.options().returnNew(true), Room.class));
    }

    @Override
    public boolean setTask(UUID roomId, Task task) {
        Query room = query(where("id").is(roomId).and(VOTES_SHOWN).ne(true));
        Update update = task == null ? new Update().unset(TASK) : new Update().set(TASK, toDocument(task));
        update.set(LAST_ACTIVITY, Instant.now());
        return mongoTemplate.updateFirst(room, update, Room.class).getMatchedCount() == 1;
    }

    @Override
    public Optional<Round> acceptEstimate(UUID roomId, Instant revealedAt, String estimate) {
        // The round must still be on the table: the cards are revealed and it is the last round of the history
        Query room = query(where("id").is(roomId).and(VOTES_SHOWN).is(true)
                .andOperator(Criteria.expr(() -> new Document("$eq", List.of(lastRound(ROUND_REVEALED_AT), revealedAt)))));
        Update update = new Update()
                .set(HISTORY + ".$[round]." + ROUND_ESTIMATE, estimate)
                .filterArray(where("round." + ROUND_REVEALED_AT).is(revealedAt))
                .set(LAST_ACTIVITY, Instant.now());
        Room after = mongoTemplate.findAndModify(room, update, FindAndModifyOptions.options().returnNew(true), Room.class);
        return Optional.ofNullable(after).map(changed -> changed.getHistory().getLast());
    }

    // A field of the last round of the history, null when the history is empty or missing
    private static Document lastRound(String field) {
        return new Document("$let", new Document("vars",
                new Document("last", new Document("$arrayElemAt", List.of("$" + HISTORY, -1))))
                .append("in", "$$last." + field));
    }

    @Override
    public boolean startTimer(UUID roomId, Timer timer) {
        Query room = query(where("id").is(roomId).and(VOTES_SHOWN).ne(true));
        Update update = new Update().set(TIMER, toDocument(timer)).set(LAST_ACTIVITY, Instant.now());
        return mongoTemplate.updateFirst(room, update, Room.class).getMatchedCount() == 1;
    }

    @Override
    public boolean stopTimer(UUID roomId) {
        Update update = new Update().unset(TIMER).set(LAST_ACTIVITY, Instant.now());
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
