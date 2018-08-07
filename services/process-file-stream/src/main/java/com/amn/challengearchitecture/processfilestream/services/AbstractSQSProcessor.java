package com.amn.challengearchitecture.processfilestream.services;

import com.amn.challengearchitecture.processfilestream.properties.AWSProperties;
import com.amn.challengearchitecture.processfilestream.services.util.SQSFetchUtil;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

public abstract class AbstractSQSProcessor implements SQSProcessor {
    private static final int MAX_NUM_MESSAGES = 5;
    @Autowired
    protected S3Client s3Client;

    @Autowired
    protected SqsClient sqsClient;

    @Autowired
    protected AWSProperties awsProperties;

    @Getter
    @Setter
    protected String queueUrl;

    @Getter
    @Setter
    protected String queueArn;

    @Getter
    @Setter
    protected String topicArn;

    public void initialize() {
        ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(MAX_NUM_MESSAGES)
                .waitTimeSeconds(10)
                .visibilityTimeout(30)
                .build();
        SQSFetchUtil.receiveMessages(sqsClient, receiveMessageRequest, 10, this::process);
    }

    protected abstract void process(Message message);
}
