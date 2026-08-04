package com.chitmon.backend.users;

import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
class UserSyncService {

    private final UserRepository userRepository;

    UserSyncService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    void sync(Jwt jwt) {
        UUID id = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaimAsString("email");
        Map<String, Object> metadata = jwt.getClaimAsMap("user_metadata");
        userRepository.upsert(id, email,
                firstNonBlank(metadata, "full_name", "name"),
                firstNonBlank(metadata, "avatar_url", "picture"));
    }

    private String firstNonBlank(Map<String, Object> metadata, String... keys) {
        if (metadata == null) {
            return null;
        }
        for (String key : keys) {
            if (metadata.get(key) instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
