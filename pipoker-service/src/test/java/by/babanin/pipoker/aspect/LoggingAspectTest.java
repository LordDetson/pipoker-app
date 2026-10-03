package by.babanin.pipoker.aspect;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import by.babanin.pipoker.entity.Deck;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.exception.RoomServiceException;
import by.babanin.pipoker.repository.RoomRepository;
import by.babanin.pipoker.service.RoomService;
import jakarta.validation.Validation;

class LoggingAspectTest {

    private RoomRepository roomRepository;
    private RoomService proxy;

    @BeforeEach
    void setup() {
        roomRepository = Mockito.mock(RoomRepository.class);
        AspectJProxyFactory factory = new AspectJProxyFactory(
                new RoomService(roomRepository, Validation.buildDefaultValidatorFactory().getValidator()));
        factory.setProxyTargetClass(true);
        factory.addAspect(new LoggingAspect());
        proxy = factory.getProxy();
    }

    @Test
    @DisplayName("Logged service call returns the result of the service")
    void returnResult() {
        UUID roomId = UUID.randomUUID();
        Room room = new Room("test", new Deck());
        Mockito.when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        assertEquals(room, proxy.get(roomId));
    }

    @Test
    @DisplayName("Logged service call rethrows the exception of the service")
    void rethrowException() {
        UUID roomId = UUID.randomUUID();
        Mockito.when(roomRepository.findById(roomId)).thenReturn(Optional.empty());

        assertThrows(RoomServiceException.class, () -> proxy.get(roomId));
    }

    @Test
    @DisplayName("Duration formatting")
    void durationToString() {
        LoggingAspect aspect = new LoggingAspect();

        assertAll(
                () -> assertEquals("00:00:00.000 (0ms)", aspect.durationToString(0)),
                () -> assertEquals("00:00:01.234 (1234ms)", aspect.durationToString(1234)),
                () -> assertEquals("01:01:01.001 (3661001ms)", aspect.durationToString(3_661_001))
        );
    }
}
