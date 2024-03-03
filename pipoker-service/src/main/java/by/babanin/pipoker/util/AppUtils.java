package by.babanin.pipoker.util;

import java.util.Collection;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.experimental.UtilityClass;

@UtilityClass
public class AppUtils {

    private static final String MESSAGE_FORMAT = "%s#%s - %s";

    public static <T, E extends RuntimeException> void validateAndThrow(Validator validator, T object, Function<String, E> exceptionFactory) {
        validateAndThrow(validator, object, exceptionFactory, null);
    }

    public static <T, E extends RuntimeException> void validateAndThrow(Validator validator, T object, Function<String, E> exceptionFactory, Runnable doBeforeThrow) {
        Set<ConstraintViolation<T>> violations = validator.validate(object);
        if(CollectionUtils.isNotEmpty(violations)) {
            if(doBeforeThrow != null) {
                doBeforeThrow.run();
            }
            throwException(violations, exceptionFactory);
        }
    }

    public static <T, E extends RuntimeException> void throwException(Collection<? extends ConstraintViolation<T>> violations, Function<String, E> exceptionFactory) {
        String message = violations.stream()
                .map(AppUtils::convertToMessage)
                .collect(Collectors.joining("\n"));
        if(StringUtils.isNotBlank(message)) {
            throw exceptionFactory.apply(message);
        }
        else if(CollectionUtils.isNotEmpty(violations)) {
            throw exceptionFactory.apply("WARNING! There are violations, but the message is blank");
        }
    }

    private static <T> String convertToMessage(ConstraintViolation<T> violation) {
        return String.format(MESSAGE_FORMAT,
                violation.getRootBeanClass().getSimpleName(),
                violation.getPropertyPath().toString(),
                violation.getMessage());
    }
}
