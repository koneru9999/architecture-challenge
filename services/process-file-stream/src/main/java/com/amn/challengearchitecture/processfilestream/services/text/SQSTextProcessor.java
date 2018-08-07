package com.amn.challengearchitecture.processfilestream.services.text;

import com.amn.challengearchitecture.processfilestream.services.AbstractSQSProcessor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import software.amazon.awssdk.services.sqs.model.Message;

@Profile("extract-text")
@Component
@Slf4j
public class SQSTextProcessor extends AbstractSQSProcessor {

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    protected void process(Message message) {
        Assert.notNull(applicationEventPublisher, "publisher is null");
        Assert.notNull(s3Client, "s3 is null");
        Assert.notNull(sqsClient, "sqs is null");

        log.debug(message.body());
        JSONObject jsonObj = new JSONObject(message.body());

        JSONObject jsonObj2 = new JSONObject(jsonObj.getString("Message"));

        log.debug("{}", jsonObj2.toString());

        log.debug("Key: {}", jsonObj2.get("Key"));
    }
}
