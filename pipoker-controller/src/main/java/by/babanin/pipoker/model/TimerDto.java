package by.babanin.pipoker.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * The discussion timer of a room. A page asks to start it for {@code seconds}; the server answers with the time left,
 * not with the moment it runs out: clocks of computers differ, and each page counts down from when it receives it.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimerDto {

    private long seconds;

    // Milliseconds left until the timer runs out, zero when it has; only in what the server sends
    private Long remainingMillis;
}
