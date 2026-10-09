package by.babanin.pipoker.activity;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Where a page was opened from, as the page knows it: the {@code from} parameter of the link that opened it and the
 * address of the referring page. Both are turned into a {@link Source} and not kept.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class VisitDto {

    @Size(max = 64)
    private String from;

    @Size(max = 2000)
    private String referrer;
}
