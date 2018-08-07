package com.amn.challengearchitecture.processfilestream.services.aws;

import com.amn.challengearchitecture.processfilestream.events.AWSTriggerEvent;
import com.amn.challengearchitecture.processfilestream.events.QueueCreatedEvent;
import com.amn.challengearchitecture.processfilestream.properties.AWSProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;

import static software.amazon.awssdk.services.sqs.model.QueueAttributeName.QUEUE_ARN;

@Component
@Slf4j
public class AWSTriggerSQSHandler implements ApplicationListener<AWSTriggerEvent> {
    private final SqsClient sqsClient;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final AWSProperties awsProperties;

    @Autowired
    public AWSTriggerSQSHandler(SqsClient sqsClient,
                                ApplicationEventPublisher applicationEventPublisher,
                                AWSProperties awsProperties) {
        this.sqsClient = sqsClient;
        this.applicationEventPublisher = applicationEventPublisher;
        this.awsProperties = awsProperties;
    }

    @Override
    public void onApplicationEvent(AWSTriggerEvent awsTriggerEvent) {
        CreateQueueRequest createQueueRequest = CreateQueueRequest
                .builder()
                .queueName(awsProperties.getSqs().getQueueName())
                .build();

        CreateQueueResponse createQueueResponse = sqsClient
                .createQueue(createQueueRequest);

        Assert.isTrue(createQueueResponse.sdkHttpResponse().statusCode() == 200,
                "Failed to create queue");

        if (log.isDebugEnabled()) {
            log.debug("QueueURL: ", createQueueResponse.queueUrl());
        }

        // Get QueueArn
        GetQueueAttributesRequest getQueueAttributesRequest = GetQueueAttributesRequest
                .builder()
                .attributeNames(QUEUE_ARN)
                .queueUrl(createQueueResponse.queueUrl())
                .build();

        GetQueueAttributesResponse getQueueAttributesResponse = sqsClient
                .getQueueAttributes(getQueueAttributesRequest);

        Assert.isTrue(getQueueAttributesResponse.sdkHttpResponse().statusCode() == 200,
                "Failed to get Queue attributes");

        final String queueArn = getQueueAttributesResponse.attributes().get(QUEUE_ARN);

        applicationEventPublisher.publishEvent(new QueueCreatedEvent(this, queueArn,
                createQueueResponse.queueUrl()));
    }
}
