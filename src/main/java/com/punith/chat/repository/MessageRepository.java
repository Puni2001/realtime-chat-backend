package com.punith.chat.repository;

import com.punith.chat.domain.message.Message;
import com.punith.chat.domain.chat.Chat;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByChatOrderByCreatedAtDesc(Chat chat, Pageable pageable);

    Optional<Message> findByChatIdAndClientMessageId(Long chatId, String clientMessageId);

}
