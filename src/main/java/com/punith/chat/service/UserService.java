package com.punith.chat.service;

import com.punith.chat.domain.user.User;
import com.punith.chat.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User createUser(String phone, String displayName) {
        User user = new User();
        user.setPhone(phone);
        user.setDisplayName(displayName);
        return userRepository.save(user);
    }

    public User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }
}
