package com.amn.challengearchitecture.processfilestream.services.images;

import com.amn.challengearchitecture.processfilestream.events.AWSInitCompletedEvent;
import com.amn.challengearchitecture.processfilestream.events.SNSSubscriptionEvent;
import com.amn.challengearchitecture.processfilestream.services.SQSProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("extract-image-preview")
@Component
public class ExtractImagePreviewQueueSNSSubscriptionListener implements ApplicationListener<AWSInitCompletedEvent> {
    private final SQSProcessor sqsProcessor;

    @Autowired
    public ExtractImagePreviewQueueSNSSubscriptionListener(SQSProcessor sqsTextProcessor) {
        this.sqsProcessor = sqsTextProcessor;
    }

    @Override
    public void onApplicationEvent(AWSInitCompletedEvent awsInitCompletedEvent) {
        sqsProcessor.setQueueUrl(awsInitCompletedEvent.getQueueUrl());
        sqsProcessor.setQueueArn(awsInitCompletedEvent.getQueueArn());
        sqsProcessor.setTopicArn(awsInitCompletedEvent.getTopicArn());

        sqsProcessor.initialize();
    }
}
