package by.babanin.pipoker.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.exception.RoomServiceException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class AppUtilsTest {

    private static Validator validator;

    @BeforeAll
    static void setup() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("Valid object passes without side effects")
    void validObject() {
        AtomicBoolean called = new AtomicBoolean();

        assertDoesNotThrow(() -> AppUtils.validateAndThrow(validator, Participant.createParticipant("Dmitry"),
                RoomServiceException::new, () -> called.set(true)));
        assertFalse(called.get());
    }

    @Test
    @DisplayName("Invalid object throws an exception describing the violation")
    void invalidObject() {
        Participant participant = Participant.createParticipant("a");

        RoomServiceException exception = assertThrows(RoomServiceException.class,
                () -> AppUtils.validateAndThrow(validator, participant, RoomServiceException::new));

        assertEquals("Participant#nickname - size must be between 2 and 32", exception.getMessage());
    }

    @Test
    @DisplayName("Rollback runs before the exception is thrown")
    void rollbackBeforeThrow() {
        AtomicBoolean called = new AtomicBoolean();

        assertThrows(RoomServiceException.class, () -> AppUtils.validateAndThrow(validator,
                Participant.createParticipant("a"), RoomServiceException::new, () -> called.set(true)));
        assertTrue(called.get());
    }

    @Test
    @DisplayName("No violations, no exception")
    void noViolations() {
        assertDoesNotThrow(() -> AppUtils.throwException(List.of(), RoomServiceException::new));
    }

    @Test
    @DisplayName("Violation without a message still throws")
    @SuppressWarnings("unchecked")
    void blankViolationMessage() {
        ConstraintViolation<Object> violation = Mockito.mock(ConstraintViolation.class);
        Path path = Mockito.mock(Path.class);
        Mockito.when(violation.getRootBeanClass()).thenReturn(Object.class);
        Mockito.when(violation.getPropertyPath()).thenReturn(path);
        Mockito.when(path.toString()).thenReturn("");
        Mockito.when(violation.getMessage()).thenReturn("");

        RoomServiceException exception = assertThrows(RoomServiceException.class,
                () -> AppUtils.throwException(List.of(violation), RoomServiceException::new));

        assertEquals("Object# - ", exception.getMessage());
    }
}
