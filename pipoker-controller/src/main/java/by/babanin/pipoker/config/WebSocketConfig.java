package by.babanin.pipoker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import by.babanin.pipoker.PiPokerApplication;

@Configuration
@Profile({ "prod", "dev", "debug" })
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${broker.host:localhost}")
    private String brokerHost;

    @Value("${broker.stomp.port:61613}")
    private int stompBrokerPort;

    @Value("${broker.stomp.system.login}")
    private String stompBrokerSystemLogin;

    @Value("${broker.stomp.system.pass}")
    private String stompBrokerSystemPasscode;

    @Value("${broker.stomp.user.login}")
    private String stompBrokerClientLogin;

    @Value("${broker.stomp.user.pass}")
    private String stompBrokerClientPasscode;

    @Value("${broker.app.destination.prefixes:/app}")
    private String[] brokerAppDestinationPrefixes;

    @Value("${broker.stomp.endpoint.paths:/ws}")
    private String[] stompEndpoints;

    @Value("${broker.stomp.endpoint.allowedOriginPatterns:*}")
    private String[] allowedOriginPatterns;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // A page hears the events of its room in the order they happened. Otherwise the messages RabbitMQ sends
        // one after another go out to the browser on different threads, and a page can hear that someone became
        // a watcher before it hears that their vote was taken back.
        registry.setPreservePublishOrder(true);
        registry.setApplicationDestinationPrefixes(brokerAppDestinationPrefixes)
                .enableStompBrokerRelay(PiPokerApplication.TOPIC_DESTINATION_PREFIX)
                .setRelayHost(brokerHost)
                .setRelayPort(stompBrokerPort)
                .setSystemLogin(stompBrokerSystemLogin)
                .setSystemPasscode(stompBrokerSystemPasscode)
                .setClientLogin(stompBrokerClientLogin)
                .setClientPasscode(stompBrokerClientPasscode);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Frames of one connection are handled one after another, in the order they came. Otherwise they run on
        // different threads, and an UNSUBSCRIBE sent right after its SUBSCRIBE can reach RabbitMQ first. RabbitMQ
        // answers it with ERROR, and Spring closes the browser's connection after any ERROR.
        registry.setPreserveReceiveOrder(true);
        registry.addEndpoint(stompEndpoints)
                .setAllowedOriginPatterns(allowedOriginPatterns)
                .withSockJS();
    }
}
