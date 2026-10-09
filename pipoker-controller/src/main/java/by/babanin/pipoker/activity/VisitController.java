package by.babanin.pipoker.activity;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Counts the visits of the start page by where people came from (see {@link Source}). The page reports a visit
 * once, when it is opened, over plain HTTP: the count must not depend on the person going on to a room.
 */
@RestController
@RequestMapping("/api/visits")
public class VisitController {

    private final RoomActivity activity;

    public VisitController(RoomActivity activity) {
        this.activity = activity;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void visited(@Valid @RequestBody VisitDto visit) {
        activity.visited(Source.of(visit));
    }
}
