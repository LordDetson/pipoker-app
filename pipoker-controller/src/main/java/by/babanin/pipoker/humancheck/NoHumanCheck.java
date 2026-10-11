package by.babanin.pipoker.humancheck;

import java.util.Optional;

/**
 * Lets every request pass. Used where the check isn't set up, for example on a developer's computer.
 */
public class NoHumanCheck implements HumanCheck {

    @Override
    public Optional<String> siteKey() {
        return Optional.empty();
    }

    @Override
    public boolean passed(String token, String action, String address) {
        return true;
    }
}
