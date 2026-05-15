package com.videoinsight.backend.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "User registration request")
public class RegisterRequest {

    @NotBlank(message = "email is required")
    @Email(message = "email format is invalid")
    @Size(max = 190, message = "email is too long")
    @Schema(description = "Login email.", example = "alice@example.com")
    private String email;

    @NotBlank(message = "password is required")
    @Size(min = 8, max = 72, message = "password must be 8-72 characters")
    @Schema(description = "Plain text password. Hashed with BCrypt server-side.", example = "Str0ng!Passw0rd")
    private String password;

    @Size(max = 80, message = "display name is too long")
    @Schema(description = "Optional display name.", example = "Alice")
    private String displayName;
}
