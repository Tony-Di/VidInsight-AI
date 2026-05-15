package com.videoinsight.backend.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authentication response containing JWT and user profile")
public class AuthResponse {

    @Schema(description = "JWT access token. Send back as 'Authorization: Bearer <token>' on subsequent calls.")
    private String token;

    @Schema(description = "Token expiration in seconds from issuance.", example = "86400")
    private Long expiresInSeconds;

    @Schema(description = "Current user profile snapshot.")
    private UserProfile user;
}
