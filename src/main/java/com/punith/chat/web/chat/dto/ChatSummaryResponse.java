package com.punith.chat.web.chat.dto;

public record ChatSummaryResponse(
        Long chatId,
        String title,
        boolean isGroup,
        String lastMessageBody,
        Long lastMessageSenderId,
        String lastMessageCreatedAt,
        long unreadCount
) {}
