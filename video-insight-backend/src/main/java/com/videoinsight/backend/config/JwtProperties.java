package com.videoinsight.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** HS256 签名密钥。生产必须 >= 32 字节,通过环境变量注入。 */
    private String secret;

    /** Access token 有效期(小时)。 */
    private long expirationHours = 24;

    /** JWT iss 字段,便于以后切换签发方时区分。 */
    private String issuer = "vidinsight-ai";
}
