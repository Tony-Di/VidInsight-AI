package com.videoinsight.backend.service.impl;

import com.videoinsight.backend.entity.AppUser;
import com.videoinsight.backend.exception.BusinessException;
import com.videoinsight.backend.mapper.AppUserMapper;
import com.videoinsight.backend.model.request.LoginRequest;
import com.videoinsight.backend.model.request.RegisterRequest;
import com.videoinsight.backend.model.response.AuthResponse;
import com.videoinsight.backend.model.response.UserProfile;
import com.videoinsight.backend.security.AppUserPrincipal;
import com.videoinsight.backend.security.JwtUtil;
import com.videoinsight.backend.security.SecurityUtil;
import com.videoinsight.backend.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AppUserMapper appUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (appUserMapper.findByEmail(email) != null) {
            throw new BusinessException(409, "email already registered");
        }

        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(StringUtils.hasText(request.getDisplayName())
                ? request.getDisplayName().trim()
                : defaultDisplayName(email));
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        appUserMapper.insert(user);

        return issueAuthResponse(user);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        AppUser user = appUserMapper.findByEmail(email);
        // 故意把"邮箱不存在"和"密码错误"合并成同一个错误,防止账号枚举(timing 攻击另说)。
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(401, "invalid email or password");
        }
        return issueAuthResponse(user);
    }

    @Override
    public UserProfile getCurrentUser() {
        AppUserPrincipal principal = SecurityUtil.currentPrincipal();
        AppUser user = appUserMapper.selectById(principal.userId());
        if (user == null) {
            // Token 还有效但 DB 里用户被删了——视为未认证。
            throw new BusinessException(401, "user no longer exists");
        }
        return UserProfile.from(user);
    }

    private AuthResponse issueAuthResponse(AppUser user) {
        String token = jwtUtil.issue(user.getId(), user.getEmail());
        return new AuthResponse(token, jwtUtil.expirationSeconds(), UserProfile.from(user));
    }

    private String defaultDisplayName(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
