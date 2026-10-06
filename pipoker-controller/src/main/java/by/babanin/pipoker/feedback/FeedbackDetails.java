package by.babanin.pipoker.feedback;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What the page added to feedback, as labelled lines the owner reads at a glance. In Russian, like the rest
 * of what the owner gets from PiPoker.
 */
public final class FeedbackDetails {

    public record Detail(String label, String value) {
    }

    private FeedbackDetails() {
    }

    // Like "Время: 17:05 (Europe/Minsk)". What the page didn't send is left out.
    public static List<Detail> of(FeedbackDto feedback) {
        List<Detail> details = new ArrayList<>();
        add(details, "Контакт", feedback.getContact());
        add(details, "Страница", feedback.getPage());
        add(details, "Комната", feedback.getRoomId() == null ? null : feedback.getRoomId().toString());
        add(details, "Участники", people(feedback));
        add(details, "Раунд", round(feedback));
        add(details, "Время", join(feedback.getTime(), feedback.getTimeZone()));
        add(details, "Язык", join(feedback.getLanguage(), isBlank(feedback.getBrowserLanguages()) ? null
                : "браузер: " + feedback.getBrowserLanguages()));
        add(details, "Экран", join(feedback.getScreen(), isBlank(feedback.getWindow()) ? null : "окно " + feedback.getWindow()));
        add(details, "Браузер", feedback.getBrowser());
        return details;
    }

    // Like "голосуют 4, наблюдают 1"
    private static String people(FeedbackDto feedback) {
        if(feedback.getVoters() == null && feedback.getWatchers() == null) {
            return null;
        }
        return "голосуют " + Objects.requireNonNullElse(feedback.getVoters(), 0)
                + ", наблюдают " + Objects.requireNonNullElse(feedback.getWatchers(), 0);
    }

    // Like "голосование, проголосовали 2 из 4" or "карты открыты, принята оценка 3"
    private static String round(FeedbackDto feedback) {
        if(feedback.getRound() == null) {
            return null;
        }
        return switch(feedback.getRound()) {
            case VOTING -> "голосование" + (feedback.getVoted() == null ? ""
                    : ", проголосовали " + feedback.getVoted() + (feedback.getVoters() == null ? "" : " из " + feedback.getVoters()));
            case REVEALED -> "карты открыты" + (isBlank(feedback.getEstimate()) ? ""
                    : ", принята оценка " + feedback.getEstimate().strip());
        };
    }

    private static void add(List<Detail> details, String label, String value) {
        if(!isBlank(value)) {
            details.add(new Detail(label, value.strip()));
        }
    }

    // The second value goes in brackets, if there is one
    private static String join(String value, String detail) {
        if(isBlank(detail)) {
            return value;
        }
        return isBlank(value) ? detail : value + " (" + detail + ")";
    }

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
