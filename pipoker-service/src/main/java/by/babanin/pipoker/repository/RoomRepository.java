package by.babanin.pipoker.repository;

import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import by.babanin.pipoker.entity.Room;

@Repository
public interface RoomRepository extends MongoRepository<Room, UUID> {

}
