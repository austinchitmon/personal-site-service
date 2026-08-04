package com.chitmon.backend.users;

import java.util.UUID;

public record UserResponse(UUID id, String email, String displayName, String avatarUrl) {
}
