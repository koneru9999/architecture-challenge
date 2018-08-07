package com.amn.challengearchitecture.processfilestream.events;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

@Setter
@Getter
public class QueueCreatedEvent extends ApplicationEvent {
    private String queueUrl;
    private String queruArn;

    public QueueCreatedEvent(Object source, String queueArn, String queueUrl) {
        super(source);
        this.queruArn = queueArn;
        this.queueUrl = queueUrl;
    }
}
