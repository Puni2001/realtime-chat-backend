package com.punith.chat.service;

import com.punith.chat.domain.chat.Chat;
import com.punith.chat.domain.chat.ChatParticipant;
import com.punith.chat.domain.user.User;
import com.punith.chat.repository.ChatParticipantRepository;
import com.punith.chat.repository.ChatRepository;
import com.punith.chat.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ChatService {

    private final ChatRepository chatRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final UserRepository userRepository;

    public ChatService(ChatRepository chatRepository,
                       ChatParticipantRepository chatParticipantRepository,
                       UserRepository userRepository) {
        this.chatRepository = chatRepository;
        this.chatParticipantRepository = chatParticipantRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Chat createChat(Long creatorUserId, boolean isGroup, String title, List<Long> participantIds) {

        Set<Long> uniqueIds = new LinkedHashSet<>(participantIds);
        uniqueIds.add(creatorUserId);


        Map<Long, User> usersById = new HashMap<>();
        for (Long id : uniqueIds) {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
            usersById.put(id, user);
        }

        Chat chat = new Chat();
        chat.setGroup(isGroup);
        chat.setTitle(isGroup ? title : null);
        chat.setCreatedBy(usersById.get(creatorUserId));

        Chat savedChat = chatRepository.save(chat);

        for (Long userId : uniqueIds) {
            ChatParticipant cp = new ChatParticipant();
            cp.setChat(savedChat);
            cp.setUser(usersById.get(userId));
            // creator is admin for groups
            if (isGroup && userId.equals(creatorUserId)) {
                cp.setRole("admin");
            } else {
                cp.setRole("member");
            }
            chatParticipantRepository.save(cp);
        }

        return savedChat;
    }

    public Chat getChatForUserOrThrow(Long chatId, Long userId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Chat not found: " + chatId));

        boolean isParticipant = chatParticipantRepository.findByChat(chat).stream()
                .anyMatch(cp -> cp.getUser().getId().equals(userId));

        if (!isParticipant) {
            throw new IllegalArgumentException("User " + userId + " is not a participant of chat " + chatId);
        }

        return chat;
    }

    public List<ChatParticipant> getChatsForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return chatParticipantRepository.findByUser(user);
    }

    public List<ChatParticipant> getParticipantsForChat(Long chatId) {
        return chatParticipantRepository.findByChatId(chatId);
    }

    public List<Chat> getChatsForUserAsChats(Long userId) {
        List<ChatParticipant> parts = getChatsForUser(userId);
        return parts.stream()
                .map(ChatParticipant::getChat)
                .distinct()
                .toList();
    }

}
