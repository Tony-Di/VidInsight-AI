package com.videoinsight.backend.controller;

import com.videoinsight.backend.common.ApiResponse;
import com.videoinsight.backend.model.request.LoginRequest;
import com.videoinsight.backend.model.request.RegisterRequest;
import com.videoinsight.backend.model.response.AuthResponse;
import com.videoinsight.backend.model.response.UserProfile;
import com.videoinsight.backend.ratelimit.KeyStrategy;
import com.videoinsight.backend.ratelimit.RateLimit;
import com.videoinsight.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "User registration, login, current-user profile")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates a new account and returns a JWT for immediate auto-login.")
    @RateLimit(key = "auth.register", capacity = 3, refillPerMinute = 3, strategy = KeyStrategy.IP)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Verifies email/password and returns a JWT.")
    @RateLimit(key = "auth.login", capacity = 5, refillPerMinute = 5, strategy = KeyStrategy.IP)
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Returns the profile of the user identified by the bearer token.")
    public ApiResponse<UserProfile> me() {
        return ApiResponse.success(authService.getCurrentUser());
    }
}
