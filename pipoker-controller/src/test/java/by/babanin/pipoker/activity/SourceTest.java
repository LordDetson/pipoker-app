package by.babanin.pipoker.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SourceTest {

    @ParameterizedTest
    @CsvSource({
            "habr, HABR",
            "TG, TELEGRAM",
            "' boosty ', BOOSTY",
            "support, SUPPORT",
            "video, VIDEO",
            "vc, VC",
            "tproger, TPROGER",
            "pikabu, PIKABU",
            "reddit, REDDIT",
            "hn, HACKER_NEWS",
            "devto, DEV_TO",
            "youtube, YOUTUBE",
            "rutube, RUTUBE",
            "instagram, INSTAGRAM",
            "test, TEST",
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
            "https://vkvideo.ru/video-1_1, VK",
            "https://www.instagram.com/p/1/, INSTAGRAM",
            "https://boosty.to/detson, BOOSTY",
            "https://lorddetson.github.io/, SUPPORT",
            "https://lorddetson.github.io/pipoker/, SUPPORT",
            "https://alternativeto.net/software/pipoker/, ALTERNATIVETO",
            "https://vc.ru/tribuna/1, VC",
            "https://tproger.ru/articles/1, TPROGER",
            "https://pikabu.ru/story/1, PIKABU",
            "https://old.reddit.com/r/agile/comments/1/, REDDIT",
            "https://news.ycombinator.com/item?id=1, HACKER_NEWS",
            "https://dev.to/detson/1, DEV_TO",
            "https://www.youtube.com/watch?v=1, YOUTUBE",
            "https://youtu.be/1, YOUTUBE",
            "https://rutube.ru/video/1/, RUTUBE",
            "https://dzen.ru/video/watch/1, OTHER_SITE",
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
    @CsvSource({"direct", "other_link", "other_site", "yandex", "bot"})
    void onlyHandedOutValuesOfTheParameterAreChannels(String from) {
        // The catch-all values and the search engines are never put in a link, so a link with them is an unknown one
        assertEquals(Source.OTHER_LINK, Source.of(from, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
            "Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko; compatible; Googlebot/2.1; +http://www.google.com/bot.html) Chrome/141.0.0.0 Safari/537.36",
            "Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko; compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm) Chrome/116.0.1938.76 Safari/537.36",
            "Mozilla/5.0 (compatible; YandexBot/3.0; +http://yandex.com/bots)",
            "Mozilla/5.0 (compatible; Mail.RU_Bot/2.0; +http://go.mail.ru/help/robots)",
            "DuckDuckBot-Https/1.1; (+https://duckduckgo.com/duckduckbot)",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/141.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Linux; Android 11; moto g power (2022)) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36 Chrome-Lighthouse",
            "curl/8.5.0",
            "python-requests/2.32.3",
            "",
    })
    void robotsAreCountedApartWhateverLinkTheyFollowed(String userAgent) {
        assertEquals(Source.BOT, Source.ofVisit(VisitDto.builder().from("habr").build(), userAgent));
        assertEquals(Source.BOT, Source.ofVisit(VisitDto.builder().referrer("https://www.bing.com/search?q=1").build(), userAgent));
        assertEquals(Source.BOT, Source.ofVisit(null, userAgent));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:143.0) Gecko/20100101 Firefox/143.0",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.6 Mobile/15E148 Safari/604.1",
            "Mozilla/5.0 (Linux; Android 14; SM-A546B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36 YaBrowser/25.8.1.100",
            "Mozilla/5.0 (Linux; Android 10; CUBOT X30) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36",
    })
    void browsersKeepTheirSource(String userAgent) {
        assertEquals(Source.HABR, Source.ofVisit(VisitDto.builder().from("habr").build(), userAgent));
        assertEquals(Source.DIRECT, Source.ofVisit(null, userAgent));
    }

    @Test
    void ownTestsStayTestsInAHeadlessBrowser() {
        String playwright = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/141.0.0.0 Safari/537.36";
        assertEquals(Source.TEST, Source.ofVisit(VisitDto.builder().from("test").build(), playwright));
    }
}
