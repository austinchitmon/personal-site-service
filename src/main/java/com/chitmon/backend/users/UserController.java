package com.chitmon.backend.users;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public UserResponse getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        UUID id = UUID.fromString(jwt.getSubject());
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("User missing after JIT sync: " + id));
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getAvatarUrl());
    }
}
