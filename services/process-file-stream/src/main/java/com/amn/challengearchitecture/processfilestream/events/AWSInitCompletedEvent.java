package com.amn.challengearchitecture.processfilestream.events;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

@Setter
@Getter
public class AWSInitCompletedEvent extends ApplicationEvent {
    private String queueUrl;
    private String queueArn;
    private String topicArn;

    public AWSInitCompletedEvent(Object source, String queueUrl, String queueArn, String topicArn) {
        super(source);
        this.queueArn = queueArn;
        this.queueUrl = queueUrl;
        this.topicArn = topicArn;
    }
}
