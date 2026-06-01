package com.videoinsight.backend.it;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
                    .withDatabaseName("video_insight")
                    .withUsername("test")
                    .withPassword("test");

    static {
        REDIS.start();
        MYSQL.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        // Lettuce (RedisTemplate / StringRedisTemplate)
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // Redisson reads the same keys via @Value in RedissonConfig — no extra wiring needed.
        // Datasource
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    protected StringRedisTemplate stringRedisTemplate;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetState() {
        stringRedisTemplate.execute((RedisConnection conn) -> {
            conn.serverCommands().flushDb();
            return null;
        });
        jdbcTemplate.execute("TRUNCATE TABLE video_info");
        jdbcTemplate.execute("TRUNCATE TABLE video_upload_task");
    }
}
