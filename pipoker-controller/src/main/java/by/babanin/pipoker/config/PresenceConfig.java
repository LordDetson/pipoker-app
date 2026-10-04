package by.babanin.pipoker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class PresenceConfig {

    // Runs the delayed leaving of people whose connection is lost (see RoomPresence) and the closing of idle rooms
    // (see IdleRoomCloser). As a bean it is started and shut down by Spring together with the application.
    @Bean
    ThreadPoolTaskScheduler roomPresenceScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("room-presence-");
        return scheduler;
    }
}
