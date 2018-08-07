package com.amn.challengearchitecture.processfilestream.services;

public interface SQSProcessor {

    void setQueueUrl(String queueUrl);

    void setQueueArn(String queueArn);

    void setTopicArn(String topicArn);

    void initialize();
}
