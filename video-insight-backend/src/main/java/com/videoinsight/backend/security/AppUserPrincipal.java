package com.videoinsight.backend.security;

/**
 * 写到 SecurityContext 的 Authentication.principal 中,Controller/Service 可通过
 * {@link SecurityUtil#currentUserId()} 取到当前请求的用户 id。
 * 故意不包含密码哈希等敏感字段,避免被序列化或日志记录到外部。
 */
public record AppUserPrincipal(Long userId, String email) {
}
