package com.amn.challengearchitecture.processfilestream.config;

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
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

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
    S3Client getS3Client() {
        return S3Client.builder()
                .region(Region.of(awsProperties.getRegion()))
                .endpointOverride(URI.create(awsProperties.getS3().getEndPoint()))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .credentialsProvider(credentialsProvider())
                .build();
    }

    @Bean
    SqsClient getSQSClient() {
        return SqsClient.builder()
                .region(Region.of(awsProperties.getRegion()))
                .credentialsProvider(credentialsProvider())
                .endpointOverride(URI.create(awsProperties.getSqs().getEndPoint()))
                .build();
    }

    @Bean
    SnsClient getSNSClient() {
        return SnsClient.builder()
                .region(Region.of(awsProperties.getRegion()))
                .credentialsProvider(credentialsProvider())
                .endpointOverride(URI.create(awsProperties.getSns().getEndPoint()))
                .build();
    }

    @Bean
    Jedis redisClient() {
        return new Jedis(redisProperties.getHost(), redisProperties.getPort());
    }

    @Bean
    AwsCredentialsProvider credentialsProvider() {
        return EnvironmentVariableCredentialsProvider.create();
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
