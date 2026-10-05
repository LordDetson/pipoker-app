package by.babanin.pipoker.bugreport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import by.babanin.pipoker.bugreport.BugReportDetails.Detail;

class BugReportDetailsTest {

    static final BugReportDto FULL_REPORT = BugReportDto.builder()
            .message("The cards don't turn over")
            .contact("@alex")
            .page("https://pipoker.duckdns.org/room/3f2b6a8e-0c1d-4e5f-9a7b-1c2d3e4f5a6b")
            .roomId(UUID.fromString("3f2b6a8e-0c1d-4e5f-9a7b-1c2d3e4f5a6b"))
            .voters(4)
            .watchers(1)
            .voted(2)
            .round(RoundStage.VOTING)
            .browser("Mozilla/5.0 Firefox/150.0")
            .language("ru")
            .browserLanguages("ru-RU, en")
            .screen("1920x1080")
            .window("1366x768")
            .time("2026-10-05 17:05:00 +03:00")
            .timeZone("Europe/Minsk")
            .build();

    @Test
    @DisplayName("Everything the page added is labelled")
    void full() {
        assertEquals(List.of(
                new Detail("Контакт", "@alex"),
                new Detail("Страница", "https://pipoker.duckdns.org/room/3f2b6a8e-0c1d-4e5f-9a7b-1c2d3e4f5a6b"),
                new Detail("Комната", "3f2b6a8e-0c1d-4e5f-9a7b-1c2d3e4f5a6b"),
                new Detail("Участники", "голосуют 4, наблюдают 1"),
                new Detail("Раунд", "голосование, проголосовали 2 из 4"),
                new Detail("Время", "2026-10-05 17:05:00 +03:00 (Europe/Minsk)"),
                new Detail("Язык", "ru (браузер: ru-RU, en)"),
                new Detail("Экран", "1920x1080 (окно 1366x768)"),
                new Detail("Браузер", "Mozilla/5.0 Firefox/150.0")
        ), BugReportDetails.of(FULL_REPORT));
    }

    @Test
    @DisplayName("What the page didn't send is left out")
    void partial() {
        BugReportDto report = BugReportDto.builder().message("Broken").contact(" ").window("400x800").timeZone("UTC").build();

        assertEquals(List.of(new Detail("Время", "UTC"), new Detail("Экран", "окно 400x800")), BugReportDetails.of(report));
    }

    @Test
    @DisplayName("A revealed round tells the estimate the team accepted, if any")
    void revealed() {
        BugReportDto revealed = BugReportDto.builder().message("Broken").round(RoundStage.REVEALED).build();
        BugReportDto estimated = BugReportDto.builder().message("Broken").round(RoundStage.REVEALED).estimate("3").build();

        assertEquals(List.of(new Detail("Раунд", "карты открыты")), BugReportDetails.of(revealed));
        assertEquals(List.of(new Detail("Раунд", "карты открыты, принята оценка 3")), BugReportDetails.of(estimated));
    }
}
