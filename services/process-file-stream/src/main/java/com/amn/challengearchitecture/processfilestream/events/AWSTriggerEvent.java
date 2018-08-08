package com.amn.challengearchitecture.processfilestream.events;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

/**
 * event to notify to initiate AWS related configs such as SQS Queue, SNS Subscription
 *
 * @author Venkaiah Chowdary Koneru
 */
@Getter
@Setter
public class AWSTriggerEvent extends ApplicationEvent {

    public AWSTriggerEvent(Object source) {
        super(source);
    }
}
