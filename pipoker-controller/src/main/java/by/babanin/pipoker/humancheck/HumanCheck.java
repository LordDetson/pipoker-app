package by.babanin.pipoker.humancheck;

import java.util.Optional;

/**
 * Tells a person from a robot without asking the person anything: the page runs a check in the background and sends
 * what it got along with its request, and the server asks the check whether that came from a person.
 */
public interface HumanCheck {

    /**
     * @return the key the page runs the check with, empty when the check is off and every request passes
     */
    Optional<String> siteKey();

    /**
     * @param token what the check gave the page, null if the page got nothing
     * @param action what the request is for, like "feedback", for the log
     * @param address the address of the browser that sent the request
     * @return false if the token isn't from a person
     */
    boolean passed(String token, String action, String address);
}
