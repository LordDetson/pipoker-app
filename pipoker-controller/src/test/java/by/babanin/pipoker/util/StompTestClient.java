package by.babanin.pipoker.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Type;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSession.Subscription;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Talks to the application the way the web client does: STOMP over SockJS,
 * strings sent as plain text and objects as JSON.
 */
public class StompTestClient implements AutoCloseable {

    private static final long TIMEOUT_SECONDS = 10;

    private final WebSocketStompClient client;
    private final StompSession session;

    public StompTestClient(String url, ObjectMapper objectMapper) throws Exception {
        client = new WebSocketStompClient(new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        client.setMessageConverter(new CompositeMessageConverter(List.of(
                new StringMessageConverter(),
                new MappingJackson2MessageConverter(objectMapper))));
        // Needed to wait for the broker's receipts
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.initialize();
        client.setTaskScheduler(taskScheduler);
        session = client.connectAsync(url, new StompSessionHandlerAdapter() {})
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Subscribes to a broker destination and waits until the broker confirms the subscription,
     * so that no message sent afterwards is missed.
     */
    public <T> BlockingQueue<T> subscribe(String destination, Class<T> type) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.setDestination(destination);
        headers.setReceipt(UUID.randomUUID().toString());
        BlockingQueue<T> messages = new LinkedBlockingQueue<>();
        Subscription subscription = session.subscribe(headers, new QueueFrameHandler<>(type, messages));
        CompletableFuture<Void> receipt = new CompletableFuture<>();
        subscription.addReceiptTask(() -> receipt.complete(null));
        receipt.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return messages;
    }

    /**
     * Subscribes to an application destination that answers once, like SUBSCRIBE /app/room/{id}.
     * The application doesn't confirm such subscriptions, so there is nothing to wait for.
     */
    public <T> BlockingQueue<T> subscribeToApplication(String destination, Class<T> type) {
        BlockingQueue<T> messages = new LinkedBlockingQueue<>();
        session.subscribe(destination, new QueueFrameHandler<>(type, messages));
        return messages;
    }

    /**
     * Subscribes and unsubscribes at once, without waiting for the broker, like the web client does when it gives up
     * on a subscription it has just made.
     */
    public void subscribeAndUnsubscribe(String destination) {
        session.subscribe(destination, new QueueFrameHandler<>(Object.class, new LinkedBlockingQueue<>())).unsubscribe();
    }

    public boolean isConnected() {
        return session.isConnected();
    }

    public <T> T request(String destination, Class<T> type) throws InterruptedException {
        return next(subscribeToApplication(destination, type));
    }

    public void send(String destination, Object payload) {
        session.send(destination, payload);
    }

    public static <T> T next(BlockingQueue<T> messages) throws InterruptedException {
        T message = messages.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(message, "No message received");
        return message;
    }

    public static void assertNoMessage(BlockingQueue<?> messages) throws InterruptedException {
        assertNull(messages.poll(1, TimeUnit.SECONDS), "Unexpected message received");
    }

    @Override
    public void close() {
        if(session.isConnected()) {
            session.disconnect();
        }
        client.stop();
        ((ThreadPoolTaskScheduler) client.getTaskScheduler()).shutdown();
    }

    private record QueueFrameHandler<T>(Class<T> type, BlockingQueue<T> messages) implements StompFrameHandler {

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return type;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            messages.add(type.cast(payload));
        }
    }
}
