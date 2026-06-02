package com.rolliedev.ticketflow.aop;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class CommonPointcuts {

    @Pointcut("within(com.rolliedev.ticketflow.service..*Service)")
    public void isServiceLayer() {
    }

    @Pointcut("execution(public * *(..))")
    public void isPublicMethod() {
    }

    @Pointcut("isServiceLayer() && isPublicMethod()")
    public void isPublicServiceMethod() {
    }
}
