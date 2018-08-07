package com.amn.challengearchitecture.processfilestream.services.aws;

import com.amn.challengearchitecture.processfilestream.events.AWSInitCompletedEvent;
import com.amn.challengearchitecture.processfilestream.events.QueueCreatedEvent;
import com.amn.challengearchitecture.processfilestream.events.SNSSubscriptionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import redis.clients.jedis.Jedis;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sns.model.SubscribeResponse;

@Component
@Slf4j
public class AWSQueueCreatedHandler implements ApplicationListener<QueueCreatedEvent> {
    private final SnsClient snsClient;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Jedis redisClient;

    @Autowired
    public AWSQueueCreatedHandler(SnsClient snsClient,
                                  ApplicationEventPublisher applicationEventPublisher,
                                  Jedis redisClient) {
        this.redisClient = redisClient;
        this.snsClient = snsClient;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void onApplicationEvent(QueueCreatedEvent queueCreatedEvent) {
        // Get Topic ARN from Redis
        final String redisKey = "topicArn";

        final String topicArn = redisClient.get(redisKey);

        log.debug("Queue: {}. TopicArn: {}", queueCreatedEvent.getQueruArn(), topicArn);

        // Subscribe to Topic
        SubscribeRequest subscribeRequest = SubscribeRequest
                .builder()
                .topicArn(topicArn)
                .protocol("sqs")
                .endpoint(queueCreatedEvent.getQueruArn())
                .build();

        SubscribeResponse subscribeResponse = snsClient
                .subscribe(subscribeRequest);

        Assert.isTrue(subscribeResponse.sdkHttpResponse().statusCode() == 200,
                "Failed to subscribe Queue to SNS topic");

        applicationEventPublisher.publishEvent(new SNSSubscriptionEvent(this, topicArn));

        applicationEventPublisher.publishEvent(new AWSInitCompletedEvent(this,
                queueCreatedEvent.getQueueUrl(), queueCreatedEvent.getQueruArn(), topicArn));
    }
}
