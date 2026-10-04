package by.babanin.pipoker.presence;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import by.babanin.pipoker.entity.Participant;

/**
 * Lets the changes of one person's place in a room happen one at a time, each together with telling the room about it.
 * <p>
 * A change of the stored room and the event about it are two steps. When two changes of the same person run
 * at the same moment, for example a page that comes back after a refresh while the server is still taking the person
 * away from the table, the events could reach the room in a different order than the changes were stored. Then
 * everyone else sees the person gone while they are at the table. Each change of a person's place (joining, leaving,
 * stepping away, coming back) runs with the person's lock held, from the change of the stored room until everyone
 * is told about it.
 * <p>
 * A fixed set of locks is shared by everyone, so no lock has to be removed when a person leaves. Two people who
 * happen to share a lock just wait for each other for a moment. A change must not take another person's lock while
 * it holds one.
 */
@Component
public class SeatLocks {

    private static final int LOCK_COUNT = 64;

    private final Object[] locks = new Object[LOCK_COUNT];

    public SeatLocks() {
        for(int i = 0; i < LOCK_COUNT; i++) {
            locks[i] = new Object();
        }
    }

    /**
     * Runs the change of the participant's place in the room, after the changes of their place that started before.
     */
    public <T> T change(UUID roomId, String nickname, Supplier<T> change) {
        synchronized(lockOf(roomId, nickname)) {
            return change.get();
        }
    }

    public void change(UUID roomId, String nickname, Runnable change) {
        synchronized(lockOf(roomId, nickname)) {
            change.run();
        }
    }

    private Object lockOf(UUID roomId, String nickname) {
        int hash = Objects.hash(roomId, Participant.normalizeNickname(nickname));
        return locks[Math.floorMod(hash, LOCK_COUNT)];
    }
}
