package by.babanin.pipoker.config;

import java.util.List;

import org.bson.Document;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.repository.RoomRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

@Configuration
@Profile("prod")
@EnableMongoRepositories(basePackageClasses = RoomRepository.class)
@PropertySources({
        @PropertySource("classpath:logback.properties"),
        @PropertySource("classpath:mongodbconfig.properties"),
})
public class ServiceConfiguration {

    @Bean
    ValidatorFactory validatorFactory() {
        return Validation.buildDefaultValidatorFactory();
    }

    // Rooms used to keep participants and votes in maps keyed by the compared nickname. Rooms still stored
    // that way (people may be planning during a release) get the same participants and votes as arrays.
    @Bean
    ApplicationRunner storeRoomsWithArrays(MongoTemplate mongoTemplate) {
        return arguments -> {
            Document participant = new Document("key", "$$entry.k")
                    .append("nickname", "$$entry.v.nickname")
                    .append("watcher", "$$entry.v.watcher");
            Document voter = new Document("key", "$$entry.k")
                    .append("nickname", "$$entry.v.participant.nickname")
                    .append("watcher", "$$entry.v.participant.watcher");
            Document set = new Document("$set", new Document()
                    .append("participants", entries("$participantMap", participant))
                    .append("votes", entries("$voteMap", new Document("participant", voter).append("card", "$$entry.v.card"))));
            Document unset = new Document("$unset", List.of("participantMap", "voteMap"));
            mongoTemplate.updateMulti(Query.query(Criteria.where("participantMap").exists(true)),
                    AggregationUpdate.from(List.of(context -> set, context -> unset)),
                    mongoTemplate.getCollectionName(Room.class));
        };
    }

    private static Document entries(String map, Document entry) {
        Document input = new Document("$objectToArray", new Document("$ifNull", List.of(map, new Document())));
        return new Document("$map", new Document("input", input).append("as", "entry").append("in", entry));
    }

    @Bean
    Validator validator(ValidatorFactory validatorFactory) {
        return validatorFactory.getValidator();
    }
}
