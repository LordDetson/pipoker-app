package by.babanin.pipoker.bugreport;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What the page added to a bug report, as labelled lines the owner reads at a glance. In Russian, like the rest
 * of what the owner gets from PiPoker.
 */
public final class BugReportDetails {

    public record Detail(String label, String value) {
    }

    private BugReportDetails() {
    }

    // Like "Время: 17:05 (Europe/Minsk)". What the page didn't send is left out.
    public static List<Detail> of(BugReportDto report) {
        List<Detail> details = new ArrayList<>();
        add(details, "Контакт", report.getContact());
        add(details, "Страница", report.getPage());
        add(details, "Комната", report.getRoomId() == null ? null : report.getRoomId().toString());
        add(details, "Участники", people(report));
        add(details, "Раунд", round(report));
        add(details, "Время", join(report.getTime(), report.getTimeZone()));
        add(details, "Язык", join(report.getLanguage(), isBlank(report.getBrowserLanguages()) ? null
                : "браузер: " + report.getBrowserLanguages()));
        add(details, "Экран", join(report.getScreen(), isBlank(report.getWindow()) ? null : "окно " + report.getWindow()));
        add(details, "Браузер", report.getBrowser());
        return details;
    }

    // Like "голосуют 4, наблюдают 1"
    private static String people(BugReportDto report) {
        if(report.getVoters() == null && report.getWatchers() == null) {
            return null;
        }
        return "голосуют " + Objects.requireNonNullElse(report.getVoters(), 0)
                + ", наблюдают " + Objects.requireNonNullElse(report.getWatchers(), 0);
    }

    // Like "голосование, проголосовали 2 из 4" or "карты открыты, принята оценка 3"
    private static String round(BugReportDto report) {
        if(report.getRound() == null) {
            return null;
        }
        return switch(report.getRound()) {
            case VOTING -> "голосование" + (report.getVoted() == null ? ""
                    : ", проголосовали " + report.getVoted() + (report.getVoters() == null ? "" : " из " + report.getVoters()));
            case REVEALED -> "карты открыты" + (isBlank(report.getEstimate()) ? ""
                    : ", принята оценка " + report.getEstimate().strip());
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
