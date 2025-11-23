package com.punith.chat.repository;

import com.punith.chat.domain.message.MessageReceipt;
import com.punith.chat.domain.message.Message;
import com.punith.chat.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import com.punith.chat.domain.chat.Chat;
import com.punith.chat.domain.message.Message;
import com.punith.chat.domain.user.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageReceiptRepository extends JpaRepository<MessageReceipt, Long> {

    Optional<MessageReceipt> findByMessageAndUser(Message message, User user);

    List<MessageReceipt> findByMessage(Message message);

    // Unread receipts for a user in a given chat, oldest first
    List<MessageReceipt> findByUserAndMessageChatAndReadTimestampIsNullOrderByMessageCreatedAtAsc(
            User user,
            Chat chat,
            org.springframework.data.domain.Pageable pageable
    );

    // Unread counts per chat for a user
    @Query("""
       select mr.message.chat.id as chatId, count(mr) as unreadCount
       from MessageReceipt mr
       where mr.user.id = :userId
         and mr.readTimestamp is null
       group by mr.message.chat.id
       """)
    List<Object[]> findUnreadCountsPerChat(@Param("userId") Long userId);

}
