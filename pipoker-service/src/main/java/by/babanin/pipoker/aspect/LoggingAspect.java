package by.babanin.pipoker.aspect;

import java.util.Arrays;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import lombok.extern.log4j.Log4j2;

@Component
@Aspect
@Log4j2
public class LoggingAspect {

    private static final int MAX_LENGTH = 2048;
    private static final int MAX_COUNT_OF_TIME = 60;

    @Pointcut("within(by.babanin.pipoker.service.*)")
    public void servicePackage() {}

    @Pointcut("execution(public * *(..))")
    private void anyPublicOperation() {}

    @Around("servicePackage() && anyPublicOperation()")
    public Object logAroundServices(ProceedingJoinPoint joinPoint) throws Throwable {
        String returnTypeName = "";
        if(joinPoint.getSignature() instanceof MethodSignature methodSignature) {
            returnTypeName = methodSignature.getReturnType().getSimpleName();
        }
        String withinTypeName = joinPoint.getSourceLocation().getWithinType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String argsString = StringUtils.left(Arrays.toString(joinPoint.getArgs()), MAX_LENGTH);
        log.debug("Started {} {}.{}() with arguments = {}", returnTypeName, withinTypeName, methodName, argsString);

        try {
            long start = System.currentTimeMillis();
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;
            String durationString = durationToString(duration);
            String resultString = StringUtils.left(Objects.toString(result), MAX_LENGTH);
            log.debug("Finished {} {}.{}() - {} with result = {}", returnTypeName, withinTypeName, methodName, durationString,
                    resultString);
            return result;
        }
        catch(Throwable throwable) {
            String exceptionName = throwable.getClass().getSimpleName();
            String message = throwable.getMessage();
            log.error("Method {} {}.{}() threw {}: {} with arguments = {}", returnTypeName, withinTypeName,
                    methodName, exceptionName, message, argsString);
            throw throwable;
        }
    }

    public String durationToString(long milliseconds) {
        int millis = (int) (milliseconds % 1000);
        int remainingSeconds = (int) (milliseconds / 1000);
        int hours = (remainingSeconds / (MAX_COUNT_OF_TIME * MAX_COUNT_OF_TIME));
        remainingSeconds = remainingSeconds - (hours * (MAX_COUNT_OF_TIME * MAX_COUNT_OF_TIME));
        int minutes = (remainingSeconds / (MAX_COUNT_OF_TIME));
        remainingSeconds = remainingSeconds - (minutes * MAX_COUNT_OF_TIME);
        int seconds = remainingSeconds;
        return String.format("%02d:%02d:%02d.%03d (%dms)", hours, minutes, seconds, millis, milliseconds);
    }
}
