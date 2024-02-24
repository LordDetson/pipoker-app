package by.babanin.pipoker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${broker.stomp.destination.prefixes:/topic}")
    private String[] stompBrokerDestinationPrefixes;

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
        registry.setApplicationDestinationPrefixes(brokerAppDestinationPrefixes)
                .enableStompBrokerRelay(stompBrokerDestinationPrefixes)
                .setRelayHost(brokerHost)
                .setRelayPort(stompBrokerPort)
                .setSystemLogin(stompBrokerSystemLogin)
                .setSystemPasscode(stompBrokerSystemPasscode)
                .setClientLogin(stompBrokerClientLogin)
                .setClientPasscode(stompBrokerClientPasscode);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(stompEndpoints)
                .setAllowedOriginPatterns(allowedOriginPatterns)
                .withSockJS();
    }
}
