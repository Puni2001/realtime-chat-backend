package com.punith.chat.messaging;

import java.util.List;

public class WsFanoutEvents {


    public record NewMessageFanoutEvent(
            Long messageId,
            Long chatId,
            Long senderId,
            String body,
            String createdAtIso
    ) {}


    public record ReadReceiptFanoutEvent(
            Long chatId,
            Long readerId,
            List<Long> messageIds,
            String readAtIso
    ) {}


    public record MessageStatusFanoutEvent(
            Long messageId,
            Long chatId,
            Long userId,
            String status
    ) {}
}
