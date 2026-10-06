package by.babanin.pipoker.feedback;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.log4j.Log4j2;

/**
 * Takes feedback over plain HTTP rather than STOMP, so that a person can report a problem even when
 * the page can't connect to the rooms.
 */
@RestController
@RequestMapping("/api/feedback")
@Log4j2
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    // The proxy in front of the backend passes the browser's address on, and the server takes it from there
    // (see server.forward-headers-strategy)
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void send(@Valid @RequestBody FeedbackDto feedback, HttpServletRequest request) {
        feedbackService.send(feedback, request.getRemoteAddr());
    }

    @ExceptionHandler(TooMuchFeedbackException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    void tooMuchFeedback() {
        // The page tells the person to try again later
    }

    @ExceptionHandler(FeedbackDeliveryException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    void notDelivered(FeedbackDeliveryException e) {
        log.error("Feedback wasn't delivered: {}", e.getMessage());
    }
}
