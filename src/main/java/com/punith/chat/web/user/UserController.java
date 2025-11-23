package com.punith.chat.web.user;

import com.punith.chat.domain.user.User;
import com.punith.chat.service.UserService;
import com.punith.chat.web.user.dto.CreateUserRequest;
import com.punith.chat.web.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.createUser(request.phone(), request.displayName());
        return ResponseEntity.ok(new UserResponse(user.getId(), user.getPhone(), user.getDisplayName()));
    }
}
