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

/**
 * Base config for the application
 */
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

    /**
     * @return AmazonS3 client with environment credentials
     */
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

    /**
     * @return AmazonSQS client with environment credentials
     */
    @Bean
    AmazonSQS getSQSClient() {
        return AmazonSQSClientBuilder.standard()
                .withCredentials(credentialsProvider())
                .withEndpointConfiguration(new EndpointConfiguration(awsProperties.getSqs().getEndPoint(),
                        awsProperties.getRegion()))
                .build();
    }

    /**
     * @return AmazonSNS client with environment credentials
     */
    @Bean
    AmazonSNS getSNSClient() {
        return AmazonSNSClientBuilder.standard()
                .withCredentials(credentialsProvider())
                .withEndpointConfiguration(new EndpointConfiguration(awsProperties.getSns().getEndPoint(),
                        awsProperties.getRegion()))
                .build();
    }

    /**
     * @return Redis client
     */
    @Bean
    Jedis redisClient() {
        return new Jedis(redisProperties.getHost(), redisProperties.getPort());
    }

    /**
     * @return AWS Environemnt credentials provider
     */
    @Bean
    AWSCredentialsProvider credentialsProvider() {
        return new EnvironmentVariableCredentialsProvider();
    }

    /**
     * Default behaviour of Spring's event ecosystem is synchronous.
     * <p>
     * With this multi caster present in the context, event ecosystem will act as asynchronous.
     */
    @Bean(name = "applicationEventMulticaster")
    ApplicationEventMulticaster simpleApplicationEventMulticaster() {
        SimpleApplicationEventMulticaster eventMulticaster
                = new SimpleApplicationEventMulticaster();

        eventMulticaster.setTaskExecutor(new SimpleAsyncTaskExecutor());
        return eventMulticaster;
    }

    /**
     * Publishes event to configure AWS like SQS Queue, SNS Subscription
     *
     * @return command line runner
     */
    @Bean
    CommandLineRunner extractTextRunner() {
        return args -> {
            log.debug("Application has started. Initializing AWS hooks...");

            applicationEventPublisher.publishEvent(new AWSTriggerEvent(this));
        };
    }
}
