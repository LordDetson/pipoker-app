package by.babanin.pipoker.event;

import com.fasterxml.jackson.annotation.JsonInclude;

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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorEvent {

    /**
     * Errors the web client handles in its own way rather than only showing the message.
     */
    public enum Code {
        /**
         * The room doesn't exist (anymore), so the page shows that instead of the room.
         */
        ROOM_NOT_FOUND
    }

    private String destination;

    private String message;

    // Null for the errors that only show their message
    private Code code;
}
