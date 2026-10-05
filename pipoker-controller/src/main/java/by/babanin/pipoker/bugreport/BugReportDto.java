package by.babanin.pipoker.bugreport;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A bug report sent from the site. The person writes what happened and, if they want an answer, how to reach them.
 * The page adds the rest by itself; of the room's people and votes only their counts are sent.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class BugReportDto {

    @NotBlank
    @Size(max = 2000)
    private String message;

    @Size(max = 200)
    private String contact;

    // The address of the page the person was on
    @Size(max = 500)
    private String page;

    // The room the person was in, if any
    private UUID roomId;

    // Who is at the table and how far the round got, as counts: no names, no votes
    @PositiveOrZero
    private Integer voters;

    @PositiveOrZero
    private Integer watchers;

    @PositiveOrZero
    private Integer voted;

    private RoundStage round;

    // The estimate the team accepted for the revealed round
    @Size(max = 20)
    private String estimate;

    // The browser's user agent
    @Size(max = 500)
    private String browser;

    // The language of the interface and the languages the browser asks for
    @Size(max = 10)
    private String language;

    @Size(max = 100)
    private String browserLanguages;

    // Sizes like 1920x1080: of the screen and of the browser window
    @Size(max = 20)
    private String screen;

    @Size(max = 20)
    private String window;

    // The person's local time with its offset, and their time zone
    @Size(max = 40)
    private String time;

    @Size(max = 60)
    private String timeZone;
}
