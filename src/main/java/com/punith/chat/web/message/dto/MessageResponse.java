package com.punith.chat.web.message.dto;

import java.time.OffsetDateTime;

public record MessageResponse(
        Long id,
        Long chatId,
        Long senderId,
        String body,
        OffsetDateTime createdAt
) {}
