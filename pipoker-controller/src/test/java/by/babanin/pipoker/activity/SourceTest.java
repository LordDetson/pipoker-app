package by.babanin.pipoker.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SourceTest {

    @ParameterizedTest
    @CsvSource({
            "habr, HABR",
            "TG, TELEGRAM",
            "' boosty ', BOOSTY",
            "video, VIDEO",
            "newsletter, OTHER_LINK",
    })
    void theFromParameterNamesTheChannel(String from, Source expected) {
        assertEquals(expected, Source.of(from, "https://example.com/"));
    }

    @ParameterizedTest
    @CsvSource({
            "https://habr.com/ru/articles/1/, HABR",
            "https://t.me/agile_ru/1, TELEGRAM",
            "https://m.vk.com/wall-1_1, VK",
            "https://boosty.to/detson, BOOSTY",
            "https://alternativeto.net/software/pipoker/, ALTERNATIVETO",
            "https://www.youtube.com/watch?v=1, VIDEO",
            "https://youtu.be/1, VIDEO",
            "https://github.com/LordDetson/pipoker-web, GITHUB",
            "https://yandex.ru/search/?text=planning+poker, YANDEX",
            "https://ya.ru/, YANDEX",
            "https://www.google.com/, GOOGLE",
            "https://www.bing.com/search?q=1, BING",
            "https://duckduckgo.com/, DUCKDUCKGO",
            "https://pipoker.app/guide, DIRECT",
            "https://qa.pipoker.app/, DIRECT",
            "http://localhost:4200/, DIRECT",
            "https://example.com/blog/tools, OTHER_SITE",
            "not a url, OTHER_SITE",
    })
    void theReferringSiteNamesTheChannelWithoutTheParameter(String referrer, Source expected) {
        assertEquals(expected, Source.of(null, referrer));
        assertEquals(expected, Source.of("", referrer));
    }

    @ParameterizedTest
    @CsvSource(value = {"null", "''", "'  '"}, nullValues = "null")
    void nothingKnownIsADirectVisit(String referrer) {
        assertEquals(Source.DIRECT, Source.of(null, referrer));
        assertEquals(Source.DIRECT, Source.of((VisitDto) null));
    }

    @ParameterizedTest
    @CsvSource({"direct", "other_link", "other_site", "yandex"})
    void onlyHandedOutValuesOfTheParameterAreChannels(String from) {
        // The catch-all values and the search engines are never put in a link, so a link with them is an unknown one
        assertEquals(Source.OTHER_LINK, Source.of(from, null));
    }
}
