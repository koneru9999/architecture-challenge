package com.amn.challengearchitecture.processfilestream.services.text;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amn.challengearchitecture.processfilestream.events.AWSInitCompletedEvent;
import com.amn.challengearchitecture.processfilestream.events.SQSMessageReceivedEvent;
import com.amn.challengearchitecture.processfilestream.services.util.SQSFetchUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * @author Venkaiah Chowdary Koneru
 */
@Profile("extract-text")
@Component
public class ExtractTextQueueSNSSubscriptionListener implements ApplicationListener<AWSInitCompletedEvent> {
    private static final int MAX_NUM_MESSAGES = 10;
    private static final int DELAY_TIME_IN_BETWEEN_SECONDS = 2;
    private static final int SQS_WAIT_TIME_SECONDS = 20;
    private static final int VISIBILITY_TIMEOUT_SECONDS = 600;

    private final SQSTextProcessor sqsProcessor;
    private final AmazonSQS sqsClient;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     *
     * @param sqsTextProcessor
     * @param sqsClient
     * @param applicationEventPublisher
     */
    @Autowired
    public ExtractTextQueueSNSSubscriptionListener(SQSTextProcessor sqsTextProcessor,
                                                   AmazonSQS sqsClient,
                                                   ApplicationEventPublisher applicationEventPublisher) {
        this.sqsProcessor = sqsTextProcessor;
        this.sqsClient = sqsClient;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     *
     * @param awsInitCompletedEvent
     */
    @Override
    public void onApplicationEvent(AWSInitCompletedEvent awsInitCompletedEvent) {
        sqsProcessor.setQueueUrl(awsInitCompletedEvent.getQueueUrl());
        sqsProcessor.setQueueArn(awsInitCompletedEvent.getQueueArn());
        sqsProcessor.setTopicArn(awsInitCompletedEvent.getTopicArn());

        while (true) {
            ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(awsInitCompletedEvent.getQueueUrl())
                    .withMaxNumberOfMessages(MAX_NUM_MESSAGES)
                    .withVisibilityTimeout(VISIBILITY_TIMEOUT_SECONDS)
                    .withWaitTimeSeconds(SQS_WAIT_TIME_SECONDS);

            SQSFetchUtil.receiveMessages(sqsClient, receiveMessageRequest, DELAY_TIME_IN_BETWEEN_SECONDS, message ->
                    applicationEventPublisher.publishEvent(new SQSMessageReceivedEvent(this, message)));
        }
    }
}
