package by.babanin.pipoker.activity;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

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
    BOOSTY("boosty"),
    ALTERNATIVETO("alternativeto"),
    SAASHUB("saashub"),
    PRODUCT_HUNT("producthunt"),
    VIDEO("video"),
    GITHUB("github"),
    YANDEX("yandex"),
    GOOGLE("google"),
    BING("bing"),
    DUCKDUCKGO("duckduckgo"),
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
            Map.entry(BOOSTY.tag, BOOSTY),
            Map.entry(ALTERNATIVETO.tag, ALTERNATIVETO),
            Map.entry(SAASHUB.tag, SAASHUB),
            Map.entry(PRODUCT_HUNT.tag, PRODUCT_HUNT),
            Map.entry(VIDEO.tag, VIDEO),
            Map.entry(GITHUB.tag, GITHUB));

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
            Map.entry("boosty.to", BOOSTY),
            Map.entry("alternativeto.net", ALTERNATIVETO),
            Map.entry("saashub.com", SAASHUB),
            Map.entry("producthunt.com", PRODUCT_HUNT),
            Map.entry("youtube.com", VIDEO),
            Map.entry("youtu.be", VIDEO),
            Map.entry("vkvideo.ru", VIDEO),
            Map.entry("rutube.ru", VIDEO),
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
