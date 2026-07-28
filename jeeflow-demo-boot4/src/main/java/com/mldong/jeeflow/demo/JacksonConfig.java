package com.mldong.jeeflow.demo;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * Jackson Long → String（全局），避免 Snowflake 17 位 ID 在 JavaScript 中精度丢失
 *
 * <p>Spring Boot 4 使用 Jackson 3 (tools.jackson.*)，通过 JsonMapperBuilderCustomizer 全局配置。</p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(long.class, ToStringSerializer.instance);
            builder.addModule(module);
        };
    }
}
