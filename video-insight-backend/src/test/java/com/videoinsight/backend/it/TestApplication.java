package com.videoinsight.backend.it;

import com.videoinsight.backend.config.RabbitMqConfig;
import com.videoinsight.backend.mq.VideoAnalysisConsumer;
import com.videoinsight.backend.mq.VideoAnalysisProducer;
import org.mockito.Mockito;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication(exclude = RabbitAutoConfiguration.class)
@ConfigurationPropertiesScan("com.videoinsight.backend.config")
@MapperScan("com.videoinsight.backend.mapper")
@ComponentScan(
        basePackages = "com.videoinsight.backend",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = { RabbitMqConfig.class, VideoAnalysisConsumer.class, VideoAnalysisProducer.class }
        )
)
public class TestApplication {

    /** Services depend on VideoAnalysisProducer; supply a no-op mock since MQ is excluded. */
    @Bean
    public VideoAnalysisProducer videoAnalysisProducer() {
        return Mockito.mock(VideoAnalysisProducer.class);
    }
}
