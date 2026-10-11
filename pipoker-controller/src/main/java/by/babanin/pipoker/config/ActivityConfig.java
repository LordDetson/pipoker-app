package by.babanin.pipoker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import by.babanin.pipoker.activity.RoomActivity;
import by.babanin.pipoker.presence.RoomPresence;
import by.babanin.pipoker.service.RoomService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;

@Configuration
public class ActivityConfig {

    // What is going on right now, read when Prometheus scrapes the metrics. What people do is counted by RoomActivity.
    // The rooms of checks and tests are left out, like everything that happens in them.
    @Bean
    MeterBinder currentActivity(RoomService roomService, RoomPresence roomPresence, RoomActivity activity) {
        return registry -> {
            Gauge.builder("pipoker.rooms", () -> Math.max(0, roomService.count() - activity.testRoomCount()))
                    .description("Rooms with someone in them, including people who may still come back")
                    .register(registry);
            Gauge.builder("pipoker.people.online", roomPresence, RoomPresence::peopleOnline)
                    .description("People at the table with an open connection")
                    .register(registry);
        };
    }
}
