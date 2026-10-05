package by.babanin.pipoker.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * What a round estimates: the name or the key of a task, like PIP-25, and a link to it.
 */
@Getter
@RequiredArgsConstructor
@EqualsAndHashCode
@ToString
public class Task {

    public static final int MAX_NAME_LENGTH = 200;
    public static final int MAX_URL_LENGTH = 2000;

    @NotBlank
    @Size(max = MAX_NAME_LENGTH)
    private final String name;

    // Null when the task has no link. Only web links, so a page never opens a link that runs a script.
    @Size(max = MAX_URL_LENGTH)
    @Pattern(regexp = "https?://\\S+", flags = Pattern.Flag.CASE_INSENSITIVE)
    private final String url;

    /**
     * @return the task with the name and the link trimmed, null when the name is blank: the round estimates nothing named
     */
    public static Task of(String name, String url) {
        if(name == null || name.isBlank()) {
            return null;
        }
        return new Task(name.strip(), url == null || url.isBlank() ? null : url.strip());
    }
}
