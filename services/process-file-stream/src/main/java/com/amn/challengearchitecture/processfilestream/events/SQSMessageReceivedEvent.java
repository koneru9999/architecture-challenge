package com.amn.challengearchitecture.processfilestream.events;

import com.amazonaws.services.sqs.model.Message;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

/**
 * Event to notify about a new message has been received from SQS Queue.
 *
 * @author Venkaiah Chowdary Koneru
 */
@Getter
@Setter
public class SQSMessageReceivedEvent extends ApplicationEvent {
    private Message message;

    public SQSMessageReceivedEvent(Object source, Message message) {
        super(source);
        this.message = message;
    }
}
