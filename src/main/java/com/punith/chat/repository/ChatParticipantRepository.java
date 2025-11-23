package com.punith.chat.repository;

import com.punith.chat.domain.chat.ChatParticipant;
import com.punith.chat.domain.chat.Chat;
import com.punith.chat.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

    List<ChatParticipant> findByUser(User user);

    List<ChatParticipant> findByChat(Chat chat);

    List<ChatParticipant> findByChatId(Long chatId);
}
