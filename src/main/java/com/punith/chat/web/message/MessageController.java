package com.punith.chat.web.message;

import com.punith.chat.domain.message.Message;
import com.punith.chat.service.MessageService;
import com.punith.chat.web.message.dto.MarkReadRequest;
import com.punith.chat.web.message.dto.MessageResponse;
import com.punith.chat.web.message.dto.SendMessageRequest;
import com.punith.chat.web.message.dto.UnreadMessageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chats/{chatId}/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    private Long getCurrentUserId(String header) {
        if (header == null) {
            throw new IllegalArgumentException("X-User-Id header is required");
        }
        return Long.parseLong(header);
    }

    @PostMapping
    public ResponseEntity<MessageResponse> sendMessage(
            @RequestHeader("X-User-Id") String userIdHeader,
            @PathVariable Long chatId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        Long currentUserId = getCurrentUserId(userIdHeader);
        Message message = messageService.sendMessage(
                currentUserId,
                chatId,
                request.body(),
                request.clientMessageId()
        );

        MessageResponse response = new MessageResponse(
                message.getId(),
                message.getChat().getId(),
                message.getSender().getId(),
                message.getBody(),
                message.getCreatedAt()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<MessageResponse>> getMessages(
            @RequestHeader("X-User-Id") String userIdHeader,
            @PathVariable Long chatId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        Long currentUserId = getCurrentUserId(userIdHeader);

        List<Message> messages = messageService.getMessages(currentUserId, chatId, limit);

        List<MessageResponse> responses = messages.stream()
                .map(m -> new MessageResponse(
                        m.getId(),
                        m.getChat().getId(),
                        m.getSender().getId(),
                        m.getBody(),
                        m.getCreatedAt()
                ))
                .toList();

        return ResponseEntity.ok(responses);
    }

    @PostMapping("/read")
    public ResponseEntity<Void> markRead(
            @RequestHeader("X-User-Id") String userIdHeader,
            @PathVariable Long chatId,
            @Valid @RequestBody MarkReadRequest request
    ) {
        Long currentUserId = getCurrentUserId(userIdHeader);
        messageService.markMessagesAsRead(currentUserId, chatId, request.messageIds());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/unread")
    public ResponseEntity<List<UnreadMessageResponse>> getUnreadMessages(
            @RequestHeader("X-User-Id") String userIdHeader,
            @PathVariable Long chatId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        Long currentUserId = getCurrentUserId(userIdHeader);
        List<UnreadMessageResponse> unread = messageService.getUnreadMessages(currentUserId, chatId, limit);
        return ResponseEntity.ok(unread);
    }
}
