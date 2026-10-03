package by.babanin.pipoker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PiPokerApplication {

    public static final String TOPIC_DESTINATION_PREFIX = "/topic";
    public static final String ROOM_DESTINATION_PREFIX = "/room";
    public static final String TOPIC_ROOM_DESTINATION_PREFIX = TOPIC_DESTINATION_PREFIX + ROOM_DESTINATION_PREFIX;
    public static final String USER_DESTINATION_PREFIX = "/user";
    public static final String TOPIC_ROOM_CREATED_DESTINATION = TOPIC_ROOM_DESTINATION_PREFIX + ".created";
    public static final String TOPIC_ROOM_ERRORS_DESTINATION = TOPIC_ROOM_DESTINATION_PREFIX + ".errors";

    public static void main(String[] args) {
        SpringApplication.run(PiPokerApplication.class, args);
    }

}
