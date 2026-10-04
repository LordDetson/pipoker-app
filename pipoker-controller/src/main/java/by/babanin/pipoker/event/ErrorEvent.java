package by.babanin.pipoker.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import by.babanin.pipoker.exception.ErrorCode;
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

    private String destination;

    // For developers: the page shows its own text for the code, in the language of the page
    private String message;

    private ErrorCode code;
}
