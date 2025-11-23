package com.punith.chat.service;

import com.punith.chat.domain.chat.Chat;
import com.punith.chat.domain.chat.ChatParticipant;
import com.punith.chat.domain.message.Message;
import com.punith.chat.domain.message.MessageReceipt;
import com.punith.chat.domain.user.User;
import com.punith.chat.repository.MessageReceiptRepository;
import com.punith.chat.repository.MessageRepository;
import com.punith.chat.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import com.punith.chat.web.message.dto.UnreadMessageResponse;
import com.punith.chat.web.chat.dto.ChatSummaryResponse;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final MessageReceiptRepository messageReceiptRepository;
    private final UserRepository userRepository;
    private final ChatService chatService;

    public MessageService(MessageRepository messageRepository,
                          MessageReceiptRepository messageReceiptRepository,
                          UserRepository userRepository,
                          ChatService chatService) {
        this.messageRepository = messageRepository;
        this.messageReceiptRepository = messageReceiptRepository;
        this.userRepository = userRepository;
        this.chatService = chatService;
    }

    @Transactional
    public Message sendMessage(Long senderId, Long chatId, String body, String clientMessageId) {

        Chat chat = chatService.getChatForUserOrThrow(chatId, senderId);
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + senderId));


        if (clientMessageId != null) {
            return messageRepository.findByChatIdAndClientMessageId(chatId, clientMessageId)
                    .orElseGet(() -> createAndSaveMessage(chat, sender, body, clientMessageId));
        } else {
            return createAndSaveMessage(chat, sender, body, null);
        }
    }


    private Message createAndSaveMessage(Chat chat, User sender, String body, String clientMessageId) {
        Message m = new Message();
        m.setChat(chat);
        m.setSender(sender);
        m.setBody(body);
        m.setClientMessageId(clientMessageId);

        Message saved = messageRepository.save(m);


        List<ChatParticipant> participants = chatService.getParticipantsForChat(chat.getId());

        OffsetDateTime now = OffsetDateTime.now();

        for (ChatParticipant cp : participants) {
            MessageReceipt receipt = new MessageReceipt();
            receipt.setMessage(saved);
            receipt.setUser(cp.getUser());
            receipt.setDeliveryTimestamp(now);
            messageReceiptRepository.save(receipt);
        }

        return saved;
    }

    public List<Message> getMessages(Long userId, Long chatId, int limit) {
        // ensure user is allowed
        Chat chat = chatService.getChatForUserOrThrow(chatId, userId);
        return messageRepository.findByChatOrderByCreatedAtDesc(chat, PageRequest.of(0, limit));
    }

    @Transactional
    public void markMessagesAsRead(Long userId, Long chatId, List<Long> messageIds) {
        chatService.getChatForUserOrThrow(chatId, userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        OffsetDateTime now = OffsetDateTime.now();

        for (Long messageId : messageIds) {
            Message message = messageRepository.findById(messageId)
                    .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));


            if (!message.getChat().getId().equals(chatId)) {
                throw new IllegalArgumentException("Message " + messageId + " not in chat " + chatId);
            }

            MessageReceipt receipt = messageReceiptRepository
                    .findByMessageAndUser(message, user)
                    .orElseGet(() -> {
                        MessageReceipt mr = new MessageReceipt();
                        mr.setMessage(message);
                        mr.setUser(user);
                        return mr;
                    });

            if (receipt.getReadTimestamp() == null || receipt.getReadTimestamp().isBefore(now)) {
                receipt.setReadTimestamp(now);
            }
            messageReceiptRepository.save(receipt);
        }
    }

    public List<UnreadMessageResponse> getUnreadMessages(Long userId, Long chatId, int limit) {
        Chat chat = chatService.getChatForUserOrThrow(chatId, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        List<MessageReceipt> receipts = messageReceiptRepository
                .findByUserAndMessageChatAndReadTimestampIsNullOrderByMessageCreatedAtAsc(
                        user, chat, PageRequest.of(0, limit));

        return receipts.stream()
                .map(r -> {
                    Message m = r.getMessage();
                    return new UnreadMessageResponse(
                            m.getId(),
                            m.getChat().getId(),
                            m.getSender().getId(),
                            m.getBody(),
                            m.getCreatedAt()
                    );
                })
                .toList();
    }

    public List<ChatSummaryResponse> getChatSummaries(Long userId) {
        // all chats user participates in
        List<Chat> chats = chatService.getChatsForUserAsChats(userId);

        // unread counts per chat
        List<Object[]> counts = messageReceiptRepository.findUnreadCountsPerChat(userId);
        java.util.Map<Long, Long> unreadByChatId = new java.util.HashMap<>();
        for (Object[] row : counts) {
            Long chatId = (Long) row[0];
            Long count = (Long) row[1];
            unreadByChatId.put(chatId, count);
        }

        java.util.List<ChatSummaryResponse> result = new java.util.ArrayList<>();

        for (Chat chat : chats) {
            // last message in chat
            List<Message> latest = messageRepository.findByChatOrderByCreatedAtDesc(
                    chat, PageRequest.of(0, 1)
            );
            Message last = latest.isEmpty() ? null : latest.get(0);

            String lastBody = last != null ? last.getBody() : null;
            Long lastSenderId = last != null ? last.getSender().getId() : null;
            String lastCreatedAt = last != null ? last.getCreatedAt().toString() : null;

            long unreadCount = unreadByChatId.getOrDefault(chat.getId(), 0L);

            result.add(new ChatSummaryResponse(
                    chat.getId(),
                    chat.getTitle(),
                    chat.isGroup(),
                    lastBody,
                    lastSenderId,
                    lastCreatedAt,
                    unreadCount
            ));
        }

        // optional: sort so chats with unread appear first, then by last message time
        result.sort((a, b) -> {
            int cmpUnread = Long.compare(b.unreadCount(), a.unreadCount());
            if (cmpUnread != 0) return cmpUnread;

            // compare lastMessageCreatedAt descending
            if (a.lastMessageCreatedAt() == null && b.lastMessageCreatedAt() == null) return 0;
            if (a.lastMessageCreatedAt() == null) return 1;
            if (b.lastMessageCreatedAt() == null) return -1;
            return b.lastMessageCreatedAt().compareTo(a.lastMessageCreatedAt());
        });

        return result;
    }
}
