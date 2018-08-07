package com.amn.challengearchitecture.processfilestream.events;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

@Getter
@Setter
public class AWSTriggerEvent extends ApplicationEvent {

    public AWSTriggerEvent(Object source) {
        super(source);
    }
}
