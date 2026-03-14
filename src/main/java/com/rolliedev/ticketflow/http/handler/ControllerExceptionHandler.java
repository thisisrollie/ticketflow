package com.rolliedev.ticketflow.http.handler;

import com.rolliedev.ticketflow.exception.AccessDeniedException;
import com.rolliedev.ticketflow.exception.BusinessRuleViolationException;
import com.rolliedev.ticketflow.exception.InvalidStatusTransitionException;
import com.rolliedev.ticketflow.exception.ResourceNotFoundException;
import com.rolliedev.ticketflow.exception.TicketFlowException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@ControllerAdvice
public class ControllerExceptionHandler {

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
            AccessDeniedException.class,
            BusinessRuleViolationException.class,
            InvalidStatusTransitionException.class
    })
    public String handleBusinessException(TicketFlowException ex,
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
        String referer = request.getHeader("Referer");

        if (referer != null && referer.contains("/tickets")) {
            return "redirect:" + referer;
        }
        return "redirect:/tickets";
    }
}
