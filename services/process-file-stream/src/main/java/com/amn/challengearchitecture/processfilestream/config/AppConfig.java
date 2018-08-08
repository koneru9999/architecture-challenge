package com.amn.challengearchitecture.processfilestream.config;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amn.challengearchitecture.processfilestream.events.AWSTriggerEvent;
import com.amn.challengearchitecture.processfilestream.properties.AWSProperties;
import com.amn.challengearchitecture.processfilestream.properties.RedisProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import redis.clients.jedis.Jedis;

@Configuration
@ComponentScan(basePackages = {
        "com.amn.challengearchitecture.processfilestream.services",
        "com.amn.challengearchitecture.processfilestream.properties"
})
@Slf4j
public class AppConfig {
    private final AWSProperties awsProperties;
    private final RedisProperties redisProperties;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    public AppConfig(AWSProperties awsProperties,
                     RedisProperties redisProperties) {
        this.awsProperties = awsProperties;
        this.redisProperties = redisProperties;
    }

    @Bean
    AmazonS3 getS3Client() {
        return AmazonS3ClientBuilder.standard()
                .withCredentials(credentialsProvider())
                .enablePathStyleAccess()
                .disableChunkedEncoding()
                .withEndpointConfiguration(new EndpointConfiguration(awsProperties.getS3().getEndPoint(),
                        awsProperties.getRegion()))
                .build();
    }

    @Bean
    AmazonSQS getSQSClient() {
        return AmazonSQSClientBuilder.standard()
                .withCredentials(credentialsProvider())
                .withEndpointConfiguration(new EndpointConfiguration(awsProperties.getSqs().getEndPoint(),
                        awsProperties.getRegion()))
                .build();
    }

    @Bean
    AmazonSNS getSNSClient() {
        return AmazonSNSClientBuilder.standard()
                .withCredentials(credentialsProvider())
                .withEndpointConfiguration(new EndpointConfiguration(awsProperties.getSns().getEndPoint(),
                        awsProperties.getRegion()))
                .build();
    }

    @Bean
    Jedis redisClient() {
        return new Jedis(redisProperties.getHost(), redisProperties.getPort());
    }

    @Bean
    AWSCredentialsProvider credentialsProvider() {
        return new EnvironmentVariableCredentialsProvider();
    }

    @Bean(name = "applicationEventMulticaster")
    public ApplicationEventMulticaster simpleApplicationEventMulticaster() {
        SimpleApplicationEventMulticaster eventMulticaster
                = new SimpleApplicationEventMulticaster();

        eventMulticaster.setTaskExecutor(new SimpleAsyncTaskExecutor());
        return eventMulticaster;
    }

    @Bean
    CommandLineRunner extractTextRunner() {
        return args -> {
            log.debug("Application has started. Initializing AWS hooks...");

            applicationEventPublisher.publishEvent(new AWSTriggerEvent(this));
        };
    }
}
