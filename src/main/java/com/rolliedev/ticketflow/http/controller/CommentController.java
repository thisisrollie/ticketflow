package com.rolliedev.ticketflow.http.controller;

import com.rolliedev.ticketflow.dto.ActorCommand;
import com.rolliedev.ticketflow.dto.CreateCommentRequest;
import com.rolliedev.ticketflow.service.CommentService;
import lombok.RequiredArgsConstructor;
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
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errors", bindingResult.getAllErrors());
        } else {
            commentService.create(ticketId, comment.getAuthorId(), comment.getText());
        }
        return "redirect:/tickets/" + ticketId;
    }

    @PostMapping("/{commentId}/delete")
    public String delete(@PathVariable Long ticketId,
                         @PathVariable Long commentId,
                         @ModelAttribute @Validated ActorCommand cmd,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errors", bindingResult.getAllErrors());
        } else {
            commentService.delete(ticketId, commentId, cmd.getActorId());
        }
        return "redirect:/tickets/" + ticketId;
    }
}
