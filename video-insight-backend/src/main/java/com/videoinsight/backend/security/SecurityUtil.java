package com.videoinsight.backend.security;

import com.videoinsight.backend.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtil {

    private SecurityUtil() {}

    /**
     * 拿当前请求的用户 id。Spring Security 的 filter chain 已经在前面把 principal 写进了
     * SecurityContext;到 Service 这一层永远应该有值。拿不到说明配置错了,直接 401。
     */
    public static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new BusinessException(401, "not authenticated");
        }
        return principal.userId();
    }

    public static AppUserPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new BusinessException(401, "not authenticated");
        }
        return principal;
    }
}
