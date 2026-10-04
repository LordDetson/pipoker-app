package by.babanin.pipoker.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import by.babanin.pipoker.entity.Room;

/**
 * Spring Data implements the methods of {@link MongoRepository} itself. The methods of {@link AtomicRoomRepository} are
 * implemented by {@link AtomicRoomRepositoryImpl}: Spring Data finds it by the name of the interface with the "Impl" suffix
 * and sends those calls there (a custom repository fragment), so nothing refers to that class directly.
 */
@Repository
public interface RoomRepository extends MongoRepository<Room, UUID>, AtomicRoomRepository {

    List<Room> findByLastActivityBefore(Instant time);
}
