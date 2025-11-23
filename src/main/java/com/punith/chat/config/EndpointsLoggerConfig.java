package com.punith.chat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration
public class EndpointsLoggerConfig {

    private static final Logger log = LoggerFactory.getLogger(EndpointsLoggerConfig.class);

    @Bean
    public ApplicationRunner printEndpoints(
            @Qualifier("requestMappingHandlerMapping")
            RequestMappingHandlerMapping handlerMapping
    ) {
        return args -> {
            handlerMapping.getHandlerMethods().forEach((info, method) -> {
                log.info("Mapped endpoint: {} -> {}", info, method);
            });
        };
    }
}
