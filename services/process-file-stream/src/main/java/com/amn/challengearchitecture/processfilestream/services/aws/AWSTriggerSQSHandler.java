package com.amn.challengearchitecture.processfilestream.services.aws;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesResult;
import com.amn.challengearchitecture.processfilestream.events.AWSTriggerEvent;
import com.amn.challengearchitecture.processfilestream.events.QueueCreatedEvent;
import com.amn.challengearchitecture.processfilestream.properties.AWSProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Collections;

/**
 * @author Venkaiah Chowdary Koneru
 */
@Component
@Slf4j
public class AWSTriggerSQSHandler implements ApplicationListener<AWSTriggerEvent> {
    private static final String QUEUE_ARN = "QueueArn";

    private final AmazonSQS sqsClient;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final AWSProperties awsProperties;

    @Autowired
    public AWSTriggerSQSHandler(AmazonSQS sqsClient,
                                ApplicationEventPublisher applicationEventPublisher,
                                AWSProperties awsProperties) {
        this.sqsClient = sqsClient;
        this.applicationEventPublisher = applicationEventPublisher;
        this.awsProperties = awsProperties;
    }

    /**
     * {@inheritDoc}
     * <p>
     * As a initial step, we have to verify the Queue existence.
     * CreateQueueRequest will either create (if not exists) or returns existing queue.
     * </p>
     */
    @Override
    public void onApplicationEvent(AWSTriggerEvent awsTriggerEvent) {
        CreateQueueResult createQueueResponse = sqsClient
                .createQueue(new CreateQueueRequest(awsProperties.getSqs().getQueueName()));

        Assert.isTrue(createQueueResponse.getSdkHttpMetadata().getHttpStatusCode() == 200,
                "Failed to create queue");

        if (log.isDebugEnabled()) {
            log.debug("QueueURL: {}", createQueueResponse.getQueueUrl());
        }

        // Get QueueArn
        GetQueueAttributesResult getQueueAttributesResponse = sqsClient.getQueueAttributes(
                new GetQueueAttributesRequest(createQueueResponse.getQueueUrl(), Collections.singletonList(QUEUE_ARN)));

        Assert.isTrue(getQueueAttributesResponse.getSdkHttpMetadata().getHttpStatusCode() == 200,
                "Failed to get Queue attributes");

        final String queueArn = getQueueAttributesResponse.getAttributes().get(QUEUE_ARN);

        applicationEventPublisher.publishEvent(new QueueCreatedEvent(this, queueArn,
                createQueueResponse.getQueueUrl()));
    }
}
