package com.rolliedev.ticketflow.http.controller;

import com.rolliedev.ticketflow.dto.CreateCommentRequest;
import com.rolliedev.ticketflow.security.TicketFlowUserDetails;
import com.rolliedev.ticketflow.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/tickets/{ticketId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    public String create(@PathVariable Long ticketId,
                         @ModelAttribute @Validated CreateCommentRequest comment,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes,
                         @AuthenticationPrincipal TicketFlowUserDetails currentUser) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errors", bindingResult.getAllErrors());
        } else {
            commentService.create(ticketId, currentUser.getId(), comment.getText());
        }
        return "redirect:/tickets/" + ticketId;
    }

    @PostMapping("/{commentId}/delete")
    public String delete(@PathVariable Long ticketId,
                         @PathVariable Long commentId,
                         @AuthenticationPrincipal TicketFlowUserDetails currentUser) {
        commentService.delete(ticketId, commentId, currentUser.getId());
        return "redirect:/tickets/" + ticketId;
    }
}
