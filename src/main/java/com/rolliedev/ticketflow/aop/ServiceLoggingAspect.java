package com.rolliedev.ticketflow.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class ServiceLoggingAspect {

    @Around("com.rolliedev.ticketflow.aop.CommonPointcuts.isPublicServiceMethod()")
    public Object logServiceExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String operation = className + "." + methodName;

        long startTime = System.nanoTime();

        log.debug("Entering {}", operation);

        try {
            Object result = joinPoint.proceed();

            long durationMs = elapsedMillis(startTime);
            log.info("Completed {} in {} ms", operation, durationMs);

            return result;
        } catch (Throwable ex) {
            long durationMs = elapsedMillis(startTime);

            log.warn("Failed {} after {} ms with {}: {}",
                    operation,
                    durationMs,
                    ex.getClass().getSimpleName(),
                    ex.getMessage());

            throw ex;
        }
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
