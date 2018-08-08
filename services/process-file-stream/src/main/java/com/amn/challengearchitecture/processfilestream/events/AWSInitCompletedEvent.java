package com.amn.challengearchitecture.processfilestream.events;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

/**
 * Event object to notify about the initial configurations such as
 * SQS Queue creation and SNS Subscription
 *
 * @author Venkaiah Chowdary Koneru
 */
@Setter
@Getter
public class AWSInitCompletedEvent extends ApplicationEvent {
    private String queueUrl;
    private String queueArn;
    private String topicArn;

    /**
     * @param source
     * @param queueUrl
     * @param queueArn
     * @param topicArn
     */
    public AWSInitCompletedEvent(Object source, String queueUrl, String queueArn, String topicArn) {
        super(source);
        this.queueArn = queueArn;
        this.queueUrl = queueUrl;
        this.topicArn = topicArn;
    }
}
