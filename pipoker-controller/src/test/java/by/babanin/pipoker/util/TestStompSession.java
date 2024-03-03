package by.babanin.pipoker.util;

import java.lang.reflect.Type;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import lombok.Getter;

@Getter
public class TestStompSession<T> {

    private final StompSession session;
    private final Queue<T> results;

    public TestStompSession(StompSession session, Queue<T> results) {
        this.session = session;
        this.results = results;
    }

    public Queue<T> send(String destination, Object payload) {
        session.send(destination, payload);
        return results;
    }

    public static <T> TestStompSessionBuilder<T> builder() {
        return new TestStompSessionBuilder<>();
    }

    public static class TestStompSessionBuilder<T> {

        private WebSocketStompClient stompClient;
        private String brokerUrl;
        private String destination;
        private long timeout = 1;
        private TimeUnit timeUnit = TimeUnit.SECONDS;
        private int resultCapacity = 1;
        private Class<T> resultType;

        public TestStompSessionBuilder<T> stompClient(WebSocketStompClient stompClient) {
            this.stompClient = stompClient;
            return this;
        }

        public TestStompSessionBuilder<T> brokerUrl(String brokerUrl) {
            this.brokerUrl = brokerUrl;
            return this;
        }

        public TestStompSessionBuilder<T> destination(String destination) {
            this.destination = destination;
            return this;
        }

        public TestStompSessionBuilder<T> timeout(long timeout) {
            this.timeout = timeout;
            return this;
        }

        public TestStompSessionBuilder<T> timeUnit(TimeUnit timeUnit) {
            this.timeUnit = timeUnit;
            return this;
        }

        public TestStompSessionBuilder<T> resultCapacity(int resultCapacity) {
            this.resultCapacity = resultCapacity;
            return this;
        }

        public TestStompSessionBuilder<T> resultType(Class<T> resultType) {
            this.resultType = resultType;
            return this;
        }

        public TestStompSession<T> build() throws ExecutionException, InterruptedException, TimeoutException {
            StompSession stompSession = stompClient.connectAsync(brokerUrl, new StompSessionHandlerAdapter() {})
                    .get(timeout, timeUnit);
            Queue<T> results = new ArrayBlockingQueue<>(1);
            stompSession.subscribe(destination, new ResultAccumulator<>(resultType, results));
            return new TestStompSession<>(stompSession, results);
        }
    }

    private record ResultAccumulator<T>(Class<T> resultType, Queue<T> results) implements StompFrameHandler {

        @Override
            public Type getPayloadType(StompHeaders headers) {
                return resultType;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                results.add((T) payload);
            }
        }
}
