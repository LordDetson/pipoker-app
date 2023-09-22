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

    @Value("${app.broker.destinations:/topic}")
    private String[] stompBrokerDestinationPrefixes;

    @Value("${app.broker.host:localhost}")
    private String stompBrokerHost;

    @Value("${app.broker.port:61613}")
    private int stompBrokerPort;

    @Value("${app.broker.login}")
    private String brokerClientLogin;

    @Value("${app.broker.pass}")
    private String brokerClientPasscode;

    @Value("${app.destinations:/app}")
    private String appDestinationPrefixes;

    @Value("${app.stomp.endpoint.paths:/ws}")
    private String[] applicationDestinationPrefixes;

    @Value("${app.stomp.endpoint.allowedOriginPatterns:*}")
    private String[] allowedOriginPatterns;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableStompBrokerRelay(stompBrokerDestinationPrefixes)
                .setRelayHost(stompBrokerHost)
                .setRelayPort(stompBrokerPort)
                .setClientLogin(brokerClientLogin)
                .setClientPasscode(brokerClientPasscode)
                .setSystemLogin(brokerClientLogin)
                .setSystemPasscode(brokerClientPasscode);
        registry.setApplicationDestinationPrefixes(appDestinationPrefixes);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(applicationDestinationPrefixes)
                .setAllowedOriginPatterns(allowedOriginPatterns)
                .withSockJS();
    }
}
