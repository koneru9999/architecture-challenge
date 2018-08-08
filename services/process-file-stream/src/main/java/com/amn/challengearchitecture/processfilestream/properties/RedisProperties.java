package com.amn.challengearchitecture.processfilestream.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Redis properties holder
 *
 * @author Venkaiah Chowdary Koneru
 */
@Component
@ConfigurationProperties(prefix = "redis")
@Data
public class RedisProperties {
    private String host = "localhost";
    private int port = 6379;
}
