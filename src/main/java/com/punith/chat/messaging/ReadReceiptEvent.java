package com.punith.chat.messaging;

import java.util.List;

public record ReadReceiptEvent(
        Long chatId,
        Long userId,
        List<Long> messageIds,
        Long timestampMillis
) {}
