package com.rolliedev.ticketflow.http.handler;

import com.rolliedev.ticketflow.exception.BusinessRuleViolationException;
import com.rolliedev.ticketflow.exception.InvalidRequestException;
import com.rolliedev.ticketflow.exception.InvalidStatusTransitionException;
import com.rolliedev.ticketflow.exception.ResourceNotFoundException;
import com.rolliedev.ticketflow.exception.TicketFlowAccessDeniedException;
import com.rolliedev.ticketflow.exception.TicketFlowException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;
import java.util.Set;

@Slf4j
@ControllerAdvice(basePackages = "com.rolliedev.ticketflow.http.controller")
public class ControllerExceptionHandler {

    private static final Set<String> ALLOWED_REDIRECT_PREFIXES = Set.of(
            "/tickets",
            "/users",
            "/register",
            "/admin"
    );

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ModelAndView handleAuthorizationDeniedException(AuthorizationDeniedException ex) {
        log.warn("Authorization denied: {}", ex.getMessage());

        ModelAndView mv = new ModelAndView("error/403");
        mv.setStatus(HttpStatus.FORBIDDEN);
        mv.addObject("message", ex.getMessage());
        return mv;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public Object handleResourceNotFoundException(ResourceNotFoundException ex,
                                                  HttpServletRequest request,
                                                  RedirectAttributes ra) {
        log.warn(ex.getMessage());

        if (!isGetRequest(request)) {
            ra.addFlashAttribute("error", ex.getMessage());
            return redirectBackOrTickets(request);
        }

        ModelAndView mv = new ModelAndView("error/404");
        mv.setStatus(HttpStatus.NOT_FOUND);
        mv.addObject("message", ex.getMessage());
        return mv;
    }

    @ExceptionHandler({
            TicketFlowAccessDeniedException.class,
            BusinessRuleViolationException.class,
            InvalidStatusTransitionException.class,
            InvalidRequestException.class
    })
    public String handleOperationalException(TicketFlowException ex,
                                             HttpServletRequest request,
                                             RedirectAttributes ra) {
        log.warn("Business rule/access issue: {}", ex.getMessage());

        ra.addFlashAttribute("error", ex.getMessage());
        return redirectBackOrTickets(request);
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleUnexpectedException(Exception ex) {

        log.error("Unexpected exception", ex);

        ModelAndView mv = new ModelAndView("error/500");
        mv.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        mv.addObject("message", "An unexpected error occurred");
        return mv;
    }

    private boolean isGetRequest(HttpServletRequest request) {
        return HttpMethod.GET.name().equalsIgnoreCase(request.getMethod());
    }

    private String redirectBackOrTickets(HttpServletRequest request) {
        String referer = request.getHeader(HttpHeaders.REFERER);
        if (referer != null) {
            try {
                String path = URI.create(referer).getPath();
                boolean isSafePath = path != null && ALLOWED_REDIRECT_PREFIXES.stream()
                        .anyMatch(path::startsWith);
                if (isSafePath) {
                    return "redirect:" + path;
                }
            } catch (IllegalArgumentException ignoredEx) {
            }
        }
        return "redirect:/tickets";
    }
}
