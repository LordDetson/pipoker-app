package by.babanin.pipoker.activity;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Where a visitor came from, as the {@code source} tag of the metrics: a known channel, a search engine, or one of
 * the few catch-all values. The set is fixed, so whatever a page sends, the metrics get a handful of series and
 * nothing a person could be told apart by.
 * <p>
 * A page learns its source from the {@code from} parameter of the link it was opened by (the links PiPoker is
 * announced with carry one, like {@code https://pipoker.app?from=habr}) or, without one, from the site the browser
 * says it came from.
 */
public enum Source {

    /**
     * Typed the address, opened a bookmark, or came from a PiPoker page.
     */
    DIRECT("direct"),
    HABR("habr"),
    TELEGRAM("tg"),
    VK("vk"),
    INSTAGRAM("instagram"),
    BOOSTY("boosty"),
    /**
     * The author's support page, https://lorddetson.github.io/.
     */
    SUPPORT("support"),
    ALTERNATIVETO("alternativeto"),
    SAASHUB("saashub"),
    PRODUCT_HUNT("producthunt"),
    VC("vc"),
    TPROGER("tproger"),
    PIKABU("pikabu"),
    REDDIT("reddit"),
    HACKER_NEWS("hn"),
    DEV_TO("devto"),
    YOUTUBE("youtube"),
    RUTUBE("rutube"),
    /**
     * A video somewhere other than YouTube, RuTube or VK Video.
     */
    VIDEO("video"),
    GITHUB("github"),
    YANDEX("yandex"),
    GOOGLE("google"),
    BING("bing"),
    DUCKDUCKGO("duckduckgo"),
    /**
     * A search engine or another robot that runs the page's script, told apart by the User-Agent it sends.
     */
    BOT("bot"),
    /**
     * PiPoker's own checks and automated tests, opened with {@code ?from=test}. They are no visitors: nothing about
     * them or the rooms they create is counted (see {@link RoomActivity}).
     */
    TEST("test"),
    /**
     * A {@code from} parameter nobody handed out.
     */
    OTHER_LINK("other_link"),
    /**
     * A site not listed here.
     */
    OTHER_SITE("other_site");

    private static final Map<String, Source> BY_TAG = Map.ofEntries(
            Map.entry(HABR.tag, HABR),
            Map.entry(TELEGRAM.tag, TELEGRAM),
            Map.entry(VK.tag, VK),
            Map.entry(INSTAGRAM.tag, INSTAGRAM),
            Map.entry(BOOSTY.tag, BOOSTY),
            Map.entry(SUPPORT.tag, SUPPORT),
            Map.entry(ALTERNATIVETO.tag, ALTERNATIVETO),
            Map.entry(SAASHUB.tag, SAASHUB),
            Map.entry(PRODUCT_HUNT.tag, PRODUCT_HUNT),
            Map.entry(VC.tag, VC),
            Map.entry(TPROGER.tag, TPROGER),
            Map.entry(PIKABU.tag, PIKABU),
            Map.entry(REDDIT.tag, REDDIT),
            Map.entry(HACKER_NEWS.tag, HACKER_NEWS),
            Map.entry(DEV_TO.tag, DEV_TO),
            Map.entry(YOUTUBE.tag, YOUTUBE),
            Map.entry(RUTUBE.tag, RUTUBE),
            Map.entry(VIDEO.tag, VIDEO),
            Map.entry(GITHUB.tag, GITHUB),
            Map.entry(TEST.tag, TEST));

    // The sites visitors come from, by the host or a parent domain of the referring page
    private static final Map<String, Source> BY_DOMAIN = Map.ofEntries(
            Map.entry("pipoker.app", DIRECT),
            Map.entry("localhost", DIRECT),
            Map.entry("habr.com", HABR),
            Map.entry("t.me", TELEGRAM),
            Map.entry("telegram.org", TELEGRAM),
            Map.entry("telegram.me", TELEGRAM),
            Map.entry("vk.com", VK),
            Map.entry("vk.ru", VK),
            Map.entry("vkvideo.ru", VK),
            Map.entry("instagram.com", INSTAGRAM),
            Map.entry("boosty.to", BOOSTY),
            Map.entry("lorddetson.github.io", SUPPORT),
            Map.entry("alternativeto.net", ALTERNATIVETO),
            Map.entry("saashub.com", SAASHUB),
            Map.entry("producthunt.com", PRODUCT_HUNT),
            Map.entry("vc.ru", VC),
            Map.entry("tproger.ru", TPROGER),
            Map.entry("pikabu.ru", PIKABU),
            Map.entry("reddit.com", REDDIT),
            Map.entry("redd.it", REDDIT),
            Map.entry("ycombinator.com", HACKER_NEWS),
            Map.entry("dev.to", DEV_TO),
            Map.entry("youtube.com", YOUTUBE),
            Map.entry("youtu.be", YOUTUBE),
            Map.entry("rutube.ru", RUTUBE),
            Map.entry("github.com", GITHUB),
            Map.entry("yandex.ru", YANDEX),
            Map.entry("yandex.by", YANDEX),
            Map.entry("yandex.com", YANDEX),
            Map.entry("ya.ru", YANDEX),
            Map.entry("google.com", GOOGLE),
            Map.entry("google.by", GOOGLE),
            Map.entry("google.ru", GOOGLE),
            Map.entry("bing.com", BING),
            Map.entry("duckduckgo.com", DUCKDUCKGO));

    // How robots name themselves: Googlebot/2.1, bingbot/2.0, YandexBot/3.0, DuckDuckBot-Https/1.1, a "+http" link to
    // their page, HeadlessChrome; and the tools scripts are written with. "bot" counts only right before one of
    // "/-_;)" or at the end, so that a phone named "CUBOT X30" isn't one.
    private static final Pattern ROBOT = Pattern.compile(
            "bot[/\\-_;)]|bot$|\\+https?://|crawl|spider|slurp|headless|lighthouse|facebookexternalhit|curl/|wget/|"
                    + "python|java/|go-http-client|okhttp|axios|node-fetch|puppeteer|playwright|selenium|phantomjs",
            Pattern.CASE_INSENSITIVE);

    private final String tag;

    Source(String tag) {
        this.tag = tag;
    }

    /**
     * The value of the {@code source} tag of the metric.
     */
    public String tag() {
        return tag;
    }

    /**
     * The source of a page opened by a link with the {@code from} parameter, or, without one, from the referring
     * page. Both may be absent, which is a direct visit.
     */
    public static Source of(String from, String referrer) {
        if(from != null && !from.isBlank()) {
            return BY_TAG.getOrDefault(from.strip().toLowerCase(Locale.ROOT), OTHER_LINK);
        }
        if(referrer == null || referrer.isBlank()) {
            return DIRECT;
        }
        String host = hostOf(referrer.strip());
        if(host == null) {
            return OTHER_SITE;
        }
        // The host itself and each parent domain: qa.pipoker.app is PiPoker, m.vk.com is VK
        for(String domain = host; domain != null; domain = parentOf(domain)) {
            Source source = BY_DOMAIN.get(domain);
            if(source != null) {
                return source;
            }
        }
        return OTHER_SITE;
    }

    public static Source of(VisitDto visit) {
        return visit == null ? DIRECT : of(visit.getFrom(), visit.getReferrer());
    }

    /**
     * The source of a visit reported by a browser with the given User-Agent: a robot is counted as {@link #BOT}
     * whatever link it followed, except PiPoker's own tests, which stay out of the count.
     */
    public static Source ofVisit(VisitDto visit, String userAgent) {
        Source source = of(visit);
        return source != TEST && isRobot(userAgent) ? BOT : source;
    }

    // A browser always names itself, so a visit without a name comes from a script
    static boolean isRobot(String userAgent) {
        return userAgent == null || userAgent.isBlank() || ROBOT.matcher(userAgent).find();
    }

    private static String hostOf(String referrer) {
        try {
            String host = URI.create(referrer).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        }
        catch(IllegalArgumentException e) {
            return null;
        }
    }

    private static String parentOf(String domain) {
        int dot = domain.indexOf('.');
        return dot < 0 ? null : domain.substring(dot + 1);
    }
}
