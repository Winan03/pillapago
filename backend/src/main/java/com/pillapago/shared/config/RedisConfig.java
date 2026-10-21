package com.pillapago.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * Configuración de Spring Data Redis y scripts de Lua atómicos
 * para el hot path de velocity e idempotencia.
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public RedisScript<Long> slidingWindowFrequencyScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/sliding_window_frequency.lua")));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public RedisScript<Long> slidingWindowDistinctScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/sliding_window_distinct.lua")));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public RedisScript<String> idempotencyScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/idempotency_check_and_reserve.lua")));
        script.setResultType(String.class);
        return script;
    }
}
