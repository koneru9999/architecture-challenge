package com.amn.challengearchitecture.processfilestream.events;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;
import software.amazon.awssdk.services.sqs.model.Message;

@Getter
@Setter
public class SQSMessageReceivedEvent extends ApplicationEvent {
    private Message message;

    public SQSMessageReceivedEvent(Object source, Message message) {
        super(source);
        this.message = message;
    }
}
