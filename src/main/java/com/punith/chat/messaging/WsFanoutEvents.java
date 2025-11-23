package com.punith.chat.messaging;

import java.util.List;

public class WsFanoutEvents {

    // For NEW_MESSAGE fanout
    public record NewMessageFanoutEvent(
            Long messageId,
            Long chatId,
            Long senderId,
            String body,
            String createdAtIso
    ) {}

    // For READ_RECEIPT fanout
    public record ReadReceiptFanoutEvent(
            Long chatId,
            Long readerId,
            List<Long> messageIds,
            String readAtIso
    ) {}

    // 🔹 NEW: message status fanout (e.g. DELIVERED)
    public record MessageStatusFanoutEvent(
            Long messageId,
            Long chatId,
            Long userId,   // who should see the status update (usually sender)
            String status  // "DELIVERED"
    ) {}
}
