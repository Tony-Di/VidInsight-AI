package com.videoinsight.backend.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "User login request")
public class LoginRequest {

    @NotBlank(message = "email is required")
    @Email(message = "email format is invalid")
    @Schema(description = "Login email.", example = "alice@example.com")
    private String email;

    @NotBlank(message = "password is required")
    @Schema(description = "Plain text password.", example = "Str0ng!Passw0rd")
    private String password;
}
