package com.punith.chat.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.punith.chat.domain.chat.Chat;
import com.punith.chat.domain.chat.ChatParticipant;
import com.punith.chat.domain.message.Message;
import com.punith.chat.domain.user.User;
import com.punith.chat.messaging.ChatMessageEvent;
import com.punith.chat.messaging.ChatMessageProducer;
import com.punith.chat.messaging.ReadReceiptEvent;
import com.punith.chat.messaging.ReadReceiptProducer;
import com.punith.chat.service.ChatService;
import com.punith.chat.session.RedisSessionService;
import com.punith.chat.messaging.WsFanoutEvents.NewMessageFanoutEvent;
import com.punith.chat.messaging.WsFanoutEvents.ReadReceiptFanoutEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import com.punith.chat.messaging.WsFanoutEvents.MessageStatusFanoutEvent;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final ChatService chatService;
    private final ChatMessageProducer messageProducer;
    private final ObjectMapper objectMapper;
    private final ReadReceiptProducer readReceiptProducer;
    private final RedisSessionService redisSessionService;
    private final String nodeId;


    private final ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>> userSessions =
            new ConcurrentHashMap<>();

    public ChatWebSocketHandler( ChatService chatService,
                                 ChatMessageProducer messageProducer,
                                 ReadReceiptProducer readReceiptProducer,
                                 RedisSessionService redisSessionService,
                                ObjectMapper objectMapper,
                                 @Value("${ws.node-id:node-1}") String nodeId,
                                 MeterRegistry meterRegistry) {
        this.chatService = chatService;
        this.messageProducer = messageProducer;
        this.readReceiptProducer = readReceiptProducer;
        this.redisSessionService = redisSessionService;
        this.objectMapper = objectMapper;
        this.nodeId = nodeId;

        Gauge.builder("chat_ws_active_sessions", this, ChatWebSocketHandler::totalActiveSessions)
                .description("Number of active WebSocket sessions on this node")
                .tag("nodeId", nodeId)
                .register(meterRegistry);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userIdHeader = (String) session.getAttributes().get("X-User-Id");
        if (userIdHeader == null) {
            Object raw = session.getAttributes().get("userId");
            if (raw != null) {
                userIdHeader = raw.toString();
            }
        }

        if (userIdHeader == null) {
            log.warn("Missing X-User-Id, closing session");
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Missing X-User-Id"));
            return;
        }

        Long userId = Long.parseLong(userIdHeader);
        session.getAttributes().put("userId", userId);


        userSessions
                .computeIfAbsent(userId, id -> new java.util.concurrent.CopyOnWriteArraySet<>())
                .add(session);


        redisSessionService.registerSession(userId, session.getId(), nodeId);

        log.info("WebSocket connected: userId={}, session={}, nodeId={}", userId, session.getId(), nodeId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Object userIdObj = session.getAttributes().get("userId");
        if (userIdObj != null) {
            Long userId = (Long) userIdObj;


            java.util.Set<WebSocketSession> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    userSessions.remove(userId);
                }
            }


            redisSessionService.unregisterSession(userId, session.getId());

            log.info("WebSocket disconnected: userId={}, session={}, nodeId={}, status={}",
                    userId, session.getId(), nodeId, status);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Object userIdObj = session.getAttributes().get("userId");
        if (userIdObj == null) {
            sendError(session, "Not authenticated");
            return;
        }
        Long userId = (Long) userIdObj;

        WsInboundMessage inbound;
        try {
            inbound = objectMapper.readValue(message.getPayload(), WsInboundMessage.class);
        } catch (JsonProcessingException e) {
            sendError(session, "Invalid JSON");
            return;
        }

        if (inbound.type == null) {
            sendError(session, "Missing type");
            return;
        }

        switch (inbound.type) {
            case "SEND_MESSAGE" -> handleSendMessage(session, userId, inbound);
            case "READ_MESSAGES" -> handleReadMessages(session, userId, inbound);
            default -> sendError(session, "Unknown type: " + inbound.type);
        }
    }

    private void handleSendMessage(WebSocketSession session, Long userId, WsInboundMessage inbound) throws IOException {
        if (inbound.chatId == null || inbound.body == null) {
            sendError(session, "chatId and body are required");
            return;
        }

        try {
            chatService.getChatForUserOrThrow(inbound.chatId, userId);


            ChatMessageEvent event = new ChatMessageEvent(
                    inbound.chatId,
                    userId,
                    inbound.body,
                    inbound.clientMessageId,
                    System.currentTimeMillis()
            );


            messageProducer.sendMessageEvent(event);

            sendJson(session, new WsOutboundWrapper<>(
                    "MESSAGE_ACCEPTED",
                    new MessageAcceptedPayload(
                            inbound.clientMessageId
                    )
            ));



        } catch (IllegalArgumentException e) {
            sendError(session, e.getMessage());
        } catch (Exception e) {
            log.error("Error handling SEND_MESSAGE", e);
            sendError(session, "Internal error");
        }
    }

    private void handleReadMessages(WebSocketSession session, Long userId, WsInboundMessage inbound) throws IOException {
        if (inbound.chatId == null || inbound.messageIds == null || inbound.messageIds.isEmpty()) {
            sendError(session, "chatId and messageIds are required");
            return;
        }

        try {
            chatService.getChatForUserOrThrow(inbound.chatId, userId);

            ReadReceiptEvent event = new ReadReceiptEvent(
                    inbound.chatId,
                    userId,
                    inbound.messageIds,
                    System.currentTimeMillis()
            );

            readReceiptProducer.send(event);

            sendJson(session, new WsOutboundWrapper<>(
                    "READ_ACCEPTED",
                    new ReadAcceptedPayload(
                            inbound.chatId,
                            inbound.messageIds
                    )
            ));

        } catch (IllegalArgumentException e) {
            sendError(session, e.getMessage());
        } catch (Exception e) {
            log.error("Error handling READ_MESSAGES", e);
            sendError(session, "Internal error");
        }
    }

    public void broadcastNewMessage(Message msg) {
        Chat chat = msg.getChat();


        List<ChatParticipant> participantEntities =
                chatService.getParticipantsForChat(chat.getId());

        Set<User> participants = participantEntities.stream()
                .map(ChatParticipant::getUser)
                .collect(Collectors.toSet());

        NewMessagePayload payload = new NewMessagePayload(
                msg.getId(),
                chat.getId(),
                msg.getSender().getId(),
                msg.getBody(),
                msg.getCreatedAt().toString()
        );


        for (User user : participants) {
            Set<WebSocketSession> sessions = userSessions.get(user.getId());
            if (sessions == null) {
                continue;
            }
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    sendJsonSafe(session, new WsOutboundWrapper<>("NEW_MESSAGE", payload));
                }
            }
        }
    }

    public void broadcastReadReceipt(Long chatId,
                                     Long readerId,
                                     java.util.List<Long> messageIds,
                                     OffsetDateTime readAt) {

        java.util.List<com.punith.chat.domain.chat.ChatParticipant> participantEntities =
                chatService.getParticipantsForChat(chatId);

        ReadReceiptPayload payload = new ReadReceiptPayload(
                chatId,
                readerId,
                messageIds,
                readAt.toString()
        );

        for (com.punith.chat.domain.chat.ChatParticipant cp : participantEntities) {
            Long userId = cp.getUser().getId();
            java.util.Set<WebSocketSession> sessions = userSessions.get(userId);
            if (sessions == null) {
                continue;
            }
            for (WebSocketSession s : sessions) {
                if (s.isOpen()) {
                    sendJsonSafe(s, new WsOutboundWrapper<>("READ_RECEIPT", payload));
                }
            }
        }
    }


    public void broadcastNewMessageFanout(NewMessageFanoutEvent event) {
        NewMessagePayload payload = new NewMessagePayload(
                event.messageId(),
                event.chatId(),
                event.senderId(),
                event.body(),
                event.createdAtIso()
        );


        java.util.List<com.punith.chat.domain.chat.ChatParticipant> participantEntities =
                chatService.getParticipantsForChat(event.chatId());

        java.util.Set<Long> userIds = participantEntities.stream()
                .map(cp -> cp.getUser().getId())
                .collect(java.util.stream.Collectors.toSet());

        for (Long userId : userIds) {
            java.util.Set<org.springframework.web.socket.WebSocketSession> sessions = userSessions.get(userId);
            if (sessions == null) continue;

            for (org.springframework.web.socket.WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    sendJsonSafe(session, new WsOutboundWrapper<>("NEW_MESSAGE", payload));
                }
            }
        }
    }


    public void broadcastReadReceiptFanout(ReadReceiptFanoutEvent event) {
        ReadReceiptPayload payload = new ReadReceiptPayload(
                event.chatId(),
                event.readerId(),
                event.messageIds(),
                event.readAtIso()
        );

        java.util.List<com.punith.chat.domain.chat.ChatParticipant> participantEntities =
                chatService.getParticipantsForChat(event.chatId());

        for (com.punith.chat.domain.chat.ChatParticipant cp : participantEntities) {
            Long userId = cp.getUser().getId();
            java.util.Set<org.springframework.web.socket.WebSocketSession> sessions = userSessions.get(userId);
            if (sessions == null) continue;

            for (org.springframework.web.socket.WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    sendJsonSafe(session, new WsOutboundWrapper<>("READ_RECEIPT", payload));
                }
            }
        }
    }

    public void broadcastMessageStatusFanout(MessageStatusFanoutEvent event) {
        MessageStatusPayload payload = new MessageStatusPayload(
                event.messageId(),
                event.chatId(),
                event.status()
        );

        Long targetUserId = event.userId();

        java.util.Set<org.springframework.web.socket.WebSocketSession> sessions = userSessions.get(targetUserId);
        if (sessions == null) {
            return;
        }

        for (org.springframework.web.socket.WebSocketSession session : sessions) {
            if (session.isOpen()) {
                sendJsonSafe(session, new WsOutboundWrapper<>("MESSAGE_STATUS", payload));
            }
        }
    }
    public int totalActiveSessions() {
        int total = 0;
        for (java.util.Set<org.springframework.web.socket.WebSocketSession> sessions : userSessions.values()) {
            total += sessions.size();
        }
        return total;
    }


    private void sendError(WebSocketSession session, String error) throws IOException {
        sendJson(session, new WsOutboundMessage("ERROR", "{\"error\":\"" + error + "\"}"));
    }

    private void sendJson(WebSocketSession session, Object obj) throws IOException {
        String json = objectMapper.writeValueAsString(obj);
        session.sendMessage(new TextMessage(json));
    }

    private void sendJsonSafe(WebSocketSession session, Object obj) {
        try {
            sendJson(session, obj);
        } catch (IOException e) {
            log.warn("Failed to send WS message to session {}", session.getId(), e);
        }
    }

    public record WsOutboundMessage(
            String type,
            String payload
    ) {}

    public record WsOutboundWrapper<T>(
            String type,
            T payload
    ) {}

    public record NewMessagePayload(
            Long id,
            Long chatId,
            Long senderId,
            String body,
            String createdAt
    ) {}

    public record MessageAcceptedPayload(
            String clientMessageId
    ) {}

    public record ReadAcceptedPayload(
            Long chatId,
            java.util.List<Long> messageIds
    ) {}

    public record ReadReceiptPayload(
            Long chatId,
            Long readerId,
            java.util.List<Long> messageIds,
            String readAt
    ) {}
    public record MessageStatusPayload(
            Long messageId,
            Long chatId,
            String status
    ) {}

}
