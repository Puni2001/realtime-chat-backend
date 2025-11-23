package com.punith.chat.web.chat;

import com.punith.chat.domain.chat.Chat;
import com.punith.chat.domain.chat.ChatParticipant;
import com.punith.chat.service.ChatService;
import com.punith.chat.service.MessageService;
import com.punith.chat.web.chat.dto.ChatResponse;
import com.punith.chat.web.chat.dto.ChatSummaryResponse;
import com.punith.chat.web.chat.dto.CreateChatRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/chats")
public class ChatController {

    private final ChatService chatService;
    private final MessageService messageService;

    public ChatController(ChatService chatService, MessageService messageService) {
        this.chatService = chatService;
        this.messageService = messageService;

    }

    private Long getCurrentUserId(String header) {
        if (header == null) {
            throw new IllegalArgumentException("X-User-Id header is required");
        }
        return Long.parseLong(header);
    }

    @PostMapping
    public ResponseEntity<ChatResponse> createChat(
            @RequestHeader("X-User-Id") String userIdHeader,
            @Valid @RequestBody CreateChatRequest request
    ) {
        Long currentUserId = getCurrentUserId(userIdHeader);

        Chat chat = chatService.createChat(
                currentUserId,
                request.isGroup(),
                request.title(),
                request.participantIds() == null ? List.of() : request.participantIds()
        );

        List<Long> participantIds = chatService.getChatsForUser(currentUserId).stream()
                .filter(cp -> cp.getChat().getId().equals(chat.getId()))
                .map(cp -> cp.getUser().getId())
                .collect(Collectors.toList());

        ChatResponse response = new ChatResponse(
                chat.getId(),
                chat.isGroup(),
                chat.getTitle(),
                participantIds
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<ChatResponse>> listMyChats(
            @RequestHeader("X-User-Id") String userIdHeader
    ) {
        Long currentUserId = getCurrentUserId(userIdHeader);

        List<ChatParticipant> parts = chatService.getChatsForUser(currentUserId);

        List<ChatResponse> responses = parts.stream()
                .collect(Collectors.groupingBy(ChatParticipant::getChat))
                .entrySet().stream()
                .map(entry -> {
                    Chat chat = entry.getKey();
                    List<Long> userIds = entry.getValue().stream()
                            .map(cp -> cp.getUser().getId())
                            .collect(Collectors.toList());
                    return new ChatResponse(chat.getId(), chat.isGroup(), chat.getTitle(), userIds);
                })
                .toList();

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/summary")
    public ResponseEntity<List<ChatSummaryResponse>> getChatSummaries(
            @RequestHeader("X-User-Id") String userIdHeader
    ) {
        Long currentUserId = getCurrentUserId(userIdHeader);
        List<ChatSummaryResponse> summaries = messageService.getChatSummaries(currentUserId);
        return ResponseEntity.ok(summaries);
    }
}
