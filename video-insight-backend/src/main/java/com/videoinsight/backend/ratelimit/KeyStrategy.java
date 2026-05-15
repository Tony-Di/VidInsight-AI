package com.videoinsight.backend.ratelimit;

public enum KeyStrategy {
    /** 按当前登录用户 id 分桶。要求请求已通过 JWT 认证。 */
    USER,
    /** 按客户端 IP 分桶。用于登录/注册等未认证就要限制的端点(防暴力破解)。 */
    IP,
}
