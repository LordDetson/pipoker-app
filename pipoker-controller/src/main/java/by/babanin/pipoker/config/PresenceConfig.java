package by.babanin.pipoker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class PresenceConfig {

    // Runs the delayed leaving of people whose connection is lost (see RoomPresence) and the closing of idle rooms
    // (see IdleRoomCloser). As a bean it is started and shut down by Spring together with the application.
    // With one thread, someone who closes the page would wait behind everyone else leaving at that moment, for example
    // a whole team whose connections were lost together, and would stay at the table for a while.
    @Bean
    ThreadPoolTaskScheduler roomPresenceScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("room-presence-");
        return scheduler;
    }
}
