package com.example.server;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class CommentsController {
    private final DocumentService service;

    public CommentsController(DocumentService service) {
        this.service = service;
    }

    @MessageMapping("/comments/{roomId}")
    @SendTo("/topic/comments/{roomId}")
    public List<Comment> handleCommentAdd(@DestinationVariable String roomId, Comment comment) {
        String editorCode = service.geteditorcode(roomId);
        service.addComment(editorCode, comment);
        List<Comment> comments = service.getComments(editorCode);
        for (Comment c : comments) {
            System.out.println("Comment: " + c.getText() + " by " + c.getAuthor());
        }
        return comments;
    }

    @MessageMapping("/comments/remove/{roomId}")
    @SendTo("/topic/comments/remove/{roomId}")
    public String handleCommentRemove(@DestinationVariable String roomId, String commentId) {
        String editorCode = service.geteditorcode(roomId);
        service.removeComment(editorCode, commentId);
        System.out.println("Removed comment: " + commentId);
        return commentId;
    }
    @MessageMapping("/comments/update/{roomId}")
    @SendTo("/topic/comments/{roomId}")
    public List<Comment> handleCommentUpdate(@DestinationVariable String roomId, Comment comment) {
        String editorCode = service.geteditorcode(roomId);
        List<Comment> comments = service.getComments(editorCode);
        // Find and update the existing comment
        comments.replaceAll(c -> c.getId().equals(comment.getId()) ? comment : c);
        service.updateComments(editorCode, comments);
        return comments;
}
}
