package by.babanin.pipoker.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * What a round estimates. A page sends a blank name to estimate nothing named; the server checks the limits
 * (see the Task entity), so a page can send what the person typed.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskDto {

    private String name;

    // Left out when the task has no link
    private String url;
}
