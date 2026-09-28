package com.example.auction.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisQueueConfig {

    @Bean
    public ObjectMapper objectMapper() {
        // JavaTimeModule 포함 Spring/Jackson 모듈 자동 등록(LocalDateTime 직렬화 지원)
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // String 타입 직렬화
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        // 키는 String으로 직렬화
        template.setKeySerializer(stringRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);

        // 값은 String으로 직렬화 (JSON으로 변환 필요 시 application 레벨에서 처리)
        template.setValueSerializer(stringRedisSerializer);
        template.setHashValueSerializer(stringRedisSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
