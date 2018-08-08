package com.amn.challengearchitecture.processfilestream.events;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

/**
 * Event to notify the SNS subscription
 *
 * @author Venkaiah Chowdary Koneru
 */
@Getter
@Setter
public class SNSSubscriptionEvent extends ApplicationEvent {
    private String topicArn;

    public SNSSubscriptionEvent(Object source, String topicArn) {
        super(source);
        this.topicArn = topicArn;
    }
}
