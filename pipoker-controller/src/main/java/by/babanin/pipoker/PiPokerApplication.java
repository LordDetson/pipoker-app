package by.babanin.pipoker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PiPokerApplication {

    public static final String TOPIC_DESTINATION_PREFIX = "/topic";
    public static final String ROOM_DESTINATION_PREFIX = "/room";
    public static final String TOPIC_ROOM_DESTINATION_PREFIX = TOPIC_DESTINATION_PREFIX + ROOM_DESTINATION_PREFIX;

    public static void main(String[] args) {
        SpringApplication.run(PiPokerApplication.class, args);
    }

}
