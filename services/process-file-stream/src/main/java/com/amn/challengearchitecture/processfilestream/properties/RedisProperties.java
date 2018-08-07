package com.amn.challengearchitecture.processfilestream.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "redis")
@Data
public class RedisProperties {
    private String host = "localhost";
    private int port = 6379;
}
