package com.videoinsight.backend.it;

import com.videoinsight.backend.VideoInsightBackendApplication;
import com.videoinsight.backend.config.RabbitMqConfig;
import com.videoinsight.backend.mq.AgentAnalysisConsumer;
import com.videoinsight.backend.mq.AgentAnalysisProducer;
import com.videoinsight.backend.mq.VideoAnalysisConsumer;
import com.videoinsight.backend.mq.VideoAnalysisProducer;
import org.mockito.Mockito;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Test-only Spring Boot application that boots the real component graph EXCEPT RabbitMQ.
 *
 * <p>Decomposed into {@code @SpringBootConfiguration + @EnableAutoConfiguration + a single
 * @ComponentScan} (the explicit constituents of {@code @SpringBootApplication}) so there is exactly
 * one component scan whose {@code excludeFilters} we fully control.
 *
 * <p>Crucially, the scan must also exclude {@link VideoInsightBackendApplication}: it lives in the
 * scanned {@code com.videoinsight.backend} package and is itself a {@code @SpringBootApplication}
 * (hence a {@code @Configuration} carrying its OWN unfiltered {@code @ComponentScan}). If left in, that
 * nested scan re-discovers {@link RabbitMqConfig} and the MQ beans, undoing our exclusions — which
 * surfaces as {@code rabbitTemplate} failing on a missing {@code ConnectionFactory}.
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = RabbitAutoConfiguration.class)
@ConfigurationPropertiesScan("com.videoinsight.backend.config")
@MapperScan("com.videoinsight.backend.mapper")
@ComponentScan(
        basePackages = "com.videoinsight.backend",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        VideoInsightBackendApplication.class,
                        RabbitMqConfig.class,
                        VideoAnalysisConsumer.class,
                        VideoAnalysisProducer.class,
                        AgentAnalysisConsumer.class,
                        AgentAnalysisProducer.class
                }
        )
)
public class TestApplication {

    /** Services depend on VideoAnalysisProducer; supply a no-op mock since MQ is excluded. */
    @Bean
    public VideoAnalysisProducer videoAnalysisProducer() {
        return Mockito.mock(VideoAnalysisProducer.class);
    }

    /** VideoAgentTaskServiceImpl depends on AgentAnalysisProducer; same no-op mock treatment. */
    @Bean
    public AgentAnalysisProducer agentAnalysisProducer() {
        return Mockito.mock(AgentAnalysisProducer.class);
    }
}
