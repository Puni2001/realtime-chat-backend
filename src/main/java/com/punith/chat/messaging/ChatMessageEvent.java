package com.punith.chat.messaging;

public record ChatMessageEvent(
        Long chatId,
        Long senderId,
        String body,
        String clientMessageId,
        Long timestampMillis
) {}
