package by.babanin.pipoker.humancheck;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells the page how to run the human check. The page asks the server rather than carrying the key in its bundle,
 * because the same bundle goes to QA and to PROD, and each runs the check with keys of its own.
 */
@RestController
@RequestMapping("/api/human-check")
public class HumanCheckController {

    private final HumanCheck humanCheck;

    public HumanCheckController(HumanCheck humanCheck) {
        this.humanCheck = humanCheck;
    }

    @GetMapping
    HumanCheckDto get() {
        return new HumanCheckDto(humanCheck.siteKey().orElse(null));
    }
}
