package by.babanin.pipoker.event;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class RoomEvent {

    public enum EventType {
        CLEAR_VOTES
    }

    @NotNull
    private UUID roomId;

    @NotNull
    private EventType eventType;
}
