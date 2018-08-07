package com.amn.challengearchitecture.processfilestream.services.images;

import com.amn.challengearchitecture.processfilestream.services.AbstractSQSProcessor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.model.Message;

@Profile("extract-image-preview")
@Component
@Slf4j
public class SQSImageProcessor extends AbstractSQSProcessor {

    @Override
    protected void process(Message message) {
        log.debug(message.body());
        JSONObject jsonObj = new JSONObject(message.body());

        JSONObject jsonObj2 = new JSONObject(jsonObj.get("Message"));

        log.debug("Key: {}", jsonObj2.get("Key"));
    }
}
