package com.videoinsight.backend.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Value("${spring.data.redis.timeout:3000ms}")
    private Duration timeout;

    /**
     * 与 Lettuce(RedisTemplate)并存:
     * <ul>
     *   <li>RedisTemplate(Lettuce) → 缓存读写(Cache Aside)</li>
     *   <li>RedissonClient       → 分布式锁(RLock,自带 WatchDog 续期)</li>
     * </ul>
     * 两套连接池各管各的,互不干扰。
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        var singleServer = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database)
                .setConnectTimeout((int) timeout.toMillis())
                .setTimeout((int) timeout.toMillis());
        if (StringUtils.hasText(password)) {
            singleServer.setPassword(password);
        }
        return Redisson.create(config);
    }
}
