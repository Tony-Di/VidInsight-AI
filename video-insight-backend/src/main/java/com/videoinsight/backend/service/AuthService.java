package com.videoinsight.backend.service;

import com.videoinsight.backend.model.request.LoginRequest;
import com.videoinsight.backend.model.request.RegisterRequest;
import com.videoinsight.backend.model.response.AuthResponse;
import com.videoinsight.backend.model.response.UserProfile;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    UserProfile getCurrentUser();
}
